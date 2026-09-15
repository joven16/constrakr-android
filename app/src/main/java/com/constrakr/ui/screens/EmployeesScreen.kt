package com.constrakr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.domain.Employee
import com.constrakr.ui.components.EmptyState
import com.constrakr.ui.components.SiteHeader
import com.constrakr.ui.components.SyncPullToRefreshBox
import com.constrakr.ui.components.rememberAdminGate
import com.constrakr.ui.theme.SuccessGreen
import com.constrakr.ui.theme.TealPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesScreen(
    onRegister: () -> Unit,
    onOpenEmployee: (UUID) -> Unit
) {
    val container = ConsTrakrApp.instance.container
    val scope = rememberCoroutineScope()
    val adminGate = rememberAdminGate()
    val siteId = container.accessSession.effectiveViewSiteId
    val employees by container.employeeRepository.observeEmployeesForSite(siteId)
        .collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Employee?>(null) }
    val filtered = remember(employees, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) employees
        else employees.filter {
            it.fullName.lowercase().contains(q) ||
                it.employeeCode.lowercase().contains(q) ||
                it.department.lowercase().contains(q)
        }
    }

    val blockReason = when {
        container.deviceStore.isBlocked -> "Device blocked on server."
        container.accessSession.operatorSiteId == null -> "Sync job sites first."
        !ConsTrakrApp.instance.adaFaceRecognizer.isReady -> "Face model missing."
        else -> null
    }

    fun confirmDelete(employee: Employee) {
        adminGate.withAdmin { pendingDelete = employee }
    }

    pendingDelete?.let { employee ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete employee?") },
            text = {
                Text(
                    "${employee.fullName} (${employee.employeeCode}) will be removed from this device, " +
                        "including face enrollment data. If synced to the server, their record stays on the server " +
                        "as Removed from app (attendance history preserved). The employee ID can be used again " +
                        "for a new registration."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = employee
                        pendingDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                container.syncCoordinator.deleteEmployee(toDelete)
                            }
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onRegister, containerColor = TealPrimary) {
                Icon(Icons.Default.Add, contentDescription = "Register")
            }
        }
    ) { padding ->
        SyncPullToRefreshBox(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    SiteHeader(
                        container.accessSession.effectiveViewSiteTitle ?: "Employees",
                        "${filtered.size} enrolled"
                    )
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
                blockReason?.let { reason ->
                    item {
                        Text(reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                adminGate.blockedMessage?.let { message ->
                    item {
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Text(
                        "Tap trash to delete · admin code required",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    siteId == null -> {
                        item {
                            EmptyState("No site", "Sync and set a default job site.", "Register", onRegister)
                        }
                    }
                    filtered.isEmpty() -> {
                        item {
                            EmptyState("No employees", "Tap + to register.", "Register", onRegister)
                        }
                    }
                    else -> {
                        items(filtered, key = { it.id }) { employee ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        confirmDelete(employee)
                                        false
                                    } else {
                                        true
                                    }
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.error)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onError
                                        )
                                    }
                                }
                            ) {
                                EmployeeRow(
                                    employee = employee,
                                    onOpenEmployee = onOpenEmployee,
                                    onDelete = { confirmDelete(employee) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeRow(
    employee: Employee,
    onOpenEmployee: (UUID) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpenEmployee(employee.id) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                employee.fullName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${employee.employeeCode} · ${employee.department}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if (employee.isEnrolled) "OK" else "No face",
            style = MaterialTheme.typography.labelSmall,
            color = if (employee.isEnrolled) SuccessGreen else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 4.dp)
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete employee",
                tint = MaterialTheme.colorScheme.error
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
