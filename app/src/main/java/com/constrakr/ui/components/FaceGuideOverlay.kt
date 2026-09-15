package com.constrakr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.constrakr.domain.FacePose
import com.constrakr.ui.theme.SuccessGreen

enum class ScannerBorderState { Ready, Active, Success, Warning, Error }

@Composable
fun FaceGuideOverlay(
    caption: String,
    modifier: Modifier = Modifier,
    conditionMet: Boolean = false,
    borderState: ScannerBorderState = ScannerBorderState.Ready,
    enrollmentPose: FacePose? = null,
    /** 0.5 = center; lower values move the oval upward (front-camera alignment). */
    ovalVerticalBias: Float = 0.38f,
    captionBelowOval: Boolean = false
) {
    val guideColor = when {
        conditionMet || borderState == ScannerBorderState.Success -> SuccessGreen
        borderState == ScannerBorderState.Warning -> com.constrakr.ui.theme.WarningOrange
        borderState == ScannerBorderState.Error -> com.constrakr.ui.theme.ErrorRed
        else -> Color.White.copy(alpha = 0.92f)
    }
    val strokeWidth = if (conditionMet || borderState == ScannerBorderState.Success) 6f else 3f

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val ovalW = minOf(maxWidth * 0.62f, 260.dp)
        val ovalH = ovalW * 1.28f
        val bias = ovalVerticalBias.coerceIn(0.18f, 0.5f)
        val ovalTop = (maxHeight - ovalH) * bias
        val ovalLeft = (maxWidth - ovalW) / 2
        val arrowSize = 48.dp

        Canvas(Modifier.fillMaxSize()) {
            val ow = ovalW.toPx()
            val oh = ovalH.toPx()
            val left = (size.width - ow) / 2f
            val top = (size.height - oh) * bias

            drawRect(Color.Black.copy(alpha = 0.32f))
            drawOval(
                color = Color.Black,
                topLeft = Offset(left, top),
                size = Size(ow, oh),
                blendMode = BlendMode.Clear
            )
            drawOval(
                color = guideColor,
                topLeft = Offset(left, top),
                size = Size(ow, oh),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    pathEffect = if (conditionMet || borderState == ScannerBorderState.Success) {
                        null
                    } else {
                        PathEffect.dashPathEffect(floatArrayOf(24f, 16f))
                    }
                )
            )
        }

        enrollmentPose?.let { pose ->
            when (pose) {
                FacePose.CENTER -> LookStraightCue(
                    color = guideColor,
                    modifier = Modifier.offset(
                        x = ovalLeft + ovalW / 2 - 52.dp,
                        y = ovalTop + ovalH / 2 - 40.dp
                    )
                )
                FacePose.LEFT -> PoseArrow(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    color = guideColor,
                    modifier = Modifier.offset(
                        x = ovalLeft - arrowSize - 8.dp,
                        y = ovalTop + ovalH / 2 - arrowSize / 2
                    )
                )
                FacePose.RIGHT -> PoseArrow(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    color = guideColor,
                    modifier = Modifier.offset(
                        x = ovalLeft + ovalW + 8.dp,
                        y = ovalTop + ovalH / 2 - arrowSize / 2
                    )
                )
                FacePose.UP -> PoseArrow(
                    icon = Icons.Default.ArrowUpward,
                    color = guideColor,
                    modifier = Modifier.offset(
                        x = ovalLeft + ovalW / 2 - arrowSize / 2,
                        y = (ovalTop - arrowSize - 8.dp).coerceAtLeast(12.dp)
                    )
                )
                FacePose.DOWN -> PoseArrow(
                    icon = Icons.Default.ArrowDownward,
                    color = guideColor,
                    modifier = Modifier.offset(
                        x = ovalLeft + ovalW / 2 - arrowSize / 2,
                        y = ovalTop + ovalH + 8.dp
                    )
                )
            }
        }

        val captionModifier = if (captionBelowOval) {
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = ovalTop + ovalH + 10.dp)
                .widthIn(max = maxWidth - 24.dp)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        }

        if (caption.isNotBlank()) {
            Text(
                caption,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = captionModifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PoseArrow(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(2.dp, color.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun LookStraightCue(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(2.dp, color.copy(alpha = 0.9f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Face, null, tint = color, modifier = Modifier.size(28.dp))
            Text("LOOK HERE", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
