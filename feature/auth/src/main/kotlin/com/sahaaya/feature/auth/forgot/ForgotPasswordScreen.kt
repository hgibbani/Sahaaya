package com.sahaaya.feature.auth.forgot

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SahaayaTextField
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.usecase.auth.SendPasswordResetUseCase

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ForgotPasswordContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onEmailChange = viewModel::onEmailChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
private fun ForgotPasswordContent(
    state: ForgotPasswordUiState,
    onNavigateBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaScreen(
        title = "Reset password",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        Text(
            text = "Enter the email address on the account. We will send a link " +
                "to set a new password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.errorMessage != null) {
            Banner(message = state.errorMessage)
        }

        if (state.isSent) {
            // Worded so it is true whether or not the address is registered.
            // Confirming which emails have accounts would leak who is a Sahaaya
            // user, and by extension who is receiving care.
            Banner(
                message = "If that address has an account, a reset link is on its way. " +
                    "Check the spam folder too.",
                tone = BannerTone.Success,
            )
        }

        SahaayaTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Email address",
            error = state.fieldErrors[SendPasswordResetUseCase.FIELD_EMAIL],
            leadingIcon = Icons.Filled.Email,
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
            enabled = !state.isSubmitting,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        PrimaryButton(
            text = "Send reset link",
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.isSubmitting,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordPreview() {
    SahaayaTheme {
        ForgotPasswordContent(
            state = ForgotPasswordUiState(email = "ibbani@example.com"),
            onNavigateBack = {},
            onEmailChange = {},
            onSubmit = {},
        )
    }
}
