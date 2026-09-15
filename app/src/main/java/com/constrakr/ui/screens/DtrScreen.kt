package com.constrakr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.constrakr.ConsTrakrApp
import com.constrakr.domain.CheckType
import com.constrakr.ui.components.EmployeeProfileAvatar
import com.constrakr.ui.components.EmptyState
import com.constrakr.ui.components.SyncPullToRefreshBox
import com.constrakr.ui.theme.SuccessGreen
import com.constrakr.ui.theme.TealPrimary
import com.constrakr.ui.theme.WarningOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class DtrRow(
    val employeeId: UUID,
    val name: String,
    val code: String,
    val timeIn: String?,
    val timeOut: String?,
    val timeInAdjusted: Boolean,
    val timeOutAdjusted: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtrScreen() {
    val container = ConsTrakrApp.instance.container
    val scope = rememberCoroutineScope()
    val siteId = container.accessSession.effectiveViewSiteId
    val siteTitle = container.accessSession.effectiveViewSiteTitle
    val employees by container.employeeRepository.observeEmployeesForSite(siteId)
        .collectAsState(initial = emptyList())
    val allAttendance by container.attendanceRepository.observeAll().collectAsState(initial = emptyList())
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showPicker by remember { mutableStateOf(false) }
    var profilePhotos by remember { mutableStateOf<Map<UUID, ByteArray?>>(emptyMap()) }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        container.syncCoordinator.syncAttendanceOnly(selectedDate.toLocalDate())
    }

    LaunchedEffect(employees) {
        withContext(Dispatchers.IO) {
            for (emp in employees) {
                if (container.employeeRepository.getProfilePhoto(emp.id) == null) {
                    container.syncCoordinator.ensureLocalProfilePhoto(emp.id)
                }
            }
        }
        profilePhotos = withContext(Dispatchers.IO) {
            employees.associate { emp ->
                emp.id to container.employeeRepository.getProfilePhoto(emp.id)
            }
        }
    }

    val (dayStart, dayEnd) = dayBounds(selectedDate)
    val dayRecords = remember(allAttendance, dayStart, dayEnd) {
        allAttendance.filter { it.timestampMillis in dayStart until dayEnd }
    }
    val rows = remember(employees, dayRecords, timeFmt) {
        employees.sortedBy { it.fullName.lowercase() }.map { emp ->
            val mine = dayRecords.filter { it.employeeId == emp.id }
            val inTime = mine.filter { it.checkType == CheckType.CHECK_IN }.minByOrNull { it.timestampMillis }
            val outTime = mine.filter { it.checkType == CheckType.CHECK_OUT }.maxByOrNull { it.timestampMillis }
            DtrRow(
                employeeId = emp.id,
                name = emp.fullName,
                code = emp.employeeCode,
                timeIn = inTime?.let { timeFmt.format(Date(it.timestampMillis)) },
                timeOut = outTime?.let { timeFmt.format(Date(it.timestampMillis)) },
                timeInAdjusted = inTime?.notes?.contains("manual dtr", true) == true,
                timeOutAdjusted = outTime?.notes?.contains("manual dtr", true) == true
            )
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDate = it }
                    showPicker = false
                    scope.launch { container.syncCoordinator.syncAttendanceOnly(selectedDate.toLocalDate()) }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    SyncPullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        focusDate = selectedDate.toLocalDate()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Surface(color = TealPrimary.copy(alpha = 0.08f)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TealPrimary)
                        Column(Modifier.weight(1f)) {
                            Text(
                                siteTitle ?: "Daily time record",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Pull down to sync punches for this date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Daily Time Record",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showPicker = true }) {
                        Text(dateFmt.format(Date(selectedDate)), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            when {
                siteId == null -> {
                    item { EmptyState("No site", "Configure a job site to view DTR.") }
                }
                rows.isEmpty() -> {
                    item { EmptyState("No DTR for this date", "No assigned employees or punches yet.") }
                }
                else -> {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Employee",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Time In",
                                modifier = Modifier.width(68.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen
                            )
                            Text(
                                "Time Out",
                                modifier = Modifier.width(68.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = WarningOrange
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    items(rows, key = { it.employeeId.toString() }) { row ->
                        DtrEmployeeRow(
                            row = row,
                            profileJpeg = profilePhotos[row.employeeId]
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DtrEmployeeRow(row: DtrRow, profileJpeg: ByteArray?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmployeeProfileAvatar(
            name = row.name,
            profileJpeg = profileJpeg,
            size = 32.dp
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DtrPunchCell(time = row.timeIn, adjusted = row.timeInAdjusted, tint = SuccessGreen)
        DtrPunchCell(time = row.timeOut, adjusted = row.timeOutAdjusted, tint = WarningOrange)
    }
}

@Composable
private fun DtrPunchCell(
    time: String?,
    adjusted: Boolean,
    tint: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier.width(68.dp).height(36.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (time != null) {
            Text(
                time,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1
            )
            if (adjusted) {
                Text(
                    "Adj",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = WarningOrange,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                "—",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun dayBounds(millis: Long): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 1)
    return start to cal.timeInMillis
}

private fun Long.toLocalDate(): java.time.LocalDate {
    val cal = Calendar.getInstance()
    cal.timeInMillis = this
    return java.time.LocalDate.of(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}
