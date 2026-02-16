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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astrolabs.gripmaxxer.datastore.WeightUnit
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import com.astrolabs.gripmaxxer.service.DebugPreviewFrame
import com.astrolabs.gripmaxxer.workout.ActiveWorkoutState
import com.astrolabs.gripmaxxer.workout.CompletedWorkoutDetail
import com.astrolabs.gripmaxxer.workout.DefaultExerciseLibrary
import com.astrolabs.gripmaxxer.workout.ExerciseTemplate
import com.astrolabs.gripmaxxer.workout.Routine
import com.astrolabs.gripmaxxer.workout.CalendarDaySummary
import com.astrolabs.gripmaxxer.workout.WorkoutExerciseState
import com.astrolabs.gripmaxxer.workout.WorkoutFeedItem
import com.astrolabs.gripmaxxer.workout.WorkoutSetState
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
    val snackbarHostState = remember { SnackbarHostState() }

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

    val notificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } else {
        true
    }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = uiState.selectedTab == RootTab.HOME,
                    onClick = { viewModel.selectTab(RootTab.HOME) },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                    label = { Text("Home") },
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
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RestTimerBanner(
                remaining = uiState.restTimer.remainingSeconds,
                running = uiState.restTimer.running,
                notice = uiState.restTimer.completionNotice,
                onDismissNotice = viewModel::clearRestTimerNotice,
            )

            when (uiState.selectedTab) {
                RootTab.HOME -> HomeTab(
                    workouts = uiState.completedWorkouts,
                    calendarDays = uiState.calendarDays,
                    selectedDetail = uiState.selectedWorkoutDetail,
                    onOpenDetail = viewModel::openWorkoutDetail,
                    onCloseDetail = viewModel::closeWorkoutDetail,
                    maxReps = uiState.profileStats.maxReps,
                    maxTimeMs = uiState.profileStats.maxActiveMs,
                )

                RootTab.WORKOUT -> WorkoutTab(
                    uiState = uiState,
                    onStartEmptyWorkout = viewModel::startEmptyWorkout,
                    onCreateRoutine = viewModel::createRoutine,
                    onQuickCreateRoutine = viewModel::quickCreateRoutine,
                    onRenameRoutine = viewModel::renameRoutine,
                    onDuplicateRoutine = viewModel::duplicateRoutine,
                    onDeleteRoutine = viewModel::deleteRoutine,
                    onStartRoutine = viewModel::startRoutineWorkout,
                    onToggleRoutinesExpanded = viewModel::toggleRoutinesExpanded,
                    onSetExploreVisible = viewModel::setExploreVisible,
                    onSetExploreQuery = viewModel::setExploreQuery,
                    onAddExerciseToWorkout = viewModel::addExerciseToActiveWorkout,
                    onUpdateWorkoutTitle = viewModel::updateActiveWorkoutTitle,
                    onFinishWorkout = viewModel::finishActiveWorkout,
                    onAddSet = viewModel::addSet,
                    onRemoveSet = viewModel::removeSet,
                    onUpdateSetWeight = viewModel::updateSetWeight,
                    onUpdateSetReps = viewModel::updateSetReps,
                    onToggleSetDone = viewModel::toggleSetDone,
                    onAutoTrackExercise = viewModel::startAutoTrackForExercise,
                    onApplyPendingAutoEvent = viewModel::applyPendingAutoEvent,
                    onDiscardPendingAutoEvent = viewModel::discardPendingAutoEvent,
                    displayWeight = viewModel::displayWeight,
                )

                RootTab.PROFILE -> ProfileTab(
                    uiState = uiState,
                    notificationsEnabled = notificationsEnabled,
                    onWeightUnitChange = viewModel::setWeightUnit,
                    onMediaToggle = viewModel::setMediaControlEnabled,
                    onOverlayToggle = viewModel::setOverlayEnabled,
                    onPreviewToggle = viewModel::setShowCameraPreview,
                    onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenNotificationAccess = openNotificationAccessSettings,
                    onOpenOverlaySettings = openOverlaySettings,
                )
            }
        }
    }
}

