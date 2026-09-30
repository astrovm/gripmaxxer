package com.astrovm.gripmaxxer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.astrovm.gripmaxxer.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Win98DecorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun bordersDrawAtNormalSizesAndAcceptTinySurfaces() {
        compose.setContent {
            Box(Modifier.size(40.dp).win98RaisedBorder().testTag("raised"))
            Box(Modifier.size(40.dp).win98SunkenBorder().testTag("sunken"))
            Box(Modifier.size(0.5.dp).win98RaisedBorder().testTag("tiny"))
        }
        val raised = compose.onNodeWithTag("raised").captureToImage()
        val sunken = compose.onNodeWithTag("sunken").captureToImage()
        assertEquals(raised.width, sunken.width)
        compose.onNodeWithTag("tiny").captureToImage()
    }
}
