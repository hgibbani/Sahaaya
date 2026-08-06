package com.sahaaya.feature.pairing

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sahaaya.feature.pairing.caregiver.RedeemCodeScreen
import com.sahaaya.feature.pairing.patient.PatientPairingScreen

object PairingRoutes {
    /** Patient side: shows the code to read out. */
    const val SHOW_CODE = "pairing/code"

    /** Caregiver side: enters a code to link. */
    const val REDEEM_CODE = "pairing/redeem"
}

fun NavGraphBuilder.pairingScreens(
    onNavigateBack: () -> Unit,
    onLinked: () -> Unit,
) {
    composable(PairingRoutes.SHOW_CODE) {
        PatientPairingScreen(onNavigateBack = onNavigateBack)
    }

    composable(PairingRoutes.REDEEM_CODE) {
        RedeemCodeScreen(
            onNavigateBack = onNavigateBack,
            onLinked = onLinked,
        )
    }
}
