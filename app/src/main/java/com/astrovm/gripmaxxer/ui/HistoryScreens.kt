package com.astrovm.gripmaxxer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astrovm.gripmaxxer.data.Workout
import com.astrovm.gripmaxxer.tracking.Exercise

/** "3 sets, 24 reps" or "2 sets, 1:40". */
fun summary(exercise: Exercise, workout: Workout): String {
    val sets = workout.sets.filter { it.exercise == exercise }
    val count = if (sets.size == 1) "1 set" else "${sets.size} sets"
    val total = if (exercise.isHold) formatDuration(sets.sumOf { it.durationMs }) else formatReps(sets.sumOf { it.reps })
    return "$count, $total"
}

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val workouts = (history as? Loadable.Ready)?.value ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("History") }
        if (workouts.isEmpty()) {
            item { EmptyHistory() }
        }
        items(workouts, key = { it.id }) { workout ->
            WorkoutCard(workout) { viewModel.openWorkout(workout.id) }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text("Finished workouts show up here", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkoutCard(workout: Workout, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDateTime(workout.startedAtMs),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatDuration(workout.durationMs ?: 0L),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (exercise in workout.exercises) {
            Row {
                Text(exercise.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(summary(exercise, workout), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun WorkoutDetailScreen(viewModel: MainViewModel) {
    val workout by viewModel.openWorkout.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val current = workout ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.openWorkout(null) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text(
                    formatDateTime(current.startedAtMs),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete workout")
                }
            }
        }
        item {
            Text(
                "Took ${formatDuration(current.durationMs ?: 0L)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
        }
        for (exercise in current.exercises) {
            item(key = exercise.name) {
                Row(Modifier.padding(start = 12.dp, top = 12.dp)) {
                    Text(exercise.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(summary(exercise, current), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val sets = current.sets.filter { it.exercise == exercise }
            items(sets.size, key = { sets[it].id }) { index ->
                val set = sets[index]
                EditableSet(
                    number = index + 1,
                    set = set,
                    showExercise = false,
                    isEditing = editing == set.id,
                    onEditingChange = { editing = if (it) set.id else null },
                    onUpdate = { reps, ms -> viewModel.updateSet(set.id, reps, ms) },
                    onDelete = { viewModel.deleteSet(set.id) },
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this workout?",
            confirm = "Delete",
            onConfirm = {
                confirmDelete = false
                viewModel.deleteWorkout(current.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
