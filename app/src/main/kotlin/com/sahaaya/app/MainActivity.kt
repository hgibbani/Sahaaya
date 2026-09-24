package com.sahaaya.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.app.navigation.SahaayaNavHost
import com.sahaaya.common.theme.SahaayaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's only activity.
 *
 * Notification permission is requested on launch rather than at the moment an
 * alert is needed: by the time a fall has been detected it is too late to be
 * asking for permission to report it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Declining is respected; the app degrades rather than failing. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ensureMonitoringPermissions()

        setContent {
            SahaayaTheme {
                val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SahaayaNavHost(sessionState = sessionState)
                }
            }
        }
    }

    /**
     * Asks for everything the monitoring pipeline needs, in one prompt, on launch.
     *
     * ACTIVITY_RECOGNITION is not optional decoration. From Android 14 a
     * foreground service declaring `foregroundServiceType="health"` - which is
     * what the fall detector runs as - may only start if the app holds one of
     * the health-related permissions at runtime. Without it `startForeground`
     * throws and fall detection never starts at all, which is a silent failure
     * of the app's single most important feature.
     *
     * Location is requested here too so that an alert carries coordinates.
     * Background location is deliberately *not* requested at launch: Android
     * refuses it unless foreground location is already granted, so the
     * monitoring settings screen asks for it separately when a safe zone is set.
     */
    private fun ensureMonitoringPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val missing = wanted.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissions.launch(missing.toTypedArray())
        }
    }
}