@Composable
private fun RestTimerBanner(
    remaining: Int,
    running: Boolean,
    notice: String?,
    onDismissNotice: () -> Unit,
) {
    if (!running && notice == null) return
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (running) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (running) "Rest timer: ${remaining}s" else notice.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!running && notice != null) {
                OutlinedButton(onClick = onDismissNotice) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    workouts: List<WorkoutFeedItem>,
    calendarDays: List<CalendarDaySummary>,
    selectedDetail: CompletedWorkoutDetail?,
    onOpenDetail: (Long) -> Unit,
    onCloseDetail: () -> Unit,
    maxReps: Int,
    maxTimeMs: Long,
) {
    Text(
        text = "Home",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricTile(label = "Max Reps", value = maxReps.toString(), modifier = Modifier.weight(1f))
        MetricTile(label = "Max Time", value = formatDuration(maxTimeMs), modifier = Modifier.weight(1f))
    }

    if (workouts.isEmpty()) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "No completed workouts yet.",
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
                        text = "${formatSessionTime(workout.completedAtMs)} • ${formatDuration(workout.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${workout.exerciseCount} exercises • ${formatWeight(workout.totalVolumeKg)} kg volume",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onOpenDetail(workout.workoutId) }) {
                        Text("View Details")
                    }
                }
            }
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Calendar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (calendarDays.isEmpty()) {
                Text("No completed days yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                calendarDays.take(10).forEach { day ->
                    Text(
                        text = "${formatDay(day.dayEpochMs)} • ${day.workoutCount} workout(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (selectedDetail != null) {
        AlertDialog(
            onDismissRequest = onCloseDetail,
            title = { Text(selectedDetail.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${formatSessionTime(selectedDetail.completedAtMs)} • ${formatDuration(selectedDetail.durationMs)}")
                    selectedDetail.exercises.forEach { exercise ->
                        Text(
                            text = exercise.exerciseName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        exercise.sets.forEach { set ->
                            Text(
                                text = "Set ${set.setNumber}: ${formatWeight(set.weightKg)}kg x ${set.reps}${if (set.done) " done" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onCloseDetail) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun WorkoutTab(
    uiState: MainUiState,
    onStartEmptyWorkout: () -> Unit,
    onCreateRoutine: (String, String) -> Unit,
    onQuickCreateRoutine: () -> Unit,
    onRenameRoutine: (Long, String) -> Unit,
    onDuplicateRoutine: (Long) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
    onStartRoutine: (Long) -> Unit,
    onToggleRoutinesExpanded: () -> Unit,
    onSetExploreVisible: (Boolean) -> Unit,
    onSetExploreQuery: (String) -> Unit,
    onAddExerciseToWorkout: (ExerciseTemplate) -> Unit,
    onUpdateWorkoutTitle: (String) -> Unit,
    onFinishWorkout: () -> Unit,
    onAddSet: (Long) -> Unit,
    onRemoveSet: (Long) -> Unit,
    onUpdateSetWeight: (Long, Float) -> Unit,
    onUpdateSetReps: (Long, Int) -> Unit,
    onToggleSetDone: (Long, Long, Boolean, Int, String) -> Unit,
    onAutoTrackExercise: (Long, ExerciseMode?) -> Unit,
    onApplyPendingAutoEvent: (Long) -> Unit,
    onDiscardPendingAutoEvent: (Long) -> Unit,
    displayWeight: (Float) -> String,
) {
    var showCreateRoutineDialog by rememberSaveable { mutableStateOf(false) }

    if (uiState.activeWorkout != null) {
        ActiveWorkoutContent(
            state = uiState.activeWorkout,
            settingsUnit = uiState.settings.weightUnit,
            showCameraPreview = uiState.showCameraPreview,
            cameraPreviewFrame = uiState.cameraPreviewFrame,
            pendingAutoEvents = uiState.pendingAutoEvents,
            onUpdateWorkoutTitle = onUpdateWorkoutTitle,
            onFinishWorkout = onFinishWorkout,
            onAddExercise = onAddExerciseToWorkout,
            onAddSet = onAddSet,
            onRemoveSet = onRemoveSet,
            onUpdateSetWeight = onUpdateSetWeight,
            onUpdateSetReps = onUpdateSetReps,
            onToggleSetDone = onToggleSetDone,
            onAutoTrackExercise = onAutoTrackExercise,
            onApplyPendingAutoEvent = onApplyPendingAutoEvent,
            onDiscardPendingAutoEvent = onDiscardPendingAutoEvent,
            displayWeight = displayWeight,
        )
        return
    }

    Text(
        text = "Workout",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onStartEmptyWorkout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Start Empty Workout")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Routines", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { showCreateRoutineDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "New Routine")
            }
            IconButton(onClick = onQuickCreateRoutine) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Quick Create")
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ElevatedCard(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { showCreateRoutineDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Text("New Routine")
            }
        }
        ElevatedCard(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { onSetExploreVisible(!uiState.exploreVisible) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Icon(Icons.Outlined.Explore, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("Explore")
            }
        }
    }

    if (uiState.exploreVisible) {
        ExploreCard(
            query = uiState.exploreQuery,
            results = uiState.exploreResults,
            onQueryChange = onSetExploreQuery,
            onAddExercise = onAddExerciseToWorkout,
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleRoutinesExpanded) {
                        Icon(
                            imageVector = if (uiState.routinesExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    }
                    Text(
                        text = "My Routines (${uiState.routines.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (uiState.routinesExpanded) {
                if (uiState.routines.isEmpty()) {
                    OutlinedButton(
                        onClick = { showCreateRoutineDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create your first routine")
                    }
                } else {
                    uiState.routines.forEach { routine ->
                        RoutineCard(
                            routine = routine,
                            onStartRoutine = onStartRoutine,
                            onRenameRoutine = onRenameRoutine,
                            onDuplicateRoutine = onDuplicateRoutine,
                            onDeleteRoutine = onDeleteRoutine,
                        )
                    }
                }
            }
        }
    }

    if (showCreateRoutineDialog) {
        CreateRoutineDialog(
            onDismiss = { showCreateRoutineDialog = false },
            onCreate = { name, exercisesCsv ->
                onCreateRoutine(name, exercisesCsv)
                showCreateRoutineDialog = false
            },
        )
    }
}

@Composable
private fun ExploreCard(
    query: String,
    results: List<ExerciseTemplate>,
    onQueryChange: (String) -> Unit,
    onAddExercise: (ExerciseTemplate) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Explore library", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search exercises") },
                singleLine = true,
            )
            if (results.isEmpty()) {
                Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                results.take(8).forEach { template ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(template.name)
                        OutlinedButton(onClick = { onAddExercise(template) }) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onStartRoutine: (Long) -> Unit,
    onRenameRoutine: (Long, String) -> Unit,
    onDuplicateRoutine: (Long) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Routine options")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuOpen = false
                                renameOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                menuOpen = false
                                onDuplicateRoutine(routine.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuOpen = false
                                onDeleteRoutine(routine.id)
                            },
                        )
                    }
                }
            }
            Text(
                text = routine.exercises.joinToString(", ") { it.exerciseName }.ifBlank { "No exercises" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onStartRoutine(routine.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Routine")
            }
        }
    }

    if (renameOpen) {
        RenameRoutineDialog(
            currentName = routine.name,
            onDismiss = { renameOpen = false },
            onConfirm = { newName ->
                onRenameRoutine(routine.id, newName)
                renameOpen = false
            },
        )
    }
}

@Composable
private fun CreateRoutineDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var exercises by rememberSaveable { mutableStateOf("Pull-up, Push-up") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Routine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = exercises,
                    onValueChange = { exercises = it },
                    label = { Text("Exercises (comma separated)") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, exercises) }) {
                Text("Create")
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
private fun RenameRoutineDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Routine") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ActiveWorkoutContent(
    state: ActiveWorkoutState,
    settingsUnit: WeightUnit,
    showCameraPreview: Boolean,
    cameraPreviewFrame: DebugPreviewFrame?,
    pendingAutoEvents: List<com.astrolabs.gripmaxxer.service.AutoSetEvent>,
    onUpdateWorkoutTitle: (String) -> Unit,
    onFinishWorkout: () -> Unit,
    onAddExercise: (ExerciseTemplate) -> Unit,
    onAddSet: (Long) -> Unit,
    onRemoveSet: (Long) -> Unit,
    onUpdateSetWeight: (Long, Float) -> Unit,
    onUpdateSetReps: (Long, Int) -> Unit,
    onToggleSetDone: (Long, Long, Boolean, Int, String) -> Unit,
    onAutoTrackExercise: (Long, ExerciseMode?) -> Unit,
    onApplyPendingAutoEvent: (Long) -> Unit,
    onDiscardPendingAutoEvent: (Long) -> Unit,
    displayWeight: (Float) -> String,
) {
    var titleDraft by remember(state.id, state.title) { mutableStateOf(state.title) }

    Text(
        text = "Active Workout",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = titleDraft,
                onValueChange = {
                    titleDraft = it
                    onUpdateWorkoutTitle(it)
                },
                label = { Text("Workout title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Elapsed ${formatDuration(state.elapsedMs)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onFinishWorkout) {
                    Text("Finish")
                }
            }
        }
    }

    if (pendingAutoEvents.isNotEmpty()) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Auto-set inbox", style = MaterialTheme.typography.titleMedium)
                pendingAutoEvents.takeLast(5).forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${event.mode.label}: ${event.reps} reps")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = { onDiscardPendingAutoEvent(event.eventId) }) {
                                Text("Discard")
                            }
                            Button(onClick = { onApplyPendingAutoEvent(event.eventId) }) {
                                Text("Apply")
                            }
                        }
                    }
                }
            }
        }
    }

    state.exercises.forEach { exercise ->
        ExerciseCard(
            exercise = exercise,
            unit = settingsUnit,
            onAddSet = onAddSet,
            onRemoveSet = onRemoveSet,
            onUpdateSetWeight = onUpdateSetWeight,
            onUpdateSetReps = onUpdateSetReps,
            onToggleSetDone = onToggleSetDone,
            onAutoTrackExercise = onAutoTrackExercise,
            displayWeight = displayWeight,
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Add Exercise", style = MaterialTheme.typography.titleMedium)
            DefaultExerciseLibrary.take(8).chunked(2).forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chunk.forEach { template ->
                        OutlinedButton(
                            onClick = { onAddExercise(template) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(template.name)
                        }
                    }
                    if (chunk.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showCameraPreview) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Live camera feedback", style = MaterialTheme.typography.titleMedium)
                if (cameraPreviewFrame != null) {
                    CameraPreviewWithTracking(frame = cameraPreviewFrame)
                } else {
                    Text("Start auto-track to view camera feedback")
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: WorkoutExerciseState,
    unit: WeightUnit,
    onAddSet: (Long) -> Unit,
    onRemoveSet: (Long) -> Unit,
    onUpdateSetWeight: (Long, Float) -> Unit,
    onUpdateSetReps: (Long, Int) -> Unit,
    onToggleSetDone: (Long, Long, Boolean, Int, String) -> Unit,
    onAutoTrackExercise: (Long, ExerciseMode?) -> Unit,
    displayWeight: (Float) -> String,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Rest ${exercise.restSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { onAutoTrackExercise(exercise.id, exercise.mode) }) {
                    Text("Auto Track")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TableHeader("Set", Modifier.weight(0.7f))
                TableHeader("Prev", Modifier.weight(1.2f))
                TableHeader(unit.label, Modifier.weight(1.1f))
                TableHeader("Reps", Modifier.weight(1.1f))
                TableHeader("Done", Modifier.weight(0.8f))
            }

            exercise.sets.forEach { set ->
                SetRow(
                    exercise = exercise,
                    set = set,
                    onRemoveSet = onRemoveSet,
                    onUpdateSetWeight = onUpdateSetWeight,
                    onUpdateSetReps = onUpdateSetReps,
                    onToggleSetDone = onToggleSetDone,
                    displayWeight = displayWeight,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAddSet(exercise.id) }) {
                    Text("Add Set")
                }
            }
        }
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SetRow(
    exercise: WorkoutExerciseState,
    set: WorkoutSetState,
    onRemoveSet: (Long) -> Unit,
    onUpdateSetWeight: (Long, Float) -> Unit,
    onUpdateSetReps: (Long, Int) -> Unit,
    onToggleSetDone: (Long, Long, Boolean, Int, String) -> Unit,
    displayWeight: (Float) -> String,
) {
    var weightText by remember(set.id, set.weightKg) { mutableStateOf(displayWeight(set.weightKg)) }
    var repsText by remember(set.id, set.reps) { mutableStateOf(set.reps.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#${set.setNumber}", modifier = Modifier.weight(0.7f))
        Text(set.previous, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = weightText,
            onValueChange = { value ->
                weightText = value
                value.toFloatOrNull()?.let { onUpdateSetWeight(set.id, it) }
            },
            modifier = Modifier.weight(1.1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = repsText,
            onValueChange = { value ->
                repsText = value
                value.toIntOrNull()?.let { onUpdateSetReps(set.id, it) }
            },
            modifier = Modifier.weight(1.1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Checkbox(
            checked = set.done,
            onCheckedChange = {
                onToggleSetDone(exercise.id, set.id, it, exercise.restSeconds, exercise.exerciseName)
            },
            modifier = Modifier.weight(0.8f),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (set.autoTracked) {
            Text(
                text = "Auto",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.height(1.dp))
        }
        OutlinedButton(onClick = { onRemoveSet(set.id) }) {
            Text("Remove")
        }
    }
    HorizontalDivider()
}

@Composable
private fun ProfileTab(
    uiState: MainUiState,
    notificationsEnabled: Boolean,
    onWeightUnitChange: (WeightUnit) -> Unit,
    onMediaToggle: (Boolean) -> Unit,
    onOverlayToggle: (Boolean) -> Unit,
    onPreviewToggle: (Boolean) -> Unit,
    onRequestCamera: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
) {
    Text(
        text = "Profile",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

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
                MetricTile("Sets", uiState.profileStats.totalSets.toString(), Modifier.weight(1f))
                MetricTile("Week", uiState.profileStats.currentWeekCount.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile("Streak", uiState.profileStats.streakDays.toString(), Modifier.weight(1f))
                MetricTile("Max Reps", uiState.profileStats.maxReps.toString(), Modifier.weight(1f))
                MetricTile("Max Time", formatDuration(uiState.profileStats.maxActiveMs), Modifier.weight(1f))
            }
            Text(
                text = "Total volume: ${formatWeight(uiState.profileStats.totalVolumeKg)} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Personal Records", style = MaterialTheme.typography.titleMedium)
            if (uiState.profileStats.records.isEmpty()) {
                Text("No records yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                uiState.profileStats.records.forEach { record ->
                    Text("${record.exerciseName}: ${record.maxReps} reps")
                }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.settings.weightUnit == WeightUnit.KG) {
                    Button(onClick = { onWeightUnitChange(WeightUnit.KG) }) { Text("kg") }
                    OutlinedButton(onClick = { onWeightUnitChange(WeightUnit.LB) }) { Text("lb") }
                } else {
                    OutlinedButton(onClick = { onWeightUnitChange(WeightUnit.KG) }) { Text("kg") }
                    Button(onClick = { onWeightUnitChange(WeightUnit.LB) }) { Text("lb") }
                }
            }
            SettingToggle("Enable media play/pause", uiState.settings.mediaControlEnabled, onMediaToggle)
            SettingToggle("Enable overlay", uiState.settings.overlayEnabled, onOverlayToggle)
            SettingToggle("Show live camera feedback", uiState.showCameraPreview, onPreviewToggle)
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            ChecklistLine("Camera permission", uiState.permissions.cameraGranted)
            if (!uiState.permissions.cameraGranted) {
                OutlinedButton(onClick = onRequestCamera) { Text("Grant Camera") }
            }
            ChecklistLine("Notification access", uiState.permissions.notificationAccessEnabled)
            if (!uiState.permissions.notificationAccessEnabled) {
                OutlinedButton(onClick = onOpenNotificationAccess) { Text("Open Notification Access") }
            }
            ChecklistLine("Overlay permission", uiState.permissions.overlayPermissionGranted)
            if (!uiState.permissions.overlayPermissionGranted) {
                OutlinedButton(onClick = onOpenOverlaySettings) { Text("Open Overlay Settings") }
            }
            ChecklistLine("Notifications enabled", notificationsEnabled)
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

private fun formatDay(timestampMs: Long): String {
    val formatter = SimpleDateFormat("EEE, MMM d", Locale.US)
    return formatter.format(Date(timestampMs))
}

private fun formatWeight(value: Float): String {
    val asInt = value.toInt()
    return if (asInt.toFloat() == value) {
        asInt.toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}
