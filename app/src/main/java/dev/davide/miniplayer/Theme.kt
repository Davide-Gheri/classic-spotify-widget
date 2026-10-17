package dev.davide.miniplayer

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Material You: colors from the wallpaper on Android 12+, the classic widget's blue before. */
@Composable
fun MiniPlayerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFB1C5FF), primaryContainer = Color(0xFF2D4677))
        else -> lightColorScheme(primary = Color(0xFF465D90), primaryContainer = Color(0xFFD9E2FF))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
