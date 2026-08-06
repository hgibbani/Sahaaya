package com.sahaaya.feature.dashboard.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.sahaaya.domain.model.EventSeverity
import com.sahaaya.domain.model.EventType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * How each event type is drawn.
 *
 * Colour follows severity, not type. Every critical event is the same red, so a
 * caregiver glancing at the timeline reads urgency before they read what
 * happened - which is the right order when they are half awake.
 */
object EventVisuals {

    fun icon(type: EventType): ImageVector = when (type) {
        EventType.FALL -> Icons.Filled.PersonalInjury
        EventType.SOS -> Icons.Filled.Emergency
        EventType.GEOFENCE_EXIT -> Icons.Filled.DirectionsWalk
        EventType.INACTIVITY -> Icons.Filled.Hotel
        EventType.MEDICATION_MISSED -> Icons.Filled.Medication
    }

    @Composable
    @ReadOnlyComposable
    fun containerColor(type: EventType): Color = when (type.severity) {
        EventSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
        EventSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    }

    @Composable
    @ReadOnlyComposable
    fun contentColor(type: EventType): Color = when (type.severity) {
        EventSeverity.CRITICAL -> MaterialTheme.colorScheme.onErrorContainer
        EventSeverity.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    /** Short label for the summary cards. */
    fun shortLabel(type: EventType): String = when (type) {
        EventType.FALL -> "Falls"
        EventType.SOS -> "SOS"
        EventType.GEOFENCE_EXIT -> "Safe zone"
        EventType.INACTIVITY -> "Inactivity"
        EventType.MEDICATION_MISSED -> "Missed doses"
    }
}

/**
 * "12 minutes ago", not a timestamp.
 *
 * A caregiver reading the timeline is working out how long ago something
 * happened, and elapsed time answers that directly. The exact clock time is on
 * the detail screen for when it matters.
 */
fun formatRelativeTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val elapsed = (nowMillis - epochMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)

    return when {
        minutes < 1 -> "Just now"
        minutes == 1L -> "1 minute ago"
        minutes < 60 -> "$minutes minutes ago"
        hours == 1L -> "1 hour ago"
        hours < 24 -> "$hours hours ago"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMillis))
    }
}

fun formatAbsoluteTime(epochMillis: Long): String =
    SimpleDateFormat("EEEE d MMMM, h:mm a", Locale.getDefault()).format(Date(epochMillis))
