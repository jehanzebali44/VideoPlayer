package com.example.ui.screens.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun PauseIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = width * 0.3f
        val gap = width * 0.3f
        
        // Left bar
        drawRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(barWidth, height)
        )
        // Right bar
        drawRect(
            color = tint,
            topLeft = Offset(barWidth + gap, 0f),
            size = Size(barWidth, height)
        )
    }
}
