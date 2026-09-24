package com.sahaaya.domain.usecase.monitoring

import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.model.SafeZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The boundary cases matter more than the obvious ones here: a detector that
 * alerts correctly when someone walks a kilometre away is easy, and a detector
 * that stays quiet when they stand still on the boundary is the hard part.
 */
class SafeZoneEvaluatorTest {

    private val centre = GeoPoint(latitude = 12.9716, longitude = 77.5946)
    private val zone = SafeZone(centre = centre, radiusMetres = 200, label = "Home")

    /** A point [metres] north of the centre. 1 deg latitude is ~111_320 m. */
    private fun northOf(metres: Double, accuracy: Float? = 10f) = GeoPoint(
        latitude = centre.latitude + metres / 111_320.0,
        longitude = centre.longitude,
        accuracyMetres = accuracy,
    )

    private fun settleInside(evaluator: SafeZoneEvaluator) {
        repeat(SafeZoneEvaluator.CONSECUTIVE_FIXES_TO_CHANGE) {
            evaluator.evaluate(northOf(10.0), zone)
        }
        assertEquals(SafeZoneStatus.INSIDE, evaluator.currentStatus())
    }

    @Test
    fun `settles inside when well within the boundary`() {
        val evaluator = SafeZoneEvaluator()
        settleInside(evaluator)
    }

    @Test
    fun `raises exactly one alert crossing from inside to outside`() {
        val evaluator = SafeZoneEvaluator()
        settleInside(evaluator)

        // First fix outside is not enough on its own.
        val first = evaluator.evaluate(northOf(400.0), zone)
        assertFalse("one outlier must not alert", first.raiseExitAlert)
        assertEquals(SafeZoneStatus.INSIDE, first.status)

        val second = evaluator.evaluate(northOf(400.0), zone)
        assertTrue("confirmed crossing must alert", second.raiseExitAlert)
        assertEquals(SafeZoneStatus.OUTSIDE, second.status)

        // Staying outside must never alert again.
        repeat(10) {
            val next = evaluator.evaluate(northOf(500.0), zone)
            assertFalse("no repeat alerts while outside", next.raiseExitAlert)
            assertEquals(SafeZoneStatus.OUTSIDE, next.status)
        }
    }

    @Test
    fun `jitter across the boundary does not change state or alert`() {
        val evaluator = SafeZoneEvaluator()
        settleInside(evaluator)

        // Alternate either side of the line, inside the hysteresis band.
        repeat(12) { i ->
            val metres = if (i % 2 == 0) 195.0 else 210.0
            val decision = evaluator.evaluate(northOf(metres), zone)
            assertFalse("boundary jitter must not alert", decision.raiseExitAlert)
            assertEquals(SafeZoneStatus.INSIDE, decision.status)
        }
    }

    @Test
    fun `a poor accuracy fix is discarded and holds previous state`() {
        val evaluator = SafeZoneEvaluator()
        settleInside(evaluator)

        val decision = evaluator.evaluate(
            northOf(5_000.0, accuracy = SafeZoneEvaluator.MAX_ACCURACY_METRES + 1f),
            zone,
        )
        assertFalse("unusable fix must be discarded", decision.accepted)
        assertFalse("unusable fix must not alert", decision.raiseExitAlert)
        assertEquals(SafeZoneStatus.INSIDE, decision.status)
    }

    @Test
    fun `first fix after restart while outside does not alert`() {
        val evaluator = SafeZoneEvaluator()
        // UNKNOWN -> OUTSIDE. The patient may have left hours ago; claiming they
        // "just left" would be wrong.
        repeat(SafeZoneEvaluator.CONSECUTIVE_FIXES_TO_CHANGE) {
            val decision = evaluator.evaluate(northOf(900.0), zone)
            assertFalse(decision.raiseExitAlert)
        }
        assertEquals(SafeZoneStatus.OUTSIDE, evaluator.currentStatus())
    }

    @Test
    fun `restoring outside state prevents a duplicate alert after restart`() {
        val evaluator = SafeZoneEvaluator()
        evaluator.restore(SafeZoneStatus.OUTSIDE)

        repeat(5) {
            val decision = evaluator.evaluate(northOf(900.0), zone)
            assertFalse("already-known exit must not re-alert", decision.raiseExitAlert)
        }
    }

    @Test
    fun `returning inside is reported once and clears the outside state`() {
        val evaluator = SafeZoneEvaluator()
        evaluator.restore(SafeZoneStatus.OUTSIDE)

        evaluator.evaluate(northOf(10.0), zone)
        val confirmed = evaluator.evaluate(northOf(10.0), zone)

        assertTrue(confirmed.returnedInside)
        assertEquals(SafeZoneStatus.INSIDE, confirmed.status)
    }

    @Test
    fun `signed distance is negative inside and positive outside`() {
        val evaluator = SafeZoneEvaluator()

        val inside = evaluator.evaluate(northOf(120.0), zone)
        assertTrue("inside should be negative", inside.metresFromBoundary!! < 0)

        val outside = evaluator.evaluate(northOf(350.0), zone)
        assertTrue("outside should be positive", outside.metresFromBoundary!! > 0)
    }
}
