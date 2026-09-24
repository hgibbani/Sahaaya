package com.sahaaya.feature.monitoring.tracking

import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.SafeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The projection behind the caregiver's route map.
 *
 * Tested because the failure mode is quiet rather than loud: a scaling mistake
 * does not crash, it draws the patient's dot outside a safe-zone circle the
 * same readings say they are inside. A caregiver would then be looking at a
 * picture that contradicts the words next to it, and there would be nothing in
 * a log to say which one was wrong.
 */
class MapProjectionTest {

    private val width = 1000f
    private val height = 1000f
    private val padding = 20f

    private fun projection(
        points: List<GeoPoint>,
        minSpanMetres: Double = 600.0,
    ) = MapProjection(
        points = points,
        minSpanMetres = minSpanMetres,
        width = width,
        height = height,
        padding = padding,
    )

    @Test
    fun `a single point lands in the middle of the canvas`() {
        val here = GeoPoint(12.99, 77.61)
        val offset = projection(listOf(here)).toOffset(here)

        assertEquals(width / 2f, offset.x, 0.5f)
        assertEquals(height / 2f, offset.y, 0.5f)
    }

    @Test
    fun `north is up`() {
        val south = GeoPoint(12.990, 77.61)
        val north = GeoPoint(12.995, 77.61)
        val projection = projection(listOf(south, north))

        // Screen y grows downwards, so the northern point must have the
        // smaller y. Getting this backwards would mirror every route.
        assertTrue(projection.toOffset(north).y < projection.toOffset(south).y)
    }

    @Test
    fun `east is right`() {
        val west = GeoPoint(12.99, 77.610)
        val east = GeoPoint(12.99, 77.615)
        val projection = projection(listOf(west, east))

        assertTrue(projection.toOffset(east).x > projection.toOffset(west).x)
    }

    @Test
    fun `a metre east and a metre north are the same number of pixels`() {
        // Anisotropic scaling would draw the safe zone as an ellipse and make a
        // straight walk look like a curve.
        val centre = GeoPoint(12.99, 77.61)
        val projection = projection(listOf(centre), minSpanMetres = 1_000.0)

        val northOffset = projection.toOffset(GeoPoint(12.99 + 100 / 111_320.0, 77.61))
        val eastMetresPerDegree = 111_320.0 * Math.cos(Math.toRadians(12.99))
        val eastOffset = projection.toOffset(GeoPoint(12.99, 77.61 + 100 / eastMetresPerDegree))

        val northPixels = (height / 2f) - northOffset.y
        val eastPixels = eastOffset.x - (width / 2f)

        assertEquals(northPixels, eastPixels, 0.5f)
    }

    @Test
    fun `a safe zone fits inside the canvas when the patient is at its centre`() {
        // The case that made this class necessary: one point and a zone. With no
        // minimum span the measured span is zero, the scale is infinite, and the
        // circle is drawn larger than the screen - so the caregiver sees no
        // boundary at all.
        val centre = GeoPoint(12.99, 77.61)
        val radiusMetres = 250.0
        val projection = projection(listOf(centre), minSpanMetres = radiusMetres * 2.4)

        val radiusPx = projection.metresToPixels(radiusMetres)

        assertTrue("circle must fit", radiusPx < minOf(width, height) / 2f)
        // And it should still be big enough to read as a boundary.
        assertTrue("circle must be visible", radiusPx > 100f)
    }

    @Test
    fun `a point 100 m from the centre is inside a 250 m circle on screen too`() {
        val centre = GeoPoint(12.99, 77.61)
        val hundredMetresNorth = GeoPoint(12.99 + 100 / 111_320.0, 77.61)
        val projection = projection(
            points = listOf(centre, hundredMetresNorth),
            minSpanMetres = 250.0 * 2.4,
        )

        val centreOffset = projection.toOffset(centre)
        val pointOffset = projection.toOffset(hundredMetresNorth)
        val drawnDistance = hypot(
            (pointOffset.x - centreOffset.x).toDouble(),
            (pointOffset.y - centreOffset.y).toDouble(),
        )

        // The geometry on screen must agree with the geometry on the ground.
        assertTrue(drawnDistance < projection.metresToPixels(250.0))
    }

    @Test
    fun `longitude is compressed towards the poles`() {
        // A degree of longitude is much shorter in Reykjavik than in Bangalore.
        // Ignoring cos(latitude) stretches high-latitude routes sideways.
        val tropicalStep = projection(listOf(GeoPoint(0.0, 0.0)), minSpanMetres = 1_000.0)
            .toOffset(GeoPoint(0.0, 0.01)).x
        val arcticStep = projection(listOf(GeoPoint(64.0, 0.0)), minSpanMetres = 1_000.0)
            .toOffset(GeoPoint(64.0, 0.01)).x

        assertTrue(arcticStep - width / 2f < tropicalStep - width / 2f)
    }

    /**
     * The clipping bug, pinned.
     *
     * A patient 657 m north of a 250 m zone used to push the zone centre to the
     * very bottom of the canvas, because the centre was the southernmost point
     * being framed and nothing reserved room for the radius. The lower half of
     * the boundary was then drawn off-panel. Framing on the circle's extent
     * fixes it; this test fails if anyone goes back to framing on the centre.
     */
    @Test
    fun `the whole safe-zone circle stays on the canvas when the patient is outside it`() {
        val zone = SafeZone(centre = GeoPoint(12.9926, 77.6100), radiusMetres = 250)
        val patient = GeoPoint(12.9985, 77.6100) // ~657 m north, 407 m outside

        val framed = buildList {
            add(patient)
            addAll(zone.framingExtent())
        }
        val projection = MapProjection(
            points = framed,
            minSpanMetres = zone.radiusMetres * 2.4,
            width = width,
            height = height,
            padding = padding,
        )

        val centre = projection.toOffset(zone.centre)
        val radiusPx = projection.metresToPixels(zone.radiusMetres.toDouble())

        assertTrue("bottom of circle clipped", centre.y + radiusPx <= height)
        assertTrue("top of circle clipped", centre.y - radiusPx >= 0f)
        assertTrue("left of circle clipped", centre.x - radiusPx >= 0f)
        assertTrue("right of circle clipped", centre.x + radiusPx <= width)
        // And the patient must still be on screen - fitting the circle is no
        // use if it pushes the person being watched out of frame.
        val patientOffset = projection.toOffset(patient)
        assertTrue(patientOffset.y in 0f..height)
        assertTrue(patientOffset.x in 0f..width)
    }

    @Test
    fun `the framing extent surrounds the centre by the radius`() {
        val zone = SafeZone(centre = GeoPoint(12.99, 77.61), radiusMetres = 250)
        val extent = zone.framingExtent()

        assertEquals(4, extent.size)
        extent.forEach { point ->
            assertEquals(
                250.0,
                zone.centre.distanceMetresTo(point),
                // Haversine against a flat-earth delta: a metre is plenty.
                1.0,
            )
        }
    }

    @Test
    fun `padding keeps the drawing off the very edge`() {
        val a = GeoPoint(12.990, 77.610)
        val b = GeoPoint(12.995, 77.615)
        val projection = projection(listOf(a, b), minSpanMetres = 0.0)

        listOf(a, b).forEach { point ->
            val offset = projection.toOffset(point)
            assertTrue(offset.x >= padding - 1f && offset.x <= width - padding + 1f)
            assertTrue(offset.y >= padding - 1f && offset.y <= height - padding + 1f)
        }
    }
}
