package com.sahaaya.feature.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.DemoModeBadge
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaPasswordField
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SahaayaTextField
import com.sahaaya.common.components.TertiaryButton
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.usecase.auth.RegisterUseCase

@Composable
fun RegisterScreen(
    role: Role,
    onNavigateBack: () -> Unit,
    onSignInInstead: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RegisterContent(
        role = role,
        state = state,
        onNavigateBack = onNavigateBack,
        onSignInInstead = onSignInInstead,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPhoneNumberChange = viewModel::onPhoneNumberChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSubmit = { viewModel.submit(role) },
        modifier = modifier,
    )
}

/**
 * Stateless body, so the layout can be previewed and tested without Hilt or a
 * live Firebase project.
 */
@Composable
private fun RegisterContent(
    role: Role,
    state: RegisterUiState,
    onNavigateBack: () -> Unit,
    onSignInInstead: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaScreen(
        title = when (role) {
            Role.PATIENT -> "Patient account"
            Role.CAREGIVER -> "Caregiver account"
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        actions = { DemoModeBadge() },
    ) {
        Text(
            text = when (role) {
                Role.PATIENT ->
                    "Set up the phone that stays with the person receiving care."
                Role.CAREGIVER ->
                    "Set up your account, then link to a patient with a code from their phone."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.errorMessage != null) {
            Banner(message = state.errorMessage)
        }

        SahaayaTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = "Full name",
            error = state.fieldErrors[RegisterUseCase.FIELD_NAME],
            leadingIcon = Icons.Filled.Person,
            capitalization = KeyboardCapitalization.Words,
            enabled = !state.isSubmitting,
        )

        SahaayaTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Email address",
            error = state.fieldErrors[RegisterUseCase.FIELD_EMAIL],
            leadingIcon = Icons.Filled.Email,
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            enabled = !state.isSubmitting,
        )

        SahaayaTextField(
            value = state.phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = "Mobile number",
            error = state.fieldErrors[RegisterUseCase.FIELD_PHONE],
            supportingText = "Used when a caregiver needs to reach you quickly",
            leadingIcon = Icons.Filled.Phone,
            keyboardType = KeyboardType.Phone,
            capitalization = KeyboardCapitalization.None,
            enabled = !state.isSubmitting,
        )

        SahaayaPasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Password",
            error = state.fieldErrors[RegisterUseCase.FIELD_PASSWORD],
            supportingText = "At least 8 characters, with a letter and a number",
            enabled = !state.isSubmitting,
        )

        SahaayaPasswordField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = "Confirm password",
            error = state.fieldErrors[RegisterUseCase.FIELD_CONFIRM_PASSWORD],
            imeAction = ImeAction.Done,
            enabled = !state.isSubmitting,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        PrimaryButton(
            text = "Create account",
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
                text = "Already registered?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TertiaryButton(text = "Sign in", onClick = onSignInInstead)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterPreview() {
    SahaayaTheme {
        RegisterContent(
            role = Role.CAREGIVER,
            state = RegisterUiState(
                displayName = "Chirag Rai",
                email = "chirag@example.com",
                phoneNumber = "9876543210",
            ),
            onNavigateBack = {},
            onSignInInstead = {},
            onDisplayNameChange = {},
            onEmailChange = {},
            onPhoneNumberChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSubmit = {},
        )
    }
}
