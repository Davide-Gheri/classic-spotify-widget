package com.classicspotifywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import java.io.File
import java.io.FileOutputStream

/**
 * Persists the last Spotify track and playback flags, so the widget keeps
 * showing them after Spotify (or this app) has been killed.
 */
object TrackStore {

    data class State(
        val trackKey: String?,
        val title: String?,
        val artist: String?,
        val coverKey: String?,
        val coverVersion: Long,
        val sessionAlive: Boolean,
        val playing: Boolean,
        val lastPlayer: String?,
    ) {
        val hasTrack: Boolean get() = !title.isNullOrBlank()
        val hasCover: Boolean get() = trackKey != null && trackKey == coverKey
    }

    private const val PREFS = "player_state"
    private const val K_TRACK = "track_key"
    private const val K_TITLE = "title"
    private const val K_ARTIST = "artist"
    private const val K_COVER_KEY = "cover_key"
    private const val K_COVER_VERSION = "cover_version"
    private const val K_ALIVE = "session_alive"
    private const val K_PLAYING = "playing"
    private const val K_LAST_PLAYER = "last_player"

    private const val COVER_FILE = "cover.jpg"
    private const val COVER_PX = 320

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): State {
        val p = prefs(context)
        return State(
            trackKey = p.getString(K_TRACK, null),
            title = p.getString(K_TITLE, null),
            artist = p.getString(K_ARTIST, null),
            coverKey = p.getString(K_COVER_KEY, null),
            coverVersion = p.getLong(K_COVER_VERSION, 0L),
            sessionAlive = p.getBoolean(K_ALIVE, false),
            playing = p.getBoolean(K_PLAYING, false),
            lastPlayer = p.getString(K_LAST_PLAYER, null),
        )
    }

    fun titleOf(metadata: MediaMetadata): String? =
        firstNonBlank(metadata, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)

    fun artistOf(metadata: MediaMetadata): String? =
        firstNonBlank(
            metadata,
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
        )

    /** Identity of the track, used to match cover art to the metadata it belongs to. */
    fun keyOf(metadata: MediaMetadata): String? {
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrBlank()) return mediaId
        val title = titleOf(metadata) ?: return null
        return listOf(title, artistOf(metadata), metadata.getString(MediaMetadata.METADATA_KEY_ALBUM))
            .joinToString("|")
    }

    /** Saves title and artist. Empty metadata is ignored so the last real track is kept. */
    fun saveMetadata(context: Context, metadata: MediaMetadata) {
        val title = titleOf(metadata) ?: return
        val key = keyOf(metadata) ?: return
        prefs(context).edit()
            .putString(K_TRACK, key)
            .putString(K_TITLE, title)
            .putString(K_ARTIST, artistOf(metadata))
            .apply()
    }

    fun setPlayback(context: Context, alive: Boolean, playing: Boolean) {
        prefs(context).edit()
            .putBoolean(K_ALIVE, alive)
            .putBoolean(K_PLAYING, playing)
            .apply()
    }

    /** Records the app that most recently started playing. Returns true if it changed. */
    fun setLastPlayer(context: Context, packageName: String): Boolean {
        val p = prefs(context)
        if (p.getString(K_LAST_PLAYER, null) == packageName) return false
        p.edit().putString(K_LAST_PLAYER, packageName).apply()
        return true
    }

    /** Center-crops, downscales and writes the cover. Call off the main thread. */
    fun saveCover(context: Context, key: String, source: Bitmap) {
        val software =
            if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
        val square = centerCropSquare(software, COVER_PX)
        val target = File(context.filesDir, COVER_FILE)
        val tmp = File(context.filesDir, "$COVER_FILE.tmp")
        FileOutputStream(tmp).use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!tmp.renameTo(target)) return
        prefs(context).edit()
            .putString(K_COVER_KEY, key)
            .putLong(K_COVER_VERSION, System.currentTimeMillis())
            .apply()
    }

    fun loadCover(context: Context): Bitmap? {
        val file = File(context.filesDir, COVER_FILE)
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    private fun firstNonBlank(metadata: MediaMetadata, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> metadata.getString(key)?.takeIf { it.isNotBlank() } }

    private fun centerCropSquare(source: Bitmap, maxSide: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(
            source, (source.width - side) / 2, (source.height - side) / 2, side, side,
        )
        return if (side > maxSide) Bitmap.createScaledBitmap(cropped, maxSide, maxSide, true) else cropped
    }
}
