package com.sahaaya.firebase

/**
 * Every collection and field name Sahaaya reads or writes, in one place.
 *
 * String literals for paths are banned elsewhere in the codebase: a typo in a
 * Firestore path does not fail to compile, it silently reads an empty document,
 * which in a care app means an alert that never arrives. Anything referenced
 * here is documented in docs/FIREBASE.md.
 */
object FirestoreCollections {

    /** `users/{uid}` - identity and role. Readable by the owner and by any
     *  caregiver actively paired with them. */
    const val USERS = "users"

    /** `patients/{uid}` - clinical detail for a patient account. */
    const val PATIENTS = "patients"

    /** `caregivers/{uid}` - caregiver-specific detail. */
    const val CAREGIVERS = "caregivers"

    /** `pairings/{patientId}_{caregiverId}` - the consent record. */
    const val PAIRINGS = "pairings"

    /** `pairingCodes/{code}` - short-lived redemption codes. */
    const val PAIRING_CODES = "pairingCodes"

    /** `patients/{uid}/emergencyContacts/{contactId}` */
    const val EMERGENCY_CONTACTS = "emergencyContacts"

    /**
     * `patients/{uid}/reminders/{reminderId}` - appointments and health checks,
     * shared between the patient and their paired caregivers.
     */
    const val REMINDERS = "reminders"

    /**
     * `events/{eventId}` - falls, geofence exits, inactivity, SOS, missed doses.
     *
     * Top-level rather than a sub-collection of the patient, so a caregiver
     * looking after several people gets one timeline from one query
     * (`whereIn patientId`). A sub-collection would need one listener per
     * patient and a client-side merge to order them.
     */
    const val EVENTS = "events"

    /** `medications/{medicationId}` - keyed by patientId for the same reason. */
    const val MEDICATIONS = "medications"

    /** `doses/{medicationId}_{scheduledAt}` - one row per scheduled occurrence. */
    const val DOSES = "doses"

    /**
     * `notifications/{notificationId}` - the delivery log written by the Cloud
     * Function. Kept so a caregiver can see that an alert was sent even if the
     * push never arrived on their device.
     */
    const val NOTIFICATIONS = "notifications"

    /** `settings/{patientId}` - monitoring thresholds and the safe zone. */
    const val SETTINGS = "settings"

    /**
     * `trackingSessions/{sessionId}` - one caregiver-requested watch.
     *
     * Top-level rather than nested under the patient because the route beneath
     * it is the only unbounded-growth collection this feature adds, and keeping
     * it out of `patients/{uid}` means a caregiver reading a patient's clinical
     * document never incidentally pages through their movements.
     */
    const val TRACKING_SESSIONS = "trackingSessions"

    /** `trackingSessions/{sessionId}/locations/{id}` - the route points. */
    const val TRACK_LOCATIONS = "locations"
}

/** Field names on `events/{eventId}`. */
object EventFields {
    const val ID = "id"
    const val PATIENT_ID = "patientId"
    const val PATIENT_NAME = "patientName"
    const val TYPE = "type"
    const val SEVERITY = "severity"
    const val STATUS = "status"
    const val OCCURRED_AT = "occurredAt"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val ACCURACY = "accuracyMetres"
    const val SUMMARY = "summary"
    const val DETAILS = "details"
    const val ACKNOWLEDGED_BY = "acknowledgedByCaregiverId"
    const val ACKNOWLEDGED_AT = "acknowledgedAt"
}

/** Field names on `medications/{medicationId}`. */
object MedicationFields {
    const val ID = "id"
    const val PATIENT_ID = "patientId"
    const val NAME = "name"
    const val DOSAGE = "dosage"
    const val TIMES_OF_DAY = "timesOfDayMinutes"
    const val SCHEDULE = "schedule"
    const val DAYS_OF_WEEK = "daysOfWeek"
    const val NOTES = "notes"
    const val IS_ACTIVE = "isActive"
    const val CREATED_AT = "createdAt"
}

/** Field names on `doses/{doseId}`. */
object DoseFields {
    const val ID = "id"
    const val MEDICATION_ID = "medicationId"
    const val PATIENT_ID = "patientId"
    const val MEDICATION_NAME = "medicationName"
    const val DOSAGE = "dosage"
    const val SCHEDULED_AT = "scheduledAt"
    const val STATUS = "status"
    const val RESPONDED_AT = "respondedAt"
}

/** Field names on `settings/{patientId}`. */
object SettingsFields {
    const val PATIENT_ID = "patientId"
    const val FALL_ENABLED = "fallDetectionEnabled"
    const val FALL_SENSITIVITY = "fallSensitivity"
    const val INACTIVITY_ENABLED = "inactivityDetectionEnabled"
    const val INACTIVITY_TIMEOUT = "inactivityTimeoutMinutes"
    const val GEOFENCE_ENABLED = "geofenceEnabled"
    const val SAFE_ZONE_LAT = "safeZoneLatitude"
    const val SAFE_ZONE_LON = "safeZoneLongitude"
    const val SAFE_ZONE_RADIUS = "safeZoneRadiusMetres"
    const val SAFE_ZONE_LABEL = "safeZoneLabel"
    const val MEDICATION_REMINDERS_ENABLED = "medicationRemindersEnabled"
    const val MISSED_DOSE_GRACE = "missedDoseGraceMinutes"
    const val UPDATED_AT = "updatedAt"

