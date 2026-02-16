package com.astrolabs.gripmaxxer.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.WORKOUT.name) }

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
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Gripmaxxer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Workout tracker and planner for camera-based reps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MainTabSelector(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )

        when (selectedTab) {
            MainTab.WORKOUT.name -> WorkoutTabContent(
                uiState = uiState,
                notificationsEnabled = notificationsEnabled,
                onStart = viewModel::startMonitoring,
                onStop = viewModel::stopMonitoring,
                onModeSelected = viewModel::setSelectedExerciseMode,
                onMediaToggle = viewModel::setMediaControlEnabled,
                onPreviewToggle = viewModel::setShowCameraPreview,
                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenNotificationAccess = openNotificationAccessSettings,
                onOpenOverlaySettings = openOverlaySettings,
            )

            MainTab.PLANNER.name -> PlannerTabContent(
                selectedMode = uiState.settings.selectedExerciseMode,
                onUsePlan = viewModel::setSelectedExerciseMode,
                onStartPlan = viewModel::startMonitoringWithMode,
            )

            MainTab.HISTORY.name -> HistoryTabContent(history = uiState.history)
        }
    }
}

@Composable
private fun WorkoutTabContent(
    uiState: MainUiState,
    notificationsEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onModeSelected: (ExerciseMode) -> Unit,
    onMediaToggle: (Boolean) -> Unit,
    onPreviewToggle: (Boolean) -> Unit,
    onRequestCamera: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (uiState.monitoring.serviceRunning) "Workout in progress" else "Ready to train",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Exercise: ${uiState.settings.selectedExerciseMode.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricTile(
                    label = "Reps",
                    value = uiState.monitoring.reps.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "Active Time",
                    value = formatDuration(uiState.monitoring.elapsedHangMs),
                    modifier = Modifier.weight(1f),
                )
            }
            if (uiState.monitoring.serviceRunning) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Finish Workout")
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = uiState.permissions.cameraGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Workout")
                }
                if (!uiState.permissions.cameraGranted) {
                    OutlinedButton(
                        onClick = onRequestCamera,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Grant Camera Access")
                    }
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
                text = "Exercise Library",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ExerciseModeSelector(
                selectedMode = uiState.settings.selectedExerciseMode,
                onSelect = onModeSelected,
            )
            Text(
                text = "Framing: ${framingHintForMode(uiState.settings.selectedExerciseMode)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = "Setup checklist",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ChecklistLine(label = "Camera permission", ready = uiState.permissions.cameraGranted)
            if (!uiState.permissions.cameraGranted) {
                OutlinedButton(
                    onClick = onRequestCamera,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enable Camera")
                }
            }
            ChecklistLine(
                label = "Notification access",
                ready = uiState.permissions.notificationAccessEnabled,
            )
            if (!uiState.permissions.notificationAccessEnabled) {
                OutlinedButton(
                    onClick = onOpenNotificationAccess,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enable Notification Access")
                }
            }
            ChecklistLine(label = "Overlay permission", ready = uiState.permissions.overlayPermissionGranted)
            if (!uiState.permissions.overlayPermissionGranted) {
                OutlinedButton(
                    onClick = onOpenOverlaySettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enable Overlay")
                }
            }
            ChecklistLine(label = "System notifications", ready = notificationsEnabled)
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Workout options",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SettingToggle(
                label = "Enable media play/pause",
                checked = uiState.settings.mediaControlEnabled,
                onToggle = onMediaToggle,
            )
            SettingToggle(
                label = "Show live camera feedback",
                checked = uiState.showCameraPreview,
                onToggle = onPreviewToggle,
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
                    text = "Live camera feedback",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val frame = uiState.cameraPreviewFrame
                if (frame != null) {
                    CameraPreviewWithTracking(frame = frame)
                } else {
                    Text(
                        text = "Start a workout to stream camera tracking feedback.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannerTabContent(
    selectedMode: ExerciseMode,
    onUsePlan: (ExerciseMode) -> Unit,
    onStartPlan: (ExerciseMode) -> Unit,
) {
    val templates = remember {
        listOf(
            RoutineTemplate(
                title = "Upper Body Strength",
                subtitle = "Pull-up + Push-up focus",
                exercises = listOf(ExerciseMode.PULL_UP, ExerciseMode.PUSH_UP, ExerciseMode.DIP),
            ),
            RoutineTemplate(
                title = "Leg Day",
                subtitle = "Build volume and control",
                exercises = listOf(ExerciseMode.SQUAT),
            ),
            RoutineTemplate(
                title = "Press Focus",
                subtitle = "Bench and support work",
                exercises = listOf(ExerciseMode.BENCH_PRESS, ExerciseMode.DIP),
            ),
            RoutineTemplate(
                title = "Bodyweight Circuit",
                subtitle = "Minimal equipment plan",
                exercises = listOf(ExerciseMode.PUSH_UP, ExerciseMode.SQUAT, ExerciseMode.PULL_UP),
            ),
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Planner",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Pick a routine template, then start tracking from the first exercise.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Currently selected: ${selectedMode.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    templates.forEach { template ->
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = template.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Exercises: ${template.exercises.joinToString(" • ") { it.label }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onUsePlan(template.exercises.first()) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Use Plan")
                    }
                    Button(
                        onClick = { onStartPlan(template.exercises.first()) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Start Plan")
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabSelector(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
) {
    val tabs = listOf(
        MainTab.WORKOUT,
        MainTab.PLANNER,
        MainTab.HISTORY,
    )
    val selectedIndex = tabs.indexOfFirst { it.name == selectedTab }.coerceAtLeast(0)

    TabRow(selectedTabIndex = selectedIndex) {
        tabs.forEach { tab ->
            Tab(
                selected = selectedTab == tab.name,
                onClick = { onTabSelected(tab.name) },
                text = { Text(tab.label) },
            )
        }
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
                text = "Training history",
                style = MaterialTheme.typography.titleLarge,
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
                MetricTile(
                    label = "Sessions",
                    value = history.sessions.size.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider()
            if (history.sessions.isEmpty()) {
                Text(
                    text = "No workouts yet. Start your first session from Workout or Planner.",
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
private fun ChecklistLine(label: String, ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = if (ready) "Ready" else "Required",
            color = if (ready) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
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
            color = MaterialTheme.colorScheme.onSurface,
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
            .background(Color.Black),
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

private data class RoutineTemplate(
    val title: String,
    val subtitle: String,
    val exercises: List<ExerciseMode>,
)

private enum class MainTab(val label: String) {
    WORKOUT("Workout"),
    PLANNER("Planner"),
    HISTORY("History"),
}
