package com.sahaaya.feature.monitoring.demo

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.ActionCard
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.EventType

@Composable
fun DemoModeScreen(
    onNavigateBack: () -> Unit,
    onFallSimulated: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DemoModeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.lastFallEventId) {
        val eventId = state.lastFallEventId
        if (eventId != null) {
            viewModel.onFallCountdownShown()
            onFallSimulated(eventId)
        }
    }

    DemoModeContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onSimulateFall = viewModel::simulateFall,
        onSimulateGeofence = viewModel::simulateGeofenceExit,
        onSimulateInactivity = viewModel::simulateInactivity,
        onSimulateSos = viewModel::simulateSos,
        onSimulateMissedDose = viewModel::simulateMissedDose,
        modifier = modifier,
    )
}

@Composable
private fun DemoModeContent(
    state: DemoModeUiState,
    onNavigateBack: () -> Unit,
    onSimulateFall: () -> Unit,
    onSimulateGeofence: () -> Unit,
    onSimulateInactivity: () -> Unit,
    onSimulateSos: () -> Unit,
    onSimulateMissedDose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaScreen(
        title = "Developer mode",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        Banner(
            message = "Debug builds only. Each button runs the real detection " +
                "pipeline - the same Firestore write, security rules and caregiver " +
                "notification. Only the physical action is skipped.",
            tone = BannerTone.Info,
        )

        if (state.errorMessage != null) Banner(message = state.errorMessage)
        if (state.lastResult != null) {
            Banner(message = state.lastResult, tone = BannerTone.Success)
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        ActionCard(
            title = "Simulate a fall",
            subtitle = if (state.runningSimulation == EventType.FALL) {
                "Working…"
            } else {
                "Raises a fall event and opens the 5-second countdown"
            },
            icon = Icons.Filled.PersonalInjury,
            onClick = onSimulateFall,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            iconBackground = MaterialTheme.colorScheme.errorContainer,
        )

        ActionCard(
            title = "Simulate leaving the safe zone",
            subtitle = if (state.runningSimulation == EventType.GEOFENCE_EXIT) {
                "Working…"
            } else {
                "Reports a geofence exit at your current location"
            },
            icon = Icons.Filled.DirectionsWalk,
            onClick = onSimulateGeofence,
        )

        ActionCard(
            title = "Simulate inactivity",
            subtitle = if (state.runningSimulation == EventType.INACTIVITY) {
                "Working…"
            } else {
                "Raises a 2-hour no-movement alert"
            },
            icon = Icons.Filled.Hotel,
            onClick = onSimulateInactivity,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            iconBackground = MaterialTheme.colorScheme.secondaryContainer,
        )

        ActionCard(
            title = "Simulate an SOS",
            subtitle = if (state.runningSimulation == EventType.SOS) {
                "Working…"
            } else {
                "Sends an emergency alert with your location"
            },
            icon = Icons.Filled.Emergency,
            onClick = onSimulateSos,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            iconBackground = MaterialTheme.colorScheme.errorContainer,
        )

        ActionCard(
            title = "Sweep for missed doses",
            subtitle = if (state.runningSimulation == EventType.MEDICATION_MISSED) {
                "Working…"
            } else {
                "Runs the missed-dose check that normally runs hourly"
            },
            icon = Icons.Filled.Medication,
            onClick = onSimulateMissedDose,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceSm))

        Text(
            text = "Sign in as the caregiver on a second device (or an emulator) " +
                "to watch these arrive on the timeline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DemoModePreview() {
    SahaayaTheme {
        DemoModeContent(
            state = DemoModeUiState(),
            onNavigateBack = {},
            onSimulateFall = {},
            onSimulateGeofence = {},
            onSimulateInactivity = {},
            onSimulateSos = {},
            onSimulateMissedDose = {},
        )
    }
}
