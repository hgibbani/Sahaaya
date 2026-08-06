package com.sahaaya.feature.medication.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.DoseStatus
import com.sahaaya.domain.model.Medication
import com.sahaaya.domain.model.MedicationDose
import com.sahaaya.domain.model.MedicationSchedule
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.usecase.medication.DeleteMedicationUseCase
import com.sahaaya.domain.usecase.medication.ObserveMedicationsUseCase
import com.sahaaya.domain.usecase.medication.ObserveRecentDosesUseCase
import com.sahaaya.domain.usecase.medication.RespondToDoseUseCase
import com.sahaaya.domain.usecase.medication.SaveMedicationUseCase
import com.sahaaya.feature.medication.MedicationRoutes
import com.sahaaya.feature.medication.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The add/edit form, held apart from the saved list. */
data class MedicationDraft(
    val id: String = "",
    val name: String = "",
    val dosage: String = "",
    val timesOfDayMinutes: List<Int> = listOf(9 * 60),
    val schedule: MedicationSchedule = MedicationSchedule.DAILY,
    val daysOfWeek: Set<Int> = (1..7).toSet(),
    val notes: String = "",
) {
    val isNew: Boolean get() = id.isBlank()
}

data class MedicationUiState(
    val medications: List<Medication> = emptyList(),
    val recentDoses: List<MedicationDose> = emptyList(),
    val draft: MedicationDraft? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isReadOnly: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
) {
    val pendingDoses: List<MedicationDose>
        get() = recentDoses.filter { it.status == DoseStatus.PENDING }

    val missedDoses: List<MedicationDose>
        get() = recentDoses.filter { it.status == DoseStatus.MISSED }

    /** Share of answered doses that were actually taken, over the loaded window. */
    val adherencePercent: Int?
        get() {
            val answered = recentDoses.count { it.status != DoseStatus.PENDING }
            if (answered == 0) return null
            val taken = recentDoses.count { it.status == DoseStatus.TAKEN }
            return (taken * 100) / answered
        }
}

/**
 * Serves both the patient's own medicine list and a caregiver's read-only view.
 *
 * The distinction is the optional patientId route argument, exactly as on the
 * profile screens. A caregiver needs to see what was missed; changing someone
 * else's prescription is not a Phase-3 capability.
 */
