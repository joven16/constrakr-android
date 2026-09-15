package com.constrakr.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import com.constrakr.MainActivity
import com.constrakr.util.AppLog

/**
 * Device Owner + Lock Task Mode — single-app company kiosk (Galaxy S8 / A17).
 * Do not use ordinary screen pinning for production.
 */
class KioskController(context: Context) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(appContext, ConsTrakrDeviceAdminReceiver::class.java)
    private val settings = KioskSettings(appContext)
    private val maintenance = KioskMaintenanceSession(appContext)

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(appContext.packageName)

    val isKioskEnabled: Boolean
        get() = settings.isKioskEnabled

    fun shouldEnterLockTask(): Boolean =
        isDeviceOwner && settings.isKioskEnabled && !maintenance.isActive.value

    /** One-time Device Owner provisioning — lock task packages, home app, keyguard. */
    fun configureDeviceOwner(activity: Activity) {
        if (!isDeviceOwner) return
        settings.ensureDefaultPinIfNeeded()
        dpm.setLockTaskPackages(admin, arrayOf(appContext.packageName))
        applyLockTaskFeatures()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { dpm.setStatusBarDisabled(admin, false) }
                .onFailure { AppLog.w("Status bar enable failed: ${it.message}") }
        }
        runCatching { dpm.setKeyguardDisabled(admin, true) }
            .onFailure { AppLog.w("Keyguard disable failed: ${it.message}") }
        setAsDefaultLauncher(activity)
        AppLog.d("Device Owner kiosk policies applied")
    }

    fun enterKioskIfNeeded(activity: Activity): Boolean {
        maintenance.refresh()
        if (!shouldEnterLockTask()) return false
        if (isInLockTask(activity)) return true
        return runCatching {
            enableKiosk(activity)
            true
        }.getOrElse {
            AppLog.w("enterKioskIfNeeded failed: ${it.message}")
            false
        }
    }

    fun enableKiosk(activity: Activity) {
        if (!isDeviceOwner) return
        dpm.setLockTaskPackages(admin, arrayOf(appContext.packageName))
        applyLockTaskFeatures()
        if (!isInLockTask(activity)) {
            activity.startLockTask()
        }
    }

    /** Pause Lock Task so system back works inside sub-screens (Settings, enrollment, etc.). */
    fun showSystemStatusBar() {
        if (!isDeviceOwner) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { dpm.setStatusBarDisabled(admin, false) }
        }
    }

    fun pauseLockTaskForNavigation(activity: Activity) {
        maintenance.refresh()
        if (maintenance.isActive.value) return
        if (isInLockTask(activity)) {
            activity.stopLockTask()
        }
    }

    fun exitKioskForMaintenance(activity: Activity) {
        if (isInLockTask(activity)) {
            activity.stopLockTask()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { dpm.setStatusBarDisabled(admin, false) }
        }
    }

    fun restoreKioskPolicies(activity: Activity) {
        if (!isDeviceOwner) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { dpm.setStatusBarDisabled(admin, false) }
        }
        enterKioskIfNeeded(activity)
    }

    fun isInLockTask(activity: Activity): Boolean {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /** Show clock, battery, and signal while pinned in Lock Task (kiosk). */
    private fun applyLockTaskFeatures() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO)
            }.onFailure { AppLog.w("Lock task features failed: ${it.message}") }
        }
    }

    private fun setAsDefaultLauncher(activity: Activity) {
        val filter = IntentFilter(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            addCategory(android.content.Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(
            admin,
            filter,
            ComponentName(appContext, MainActivity::class.java)
        )
    }
}
