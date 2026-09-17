package com.constrakr.admin

import com.constrakr.domain.DeviceStore
import com.constrakr.domain.Employee
import com.constrakr.domain.JobSite
import com.constrakr.domain.JobSiteStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AppAccessSession(
    private val jobSiteStore: JobSiteStore,
    private val deviceStore: DeviceStore
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val isAdminUnlocked: Boolean
        get() {
            val until = _state.value.unlockedUntilMillis ?: return false
            if (System.currentTimeMillis() >= until) {
                lock()
                return false
            }
            return true
        }

    val operatorSiteId: UUID?
        get() = jobSiteStore.defaultSiteId ?: jobSiteStore.defaultSite?.id

    val operatorSiteTitle: String?
        get() = operatorSiteId?.let { jobSiteStore.assignedSiteLabel(it) }

    val effectiveViewSiteId: UUID?
        get() {
            if (isAdminUnlocked) {
                _state.value.adminViewSiteId?.let { id ->
                    if (jobSiteStore.site(id) != null) return id
                }
            }
            return operatorSiteId
        }

    val effectiveViewSiteTitle: String?
        get() = effectiveViewSiteId?.let { jobSiteStore.assignedSiteLabel(it) }

    val isViewingNonDefaultSite: Boolean
        get() {
            val viewId = _state.value.adminViewSiteId ?: return false
            val defaultId = operatorSiteId ?: return false
            return isAdminUnlocked && viewId != defaultId
        }

    val selectableSites: List<JobSite>
        get() = jobSiteStore.allSites.filter { it.hasCoordinate }

    fun unlock(operatorName: String?) {
        _state.value = _state.value.copy(
            unlockedUntilMillis = System.currentTimeMillis() + UNLOCK_MS,
            unlockedOperatorName = operatorName
        )
    }

    fun lock() {
        _state.value = SessionState()
    }

    fun setAdminViewSite(siteId: UUID) {
        _state.value = _state.value.copy(adminViewSiteId = siteId)
    }

    fun resetAdminViewSite() {
        _state.value = _state.value.copy(adminViewSiteId = null)
    }

    fun canRegisterEmployee(): Boolean = operatorSiteId != null && !deviceStore.isBlocked

    fun canEditEmployee(employee: Employee): Boolean {
        val siteId = operatorSiteId ?: return false
        if (isAdminUnlocked) return true
        if (employee.assignedSiteId == siteId) return true
        if (employee.assignedSiteId == null) return true
        return false
    }

    fun canDeleteEmployees(): Boolean = isAdminUnlocked

    fun canManageJobSites(): Boolean = isAdminUnlocked

    fun canAccessAdminSettings(): Boolean = isAdminUnlocked

    data class SessionState(
        val unlockedUntilMillis: Long? = null,
        val unlockedOperatorName: String? = null,
        val adminViewSiteId: UUID? = null
    ) {
        fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
            val until = unlockedUntilMillis ?: return false
            return nowMillis < until
        }
    }

    companion object {
        private const val UNLOCK_MS = 15 * 60 * 1000L
    }
}
