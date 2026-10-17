package com.classicspotifywidget

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/**
 * Spotify's own previous / play / next buttons, taken from its media notification.
 *
 * Sending one runs Spotify's own handler, as tapping its notification or its official widget
 * does. That keeps working after Spotify's process has died, because the system keeps a
 * PendingIntent alive until Spotify cancels it or is force-stopped. Only kept in memory:
 * a PendingIntent can't be saved to disk.
 */
object SpotifyActions {
    private var previous: PendingIntent? = null
    private var play: PendingIntent? = null
    private var next: PendingIntent? = null

    /**
     * Spotify lists previous, play-or-pause and next as its compact actions. The middle one
     * is only stored while Spotify isn't playing, when it's the play button.
     */
    fun update(notification: Notification, playing: Boolean) {
        val actions = notification.actions ?: return
        val compact = notification.extras.getIntArray(Notification.EXTRA_COMPACT_ACTIONS) ?: return
        if (compact.size != 3) return
        fun at(i: Int) = actions.getOrNull(compact[i])?.actionIntent
        previous = at(0) ?: previous
        next = at(2) ?: next
        if (!playing) play = at(1) ?: play
    }

    /** Returns false if there's nothing cached for [action] or Spotify cancelled it. */
    fun send(context: Context, action: String): Boolean {
        val intent = when (action) {
            PlayerControls.ACTION_PLAY_PAUSE -> play
            PlayerControls.ACTION_NEXT -> next
            PlayerControls.ACTION_PREVIOUS -> previous
            else -> null
        } ?: return false
        return try {
            // Passes on the widget tap's permission to start from the background, so Spotify
            // may start its playback service.
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            intent.send(context, 0, null, null, null, null, options)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }
}