    // --- caregiver-controlled live tracking --------------------------------
    //
    // These live on the patient's settings document so the patient's phone can
    // watch them with the listener it already has - but unlike every other key
    // here, they are written by the CAREGIVER and are read-only to the patient.
    // The Firestore rule enforces that split; [TRACKING_KEYS] is the list it
    // and the rule must agree on.

    const val TRACKING_ACTIVE = "trackingActive"
    const val TRACKING_SESSION_ID = "trackingSessionId"
    const val TRACKING_STARTED_AT = "trackingStartedAt"
    const val TRACKING_STARTED_BY = "trackingStartedBy"
    const val TRACKING_STOPPED_AT = "trackingStoppedAt"
    const val TRACKING_STOPPED_BY = "trackingStoppedBy"

    /** Every field a patient must never be able to write. */
    val TRACKING_KEYS = listOf(
        TRACKING_ACTIVE,
        TRACKING_SESSION_ID,
        TRACKING_STARTED_AT,
        TRACKING_STARTED_BY,
        TRACKING_STOPPED_AT,
        TRACKING_STOPPED_BY,
    )
}

/** Field names on `trackingSessions/{sessionId}`. */
object TrackingSessionFields {
    const val PATIENT_ID = "patientId"
    const val CAREGIVER_ID = "caregiverId"
    const val STARTED_AT = "startedAt"
    const val STOPPED_AT = "stoppedAt"
    const val ACTIVE = "active"
}

/** Field names on `trackingSessions/{sessionId}/locations/{id}`. */
object TrackPointFields {
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val ACCURACY = "accuracy"
    const val TIMESTAMP = "timestamp"
}

/** Field names on `users/{uid}`. */
object UserFields {
    const val UID = "uid"
    const val EMAIL = "email"
    const val DISPLAY_NAME = "displayName"
    const val ROLE = "role"
    const val PHONE_NUMBER = "phoneNumber"
    const val PHOTO_URL = "photoUrl"
    const val FCM_TOKENS = "fcmTokens"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
}

/** Field names on `patients/{uid}`. */
object PatientFields {
    const val UID = "uid"
    const val DATE_OF_BIRTH = "dateOfBirth"
    const val GENDER = "gender"
    const val BLOOD_GROUP = "bloodGroup"
    const val HEIGHT = "height"
    const val ADDRESS = "address"
    const val DIAGNOSIS_STAGE = "diagnosisStage"
    const val DIAGNOSED_ON = "diagnosedOn"
    const val MEDICAL_NOTES = "medicalNotes"
    const val ALLERGIES = "allergies"
    const val PRIMARY_CAREGIVER_ID = "primaryCaregiverId"
    const val UPDATED_AT = "updatedAt"

    // --- Latest position -----------------------------------------------------
    // Written by the patient's own device and read by an actively paired
    // caregiver. They live on this document because its existing rules already
    // express exactly that, so no rule had to be widened to carry them.
    //
    // Always written as a targeted merge, never as part of the profile map, so
    // that saving the profile screen cannot blank the patient's location.
    const val LAST_LATITUDE = "lastLatitude"
    const val LAST_LONGITUDE = "lastLongitude"
    const val LAST_ACCURACY = "lastAccuracyMetres"
    const val LAST_LOCATION_AT = "lastLocationAt"
    const val SAFE_ZONE_STATUS = "safeZoneStatus"
    const val METRES_FROM_BOUNDARY = "metresFromBoundary"
}

/** Field names on `caregivers/{uid}`. */
object CaregiverFields {
    const val UID = "uid"
    const val RELATIONSHIP = "relationshipToPatient"
    const val ADDRESS = "address"
    const val AVAILABLE_FOR_ALERTS = "isAvailableForAlerts"
    const val UPDATED_AT = "updatedAt"
}

/** Field names on `pairings/{id}`. */
object PairingFields {
    const val ID = "id"
    const val PATIENT_ID = "patientId"
    const val CAREGIVER_ID = "caregiverId"
    const val PATIENT_NAME = "patientName"
    const val CAREGIVER_NAME = "caregiverName"
    const val PATIENT_PHONE = "patientPhone"
    const val CAREGIVER_PHONE = "caregiverPhone"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
    const val REVOKED_AT = "revokedAt"
}

/** Field names on `pairingCodes/{code}`. */
object PairingCodeFields {
    const val CODE = "code"
    const val PATIENT_ID = "patientId"
    const val PATIENT_NAME = "patientName"
    const val PATIENT_PHONE = "patientPhone"
    const val CREATED_AT = "createdAt"
    const val EXPIRES_AT = "expiresAt"
    const val REDEEMED_BY = "redeemedByCaregiverId"
}

/** Field names on an emergency contact document. */
object EmergencyContactFields {
    const val ID = "id"
    const val NAME = "name"
    const val PHONE_NUMBER = "phoneNumber"
    const val RELATIONSHIP = "relationship"
    const val PRIORITY = "priority"
    const val IS_PRIMARY = "isPrimary"
}

/** Field names on `patients/{uid}/reminders/{reminderId}`. */
object ReminderFields {
    const val ID = "id"
    const val PATIENT_ID = "patientId"
    const val TYPE = "type"
    const val TITLE = "title"
    const val HOSPITAL_NAME = "hospitalName"
    const val DOCTOR_NAME = "doctorName"
    const val SCHEDULED_AT = "scheduledAt"
    const val NOTES = "notes"
    const val CREATED_BY_UID = "createdByUid"
    const val CREATED_BY_NAME = "createdByName"
    const val CREATED_AT = "createdAt"
}
