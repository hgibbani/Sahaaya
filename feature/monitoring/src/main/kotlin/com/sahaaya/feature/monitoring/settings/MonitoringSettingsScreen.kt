package com.sahaaya.feature.monitoring.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SecondaryButton
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.FallSensitivity
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.model.SafeZone
import kotlin.math.roundToInt

@Composable
fun MonitoringSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonitoringSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MonitoringSettingsContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onFallEnabled = viewModel::setFallDetectionEnabled,
        onSensitivity = viewModel::setFallSensitivity,
        onInactivityEnabled = viewModel::setInactivityEnabled,
        onInactivityTimeout = viewModel::setInactivityTimeout,
        onGeofenceEnabled = viewModel::setGeofenceEnabled,
        onSafeZoneRadius = viewModel::setSafeZoneRadius,
        onSetSafeZoneHere = viewModel::setSafeZoneHere,
        onRemindersEnabled = viewModel::setMedicationRemindersEnabled,
        onMissedDoseGrace = viewModel::setMissedDoseGrace,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
private fun MonitoringSettingsContent(
    state: MonitoringSettingsUiState,
    onNavigateBack: () -> Unit,
    onFallEnabled: (Boolean) -> Unit,
    onSensitivity: (FallSensitivity) -> Unit,
    onInactivityEnabled: (Boolean) -> Unit,
    onInactivityTimeout: (Int) -> Unit,
    onGeofenceEnabled: (Boolean) -> Unit,
    onSafeZoneRadius: (Int) -> Unit,
    onSetSafeZoneHere: () -> Unit,
    onRemindersEnabled: (Boolean) -> Unit,
    onMissedDoseGrace: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    if (state.isLoading || settings == null) {
        LoadingState(label = "Loading settings")
        return
    }

    SahaayaScreen(
        title = "Monitoring settings",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        if (state.errorMessage != null) Banner(message = state.errorMessage)
        if (state.infoMessage != null) {
            Banner(message = state.infoMessage, tone = BannerTone.Success)
        }
        if (state.savedAtLeastOnce && state.errorMessage == null) {
            Banner(message = "Settings saved and monitoring updated.", tone = BannerTone.Success)
        }

        // --- Fall detection ---
        SahaayaCard {
            SettingToggle(
                title = "Fall detection",
                description = "Watches the phone's motion sensor for a fall.",
                checked = settings.fallDetectionEnabled,
                onCheckedChange = onFallEnabled,
                enabled = !state.isSaving,
            )

            if (settings.fallDetectionEnabled) {
                Spacer(modifier = Modifier.height(Dimens.SpaceSm))
                Text(
                    text = "How sensitive",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FallSensitivity.entries.forEach { option ->
                    SettingChoice(
                        title = option.displayName,
                        description = option.description,
                        selected = settings.fallSensitivity == option,
                        onSelect = { onSensitivity(option) },
                        enabled = !state.isSaving,
                    )
                }
                Text(
                    text = "After a fall is detected you have " +
                        "${MonitoringSettings.FALL_CONFIRMATION_SECONDS} seconds to " +
                        "cancel before your caregiver is told.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Safe zone ---
        SahaayaCard {
            SettingToggle(
                title = "Safe zone",
                description = "Alerts your caregiver if you go beyond a set area.",
                checked = settings.geofenceEnabled,
                onCheckedChange = onGeofenceEnabled,
                enabled = !state.isSaving,
            )

            if (settings.geofenceEnabled) {
                if (!state.hasBackgroundLocationPermission) {
                    Banner(
                        message = "Safe-zone alerts need location set to " +
                            "\"Allow all the time\" in Android settings. Without it " +
                            "they will not work when the app is closed.",
                        tone = BannerTone.Info,
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.SpaceSm))
                val zone = settings.safeZone
                if (zone == null) {
                    Text(
                        text = "No safe zone set yet. Stand where the centre should " +
                            "be - usually at home - and tap below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Centred on ${zone.label}, ${zone.radiusMetres} m across.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SliderSetting(
                        label = "Radius",
                        value = zone.radiusMetres,
                        range = SafeZone.MIN_RADIUS_METRES..SafeZone.MAX_RADIUS_METRES,
                        step = 50,
                        unit = "m",
                        onValueChange = onSafeZoneRadius,
                        enabled = !state.isSaving,
                    )
                    Text(
                        text = "Anything under ${SafeZone.MIN_RADIUS_METRES} m and normal " +
                            "GPS drift indoors will report exits that never happened.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SecondaryButton(
                    text = if (zone == null) "Set safe zone here" else "Move safe zone here",
                    onClick = onSetSafeZoneHere,
                    enabled = !state.isSaving && !state.isSettingSafeZone,
                )
            }
        }

        // --- Inactivity ---
        SahaayaCard {
            SettingToggle(
                title = "Inactivity alerts",
                description = "Tells your caregiver if there is no movement for a long time.",
                checked = settings.inactivityDetectionEnabled,
                onCheckedChange = onInactivityEnabled,
                enabled = !state.isSaving,
            )

            if (settings.inactivityDetectionEnabled) {
                SliderSetting(
                    label = "Alert after",
                    value = settings.inactivityTimeoutMinutes,
                    range = MonitoringSettings.MIN_INACTIVITY_MINUTES..
                        MonitoringSettings.MAX_INACTIVITY_MINUTES,
                    step = 15,
                    unit = "min",
                    onValueChange = onInactivityTimeout,
                    enabled = !state.isSaving,
                )
                Text(
                    text = "Set this longer than a normal nap, or you will be woken " +
                        "by every afternoon sleep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Medication ---
        SahaayaCard {
            SettingToggle(
                title = "Medicine reminders",
                description = "Prompts you at each dose time.",
                checked = settings.medicationRemindersEnabled,
                onCheckedChange = onRemindersEnabled,
                enabled = !state.isSaving,
            )

            if (settings.medicationRemindersEnabled) {
                SliderSetting(
                    label = "Count a dose as missed after",
                    value = settings.missedDoseGraceMinutes,
                    range = MonitoringSettings.MIN_MISSED_DOSE_GRACE_MINUTES..
                        MonitoringSettings.MAX_MISSED_DOSE_GRACE_MINUTES,
                    step = 10,
                    unit = "min",
                    onValueChange = onMissedDoseGrace,
                    enabled = !state.isSaving,
                )
            }
        }

        PrimaryButton(
            text = "Save settings",
            onClick = onSave,
            loading = state.isSaving,
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun SettingChoice(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean,
) {
    SahaayaCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = selected, onCheckedChange = { onSelect() }, enabled = enabled)
        }
    }
}

/**
 * A slider whose value is announced as a number with its unit.
 *
 * The stepped range matters: a continuous slider produces values like "137
 * minutes", which nobody chooses deliberately and nobody can reproduce.
 */
@Composable
private fun SliderSetting(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    unit: String,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
) {
    val steps = ((range.last - range.first) / step) - 1

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "$value $unit", style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw / step).roundToInt() * step
                onValueChange(snapped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps.coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "$label, $value $unit"
            },
        )
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun MonitoringSettingsPreview() {
    SahaayaTheme {
        MonitoringSettingsContent(
            state = MonitoringSettingsUiState(
                settings = MonitoringSettings(
                    patientId = "p1",
                    geofenceEnabled = true,
                    safeZone = SafeZone(
                        centre = com.sahaaya.domain.model.GeoPoint(13.18, 74.93),
                    ),
                ),
                isLoading = false,
                hasLocationPermission = true,
                hasBackgroundLocationPermission = false,
            ),
            onNavigateBack = {},
            onFallEnabled = {},
            onSensitivity = {},
            onInactivityEnabled = {},
            onInactivityTimeout = {},
            onGeofenceEnabled = {},
            onSafeZoneRadius = {},
            onSetSafeZoneHere = {},
            onRemindersEnabled = {},
            onMissedDoseGrace = {},
            onSave = {},
        )
    }
}
