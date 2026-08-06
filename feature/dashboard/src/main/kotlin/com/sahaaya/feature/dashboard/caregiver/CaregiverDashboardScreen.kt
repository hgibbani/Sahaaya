package com.sahaaya.feature.dashboard.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.ActionCard
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.EmptyState
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingStatus
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.model.User
import com.sahaaya.feature.dashboard.timeline.EventVisuals
import com.sahaaya.feature.dashboard.timeline.formatRelativeTime

@Composable
fun CaregiverDashboardScreen(
    onOpenProfile: () -> Unit,
    onAddPatient: () -> Unit,
    onOpenPatient: (patientId: String) -> Unit,
    onOpenPatientMedications: (patientId: String) -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaregiverDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CaregiverDashboardContent(
        state = state,
        onOpenProfile = onOpenProfile,
        onAddPatient = onAddPatient,
        onOpenPatient = onOpenPatient,
        onOpenPatientMedications = onOpenPatientMedications,
        onOpenTimeline = onOpenTimeline,
        onOpenEvent = onOpenEvent,
        onUnlinkPatient = viewModel::unlinkPatient,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@Composable
private fun CaregiverDashboardContent(
    state: CaregiverDashboardUiState,
    onOpenProfile: () -> Unit,
    onAddPatient: () -> Unit,
    onOpenPatient: (String) -> Unit,
    onOpenPatientMedications: (String) -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onUnlinkPatient: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(label = "Loading your patients")
        return
    }

    SahaayaScreen(
        title = state.user?.firstName?.let { "Hello, $it" } ?: "Sahaaya",
        modifier = modifier,
        actions = {
            IconButton(onClick = onSignOut) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign out",
                )
            }
        },
    ) {
        if (state.errorMessage != null) {
            Banner(message = state.errorMessage)
        }

        // Outstanding alerts come first and nothing competes with them. A
        // caregiver opening this app during an emergency must not have to
        // scroll past a profile card to find out what happened.
        if (!state.hasNoPatients) {
            if (state.unresolvedEvents.isEmpty()) {
                Banner(
                    message = "All clear. Nothing needs your attention.",
                    tone = BannerTone.Success,
                )
            } else {
                Banner(
                    message = if (state.hasCriticalUnresolved) {
                        "${state.unresolvedEvents.size} alert(s) need you now."
                    } else {
                        "${state.unresolvedEvents.size} alert(s) to review."
                    },
                    tone = if (state.hasCriticalUnresolved) {
                        BannerTone.Error
                    } else {
                        BannerTone.Info
                    },
                )

                state.topUnresolved.forEach { event ->
                    ActionCard(
                        title = event.type.displayName,
                        subtitle = event.patientName.ifBlank { "Patient" } + " - " +
                            formatRelativeTime(event.occurredAtEpochMillis),
                        icon = EventVisuals.icon(event.type),
                        onClick = { onOpenEvent(event.id) },
                        iconTint = EventVisuals.contentColor(event.type),
                        iconBackground = EventVisuals.containerColor(event.type),
                    )
                }
            }

            ActionCard(
                title = "All alerts",
                subtitle = if (state.recentEvents.isEmpty()) {
                    "Nothing recorded yet"
                } else {
                    "${state.recentEvents.size} in recent history"
                },
                icon = Icons.Filled.Notifications,
                onClick = onOpenTimeline,
            )

            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
        }

        Text(
            text = if (state.hasNoPatients) "Your patients" else
                "Your patients (${state.patients.size})",
            style = MaterialTheme.typography.titleLarge,
        )

        if (state.hasNoPatients) {
            EmptyState(
                title = "No one linked yet",
                description = "Ask the person you care for to open Sahaaya and tap " +
                    "\"Link a caregiver\". They will read out a six-character code.",
                actionLabel = "Enter a code",
                onAction = onAddPatient,
            )
        } else {
            state.patients.forEach { pairing ->
                PatientRow(
                    pairing = pairing,
                    isUnlinking = state.unlinkingPairingId == pairing.id,
                    onOpen = { onOpenPatient(pairing.patientId) },
                    onOpenMedications = { onOpenPatientMedications(pairing.patientId) },
                    onUnlink = { onUnlinkPatient(pairing.id) },
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceXs))

            ActionCard(
                title = "Link another patient",
                subtitle = "Enter a code from their phone",
                icon = Icons.Filled.PersonAdd,
                onClick = onAddPatient,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        ActionCard(
            title = "My profile",
            subtitle = "Your details and alert availability",
            icon = Icons.Filled.Person,
            onClick = onOpenProfile,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            iconBackground = MaterialTheme.colorScheme.secondaryContainer,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceSm))

        Text(
            text = "Fall, safe-zone and inactivity alerts arrive in the next " +
                "release. They will be sent to every caregiver linked here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One linked patient.
 *
 * Unlinking asks for confirmation inline rather than in a dialog: it is
 * destructive, it silently stops that caregiver receiving alerts, and it should
 * not be possible to do it with a stray tap.
 */
@Composable
private fun PatientRow(
    pairing: Pairing,
    isUnlinking: Boolean,
    onOpen: () -> Unit,
    onOpenMedications: () -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }

    SahaayaCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pairing.patientName.ifBlank { "Patient" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Linked - you will receive their alerts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isUnlinking) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.5.dp,
                )
            } else {
                IconButton(onClick = { confirming = !confirming }) {
                    Icon(
                        imageVector = Icons.Filled.LinkOff,
                        contentDescription = "Unlink ${pairing.patientName}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (confirming && !isUnlinking) {
            Text(
                text = "Unlink ${pairing.patientName.ifBlank { "this patient" }}? " +
                    "You will stop receiving their alerts. They can link you again " +
                    "with a new code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { confirming = false }) { Text("Keep linked") }
                TextButton(
                    onClick = {
                        confirming = false
                        onUnlink()
                    },
                ) {
                    Text(text = "Unlink", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onOpen) { Text("View profile") }
            TextButton(onClick = onOpenMedications) { Text("Medicines") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CaregiverDashboardPreview() {
    SahaayaTheme {
        CaregiverDashboardContent(
            state = CaregiverDashboardUiState(
                user = User(
                    uid = "c1",
                    email = "chirag@example.com",
                    displayName = "Chirag Rai",
                    role = Role.CAREGIVER,
                ),
                patients = listOf(
                    Pairing(
                        id = "p1_c1",
                        patientId = "p1",
                        caregiverId = "c1",
                        patientName = "Ibbani H G",
                        caregiverName = "Chirag Rai",
                        status = PairingStatus.ACTIVE,
                    ),
                ),
                isLoading = false,
            ),
            onOpenProfile = {},
            onAddPatient = {},
            onOpenPatient = {},
            onOpenPatientMedications = {},
            onOpenTimeline = {},
            onOpenEvent = {},
            onUnlinkPatient = {},
            onSignOut = {},
        )
    }
}
