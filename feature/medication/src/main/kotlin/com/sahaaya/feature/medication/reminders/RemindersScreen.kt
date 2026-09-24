package com.sahaaya.feature.medication.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.ActionCard
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.EmptyState
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SahaayaTextField
import com.sahaaya.common.components.SecondaryButton
import com.sahaaya.common.theme.Dimens
import com.sahaaya.domain.model.CareReminder
import com.sahaaya.domain.model.ReminderType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RemindersScreen(
    onNavigateBack: () -> Unit,
    onOpenMedicines: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        LoadingState(label = "Loading reminders")
        return
    }

    SahaayaScreen(
        title = "Reminders",
        onNavigateBack = if (state.draft != null) viewModel::cancelDraft else onNavigateBack,
        modifier = modifier,
    ) {
        if (state.errorMessage != null) Banner(message = state.errorMessage!!)
        if (state.message != null) {
            Banner(message = state.message!!, tone = BannerTone.Success)
        }

        val draft = state.draft
        if (draft != null) {
            ReminderForm(
                draft = draft,
                isSaving = state.isSaving,
                onChange = viewModel::updateDraft,
                onSave = viewModel::save,
                onCancel = viewModel::cancelDraft,
            )
            return@SahaayaScreen
        }

        Text(
            text = if (viewModel.isCaregiverView) {
                "Anything you add here appears on the patient's phone."
            } else {
                "Anything you add here is shared with your caregiver."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(text = "Add a reminder", style = MaterialTheme.typography.titleLarge)

        // Medicines are listed first because they are the most common, but they
        // open the existing medicines screen - that feature owns dose times and
        // alarms, and a second copy here would not ring.
        ActionCard(
            title = ReminderType.MEDICINE.displayName,
            subtitle = "Daily medicines and dose times",
            icon = Icons.Filled.Medication,
            onClick = onOpenMedicines,
        )
        ReminderType.storedTypes.forEach { type ->
            ActionCard(
                title = type.displayName,
                subtitle = subtitleFor(type),
                icon = iconFor(type),
                onClick = { viewModel.startAdding(type) },
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceSm))
        Text(text = "Upcoming", style = MaterialTheme.typography.titleLarge)

        if (state.upcoming.isEmpty()) {
            EmptyState(
                title = "Nothing coming up",
                description = "Appointments and health checks you add will appear here.",
            )
        } else {
            state.upcoming.forEach { reminder ->
                ReminderCard(reminder = reminder, onDelete = { viewModel.delete(reminder.id) })
            }
        }

        if (state.past.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
            Text(text = "Earlier", style = MaterialTheme.typography.titleLarge)
            state.past.take(MAX_PAST_SHOWN).forEach { reminder ->
                ReminderCard(reminder = reminder, onDelete = { viewModel.delete(reminder.id) })
            }
        }
    }
}

@Composable
private fun ReminderForm(
    draft: ReminderDraft,
    isSaving: Boolean,
    onChange: ((ReminderDraft) -> ReminderDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val chosen = Calendar.getInstance().apply {
        draft.scheduledAtEpochMillis?.let { timeInMillis = it }
    }

    Text(text = draft.type.displayName, style = MaterialTheme.typography.headlineSmall)

    when (draft.type) {
        ReminderType.HOSPITAL_APPOINTMENT -> {
            SahaayaTextField(
                value = draft.hospitalName,
                onValueChange = { v -> onChange { it.copy(hospitalName = v) } },
                label = "Hospital name",
            )
            SahaayaTextField(
                value = draft.doctorName,
                onValueChange = { v -> onChange { it.copy(doctorName = v) } },
                label = "Doctor name",
            )
        }
        ReminderType.GENERAL -> {
            SahaayaTextField(
                value = draft.title,
                onValueChange = { v -> onChange { it.copy(title = v) } },
                label = "What is it for?",
            )
        }
        else -> Unit
    }

    // Date and time as large tappable rows that open the system pickers.
    SahaayaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = "Date", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = draft.scheduledAtEpochMillis?.let { formatDate(it) } ?: "Not chosen",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            TextButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val c = Calendar.getInstance().apply {
                                timeInMillis = draft.scheduledAtEpochMillis
                                    ?: defaultTime().timeInMillis
                                set(year, month, day)
                            }
                            onChange { it.copy(scheduledAtEpochMillis = c.timeInMillis) }
                        },
                        chosen.get(Calendar.YEAR),
                        chosen.get(Calendar.MONTH),
                        chosen.get(Calendar.DAY_OF_MONTH),
                    ).show()
                },
            ) { Text("Choose date") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = "Time", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = draft.scheduledAtEpochMillis?.let { formatTime(it) } ?: "Not chosen",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            TextButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val c = Calendar.getInstance().apply {
                                timeInMillis = draft.scheduledAtEpochMillis
                                    ?: defaultTime().timeInMillis
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onChange { it.copy(scheduledAtEpochMillis = c.timeInMillis) }
                        },
                        chosen.get(Calendar.HOUR_OF_DAY),
                        chosen.get(Calendar.MINUTE),
                        false,
                    ).show()
                },
            ) { Text("Choose time") }
        }
    }

    SahaayaTextField(
        value = draft.notes,
        onValueChange = { v -> onChange { it.copy(notes = v) } },
        label = when (draft.type) {
            ReminderType.BLOOD_PRESSURE_CHECK,
            ReminderType.BLOOD_SUGAR_CHECK,
            -> "Notes or result (optional)"
            else -> "Notes (optional)"
        },
        singleLine = false,
    )

    PrimaryButton(
        text = if (isSaving) "Saving…" else "Save reminder",
        onClick = onSave,
        enabled = !isSaving,
    )
    SecondaryButton(text = "Cancel", onClick = onCancel, enabled = !isSaving)
}

@Composable
private fun ReminderCard(reminder: CareReminder, onDelete: () -> Unit) {
    SahaayaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = iconFor(reminder.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text(text = reminder.headline, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${formatDate(reminder.scheduledAtEpochMillis)}  " +
                            formatTime(reminder.scheduledAtEpochMillis),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (reminder.type != ReminderType.GENERAL &&
                        reminder.type != ReminderType.HOSPITAL_APPOINTMENT
                    ) {
                        Text(
                            text = reminder.type.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (reminder.notes.isNotBlank()) {
                        Text(
                            text = reminder.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (reminder.createdByName.isNotBlank()) {
                        Text(
                            text = "Added by ${reminder.createdByName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove ${reminder.headline}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun iconFor(type: ReminderType): ImageVector = when (type) {
    ReminderType.MEDICINE -> Icons.Filled.Medication
    ReminderType.HOSPITAL_APPOINTMENT -> Icons.Filled.LocalHospital
    ReminderType.BLOOD_PRESSURE_CHECK -> Icons.Filled.MonitorHeart
    ReminderType.BLOOD_SUGAR_CHECK -> Icons.Filled.Bloodtype
    ReminderType.GENERAL -> Icons.Filled.NotificationsActive
}

private fun subtitleFor(type: ReminderType): String = when (type) {
    ReminderType.HOSPITAL_APPOINTMENT -> "Hospital, doctor, date and time"
    ReminderType.BLOOD_PRESSURE_CHECK -> "When to check blood pressure"
    ReminderType.BLOOD_SUGAR_CHECK -> "When to check blood sugar"
    ReminderType.GENERAL -> "Anything else to remember"
    ReminderType.MEDICINE -> ""
}

/** Tomorrow at 10:00 - a sensible starting point for a new appointment. */
private fun defaultTime(): Calendar = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 10)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(epochMillis))

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))

private const val MAX_PAST_SHOWN = 10

