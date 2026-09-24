package com.sahaaya.sensor.fall

import com.sahaaya.domain.model.FallSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The algorithm under test, driven by synthetic accelerometer traces.
 *
 * This is the reason [FallDetectionEngine] is a plain class rather than a
 * SensorEventListener: a fall can be replayed deterministically here, whereas
 * testing it on a device means repeatedly dropping a phone and hoping.
 */
class FallDetectionEngineTest {

    private lateinit var engine: FallDetectionEngine
    private var clock = 0L

    private companion object {
        const val G = 9.81f
        const val SAMPLE_INTERVAL_MILLIS = 20L
    }

    @Before
    fun setUp() {
        engine = FallDetectionEngine(FallSensitivity.BALANCED)
        clock = 100_000L
    }

    /** Feeds [count] samples of the given vector, advancing the clock. */
    private fun feed(x: Float, y: Float, z: Float, count: Int): FallCandidate? {
        var result: FallCandidate? = null
        repeat(count) {
            clock += SAMPLE_INTERVAL_MILLIS
            engine.feed(x, y, z, clock)?.let { result = it }
        }
        return result
    }

    /** Phone upright in a pocket: gravity along -Y. */
    private fun standStill(seconds: Int) = feed(0f, -G, 0f, (seconds * 1000 / SAMPLE_INTERVAL_MILLIS).toInt())

    /** Phone flat on its back: gravity along -Z. A person lying down. */
    private fun lieStill(seconds: Int) = feed(0f, 0f, -G, (seconds * 1000 / SAMPLE_INTERVAL_MILLIS).toInt())

    @Test
    fun `a full fall is detected`() {
        standStill(3)
        // Free fall.
        feed(0.4f, 0.6f, 0.3f, 8)
        // Impact.
        feed(2f, -30f, 4f, 3)
        // Lying still afterwards, in a new orientation.
        val candidate = lieStill(2)

        assertNotNull("a standing-to-lying fall should be detected", candidate)
        assertTrue(candidate!!.impactMagnitude > 25f)
        assertTrue(
            "expected a large orientation change, got ${candidate.orientationChangeDegrees}",
            candidate.orientationChangeDegrees > 45f,
        )
    }

    @Test
    fun `sitting still never triggers`() {
        assertNull(standStill(30))
    }

    @Test
    fun `walking never triggers`() {
        // Ordinary gait: roughly 1g with a rhythmic 3 m per s squared wobble.
        var result: FallCandidate? = null
        repeat(500) { i ->
            clock += SAMPLE_INTERVAL_MILLIS
            val wobble = if (i % 10 < 5) 3f else -3f
            engine.feed(wobble, -G + wobble, wobble * 0.5f, clock)?.let { result = it }
        }
        assertNull("walking should not look like a fall", result)
    }

    @Test
    fun `a dropped phone that lands the same way up does not trigger`() {
        standStill(3)
        feed(0.4f, 0.6f, 0.3f, 8)
        feed(2f, -30f, 4f, 3)
        // Comes to rest in the SAME orientation - a phone slipping from a hand
        // onto a table, not a person going down.
        val candidate = standStill(2)

        assertNull("no orientation change means no fall", candidate)
    }

    @Test
    fun `an impact followed by continued movement does not trigger`() {
        standStill(3)
        feed(0.4f, 0.6f, 0.3f, 8)
        feed(2f, -30f, 4f, 3)
        // Still moving about afterwards: the person caught themselves.
        var result: FallCandidate? = null
        repeat(100) { i ->
            clock += SAMPLE_INTERVAL_MILLIS
            val jostle = if (i % 6 < 3) 7f else -7f
            engine.feed(jostle, -G + jostle, jostle, clock)?.let { result = it }
        }
        assertNull("someone still moving has not fallen and stayed down", result)
    }

    @Test
    fun `free fall with no impact does not trigger`() {
        standStill(2)
        feed(0.4f, 0.6f, 0.3f, 8)
        // Caught mid-air, returns to normal without an impact spike.
        val candidate = standStill(3)
        assertNull(candidate)
    }

    @Test
    fun `a hard fall against a wall is detected without a free-fall phase when configured`() {
        // Free fall is required by default (it is the main filter against
        // setting the phone down hard). Relaxing it catches falls with no drop.
        engine = FallDetectionEngine(
            FallSensitivity.BALANCED,
            FallDetectionConfig(requireFreeFall = false),
        )
        standStill(3)
        // Straight to impact - a fall from a chair is too short to free-fall.
        feed(3f, -32f, 5f, 3)
        val candidate = lieStill(2)

        assertNotNull("a short fall should still be caught", candidate)
    }

    @Test
    fun `a soft fall is missed at low sensitivity and caught at high`() {
        // ~2.2g impact: above the HIGH threshold (2.0g), below LOW (3.0g).
        fun runSoftFall(sensitivity: FallSensitivity): FallCandidate? {
            engine = FallDetectionEngine(sensitivity)
            clock = 100_000L
            standStill(3)
            feed(0.4f, 0.6f, 0.3f, 8)
            feed(1f, -21.5f, 2f, 3)
            return lieStill(2)
        }

        assertNull("low sensitivity should ignore a soft fall", runSoftFall(FallSensitivity.LOW))
        assertNotNull(
            "high sensitivity should catch a soft fall",
            runSoftFall(FallSensitivity.HIGH),
        )
    }

