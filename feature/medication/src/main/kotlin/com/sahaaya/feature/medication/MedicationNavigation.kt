package com.sahaaya.feature.medication

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sahaaya.feature.medication.list.MedicationScreen
import com.sahaaya.feature.medication.reminders.RemindersScreen

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

    // --- Shared reminders --------------------------------------------------
    // Same SELF convention as medicines, so one screen serves the patient
    // (their own list) and a caregiver (a linked patient's list).
    private const val REMINDERS_BASE = "reminders"
    const val REMINDERS = "$REMINDERS_BASE/{$ARG_PATIENT_ID}"

    fun ownReminders(): String = "$REMINDERS_BASE/$SELF"

    fun patientReminders(patientId: String): String = "$REMINDERS_BASE/$patientId"
}

fun NavGraphBuilder.medicationScreens(
    onNavigateBack: () -> Unit,
    onOpenMedicines: (patientId: String) -> Unit = {},
) {
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

    composable(
        route = MedicationRoutes.REMINDERS,
        arguments = listOf(
            navArgument(MedicationRoutes.ARG_PATIENT_ID) {
                type = NavType.StringType
                defaultValue = MedicationRoutes.SELF
            },
        ),
    ) { entry ->
        val patientArg = entry.arguments?.getString(MedicationRoutes.ARG_PATIENT_ID)
            ?: MedicationRoutes.SELF
        RemindersScreen(
            onNavigateBack = onNavigateBack,
            // "Medicine" routes to the existing medicines screen for the same
            // patient rather than storing a second copy here.
            onOpenMedicines = { onOpenMedicines(patientArg) },
        )
    }
}
