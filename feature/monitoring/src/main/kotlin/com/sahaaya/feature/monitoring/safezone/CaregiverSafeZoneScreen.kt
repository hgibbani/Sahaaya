package com.sahaaya.feature.monitoring.safezone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.StatusChip
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.SafeZone

@Composable
fun CaregiverSafeZoneScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaregiverSafeZoneViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CaregiverSafeZoneContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onRadius = viewModel::setRadius,
        onSaveHere = viewModel::saveHere,
        onTurnOff = viewModel::turnOff,
        modifier = modifier,
    )
}

@Composable
private fun CaregiverSafeZoneContent(
    state: CaregiverSafeZoneUiState,
    onNavigateBack: () -> Unit,
    onRadius: (Int) -> Unit,
    onSaveHere: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaScreen(
        title = "Safe zone",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        Text(
            text = "Sahaaya will tell you once if they go outside this area. " +
                "It will not keep alerting while they are out.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.errorMessage != null) Banner(message = state.errorMessage)
        if (state.message != null) {
            Banner(message = state.message, tone = BannerTone.Success)
        }

        // --- Current zone ---
        SahaayaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Current zone", style = MaterialTheme.typography.titleMedium)
                StatusChip(
                    label = if (state.geofenceEnabled) "On" else "Off",
                    tone = if (state.geofenceEnabled) BannerTone.Success else BannerTone.Info,
                )
            }

            val zone = state.savedZone
            if (zone == null) {
                Text(
                    text = "No safe zone yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "%.5f, %.5f".format(zone.centre.latitude, zone.centre.longitude),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${zone.radiusMetres} m around ${zone.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Radius ---
        SahaayaCard {
            Text(text = "How large", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Anything under ${SafeZone.MIN_RADIUS_METRES} m and ordinary " +
                    "GPS drift indoors will look like they left.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceXs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            ) {
                RADIUS_CHOICES.forEach { metres ->
                    TextButton(onClick = { onRadius(metres) }) {
                        Text(
                            text = "$metres m",
                            color = if (state.selectedRadiusMetres == metres) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Text(
                text = "Selected: ${state.selectedRadiusMetres} m",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // --- Centre ---
        SahaayaCard {
            Text(text = "Where the centre is", style = MaterialTheme.typography.titleMedium)
            val location = state.patientLocation
            if (location == null) {
                Text(
                    text = "Waiting for the patient's phone to report a position. " +
                        "Ask them to open Sahaaya once with location switched on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "%.5f, %.5f".format(
                        location.point.latitude,
                        location.point.longitude,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "This is where the patient's phone last reported. Saving " +
                        "centres the zone here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        PrimaryButton(
            text = if (state.isSaving) "Saving…" else "Save safe zone here",
            onClick = onSaveHere,
            enabled = state.canSave,
        )

        if (state.geofenceEnabled) {
            TextButton(onClick = onTurnOff, enabled = !state.isSaving) {
                Text(
                    text = "Turn safe-zone alerts off",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Sensible presets. All at or above [SafeZone.MIN_RADIUS_METRES]. */
private val RADIUS_CHOICES = listOf(100, 250, 500, 1_000)

@Preview(showBackground = true)
@Composable
private fun CaregiverSafeZonePreview() {
    SahaayaTheme {
        CaregiverSafeZoneContent(
            state = CaregiverSafeZoneUiState(),
            onNavigateBack = {},
            onRadius = {},
            onSaveHere = {},
            onTurnOff = {},
        )
    }
}
