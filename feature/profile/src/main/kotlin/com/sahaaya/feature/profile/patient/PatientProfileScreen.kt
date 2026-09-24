package com.sahaaya.feature.profile.patient

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import com.sahaaya.common.components.DetailRow
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SahaayaTextField
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.DementiaStage
import com.sahaaya.domain.model.Gender
import com.sahaaya.domain.usecase.profile.SavePatientProfileUseCase
import com.sahaaya.feature.profile.components.ChoiceRow

@Composable
fun PatientProfileScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PatientProfileContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onPhoneNumberChange = viewModel::onPhoneNumberChange,
        onDateOfBirthChange = viewModel::onDateOfBirthChange,
        onGenderChange = viewModel::onGenderChange,
        onBloodGroupChange = viewModel::onBloodGroupChange,
        onHeightChange = viewModel::onHeightChange,
        onAddressChange = viewModel::onAddressChange,
        onDiagnosisStageChange = viewModel::onDiagnosisStageChange,
        onDiagnosedOnChange = viewModel::onDiagnosedOnChange,
        onMedicalNotesChange = viewModel::onMedicalNotesChange,
        onAllergiesChange = viewModel::onAllergiesChange,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
private fun PatientProfileContent(
    state: PatientProfileUiState,
    onNavigateBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onDateOfBirthChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onBloodGroupChange: (String) -> Unit,
    onHeightChange: (String) -> Unit = {},
    onAddressChange: (String) -> Unit,
    onDiagnosisStageChange: (DementiaStage) -> Unit,
    onDiagnosedOnChange: (String) -> Unit,
    onMedicalNotesChange: (String) -> Unit,
    onAllergiesChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(label = "Loading profile")
        return
    }

    SahaayaScreen(
        title = if (state.isEditable) "My profile" else "Patient profile",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        if (state.errorMessage != null) {
            Banner(message = state.errorMessage)
        }
        if (state.savedAtLeastOnce) {
            Banner(message = "Profile saved.", tone = BannerTone.Success)
        }

        if (!state.isEditable) {
            ReadOnlyProfile(state)
            return@SahaayaScreen
        }

        Text(text = "About you", style = MaterialTheme.typography.titleLarge)

        SahaayaTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = "Full name",
            error = state.fieldErrors[SavePatientProfileUseCase.FIELD_NAME],
            leadingIcon = Icons.Filled.Person,
            capitalization = KeyboardCapitalization.Words,
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = "Mobile number",
            error = state.fieldErrors[SavePatientProfileUseCase.FIELD_PHONE],
            leadingIcon = Icons.Filled.Phone,
            keyboardType = KeyboardType.Phone,
            capitalization = KeyboardCapitalization.None,
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.dateOfBirth,
            onValueChange = onDateOfBirthChange,
            label = "Date of birth",
            supportingText = "For example 14-03-1952",
            leadingIcon = Icons.Filled.Cake,
            capitalization = KeyboardCapitalization.None,
            enabled = !state.isSaving,
        )

        ChoiceRow(
            label = "Gender",
            options = Gender.entries,
            selected = state.gender,
            optionLabel = { it.displayName },
            onSelect = onGenderChange,
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.bloodGroup,
            onValueChange = onBloodGroupChange,
            label = "Blood group",
            supportingText = "Optional, but useful in an emergency",
            capitalization = KeyboardCapitalization.Characters,
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.height,
            onValueChange = onHeightChange,
            label = "Height",
            supportingText = "For example 158 cm or 5 ft 2 in",
        )

        SahaayaTextField(
            value = state.address,
            onValueChange = onAddressChange,
            label = "Home address",
            supportingText = "Where a caregiver should come if you need help",
            leadingIcon = Icons.Filled.Home,
            singleLine = false,
            minLines = 2,
            enabled = !state.isSaving,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceSm))

        Text(text = "Medical details", style = MaterialTheme.typography.titleLarge)

        ChoiceRow(
            label = "Stage of dementia",
            options = DementiaStage.entries,
            selected = state.diagnosisStage,
            optionLabel = { it.displayName },
            onSelect = onDiagnosisStageChange,
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.diagnosedOn,
            onValueChange = onDiagnosedOnChange,
            label = "Diagnosed on",
            supportingText = "Optional. For example March 2024",
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.allergies,
            onValueChange = onAllergiesChange,
            label = "Allergies",
            error = state.fieldErrors[SavePatientProfileUseCase.FIELD_ALLERGIES],
            supportingText = "Medicines or foods to avoid",
            singleLine = false,
            minLines = 2,
            enabled = !state.isSaving,
        )

        SahaayaTextField(
            value = state.medicalNotes,
            onValueChange = onMedicalNotesChange,
            label = "Notes for a caregiver",
            error = state.fieldErrors[SavePatientProfileUseCase.FIELD_NOTES],
            supportingText = "Other conditions, current medicines, anything a " +
                "caregiver should know",
            singleLine = false,
            minLines = 3,
            imeAction = ImeAction.Done,
            enabled = !state.isSaving,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        PrimaryButton(
            text = "Save profile",
            onClick = onSave,
            loading = state.isSaving,
        )
    }
}

