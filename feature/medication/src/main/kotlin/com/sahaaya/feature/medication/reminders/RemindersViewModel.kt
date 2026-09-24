package com.sahaaya.feature.medication.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CareReminder
import com.sahaaya.domain.model.ReminderType
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.usecase.reminder.DeleteReminderUseCase
import com.sahaaya.domain.usecase.reminder.ObserveRemindersUseCase
import com.sahaaya.domain.usecase.reminder.SaveReminderUseCase
import com.sahaaya.feature.medication.MedicationRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The add form. One shape for every type; the screen shows the relevant fields. */
data class ReminderDraft(
    val type: ReminderType = ReminderType.HOSPITAL_APPOINTMENT,
    val title: String = "",
    val hospitalName: String = "",
    val doctorName: String = "",
    /** Epoch millis of the chosen date + time, or null until both are picked. */
    val scheduledAtEpochMillis: Long? = null,
    val notes: String = "",
)

data class RemindersUiState(
    val reminders: List<CareReminder> = emptyList(),
    val isLoading: Boolean = true,
    val draft: ReminderDraft? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    private val now: Long get() = System.currentTimeMillis()

    val upcoming: List<CareReminder> get() = reminders.filterNot { it.isPast(now) }
    val past: List<CareReminder> get() = reminders.filter { it.isPast(now) }.reversed()
}

/**
 * The shared reminders list.
 *
 * One screen for both roles, keyed on the route's patient id exactly as the
 * medicines screen is: the patient opens it with `self`, a caregiver with the
 * patient they are linked to. Both can add and delete - the Firestore rule for
 * `patients/{uid}/reminders` is what decides that a caregiver may, and only
 * while their pairing is active.
 */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    observeReminders: ObserveRemindersUseCase,
    private val saveReminder: SaveReminderUseCase,
    private val deleteReminder: DeleteReminderUseCase,
) : ViewModel() {

    private val viewedPatientId: String? =
        savedStateHandle.get<String>(MedicationRoutes.ARG_PATIENT_ID)
            ?.takeIf { it.isNotBlank() && it != MedicationRoutes.SELF }

    /** Whose reminders these are - the linked patient, or the signed-in patient. */
    private val patientId: String? = viewedPatientId ?: authRepository.currentUserId()

    /** Caregivers see a heading naming whose list they are editing. */
    val isCaregiverView: Boolean = viewedPatientId != null

    private val draft = MutableStateFlow<ReminderDraft?>(null)
    private val saving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<RemindersUiState> = combine(
        patientId?.let { observeReminders(it) } ?: flowOf(emptyList()),
        draft,
        saving,
        message,
        error,
    ) { reminders, currentDraft, isSaving, info, problem ->
        RemindersUiState(
            reminders = reminders,
            isLoading = false,
            draft = currentDraft,
            isSaving = isSaving,
            message = info,
            errorMessage = problem ?: if (patientId == null) "You are signed out." else null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RemindersUiState(),
    )

    fun startAdding(type: ReminderType) {
        message.update { null }
        error.update { null }
        draft.update { ReminderDraft(type = type) }
    }

    fun updateDraft(transform: (ReminderDraft) -> ReminderDraft) {
        draft.update { it?.let(transform) }
    }

    fun cancelDraft() {
        draft.update { null }
    }

    fun save() {
        val current = draft.value ?: return
        val owner = patientId ?: return
        if (saving.value) return

        val scheduledAt = current.scheduledAtEpochMillis
        if (scheduledAt == null) {
            error.update { "Please choose a date and a time." }
            return
        }

        saving.update { true }
        error.update { null }
        viewModelScope.launch {
            val result = saveReminder(
                CareReminder(
                    id = "",
                    patientId = owner,
                    type = current.type,
                    title = current.title.trim(),
                    hospitalName = current.hospitalName.trim(),
                    doctorName = current.doctorName.trim(),
                    scheduledAtEpochMillis = scheduledAt,
                    notes = current.notes.trim(),
                ),
            )
            when (result) {
                is Outcome.Success -> {
                    draft.update { null }
                    message.update {
                        if (isCaregiverView) {
                            "Reminder saved. It will appear on the patient's phone."
                        } else {
                            "Reminder saved. Your caregiver can see it too."
                        }
                    }
                }
                is Outcome.Failure -> error.update { result.error.message }
            }
            saving.update { false }
        }
    }

    fun delete(reminderId: String) {
        val owner = patientId ?: return
        viewModelScope.launch {
            when (val result = deleteReminder(owner, reminderId)) {
                is Outcome.Success -> message.update { "Reminder removed." }
                is Outcome.Failure -> error.update { result.error.message }
            }
        }
    }

    fun onMessageShown() {
        message.update { null }
        error.update { null }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
