package com.sahaaya.data.mapper

import com.google.firebase.firestore.DocumentSnapshot
import com.sahaaya.domain.model.DoseStatus
import com.sahaaya.domain.model.EventStatus
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.FallSensitivity
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.model.Medication
import com.sahaaya.domain.model.MedicationDose
import com.sahaaya.domain.model.MedicationSchedule
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.firebase.DoseFields
import com.sahaaya.firebase.EventFields
import com.sahaaya.firebase.MedicationFields
import com.sahaaya.firebase.SettingsFields

// --- events/{eventId} -------------------------------------------------------

fun DocumentSnapshot.toHealthEvent(): HealthEvent? {
    val type = EventType.fromStorageKey(getString(EventFields.TYPE)) ?: return null
    val patientId = getString(EventFields.PATIENT_ID) ?: return null

    val latitude = getDouble(EventFields.LATITUDE)
    val longitude = getDouble(EventFields.LONGITUDE)

    @Suppress("UNCHECKED_CAST")
    val details = (get(EventFields.DETAILS) as? Map<String, Any?>)
        ?.mapValues { (_, value) -> value?.toString().orEmpty() }
        .orEmpty()

    return HealthEvent(
        id = getString(EventFields.ID) ?: id,
        patientId = patientId,
        patientName = getString(EventFields.PATIENT_NAME).orEmpty(),
        type = type,
        status = EventStatus.fromStorageKey(getString(EventFields.STATUS)),
        occurredAtEpochMillis = getLong(EventFields.OCCURRED_AT) ?: 0L,
        location = if (latitude != null && longitude != null) {
            GeoPoint(
                latitude = latitude,
                longitude = longitude,
                accuracyMetres = getDouble(EventFields.ACCURACY)?.toFloat(),
            )
        } else {
            null
        },
        summary = getString(EventFields.SUMMARY).orEmpty(),
        details = details,
        acknowledgedByCaregiverId = getString(EventFields.ACKNOWLEDGED_BY),
        acknowledgedAtEpochMillis = getLong(EventFields.ACKNOWLEDGED_AT),
    )
}

fun HealthEvent.toMap(): Map<String, Any?> = mapOf(
    EventFields.ID to id,
    EventFields.PATIENT_ID to patientId,
    EventFields.PATIENT_NAME to patientName,
    EventFields.TYPE to type.storageKey,
    // Denormalised so the Cloud Function can pick a notification channel
    // without loading and interpreting the enum.
    EventFields.SEVERITY to type.severity.name.lowercase(),
    EventFields.STATUS to status.storageKey,
    EventFields.OCCURRED_AT to occurredAtEpochMillis,
    EventFields.LATITUDE to location?.latitude,
    EventFields.LONGITUDE to location?.longitude,
    EventFields.ACCURACY to location?.accuracyMetres?.toDouble(),
    EventFields.SUMMARY to summary,
    EventFields.DETAILS to details,
    EventFields.ACKNOWLEDGED_BY to acknowledgedByCaregiverId,
    EventFields.ACKNOWLEDGED_AT to acknowledgedAtEpochMillis,
)

// --- medications/{medicationId} ---------------------------------------------

fun DocumentSnapshot.toMedication(): Medication? {
    val patientId = getString(MedicationFields.PATIENT_ID) ?: return null
    val name = getString(MedicationFields.NAME) ?: return null

    val times = (get(MedicationFields.TIMES_OF_DAY) as? List<*>)
        ?.mapNotNull { (it as? Number)?.toInt() }
        .orEmpty()

    val days = (get(MedicationFields.DAYS_OF_WEEK) as? List<*>)
        ?.mapNotNull { (it as? Number)?.toInt() }
        ?.toSet()
        .orEmpty()

    return Medication(
        id = getString(MedicationFields.ID) ?: id,
        patientId = patientId,
        name = name,
        dosage = getString(MedicationFields.DOSAGE).orEmpty(),
        timesOfDayMinutes = times,
        schedule = MedicationSchedule.fromStorageKey(getString(MedicationFields.SCHEDULE)),
        daysOfWeek = days,
        notes = getString(MedicationFields.NOTES),
        isActive = getBoolean(MedicationFields.IS_ACTIVE) ?: true,
        createdAtEpochMillis = getLong(MedicationFields.CREATED_AT) ?: 0L,
    )
}

fun Medication.toMap(): Map<String, Any?> = mapOf(
    MedicationFields.ID to id,
    MedicationFields.PATIENT_ID to patientId,
    MedicationFields.NAME to name,
    MedicationFields.DOSAGE to dosage,
    MedicationFields.TIMES_OF_DAY to timesOfDayMinutes,
    MedicationFields.SCHEDULE to schedule.storageKey,
    MedicationFields.DAYS_OF_WEEK to daysOfWeek.toList(),
    MedicationFields.NOTES to notes,
    MedicationFields.IS_ACTIVE to isActive,
    MedicationFields.CREATED_AT to createdAtEpochMillis,
)

