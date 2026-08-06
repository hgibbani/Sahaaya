package com.sahaaya.feature.profile.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SahaayaTextField
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.usecase.profile.SaveCaregiverProfileUseCase

@Composable
fun CaregiverProfileScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaregiverProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CaregiverProfileContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onPhoneNumberChange = viewModel::onPhoneNumberChange,
        onRelationshipChange = viewModel::onRelationshipChange,
        onAddressChange = viewModel::onAddressChange,
        onAvailabilityChange = viewModel::onAvailabilityChange,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
private fun CaregiverProfileContent(
    state: CaregiverProfileUiState,
    onNavigateBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onAvailabilityChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(label = "Loading profile")
        return
    }

    SahaayaScreen(
        title = "My profile",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        if (state.errorMessage != null) {
            Banner(message = state.errorMessage)
        }
        if (state.savedAtLeastOnce) {
            Banner(message = "Profile saved.", tone = BannerTone.Success)
        }

        SahaayaTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = "Full name",
            error = state.fieldErrors[SaveCaregiverProfileUseCase.FIELD_NAME],
            supportingText = "This is the name your patient sees",
            leadingIcon = Icons.Filled.Person,
            capitalization = KeyboardCapitalization.Words,
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = "Mobile number",
            error = state.fieldErrors[SaveCaregiverProfileUseCase.FIELD_PHONE],
            leadingIcon = Icons.Filled.Phone,
            keyboardType = KeyboardType.Phone,
            capitalization = KeyboardCapitalization.None,
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.relationship,
            onValueChange = onRelationshipChange,
            label = "Relationship to patient",
            error = state.fieldErrors[SaveCaregiverProfileUseCase.FIELD_RELATIONSHIP],
            supportingText = "For example daughter, son, attendant",
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.address,
            onValueChange = onAddressChange,
            label = "Your address",
            supportingText = "Optional. Helps judge how quickly you can reach them",
            leadingIcon = Icons.Filled.Home,
            singleLine = false,
            minLines = 2,
            imeAction = ImeAction.Done,
            enabled = !state.isSaving,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        // Availability is a care handover switch, not a notification preference.
        // Phase 3 uses it to decide who an alert reaches first, so the wording
        // says what it will actually do.
        SahaayaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Available for alerts",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Turn this off when someone else is on duty. " +
                            "You stay linked and can still see the profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.isAvailableForAlerts,
                    onCheckedChange = onAvailabilityChange,
                    enabled = !state.isSaving,
                )
            }
        }

        PrimaryButton(
            text = "Save profile",
            onClick = onSave,
            loading = state.isSaving,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CaregiverProfilePreview() {
    SahaayaTheme {
        CaregiverProfileContent(
            state = CaregiverProfileUiState(
                displayName = "Chirag Rai",
                phoneNumber = "9876543210",
                relationship = "Son",
                isLoading = false,
            ),
            onNavigateBack = {},
            onDisplayNameChange = {},
            onPhoneNumberChange = {},
            onRelationshipChange = {},
            onAddressChange = {},
            onAvailabilityChange = {},
            onSave = {},
        )
    }
}
