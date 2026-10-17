package dev.davide.miniplayer

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One screen: grant notification access, add a widget, see what was last seen. */
class SetupActivity : ComponentActivity() {

    // Re-read in onResume, so coming back from Settings refreshes the screen.
    private var granted by mutableStateOf(false)
    private var track by mutableStateOf<TrackStore.State?>(null)
    private var cover by mutableStateOf<Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val widgets = AppWidgetManager.getInstance(this)
        val pin: ((Class<*>) -> Unit)? =
            if (widgets.isRequestPinAppWidgetSupported) {
                { provider -> widgets.requestPinAppWidget(ComponentName(this, provider), null, null) }
            } else {
                null
            }

        setContent {
            MiniPlayerTheme {
                SetupScreen(
                    granted = granted,
                    track = track,
                    cover = cover,
                    onGrantAccess = ::openListenerSettings,
                    onOpenAppInfo = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                        )
                    },
                    onAddWidget = pin,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted = Access.isGranted(this)
        val state = TrackStore.load(this)
        if (state != track) {
            track = state
            cover = if (state.hasCover) TrackStore.loadCover(this) else null
        }

        // Coming back from Settings: refresh the widget out of (or into) setup mode.
        WidgetRenderer.updateAll(this, force = true)
    }

    private fun openListenerSettings() {
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Straight to this app's toggle where the device supports it.
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                Access.listenerComponent(this).flattenToString(),
            )
            try {
                startActivity(detail)
                return
            } catch (e: ActivityNotFoundException) {
                // Fall through to the full list.
            }
        }
        startActivity(list)
    }
}
