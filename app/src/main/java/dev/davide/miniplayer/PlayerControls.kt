package dev.davide.miniplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/** Receives the widget's prev / play-pause / next taps. */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // Keeps the process alive while play waits to see whether Spotify woke up.
        val result = goAsync()
        PlayerControls.handle(context.applicationContext, action, done = result::finish)
    }
}

object PlayerControls {
    const val ACTION_PLAY_PAUSE = "dev.davide.miniplayer.action.PLAY_PAUSE"
    const val ACTION_NEXT = "dev.davide.miniplayer.action.NEXT"
    const val ACTION_PREVIOUS = "dev.davide.miniplayer.action.PREVIOUS"

    /** How long play waits for Spotify to start playing before opening it. A cold resume takes about 5 s. */
    private const val RESUME_TIMEOUT_MS = 6000L

    /** Calls [done] once handling is over, possibly after [RESUME_TIMEOUT_MS]. */
    fun handle(context: Context, action: String, done: () -> Unit) {
        val session = spotifySession(context)
        if (session != null) {
            val controls = session.transportControls
            when (action) {
                ACTION_PLAY_PAUSE -> if (isPlaying(session.playbackState)) controls.pause() else controls.play()
                ACTION_NEXT -> controls.skipToNext()
                ACTION_PREVIOUS -> controls.skipToPrevious()
            }
            done()
            return
        }

        // Spotify isn't running. Its own notification buttons, cached from when it last
        // showed them, restart it the way its official widget does.
        val sent = SpotifyActions.send(context, action)

        // Otherwise a media key: with no active session, Android delivers it to the app that
        // played last and lets it start in the background, which is how Spotify resumes the
        // last track. Only take that route when the last app was Spotify; otherwise the
        // widget's play button opens Spotify instead.
        if (!sent && TrackStore.load(context).lastPlayer == Spotify.PACKAGE) {
            val keyCode = when (action) {
                ACTION_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY
                ACTION_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                ACTION_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> null
            }
            if (keyCode != null) {
                val audio = context.getSystemService(AudioManager::class.java)
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
        }
        if (action != ACTION_PLAY_PAUSE) {
            done()
            return
        }

        // Last resort. After a long idle Spotify can leave behind an inactive session with
        // nothing loaded, which swallows media keys; and nothing may be cached yet.
        // If Spotify still isn't playing, open it.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isPlaying(spotifySession(context)?.playbackState)) {
                Spotify.launchIntent(context)?.let(context::startActivity)
            }
            done()
        }, RESUME_TIMEOUT_MS)
    }

    /** Looked up fresh on every tap, so it works even if this process was just restarted. */
    private fun spotifySession(context: Context): MediaController? {
        if (!Access.isGranted(context)) return null
        return try {
            context.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(Access.listenerComponent(context))
                .orEmpty()
                .firstOrNull { it.packageName == Spotify.PACKAGE }
        } catch (e: SecurityException) {
            null
        }
    }
}
