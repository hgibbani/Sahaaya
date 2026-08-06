package com.sahaaya.feature.pairing.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.EmptyState
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingCode
import com.sahaaya.domain.model.PairingStatus

@Composable
fun PatientPairingScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientPairingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PatientPairingContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onGenerateCode = viewModel::generateCode,
        onRevoke = viewModel::revokeCaregiver,
        modifier = modifier,
    )
}

@Composable
private fun PatientPairingContent(
    state: PatientPairingUiState,
    onNavigateBack: () -> Unit,
    onGenerateCode: () -> Unit,
    onRevoke: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaScreen(
        title = "Link a caregiver",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        if (state.errorMessage != null) {
            Banner(message = state.errorMessage)
        }

        Text(
            text = "Read this code out to the person who looks after you. They " +
                "type it into their own phone.",
            style = MaterialTheme.typography.bodyLarge,
        )

        SahaayaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            if (state.hasLiveCode && state.code != null) {
                CodeDisplay(code = state.code.code)
                Text(
                    text = "Expires in ${formatCountdown(state.remainingSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = "No code right now",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Codes last 15 minutes. Make one when your caregiver " +
                        "is ready to type it in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        PrimaryButton(
            text = if (state.hasLiveCode) "Make a new code" else "Make a code",
            onClick = onGenerateCode,
            loading = state.isGenerating,
        )

        if (state.hasLiveCode) {
            Text(
                text = "Making a new code cancels the one above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceMd))

        Text(
            text = "People linked to you",
            style = MaterialTheme.typography.titleLarge,
        )

        if (state.caregivers.isEmpty()) {
            EmptyState(
                title = "Nobody is linked yet",
                description = "Once a caregiver uses your code, they will appear here.",
            )
        } else {
            state.caregivers.forEach { pairing ->
                CaregiverRow(
                    pairing = pairing,
                    isRevoking = state.revokingPairingId == pairing.id,
                    onRevoke = { onRevoke(pairing.id) },
                )
            }
            Banner(
                message = "Anyone listed here can see your profile and will be " +
                    "alerted if you need help. You can remove them at any time.",
                tone = BannerTone.Info,
            )
        }
    }
}

/**
 * The code itself.
 *
 * Spaced, very large and monospaced-by-letter-spacing so it can be read aloud
 * accurately by someone whose eyesight is poor. The accessibility label spells
 * it out character by character, because TalkBack otherwise pronounces "K7M2PQ"
 * as a word.
 */
@Composable
private fun CodeDisplay(code: String, modifier: Modifier = Modifier) {
    val spokenCode = code.toCharArray().joinToString(separator = ", ")
    Text(
        text = code.toCharArray().joinToString(separator = " "),
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = 44.sp,
            letterSpacing = 2.sp,
        ),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Your code is $spokenCode" },
    )
}

@Composable
private fun CaregiverRow(
    pairing: Pairing,
    isRevoking: Boolean,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pairing.caregiverName.ifBlank { "Caregiver" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Can see your profile and receive your alerts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRevoking) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.5.dp,
                )
            } else {
                IconButton(onClick = onRevoke) {
                    Icon(
                        imageVector = Icons.Filled.LinkOff,
                        contentDescription = "Remove ${pairing.caregiverName}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatCountdown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun PatientPairingPreview() {
    SahaayaTheme {
        PatientPairingContent(
            state = PatientPairingUiState(
                code = PairingCode(
                    code = "K7M2PQ",
                    patientId = "p1",
                    patientName = "Ibbani H G",
                    createdAtEpochMillis = 0L,
                    expiresAtEpochMillis = 0L,
                ),
                remainingSeconds = 754,
                caregivers = listOf(
                    Pairing(
                        id = "p1_c1",
                        patientId = "p1",
                        caregiverId = "c1",
                        patientName = "Ibbani H G",
                        caregiverName = "Chirag Rai",
                        status = PairingStatus.ACTIVE,
                    ),
                ),
            ),
            onNavigateBack = {},
            onGenerateCode = {},
            onRevoke = {},
        )
    }
}
