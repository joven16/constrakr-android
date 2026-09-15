package com.constrakr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.constrakr.face.JpegImageUtils
import com.constrakr.ui.theme.TealPrimary

@Composable
fun EmployeeProfileAvatar(
    name: String,
    profileJpeg: ByteArray?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val bitmap = remember(profileJpeg) {
        profileJpeg?.let { JpegImageUtils.decodeDisplayBitmap(it) }
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (bitmap != null) MaterialTheme.colorScheme.surfaceVariant
                else TealPrimary.copy(alpha = 0.14f)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                initialsFor(name),
                style = MaterialTheme.typography.labelSmall,
                color = TealPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun initialsFor(name: String): String =
    name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifBlank { "?" }
