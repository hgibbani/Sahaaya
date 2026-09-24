package com.sahaaya.feature.monitoring.tracking

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.domain.model.PatientLocation
import java.util.concurrent.TimeUnit

/**
 * "Patient Live Tracking" - the caregiver's screen.
 *
 * Start and stop live here rather than only on the dashboard, so a caregiver who
 * has drilled into the map does not have to navigate back to turn tracking off.
 */
@Composable
fun LiveTrackingScreen(
    patientName: String,
    onNavigateBack: () -> Unit,
    onSetSafeZone: (String) -> Unit,
    viewModel: LiveTrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SahaayaScreen(
        title = "Patient Live Tracking",
        onNavigateBack = onNavigateBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderRow(
                name = patientName.ifBlank { "Patient" },
                tracking = state.isTracking,
            )

            RouteMap(
                route = state.route.map { it.point },
                current = state.location?.point,
                zone = state.zone.takeIf { state.geofenceEnabled },
                inside = state.isInsideZone,
            )

            StatusCard(state = state)

            if (state.isTracking) {
                SessionStatsRow(
                    distanceMetres = state.routeDistanceMetres,
                    durationMillis = state.sessionDurationMillis,
                )
            }

            state.errorMessage?.let { message ->
                Text(text = message, color = TrackPalette.Danger, fontSize = 15.sp)
            }

            // "Open in Google Maps" is the escape hatch from the canvas map: it
            // hands the real coordinates to an app that has streets.
            state.location?.let { location ->
                OutlinedButton(
                    onClick = {
                        val uri = Uri.parse(
                            "geo:${location.point.latitude},${location.point.longitude}" +
                                "?q=${location.point.latitude},${location.point.longitude}" +
                                "(${patientName.ifBlank { "Patient" }})",
                        )
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Open in Google Maps", fontSize = 17.sp)
                }
            }

            OutlinedButton(
                onClick = { onSetSafeZone(state.patientId) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Set Safe Zone", fontSize = 17.sp)
            }

            Button(
                onClick = {
                    if (state.isTracking) viewModel.onStopTracking()
                    else viewModel.onStartTracking()
                },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isTracking) TrackPalette.Danger
                    else TrackPalette.Go,
                ),
            ) {
                Text(
                    text = if (state.isTracking) "Stop Tracking" else "Start Tracking Patient",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeaderRow(name: String, tracking: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TrackPalette.Ink)
            Text(
                text = if (tracking) "Tracking Live" else "Tracking off",
                fontSize = 16.sp,
                color = if (tracking) TrackPalette.Go else TrackPalette.Muted,
            )
        }
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (tracking) TrackPalette.Go else TrackPalette.Muted),
        )
    }
}

@Composable
private fun StatusCard(state: LiveTrackingUiState) {
    val outside = !state.isInsideZone && state.geofenceEnabled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (outside) TrackPalette.DangerBg else TrackPalette.Strip)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = state.statusLabel,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (outside) TrackPalette.Danger else TrackPalette.Ink,
        )
        Text(
            text = state.location?.let { "Last updated ${relativeTime(it)}" }
                ?: "No location reported yet",
            fontSize = 15.sp,
            color = TrackPalette.Muted,
        )
        state.location?.let { location ->
            Text(
                text = "%.5f, %.5f".format(location.point.latitude, location.point.longitude),
                fontSize = 15.sp,
                color = TrackPalette.Ink,
            )
        }
        if (state.geofenceEnabled) {
            state.zone?.let { zone ->
                Text(
                    text = "Safe zone: ${zone.label}, ${zone.radiusMetres} m radius",
                    fontSize = 15.sp,
                    color = TrackPalette.Muted,
                )
            }
            // Signed distance, so the caregiver reads "how much room is left"
            // and "how far gone" off the same line.
            state.location?.metresFromBoundary?.let { metres ->
                Text(
                    text = if (metres >= 0) "About $metres m beyond the boundary"
                    else "About ${-metres} m inside the boundary",
                    fontSize = 15.sp,
                    color = if (metres >= 0) TrackPalette.Danger else TrackPalette.Muted,
                )
            }
        }
    }
}

@Composable
private fun SessionStatsRow(distanceMetres: Double, durationMillis: Long?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(
            label = "Distance travelled",
            value = if (distanceMetres >= 1_000) "%.1f km".format(distanceMetres / 1_000)
            else "${distanceMetres.toInt()} m",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Duration",
            value = durationMillis?.let { millis ->
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
                if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
            } ?: "-",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TrackPalette.Strip)
            .padding(14.dp),
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TrackPalette.Ink)
        Text(label, fontSize = 13.sp, color = TrackPalette.Muted)
    }
}

/** Short, human relative time. Seconds matter on this screen; days do not. */
internal fun relativeTime(location: PatientLocation): String {
    val elapsed = System.currentTimeMillis() - location.recordedAtEpochMillis
    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    return when {
        seconds < 10 -> "just now"
        seconds < 60 -> "$seconds seconds ago"
        minutes < 60 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
        else -> "over a day ago"
    }
}

private object TrackPalette {
    val Ink = Color(0xFF1B2A41)
    val Muted = Color(0xFF5B6B82)
    val Strip = Color(0xFFEEF3FB)
    val Go = Color(0xFF22A55B)
    val Danger = Color(0xFFE5484D)
    val DangerBg = Color(0xFFFDE7EA)
}
