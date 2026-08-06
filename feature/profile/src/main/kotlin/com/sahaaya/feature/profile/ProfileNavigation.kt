package com.sahaaya.feature.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sahaaya.feature.profile.caregiver.CaregiverProfileScreen
import com.sahaaya.feature.profile.emergency.EmergencyContactsScreen
import com.sahaaya.feature.profile.patient.PatientProfileScreen

object ProfileRoutes {
    const val ARG_PATIENT_ID = "patientId"

    /** Sentinel meaning "the signed-in user", so one route serves both cases. */
    const val SELF = "self"

    private const val PATIENT_BASE = "profile/patient"
    const val PATIENT = "$PATIENT_BASE/{$ARG_PATIENT_ID}"
    const val CAREGIVER = "profile/caregiver"
    const val EMERGENCY_CONTACTS = "profile/emergency"

    /** The signed-in patient editing their own profile. */
    fun ownPatientProfile(): String = "$PATIENT_BASE/$SELF"

    /** A caregiver viewing a patient they are linked to. */
    fun patientProfile(patientId: String): String = "$PATIENT_BASE/$patientId"
}

fun NavGraphBuilder.profileScreens(onNavigateBack: () -> Unit) {
    composable(
        route = ProfileRoutes.PATIENT,
        arguments = listOf(
            navArgument(ProfileRoutes.ARG_PATIENT_ID) {
                type = NavType.StringType
                defaultValue = ProfileRoutes.SELF
            },
        ),
    ) {
        PatientProfileScreen(onNavigateBack = onNavigateBack)
    }

    composable(ProfileRoutes.CAREGIVER) {
        CaregiverProfileScreen(onNavigateBack = onNavigateBack)
    }

    composable(ProfileRoutes.EMERGENCY_CONTACTS) {
        EmergencyContactsScreen(onNavigateBack = onNavigateBack)
    }
}
