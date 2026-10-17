package com.classicspotifywidget

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Spotify {
    const val PACKAGE = "com.spotify.music"

    /** Intent that opens Spotify, or null if it isn't installed. */
    fun launchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/** Notification access is what lets an app read other apps' media sessions. */
object Access {
    fun listenerComponent(context: Context) =
        ComponentName(context, SpotifyListenerService::class.java)

    fun isGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listenerComponent(context))
}

fun isPlaying(state: PlaybackState?): Boolean = when (state?.state) {
    PlaybackState.STATE_PLAYING,
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_FAST_FORWARDING,
    PlaybackState.STATE_REWINDING,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
    else -> false
}

object Covers {
    /** Single background thread for decoding, downloading and writing cover art. */
    val executor: ExecutorService = Executors.newSingleThreadExecutor()

    val BITMAP_KEYS = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART,
        MediaMetadata.METADATA_KEY_ART,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON,
    )

    val URI_KEYS = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
    )

    /** Loads cover art from a content:// or https:// URI. Returns null on any failure. */
    fun download(context: Context, uri: String): Bitmap? = try {
        val parsed = Uri.parse(uri)
        when (parsed.scheme?.lowercase()) {
            "content", "android.resource" ->
                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it) }

            "https" -> {
                val connection = URL(uri).openConnection() as HttpURLConnection
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                try {
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    connection.disconnect()
                }
            }

            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
