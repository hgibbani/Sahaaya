package com.sahaaya.domain.model

/**
 * Someone to call when something goes wrong, stored under
 * `patients/{uid}/emergencyContacts/{contactId}`.
 *
 * [priority] is the calling order: 1 is tried first. Phase 3's escalation logic
 * walks this list in order, which is why it is a stable integer rather than a
 * derived position in a UI list.
 */
data class EmergencyContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val priority: Int = DEFAULT_PRIORITY,
    val isPrimary: Boolean = false,
) {
    companion object {
        const val DEFAULT_PRIORITY = 99
        const val MAX_CONTACTS = 5
    }
}
