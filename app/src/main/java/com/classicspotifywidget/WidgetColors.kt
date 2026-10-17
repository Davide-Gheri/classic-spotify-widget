package com.classicspotifywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Widget background colors taken from the album cover, as Spotify's own widget does: the
 * cover's dominant color, darkened until the widget's white text stays readable.
 *
 * Each layout's backgrounds are ImageViews tinted with setColorFilter, the only way to
 * recolor a rounded shape in a widget on every Android version the app supports.
 */
object WidgetColors {

    /** WCAG AA contrast for normal text. */
    private const val MIN_CONTRAST = 4.5

    /** The classic controls bar is this much darker than the band, as in Spotify's original. */
    private const val BAR_LIGHTNESS = 0.42f

    /** The color for a cover, or null if Palette finds nothing. Call off the main thread. */
    fun fromCover(cover: Bitmap): Int? {
        val dominant = Palette.from(cover).generate().dominantSwatch?.rgb ?: return null
        return readableUnderWhite(dominant)
    }

    /** Background view id → color for [layout], with [coverColor] or the layout's fixed colors. */
    fun forLayout(context: Context, layout: Int, coverColor: Int?): Map<Int, Int> = when (layout) {
        R.layout.widget_classic -> mapOf(
            R.id.header_background to (coverColor ?: context.getColor(R.color.classic_header)),
            R.id.background to (coverColor?.let(::darker) ?: context.getColor(R.color.classic_controls)),
        )
        else -> mapOf(R.id.background to (coverColor ?: context.getColor(R.color.widget_background)))
    }

    private fun readableUnderWhite(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        var result = color
        while (ColorUtils.calculateContrast(Color.WHITE, result) < MIN_CONTRAST && hsl[2] > 0f) {
            hsl[2] = (hsl[2] - 0.02f).coerceAtLeast(0f)
            result = ColorUtils.HSLToColor(hsl)
        }
        return result
    }

    private fun darker(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] *= BAR_LIGHTNESS
        return ColorUtils.HSLToColor(hsl)
    }
}
