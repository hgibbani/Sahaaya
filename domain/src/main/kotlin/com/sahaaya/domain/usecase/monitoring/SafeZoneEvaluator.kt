package com.sahaaya.domain.usecase.monitoring

import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.model.SafeZoneStatus
import kotlin.math.roundToInt

/**
 * Decides whether the patient is inside or outside their safe zone, and whether
 * that is a change worth waking a caregiver for.
 *
 * A plain class with no Android in it, so the whole decision - including the
 * awkward cases that only show up near the boundary - is unit testable by
 * pushing synthetic fixes through [evaluate].
 *
 * ## Why this is not just `distance > radius`
 *
 * A consumer GPS fix is a guess with an error bar. Standing still 5 m outside a
 * boundary, a phone will report positions scattered either side of it, and the
 * naive comparison flips state on every one. The caregiver's phone would then
 * alarm repeatedly while the patient has not moved - which trains them to
 * ignore it, and a safe-zone alert that gets ignored is worse than none.
 *
 * Three defences, in order:
 *
 * 1. **Accuracy gate.** A fix whose own accuracy radius is larger than
 *    [MAX_ACCURACY_METRES] cannot answer the question at all - a 400 m estimate
 *    against a 200 m zone is noise - so it is discarded rather than acted on.
 * 2. **Hysteresis.** Crossing out requires being [HYSTERESIS_METRES] *beyond*
 *    the boundary; crossing back in requires being that far inside it. The band
 *    between the two thresholds holds whatever state it already had, so jitter
 *    across the line changes nothing.
 * 3. **Confirmation.** A candidate change must survive
 *    [CONSECUTIVE_FIXES_TO_CHANGE] fixes in a row before it is accepted, so one
 *    wild outlier cannot raise an alert on its own.
 */
class SafeZoneEvaluator {

    /** What the caller should do with a fix. */
    data class Decision(
        val status: SafeZoneStatus,
        /** Signed metres from the boundary: negative inside, positive outside. */
        val metresFromBoundary: Int?,
        /**
         * True only on a confirmed INSIDE -> OUTSIDE transition. This is the one
         * moment an alert is raised; every other combination is silent.
         */
        val raiseExitAlert: Boolean,
        /** True on a confirmed OUTSIDE -> INSIDE transition. */
        val returnedInside: Boolean,
        /** Whether the fix was usable at all. */
        val accepted: Boolean,
    )

    private var current: SafeZoneStatus = SafeZoneStatus.UNKNOWN
    private var pending: SafeZoneStatus? = null
    private var pendingCount = 0

    /** Restores state after a process restart so a reboot cannot re-alert. */
    fun restore(status: SafeZoneStatus) {
        current = status
        pending = null
        pendingCount = 0
    }

    fun currentStatus(): SafeZoneStatus = current

    fun evaluate(fix: GeoPoint, zone: SafeZone): Decision {
        val accuracy = fix.accuracyMetres
        if (accuracy != null && accuracy > MAX_ACCURACY_METRES) {
            // Deliberately does not change state or clear the pending run: a
            // useless fix is an absence of information, not evidence of
            // anything. The previous status stands.
            return Decision(
                status = current,
                metresFromBoundary = null,
                raiseExitAlert = false,
                returnedInside = false,
                accepted = false,
            )
        }

        val distance = zone.centre.distanceMetresTo(fix)
        val signed = (distance - zone.radiusMetres).roundToInt()

        val observed = when {
            distance > zone.radiusMetres + HYSTERESIS_METRES -> SafeZoneStatus.OUTSIDE
            distance < zone.radiusMetres - HYSTERESIS_METRES -> SafeZoneStatus.INSIDE
            // Inside the hysteresis band: not a reading either way. Keep what we
            // have, and abandon any run that was building.
            else -> current
        }

        if (observed == current) {
            pending = null
            pendingCount = 0
            return Decision(
                status = current,
                metresFromBoundary = signed,
                raiseExitAlert = false,
                returnedInside = false,
                accepted = true,
            )
        }

        // A different reading. Count it, and only act once it has repeated.
        if (observed == pending) {
            pendingCount++
        } else {
            pending = observed
            pendingCount = 1
        }

        if (pendingCount < CONSECUTIVE_FIXES_TO_CHANGE) {
            return Decision(
                status = current,
                metresFromBoundary = signed,
                raiseExitAlert = false,
                returnedInside = false,
                accepted = true,
            )
        }

        val previous = current
        current = observed
        pending = null
        pendingCount = 0

        return Decision(
            status = current,
            metresFromBoundary = signed,
            // Only INSIDE -> OUTSIDE alerts. UNKNOWN -> OUTSIDE does not: that
            // is the first fix after a restart or after GPS came back, and the
            // patient may have been outside the whole time. Alerting there would
            // fire an "they have just left" alarm for something that already
            // happened, possibly hours ago.
            raiseExitAlert = previous == SafeZoneStatus.INSIDE &&
                current == SafeZoneStatus.OUTSIDE,
            returnedInside = previous == SafeZoneStatus.OUTSIDE &&
                current == SafeZoneStatus.INSIDE,
            accepted = true,
        )
    }

    companion object {
        /**
         * Fixes worse than this are unusable against the smallest permitted
         * zone ([SafeZone.MIN_RADIUS_METRES] is 100 m).
         */
        const val MAX_ACCURACY_METRES = 50f

        /** Half-width of the dead band around the boundary. */
        const val HYSTERESIS_METRES = 25.0

        /** Fixes a new reading must hold for before the state actually changes. */
        const val CONSECUTIVE_FIXES_TO_CHANGE = 2
    }
}
