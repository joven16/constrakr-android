package com.constrakr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.constrakr.kiosk.KioskController
import com.constrakr.ui.ConsTrakrNavHost
import com.constrakr.ui.theme.ConsTrakrTheme

class MainActivity : ComponentActivity() {
    lateinit var kioskController: KioskController
        private set

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Scanner/Enrollment screens handle denial */ }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Geofence screens show errors if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kioskController = KioskController(this)
        if (kioskController.isDeviceOwner) {
            kioskController.configureDeviceOwner(this)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        setContent {
            ConsTrakrTheme(themeSettings = ConsTrakrApp.instance.container.themeSettings) {
                ConsTrakrNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ConsTrakrApp.instance.container.kioskMaintenanceSession.refresh()
        if (kioskController.isDeviceOwner) {
            kioskController.showSystemStatusBar()
        }
        kioskController.enterKioskIfNeeded(this)
    }
}
