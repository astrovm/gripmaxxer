package com.astrolabs.gripmaxxer.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.Checkbox as M3Checkbox
import androidx.compose.material3.ElevatedCard as M3ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar as M3NavigationBar
import androidx.compose.material3.NavigationBarItem as M3NavigationBarItem
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch as M3Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.astrolabs.gripmaxxer.datastore.ColorPalette
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import com.astrolabs.gripmaxxer.service.DebugPreviewFrame
import com.astrolabs.gripmaxxer.ui.theme.LocalIsWindows98Theme
import com.astrolabs.gripmaxxer.ui.theme.White
import com.astrolabs.gripmaxxer.ui.theme.Win98Surface
import com.astrolabs.gripmaxxer.ui.theme.Win98SurfaceVariant
import com.astrolabs.gripmaxxer.ui.theme.win98RaisedBorder
import com.astrolabs.gripmaxxer.ui.theme.win98SunkenBorder
import com.astrolabs.gripmaxxer.workout.CameraTrackableModes
import com.astrolabs.gripmaxxer.workout.CompletedWorkoutDetail
import com.astrolabs.gripmaxxer.workout.WorkoutFeedItem
import com.astrolabs.gripmaxxer.workout.WorkoutSetState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(uiState.monitoring.serviceRunning, view) {
        view.keepScreenOn = uiState.monitoring.serviceRunning
        onDispose {
            view.keepScreenOn = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshPermissionState() },
    )

    val openOverlaySettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        context.startActivity(intent)
    }

    val openNotificationAccessSettings: () -> Unit = {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    LaunchedEffect(uiState.workoutMessage) {
        val message = uiState.workoutMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearWorkoutMessage()
    }

    val inFullscreenTracker = uiState.selectedTab == RootTab.WORKOUT && uiState.workoutSession != null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!inFullscreenTracker) {
                NavigationBar {
                    NavigationBarItem(
                        selected = uiState.selectedTab == RootTab.LOG,
                        onClick = { viewModel.selectTab(RootTab.LOG) },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = "Log") },
                        label = { Text("Log") },
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == RootTab.WORKOUT,
                        onClick = { viewModel.selectTab(RootTab.WORKOUT) },
                        icon = { Icon(Icons.Outlined.FitnessCenter, contentDescription = "Workout") },
                        label = { Text("Workout") },
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == RootTab.PROFILE,
                        onClick = { viewModel.selectTab(RootTab.PROFILE) },
                        icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = "Profile") },
                        label = { Text("Profile") },
                    )
                }
            }
        },
    ) { padding ->
        if (inFullscreenTracker) {
            WorkoutTrackerScreen(
                uiState = uiState,
                onPause = viewModel::pauseWorkout,
                onResume = viewModel::resumeWorkout,
                onEnd = viewModel::endWorkout,
                onEditSet = viewModel::editActiveSet,
                onDeleteSet = viewModel::deleteActiveSet,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (uiState.selectedTab) {
                    RootTab.LOG -> LogTab(
                        workouts = uiState.completedWorkouts,
                        selectedDetail = uiState.selectedWorkoutDetail,
                        onOpenDetail = viewModel::openWorkoutDetail,
                        onCloseDetail = viewModel::closeWorkoutDetail,
                        onEditSet = viewModel::editDetailSet,
                        onDeleteSet = viewModel::deleteDetailSet,
                    )

                    RootTab.WORKOUT -> WorkoutStartTab(
                        selectedMode = uiState.settings.selectedExerciseMode,
                        onSelectMode = viewModel::setSelectedExerciseMode,
                        onStart = { viewModel.startWorkout(uiState.settings.selectedExerciseMode) },
                        cameraGranted = uiState.permissions.cameraGranted,
                        notificationAccessEnabled = uiState.permissions.notificationAccessEnabled,
                        overlayPermissionGranted = uiState.permissions.overlayPermissionGranted,
                        mediaControlEnabled = uiState.settings.mediaControlEnabled,
                        overlayEnabled = uiState.settings.overlayEnabled,
                        onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenNotificationAccess = openNotificationAccessSettings,
                        onOpenOverlaySettings = openOverlaySettings,
                    )

                    RootTab.PROFILE -> ProfileTab(
                        uiState = uiState,
                        onMediaToggle = viewModel::setMediaControlEnabled,
                        onOverlayToggle = viewModel::setOverlayEnabled,
                        onPreviewToggle = viewModel::setShowCameraPreview,
                        onPaletteSelect = viewModel::setColorPalette,
                    )
                }
            }
        }
    }
}

