package com.sahaaya.feature.profile.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EmergencyContact
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.usecase.profile.DeleteEmergencyContactUseCase
import com.sahaaya.domain.usecase.profile.ObserveEmergencyContactsUseCase
import com.sahaaya.domain.usecase.profile.SaveEmergencyContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The add/edit form, held separately from the saved list. */
data class ContactDraft(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = "",
    val isPrimary: Boolean = false,
) {
    val isNew: Boolean get() = id.isBlank()
}

data class EmergencyContactsUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val draft: ContactDraft? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val deletingContactId: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
) {
    val canAddMore: Boolean get() = contacts.size < EmergencyContact.MAX_CONTACTS
}

@HiltViewModel
class EmergencyContactsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val observeEmergencyContacts: ObserveEmergencyContactsUseCase,
    private val saveEmergencyContact: SaveEmergencyContactUseCase,
    private val deleteEmergencyContact: DeleteEmergencyContactUseCase,
) : ViewModel() {

    private val uid = authRepository.currentUserId()

    private val _uiState = MutableStateFlow(EmergencyContactsUiState())
    val uiState: StateFlow<EmergencyContactsUiState> = _uiState.asStateFlow()

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
                observeEmergencyContacts(currentUid).collect { contacts ->
                    _uiState.update { it.copy(contacts = contacts, isLoading = false) }
                }
            }
        }
    }

    fun startAdding() {
        _uiState.update {
            it.copy(draft = ContactDraft(), fieldErrors = emptyMap(), errorMessage = null)
        }
    }

    fun startEditing(contact: EmergencyContact) {
        _uiState.update {
            it.copy(
                draft = ContactDraft(
                    id = contact.id,
                    name = contact.name,
                    phoneNumber = contact.phoneNumber,
                    relationship = contact.relationship,
                    isPrimary = contact.isPrimary,
                ),
                fieldErrors = emptyMap(),
                errorMessage = null,
            )
        }
    }

    fun cancelDraft() {
        _uiState.update { it.copy(draft = null, fieldErrors = emptyMap()) }
    }

    fun onNameChange(value: String) = editDraft(SaveEmergencyContactUseCase.FIELD_NAME) {
        it.copy(name = value)
    }

    fun onPhoneNumberChange(value: String) =
        editDraft(SaveEmergencyContactUseCase.FIELD_PHONE) { it.copy(phoneNumber = value) }

    fun onRelationshipChange(value: String) =
        editDraft(SaveEmergencyContactUseCase.FIELD_RELATIONSHIP) {
            it.copy(relationship = value)
        }

    fun onIsPrimaryChange(value: Boolean) = editDraft(null) { it.copy(isPrimary = value) }

    /**
     * Saves the draft.
     *
     * Priority is derived from position rather than asked for: a primary contact
     * is 1, everyone else keeps the order they were added in. Making a caregiver
     * hand-number an escalation chain invites gaps and duplicates in exactly the
     * list that must not have them.
     */
    fun saveDraft() {
        val currentUid = uid ?: return
        val state = _uiState.value
        val draft = state.draft ?: return
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val priority = when {
                draft.isPrimary -> PRIMARY_PRIORITY
                draft.isNew -> state.contacts.size + 2
                else -> state.contacts
                    .firstOrNull { it.id == draft.id }
                    ?.priority
                    ?: EmergencyContact.DEFAULT_PRIORITY
            }

            val result = saveEmergencyContact(
                SaveEmergencyContactUseCase.Params(
                    patientId = currentUid,
                    contact = EmergencyContact(
                        id = draft.id,
                        name = draft.name,
                        phoneNumber = draft.phoneNumber,
                        relationship = draft.relationship,
                        priority = priority,
                        isPrimary = draft.isPrimary,
                    ),
                    existingCount = state.contacts.size,
                ),
            )

            when (result) {
                is Outcome.Success -> {
                    // Demote any previous primary so exactly one contact is
                    // first in the chain.
                    if (draft.isPrimary) {
                        state.contacts
                            .filter { it.isPrimary && it.id != draft.id }
                            .forEach { previous ->
                                saveEmergencyContact(
                                    SaveEmergencyContactUseCase.Params(
                                        patientId = currentUid,
                                        contact = previous.copy(
                                            isPrimary = false,
                                            priority = previous.priority
                                                .coerceAtLeast(PRIMARY_PRIORITY + 1),
                                        ),
                                        existingCount = state.contacts.size,
                                    ),
                                )
                            }
                    }
                    _uiState.update {
                        it.copy(isSaving = false, draft = null, fieldErrors = emptyMap())
                    }
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

    fun deleteContact(contactId: String) {
        val currentUid = uid ?: return
        if (_uiState.value.deletingContactId != null) return

        _uiState.update { it.copy(deletingContactId = contactId) }

        viewModelScope.launch {
            when (val result = deleteEmergencyContact(currentUid, contactId)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(deletingContactId = null, errorMessage = null)
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(deletingContactId = null, errorMessage = result.error.message)
                }
            }
        }
    }

    private fun editDraft(clearedField: String?, transform: (ContactDraft) -> ContactDraft) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(
                draft = transform(draft),
                fieldErrors = if (clearedField == null) {
                    state.fieldErrors
                } else {
                    state.fieldErrors - clearedField
                },
            )
        }
    }

    private companion object {
        const val PRIMARY_PRIORITY = 1
    }
}
