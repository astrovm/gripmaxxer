package com.example.hangplaycam.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hangplaycam.reps.ExerciseMode
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Hangmaxxer", style = MaterialTheme.typography.headlineSmall)

        StatusLine("Camera permission", uiState.permissions.cameraGranted)
        StatusLine("Notification access", uiState.permissions.notificationAccessEnabled)
        StatusLine("Overlay permission", uiState.permissions.overlayPermissionGranted)
        StatusLine("Service running", uiState.monitoring.serviceRunning)
        StatusLine("Hang state", uiState.monitoring.hanging)

        Text(
            text = "Reps (${uiState.monitoring.mode.label}): ${uiState.monitoring.reps}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Elapsed hanging time: ${String.format(Locale.US, "%.1f", uiState.monitoring.elapsedHangMs / 1000f)} s",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Active media controller: ${uiState.monitoring.mediaControllerPackage ?: "none"}",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()

        Button(
            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Grant Camera Permission")
        }

        Button(
            onClick = { openNotificationAccessSettings() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Notification Access Settings")
        }

        SettingToggle(
            label = "Enable overlay stopwatch",
            checked = uiState.settings.overlayEnabled,
            onToggle = { enabled ->
                viewModel.setOverlayEnabled(enabled)
                if (enabled && !uiState.permissions.overlayPermissionGranted) {
                    openOverlaySettings()
                }
            },
        )

        Button(
            onClick = { openOverlaySettings() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Request Overlay Permission")
        }

        SettingToggle(
            label = "Pose mode accurate (FAST off)",
            checked = uiState.settings.poseModeAccurate,
            onToggle = viewModel::setPoseModeAccurate,
        )

        Text("Exercise mode", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExerciseMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.selectedMode == mode,
                    onClick = { viewModel.setMode(mode) },
                    label = { Text(mode.label) },
                )
            }
        }

        SettingSlider(
            title = "wristShoulderMargin",
            value = uiState.settings.wristShoulderMargin,
            valueRange = 0.02f..0.15f,
            displayValue = String.format(Locale.US, "%.3f", uiState.settings.wristShoulderMargin),
            onValueChange = viewModel::setWristShoulderMargin,
        )

        SettingSlider(
            title = "missingPoseTimeoutMs",
            value = uiState.settings.missingPoseTimeoutMs.toFloat(),
            valueRange = 100f..800f,
            displayValue = "${uiState.settings.missingPoseTimeoutMs.toInt()} ms",
            onValueChange = { viewModel.setMissingPoseTimeoutMs(it.toLong()) },
        )

        SettingSlider(
            title = "marginUp",
            value = uiState.settings.marginUp,
            valueRange = 0.01f..0.20f,
            displayValue = String.format(Locale.US, "%.3f", uiState.settings.marginUp),
            onValueChange = viewModel::setMarginUp,
        )

        SettingSlider(
            title = "marginDown",
            value = uiState.settings.marginDown,
            valueRange = 0.01f..0.20f,
            displayValue = String.format(Locale.US, "%.3f", uiState.settings.marginDown),
            onValueChange = viewModel::setMarginDown,
        )

        SettingSlider(
            title = "elbowUpAngle",
            value = uiState.settings.elbowUpAngle,
            valueRange = 70f..150f,
            displayValue = String.format(Locale.US, "%.1f°", uiState.settings.elbowUpAngle),
            onValueChange = viewModel::setElbowUpAngle,
        )

        SettingSlider(
            title = "elbowDownAngle",
            value = uiState.settings.elbowDownAngle,
            valueRange = 130f..180f,
            displayValue = String.format(Locale.US, "%.1f°", uiState.settings.elbowDownAngle),
            onValueChange = viewModel::setElbowDownAngle,
        )

        Button(
            onClick = { viewModel.startMonitoring() },
            enabled = uiState.permissions.cameraGranted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start monitoring")
        }

        Button(
            onClick = { viewModel.stopMonitoring() },
            enabled = uiState.monitoring.serviceRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Stop monitoring")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            StatusLine("Notifications enabled", notificationsEnabled)
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
        Text(text = label)
        Text(text = if (value) "Yes" else "No")
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
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title)
            Text(text = displayValue)
        }
        Slider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
        )
    }
}