    @Test
    fun `one incident produces one alert`() {
        standStill(3)
        feed(0.4f, 0.6f, 0.3f, 8)
        feed(2f, -30f, 4f, 3)
        val first = lieStill(2)
        assertNotNull(first)

        // Being helped up moments later must not raise a second alert for the
        // same fall.
        feed(0.4f, 0.6f, 0.3f, 8)
        feed(2f, -30f, 4f, 3)
        val second = lieStill(2)
        assertNull("the refractory period should suppress a duplicate", second)
    }

    @Test
    fun `reset clears a partial detection`() {
        standStill(2)
        feed(0.4f, 0.6f, 0.3f, 8)
        engine.reset()
        // The impact now arrives with no remembered free-fall stage, and the
        // phone stays upright, so nothing should fire.
        feed(2f, -30f, 4f, 3)
        assertNull(standStill(2))
    }

    // --- False-positive protection -----------------------------------------

    @Test
    fun `an impact with no free fall is ignored by default`() {
        standStill(3)
        feed(3f, -32f, 5f, 3)
        assertEquals(FallDetectionState.NORMAL, engine.state)
        assertNull(lieStill(2))
    }

    @Test
    fun `small phone movements never trigger`() {
        var result: FallCandidate? = null
        repeat(1_000) { i ->
            clock += SAMPLE_INTERVAL_MILLIS
            val jiggle = if (i % 4 < 2) 1f else -1f
            engine.feed(jiggle, -G + jiggle, jiggle, clock)?.let { result = it }
        }
        assertNull(result)
    }

    @Test
    fun `placing the phone on a table does not trigger`() {
        standStill(3)
        // Picked up and moved: a little above and below 1g.
        feed(1f, -11f, 2f, 20)
        feed(0f, -8f, -4f, 20)
        // Set down flat with a modest knock, then lies still face up.
        feed(0f, -2f, -15f, 2)
        assertNull("setting the phone down is not a fall", lieStill(3))
    }

    @Test
    fun `running never triggers`() {
        var result: FallCandidate? = null
        var reachedPossible = false
        repeat(40) {
            // One stride: brief low-g flight, hard footfall, then push-off.
            feed(1f, -2.5f, 1f, 6)?.let { r -> result = r }
            feed(3f, -27f, 4f, 3)?.let { r -> result = r }
            if (engine.state == FallDetectionState.POSSIBLE_FALL) reachedPossible = true
            feed(4f, -13f, 3f, 11)?.let { r -> result = r }
        }
        assertNull("running is not a fall", result)
        assertTrue("footfalls should at least be considered", reachedPossible)
    }

    @Test
    fun `a short shake never triggers`() {
        standStill(2)
        var result: FallCandidate? = null
        repeat(50) { i ->
            clock += SAMPLE_INTERVAL_MILLIS
            val s = if (i % 2 == 0) 25f else -25f
            engine.feed(s, -G, s * 0.5f, clock)?.let { result = it }
        }
        assertNull(result)
        assertNull(standStill(2))
    }

    // --- State machine --------------------------------------------------------

    @Test
    fun `an impact moves to POSSIBLE_FALL and never straight to a fall`() {
        standStill(3)
        feed(0.4f, 0.6f, 0.3f, 8)
        val atImpact = feed(2f, -30f, 4f, 3)
        assertNull("an impact alone must never produce a fall", atImpact)
        assertEquals(FallDetectionState.POSSIBLE_FALL, engine.state)
    }

    @Test
    fun `spike followed by continued walking is cancelled`() {
        standStill(3)
        feed(0.4f, 0.6f, 0.3f, 8)
        feed(2f, -30f, 4f, 3)
        assertEquals(FallDetectionState.POSSIBLE_FALL, engine.state)

        var cancelled = false
        var result: FallCandidate? = null
        repeat(150) { i ->
            clock += SAMPLE_INTERVAL_MILLIS
            val stride = if (i % 10 < 5) 5f else -5f
            engine.feed(stride, -G + stride, stride, clock)?.let { result = it }
            if (engine.state == FallDetectionState.CANCELLED) cancelled = true
        }
        assertTrue("continued activity should cancel the possible fall", cancelled)
        assertNull(result)
    }

    @Test
    fun `a matched pattern passes through CONFIRMING to FALL_CONFIRMED`() {
        standStill(3)
        feed(0.4f, 0.6f, 0.3f, 8)
        feed(2f, -30f, 4f, 3)
        var sawConfirming = false
        var candidate: FallCandidate? = null
        repeat(150) {
            clock += SAMPLE_INTERVAL_MILLIS
            engine.feed(0f, 0f, -G, clock)?.let { c -> candidate = c }
            if (engine.state == FallDetectionState.CONFIRMING) sawConfirming = true
            if (candidate != null) return@repeat
        }
        assertTrue(sawConfirming)
        assertNotNull(candidate)
        assertTrue(candidate!!.hadFreeFall)
    }

    @Test
    fun `the same movement repeated rapidly produces only one candidate`() {
        var count = 0
        repeat(5) {
            standStill(1)
            feed(0.4f, 0.6f, 0.3f, 8)
            feed(2f, -30f, 4f, 3)
            if (lieStill(2) != null) count++
        }
        assertEquals(1, count)
    }
}