@Composable
private fun ElevatedCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    content: @Composable () -> Unit,
) {
    if (LocalIsWindows98Theme.current) {
        Box(
            modifier = modifier
                .background(Win98Surface)
                .win98RaisedBorder(),
        ) {
            content()
        }
    } else {
        M3ElevatedCard(
            modifier = modifier,
            colors = colors,
            content = { content() },
        )
    }
}

@Composable
private fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalIsWindows98Theme.current) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        Surface(
            modifier = modifier
                .then(if (pressed) Modifier.win98SunkenBorder() else Modifier.win98RaisedBorder())
                .alpha(if (enabled) 1f else 0.6f),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RectangleShape,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    } else {
        M3Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}

@Composable
private fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalIsWindows98Theme.current) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        Surface(
            modifier = modifier
                .then(if (pressed) Modifier.win98SunkenBorder() else Modifier.win98RaisedBorder())
                .alpha(if (enabled) 1f else 0.6f),
            color = Win98SurfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RectangleShape,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    } else {
        M3OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}

@Composable
private fun NavigationBar(
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalIsWindows98Theme.current) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Win98Surface)
                .win98RaisedBorder()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    } else {
        M3NavigationBar(content = content)
    }
}

@Composable
private fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable (() -> Unit)? = null,
) {
    if (LocalIsWindows98Theme.current) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        Column(
            modifier = Modifier
                .weight(1f)
                .background(if (selected) Win98SurfaceVariant else Win98Surface)
                .then(
                    if (selected || pressed) {
                        Modifier.win98SunkenBorder()
                    } else {
                        Modifier.win98RaisedBorder()
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            icon()
            label?.invoke()
        }
    } else {
        M3NavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = icon,
            label = label,
        )
    }
}

@Composable
private fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    if (LocalIsWindows98Theme.current) {
        M3OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .background(White)
                .win98SunkenBorder(),
            label = label,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                disabledContainerColor = White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
            ),
        )
    } else {
        M3OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
        )
    }
}

@Composable
private fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (LocalIsWindows98Theme.current) {
        M3Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    } else {
        M3Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ScreenHeader(title: String) {
    if (LocalIsWindows98Theme.current) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .win98RaisedBorder()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun LogTab(
    workouts: List<WorkoutFeedItem>,
    selectedDetail: CompletedWorkoutDetail?,
    onOpenDetail: (Long) -> Unit,
    onCloseDetail: () -> Unit,
    onEditSet: (Long, Int, Long) -> Unit,
    onDeleteSet: (Long) -> Unit,
) {
    ScreenHeader("Log")
    WeeklyActivityBar(workouts = workouts)

    if (workouts.isEmpty()) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "No completed sessions yet.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        workouts.forEach { workout ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = workout.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${workout.mode.label} • ${formatSessionTime(workout.completedAtMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${formatDuration(workout.durationMs)} • ${workout.setCount} set(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onOpenDetail(workout.workoutId) }) {
                        Text("View session")
                    }
                }
            }
        }
    }

    if (selectedDetail != null) {
        SessionDetailDialog(
            detail = selectedDetail,
            onDismiss = onCloseDetail,
            onEditSet = onEditSet,
            onDeleteSet = onDeleteSet,
        )
    }
}

