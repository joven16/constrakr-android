package com.constrakr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.constrakr.config.ConsTrakrConstants
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PasscodeKeypad(
    title: String,
    subtitle: String,
    digitCount: Int = ConsTrakrConstants.ADMIN_CODE_DIGITS,
    onSubmit: suspend (String) -> Result<Unit>
) {
    val scope = rememberCoroutineScope()
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun appendDigit(d: String) {
        if (verifying || digits.length >= digitCount) return
        error = null
        digits += d
        if (digits.length == digitCount) {
            scope.launch {
                verifying = true
                val result = onSubmit(digits)
                // Success dismisses the sheet — do not touch state after composition leaves.
                if (result.isSuccess || !isActive) return@launch
                verifying = false
                result.onFailure { err ->
                    error = err.message
                    digits = ""
                    shake.snapTo(0f)
                    shake.animateTo(
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = 400
                            0f at 0
                            14f at 80
                            -14f at 160
                            10f at 240
                            -10f at 320
                            0f at 400
                        }
                    )
                }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))

                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                        )
                    }

                    Row(
                        Modifier.padding(top = 28.dp).offset(x = shake.value.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(digitCount) { index ->
                            val filled = index < digits.length
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (filled) {
                                            Modifier.background(MaterialTheme.colorScheme.onBackground)
                                        } else {
                                            Modifier.border(
                                                1.5.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                CircleShape
                                            )
                                        }
                                    )
                            )
                        }
                    }

                    Box(Modifier.padding(top = 14.dp).height(22.dp), contentAlignment = Alignment.Center) {
                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Column(
                        Modifier.padding(top = 28.dp, bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        KeypadRow(listOf("1", "2", "3"), verifying, ::appendDigit)
                        KeypadRow(listOf("4", "5", "6"), verifying, ::appendDigit)
                        KeypadRow(listOf("7", "8", "9"), verifying, ::appendDigit)
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Spacer(Modifier.size(72.dp))
                            DigitKey("0", verifying) { appendDigit("0") }
                            DeleteKey(
                                enabled = digits.isNotEmpty() && !verifying,
                                onClick = {
                                    if (digits.isNotEmpty() && !verifying) {
                                        error = null
                                        digits = digits.dropLast(1)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
            }

            if (verifying) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun KeypadRow(
    labels: List<String>,
    disabled: Boolean,
    onDigit: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        labels.forEach { label ->
            DigitKey(label, disabled) { onDigit(label) }
        }
    }
}

@Composable
private fun DigitKey(label: String, disabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                enabled = !disabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DeleteKey(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f)
        )
    }
}
