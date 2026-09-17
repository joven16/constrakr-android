package com.constrakr.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.constrakr.domain.FacePose
import com.constrakr.face.JpegImageUtils

@Composable
fun EmployeePhotosPanel(
    profileJpeg: ByteArray?,
    posePhotos: Map<FacePose, ByteArray>,
    showEnrollmentPoses: Boolean = posePhotos.isNotEmpty(),
    modifier: Modifier = Modifier
) {
    val profileBitmap = remember(profileJpeg) {
        profileJpeg?.let { JpegImageUtils.decodeDisplayBitmap(it) }
    }
    val poseBitmaps = remember(posePhotos) {
        posePhotos.mapValues { (_, jpeg) -> JpegImageUtils.decodeDisplayBitmap(jpeg) }
    }
    var viewerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewerTitle by remember { mutableStateOf("") }

    PhotoViewerDialog(
        bitmap = viewerBitmap,
        title = viewerTitle,
        onDismiss = { viewerBitmap = null }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(120.dp)
                .then(
                    if (profileBitmap != null) {
                        Modifier.clickable {
                            viewerBitmap = profileBitmap
                            viewerTitle = "Profile photo"
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (profileBitmap != null) {
                Image(
                    bitmap = profileBitmap.asImageBitmap(),
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "Profile photo",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showEnrollmentPoses) {
            ConsTrakrCard {
                Text("Face scan poses", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tap a photo to enlarge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FacePose.enrollmentOrder.forEach { pose ->
                        val bitmap = poseBitmaps[pose]
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(108.dp)
                                    .then(
                                        if (bitmap != null) {
                                            Modifier.clickable {
                                                viewerBitmap = bitmap
                                                viewerTitle = pose.displayName
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = pose.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        "—",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 24.dp),
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                pose.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
