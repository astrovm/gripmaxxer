package com.astrovm.gripmaxxer

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.astrovm.gripmaxxer.camera.CameraFrame
import com.astrovm.gripmaxxer.data.GripDatabase
import com.astrovm.gripmaxxer.data.SettingsRepository
import com.astrovm.gripmaxxer.data.WorkoutRepository
import com.astrovm.gripmaxxer.service.TrackingService
import com.astrovm.gripmaxxer.tracking.WorkoutController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

private val Context.settingsStore by preferencesDataStore(name = "gripmaxxer_settings")

/** Everything that lives as long as the app. */
class AppContainer(context: Context, database: GripDatabase = GripDatabase.create(context)) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val workouts = WorkoutRepository(database.workoutDao())
    val settings = SettingsRepository(context.settingsStore)
    val controller = WorkoutController(
        workouts = workouts,
        settingsRepository = settings,
        scope = scope,
        startCamera = { TrackingService.start(context) },
        stopCamera = { TrackingService.stop(context) },
    )

    /** Latest camera image for the in-app preview. Only filled in while [previewVisible]. */
    val preview = MutableStateFlow<CameraFrame?>(null)
    val previewVisible = MutableStateFlow(false)
}

class GripApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.container: AppContainer get() = (applicationContext as GripApp).container
