package com.constrakr.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.domain.CheckType
import com.constrakr.domain.SyncStatus
import com.constrakr.ui.components.EmptyState
import com.constrakr.ui.components.SyncPullToRefreshBox
import com.constrakr.ui.theme.CyanAccent
import com.constrakr.ui.theme.ErrorRed
import com.constrakr.ui.theme.SuccessGreen
import com.constrakr.ui.theme.TealPrimary
import com.constrakr.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class AttendanceTotals(
    val assigned: Int = 0,
    val checkInCount: Int = 0,
    val checkOutCount: Int = 0,
    val absent: Int = 0,
    val incomplete: Int = 0,
    val completionScore: Float = 0f
) {
    val coveragePercent: Int?
        get() = if (assigned > 0) ((completionScore / assigned) * 100).toInt() else null

    val completeCount: Int
        get() = maxOf(0, assigned - incomplete - absent)

    val completionFractionLine: String
        get() {
            val score = if (completionScore % 1f == 0f) completionScore.toInt().toString()
            else String.format(Locale.US, "%.1f", completionScore)
            return "$score/$assigned"
        }
}

@Composable
fun DashboardScreen() {
    val container = ConsTrakrApp.instance.container
    val access = container.accessSession
    val siteId = access.effectiveViewSiteId
    val siteTitle = access.effectiveViewSiteTitle
    val employees by container.employeeRepository.observeEmployeesForSite(siteId)
        .collectAsState(initial = emptyList())
    val allEmployees by container.employeeRepository.observeEmployees()
        .collectAsState(initial = emptyList())
    val attendance by container.attendanceRepository.observeAll().collectAsState(initial = emptyList())
    val pendingAttendance by container.attendanceRepository.observePendingCount().collectAsState(initial = 0)

    val (start, end) = remember { dayBounds() }
    val todayTitle = remember {
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
    }
    val todayRecords = remember(attendance, start, end) {
        attendance.filter { it.timestampMillis in start until end }
    }
    val assigned = employees.filter { it.assignedSiteId == siteId }
    val enrolled = assigned.count { it.isEnrolled }
    val unassigned = allEmployees.count { it.assignedSiteId == null }
    val pendingSync = pendingAttendance + allEmployees.count { it.syncStatus == SyncStatus.PENDING }

    val totals = remember(assigned, todayRecords) {
        var checkIn = 0
        var checkOut = 0
        var absent = 0
        var incomplete = 0
        var score = 0f
        for (emp in assigned) {
            val rows = todayRecords.filter { it.employeeId == emp.id }
            val hasIn = rows.any { it.checkType == CheckType.CHECK_IN }
            val hasOut = rows.any { it.checkType == CheckType.CHECK_OUT }
            when {
                hasIn && hasOut -> {
                    checkIn++
                    checkOut++
                    score += 1f
                }
                hasIn -> {
                    checkIn++
                    incomplete++
                    score += 0.5f
                }
                else -> absent++
            }
        }
        AttendanceTotals(
            assigned = assigned.size,
            checkInCount = checkIn,
            checkOutCount = checkOut,
            absent = absent,
            incomplete = incomplete,
            completionScore = score
        )
    }
    val coveragePercent = totals.coveragePercent ?: 0
    val needsAttention = totals.assigned > 0 && (coveragePercent < 70 || totals.absent > 0)

    SyncPullToRefreshBox(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            CyanAccent.copy(alpha = 0.08f)
                        )
                    )
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (siteId == null) {
                    EmptyState(
                        title = if (access.isAdminUnlocked) "No job site selected" else "No default job site",
                        message = if (access.isAdminUnlocked) {
                            "Choose a site from the menu above, or set a default under More → Job Sites."
                        } else {
                            "Set a default site under More → Job sites."
                        }
                    )
                    return@Column
                }

                siteTitle?.let { title ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = TealPrimary)
                        Text(
                            if (access.isViewingNonDefaultSite) "$title (viewing only)" else title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (access.isViewingNonDefaultSite) {
                    access.operatorSiteTitle?.let { defaultTitle ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Scanner still uses $defaultTitle for GPS check-in.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                CoverageHeroCard(totals = totals, todayTitle = todayTitle, rosterCount = assigned.size)

                if (needsAttention) {
                    AttentionCard(totals = totals, coveragePercent = coveragePercent)
                }

                if (totals.assigned > 0 && totals.coveragePercent != null) {
                    TodayProgressCard(totals = totals, coveragePercent = coveragePercent)
                }

                RosterSyncCard(
                    enrolled = enrolled,
                    rosterCount = assigned.size,
                    unassigned = unassigned,
                    pendingSync = pendingSync
                )
            }
        }
    }
}

