package com.constrakr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ScreenLockOverlay(
    locked: Boolean,
    onUnlock: () -> Unit
) {
    if (!locked) return
    var lastTap by remember { mutableLongStateOf(0L) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    val now = System.currentTimeMillis()
                    if (now - lastTap <= 350) onUnlock()
                    lastTap = now
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Double tap to unlock",
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
