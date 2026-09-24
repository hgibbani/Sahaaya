package com.sahaaya.domain.model

import com.sahaaya.core.demo.FeatureScope

/**
 * Everything about how closely this patient is watched.
 *
 * Stored on the patient's own document so the patient controls it, and readable
 * by a paired caregiver so they can see what the phone is actually doing.
 *
 * Every threshold here is a trade between missing a real event and crying wolf.
 * There is no universally right value, which is why they are settings rather
 * than constants: a patient in the early stage who still walks to the shops
 * needs a different safe-zone radius from one who does not leave the house.
 */
data class MonitoringSettings(
    val patientId: String,
    /**
     * Defaults follow [FeatureScope], so a newly registered patient is not
     * silently opted into a detector this build does not run. See
     * [FeatureScope.FALL_DETECTION_ACTIVE].
     */
    val fallDetectionEnabled: Boolean = FeatureScope.FALL_DETECTION_ACTIVE,
    val fallSensitivity: FallSensitivity = FallSensitivity.BALANCED,
    val inactivityDetectionEnabled: Boolean = FeatureScope.INACTIVITY_DETECTION_ACTIVE,
    val inactivityTimeoutMinutes: Int = DEFAULT_INACTIVITY_MINUTES,
    val geofenceEnabled: Boolean = false,
    val safeZone: SafeZone? = null,
    val medicationRemindersEnabled: Boolean = true,
    /** Minutes after a scheduled dose before it counts as missed. */
    val missedDoseGraceMinutes: Int = DEFAULT_MISSED_DOSE_GRACE_MINUTES,
    val updatedAtEpochMillis: Long = 0L,
) {
    val isMonitoringAnything: Boolean
        get() = fallDetectionEnabled ||
            inactivityDetectionEnabled ||
            (geofenceEnabled && safeZone != null)

    companion object {
        const val DEFAULT_INACTIVITY_MINUTES = 90
        const val MIN_INACTIVITY_MINUTES = 30
        const val MAX_INACTIVITY_MINUTES = 480

        const val DEFAULT_MISSED_DOSE_GRACE_MINUTES = 30
        const val MIN_MISSED_DOSE_GRACE_MINUTES = 10
        const val MAX_MISSED_DOSE_GRACE_MINUTES = 180

        /**
         * How long the patient has to cancel before an alert is sent.
         *
         * Thirty seconds: an older person who has just gone down needs time to
         * find the phone, read the screen and press a large button. Five seconds
         * - common in research prototypes - would report most stumbles before a
         * patient could answer. Nothing is sent while the prompt is open, so
         * the only cost of a longer window is a later alert for a patient who
         * cannot respond.
         */
        const val FALL_CONFIRMATION_SECONDS = 30
    }
}

/**
 * How eager the fall detector is.
 *
 * Scales the impact threshold. Lower threshold means more falls caught and more
 * false alarms; the point of exposing this is that a family can tune it after
 * living with it for a week, which is the only way to get it right.
 */
enum class FallSensitivity(
    val storageKey: String,
    val displayName: String,
    val description: String,
    /** Impact acceleration in m/s² that must be exceeded. 1g = 9.81. */
    val impactThresholdMetresPerSecondSquared: Float,
) {
    LOW(
        storageKey = "low",
        displayName = "Less sensitive",
        description = "Only hard falls. Fewest false alarms.",
        impactThresholdMetresPerSecondSquared = 29.4f, // 3.0g
    ),
    BALANCED(
        storageKey = "balanced",
        displayName = "Balanced",
        description = "Recommended for most people.",
        impactThresholdMetresPerSecondSquared = 24.5f, // 2.5g
    ),
    HIGH(
        storageKey = "high",
        displayName = "More sensitive",
        description = "Catches softer falls. Expect more false alarms.",
        impactThresholdMetresPerSecondSquared = 19.6f, // 2.0g
    ),
    ;

    companion object {
        fun fromStorageKey(key: String?): FallSensitivity =
            entries.firstOrNull { it.storageKey == key } ?: BALANCED
    }
}

/**
 * The area the patient is expected to stay inside.
 *
 * A circle, not a polygon: Android's Geofencing API takes a centre and a radius,
 * and a shape a family can describe as "home and the street outside" is easier
 * to reason about than one they drew on a map and cannot remember.
 */
data class SafeZone(
    val centre: GeoPoint,
    val radiusMetres: Int = DEFAULT_RADIUS_METRES,
    val label: String = "Home",
) {
    fun contains(point: GeoPoint): Boolean =
        centre.distanceMetresTo(point) <= radiusMetres

    companion object {
        const val DEFAULT_RADIUS_METRES = 200

        /**
         * Below about 100m, ordinary GPS drift indoors will report exits that
         * never happened. This floor exists to stop a family configuring
         * themselves a stream of false alarms.
         */
        const val MIN_RADIUS_METRES = 100
        const val MAX_RADIUS_METRES = 2_000
    }
}
