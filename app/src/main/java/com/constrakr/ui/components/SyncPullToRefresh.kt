package com.constrakr.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.constrakr.ConsTrakrApp
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Pull-to-refresh — mirrors iOS `.refreshable { await syncNow() }`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPullToRefreshBox(
    modifier: Modifier = Modifier,
    focusDate: LocalDate? = null,
    content: @Composable () -> Unit
) {
    val sync = ConsTrakrApp.instance.container.syncCoordinator
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    if (focusDate != null) {
                        sync.syncAttendanceOnly(focusDate)
                    } else {
                        sync.syncPending()
                    }
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
        }
    }
}
