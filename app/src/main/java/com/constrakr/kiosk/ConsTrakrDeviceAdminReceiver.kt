package com.constrakr.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ConsTrakrDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        KioskSettings(context).ensureDefaultPinIfNeeded()
        Toast.makeText(context, "ConsTrakr device admin enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "ConsTrakr device admin disabled", Toast.LENGTH_SHORT).show()
    }
}
