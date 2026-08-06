package com.sahaaya.feature.medication

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sahaaya.feature.medication.list.MedicationScreen

object MedicationRoutes {
    const val ARG_PATIENT_ID = "patientId"

    /** Sentinel meaning "the signed-in user", so one route serves both roles. */
    const val SELF = "self"

    private const val BASE = "medication"
    const val LIST = "$BASE/{$ARG_PATIENT_ID}"

    /** The patient's own medicines, editable. */
    fun ownMedications(): String = "$BASE/$SELF"

    /** A caregiver's read-only view of a linked patient's medicines. */
    fun patientMedications(patientId: String): String = "$BASE/$patientId"
}

fun NavGraphBuilder.medicationScreens(onNavigateBack: () -> Unit) {
    composable(
        route = MedicationRoutes.LIST,
        arguments = listOf(
            navArgument(MedicationRoutes.ARG_PATIENT_ID) {
                type = NavType.StringType
                defaultValue = MedicationRoutes.SELF
            },
        ),
    ) {
        MedicationScreen(onNavigateBack = onNavigateBack)
    }
}
