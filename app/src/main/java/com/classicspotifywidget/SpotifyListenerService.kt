package com.classicspotifywidget

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Bound by the system while notification access is granted, which is what allows reading
 * other apps' media sessions. The only notification it looks at is Spotify's player, to
 * keep its buttons for [SpotifyActions].
 *
 * Every active session is watched to know which app played last (that's where Android
 * routes media keys when nothing is playing); Spotify's session feeds the widget.
 * All callbacks run on the main thread.
 */
class SpotifyListenerService : NotificationListenerService() {

    private class Watched(val controller: MediaController, val callback: MediaController.Callback)

    private val handler = Handler(Looper.getMainLooper())
    private val watched = HashMap<MediaSession.Token, Watched>()
    private var sessionManager: MediaSessionManager? = null
    private var pendingCoverKey: String? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            onSessionsChanged(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java)
        val component = Access.listenerComponent(this)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            sessionManager = manager
            onSessionsChanged(manager.getActiveSessions(component).orEmpty())
        } catch (e: SecurityException) {
            // Access was revoked between binding and now.
        }
        activeNotifications?.forEach(::onNotificationPosted)
        WidgetRenderer.updateAll(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != Spotify.PACKAGE) return
        val notification = sbn.notification
        if (notification.category != Notification.CATEGORY_TRANSPORT) return
        val spotify = watched.values.firstOrNull { it.controller.packageName == Spotify.PACKAGE }
        SpotifyActions.update(notification, playing = isPlaying(spotify?.controller?.playbackState))
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        unwatchAll()
        WidgetRenderer.updateAll(this)
        // If the system dropped the binding while access is still granted, ask for it back.
        // When the user has turned access off this is a no-op.
        try {
            requestRebind(Access.listenerComponent(this))
        } catch (e: RuntimeException) {
            // Nothing to do; the system binds us again when access is re-enabled.
        }
    }

    override fun onDestroy() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        unwatchAll()
        super.onDestroy()
    }

    private fun onSessionsChanged(controllers: List<MediaController>) {
        val current = controllers.associateBy { it.sessionToken }

        for (token in watched.keys - current.keys) {
            watched.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
        }
        for ((token, controller) in current) {
            if (token in watched) continue
            val callback = SessionCallback(controller)
            controller.registerCallback(callback, handler)
            watched[token] = Watched(controller, callback)
            if (isPlaying(controller.playbackState)) {
                TrackStore.setLastPlayer(this, controller.packageName)
            }
        }

        val spotify = controllers.firstOrNull { it.packageName == Spotify.PACKAGE }
        if (spotify != null) {
            publish(spotify)
        } else {
            // Spotify's session is gone: keep the last track, show it as stopped.
            TrackStore.setPlayback(this, alive = false, playing = false)
            WidgetRenderer.updateAll(this)
        }
    }

    private fun unwatchAll() {
        for (entry in watched.values) entry.controller.unregisterCallback(entry.callback)
        watched.clear()
    }

    private inner class SessionCallback(private val controller: MediaController) :
        MediaController.Callback() {

        private val isSpotify = controller.packageName == Spotify.PACKAGE

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val service = this@SpotifyListenerService
            val lastPlayerChanged = isPlaying(state) && TrackStore.setLastPlayer(service, controller.packageName)
            if (isSpotify) {
                publish(controller)
            } else if (lastPlayerChanged) {
                // Another app took over media keys; the play button's fallback depends on it.
                WidgetRenderer.updateAll(service)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (isSpotify) publish(controller)
        }
    }

    private fun publish(spotify: MediaController) {
        val metadata = spotify.metadata
        if (metadata != null) {
            TrackStore.saveMetadata(this, metadata)
            fetchCoverIfNeeded(metadata)
        }
        TrackStore.setPlayback(this, alive = true, playing = isPlaying(spotify.playbackState))
        WidgetRenderer.updateAll(this)
    }

    /**
     * Spotify usually sends the cover as a bitmap, sometimes only as a URI, and sometimes
     * in a second metadata update after the title. Each track's cover is saved once.
     */
    private fun fetchCoverIfNeeded(metadata: MediaMetadata) {
        val key = TrackStore.keyOf(metadata) ?: return
        if (TrackStore.load(this).coverKey == key || pendingCoverKey == key) return

        val bitmap = Covers.BITMAP_KEYS.firstNotNullOfOrNull { metadata.getBitmap(it) }
        val uri = if (bitmap != null) null else {
            Covers.URI_KEYS.firstNotNullOfOrNull { k -> metadata.getString(k)?.takeIf { it.isNotBlank() } }
        }
        if (bitmap == null && uri == null) return

        pendingCoverKey = key
        val app = applicationContext
        Covers.executor.execute {
            val image = bitmap ?: uri?.let { Covers.download(app, it) }
            // Only keep it if Spotify is still on the same track.
            if (image != null && TrackStore.load(app).trackKey == key) {
                try {
                    TrackStore.saveCover(app, key, image)
                } catch (e: Exception) {
                    // Disk or decode failure: the widget keeps the placeholder.
                }
            }
            handler.post {
                if (pendingCoverKey == key) pendingCoverKey = null
                WidgetRenderer.updateAll(app)
            }
        }
    }
}
