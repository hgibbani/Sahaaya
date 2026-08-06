package com.sahaaya.domain.model

/**
 * Who a signed-in account belongs to.
 *
 * The role is chosen once at registration and decides which dashboard the user
 * lands on, what they may read in Firestore, and which half of a [Pairing] they
 * occupy. It is stored on the `users/{uid}` document and mirrored into a custom
 * claim by the security rules described in docs/FIREBASE.md.
 *
 * Sahaaya 360 will add clinician and facility roles here. Because every
 * role-dependent decision in the app goes through this enum and the
 * `when` blocks over it are exhaustive, adding a case makes the compiler point
 * at every place that needs a decision.
 */
enum class Role(val storageKey: String, val displayName: String) {

    /** The person being cared for. Uses the simplified, large-target UI. */
    PATIENT("patient", "Patient"),

    /** A family member or attendant who receives alerts about a patient. */
    CAREGIVER("caregiver", "Caregiver"),
    ;

    companion object {
        fun fromStorageKey(key: String?): Role? =
            entries.firstOrNull { it.storageKey == key }
    }
}
