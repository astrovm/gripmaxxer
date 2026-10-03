package com.astrovm.gripmaxxer.ui

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.GripApp
import com.astrovm.gripmaxxer.container
import org.junit.Before
import org.junit.Rule
import com.astrovm.gripmaxxer.data.Accent
import com.astrovm.gripmaxxer.tracking.Exercise
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Runs the real app UI against a real database, with a fake permission dialog. */
@RunWith(RobolectricTestRunner::class)
@Config(application = GripApp::class, qualifiers = "w411dp-h914dp-xxhdpi")
abstract class UiTestBase {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected val app: Application = ApplicationProvider.getApplicationContext()
    protected val container get() = app.container
    protected var access = Access(camera = true, notifications = true, overlay = true)
    protected lateinit var viewModel: MainViewModel
    protected val openedLinks = mutableListOf<String>()

    /** What the permission dialog answers. */
    protected var grantCamera = true
    protected var permissionRequests = 0

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                permissionRequests++
                if (grantCamera) access = access.copy(camera = true)
                val granted = (input as Array<*>).map { it == android.Manifest.permission.CAMERA && grantCamera }
                val intent = Intent()
                    .putExtra(ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSIONS, input.map { it as String }.toTypedArray())
                    .putExtra(
                        ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS,
                        granted.map { if (it) 0 else -1 }.toIntArray(),
                    )
                dispatchResult(requestCode, android.app.Activity.RESULT_OK, intent)
            }
        }
    }

    /** Settings live in a process-wide store, so put them back to defaults for each test. */
    @Before
    fun resetSettings() = runBlocking {
        val settings = container.settings
        settings.setLastExercise(Exercise.DEAD_HANG)
        settings.setAccent(Accent.WHITE)
        settings.setMediaControl(true)
        settings.setOverlay(true)
        settings.setRepSound(true)
        settings.setVoiceCues(true)
    }

    protected fun launch() {
        viewModel = MainViewModel(container) { access }
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) {
                        openedLinks += uri
                    }
                },
            ) { GripmaxxerApp(viewModel) }
        }
        advance()
    }

    /** Lets a frame pass, plus any main-thread work it triggers. */
    protected fun advance() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    /** Clicks, then lets the UI react. */
    protected fun SemanticsNodeInteraction.tap() {
        performSemanticsAction(SemanticsActions.OnClick)
        advance()
    }

    protected fun waitForText(text: String, substring: Boolean = false) {
        waitUntil { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun waitUntil(check: () -> Boolean) {
        try {
            compose.waitUntil(5_000) {
                advance()
                check()
            }
        } catch (e: Throwable) {
            throw AssertionError("live=${container.controller.live.value}\n" + compose.onRoot().printToString(), e)
        }
    }

    /** Scrolls the screen's main list to the item with [text]. */
    protected fun scrollTo(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = substring))

    protected fun lastStartedIntent(): Intent? = shadowOf(compose.activity).nextStartedActivity
}