/** What a linked caregiver sees. Read-only, and ordered by what matters in a hurry. */
@Composable
private fun ReadOnlyProfile(state: PatientProfileUiState) {
    SahaayaCard {
        DetailRow(label = "Name", value = state.displayName)
        DetailRow(label = "Mobile number", value = state.phoneNumber)
        DetailRow(label = "Home address", value = state.address)
    }
    SahaayaCard {
        DetailRow(label = "Stage of dementia", value = state.diagnosisStage.displayName)
        DetailRow(label = "Diagnosed on", value = state.diagnosedOn)
        DetailRow(label = "Allergies", value = state.allergies)
        DetailRow(label = "Notes", value = state.medicalNotes)
    }
    SahaayaCard {
        DetailRow(label = "Date of birth", value = state.dateOfBirth)
        ageFrom(state.dateOfBirth)?.let { DetailRow(label = "Age", value = "$it years") }
        DetailRow(label = "Gender", value = state.gender.displayName)
        DetailRow(label = "Blood group", value = state.bloodGroup)
        DetailRow(label = "Height", value = state.height)
    }
    Text(
        text = "You can see this because you are linked to this patient. " +
            "Only they can change it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun PatientProfilePreview() {
    SahaayaTheme {
        PatientProfileContent(
            state = PatientProfileUiState(
                displayName = "Ibbani H G",
                phoneNumber = "9876543210",
                dateOfBirth = "14-03-1952",
                diagnosisStage = DementiaStage.EARLY,
                isLoading = false,
            ),
            onNavigateBack = {},
            onDisplayNameChange = {},
            onPhoneNumberChange = {},
            onDateOfBirthChange = {},
            onGenderChange = {},
            onBloodGroupChange = {},
            onAddressChange = {},
            onDiagnosisStageChange = {},
            onDiagnosedOnChange = {},
            onMedicalNotesChange = {},
            onAllergiesChange = {},
            onSave = {},
        )
    }
}

/**
 * Age in whole years from a date of birth, or null if it cannot be read.
 *
 * Accepts the format the field's own hint asks for (14-03-1952), the same with
 * slashes, and ISO (1952-03-14) as older records store it. Derived rather than
 * stored, so it never goes stale on a birthday.
 */
private fun ageFrom(dateOfBirth: String?): Int? {
    val text = dateOfBirth?.trim().orEmpty()
    if (text.isEmpty()) return null
    val date = listOf("dd-MM-yyyy", "dd/MM/yyyy", "yyyy-MM-dd").firstNotNullOfOrNull { pattern ->
        runCatching {
            java.time.LocalDate.parse(text, java.time.format.DateTimeFormatter.ofPattern(pattern))
        }.getOrNull()
    } ?: return null
    val years = java.time.Period.between(date, java.time.LocalDate.now()).years
    return years.takeIf { it in 0..130 }
}