@Composable
private fun CoverageHeroCard(totals: AttendanceTotals, todayTitle: String, rosterCount: Int) {
    val percent = totals.coveragePercent ?: 0
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CoverageRing(
                percent = percent,
                fractionLine = totals.completionFractionLine,
                hasRoster = totals.assigned > 0
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today's coverage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "$todayTitle · $rosterCount on roster",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (totals.assigned > 0) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    item {
                        HeroChip("Checked in", "${totals.checkInCount}", if (totals.checkInCount > 0) androidx.compose.ui.graphics.Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        HeroChip("Checked out", "${totals.checkOutCount}", if (totals.checkOutCount > 0) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        HeroChip("Absent", "${totals.absent}", if (totals.absent > 0) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        HeroChip("Incomplete", "${totals.incomplete}", if (totals.incomplete > 0) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        HeroChip("Assigned", "${totals.assigned}", MaterialTheme.colorScheme.onSurface)
                    }
                    item {
                        HeroChip(
                            "Complete",
                            "${totals.completeCount}",
                            if (percent >= 90) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverageRing(percent: Int, fractionLine: String, hasRoster: Boolean) {
    val ringColor = coverageColor(percent)
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (hasRoster) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * (percent.coerceIn(0, 100) / 100f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasRoster) {
                Text("${percent}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(fractionLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("—", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeroChip(title: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AttentionCard(totals: AttendanceTotals, coveragePercent: Int) {
    val summary = buildList {
        if (totals.absent > 0) add("${totals.absent} absent")
        if (totals.incomplete > 0) add("${totals.incomplete} incomplete")
    }.joinToString(" · ").ifBlank { "$coveragePercent% coverage" }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WarningOrange.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange)
                Text("Needs attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = WarningOrange)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("$coveragePercent% coverage today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${coveragePercent}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = coverageColor(coveragePercent))
            }
        }
    }
}

@Composable
private fun TodayProgressCard(totals: AttendanceTotals, coveragePercent: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = TealPrimary)
                Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Attendance progress", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "In (${totals.checkInCount}) | Out (${totals.checkOutCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(99.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(coveragePercent.coerceIn(0, 100) / 100f)
                            .height(8.dp)
                            .background(coverageColor(coveragePercent), RoundedCornerShape(99.dp))
                    )
                }
                Text(
                    "$coveragePercent% of roster with full in/out today",
                    style = MaterialTheme.typography.labelSmall,
                    color = coverageColor(coveragePercent)
                )
            }
        }
    }
}

@Composable
private fun RosterSyncCard(enrolled: Int, rosterCount: Int, unassigned: Int, pendingSync: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.People, contentDescription = null, tint = TealPrimary)
                Text("Roster & sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                item {
                    MetricTile(
                        title = "Enrolled",
                        value = "$enrolled/$rosterCount",
                        subtitle = "Face-ready for scanner",
                        tint = if (enrolled == rosterCount && rosterCount > 0) SuccessGreen else androidx.compose.ui.graphics.Color(0xFF6366F1)
                    )
                }
                item {
                    MetricTile(
                        title = "Not enrolled",
                        value = "${maxOf(0, rosterCount - enrolled)}",
                        subtitle = "Need face registration",
                        tint = if (enrolled < rosterCount) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    MetricTile(
                        title = "Unassigned",
                        value = "$unassigned",
                        subtitle = "No job site on roster",
                        tint = if (unassigned > 0) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    MetricTile(
                        title = "Pending sync",
                        value = "$pendingSync",
                        subtitle = if (pendingSync > 0) "Pull down to sync" else "Up to date",
                        tint = if (pendingSync > 0) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(title: String, value: String, subtitle: String, tint: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun coverageColor(percent: Int): androidx.compose.ui.graphics.Color = when {
    percent >= 90 -> SuccessGreen
    percent >= 70 -> WarningOrange
    else -> ErrorRed
}

private fun dayBounds(): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 1)
    return start to cal.timeInMillis
}
