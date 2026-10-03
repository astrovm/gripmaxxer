package com.astrovm.gripmaxxer.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astrovm.gripmaxxer.BuildConfig
import com.astrovm.gripmaxxer.data.Accent
import com.astrovm.gripmaxxer.data.ExerciseStats
import com.astrovm.gripmaxxer.data.Settings
import com.astrovm.gripmaxxer.ui.theme.accentColor

private const val SOURCE_URL = "https://github.com/astrovm/gripmaxxer"

@Composable
fun ProfileScreen(viewModel: MainViewModel, settings: Settings) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Profile") }
        if (stats.isNotEmpty()) {
            item { SectionTitle("Personal bests") }
            items(stats, key = { it.exercise.name }) { StatsRow(it) }
        }
        item { SectionTitle("While you train", Modifier.padding(top = 8.dp)) }
        // These only show as on once Android allows them. Turning one on asks for the access it needs.
        item {
            ToggleRow(
                title = "Play and pause media",
                detail = "Plays while you're in a set, pauses when you stop",
                checked = settings.mediaControl && access.notifications,
                onChange = { on ->
                    viewModel.setMediaControl(on)
                    if (on && !access.notifications) {
                        context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                },
            )
        }
        item {
            ToggleRow(
                title = "Floating timer",
                detail = "Shows over other apps",
                checked = settings.overlay && access.overlay,
                onChange = { on ->
                    viewModel.setOverlay(on)
                    if (on && !access.overlay) {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                },
            )
        }
        item { ToggleRow("Beep on each rep", null, settings.repSound, viewModel::setRepSound) }
        item { ToggleRow("Read out hold time", "Every 10 seconds", settings.voiceCues, viewModel::setVoiceCues) }
        item { SectionTitle("Color", Modifier.padding(top = 8.dp)) }
        item { AccentPicker(settings.accent, viewModel::setAccent) }
        item {
            Text(
                "Gripmaxxer ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .clickable { uriHandler.openUri(SOURCE_URL) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StatsRow(stats: ExerciseStats) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stats.exercise.label, style = MaterialTheme.typography.titleMedium)
            val total = if (stats.exercise.isHold) formatDuration(stats.totalHoldMs) else formatReps(stats.totalReps)
            val sets = if (stats.sets == 1) "1 set" else "${stats.sets} sets"
            Text(
                "$sets, $total total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (stats.exercise.isHold) formatDuration(stats.bestHoldMs) else formatReps(stats.bestReps),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ToggleRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The whole row toggles, so the switch itself only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun AccentPicker(selected: Accent, onSelect: (Accent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        for (accent in Accent.entries) {
            val color = accentColor(accent)
            val name = accent.name.lowercase().replaceFirstChar(Char::uppercase)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, if (accent == selected) Color.White else Color.Transparent, CircleShape)
                    .selectable(selected = accent == selected) { onSelect(accent) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                if (accent == selected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.Black)
                }
            }
        }
    }
}