@Composable
private fun WeeklyActivityBar(workouts: List<WorkoutFeedItem>) {
    val isWindows98 = LocalIsWindows98Theme.current
    val zoneId = ZoneId.systemDefault()
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE", Locale.US) }
    val today = LocalDate.now(zoneId)
    val lastSevenDays = remember(today) {
        (6 downTo 0).map { offset -> today.minusDays(offset.toLong()) }
    }
    val exercisedDays = remember(workouts, zoneId) {
        workouts.mapTo(hashSetOf()) { item ->
            Instant.ofEpochMilli(item.completedAtMs).atZone(zoneId).toLocalDate()
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            lastSevenDays.forEach { day ->
                val exercised = exercisedDays.contains(day)
                Column(
                    modifier = if (isWindows98) {
                        Modifier
                            .weight(1f)
                            .background(
                                color = if (exercised) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Win98SurfaceVariant
                                },
                            )
                            .win98SunkenBorder()
                            .padding(vertical = 8.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .background(
                                color = if (exercised) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(vertical = 8.dp)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = day.format(dayFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (exercised) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (exercised) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutStartTab(
    selectedMode: ExerciseMode,
    onSelectMode: (ExerciseMode) -> Unit,
    onStart: () -> Unit,
    cameraGranted: Boolean,
    notificationAccessEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    mediaControlEnabled: Boolean,
    overlayEnabled: Boolean,
    onRequestCamera: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
) {
    val notificationAccessRequired = mediaControlEnabled && !notificationAccessEnabled
    val overlayPermissionRequired = overlayEnabled && !overlayPermissionGranted
    val canStart = cameraGranted && !notificationAccessRequired && !overlayPermissionRequired

    ScreenHeader("Workout")

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Select exercise", style = MaterialTheme.typography.titleMedium)

            CameraTrackableModes.chunked(2).forEach { rowModes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowModes.forEach { mode ->
                        val selected = selectedMode == mode
                        if (selected) {
                            Button(
                                onClick = { onSelectMode(mode) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(mode.label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSelectMode(mode) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                    if (rowModes.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (!cameraGranted) {
                OutlinedButton(
                    onClick = onRequestCamera,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Grant camera permission")
                }
            }

            if (notificationAccessRequired || overlayPermissionRequired) {
                Text(
                    text = "Required for your enabled settings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (notificationAccessRequired) {
                    ChecklistLine("Notification access", false)
                    OutlinedButton(
                        onClick = onOpenNotificationAccess,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open notification access")
                    }
                }

                if (overlayPermissionRequired) {
                    ChecklistLine("Overlay permission", false)
                    OutlinedButton(
                        onClick = onOpenOverlaySettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open overlay settings")
                    }
                }
            }

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = canStart,
            ) {
                Text("Start Workout")
            }
        }
    }
}

@Composable
private fun WorkoutTrackerScreen(
    uiState: MainUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onEditSet: (Long, Int, Long) -> Unit,
    onDeleteSet: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = uiState.workoutSession ?: return
    var showSetEditor by rememberSaveable(session.workoutId) { mutableStateOf(false) }

    Box(modifier = modifier.background(Color.Black)) {
        if (uiState.showCameraPreview && uiState.cameraPreviewFrame != null) {
            CameraPreviewWithTracking(
                frame = uiState.cameraPreviewFrame,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (uiState.showCameraPreview) "Waiting for camera..." else "Camera preview is off",
                    color = Color.White,
                )
            }
        }

        ElevatedCard(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = Color(0xCC101216),
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(session.mode.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Workout ${formatDuration(session.elapsedMs)}", color = Color.White)
                Text("Sets ${session.completedSetCount}", color = Color.White)
                if (session.mode.isHangMode()) {
                    Text(
                        text = "Current hold ${formatHoldDuration(session.liveSet.durationMs)}",
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = "Current reps ${session.liveSet.reps}",
                        color = Color.White,
                    )
                    Text(
                        text = "Current set ${formatHoldDuration(session.liveSet.durationMs)}",
                        color = Color.White,
                    )
                }
            }
        }

        ElevatedCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = Color(0xCC101216),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showSetEditor = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.List, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Sets")
                }

                if (session.paused) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                        Text("Resume")
                    }
                } else {
                    OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                        Text("Pause")
                    }
                }

                Button(onClick = onEnd, modifier = Modifier.weight(1f)) {
                    Text("End")
                }
            }
        }
    }

    if (showSetEditor) {
        SetEditorDialog(
            title = "Session sets",
            sets = session.editor.sets,
            onDismiss = { showSetEditor = false },
            onEditSet = onEditSet,
            onDeleteSet = onDeleteSet,
        )
    }
}

@Composable
private fun SessionDetailDialog(
    detail: CompletedWorkoutDetail,
    onDismiss: () -> Unit,
    onEditSet: (Long, Int, Long) -> Unit,
    onDeleteSet: (Long) -> Unit,
) {
    SetEditorDialog(
        title = "${detail.mode.label} • ${formatSessionTime(detail.completedAtMs)}",
        subtitle = "${formatDuration(detail.durationMs)} • ${detail.sets.size} set(s)",
        sets = detail.sets,
        onDismiss = onDismiss,
        onEditSet = onEditSet,
        onDeleteSet = onDeleteSet,
    )
}

@Composable
private fun SetEditorDialog(
    title: String,
    sets: List<WorkoutSetState>,
    onDismiss: () -> Unit,
    onEditSet: (Long, Int, Long) -> Unit,
    onDeleteSet: (Long) -> Unit,
    subtitle: String? = null,
) {
    var editingSet by remember { mutableStateOf<WorkoutSetState?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subtitle != null) {
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (sets.isEmpty()) {
                    Text("No sets yet")
                } else {
                    sets.forEach { set ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val line = if (set.reps == 0 && set.durationMs > 0L) {
                                "Set ${set.setNumber}: ${formatHoldDuration(set.durationMs)}"
                            } else {
                                "Set ${set.setNumber}: ${set.reps} reps • ${formatHoldDuration(set.durationMs)}"
                            }
                            Text(line, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { editingSet = set }) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Edit")
                            }
                            Spacer(modifier = Modifier.size(6.dp))
                            OutlinedButton(onClick = { onDeleteSet(set.id) }) {
                                Text("Delete")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
    )

    if (editingSet != null) {
        EditSetDialog(
            set = editingSet!!,
            onDismiss = { editingSet = null },
            onSave = { reps, durationMs ->
                onEditSet(editingSet!!.id, reps, durationMs)
                editingSet = null
            },
        )
    }
}

@Composable
private fun EditSetDialog(
    set: WorkoutSetState,
    onDismiss: () -> Unit,
    onSave: (Int, Long) -> Unit,
) {
    var repsInput by rememberSaveable(set.id) { mutableStateOf(set.reps.toString()) }
    var durationInput by rememberSaveable(set.id) { mutableStateOf(((set.durationMs / 1000L).coerceAtLeast(0L)).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit set ${set.setNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = repsInput,
                    onValueChange = { repsInput = it },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { durationInput = it },
                    label = { Text("Duration (seconds)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val reps = repsInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val seconds = durationInput.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    onSave(reps, seconds * 1000L)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ProfileTab(
    uiState: MainUiState,
    onMediaToggle: (Boolean) -> Unit,
    onOverlayToggle: (Boolean) -> Unit,
    onPreviewToggle: (Boolean) -> Unit,
    onPaletteSelect: (ColorPalette) -> Unit,
) {
    ScreenHeader("Profile")

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Stats", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile("Workouts", uiState.profileStats.totalWorkouts.toString(), Modifier.weight(1f))
                MetricTile("Max Reps", uiState.profileStats.maxReps.toString(), Modifier.weight(1f))
                MetricTile("Max Hold", formatDuration(uiState.profileStats.maxHoldMs), Modifier.weight(1f))
            }
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            SettingToggle("Enable media play/pause", uiState.settings.mediaControlEnabled, onMediaToggle)
            SettingToggle("Enable overlay", uiState.settings.overlayEnabled, onOverlayToggle)
            SettingToggle("Show camera preview", uiState.showCameraPreview, onPreviewToggle)
            Text(
                text = "Color palette",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ColorPaletteSelector(
                selectedPalette = uiState.settings.colorPalette,
                onSelect = onPaletteSelect,
            )
        }
    }
}

@Composable
private fun ColorPaletteSelector(
    selectedPalette: ColorPalette,
    onSelect: (ColorPalette) -> Unit,
) {
    ColorPalette.entries.chunked(2).forEach { rowPalettes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowPalettes.forEach { palette ->
                val selected = selectedPalette == palette
                if (selected) {
                    Button(
                        onClick = { onSelect(palette) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(palette.label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(palette) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(palette.label)
                    }
                }
            }
            if (rowPalettes.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
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
            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val isWindows98 = LocalIsWindows98Theme.current
    Column(
        modifier = if (isWindows98) {
            modifier
                .background(Win98SurfaceVariant)
                .win98SunkenBorder()
                .padding(10.dp)
        } else {
            modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(10.dp)
        },
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
private fun CameraPreviewWithTracking(
    frame: DebugPreviewFrame,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
    ) {
        Image(
            bitmap = frame.bitmap.asImageBitmap(),
            contentDescription = "Camera preview with tracking",
            contentScale = ContentScale.Crop,
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

private fun ExerciseMode.isHangMode(): Boolean {
    return this == ExerciseMode.DEAD_HANG || this == ExerciseMode.ACTIVE_HANG
}

private fun formatHoldDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
