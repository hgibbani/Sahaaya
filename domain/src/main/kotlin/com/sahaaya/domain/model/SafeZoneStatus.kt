package com.sahaaya.domain.model

/**
 * Where the patient is relative to their safe zone.
 *
 * [UNKNOWN] is a real state, not a placeholder for "not loaded". It is what the
 * caregiver sees when no usable fix has arrived yet - GPS off, permission
 * refused, or indoors with nothing but a 400 m cell-tower estimate. Collapsing
 * it into [INSIDE] would be the dangerous choice: a caregiver would read a green
 * badge as "I checked, they are home" when nothing was actually checked.
 */
enum class SafeZoneStatus(val storageKey: String, val displayName: String) {
    INSIDE("inside", "Inside safe zone"),
    OUTSIDE("outside", "Outside safe zone"),
    UNKNOWN("unknown", "Location unavailable"),
    ;

    companion object {
        fun fromStorageKey(key: String?): SafeZoneStatus =
            entries.firstOrNull { it.storageKey == key } ?: UNKNOWN
    }
}

/**
 * The patient's most recent position, as the caregiver sees it.
 *
 * Stored on `patients/{uid}` rather than in a new collection: the security rules
 * there already say exactly what is wanted - the patient writes their own
 * document, and only an actively paired caregiver may read it.
 */
data class PatientLocation(
    val point: GeoPoint,
    val recordedAtEpochMillis: Long,
    val status: SafeZoneStatus,
    /**
     * Metres from the boundary. Negative inside, positive outside, null when the
     * status is [SafeZoneStatus.UNKNOWN] or no zone is configured.
     *
     * Signed rather than two fields because "82 m inside" and "143 m outside"
     * are the same measurement, and a caregiver reads the sign instantly.
     */
    val metresFromBoundary: Int? = null,
) {
    val isStale: Boolean
        get() = System.currentTimeMillis() - recordedAtEpochMillis > STALE_AFTER_MILLIS

    companion object {
        /**
         * After this long with no fix, the reading is presented as last-known
         * rather than current. Six minutes is two missed update intervals plus
         * slack, so a single failed fix does not make the card look broken.
         */
        const val STALE_AFTER_MILLIS = 6 * 60 * 1000L
    }
}
