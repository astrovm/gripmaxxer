package com.astrovm.gripmaxxer.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.astrovm.gripmaxxer.data.ExerciseStats
import com.astrovm.gripmaxxer.data.Settings
import com.astrovm.gripmaxxer.data.Workout
import com.astrovm.gripmaxxer.tracking.Exercise
import com.astrovm.gripmaxxer.tracking.LiveState

@Composable
fun StartWorkoutScreen(viewModel: MainViewModel, settings: Settings) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(settings.lastExercise) }
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    // Starts either way: the notification is nice to have, the camera is not optional.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startWorkout(selected)
    }
    val start = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canNotify(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startWorkout(selected)
        }
    }
    // Only asks for notifications once the camera is allowed, since nothing starts without it.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshAccess()
        cameraDenied = !granted
        if (granted) start()
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ScreenTitle("Workout") }
            item { SectionTitle("On the bar") }
            item { ExerciseGrid(Exercise.entries.filter { it.onBar }, selected) { selected = it } }
            item { SectionTitle("On the floor") }
            item { ExerciseGrid(Exercise.entries.filter { !it.onBar }, selected) { selected = it } }
            if (settings.mediaControl && !access.notifications) {
                item {
                    AccessHint(
                        Icons.Outlined.MusicNote,
                        "Play and pause your music while you train",
                        onDismiss = { viewModel.setMediaControl(false) },
                    ) { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }
            }
            if (settings.overlay && !access.overlay) {
                item {
                    AccessHint(
                        Icons.Outlined.Layers,
                        "Show a floating timer over other apps",
                        onDismiss = { viewModel.setOverlay(false) },
                    ) {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cameraDenied && !access.camera) {
                ErrorLine("Gripmaxxer needs the camera to count your reps")
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open app settings") }
            }
            Button(
                onClick = {
                    if (access.camera) start() else cameraPermission.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Start ${selected.label}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The workout notification needs permission on Android 13 and up. */
private fun canNotify(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun ExerciseGrid(exercises: List<Exercise>, selected: Exercise, onSelect: (Exercise) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in exercises.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (exercise in row) {
                    ExerciseCard(exercise, exercise == selected, Modifier.weight(1f)) { onSelect(exercise) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .border(2.dp, if (selected) colors.primary else colors.surfaceContainer, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            exercise.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) colors.primary else colors.onSurface,
        )
        Text(
            if (exercise.isHold) "Hold" else "Reps",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/** Asks for a permission a setting needs. Dismissing it turns the setting off. */
@Composable
private fun AccessHint(icon: ImageVector, text: String, onDismiss: () -> Unit, onAllow: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 14.dp, end = 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onAllow) { Text("Allow") }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "Turn off", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LiveWorkoutScreen(viewModel: MainViewModel, workout: Workout) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val frame by viewModel.preview.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val now by viewModel.clock.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    // Each new set gets a fresh editor, so it never shows what was typed for the last one.
    var addCount by rememberSaveable { mutableIntStateOf(0) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val deleteSet = rememberDeleteWithUndo(viewModel, snackbar)
    val exercise = live.exercise ?: workout.exercise
    val restSinceMs = workout.sets.lastOrNull()?.completedAtMs.takeUnless { live.inSet }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        viewModel.setPreviewVisible(true)
        onDispose {
            view.keepScreenOn = false
            viewModel.setPreviewVisible(false)
        }
    }
    // Reopened after the app was closed: turn the camera back on.
    LaunchedEffect(workout.id) {
        if (!live.tracking && live.error == null && access.camera) viewModel.resumeTracking()
    }

    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WorkoutClock(workout.startedAtMs, now, Modifier.weight(1f))
                    Button(onClick = { if (workout.sets.isEmpty()) confirmDiscard = true else viewModel.finishWorkout() }) {
                        Text("Finish")
                    }
                }
            }
            item {
                LivePanel(
                    live = live,
                    exercise = exercise,
                    frame = frame,
                    restMs = restSinceMs?.let { now - it },
                    stats = stats.firstOrNull { it.exercise == exercise },
                    onRetry = viewModel::resumeTracking,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Exercise.entries) { option ->
                        FilterChip(
                            selected = option == exercise,
                            onClick = { viewModel.switchExercise(option) },
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Sets", Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            addCount++
                            adding = true
                        },
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Add set")
                    }
                }
            }
            if (adding) {
                item(key = "new-$addCount") {
                    SetEditor(
                        exercise = exercise,
                        existing = null,
                        onSave = { reps, ms ->
                            viewModel.addSet(workout.id, exercise, reps, ms)
                            adding = false
                        },
                        onDelete = null,
                        onCancel = { adding = false },
                    )
                }
            }
            val mixed = workout.exercises.size > 1
            itemsIndexed(workout.sets.asReversed(), key = { _, set -> set.id }) { index, set ->
                EditableSet(
                    number = workout.sets.size - index,
                    set = set,
                    showExercise = mixed || set.exercise != exercise,
                    isEditing = editing == set.id,
                    onEditingChange = { editing = if (it) set.id else null },
                    onUpdate = { reps, ms -> viewModel.updateSet(set.id, reps, ms) },
                    onDelete = { deleteSet(workout.id, set) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Nothing logged yet. Discard this workout?",
            confirm = "Discard",
            onConfirm = {
                confirmDiscard = false
                viewModel.finishWorkout()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun WorkoutClock(startedAtMs: Long, now: Long, modifier: Modifier) {
    Text(
        formatDuration(now - startedAtMs),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun LivePanel(
    live: LiveState,
    exercise: Exercise,
    frame: com.astrovm.gripmaxxer.camera.CameraFrame?,
    restMs: Long?,
    stats: ExerciseStats?,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.surfaceContainer)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Small enough that the sets stay on screen. Once the phone is propped up, the count matters more.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 220.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                live.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ErrorLine(live.error)
                    OutlinedButton(onClick = onRetry) { Text("Try again") }
                }

                frame?.image != null -> CameraPreview(
                    frame,
                    if (live.inSet) colors.primary else colors.onSurfaceVariant,
                    Modifier.clip(MaterialTheme.shapes.large),
                )

                else -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Videocam, contentDescription = null, tint = colors.onSurfaceVariant)
                    Text("Starting camera", color = colors.onSurfaceVariant)
                }
            }
        }
        Text(
            if (exercise.isHold) formatDuration(live.setDurationMs) else live.reps.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = if (live.inSet) colors.primary else colors.onSurface,
        )
        StatusLine(live, exercise)
        if (restMs != null) IconLine(Icons.Outlined.Timer, "Rest ${formatDuration(restMs)}")
        if (stats != null) {
            val best = formatResult(exercise, stats.bestReps, stats.bestHoldMs)
            val last = formatResult(exercise, stats.lastReps, stats.lastHoldMs)
            IconLine(Icons.Outlined.EmojiEvents, "Best $best, last time $last")
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusLine(live: LiveState, exercise: Exercise) {
    val colors = MaterialTheme.colorScheme
    val lastSet = live.lastSet
    when {
        !live.tracking -> Unit
        !live.personVisible -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.PersonOff, contentDescription = null, tint = colors.onSurfaceVariant)
            Text("Step into frame", color = colors.onSurfaceVariant)
        }

        live.inSet -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(colors.primary, CircleShape),
            )
            Text(
                if (exercise.isHold) "Holding" else "In set, ${formatDuration(live.setDurationMs)}",
                color = colors.primary,
            )
        }

        lastSet != null && lastSet.exercise == exercise -> Text(
            "Saved ${formatResult(exercise, lastSet.reps, lastSet.durationMs)}",
            color = colors.onSurfaceVariant,
        )

        else -> Text(
            if (exercise.onBar) "Grab the bar to start" else "Start your first rep",
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

