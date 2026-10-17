package dev.davide.miniplayer

import android.appwidget.AppWidgetProvider
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The app's only screen: notification access, the two widgets with a live preview, and
 * buttons to pin them. Stateless; [SetupActivity] owns the state.
 *
 * [onAddWidget] is null when the launcher can't pin widgets from an app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    granted: Boolean,
    track: TrackStore.State?,
    cover: Bitmap?,
    onGrantAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onAddWidget: ((Class<out AppWidgetProvider>) -> Unit)?,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scroll)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccessCard(granted, onGrantAccess, onOpenAppInfo)

            Text(
                stringResource(R.string.widgets_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
            WidgetCard(
                name = R.string.classic_widget_name,
                description = R.string.classic_widget_description,
                layout = R.layout.widget_classic,
                previewHeight = 100.dp,
                track = track,
                cover = cover,
                onAdd = onAddWidget?.let { add -> { add(ClassicPlayerWidget::class.java) } },
            )
            val rounded = remember(cover) { cover?.let(WidgetRenderer::roundCorners) }
            WidgetCard(
                name = R.string.widget_name,
                description = R.string.widget_description,
                layout = R.layout.widget_player,
                previewHeight = 72.dp,
                track = track,
                cover = rounded,
                onAdd = onAddWidget?.let { add -> { add(PlayerWidget::class.java) } },
            )
            if (track?.hasTrack != true) {
                Text(
                    stringResource(R.string.no_track_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AccessCard(granted: Boolean, onGrantAccess: () -> Unit, onOpenAppInfo: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (granted) colors.primaryContainer else colors.errorContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_warning),
                    contentDescription = null,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (granted) R.string.status_granted else R.string.status_missing),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(stringResource(R.string.setup_explainer), style = MaterialTheme.typography.bodyMedium)
            if (granted) {
                // The theme's primary color can match the card (e.g. monochrome Material You),
                // so the outline and label follow the card's own text color.
                val content = LocalContentColor.current
                OutlinedButton(
                    onClick = onGrantAccess,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
                    border = BorderStroke(1.dp, content.copy(alpha = 0.5f)),
                ) { Text(stringResource(R.string.manage_access)) }
            } else {
                Button(onClick = onGrantAccess) { Text(stringResource(R.string.grant_access)) }
                Text(stringResource(R.string.restricted_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.open_app_info)) }
            }
        }
    }
}

@Composable
private fun WidgetCard(
    @StringRes name: Int,
    @StringRes description: Int,
    @LayoutRes layout: Int,
    previewHeight: Dp,
    track: TrackStore.State?,
    cover: Bitmap?,
    onAdd: (() -> Unit)?,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The real widget layout, on a strip that stands in for the home screen.
            AndroidView(
                factory = { context -> LayoutInflater.from(context).inflate(layout, FrameLayout(context), false) },
                update = { view -> fillPreview(view, track, cover) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(12.dp)
                    .height(previewHeight),
            )
            Column {
                Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onAdd != null) {
                FilledTonalButton(onClick = onAdd, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_to_home))
                }
            }
        }
    }
}

/** Shows the last track in a widget layout, or the layout's placeholders. */
private fun fillPreview(view: android.view.View, track: TrackStore.State?, cover: Bitmap?) {
    val context = view.context
    val hasTrack = track?.hasTrack == true
    view.findViewById<TextView>(R.id.title).text =
        if (hasTrack) track?.title else context.getString(R.string.preview_title)
    view.findViewById<TextView>(R.id.artist).text =
        if (hasTrack) track?.artist.orEmpty() else context.getString(R.string.preview_artist)
    val coverView = view.findViewById<ImageView>(R.id.cover)
    if (cover != null) coverView.setImageBitmap(cover) else coverView.setImageResource(R.drawable.ic_cover_placeholder)
}

@Preview
@Composable
private fun SetupScreenPreview() {
    MiniPlayerTheme {
        SetupScreen(
            granted = false,
            track = null,
            cover = null,
            onGrantAccess = {},
            onOpenAppInfo = {},
            onAddWidget = {},
        )
    }
}
