package com.sahaaya.feature.monitoring.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.SafeZone
import kotlin.math.cos
import kotlin.math.max

/**
 * Draws the patient's real route, current position and safe zone.
 *
 * A Compose canvas rather than an embedded Google Map, and the reasoning is
 * worth recording. A real map needs a Maps SDK key, which needs a billing
 * account, which this project does not have - so the alternative was not "a
 * nicer map" but "a grey rectangle that says the API key is missing". Every
 * quantity drawn here is a real GPS reading; what is missing is the street
 * artwork underneath, and the "Open in Google Maps" handoff covers the case
 * where a caregiver needs streets to navigate by.
 *
 * Geometry: an equirectangular projection centred on the drawn points, with
 * longitude scaled by cos(latitude). Over a safe zone a few kilometres across
 * the error against a proper projection is far smaller than GPS accuracy
 * itself, and unlike a Mercator it keeps the safe zone a circle.
 */
@Composable
internal fun RouteMap(
    route: List<GeoPoint>,
    current: GeoPoint?,
    zone: SafeZone?,
    modifier: Modifier = Modifier,
    inside: Boolean = true,
) {
    // What the view is framed around.
    //
    // The safe-zone centre is included only when the patient is somewhere near
    // it. This is not a nicety: a zone left centred on an old address hundreds
    // of kilometres away made the frame hundreds of kilometres wide, and a
    // genuine 300 m walk then collapsed into a single dot - the caregiver saw
    // no route at all and no error explaining why. Framing on the patient keeps
    // the thing being watched legible; the circle is still drawn, it simply
    // falls outside the visible area, which is the honest picture of a patient
    // a long way outside their zone.
    val anchors = buildList {
        addAll(route)
        current?.let { add(it) }
    }
    val zoneWorthFraming = zone?.takeIf { safeZone ->
        val reference = current ?: route.lastOrNull()
        reference == null ||
            reference.distanceMetresTo(safeZone.centre) <=
            safeZone.radiusMetres * ZONE_FRAMING_RADII
    }
    val points = buildList {
        addAll(anchors)
        // The circle's extent, not its centre.
        //
        // Framing on the centre alone reserved no room for the radius, so a
        // patient outside their zone pushed that centre to the very edge of the
        // canvas and half the boundary was clipped away - the caregiver saw an
        // arc running off the bottom of the panel instead of a circle. Handing
        // the projection the circle's outermost points makes the span cover the
        // whole boundary by construction.
        zoneWorthFraming?.let { addAll(it.framingExtent()) }
        // Nothing from the patient yet: frame on the zone alone so the
        // caregiver at least sees the boundary they drew.
        if (isEmpty()) zone?.let { addAll(it.framingExtent()) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MapPalette.Canvas)
    ) {
        if (points.isEmpty()) {
            Text(
                text = "Waiting for the first location from the patient's phone.",
                color = MapPalette.Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            return@Box
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val projection = MapProjection(
                points = points,
                // The safe zone must fit in the frame even when the patient is
                // standing at its centre and the route is a single dot;
                // otherwise the circle is drawn larger than the canvas and the
                // caregiver sees no boundary at all.
                minSpanMetres = zoneWorthFraming?.let { it.radiusMetres * 2.4 }
                    ?: DEFAULT_SPAN_METRES,
                width = size.width,
                height = size.height,
                padding = PADDING_PX,
            )

            if (zone != null) {
                val centre = projection.toOffset(zone.centre)
                val radiusPx = projection.metresToPixels(zone.radiusMetres.toDouble())
                drawCircle(
                    color = if (inside) MapPalette.ZoneFillInside else MapPalette.ZoneFillOutside,
                    radius = radiusPx,
                    center = centre,
                )
                drawCircle(
                    color = if (inside) MapPalette.ZoneEdgeInside else MapPalette.ZoneEdgeOutside,
                    radius = radiusPx,
                    center = centre,
                    style = Stroke(width = 4f),
                )
            }

            // The walked route. Drawn as one path so the joins are continuous
            // rather than a string of separate segments with gaps at the bends.
            if (route.size >= 2) {
                val path = Path()
                route.forEachIndexed { index, point ->
                    val offset = projection.toOffset(point)
                    if (index == 0) path.moveTo(offset.x, offset.y)
                    else path.lineTo(offset.x, offset.y)
                }
                drawPath(path, color = MapPalette.Route, style = Stroke(width = 7f))
            }

            // Where the patient has been, smallest marks, so the current
            // position is never confused with a historical one.
            route.forEach { point ->
                drawCircle(
                    color = MapPalette.RouteDot,
                    radius = 5f,
                    center = projection.toOffset(point),
                )
            }

            current?.let { position ->
                val offset = projection.toOffset(position)
                drawCircle(MapPalette.CurrentHalo, radius = 20f, center = offset)
                drawCircle(Color.White, radius = 12f, center = offset)
                drawCircle(
                    color = if (inside) MapPalette.CurrentInside else MapPalette.CurrentOutside,
                    radius = 9f,
                    center = offset,
                )
            }
        }
    }
}

/**
 * The four outermost points of the safe-zone circle.
 *
 * Given to the projection so the span it computes covers the whole boundary.
 * North/south/east/west extremes are enough: the circle is drawn round, so if
 * its widest and tallest points fit, all of it fits.
 */
internal fun SafeZone.framingExtent(): List<GeoPoint> {
    val latDelta = radiusMetres / METRES_PER_DEGREE_LAT
    // A degree of longitude is shorter away from the equator, so the same
    // number of metres is a larger number of degrees.
    val lonDelta = radiusMetres /
        (METRES_PER_DEGREE_LAT * cos(Math.toRadians(centre.latitude)).coerceAtLeast(0.01))
    return listOf(
        GeoPoint(centre.latitude + latDelta, centre.longitude),
        GeoPoint(centre.latitude - latDelta, centre.longitude),
        GeoPoint(centre.latitude, centre.longitude + lonDelta),
        GeoPoint(centre.latitude, centre.longitude - lonDelta),
    )
}

/** One degree of latitude, near enough anywhere on earth. */
internal const val METRES_PER_DEGREE_LAT = 111_320.0

/**
 * Maps latitude/longitude onto canvas pixels.
 *
 * Kept as a small class with no Compose types in its maths so the projection is
 * testable on the JVM: an off-by-one in the scaling would put the patient's dot
 * outside the safe-zone circle the same readings say they are inside, which is
 * precisely the sort of quiet wrongness a care app must not ship.
 */
internal class MapProjection(
    points: List<GeoPoint>,
    minSpanMetres: Double,
    private val width: Float,
    private val height: Float,
    private val padding: Float,
) {
    // The midpoint of the bounding box, NOT the mean of the points.
    //
    // The mean is pulled towards wherever points are dense, and the span below
    // is measured from the bounding box - so the two disagree and whatever lies
    // at the sparse end gets pushed off the canvas. It showed up as the
    // patient's own marker leaving the frame when the safe-zone extent
    // contributed several points at the opposite end, and it would do the same
    // to a walk that lingered at one spot and then set off: most dots at the
    // start, the patient's current position off-screen at the finish.
    //
    // The bounding-box midpoint is the only centre for which every framed point
    // is within half the span, which is what makes "it fits" true rather than
    // usually true.
    private val centreLat = (points.maxOf { it.latitude } + points.minOf { it.latitude }) / 2
    private val centreLon = (points.maxOf { it.longitude } + points.minOf { it.longitude }) / 2

    /** Metres per degree of longitude shrinks towards the poles. */
    private val lonScale = cos(Math.toRadians(centreLat)).coerceAtLeast(0.01)

    private val spanMetres: Double = run {
        val latSpan = (
            (points.maxOf { it.latitude } - points.minOf { it.latitude }) * METRES_PER_DEGREE_LAT
            )
        val lonSpan = (
            (points.maxOf { it.longitude } - points.minOf { it.longitude }) *
                METRES_PER_DEGREE_LAT * lonScale
            )
        // A square span keeps the safe zone circular and the route undistorted.
        max(max(latSpan, lonSpan), minSpanMetres)
    }

    private val usable = max(1f, minOf(width, height) - padding * 2)

    /** Canvas pixels per metre on the ground. */
    private val pixelsPerMetre: Double = usable / spanMetres

    fun metresToPixels(metres: Double): Float = (metres * pixelsPerMetre).toFloat()

    fun toOffset(point: GeoPoint): Offset {
        val eastMetres = (point.longitude - centreLon) * METRES_PER_DEGREE_LAT * lonScale
        // Screen y grows downwards while latitude grows northwards.
        val northMetres = (point.latitude - centreLat) * METRES_PER_DEGREE_LAT
        return Offset(
            x = width / 2f + metresToPixels(eastMetres),
            y = height / 2f - metresToPixels(northMetres),
        )
    }

}

private const val PADDING_PX = 28f

/**
 * How many zone radii away the patient can be before the zone stops being used
 * to frame the map. Three keeps a patient who has wandered just outside their
 * boundary on screen together with it - the case a caregiver most wants to see -
 * while dropping a zone that is effectively somewhere else entirely.
 */
private const val ZONE_FRAMING_RADII = 3

/** What to show when there is no zone to frame: a few streets' worth. */
private const val DEFAULT_SPAN_METRES = 600.0

private object MapPalette {
    val Canvas = Color(0xFFEDF2F7)
    val Muted = Color(0xFF5B6B82)
    val Route = Color(0xFFE5484D)
    val RouteDot = Color(0xFFB3272C)
    val CurrentHalo = Color(0x332F7BEA)
    val CurrentInside = Color(0xFF22A55B)
    val CurrentOutside = Color(0xFFE5484D)
    val ZoneFillInside = Color(0x1A2F7BEA)
    val ZoneEdgeInside = Color(0xFF2F7BEA)
    val ZoneFillOutside = Color(0x1AE5484D)
    val ZoneEdgeOutside = Color(0xFFE5484D)
}
