package com.constrakr.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.constrakr.MainActivity

/**
 * Auto-launch ConsTrakr after reboot when provisioned as Device Owner kiosk.
 */
class KioskBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        val kiosk = KioskController(context)
        val settings = KioskSettings(context)
        if (!kiosk.isDeviceOwner || !settings.autoStartOnBoot || !settings.isKioskEnabled) {
            return
        }
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(launch)
    }
}
