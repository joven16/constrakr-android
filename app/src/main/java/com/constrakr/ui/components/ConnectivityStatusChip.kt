package com.constrakr.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.ui.theme.SuccessGreen

enum class ConnectivityChipVariant {
    Default,
    OnDark
}

@Composable
fun ConnectivityStatusChip(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    variant: ConnectivityChipVariant = ConnectivityChipVariant.Default
) {
    val online by ConsTrakrApp.instance.container.networkMonitor.isOnline.collectAsState()
    val label = if (online) "Online" else "Offline"
    val tint = when (variant) {
        ConnectivityChipVariant.Default ->
            if (online) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
        ConnectivityChipVariant.OnDark ->
            if (online) SuccessGreen else Color.White.copy(alpha = 0.75f)
    }
    val surfaceColor = when (variant) {
        ConnectivityChipVariant.Default ->
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
        ConnectivityChipVariant.OnDark ->
            Color.Black.copy(alpha = 0.45f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = surfaceColor,
        shadowElevation = if (compact) 0.dp else 2.dp
    ) {
        Row(
            Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 3.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (online) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
            if (!compact) {
                Text(
                    label,
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tint
                )
            }
        }
    }
}
