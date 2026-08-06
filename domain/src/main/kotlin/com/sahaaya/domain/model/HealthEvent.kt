package com.sahaaya.domain.model

/**
 * Something that happened to a patient which a caregiver needs to know about.
 *
 * One model for every detector. A fall, a geofence exit, prolonged inactivity, a
 * pressed SOS button and a missed dose all land in the same `events` collection
 * and the same caregiver timeline, because from the caregiver's side they are
 * the same question: *is the person I look after alright?*
 *
 * Events are append-only. [status] moves forward and the document is never
 * deleted, so the record of what happened and who responded survives.
 */
data class HealthEvent(
    val id: String,
    val patientId: String,
    val patientName: String,
    val type: EventType,
    val status: EventStatus = EventStatus.NEW,
    val occurredAtEpochMillis: Long,
    val location: GeoPoint? = null,
    /** Human-readable one-liner, generated at creation so history stays stable. */
    val summary: String = "",
    /** Detector-specific detail, e.g. impact force or minutes inactive. */
    val details: Map<String, String> = emptyMap(),
    val acknowledgedByCaregiverId: String? = null,
    val acknowledgedAtEpochMillis: Long? = null,
) {
    val isUnresolved: Boolean get() = status == EventStatus.NEW

    val isCritical: Boolean get() = type.severity == EventSeverity.CRITICAL
}

/**
 * What kind of event this is.
 *
 * [severity] decides the notification channel and the colour on the timeline.
 * It is a property of the type rather than a per-event field on purpose: a fall
 * is always urgent, and letting a detector downgrade one would eventually let a
 * bug silence it.
 */
enum class EventType(
    val storageKey: String,
    val displayName: String,
    val severity: EventSeverity,
) {
    FALL("fall", "Fall detected", EventSeverity.CRITICAL),
    SOS("sos", "Emergency SOS", EventSeverity.CRITICAL),
    GEOFENCE_EXIT("geofence_exit", "Left safe zone", EventSeverity.CRITICAL),
    INACTIVITY("inactivity", "No movement", EventSeverity.WARNING),
    MEDICATION_MISSED("medication_missed", "Dose missed", EventSeverity.WARNING),
    ;

    companion object {
        fun fromStorageKey(key: String?): EventType? =
            entries.firstOrNull { it.storageKey == key }
    }
}

/**
 * How loudly an event should arrive.
 *
 * [CRITICAL] events go to the high-importance channel that bypasses Do Not
 * Disturb. [WARNING] events do not. A caregiver must be able to sleep through a
 * missed dose and still be woken by a fall.
 */
enum class EventSeverity { CRITICAL, WARNING }

enum class EventStatus(val storageKey: String, val displayName: String) {
    /** Raised, nobody has looked at it. */
    NEW("new", "New"),

    /** A caregiver has seen it and taken responsibility. */
    ACKNOWLEDGED("acknowledged", "Acknowledged"),

    /** Raised, then withdrawn during the confirmation countdown by the patient. */
    CANCELLED("cancelled", "Cancelled by patient"),
    ;

    companion object {
        fun fromStorageKey(key: String?): EventStatus =
            entries.firstOrNull { it.storageKey == key } ?: NEW
    }
}

/** A latitude/longitude pair. Kept in :domain so no layer needs Android's Location. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float? = null,
) {
    /**
     * Great-circle distance to [other] in metres.
     *
     * Haversine on a spherical earth. Accurate to well under a metre at the
     * scale of a safe zone, and unlike Android's `Location.distanceTo` it works
     * in :domain, which has no Android dependency.
     */
    fun distanceMetresTo(other: GeoPoint): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(latitude)) *
            kotlin.math.cos(Math.toRadians(other.latitude)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadius * c
    }
}
