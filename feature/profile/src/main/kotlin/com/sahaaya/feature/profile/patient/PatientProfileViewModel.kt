package com.sahaaya.feature.profile.patient

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.DementiaStage
import com.sahaaya.domain.model.Gender
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.usecase.profile.ObservePatientProfileUseCase
import com.sahaaya.domain.usecase.profile.SavePatientProfileUseCase
import com.sahaaya.feature.profile.ProfileRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val dateOfBirth: String = "",
    val gender: Gender = Gender.UNSPECIFIED,
    val bloodGroup: String = "",
    val height: String = "",
    val address: String = "",
    val diagnosisStage: DementiaStage = DementiaStage.UNSPECIFIED,
    val diagnosedOn: String = "",
    val medicalNotes: String = "",
    val allergies: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isEditable: Boolean = true,
    val savedAtLeastOnce: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
)

/**
 * Backs both "my profile" (patient viewing their own) and "patient profile"
 * (caregiver viewing someone they are linked to).
 *
 * The distinction is the optional `patientId` route argument. When it is
 * present the caregiver is looking at someone else, and the screen is read-only:
 * a caregiver may need to *see* a diagnosis in an emergency, but changing
 * someone else's medical record is not a Phase-2 capability, and pretending
 * otherwise would be worse than not offering it.
 */
@HiltViewModel
class PatientProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val observePatientProfile: ObservePatientProfileUseCase,
    private val savePatientProfile: SavePatientProfileUseCase,
) : ViewModel() {

    private val viewedPatientId: String? =
        savedStateHandle.get<String>(ProfileRoutes.ARG_PATIENT_ID)
            ?.takeIf { it.isNotBlank() && it != ProfileRoutes.SELF }

    private val targetUid: String? = viewedPatientId ?: authRepository.currentUserId()

    private val isOwnProfile = viewedPatientId == null

    private val _uiState = MutableStateFlow(
        PatientProfileUiState(isEditable = isOwnProfile),
    )
    val uiState: StateFlow<PatientProfileUiState> = _uiState.asStateFlow()

    init {
        val uid = targetUid
        if (uid == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "You are signed out. Please sign in again.",
                )
            }
        } else {
            observe(uid)
        }
    }

    /**
     * Loads once, then stops overwriting the form.
     *
     * The listener stays attached so a caregiver's read-only view updates live,
     * but for the owner it must not clobber half-typed input every time
     * Firestore echoes a write back. [PatientProfileUiState.savedAtLeastOnce]
     * marks the point after which the user's text wins.
     */
    private fun observe(uid: String) {
        viewModelScope.launch {
            observePatientProfile(uid).collect { snapshot ->
                _uiState.update { current ->
                    val keepUserEdits = !current.isLoading && current.isEditable
                    if (keepUserEdits) {
                        current.copy(isLoading = false)
                    } else {
                        current.copy(
                            displayName = snapshot.user?.displayName.orEmpty(),
                            email = snapshot.user?.email.orEmpty(),
                            phoneNumber = snapshot.user?.phoneNumber.orEmpty(),
                            dateOfBirth = snapshot.profile?.dateOfBirth.orEmpty(),
                            gender = snapshot.profile?.gender ?: Gender.UNSPECIFIED,
                            bloodGroup = snapshot.profile?.bloodGroup.orEmpty(),
                            height = snapshot.profile?.height.orEmpty(),
                            address = snapshot.profile?.address.orEmpty(),
                            diagnosisStage = snapshot.profile?.diagnosisStage
                                ?: DementiaStage.UNSPECIFIED,
                            diagnosedOn = snapshot.profile?.diagnosedOn.orEmpty(),
                            medicalNotes = snapshot.profile?.medicalNotes.orEmpty(),
                            allergies = snapshot.profile?.allergies.orEmpty(),
                            isLoading = false,
                        )
                    }
                }
            }
        }
    }

    fun onDisplayNameChange(value: String) = edit(SavePatientProfileUseCase.FIELD_NAME) {
        it.copy(displayName = value)
    }

    fun onPhoneNumberChange(value: String) = edit(SavePatientProfileUseCase.FIELD_PHONE) {
        it.copy(phoneNumber = value)
    }

    fun onDateOfBirthChange(value: String) = edit(null) { it.copy(dateOfBirth = value) }

    fun onGenderChange(value: Gender) = edit(null) { it.copy(gender = value) }

    fun onBloodGroupChange(value: String) = edit(null) { it.copy(bloodGroup = value) }

    fun onHeightChange(value: String) = edit(null) { it.copy(height = value) }

    fun onAddressChange(value: String) = edit(null) { it.copy(address = value) }

    fun onDiagnosisStageChange(value: DementiaStage) =
        edit(null) { it.copy(diagnosisStage = value) }

    fun onDiagnosedOnChange(value: String) = edit(null) { it.copy(diagnosedOn = value) }

    fun onMedicalNotesChange(value: String) = edit(SavePatientProfileUseCase.FIELD_NOTES) {
        it.copy(medicalNotes = value)
    }

    fun onAllergiesChange(value: String) = edit(SavePatientProfileUseCase.FIELD_ALLERGIES) {
        it.copy(allergies = value)
    }

    fun save() {
        val uid = targetUid ?: return
        val state = _uiState.value
        if (state.isSaving || !state.isEditable) return

        _uiState.update {
            it.copy(isSaving = true, errorMessage = null, savedAtLeastOnce = false)
        }

        viewModelScope.launch {
            val result = savePatientProfile(
                SavePatientProfileUseCase.Params(
                    uid = uid,
                    displayName = state.displayName,
                    phoneNumber = state.phoneNumber,
                    profile = PatientProfile(
                        uid = uid,
                        dateOfBirth = state.dateOfBirth.ifBlank { null },
                        gender = state.gender,
                        bloodGroup = state.bloodGroup.ifBlank { null },
                        height = state.height.ifBlank { null },
                        address = state.address.ifBlank { null },
                        diagnosisStage = state.diagnosisStage,
                        diagnosedOn = state.diagnosedOn.ifBlank { null },
                        medicalNotes = state.medicalNotes.ifBlank { null },
                        allergies = state.allergies.ifBlank { null },
                    ),
                ),
            )

            when (result) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isSaving = false, savedAtLeastOnce = true, fieldErrors = emptyMap())
                }

                is Outcome.Failure -> _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        errorMessage = result.error.message,
                        fieldErrors = (result.error as? AppError.Validation)
                            ?.fieldErrors
                            .orEmpty(),
                    )
                }
            }
        }
    }

    private fun edit(
        clearedField: String?,
        transform: (PatientProfileUiState) -> PatientProfileUiState,
    ) {
        _uiState.update { state ->
            transform(state).copy(
                savedAtLeastOnce = false,
                fieldErrors = if (clearedField == null) {
                    state.fieldErrors
                } else {
                    state.fieldErrors - clearedField
                },
            )
        }
    }
}
