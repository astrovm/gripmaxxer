package com.astrovm.gripmaxxer.ui

import android.view.View
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.astrovm.gripmaxxer.ui.theme.GripmaxxerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewThemeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun editorPreviewRendersWithoutTouchingTheActivityWindow() {
        compose.setContent {
            val context = LocalContext.current
            val preview = object : View(context) { override fun isInEditMode() = true }
            CompositionLocalProvider(LocalView provides preview) {
                GripmaxxerTheme { Text("Editor preview") }
            }
        }
        compose.onNodeWithText("Editor preview").assertExists()
    }
}
