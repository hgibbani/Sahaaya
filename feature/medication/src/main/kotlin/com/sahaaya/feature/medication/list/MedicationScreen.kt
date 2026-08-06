package com.sahaaya.feature.medication.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.EmptyState
import com.sahaaya.common.components.IconBadge
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SahaayaTextField
import com.sahaaya.common.components.SecondaryButton
import com.sahaaya.common.components.StatusChip
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.DoseStatus
import com.sahaaya.domain.model.Medication
import com.sahaaya.domain.model.MedicationDose
import com.sahaaya.domain.model.MedicationSchedule
import com.sahaaya.domain.usecase.medication.SaveMedicationUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MedicationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicationListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MedicationContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onStartAdding = viewModel::startAdding,
        onStartEditing = viewModel::startEditing,
        onCancelDraft = viewModel::cancelDraft,
        onNameChange = viewModel::onNameChange,
        onDosageChange = viewModel::onDosageChange,
        onNotesChange = viewModel::onNotesChange,
        onScheduleChange = viewModel::onScheduleChange,
        onDayToggled = viewModel::onDayToggled,
        onTimeAdded = viewModel::onTimeAdded,
        onTimeRemoved = viewModel::onTimeRemoved,
        onSaveDraft = viewModel::saveDraft,
        onDelete = viewModel::delete,
        onAnswerDose = viewModel::answerDose,
        modifier = modifier,
    )
}

