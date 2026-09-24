package com.sahaaya.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sahaaya.app.SessionState
import com.sahaaya.common.components.LoadingState
import com.sahaaya.domain.model.Role
import com.sahaaya.feature.auth.AuthRoutes
import com.sahaaya.feature.auth.authGraph
import com.sahaaya.feature.dashboard.DashboardRoutes
import com.sahaaya.feature.dashboard.caregiverDashboard
import com.sahaaya.feature.dashboard.patientDashboard
import com.sahaaya.feature.dashboard.timelineScreens
import com.sahaaya.feature.medication.MedicationRoutes
import com.sahaaya.feature.medication.medicationScreens
import com.sahaaya.feature.monitoring.MonitoringRoutes
import com.sahaaya.feature.monitoring.monitoringScreens
import com.sahaaya.feature.pairing.PairingRoutes
import com.sahaaya.feature.pairing.pairingScreens
import com.sahaaya.feature.profile.ProfileRoutes
import com.sahaaya.feature.profile.profileScreens

private const val SPLASH_ROUTE = "splash"

/**
 * The single navigation graph for the app.
 *
 * Routing is driven by [SessionState] rather than by screens calling each other
 * on success. Registration, sign-in, sign-out and a token expiring mid-session
 * all produce the same signal, and this one effect decides what happens - so
 * there is no path by which a signed-out user is left sitting on a dashboard,
 * or a signed-in patient lands on the caregiver side.
 *
 * This file is also the only place features are joined to each other. A
 * dashboard that opens a profile screen does so through a lambda supplied here,
 * which is what keeps `:feature:dashboard` from depending on `:feature:profile`.
 */
@Composable
fun SahaayaNavHost(
    sessionState: SessionState,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(sessionState) {
        when (sessionState) {
            SessionState.Resolving -> Unit

            SessionState.SignedOut -> navController.navigate(AuthRoutes.GRAPH) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }

            is SessionState.SignedIn -> {
                val destination = when (sessionState.user.role) {
                    Role.PATIENT -> DashboardRoutes.PATIENT
                    Role.CAREGIVER -> DashboardRoutes.CAREGIVER
                }
                navController.navigate(destination) {
                    // Clearing the whole back stack is deliberate: pressing back
                    // from a dashboard must not return to a sign-in screen for a
                    // session that is now live.
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = SPLASH_ROUTE,
        modifier = modifier,
    ) {
        composable(SPLASH_ROUTE) {
            LoadingState(label = "Starting Sahaaya")
        }

        authGraph(navController)

        patientDashboard(
            onOpenProfile = { navController.navigate(ProfileRoutes.ownPatientProfile()) },
            onOpenEmergencyContacts = {
                navController.navigate(ProfileRoutes.EMERGENCY_CONTACTS)
            },
            onOpenPairing = { navController.navigate(PairingRoutes.SHOW_CODE) },
            onOpenMedications = {
                navController.navigate(MedicationRoutes.ownMedications())
            },
            onOpenSettings = { navController.navigate(MonitoringRoutes.SETTINGS) },
            onOpenReminders = { navController.navigate(MedicationRoutes.ownReminders()) },
            onOpenTrackingStatus = {
                navController.navigate(MonitoringRoutes.TRACKING_STATUS)
            },
            // Always null: the developer "Demo mode" screen has been removed.
            // The patient dashboard already omits the card entirely when this
            // is null, so the entry point disappears without that screen
            // needing to know why.
            onOpenDemoMode = null,
        )

        caregiverDashboard(
            onOpenProfile = { navController.navigate(ProfileRoutes.CAREGIVER) },
            onAddPatient = { navController.navigate(PairingRoutes.REDEEM_CODE) },
            onOpenPatient = { patientId ->
                navController.navigate(ProfileRoutes.patientProfile(patientId))
            },
            onOpenPatientMedications = { patientId ->
                navController.navigate(MedicationRoutes.patientMedications(patientId))
            },
            onOpenTimeline = { navController.navigate(DashboardRoutes.TIMELINE) },
            onOpenEvent = { eventId ->
                navController.navigate(DashboardRoutes.eventDetail(eventId))
            },
            onOpenSafeZone = { patientId ->
                navController.navigate(MonitoringRoutes.safeZone(patientId))
            },
            onOpenLiveTracking = { patientId, patientName ->
                navController.navigate(MonitoringRoutes.liveTracking(patientId, patientName))
            },
            onOpenPatientReminders = { patientId ->
                navController.navigate(MedicationRoutes.patientReminders(patientId))
            },
        )

        timelineScreens(
            onNavigateBack = back,
            onOpenEvent = { eventId ->
                navController.navigate(DashboardRoutes.eventDetail(eventId))
            },
        )

        profileScreens(onNavigateBack = back)

        medicationScreens(
            onNavigateBack = back,
            // "Medicine" inside Reminders opens the existing medicines screen
            // for the same patient: SELF for the patient, the id for a caregiver.
            onOpenMedicines = { patientId ->
                navController.navigate(
                    if (patientId == MedicationRoutes.SELF) {
                        MedicationRoutes.ownMedications()
                    } else {
                        MedicationRoutes.patientMedications(patientId)
                    },
                )
            },
        )

        monitoringScreens(
            onNavigateBack = back,
            onSetSafeZone = { patientId ->
                navController.navigate(MonitoringRoutes.safeZone(patientId))
            },
        )

        pairingScreens(
            onNavigateBack = back,
            onLinked = {
                navController.navigate(DashboardRoutes.CAREGIVER) {
                    popUpTo(DashboardRoutes.CAREGIVER) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
}
