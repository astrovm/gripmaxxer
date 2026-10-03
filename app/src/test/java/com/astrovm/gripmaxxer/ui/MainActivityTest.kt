package com.astrovm.gripmaxxer.ui

import android.Manifest
import android.app.Application
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.GripApp
import com.astrovm.gripmaxxer.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(application = GripApp::class)
class MainActivityTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun access(scenario: ActivityScenario<MainActivity>): Access {
        var access: Access? = null
        scenario.onActivity { access = ViewModelProvider(it)[MainViewModel::class.java].access.value }
        return access!!
    }

    @Test
    fun readsAccessAndRefreshesOnReturn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Access(), access(scenario))

            shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
            ShadowSettings.setCanDrawOverlays(true)
            Settings.Secure.putString(
                app.contentResolver,
                "enabled_notification_listeners",
                "${app.packageName}/com.astrovm.gripmaxxer.media.HangNotificationListener",
            )
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Access(camera = true, notifications = true, overlay = true), access(scenario))
        }
    }
}