@HiltViewModel
class MedicationListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    observeMedications: ObserveMedicationsUseCase,
    observeRecentDoses: ObserveRecentDosesUseCase,
    private val saveMedication: SaveMedicationUseCase,
    private val deleteMedication: DeleteMedicationUseCase,
    private val respondToDose: RespondToDoseUseCase,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val viewedPatientId: String? =
        savedStateHandle.get<String>(MedicationRoutes.ARG_PATIENT_ID)
            ?.takeIf { it.isNotBlank() && it != MedicationRoutes.SELF }

    private val targetUid: String? = viewedPatientId ?: authRepository.currentUserId()
    private val isOwnList = viewedPatientId == null

    private val _uiState = MutableStateFlow(
        MedicationUiState(
            isReadOnly = !isOwnList,
            canScheduleExactAlarms = reminderScheduler.canScheduleExactAlarms(),
        ),
    )
    val uiState: StateFlow<MedicationUiState> = _uiState.asStateFlow()

    init {
        val uid = targetUid
        if (uid == null) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "You are signed out.")
            }
        } else {
            viewModelScope.launch {
                combine(
                    observeMedications(uid),
                    observeRecentDoses(uid),
                ) { medications, doses -> medications to doses }
                    .collect { (medications, doses) ->
                        _uiState.update {
                            it.copy(
                                medications = medications,
                                recentDoses = doses,
                                isLoading = false,
                            )
                        }
                        // Alarms live on the device, not in Firestore, so they
                        // are re-booked whenever the list is loaded. This is
                        // what restores reminders after a reinstall or a
                        // sign-in on a new phone.
                        if (isOwnList) reminderScheduler.scheduleAll(medications)
                    }
            }
        }
    }

    // --- draft editing -----------------------------------------------------

    fun startAdding() {
        _uiState.update {
            it.copy(draft = MedicationDraft(), fieldErrors = emptyMap(), errorMessage = null)
        }
    }

    fun startEditing(medication: Medication) {
        _uiState.update {
            it.copy(
                draft = MedicationDraft(
                    id = medication.id,
                    name = medication.name,
                    dosage = medication.dosage,
                    timesOfDayMinutes = medication.timesOfDayMinutes,
                    schedule = medication.schedule,
                    daysOfWeek = medication.daysOfWeek.ifEmpty { (1..7).toSet() },
                    notes = medication.notes.orEmpty(),
                ),
                fieldErrors = emptyMap(),
                errorMessage = null,
            )
        }
    }

    fun cancelDraft() {
        _uiState.update { it.copy(draft = null, fieldErrors = emptyMap()) }
    }

    fun onNameChange(value: String) = editDraft(SaveMedicationUseCase.FIELD_NAME) {
        it.copy(name = value)
    }

    fun onDosageChange(value: String) = editDraft(SaveMedicationUseCase.FIELD_DOSAGE) {
        it.copy(dosage = value)
    }

    fun onNotesChange(value: String) = editDraft(null) { it.copy(notes = value) }

    fun onScheduleChange(schedule: MedicationSchedule) =
        editDraft(SaveMedicationUseCase.FIELD_DAYS) { it.copy(schedule = schedule) }

    fun onDayToggled(isoDay: Int) = editDraft(SaveMedicationUseCase.FIELD_DAYS) { draft ->
        draft.copy(
            daysOfWeek = if (isoDay in draft.daysOfWeek) {
                draft.daysOfWeek - isoDay
            } else {
                draft.daysOfWeek + isoDay
            },
        )
    }

    fun onTimeAdded(minutesOfDay: Int) = editDraft(SaveMedicationUseCase.FIELD_TIMES) { draft ->
        if (minutesOfDay in draft.timesOfDayMinutes) return@editDraft draft
        draft.copy(timesOfDayMinutes = (draft.timesOfDayMinutes + minutesOfDay).sorted())
    }

    fun onTimeRemoved(minutesOfDay: Int) = editDraft(SaveMedicationUseCase.FIELD_TIMES) { draft ->
        draft.copy(timesOfDayMinutes = draft.timesOfDayMinutes - minutesOfDay)
    }

    // --- actions -----------------------------------------------------------

    fun saveDraft() {
        val draft = _uiState.value.draft ?: return
        if (_uiState.value.isSaving) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val result = saveMedication(
                Medication(
                    id = draft.id,
                    patientId = targetUid.orEmpty(),
                    name = draft.name,
                    dosage = draft.dosage,
                    timesOfDayMinutes = draft.timesOfDayMinutes,
                    schedule = draft.schedule,
                    daysOfWeek = if (draft.schedule == MedicationSchedule.SPECIFIC_DAYS) {
                        draft.daysOfWeek
                    } else {
                        emptySet()
                    },
                    notes = draft.notes.ifBlank { null },
                ),
            )

            when (result) {
                is Outcome.Success -> {
                    reminderScheduler.schedule(result.data)
                    _uiState.update {
                        it.copy(isSaving = false, draft = null, fieldErrors = emptyMap())
                    }
                }

                is Outcome.Failure -> _uiState.update { state ->
                    state.copy(
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

    fun delete(medication: Medication) {
        viewModelScope.launch {
            // Cancel the alarms first: a deleted medicine that keeps prompting
            // is worse than one that was never added.
            reminderScheduler.cancel(medication)
            when (val result = deleteMedication(medication.id)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    fun answerDose(doseId: String, taken: Boolean) {
        viewModelScope.launch {
            when (val result = respondToDose(doseId, taken)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun editDraft(
        clearedField: String?,
        transform: (MedicationDraft) -> MedicationDraft,
    ) {
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
}
