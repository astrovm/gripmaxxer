package com.astrovm.gripmaxxer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.astrovm.gripmaxxer.camera.CameraFrame
import com.astrovm.gripmaxxer.data.WorkoutSet
import com.astrovm.gripmaxxer.tracking.Exercise
import com.astrovm.gripmaxxer.tracking.Joint

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** "8 reps" for rep exercises, "0:42" for holds. */
fun WorkoutSet.headline(): String = if (exercise.isHold) formatDuration(durationMs) else formatReps(reps)

@Composable
fun SetRow(number: Int, set: WorkoutSet, showExercise: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.weight(1f)) {
            Text(set.headline(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (showExercise) {
                Text(
                    set.exercise.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!set.exercise.isHold && set.durationMs > 0) {
            Text(
                formatDuration(set.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (set.tracked) {
            Icon(
                Icons.Outlined.Videocam,
                contentDescription = "Counted by camera",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A set that turns into an editor when tapped. */
@Composable
fun EditableSet(
    number: Int,
    set: WorkoutSet,
    showExercise: Boolean,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onUpdate: (reps: Int, durationMs: Long) -> Unit,
    onDelete: () -> Unit,
) {
    if (!isEditing) {
        SetRow(number, set, showExercise) { onEditingChange(true) }
        return
    }
    SetEditor(
        exercise = set.exercise,
        existing = set,
        onSave = { reps, ms ->
            onUpdate(reps, ms)
            onEditingChange(false)
        },
        onDelete = {
            onDelete()
            onEditingChange(false)
        },
        onCancel = { onEditingChange(false) },
    )
}

/**
 * Edits a set in place, or adds one when [existing] is null.
 * Holds only ask for time. Rep exercises ask for reps, time is optional.
 */
@Composable
fun SetEditor(
    exercise: Exercise,
    existing: WorkoutSet?,
    onSave: (reps: Int, durationMs: Long) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var reps by rememberSaveable { mutableStateOf(existing?.reps?.toString() ?: "") }
    var seconds by rememberSaveable { mutableStateOf(existing?.durationMs?.div(1000)?.toString() ?: "") }
    val repsValue = reps.toIntOrNull()
    val secondsValue = seconds.toLongOrNull()
    val valid = if (exercise.isHold) {
        secondsValue != null && secondsValue > 0
    } else {
        repsValue != null && repsValue > 0
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (existing == null) "New ${exercise.label} set" else exercise.label,
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!exercise.isHold) {
                NumberField("Reps", reps, Modifier.weight(1f)) { reps = it }
            }
            NumberField("Seconds", seconds, Modifier.weight(1f)) { seconds = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(
                enabled = valid,
                onClick = { onSave(repsValue ?: 0, (secondsValue ?: 0L) * 1000) },
            ) { Text("Save") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** A yes/no confirmation for things that can't be undone. */
@Composable
fun ConfirmDialog(title: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val BONES = listOf(
    Joint.LEFT_SHOULDER to Joint.RIGHT_SHOULDER,
    Joint.LEFT_SHOULDER to Joint.LEFT_ELBOW,
    Joint.LEFT_ELBOW to Joint.LEFT_WRIST,
    Joint.RIGHT_SHOULDER to Joint.RIGHT_ELBOW,
    Joint.RIGHT_ELBOW to Joint.RIGHT_WRIST,
    Joint.LEFT_SHOULDER to Joint.LEFT_HIP,
    Joint.RIGHT_SHOULDER to Joint.RIGHT_HIP,
    Joint.LEFT_HIP to Joint.RIGHT_HIP,
    Joint.LEFT_HIP to Joint.LEFT_KNEE,
    Joint.LEFT_KNEE to Joint.LEFT_ANKLE,
    Joint.RIGHT_HIP to Joint.RIGHT_KNEE,
    Joint.RIGHT_KNEE to Joint.RIGHT_ANKLE,
)

/** The camera image with the detected body drawn on top, mirrored like a selfie. */
@Composable
fun CameraPreview(frame: CameraFrame, color: Color, modifier: Modifier = Modifier) {
    val image = frame.image ?: return
    val bitmap = remember(image) { image.asImageBitmap() }
    Box(
        modifier
            .aspectRatio(frame.width.toFloat() / frame.height)
            .graphicsLayer { scaleX = -1f },
    ) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val pose = frame.pose ?: return@Canvas
            val sx = size.width / frame.width
            val sy = size.height / frame.height
            fun at(joint: Joint) = pose[joint]?.let { Offset(it.x * sx, it.y * sy) }
            for ((a, b) in BONES) {
                val start = at(a) ?: continue
                val end = at(b) ?: continue
                drawLine(color, start, end, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            }
            for (joint in Joint.entries) {
                val point = at(joint) ?: continue
                drawCircle(Color.White, radius = 5.dp.toPx(), center = point)
            }
        }
    }
}
