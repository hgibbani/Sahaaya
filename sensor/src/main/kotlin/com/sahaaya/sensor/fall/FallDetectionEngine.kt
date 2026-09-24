package com.sahaaya.sensor.fall

import com.sahaaya.domain.model.FallSensitivity
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A possible fall the engine believes it saw.
 *
 * "Possible" on purpose. A smartphone accelerometer cannot tell a person falling
 * from a phone falling on its own - both produce free fall, an impact and a new
 * resting orientation - so this is only ever a *candidate*, and the patient is
 * asked before anyone is told.
 */
data class FallCandidate(
    val impactMagnitude: Float,
    val orientationChangeDegrees: Float,
    val detectedAtEpochMillis: Long,
    /** True when the engine saw a free-fall dip before the impact. */
    val hadFreeFall: Boolean = true,
    /** Set only by the developer "Simulate Fall Event" path, never by the sensor. */
    val simulated: Boolean = false,
)

/**
 * Where the detector is in its reasoning about the current movement.
 *
 * ```
 * NORMAL ──impact──▶ POSSIBLE_FALL ──settling──▶ CONFIRMING ──still + turned──▶ FALL_CONFIRMED
 *                        │                          │
 *                        └──── continued activity ──┴──▶ CANCELLED ──▶ NORMAL
 * ```
 *
 * FALL_CONFIRMED means "the sensor pattern matched", not "a person fell" - the
 * patient confirmation screen decides what happens next.
 */
enum class FallDetectionState { NORMAL, POSSIBLE_FALL, CONFIRMING, FALL_CONFIRMED, CANCELLED }

/**
 * Every tunable number in one place, so thresholds can be adjusted during field
 * testing without touching the algorithm. Defaults are conservative starting
 * points, not clinically validated values.
 */
data class FallDetectionConfig(
    /** Magnitude (m/s²) below which the phone is treated as near free fall. */
    val freeFallThreshold: Float = 3.5f,
    /** Max gap between the free-fall dip and the impact. */
    val freeFallToImpactWindowMillis: Long = 800L,
    /**
     * Whether an impact must be preceded by a free-fall dip.
     *
     * `true` is the default because it is the strongest single filter against
     * everyday knocks: putting a phone down, bumping a table, a hard step. Its
     * cost is missing falls with no measurable drop, such as sliding off a chair.
     */
    val requireFreeFall: Boolean = true,
    /** Time allowed for the body to come to rest before it is judged. */
    val settleMillis: Long = 700L,
    /** If the pattern has not resolved by now, it was not a fall. */
    val confirmationWindowMillis: Long = 3_000L,
    /** How close to 1 g counts as motionless. */
    val stillnessTolerance: Float = 2.0f,
    /**
     * How long the phone must stay motionless before the pattern is accepted.
     * One quiet sample is not "lying still"; half a second of them is.
     */
    val minStillMillis: Long = 500L,
    /**
     * Samples after settling that deviate from 1 g by more than this count as
     * activity. Sustained activity cancels the candidate: a person who keeps
     * walking after a stumble has not fallen.
     */
    val activityTolerance: Float = 3.0f,
    /** Fraction of post-settle samples that must be active to cancel. */
    val activityCancelRatio: Float = 0.4f,
    /** Minimum samples before the activity ratio is trusted. */
    val activityMinSamples: Int = 10,
    /** Lying down after standing is roughly 60-90 degrees. */
    val minOrientationChangeDegrees: Float = 45f,
    /** Quiet period after a match, so one incident produces one candidate. */
    val refractoryMillis: Long = 30_000L,
)

