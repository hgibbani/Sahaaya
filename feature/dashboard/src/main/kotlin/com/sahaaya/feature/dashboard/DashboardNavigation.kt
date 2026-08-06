package com.sahaaya.feature.dashboard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sahaaya.feature.dashboard.caregiver.CaregiverDashboardScreen
import com.sahaaya.feature.dashboard.patient.PatientDashboardScreen
import com.sahaaya.feature.dashboard.timeline.EventDetailScreen
import com.sahaaya.feature.dashboard.timeline.TimelineScreen

/**
 * Dashboard routes.
 *
 * Callbacks are passed in from `:app` rather than resolved here, because a
 * dashboard links to screens owned by other feature modules. Keeping the wiring
 * in the app module is what stops the feature modules depending on each other.
 */
object DashboardRoutes {
    const val PATIENT = "dashboard/patient"
    const val CAREGIVER = "dashboard/caregiver"
    const val TIMELINE = "dashboard/timeline"

    const val ARG_EVENT_ID = "eventId"
    private const val EVENT_BASE = "dashboard/event"
    const val EVENT_DETAIL = "$EVENT_BASE/{$ARG_EVENT_ID}"

    fun eventDetail(eventId: String): String = "$EVENT_BASE/$eventId"
}

fun NavGraphBuilder.patientDashboard(
    onOpenProfile: () -> Unit,
    onOpenEmergencyContacts: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenMedications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDemoMode: (() -> Unit)?,
) {
    composable(DashboardRoutes.PATIENT) {
        PatientDashboardScreen(
            onOpenProfile = onOpenProfile,
            onOpenEmergencyContacts = onOpenEmergencyContacts,
            onOpenPairing = onOpenPairing,
            onOpenMedications = onOpenMedications,
            onOpenSettings = onOpenSettings,
            onOpenDemoMode = onOpenDemoMode,
        )
    }
}

fun NavGraphBuilder.caregiverDashboard(
    onOpenProfile: () -> Unit,
    onAddPatient: () -> Unit,
    onOpenPatient: (patientId: String) -> Unit,
    onOpenPatientMedications: (patientId: String) -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
) {
    composable(DashboardRoutes.CAREGIVER) {
        CaregiverDashboardScreen(
            onOpenProfile = onOpenProfile,
            onAddPatient = onAddPatient,
            onOpenPatient = onOpenPatient,
            onOpenPatientMedications = onOpenPatientMedications,
            onOpenTimeline = onOpenTimeline,
            onOpenEvent = onOpenEvent,
        )
    }
}

fun NavGraphBuilder.timelineScreens(
    onNavigateBack: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
) {
    composable(DashboardRoutes.TIMELINE) {
        TimelineScreen(onNavigateBack = onNavigateBack, onOpenEvent = onOpenEvent)
    }

    composable(
        route = DashboardRoutes.EVENT_DETAIL,
        arguments = listOf(
            navArgument(DashboardRoutes.ARG_EVENT_ID) { type = NavType.StringType },
        ),
    ) {
        EventDetailScreen(onNavigateBack = onNavigateBack)
    }
}
