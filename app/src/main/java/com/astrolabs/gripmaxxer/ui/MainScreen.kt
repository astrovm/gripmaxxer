package com.astrolabs.gripmaxxer.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astrolabs.gripmaxxer.datastore.WorkoutHistory
import com.astrolabs.gripmaxxer.datastore.WorkoutSession
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import com.astrolabs.gripmaxxer.service.DebugPreviewFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.LIVE.name) }

    DisposableEffect(uiState.monitoring.serviceRunning, view) {
        view.keepScreenOn = uiState.monitoring.serviceRunning
        onDispose {
            view.keepScreenOn = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshPermissionState() },
    )

    val openOverlaySettings = remember {
        {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            context.startActivity(intent)
        }
    }

    val openNotificationAccessSettings = remember {
        {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    val notificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } else {
        true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Gripmaxxer",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Real-time monitoring with selectable exercise rep tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MainTabSelector(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )

        if (selectedTab == MainTab.LIVE.name) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Live Overview",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Selected mode: ${uiState.settings.selectedExerciseMode.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Reps: ${uiState.monitoring.reps}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricTile(
                        label = "Active Time",
                        value = formatDuration(uiState.monitoring.elapsedHangMs),
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Camera FPS",
                        value = uiState.monitoring.cameraFps.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Last Frame",
                        value = formatLastFrameAge(uiState.monitoring.lastFrameAgeMs),
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider()
                StatusLine("Monitoring service", uiState.monitoring.serviceRunning)
                StatusLine("Exercise active", uiState.monitoring.hanging)
                StatusLine("Pose detected", uiState.monitoring.posePresent)
                StatusLine("Notifications enabled", notificationsEnabled)
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Controls",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Button(
                    onClick = { viewModel.startMonitoring() },
                    enabled = uiState.permissions.cameraGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Monitoring")
                }
                OutlinedButton(
                    onClick = { viewModel.stopMonitoring() },
                    enabled = uiState.monitoring.serviceRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop Monitoring")
                }
                Text(
                    text = "Active media source: ${uiState.monitoring.mediaControllerPackage ?: "Not connected"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusLine("Camera permission", uiState.permissions.cameraGranted)
                if (!uiState.permissions.cameraGranted) {
                    Button(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Grant Camera Permission")
                    }
                }
                StatusLine("Notification access", uiState.permissions.notificationAccessEnabled)
                if (!uiState.permissions.notificationAccessEnabled) {
                    Button(
                        onClick = { openNotificationAccessSettings() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open Notification Access Settings")
                    }
                }
                StatusLine("Overlay permission", uiState.permissions.overlayPermissionGranted)
                if (!uiState.permissions.overlayPermissionGranted) {
                    OutlinedButton(
                        onClick = { openOverlaySettings() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Grant Overlay Permission")
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Overlay is shown automatically while monitoring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Camera framing: ${framingHintForMode(uiState.settings.selectedExerciseMode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExerciseModeSelector(
                    selectedMode = uiState.settings.selectedExerciseMode,
                    onSelect = viewModel::setSelectedExerciseMode,
                )
                SettingToggle(
                    label = "Enable media play/pause",
                    checked = uiState.settings.mediaControlEnabled,
                    onToggle = viewModel::setMediaControlEnabled,
                )
                SettingToggle(
                    label = "Show live camera preview",
                    checked = uiState.showCameraPreview,
                    onToggle = viewModel::setShowCameraPreview,
                )
            }
        }

            if (uiState.showCameraPreview) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Live Camera Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val frame = uiState.cameraPreviewFrame
                        if (frame != null) {
                            CameraPreviewWithTracking(frame = frame)
                        } else {
                            Text(
                                text = "Waiting for camera frames. Start monitoring and keep this screen open.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            HistoryTabContent(history = uiState.history)
        }
    }
}

@Composable
private fun MainTabSelector(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
) {
    TabRow(selectedTabIndex = if (selectedTab == MainTab.LIVE.name) 0 else 1) {
        Tab(
            selected = selectedTab == MainTab.LIVE.name,
            onClick = { onTabSelected(MainTab.LIVE.name) },
            text = { Text("Live") },
        )
        Tab(
            selected = selectedTab == MainTab.HISTORY.name,
            onClick = { onTabSelected(MainTab.HISTORY.name) },
            text = { Text("History") },
        )
    }
}

@Composable
private fun HistoryTabContent(history: WorkoutHistory) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "History",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricTile(
                    label = "Max Reps",
                    value = history.maxReps.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "Max Time",
                    value = formatDuration(history.maxActiveMs),
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider()
            if (history.sessions.isEmpty()) {
                Text(
                    text = "No completed sessions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                history.sessions.take(30).forEach { session ->
                    SessionRow(session)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: WorkoutSession) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${session.mode.label} • ${session.reps} reps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatSessionTime(session.completedAtMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatDuration(session.activeMs),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExerciseModeSelector(
    selectedMode: ExerciseMode,
    onSelect: (ExerciseMode) -> Unit,
) {
    val modes = listOf(
        ExerciseMode.PULL_UP,
        ExerciseMode.PUSH_UP,
        ExerciseMode.SQUAT,
        ExerciseMode.BENCH_PRESS,
        ExerciseMode.DIP,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Exercise Mode",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        modes.forEach { mode ->
            val selected = mode == selectedMode
            if (selected) {
                Button(
                    onClick = { onSelect(mode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(mode.label)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(mode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(mode.label)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = if (value) "Ready" else "Needs action",
            color = if (value) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CameraPreviewWithTracking(frame: DebugPreviewFrame) {
    val aspect = frame.bitmap.width.toFloat() / frame.bitmap.height.toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.4f, 2.5f))
            .background(Color.Black)
    ) {
        Image(
            bitmap = frame.bitmap.asImageBitmap(),
            contentDescription = "Camera preview with tracking",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = 5.dp.toPx()
            frame.landmarks.values.forEach { landmark ->
                drawCircle(
                    color = Color.Red,
                    radius = radius,
                    center = Offset(
                        x = (1f - landmark.x).coerceIn(0f, 1f) * size.width,
                        y = landmark.y.coerceIn(0f, 1f) * size.height,
                    ),
                )
            }
        }
    }
}

private fun formatLastFrameAge(lastFrameAgeMs: Long): String {
    return if (lastFrameAgeMs == Long.MAX_VALUE) {
        "No frames"
    } else {
        "${lastFrameAgeMs}ms"
    }
}

private fun formatDuration(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatSessionTime(timestampMs: Long): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.US)
    return formatter.format(Date(timestampMs))
}

private fun framingHintForMode(mode: ExerciseMode): String {
    return when (mode) {
        ExerciseMode.PULL_UP -> "Front bar framing with shoulders and arms visible"
        ExerciseMode.PUSH_UP -> "Front view with shoulders, elbows, and torso visible"
        ExerciseMode.SQUAT -> "Front full-body framing (hips, knees, ankles visible)"
        ExerciseMode.BENCH_PRESS -> "Side profile view focused on shoulder-elbow-wrist chain"
        ExerciseMode.DIP -> "Front upper-body framing with shoulders, elbows, wrists visible"
    }
}

private enum class MainTab {
    LIVE,
    HISTORY,
}