@Composable
private fun MedicationContent(
    state: MedicationUiState,
    onNavigateBack: () -> Unit,
    onStartAdding: () -> Unit,
    onStartEditing: (Medication) -> Unit,
    onCancelDraft: () -> Unit,
    onNameChange: (String) -> Unit,
    onDosageChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onScheduleChange: (MedicationSchedule) -> Unit,
    onDayToggled: (Int) -> Unit,
    onTimeAdded: (Int) -> Unit,
    onTimeRemoved: (Int) -> Unit,
    onSaveDraft: () -> Unit,
    onDelete: (Medication) -> Unit,
    onAnswerDose: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(label = "Loading medicines")
        return
    }

    SahaayaScreen(
        title = if (state.isReadOnly) "Their medicines" else "My medicines",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        if (state.errorMessage != null) Banner(message = state.errorMessage)

        if (!state.canScheduleExactAlarms && !state.isReadOnly) {
            Banner(
                message = "Android is not allowing exact alarms for Sahaaya, so " +
                    "reminders may arrive a few minutes late. You can allow them " +
                    "under Alarms & reminders in Android settings.",
                tone = BannerTone.Info,
            )
        }

        // --- Doses waiting for an answer ---
        if (state.pendingDoses.isNotEmpty() && !state.isReadOnly) {
            Text(text = "Waiting for you", style = MaterialTheme.typography.titleLarge)
            state.pendingDoses.forEach { dose ->
                PendingDoseCard(
                    dose = dose,
                    onTaken = { onAnswerDose(dose.id, true) },
                    onSkipped = { onAnswerDose(dose.id, false) },
                )
            }
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
        }

        // --- Caregiver summary ---
        if (state.isReadOnly) {
            SahaayaCard {
                Text(text = "Recent adherence", style = MaterialTheme.typography.titleMedium)
                val percent = state.adherencePercent
                Text(
                    text = if (percent == null) {
                        "No doses recorded yet."
                    } else {
                        "$percent% of recent doses were taken."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (state.missedDoses.isNotEmpty()) {
                    Text(
                        text = "${state.missedDoses.size} missed in the recent history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // --- Draft form ---
        if (state.draft != null) {
            MedicationDraftForm(
                draft = state.draft,
                fieldErrors = state.fieldErrors,
                isSaving = state.isSaving,
                onNameChange = onNameChange,
                onDosageChange = onDosageChange,
                onNotesChange = onNotesChange,
                onScheduleChange = onScheduleChange,
                onDayToggled = onDayToggled,
                onTimeAdded = onTimeAdded,
                onTimeRemoved = onTimeRemoved,
                onSave = onSaveDraft,
                onCancel = onCancelDraft,
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
        }

        // --- The list ---
        Text(text = "Medicines", style = MaterialTheme.typography.titleLarge)

        if (state.medications.isEmpty() && state.draft == null) {
            EmptyState(
                title = "No medicines yet",
                description = if (state.isReadOnly) {
                    "This patient has not added any medicines."
                } else {
                    "Add a medicine and Sahaaya will remind you at each dose time."
                },
                actionLabel = if (state.isReadOnly) null else "Add a medicine",
                onAction = if (state.isReadOnly) null else onStartAdding,
            )
        } else {
            state.medications.forEach { medication ->
                MedicationCard(
                    medication = medication,
                    readOnly = state.isReadOnly,
                    onEdit = { onStartEditing(medication) },
                    onDelete = { onDelete(medication) },
                )
            }
        }

        if (!state.isReadOnly && state.draft == null && state.medications.isNotEmpty()) {
            SecondaryButton(
                text = "Add another medicine",
                onClick = onStartAdding,
                leadingIcon = Icons.Filled.Add,
            )
        }

        // --- History ---
        if (state.recentDoses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
            Text(text = "Recent doses", style = MaterialTheme.typography.titleLarge)
            state.recentDoses.take(HISTORY_LIMIT).forEach { dose ->
                DoseHistoryRow(dose)
            }
        }
    }
}

@Composable
private fun PendingDoseCard(
    dose: MedicationDose,
    onTaken: () -> Unit,
    onSkipped: () -> Unit,
) {
    SahaayaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = dose.medicationName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = "${dose.dosage} - due ${formatTime(dose.scheduledAtEpochMillis)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            PrimaryButton(text = "Taken", onClick = onTaken, modifier = Modifier.weight(1f))
            SecondaryButton(text = "Skip", onClick = onSkipped, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationCard(
    medication: Medication,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    SahaayaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = Icons.Filled.Medication)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = medication.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = medication.dosage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!readOnly) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${medication.name}")
                }
                IconButton(onClick = { confirmingDelete = !confirmingDelete }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove ${medication.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            medication.timesOfDayMinutes.forEach { minutes ->
                AssistChip(onClick = {}, label = { Text(formatMinutesOfDay(minutes)) })
            }
        }

        Text(
            text = when (medication.schedule) {
                MedicationSchedule.DAILY -> "Every day"
                MedicationSchedule.SPECIFIC_DAYS ->
                    medication.daysOfWeek.sorted().joinToString(", ") { dayName(it) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        medication.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(text = notes, style = MaterialTheme.typography.bodyMedium)
        }

        if (confirmingDelete) {
            Text(
                text = "Remove ${medication.name}? Reminders stop, but the record " +
                    "of doses already taken is kept.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep") }
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationDraftForm(
    draft: MedicationDraft,
    fieldErrors: Map<String, String>,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onDosageChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onScheduleChange: (MedicationSchedule) -> Unit,
    onDayToggled: (Int) -> Unit,
    onTimeAdded: (Int) -> Unit,
    onTimeRemoved: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    SahaayaCard {
        Text(
            text = if (draft.isNew) "New medicine" else "Edit medicine",
            style = MaterialTheme.typography.titleMedium,
        )

        SahaayaTextField(
            value = draft.name,
            onValueChange = onNameChange,
            label = "Medicine name",
            error = fieldErrors[SaveMedicationUseCase.FIELD_NAME],
            capitalization = KeyboardCapitalization.Words,
            enabled = !isSaving,
        )

        SahaayaTextField(
            value = draft.dosage,
            onValueChange = onDosageChange,
            label = "Dosage",
            error = fieldErrors[SaveMedicationUseCase.FIELD_DOSAGE],
            supportingText = "For example 5 mg, or 1 tablet",
            enabled = !isSaving,
        )

        // Times are picked from a fixed set rather than a free clock, because
        // dose times in practice are meal-anchored and a fixed set is far
        // faster to tap than scrolling a time picker four times.
        Text(
            text = "Times of day",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            draft.timesOfDayMinutes.forEach { minutes ->
                InputChip(
                    selected = true,
                    onClick = { onTimeRemoved(minutes) },
                    label = { Text(formatMinutesOfDay(minutes)) },
                    enabled = !isSaving,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            COMMON_TIMES.filterNot { it in draft.timesOfDayMinutes }.forEach { minutes ->
                AssistChip(
                    onClick = { onTimeAdded(minutes) },
                    label = { Text("+ ${formatMinutesOfDay(minutes)}") },
                    enabled = !isSaving,
                )
            }
        }
        fieldErrors[SaveMedicationUseCase.FIELD_TIMES]?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            text = "How often",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            MedicationSchedule.entries.forEach { schedule ->
                FilterChip(
                    selected = draft.schedule == schedule,
                    onClick = { onScheduleChange(schedule) },
                    label = { Text(schedule.displayName) },
                    enabled = !isSaving,
                )
            }
        }

        if (draft.schedule == MedicationSchedule.SPECIFIC_DAYS) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                (1..7).forEach { day ->
                    FilterChip(
                        selected = day in draft.daysOfWeek,
                        onClick = { onDayToggled(day) },
                        label = { Text(dayName(day)) },
                        enabled = !isSaving,
                    )
                }
            }
            fieldErrors[SaveMedicationUseCase.FIELD_DAYS]?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SahaayaTextField(
            value = draft.notes,
            onValueChange = onNotesChange,
            label = "Notes",
            supportingText = "Optional. For example, take after food",
            singleLine = false,
            minLines = 2,
            imeAction = ImeAction.Done,
            enabled = !isSaving,
        )

        PrimaryButton(
            text = if (draft.isNew) "Add medicine" else "Save changes",
            onClick = onSave,
            loading = isSaving,
        )
        SecondaryButton(text = "Cancel", onClick = onCancel, enabled = !isSaving)
    }
}

@Composable
private fun DoseHistoryRow(dose: MedicationDose) {
    SahaayaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = dose.medicationName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = formatDateTime(dose.scheduledAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(
                label = dose.status.displayName,
                tone = when (dose.status) {
                    DoseStatus.TAKEN -> BannerTone.Success
                    DoseStatus.MISSED -> BannerTone.Error
                    else -> BannerTone.Info
                },
            )
        }
    }
}

// --- formatting -------------------------------------------------------------

private const val HISTORY_LIMIT = 20

/** Meal-anchored defaults, which is how dose times are actually described. */
private val COMMON_TIMES = listOf(
    7 * 60, 8 * 60, 9 * 60, 12 * 60, 14 * 60, 18 * 60, 20 * 60, 21 * 60, 22 * 60,
)

private fun formatMinutesOfDay(minutes: Int): String {
    val hour24 = minutes / 60
    val minute = minutes % 60
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val suffix = if (hour24 < 12) "am" else "pm"
    return "%d:%02d %s".format(hour12, minute, suffix)
}

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))

private fun formatDateTime(epochMillis: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(epochMillis))

private fun dayName(isoDay: Int): String = when (isoDay) {
    1 -> "Mon"
    2 -> "Tue"
    3 -> "Wed"
    4 -> "Thu"
    5 -> "Fri"
    6 -> "Sat"
    else -> "Sun"
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun MedicationPreview() {
    SahaayaTheme {
        MedicationContent(
            state = MedicationUiState(
                medications = listOf(
                    Medication(
                        id = "m1",
                        patientId = "p1",
                        name = "Donepezil",
                        dosage = "5 mg",
                        timesOfDayMinutes = listOf(9 * 60, 21 * 60),
                    ),
                ),
                isLoading = false,
            ),
            onNavigateBack = {},
            onStartAdding = {},
            onStartEditing = {},
            onCancelDraft = {},
            onNameChange = {},
            onDosageChange = {},
            onNotesChange = {},
            onScheduleChange = {},
            onDayToggled = {},
            onTimeAdded = {},
            onTimeRemoved = {},
            onSaveDraft = {},
            onDelete = {},
            onAnswerDose = { _, _ -> },
        )
    }
}
