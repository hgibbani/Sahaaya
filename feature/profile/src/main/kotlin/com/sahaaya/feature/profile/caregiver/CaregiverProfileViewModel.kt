package com.sahaaya.feature.profile.caregiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.usecase.profile.ObserveCaregiverProfileUseCase
import com.sahaaya.domain.usecase.profile.SaveCaregiverProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaregiverProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val relationship: String = "",
    val address: String = "",
    val isAvailableForAlerts: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedAtLeastOnce: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
)

@HiltViewModel
class CaregiverProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val observeCaregiverProfile: ObserveCaregiverProfileUseCase,
    private val saveCaregiverProfile: SaveCaregiverProfileUseCase,
) : ViewModel() {

    private val uid = authRepository.currentUserId()

    private val _uiState = MutableStateFlow(CaregiverProfileUiState())
    val uiState: StateFlow<CaregiverProfileUiState> = _uiState.asStateFlow()

    init {
        val currentUid = uid
        if (currentUid == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "You are signed out. Please sign in again.",
                )
            }
        } else {
            viewModelScope.launch {
                observeCaregiverProfile(currentUid).collect { snapshot ->
                    _uiState.update { current ->
                        // Only the initial load populates the form; after that the
                        // user's typing wins over Firestore echoes.
                        if (!current.isLoading) {
                            current
                        } else {
                            current.copy(
                                displayName = snapshot.user?.displayName.orEmpty(),
                                email = snapshot.user?.email.orEmpty(),
                                phoneNumber = snapshot.user?.phoneNumber.orEmpty(),
                                relationship = snapshot.profile
                                    ?.relationshipToPatient.orEmpty(),
                                address = snapshot.profile?.address.orEmpty(),
                                isAvailableForAlerts = snapshot.profile
                                    ?.isAvailableForAlerts ?: true,
                                isLoading = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun onDisplayNameChange(value: String) = edit(SaveCaregiverProfileUseCase.FIELD_NAME) {
        it.copy(displayName = value)
    }

    fun onPhoneNumberChange(value: String) = edit(SaveCaregiverProfileUseCase.FIELD_PHONE) {
        it.copy(phoneNumber = value)
    }

    fun onRelationshipChange(value: String) =
        edit(SaveCaregiverProfileUseCase.FIELD_RELATIONSHIP) {
            it.copy(relationship = value)
        }

    fun onAddressChange(value: String) = edit(null) { it.copy(address = value) }

    fun onAvailabilityChange(value: Boolean) =
        edit(null) { it.copy(isAvailableForAlerts = value) }

    fun save() {
        val currentUid = uid ?: return
        val state = _uiState.value
        if (state.isSaving) return

        _uiState.update {
            it.copy(isSaving = true, errorMessage = null, savedAtLeastOnce = false)
        }

        viewModelScope.launch {
            val result = saveCaregiverProfile(
                SaveCaregiverProfileUseCase.Params(
                    uid = currentUid,
                    displayName = state.displayName,
                    phoneNumber = state.phoneNumber,
                    profile = CaregiverProfile(
                        uid = currentUid,
                        relationshipToPatient = state.relationship.ifBlank { null },
                        address = state.address.ifBlank { null },
                        isAvailableForAlerts = state.isAvailableForAlerts,
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
        transform: (CaregiverProfileUiState) -> CaregiverProfileUiState,
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
