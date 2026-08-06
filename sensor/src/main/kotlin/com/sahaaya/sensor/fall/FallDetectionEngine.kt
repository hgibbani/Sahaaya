package com.sahaaya.sensor.fall

import com.sahaaya.domain.model.FallSensitivity
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A fall the engine believes it saw.
 */
data class FallCandidate(
    val impactMagnitude: Float,
    val orientationChangeDegrees: Float,
    val detectedAtEpochMillis: Long,
)

/**
 * The fall-detection state machine, with no Android in it.
 *
 * A fall has a shape that ordinary movement does not: the body accelerates
 * downward (acceleration briefly drops towards zero as the phone approaches
 * free fall), then decelerates hard against the floor, and then *stays* in a
 * new orientation because the person is now lying down.
 *
 * Checking only the impact spike is what gives naive detectors their false-alarm
 * rate - setting a phone down hard, or dropping it on a table, produces the same
 * spike. All three stages must occur, in order, inside a time window, and the
 * final stillness check is what separates "the phone was dropped" from "the
 * person fell and is now on the floor".
 *
 * Deliberately a plain class with a [feed] function rather than a SensorEventListener,
 * so the whole algorithm is unit-testable by pushing synthetic samples through it.
 */
class FallDetectionEngine(
    private var sensitivity: FallSensitivity = FallSensitivity.BALANCED,
) {

    private enum class Stage { WATCHING, FREE_FALL_SEEN, IMPACT_SEEN }

    private var stage = Stage.WATCHING
    private var freeFallAtMillis = 0L
    private var impactAtMillis = 0L
    private var peakImpact = 0f

    /** Gravity direction before the impact, used to measure the orientation change. */
    private var preImpactGravity: Triple<Float, Float, Float>? = null
    private var lastSample: Triple<Float, Float, Float>? = null

    /** Low-pass filtered gravity vector, tracked continuously. */
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var gravityInitialised = false

    private var lastConfirmedAtMillis = 0L

    fun updateSensitivity(value: FallSensitivity) {
        sensitivity = value
    }

    fun reset() {
        stage = Stage.WATCHING
        freeFallAtMillis = 0L
        impactAtMillis = 0L
        peakImpact = 0f
        preImpactGravity = null
    }

    /**
     * Feeds one accelerometer sample.
     *
     * @return a [FallCandidate] on the sample that completes a fall, otherwise null.
     */
    fun feed(x: Float, y: Float, z: Float, atMillis: Long): FallCandidate? {
        val magnitude = sqrt(x * x + y * y + z * z)
        lastSample = Triple(x, y, z)

        updateGravity(x, y, z)

        // A confirmed fall is followed by a quiet period. Without it, the
        // aftermath of one fall - being helped up, the phone being picked up -
        // reliably produces a second alert for the same incident.
        if (atMillis - lastConfirmedAtMillis < REFRACTORY_MILLIS) return null

        when (stage) {
            Stage.WATCHING -> {
                if (magnitude < FREE_FALL_THRESHOLD) {
                    stage = Stage.FREE_FALL_SEEN
                    freeFallAtMillis = atMillis
                    preImpactGravity = Triple(gravityX, gravityY, gravityZ)
                } else if (magnitude > sensitivity.impactThresholdMetresPerSecondSquared) {
                    // An impact with no preceding free fall. Real for a fall
                    // against a wall or from a chair, where the drop is too
                    // short to register - so it is accepted, but the
                    // orientation check still has to pass.
                    stage = Stage.IMPACT_SEEN
                    impactAtMillis = atMillis
                    peakImpact = magnitude
                    preImpactGravity = Triple(gravityX, gravityY, gravityZ)
                }
            }

            Stage.FREE_FALL_SEEN -> {
                when {
                    atMillis - freeFallAtMillis > FREE_FALL_TO_IMPACT_WINDOW_MILLIS -> reset()

                    magnitude > sensitivity.impactThresholdMetresPerSecondSquared -> {
                        stage = Stage.IMPACT_SEEN
                        impactAtMillis = atMillis
                        peakImpact = magnitude
                    }
                }
            }

            Stage.IMPACT_SEEN -> {
                peakImpact = maxOf(peakImpact, magnitude)
                val sinceImpact = atMillis - impactAtMillis

                if (sinceImpact > POST_IMPACT_WINDOW_MILLIS) {
                    reset()
                    return null
                }

                // Wait for the body to settle before judging orientation.
                if (sinceImpact < POST_IMPACT_SETTLE_MILLIS) return null

                val isStill = abs(magnitude - EARTH_GRAVITY) < STILLNESS_TOLERANCE
                if (!isStill) return null

                val orientationChange = orientationChangeDegrees()
                if (orientationChange < MIN_ORIENTATION_CHANGE_DEGREES) {
                    // Hard knock, but the phone is lying the same way up as
                    // before. A dropped phone, not a fallen person.
                    reset()
                    return null
                }

                val candidate = FallCandidate(
                    impactMagnitude = peakImpact,
                    orientationChangeDegrees = orientationChange,
                    detectedAtEpochMillis = atMillis,
                )
                lastConfirmedAtMillis = atMillis
                reset()
                return candidate
            }
        }

        return null
    }

    /**
     * Tracks the gravity direction with a low-pass filter.
     *
     * The accelerometer reports gravity plus movement; the filter recovers the
     * gravity component, which is what "which way is the phone facing" means.
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

    /** Angle between the gravity vector before the impact and now, in degrees. */
    private fun orientationChangeDegrees(): Float {
        val before = preImpactGravity ?: return 0f
        val (bx, by, bz) = before

        val beforeMagnitude = sqrt(bx * bx + by * by + bz * bz)
        val nowMagnitude = sqrt(
            gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ,
        )
        if (beforeMagnitude == 0f || nowMagnitude == 0f) return 0f

        val dot = bx * gravityX + by * gravityY + bz * gravityZ
        val cosine = (dot / (beforeMagnitude * nowMagnitude)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cosine).toDouble()).toFloat()
    }

    companion object {
        const val EARTH_GRAVITY = 9.81f

        /**
         * Below this the phone is close to free fall. Not zero: a real fall is
         * never a clean drop, the body tumbles and the limbs move.
         */
        const val FREE_FALL_THRESHOLD = 3.5f

        /** A fall from standing reaches the floor well inside this. */
        const val FREE_FALL_TO_IMPACT_WINDOW_MILLIS = 800L

        /** Give the body time to come to rest before judging orientation. */
        const val POST_IMPACT_SETTLE_MILLIS = 700L

        /** If nothing has settled by now, it was not a fall. */
        const val POST_IMPACT_WINDOW_MILLIS = 3_000L

        /** How close to 1g counts as motionless. */
        const val STILLNESS_TOLERANCE = 2.0f

        /**
         * Lying down is roughly a 60-90 degree change from standing or sitting.
         * 45 leaves room for a phone in a pocket at an odd angle.
         */
        const val MIN_ORIENTATION_CHANGE_DEGREES = 45f

        /** Quiet period after a confirmed fall, so one incident is one alert. */
        const val REFRACTORY_MILLIS = 30_000L

        private const val GRAVITY_FILTER_ALPHA = 0.8f
    }
}
