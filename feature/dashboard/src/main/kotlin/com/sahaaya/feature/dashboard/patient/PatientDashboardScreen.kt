package com.sahaaya.feature.dashboard.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.ActionCard
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.StatusChip
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.model.User
import com.sahaaya.feature.monitoring.sos.SosButton

@Composable
fun PatientDashboardScreen(
    onOpenProfile: () -> Unit,
    onOpenEmergencyContacts: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenMedications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDemoMode: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: PatientDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PatientDashboardContent(
        state = state,
        onOpenProfile = onOpenProfile,
        onOpenEmergencyContacts = onOpenEmergencyContacts,
        onOpenPairing = onOpenPairing,
        onOpenMedications = onOpenMedications,
        onOpenSettings = onOpenSettings,
        onOpenDemoMode = onOpenDemoMode,
        onSos = viewModel::triggerSos,
        onSignOut = viewModel::signOut,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

/**
 * The patient's home screen.
 *
 * The SOS button is the first thing on it and the largest thing on it. Everything
 * else - profile, medicines, settings - is a card below. Someone with moderate
 * dementia should be able to look at this and find the way to get help without
 * reading anything.
 */
@Composable
private fun PatientDashboardContent(
    state: PatientDashboardUiState,
    onOpenProfile: () -> Unit,
    onOpenEmergencyContacts: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenMedications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDemoMode: (() -> Unit)?,
    onSos: () -> Unit,
    onSignOut: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(label = "Loading your details")
        return
    }

    SahaayaScreen(
        title = state.user?.firstName?.let { "Hello, $it" } ?: "Sahaaya",
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Tune, contentDescription = "Monitoring settings")
            }
            IconButton(onClick = onSignOut) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign out",
                )
            }
        },
    ) {
        if (state.errorMessage != null) Banner(message = state.errorMessage)
        if (state.sosSentMessage != null) {
            Banner(message = state.sosSentMessage, tone = BannerTone.Success)
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceSm))

        SosButton(
            onTriggered = onSos,
            isSending = state.isSendingSos,
            enabled = !state.hasNoCaregiver,
        )

        if (state.hasNoCaregiver) {
            Banner(
                message = "Link a caregiver first - there is nobody to send an " +
                    "emergency alert to yet.",
                tone = BannerTone.Info,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceSm))

        // Who is watching over me. The most reassuring fact the screen can state.
        SahaayaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Your care circle", style = MaterialTheme.typography.titleMedium)
                StatusChip(
                    label = if (state.hasNoCaregiver) "Not linked" else "Linked",
                    tone = if (state.hasNoCaregiver) BannerTone.Info else BannerTone.Success,
                )
            }

            if (state.hasNoCaregiver) {
                Text(
                    text = "No caregiver is linked yet. Tap \"Link a caregiver\" below " +
                        "and read out the code that appears.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.caregivers.forEach { pairing ->
                    Text(
                        text = pairing.caregiverName.ifBlank { "Caregiver" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(
                    text = "They will be alerted if you fall, press SOS, or leave " +
                        "your safe zone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val monitoringSummary = state.monitoringSummary
        if (monitoringSummary != null) {
            Banner(message = monitoringSummary, tone = BannerTone.Info)
        }

        if (state.needsProfileCompletion) {
            Banner(
                message = "Your profile is missing details a caregiver would need in " +
                    "an emergency. Tap \"My profile\" to finish it.",
                tone = BannerTone.Info,
            )
        }

        if (state.hasNoEmergencyContact) {
            Banner(
                message = "No emergency contact saved yet. Add at least one person " +
                    "who should be called first.",
                tone = BannerTone.Info,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        ActionCard(
            title = "My medicines",
            subtitle = if (state.pendingDoseCount > 0) {
                "${state.pendingDoseCount} dose(s) waiting for you"
            } else {
                "Reminders and what you have taken"
            },
            icon = Icons.Filled.Medication,
            onClick = onOpenMedications,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
        )

        ActionCard(
            title = "Link a caregiver",
            subtitle = "Show a code for someone to add you",
            icon = Icons.Filled.QrCode2,
            onClick = onOpenPairing,
        )

        ActionCard(
            title = "My profile",
            subtitle = "Name, contact details and medical information",
            icon = Icons.Filled.Person,
            onClick = onOpenProfile,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            iconBackground = MaterialTheme.colorScheme.secondaryContainer,
        )

        ActionCard(
            title = "Emergency contacts",
            subtitle = if (state.emergencyContacts.isEmpty()) {
                "None saved yet"
            } else {
                "${state.emergencyContacts.size} saved"
            },
            icon = Icons.Filled.ContactPhone,
            onClick = onOpenEmergencyContacts,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            iconBackground = MaterialTheme.colorScheme.errorContainer,
        )

        ActionCard(
            title = "Monitoring settings",
            subtitle = "Fall sensitivity, safe zone and inactivity",
            icon = Icons.Filled.Tune,
            onClick = onOpenSettings,
        )

        // Debug builds only - the callback is null in release, so the card is
        // not composed at all.
        if (onOpenDemoMode != null) {
            ActionCard(
                title = "Developer mode",
                subtitle = "Simulate a fall, geofence exit, inactivity or SOS",
                icon = Icons.Filled.BugReport,
                onClick = onOpenDemoMode,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun PatientDashboardPreview() {
    SahaayaTheme {
        PatientDashboardContent(
            state = PatientDashboardUiState(
                user = User(
                    uid = "u1",
                    email = "ibbani@example.com",
                    displayName = "Ibbani H G",
                    role = Role.PATIENT,
                ),
                profile = PatientProfile(uid = "u1"),
                isLoading = false,
            ),
            onOpenProfile = {},
            onOpenEmergencyContacts = {},
            onOpenPairing = {},
            onOpenMedications = {},
            onOpenSettings = {},
            onOpenDemoMode = {},
            onSos = {},
            onSignOut = {},
            onMessageShown = {},
        )
    }
}
