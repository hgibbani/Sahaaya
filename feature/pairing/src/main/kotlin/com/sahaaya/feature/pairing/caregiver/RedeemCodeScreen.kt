package com.sahaaya.feature.pairing.caregiver

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SahaayaTextField
import com.sahaaya.common.components.SecondaryButton
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme

@Composable
fun RedeemCodeScreen(
    onNavigateBack: () -> Unit,
    onLinked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RedeemCodeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RedeemCodeContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onCodeChange = viewModel::onCodeChange,
        onSubmit = viewModel::submit,
        onDone = {
            viewModel.onLinkAcknowledged()
            onLinked()
        },
        modifier = modifier,
    )
}

@Composable
private fun RedeemCodeContent(
    state: RedeemCodeUiState,
    onNavigateBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaScreen(
        title = "Link a patient",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        // Success is a distinct state rather than an instant navigation. Linking
        // grants access to someone's medical profile, so the caregiver is shown
        // exactly whose - a silent redirect would leave them unsure it worked.
        if (state.linkedPatientName != null) {
            Banner(
                message = "You are now linked to ${state.linkedPatientName}. " +
                    "You will receive their alerts.",
                tone = BannerTone.Success,
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
            PrimaryButton(text = "Done", onClick = onDone)
            return@SahaayaScreen
        }

        Text(
            text = "Ask the person you care for to open Sahaaya and tap " +
                "\"Link a caregiver\". They will read out a six-character code.",
            style = MaterialTheme.typography.bodyLarge,
        )

        if (state.errorMessage != null && state.fieldError == null) {
            Banner(message = state.errorMessage)
        }

        SahaayaTextField(
            value = state.code,
            onValueChange = onCodeChange,
            label = "Pairing code",
            error = state.fieldError,
            supportingText = "Six letters and numbers, for example K7M2PQ",
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
            enabled = !state.isSubmitting,
        )

        Text(
            text = "Codes expire 15 minutes after they are made.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        PrimaryButton(
            text = "Link patient",
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.isSubmitting,
        )

        SecondaryButton(text = "Cancel", onClick = onNavigateBack)

        Spacer(modifier = Modifier.height(Dimens.SpaceMd))

        Text(
            text = "Linking gives you access to their profile and emergency " +
                "contacts. Either of you can end the link at any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RedeemCodePreview() {
    SahaayaTheme {
        RedeemCodeContent(
            state = RedeemCodeUiState(code = "K7M2PQ"),
            onNavigateBack = {},
            onCodeChange = {},
            onSubmit = {},
            onDone = {},
        )
    }
}
