package com.astrolabs.gripmaxxer.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

fun Modifier.win98RaisedBorder(): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        drawWin98Bevel(
            topLeftOuter = Win98Highlight,
            topLeftInner = Win98Light,
            bottomRightOuter = Win98Shadow,
            bottomRightInner = Win98Dark,
        )
    }
)

fun Modifier.win98SunkenBorder(): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        drawWin98Bevel(
            topLeftOuter = Win98Shadow,
            topLeftInner = Win98Dark,
            bottomRightOuter = Win98Highlight,
            bottomRightInner = Win98Light,
        )
    }
)

private fun DrawScope.drawWin98Bevel(
    topLeftOuter: Color,
    topLeftInner: Color,
    bottomRightOuter: Color,
    bottomRightInner: Color,
) {
    val width = size.width
    val height = size.height
    if (width <= 2f || height <= 2f) return

    drawLine(topLeftOuter, Offset(0f, 0f), Offset(width - 1f, 0f), strokeWidth = 1f)
    drawLine(topLeftOuter, Offset(0f, 0f), Offset(0f, height - 1f), strokeWidth = 1f)
    drawLine(bottomRightOuter, Offset(0f, height - 1f), Offset(width - 1f, height - 1f), strokeWidth = 1f)
    drawLine(bottomRightOuter, Offset(width - 1f, 0f), Offset(width - 1f, height - 1f), strokeWidth = 1f)

    drawLine(topLeftInner, Offset(1f, 1f), Offset(width - 2f, 1f), strokeWidth = 1f)
    drawLine(topLeftInner, Offset(1f, 1f), Offset(1f, height - 2f), strokeWidth = 1f)
    drawLine(bottomRightInner, Offset(1f, height - 2f), Offset(width - 2f, height - 2f), strokeWidth = 1f)
    drawLine(bottomRightInner, Offset(width - 2f, 1f), Offset(width - 2f, height - 2f), strokeWidth = 1f)
}
