package com.sahaaya.feature.monitoring

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sahaaya.feature.monitoring.safezone.CaregiverSafeZoneScreen
import com.sahaaya.feature.monitoring.safezone.CaregiverSafeZoneViewModel
import com.sahaaya.feature.monitoring.settings.MonitoringSettingsScreen
import com.sahaaya.feature.monitoring.tracking.LiveTrackingScreen
import com.sahaaya.feature.monitoring.tracking.PatientTrackingStatusScreen

object MonitoringRoutes {
    const val SETTINGS = "monitoring/settings"

    const val ARG_PATIENT_ID = CaregiverSafeZoneViewModel.ARG_PATIENT_ID
    private const val SAFE_ZONE_BASE = "monitoring/safezone"
    const val SAFE_ZONE = "$SAFE_ZONE_BASE/{$ARG_PATIENT_ID}"

    fun safeZone(patientId: String): String = "$SAFE_ZONE_BASE/$patientId"

    /** Caregiver-facing live map. */
    const val ARG_PATIENT_NAME = "patientName"
    private const val LIVE_TRACKING_BASE = "monitoring/tracking"
    const val LIVE_TRACKING = "$LIVE_TRACKING_BASE/{$ARG_PATIENT_ID}/{$ARG_PATIENT_NAME}"

    fun liveTracking(patientId: String, patientName: String): String =
        "$LIVE_TRACKING_BASE/$patientId/${Uri.encode(patientName.ifBlank { "Patient" })}"

    /** Patient-facing tracking status. No stop control lives behind it. */
    const val TRACKING_STATUS = "monitoring/tracking/status"
}

/**
 * The monitoring graph.
 *
 * Only the settings screen is registered. The fall countdown and the developer
 * "Demo mode" screen were removed from the interface: fall detection is
 * switched off for this release (see
 * [com.sahaaya.core.demo.FeatureScope.FALL_DETECTION_ACTIVE]), so a countdown
 * could never be shown, and a screen that fabricates alerts has no place in a
 * build people are actually using.
 *
 * The detection pipeline itself is untouched - `FallDetectionEngine`,
 * `FallSensorMonitor`, `MonitoringService`, `ReportFallUseCase` and
 * `CancelFallAlertUseCase` all remain. Re-enabling the feature means turning the
 * flag back on **and** restoring a confirmation UI, because without one a
 * detected fall would be reported with no way for the patient to withdraw it.
 */
fun NavGraphBuilder.monitoringScreens(
    onNavigateBack: () -> Unit,
    onSetSafeZone: (String) -> Unit = {},
) {
    composable(MonitoringRoutes.SETTINGS) {
        MonitoringSettingsScreen(onNavigateBack = onNavigateBack)
    }

    // Caregiver-facing: draws the boundary that the patient's phone is judged
    // against. Keyed by patient id so a caregiver with several patients gets a
    // separate zone for each.
    composable(
        route = MonitoringRoutes.SAFE_ZONE,
        arguments = listOf(
            navArgument(MonitoringRoutes.ARG_PATIENT_ID) { type = NavType.StringType },
        ),
    ) {
        CaregiverSafeZoneScreen(onNavigateBack = onNavigateBack)
    }

    // Caregiver-facing: the live map, start/stop control and route.
    composable(
        route = MonitoringRoutes.LIVE_TRACKING,
        arguments = listOf(
            navArgument(MonitoringRoutes.ARG_PATIENT_ID) { type = NavType.StringType },
            navArgument(MonitoringRoutes.ARG_PATIENT_NAME) { type = NavType.StringType },
        ),
    ) { entry ->
        LiveTrackingScreen(
            patientName = entry.arguments
                ?.getString(MonitoringRoutes.ARG_PATIENT_NAME)
                .orEmpty(),
            onNavigateBack = onNavigateBack,
            onSetSafeZone = onSetSafeZone,
        )
    }

    // Patient-facing: what is happening and who started it. Read-only.
    composable(MonitoringRoutes.TRACKING_STATUS) {
        PatientTrackingStatusScreen(onNavigateBack = onNavigateBack)
    }
}
