package com.sahaaya.feature.profile.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.EmptyState
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SahaayaTextField
import com.sahaaya.common.components.SecondaryButton
import com.sahaaya.common.components.StatusChip
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.EmergencyContact
import com.sahaaya.domain.usecase.profile.SaveEmergencyContactUseCase

@Composable
fun EmergencyContactsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmergencyContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EmergencyContactsContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onStartAdding = viewModel::startAdding,
        onStartEditing = viewModel::startEditing,
        onCancelDraft = viewModel::cancelDraft,
        onNameChange = viewModel::onNameChange,
        onPhoneNumberChange = viewModel::onPhoneNumberChange,
        onRelationshipChange = viewModel::onRelationshipChange,
        onIsPrimaryChange = viewModel::onIsPrimaryChange,
        onSaveDraft = viewModel::saveDraft,
        onDelete = viewModel::deleteContact,
        modifier = modifier,
    )
}

@Composable
private fun EmergencyContactsContent(
    state: EmergencyContactsUiState,
    onNavigateBack: () -> Unit,
    onStartAdding: () -> Unit,
    onStartEditing: (EmergencyContact) -> Unit,
    onCancelDraft: () -> Unit,
    onNameChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onIsPrimaryChange: (Boolean) -> Unit,
    onSaveDraft: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(label = "Loading contacts")
        return
    }

    SahaayaScreen(
        title = "Emergency contacts",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        if (state.errorMessage != null) {
            Banner(message = state.errorMessage)
        }

        Text(
            text = "These are the people to call if something goes wrong. " +
                "The primary contact is tried first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.draft != null) {
            DraftForm(
                draft = state.draft,
                fieldErrors = state.fieldErrors,
                isSaving = state.isSaving,
                onNameChange = onNameChange,
                onPhoneNumberChange = onPhoneNumberChange,
                onRelationshipChange = onRelationshipChange,
                onIsPrimaryChange = onIsPrimaryChange,
                onSave = onSaveDraft,
                onCancel = onCancelDraft,
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
        }

        if (state.contacts.isEmpty() && state.draft == null) {
            EmptyState(
                title = "No contacts yet",
                description = "Add at least one person who should be called first " +
                    "in an emergency.",
                actionLabel = "Add a contact",
                onAction = onStartAdding,
            )
        } else {
            state.contacts.forEach { contact ->
                ContactRow(
                    contact = contact,
                    isDeleting = state.deletingContactId == contact.id,
                    onEdit = { onStartEditing(contact) },
                    onDelete = { onDelete(contact.id) },
                )
            }
        }

        if (state.draft == null && state.contacts.isNotEmpty()) {
            if (state.canAddMore) {
                SecondaryButton(
                    text = "Add another contact",
                    onClick = onStartAdding,
                    leadingIcon = Icons.Filled.PersonAdd,
                )
            } else {
                Banner(
                    message = "You have saved the maximum of " +
                        "${EmergencyContact.MAX_CONTACTS} contacts. Remove one to " +
                        "add another.",
                    tone = BannerTone.Info,
                )
            }
        }
    }
}

@Composable
private fun DraftForm(
    draft: ContactDraft,
    fieldErrors: Map<String, String>,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onIsPrimaryChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaCard(modifier = modifier) {
        Text(
            text = if (draft.isNew) "New contact" else "Edit contact",
            style = MaterialTheme.typography.titleMedium,
        )

        SahaayaTextField(
            value = draft.name,
            onValueChange = onNameChange,
            label = "Name",
            error = fieldErrors[SaveEmergencyContactUseCase.FIELD_NAME],
            leadingIcon = Icons.Filled.Person,
            capitalization = KeyboardCapitalization.Words,
            enabled = !isSaving,
        )

        SahaayaTextField(
            value = draft.phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = "Mobile number",
            error = fieldErrors[SaveEmergencyContactUseCase.FIELD_PHONE],
            leadingIcon = Icons.Filled.Phone,
            keyboardType = KeyboardType.Phone,
            capitalization = KeyboardCapitalization.None,
            enabled = !isSaving,
        )

        SahaayaTextField(
            value = draft.relationship,
            onValueChange = onRelationshipChange,
            label = "Relationship",
            error = fieldErrors[SaveEmergencyContactUseCase.FIELD_RELATIONSHIP],
            supportingText = "For example daughter, neighbour, doctor",
            imeAction = ImeAction.Done,
            enabled = !isSaving,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Call this person first",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Only one contact can be first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = draft.isPrimary,
                onCheckedChange = onIsPrimaryChange,
                enabled = !isSaving,
            )
        }

        PrimaryButton(
            text = if (draft.isNew) "Add contact" else "Save changes",
            onClick = onSave,
            loading = isSaving,
        )
        SecondaryButton(text = "Cancel", onClick = onCancel, enabled = !isSaving)
    }
}

@Composable
private fun ContactRow(
    contact: EmergencyContact,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            ) {
                Text(text = contact.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = contact.relationship,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (contact.isPrimary) {
                    StatusChip(label = "Called first", tone = BannerTone.Success)
                }
            }

            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.5.dp,
                )
            } else {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit ${contact.name}",
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete ${contact.name}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmergencyContactsPreview() {
    SahaayaTheme {
        EmergencyContactsContent(
            state = EmergencyContactsUiState(
                contacts = listOf(
                    EmergencyContact(
                        id = "1",
                        name = "Chirag Rai",
                        phoneNumber = "9876543210",
                        relationship = "Son",
                        priority = 1,
                        isPrimary = true,
                    ),
                ),
                isLoading = false,
            ),
            onNavigateBack = {},
            onStartAdding = {},
            onStartEditing = {},
            onCancelDraft = {},
            onNameChange = {},
            onPhoneNumberChange = {},
            onRelationshipChange = {},
            onIsPrimaryChange = {},
            onSaveDraft = {},
            onDelete = {},
        )
    }
}
