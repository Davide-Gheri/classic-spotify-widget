package com.classicspotifywidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.RemoteViews

/** Builds the widget from [TrackStore]. Main thread only. */
object WidgetRenderer {

    // Width breakpoints in dp. Launchers report the portrait width as MIN_WIDTH.
    // 4x1 and 3x1 show everything; narrower drops the cover, then the text.
    private const val MIN_WIDTH_FOR_COVER = 220
    private const val MIN_WIDTH_FOR_TEXT = 150

    private const val REQ_SETUP = 1
    private const val REQ_OPEN_SPOTIFY = 2

    private var lastSignature: Any? = null
    private var cachedCover: Pair<Long, Cover>? = null

    /** The slim widget shows the cover with rounded corners, the classic one square. */
    private class Cover(val square: Bitmap, val rounded: Bitmap)

    fun updateAll(context: Context, force: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        val slimIds = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        val classicIds = manager.getAppWidgetIds(ComponentName(context, ClassicPlayerWidget::class.java))
        if (slimIds.isEmpty() && classicIds.isEmpty()) return

        val state = TrackStore.load(context)
        val granted = Access.isGranted(context)
        val signature = listOf(state, granted, slimIds.toList(), classicIds.toList())
        if (!force && signature == lastSignature) return
        lastSignature = signature

        val cover = if (granted && state.hasCover) cover(context, state.coverVersion) else null
        for (id in slimIds) {
            val views = build(context, R.layout.widget_player, state, granted, cover?.rounded)
            if (granted) fitWidth(views, manager, id)
            manager.updateAppWidget(id, views)
        }
        for (id in classicIds) {
            manager.updateAppWidget(id, build(context, R.layout.widget_classic, state, granted, cover?.square))
        }
    }

    private fun build(
        context: Context,
        layout: Int,
        state: TrackStore.State,
        granted: Boolean,
        cover: Bitmap?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        val setup = activity(
            context, REQ_SETUP,
            Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        if (!granted) {
            views.setImageViewResource(R.id.cover, R.drawable.ic_cover_placeholder)
            views.setTextViewText(R.id.title, context.getString(R.string.setup_title))
            views.setTextViewText(R.id.artist, context.getString(R.string.setup_subtitle))
            views.setViewVisibility(R.id.controls, View.GONE)
            views.setOnClickPendingIntent(R.id.root, setup)
            return views
        }

        if (state.hasTrack) {
            views.setTextViewText(R.id.title, state.title)
            views.setTextViewText(R.id.artist, state.artist.orEmpty())
        } else {
            views.setTextViewText(R.id.title, context.getString(R.string.idle_title))
            views.setTextViewText(R.id.artist, context.getString(R.string.idle_subtitle))
        }

        if (cover != null) {
            views.setImageViewBitmap(R.id.cover, cover)
        } else {
            views.setImageViewResource(R.id.cover, R.drawable.ic_cover_placeholder)
        }

        val playing = state.sessionAlive && state.playing
        views.setImageViewResource(R.id.play_pause, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        views.setContentDescription(
            R.id.play_pause,
            context.getString(if (playing) R.string.pause else R.string.play),
        )

        val openSpotify = Spotify.launchIntent(context)?.let { activity(context, REQ_OPEN_SPOTIFY, it) } ?: setup
        views.setOnClickPendingIntent(R.id.root, openSpotify)
        views.setOnClickPendingIntent(R.id.cover, openSpotify)
        views.setOnClickPendingIntent(R.id.text_area, openSpotify)

        // Spotify not running and another app was the last to play: a media key would
        // wake that other app, so the play button opens Spotify instead.
        val playTarget =
            if (!state.sessionAlive && state.lastPlayer != Spotify.PACKAGE) openSpotify
            else control(context, PlayerControls.ACTION_PLAY_PAUSE)
        views.setOnClickPendingIntent(R.id.play_pause, playTarget)
        views.setOnClickPendingIntent(R.id.prev, control(context, PlayerControls.ACTION_PREVIOUS))
        views.setOnClickPendingIntent(R.id.next, control(context, PlayerControls.ACTION_NEXT))
        return views
    }

    /** Slim widget only: drops the cover, then the text, as the widget gets narrower. */
    private fun fitWidth(views: RemoteViews, manager: AppWidgetManager, appWidgetId: Int) {
        val width = manager.getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            .takeIf { it > 0 } ?: Int.MAX_VALUE
        val showCover = width >= MIN_WIDTH_FOR_COVER
        val showText = width >= MIN_WIDTH_FOR_TEXT
        views.setViewVisibility(R.id.cover, if (showCover) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.text_area, if (showText) View.VISIBLE else View.GONE)
        // Without the text column, spacers on both sides center the buttons.
        val spacers = if (showText) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.spacer_start, spacers)
        views.setViewVisibility(R.id.spacer_end, spacers)
    }

    private fun activity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun control(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, ControlReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun cover(context: Context, version: Long): Cover? {
        cachedCover?.let { (cachedVersion, cover) -> if (cachedVersion == version) return cover }
        val square = TrackStore.loadCover(context) ?: return null
        val cover = Cover(square, roundCorners(square))
        cachedCover = version to cover
        return cover
    }

    /** Also used by the setup screen's preview of the slim widget. */
    fun roundCorners(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = source.width * 0.08f
        Canvas(output).drawRoundRect(
            RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), radius, radius, paint,
        )
        return output
    }
}