// --- doses/{doseId} ---------------------------------------------------------

fun DocumentSnapshot.toMedicationDose(): MedicationDose? {
    val medicationId = getString(DoseFields.MEDICATION_ID) ?: return null
    val patientId = getString(DoseFields.PATIENT_ID) ?: return null
    return MedicationDose(
        id = getString(DoseFields.ID) ?: id,
        medicationId = medicationId,
        patientId = patientId,
        medicationName = getString(DoseFields.MEDICATION_NAME).orEmpty(),
        dosage = getString(DoseFields.DOSAGE).orEmpty(),
        scheduledAtEpochMillis = getLong(DoseFields.SCHEDULED_AT) ?: 0L,
        status = DoseStatus.fromStorageKey(getString(DoseFields.STATUS)),
        respondedAtEpochMillis = getLong(DoseFields.RESPONDED_AT),
    )
}

fun MedicationDose.toMap(): Map<String, Any?> = mapOf(
    DoseFields.ID to id,
    DoseFields.MEDICATION_ID to medicationId,
    DoseFields.PATIENT_ID to patientId,
    DoseFields.MEDICATION_NAME to medicationName,
    DoseFields.DOSAGE to dosage,
    DoseFields.SCHEDULED_AT to scheduledAtEpochMillis,
    DoseFields.STATUS to status.storageKey,
    DoseFields.RESPONDED_AT to respondedAtEpochMillis,
)

// --- settings/{patientId} ---------------------------------------------------

fun DocumentSnapshot.toMonitoringSettings(fallbackPatientId: String): MonitoringSettings {
    val latitude = getDouble(SettingsFields.SAFE_ZONE_LAT)
    val longitude = getDouble(SettingsFields.SAFE_ZONE_LON)

    return MonitoringSettings(
        patientId = getString(SettingsFields.PATIENT_ID) ?: fallbackPatientId,
        fallDetectionEnabled = getBoolean(SettingsFields.FALL_ENABLED) ?: true,
        fallSensitivity = FallSensitivity.fromStorageKey(
            getString(SettingsFields.FALL_SENSITIVITY),
        ),
        inactivityDetectionEnabled = getBoolean(SettingsFields.INACTIVITY_ENABLED) ?: true,
        inactivityTimeoutMinutes = (getLong(SettingsFields.INACTIVITY_TIMEOUT)
            ?: MonitoringSettings.DEFAULT_INACTIVITY_MINUTES.toLong()).toInt(),
        geofenceEnabled = getBoolean(SettingsFields.GEOFENCE_ENABLED) ?: false,
        safeZone = if (latitude != null && longitude != null) {
            SafeZone(
                centre = GeoPoint(latitude, longitude),
                radiusMetres = (getLong(SettingsFields.SAFE_ZONE_RADIUS)
                    ?: SafeZone.DEFAULT_RADIUS_METRES.toLong()).toInt(),
                label = getString(SettingsFields.SAFE_ZONE_LABEL) ?: "Home",
            )
        } else {
            null
        },
        medicationRemindersEnabled = getBoolean(SettingsFields.MEDICATION_REMINDERS_ENABLED)
            ?: true,
        missedDoseGraceMinutes = (getLong(SettingsFields.MISSED_DOSE_GRACE)
            ?: MonitoringSettings.DEFAULT_MISSED_DOSE_GRACE_MINUTES.toLong()).toInt(),
        updatedAtEpochMillis = getLong(SettingsFields.UPDATED_AT) ?: 0L,
    )
}

fun MonitoringSettings.toMap(): Map<String, Any?> = mapOf(
    SettingsFields.PATIENT_ID to patientId,
    SettingsFields.FALL_ENABLED to fallDetectionEnabled,
    SettingsFields.FALL_SENSITIVITY to fallSensitivity.storageKey,
    SettingsFields.INACTIVITY_ENABLED to inactivityDetectionEnabled,
    SettingsFields.INACTIVITY_TIMEOUT to inactivityTimeoutMinutes,
    SettingsFields.GEOFENCE_ENABLED to geofenceEnabled,
    SettingsFields.SAFE_ZONE_LAT to safeZone?.centre?.latitude,
    SettingsFields.SAFE_ZONE_LON to safeZone?.centre?.longitude,
    SettingsFields.SAFE_ZONE_RADIUS to safeZone?.radiusMetres,
    SettingsFields.SAFE_ZONE_LABEL to safeZone?.label,
    SettingsFields.MEDICATION_REMINDERS_ENABLED to medicationRemindersEnabled,
    SettingsFields.MISSED_DOSE_GRACE to missedDoseGraceMinutes,
    SettingsFields.UPDATED_AT to updatedAtEpochMillis,
)