/**
 * Multi-stage possible-fall detector, with no Android in it.
 *
 * Deliberately not "acceleration above a threshold". A single spike is what
 * walking, running, shaking and setting the phone down all produce. A fall is
 * recognised only when several signals line up, in order, inside time windows:
 *
 * 1. **Free-fall dip** - magnitude drops towards zero as the body and phone drop.
 * 2. **Impact** - magnitude exceeds the sensitivity threshold. This moves the
 *    state to [FallDetectionState.POSSIBLE_FALL] and nothing more.
 * 3. **Settle** - the body is given time to come to rest.
 * 4. **Confirm** - the phone must be still *and* lying at a new angle. Continued
 *    activity cancels; an unchanged orientation cancels.
 * 5. **Refractory** - the aftermath of one incident cannot trigger a second.
 *
 * The approach follows the peak-detection → activity-test → feature-check
 * structure common in smartphone fall-detection research, adapted to a single
 * accelerometer. It is a false-positive *filter*, not a medically validated
 * classifier.
 */
class FallDetectionEngine(
    private var sensitivity: FallSensitivity = FallSensitivity.BALANCED,
    private val config: FallDetectionConfig = FallDetectionConfig(),
) {

    /** Current stage. Exposed for tests and diagnostics. */
    var state: FallDetectionState = FallDetectionState.NORMAL
        private set

    private var freeFallAtMillis = -1L
    private var impactAtMillis = 0L
    private var peakImpact = 0f
    private var sawFreeFall = false

    private var postSettleSamples = 0
    private var postSettleActiveSamples = 0
    private var stillSinceMillis = -1L

    /** Gravity direction before the drop, used to measure the orientation change. */
    private var preImpactGravity: Triple<Float, Float, Float>? = null

    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var gravityInitialised = false

    private var lastConfirmedAtMillis = Long.MIN_VALUE / 2

    fun updateSensitivity(value: FallSensitivity) {
        sensitivity = value
    }

    fun reset() {
        state = FallDetectionState.NORMAL
        freeFallAtMillis = -1L
        impactAtMillis = 0L
        peakImpact = 0f
        sawFreeFall = false
        postSettleSamples = 0
        postSettleActiveSamples = 0
        stillSinceMillis = -1L
        preImpactGravity = null
    }

    private fun cancel() {
        reset()
        state = FallDetectionState.CANCELLED
    }

    /**
     * Feeds one accelerometer sample.
     *
     * @return a [FallCandidate] on the sample that completes the pattern,
     *   otherwise null. At most one candidate per refractory period.
     */
    fun feed(x: Float, y: Float, z: Float, atMillis: Long): FallCandidate? {
        val magnitude = sqrt(x * x + y * y + z * z)
        val threshold = sensitivity.impactThresholdMetresPerSecondSquared

        // CANCELLED and FALL_CONFIRMED are momentary: report them for one
        // sample, then go back to watching.
        if (state == FallDetectionState.CANCELLED || state == FallDetectionState.FALL_CONFIRMED) {
            state = FallDetectionState.NORMAL
        }

        if (atMillis - lastConfirmedAtMillis < config.refractoryMillis) {
            updateGravity(x, y, z)
            return null
        }

        when (state) {
            FallDetectionState.NORMAL -> {
                if (magnitude < config.freeFallThreshold) {
                    // Remember the dip and freeze the pre-fall orientation now,
                    // before the tumble drags the filtered gravity around.
                    if (freeFallAtMillis < 0 || atMillis - freeFallAtMillis > config.freeFallToImpactWindowMillis) {
                        preImpactGravity = Triple(gravityX, gravityY, gravityZ)
                    }
                    freeFallAtMillis = atMillis
                } else if (magnitude > threshold) {
                    val recentDip = freeFallAtMillis >= 0 &&
                        atMillis - freeFallAtMillis <= config.freeFallToImpactWindowMillis
                    if (recentDip || !config.requireFreeFall) {
                        if (!recentDip) preImpactGravity = Triple(gravityX, gravityY, gravityZ)
                        sawFreeFall = recentDip
                        state = FallDetectionState.POSSIBLE_FALL
                        impactAtMillis = atMillis
                        peakImpact = magnitude
                    }
                    // A spike with no dip is ignored outright when a dip is
                    // required: that is the knock / step / set-down case.
                } else if (freeFallAtMillis >= 0 &&
                    atMillis - freeFallAtMillis > config.freeFallToImpactWindowMillis
                ) {
                    // A dip that never led to an impact (tossing the phone
                    // onto a sofa, a lift starting) is forgotten.
                    freeFallAtMillis = -1L
                    preImpactGravity = null
                }
            }

            FallDetectionState.POSSIBLE_FALL,
            FallDetectionState.CONFIRMING,
            -> {
                peakImpact = maxOf(peakImpact, magnitude)
                val sinceImpact = atMillis - impactAtMillis

                if (sinceImpact > config.confirmationWindowMillis) {
                    // Never came to rest: moving about, not lying on the floor.
                    cancel()
                    updateGravity(x, y, z)
                    return null
                }

                if (sinceImpact < config.settleMillis) {
                    updateGravity(x, y, z)
                    return null
                }

                state = FallDetectionState.CONFIRMING
                postSettleSamples++
                val deviation = abs(magnitude - EARTH_GRAVITY)
                if (deviation > config.activityTolerance) postSettleActiveSamples++

                if (postSettleSamples >= config.activityMinSamples &&
                    postSettleActiveSamples.toFloat() / postSettleSamples >= config.activityCancelRatio
                ) {
                    // Spike followed by sustained movement: walking on, running,
                    // shaking. Not a fall.
                    cancel()
                    updateGravity(x, y, z)
                    return null
                }

                updateGravity(x, y, z)
                if (deviation >= config.stillnessTolerance) {
                    stillSinceMillis = -1L
                    return null
                }
                if (stillSinceMillis < 0) stillSinceMillis = atMillis
                if (atMillis - stillSinceMillis < config.minStillMillis) return null

                val orientationChange = orientationChangeDegrees()
                if (orientationChange < config.minOrientationChangeDegrees) {
                    // Hard knock, but lying the same way up as before - a phone
                    // put down or dropped flat, not a person now lying down.
                    cancel()
                    return null
                }

                val candidate = FallCandidate(
                    impactMagnitude = peakImpact,
                    orientationChangeDegrees = orientationChange,
                    detectedAtEpochMillis = atMillis,
                    hadFreeFall = sawFreeFall,
                )
                lastConfirmedAtMillis = atMillis
                reset()
                state = FallDetectionState.FALL_CONFIRMED
                return candidate
            }

            FallDetectionState.FALL_CONFIRMED,
            FallDetectionState.CANCELLED,
            -> Unit
        }

        updateGravity(x, y, z)
        return null
    }

    /**
     * Low-pass filtered gravity. The accelerometer reports gravity plus motion;
     * the slow component is which way the phone is facing.
     */
    private fun updateGravity(x: Float, y: Float, z: Float) {
        if (!gravityInitialised) {
            gravityX = x
            gravityY = y
            gravityZ = z
            gravityInitialised = true
            return
        }
        gravityX = GRAVITY_FILTER_ALPHA * gravityX + (1 - GRAVITY_FILTER_ALPHA) * x
        gravityY = GRAVITY_FILTER_ALPHA * gravityY + (1 - GRAVITY_FILTER_ALPHA) * y
        gravityZ = GRAVITY_FILTER_ALPHA * gravityZ + (1 - GRAVITY_FILTER_ALPHA) * z
    }

    /** Angle between the pre-fall gravity vector and now, in degrees. */
    private fun orientationChangeDegrees(): Float {
        val (bx, by, bz) = preImpactGravity ?: return 0f
        val beforeMagnitude = sqrt(bx * bx + by * by + bz * bz)
        val nowMagnitude = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ)
        if (beforeMagnitude == 0f || nowMagnitude == 0f) return 0f
        val dot = bx * gravityX + by * gravityY + bz * gravityZ
        val cosine = (dot / (beforeMagnitude * nowMagnitude)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cosine).toDouble()).toFloat()
    }

    companion object {
        const val EARTH_GRAVITY = 9.81f
        private const val GRAVITY_FILTER_ALPHA = 0.8f

        // Kept for callers and tests that referenced the old constants.
        const val FREE_FALL_THRESHOLD = 3.5f
        const val REFRACTORY_MILLIS = 30_000L
    }
}
