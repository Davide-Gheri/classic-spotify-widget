package dev.davide.miniplayer

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

open class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRenderer.updateAll(context, force = true)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        // Resized: re-layout for the new width.
        WidgetRenderer.updateAll(context, force = true)
    }
}

/** Two-row widget in the style of Spotify's old one. [WidgetRenderer] picks the layout. */
class ClassicPlayerWidget : PlayerWidget()
