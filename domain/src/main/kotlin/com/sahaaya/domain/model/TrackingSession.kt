package com.sahaaya.domain.model

/**
 * Whether a caregiver is currently watching this patient move.
 *
 * Deliberately a caregiver-owned decision, not a patient-owned one. Sahaaya's
 * other monitoring settings belong to the patient - they choose their own fall
 * sensitivity and reminder times - but live tracking exists for the case where
 * a family has agreed that somebody with dementia should not be able to switch
 * off the thing that finds them. The patient is told clearly that it is on and
 * who turned it on; they simply cannot turn it off from this phone.
 *
 * That is a real trade against the patient's autonomy, so it is made explicit
 * in the data model and enforced in the Firestore rules rather than by hiding a
 * button: a patient with a rooted phone and a hostile client must hit the same
 * wall as one tapping the UI.
 */
data class TrackingState(
    val active: Boolean = false,
    /** The session route points are filed under. Null when never started. */
    val sessionId: String? = null,
    val startedAtEpochMillis: Long? = null,
    /** Caregiver uid who started it, so the patient can be told who is watching. */
    val startedByCaregiverId: String? = null,
    val stoppedAtEpochMillis: Long? = null,
    val stoppedByCaregiverId: String? = null,
) {
    /** A session we can file route points against. */
    val activeSessionId: String?
        get() = sessionId.takeIf { active && !it.isNullOrBlank() }

    companion object {
        val INACTIVE = TrackingState()
    }
}

/**
 * One tracking session: a continuous stretch of caregiver-requested watching.
 *
 * A session rather than a single flag because the route only means something
 * bounded by one. "Where has Asha been?" answered over all time would draw
 * every street she has ever walked down; answered over this session it draws
 * the walk she is on now, which is the question a worried caregiver is asking.
 */
data class TrackingSession(
    val id: String,
    val patientId: String,
    val caregiverId: String,
    val startedAtEpochMillis: Long,
    val stoppedAtEpochMillis: Long? = null,
    val active: Boolean = true,
)

/**
 * A single point on the route.
 *
 * Thinner than [PatientLocation] on purpose. The latest position carries a
 * safe-zone verdict because a caregiver reads it as a status; a trail point is
 * only ever drawn as a dot on a line, and storing a status per point would
 * invite reading a stale verdict as a current one.
 */
data class TrackPoint(
    val point: GeoPoint,
    val recordedAtEpochMillis: Long,
) {
    companion object {
        /**
         * How far the patient must move before a new point is filed.
         *
         * Without this, a phone sitting on a kitchen table files a point every
         * update interval for hours: the route becomes a dense blob at one
         * address, the read cost grows without bound, and a caregiver scrolling
         * back cannot see the walk for the standing still. Twenty metres is
         * comfortably outside ordinary GPS jitter while still catching a slow
         * walk down a street.
         */
        const val MIN_DISTANCE_METRES = 20.0

        /**
         * The newest points a caregiver's map draws.
         *
         * A cap, not a window: it bounds the query and the polyline, and at the
         * default update interval it covers several hours of walking, which is
         * far longer than any session this feature is meant for.
         */
        const val ROUTE_LIMIT = 200
    }
}
