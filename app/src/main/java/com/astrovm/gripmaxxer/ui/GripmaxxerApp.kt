package com.astrovm.gripmaxxer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astrovm.gripmaxxer.data.Settings
import com.astrovm.gripmaxxer.data.Workout
import com.astrovm.gripmaxxer.ui.theme.GripTheme

@Composable
fun GripmaxxerApp(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val active by viewModel.activeWorkout.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val openWorkoutId by viewModel.openWorkoutId.collectAsStateWithLifecycle()

    // Wait for settings so the accent color doesn't flash.
    val loadedSettings = settings ?: return
    GripTheme(loadedSettings.accent) {
        // Sets the default text color for screens without a Scaffold.
        Surface(color = MaterialTheme.colorScheme.background) {
            Screens(viewModel, loadedSettings, active, tab, openWorkoutId)
        }
    }
}

@Composable
private fun Screens(
    viewModel: MainViewModel,
    settings: Settings,
    active: Loadable<Workout?>,
    tab: Tab,
    openWorkoutId: Long?,
) {
    val activeWorkout = (active as? Loadable.Ready)?.value
    when {
        active is Loadable.Loading -> Box(Modifier.fillMaxSize())
        activeWorkout != null -> LiveWorkoutScreen(viewModel, activeWorkout)
        openWorkoutId != null -> {
            BackHandler { viewModel.openWorkout(null) }
            WorkoutDetailScreen(viewModel)
        }

        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { BottomTabs(tab, viewModel::selectTab) },
        ) { padding ->
            Box(Modifier.padding(padding)) { TabScreen(viewModel, settings, tab) }
        }
    }
}

@Composable
private fun TabScreen(viewModel: MainViewModel, settings: Settings, tab: Tab) {
    when (tab) {
        Tab.WORKOUT -> StartWorkoutScreen(viewModel, settings)
        Tab.HISTORY -> HistoryScreen(viewModel)
        else -> ProfileScreen(viewModel, settings)
    }
}

@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        TabItem(Tab.WORKOUT, selected, Icons.Outlined.FitnessCenter, "Workout", onSelect)
        TabItem(Tab.HISTORY, selected, Icons.Outlined.History, "History", onSelect)
        TabItem(Tab.PROFILE, selected, Icons.Outlined.Person, "Profile", onSelect)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    tab: Tab,
    selected: Tab,
    icon: ImageVector,
    label: String,
    onSelect: (Tab) -> Unit,
) {
    NavigationBarItem(
        selected = tab == selected,
        onClick = { onSelect(tab) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
