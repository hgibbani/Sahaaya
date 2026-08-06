package com.sahaaya.feature.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaPasswordField
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SahaayaTextField
import com.sahaaya.common.components.TertiaryButton
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.usecase.auth.SignInUseCase

@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LoginContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onForgotPassword = onForgotPassword,
        onCreateAccount = onCreateAccount,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onNavigateBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaScreen(
        title = "Sign in",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        Text(
            text = "Welcome back to Sahaaya.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.errorMessage != null) {
            Banner(message = state.errorMessage)
        }

        SahaayaTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Email address",
            error = state.fieldErrors[SignInUseCase.FIELD_EMAIL],
            leadingIcon = Icons.Filled.Email,
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            enabled = !state.isSubmitting,
        )

        SahaayaPasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Password",
            error = state.fieldErrors[SignInUseCase.FIELD_PASSWORD],
            imeAction = ImeAction.Done,
            enabled = !state.isSubmitting,
        )

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TertiaryButton(
                text = "Forgot password?",
                onClick = onForgotPassword,
                enabled = !state.isSubmitting,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        PrimaryButton(
            text = "Sign in",
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.isSubmitting,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "New to Sahaaya?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TertiaryButton(text = "Create an account", onClick = onCreateAccount)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    SahaayaTheme {
        LoginContent(
            state = LoginUiState(email = "ibbani@example.com", password = "secret123"),
            onNavigateBack = {},
            onForgotPassword = {},
            onCreateAccount = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}
