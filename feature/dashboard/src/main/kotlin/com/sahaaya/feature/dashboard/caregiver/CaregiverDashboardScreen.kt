package com.sahaaya.feature.dashboard.caregiver

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingStatus
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.Role as UserRole
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.model.TrackingState
import com.sahaaya.domain.model.SafeZoneStatus
import com.sahaaya.domain.model.User
import com.sahaaya.feature.dashboard.timeline.formatRelativeTime
import com.sahaaya.feature.dashboard.ui.Palette

@Composable
fun CaregiverDashboardScreen(
    onOpenProfile: () -> Unit,
    onAddPatient: () -> Unit,
    onOpenPatient: (patientId: String) -> Unit,
    /** Hidden from the current caregiver UI; kept so it can be restored. */
    @Suppress("UNUSED_PARAMETER") onOpenPatientMedications: (patientId: String) -> Unit,
    onOpenTimeline: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenEvent: (eventId: String) -> Unit,
    onOpenSafeZone: (patientId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPatientReminders: (patientId: String) -> Unit = {},
    onOpenLiveTracking: (patientId: String, patientName: String) -> Unit = { _, _ -> },
    viewModel: CaregiverDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val patientLocation by viewModel.patientLocation.collectAsStateWithLifecycle()
    val safeZone by viewModel.safeZone.collectAsStateWithLifecycle()
    val tracking by viewModel.trackingState.collectAsStateWithLifecycle()

    CaregiverDashboardContent(
        state = state,
        patientLocation = patientLocation,
        safeZone = safeZone,
        tracking = tracking,
        onOpenLiveTracking = onOpenLiveTracking,
        onStartTracking = viewModel::startTracking,
        onStopTracking = viewModel::stopTracking,
        onOpenProfile = onOpenProfile,
        onAddPatient = onAddPatient,
        onOpenPatient = onOpenPatient,
        onOpenSafeZone = onOpenSafeZone,
        onOpenPatientReminders = onOpenPatientReminders,
        onOpenTimeline = onOpenTimeline,
        onUnlinkPatient = viewModel::unlinkPatient,
        onSignOut = viewModel::signOut,
        onAcknowledge = viewModel::acknowledge,
        modifier = modifier,
    )
}

/**
 * The caregiver's home screen, styled to match the patient's so the two read as
 * one app.
 *
 * Built around one question - is the person I look after all right? - so the
 * linked patient and their status come first, then the way to reach them, then
 * four large tiles. SOS, medicines and emergency contacts are deliberately not
 * shown here in this release; their screens and data are untouched, and SOS
 * still reaches the caregiver as a notification and in Event history.
 */
@Composable
private fun CaregiverDashboardContent(
    state: CaregiverDashboardUiState,
    patientLocation: PatientLocation?,
    safeZone: SafeZone?,
    tracking: TrackingState,
    onOpenLiveTracking: (String, String) -> Unit,
    onStartTracking: (String) -> Unit,
    onStopTracking: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onAddPatient: () -> Unit,
    onOpenPatient: (String) -> Unit,
    onOpenSafeZone: (String) -> Unit,
    onOpenPatientReminders: (String) -> Unit,
    onOpenTimeline: () -> Unit,
    onUnlinkPatient: (String) -> Unit,
    onSignOut: () -> Unit,
    onAcknowledge: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(label = "Loading your patients")
        return
    }

    val context = LocalContext.current
    val patient: Pairing? = state.patients.firstOrNull { it.isActive }
    var showingLocation by remember { mutableStateOf(false) }
    var confirmingUnlink by remember { mutableStateOf(false) }
    var callNotice by remember { mutableStateOf<String?>(null) }

    if (showingLocation && patient != null) {
        PatientLocationDialog(
            patientName = patient.patientName,
            location = patientLocation,
            zone = safeZone,
            onDismiss = { showingLocation = false },
        )
    }

    if (confirmingUnlink && patient != null) {
        AlertDialog(
            onDismissRequest = { confirmingUnlink = false },
            title = { Text("Unlink ${patient.patientName.ifBlank { "this patient" }}?") },
            text = {
                Text(
                    "You will stop receiving their alerts. They can link you again " +
                        "with a new code.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingUnlink = false
                        onUnlinkPatient(patient.id)
                    },
                ) { Text("Unlink", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUnlink = false }) { Text("Keep linked") }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(
            name = state.user?.firstName ?: state.user?.displayName.orEmpty(),
            onOpenProfile = onOpenProfile,
            onSignOut = onSignOut,
        )

        val error = state.errorMessage
        if (error != null) Banner(message = error)

        if (patient == null) {
            NoPatientCard(onAddPatient = onAddPatient)
            return@Column
        }

        LinkedPatientCard(
            pairing = patient,
            location = patientLocation,
            zone = safeZone,
            onClick = { onOpenPatient(patient.patientId) },
        )

        // A possible fall the patient did not dismiss: they asked for help, or
        // did not answer. Shown above everything else until it is marked seen.
        val fall = state.recentEvents.firstOrNull {
            it.type == EventType.FALL && it.isUnresolved && it.patientId == patient.patientId
        }
        if (fall != null) {
            PossibleFallCard(
                event = fall,
                onOpenLocation = { point ->
                    val label = Uri.encode(fall.patientName.ifBlank { "Patient" })
                    val uri = Uri.parse(
                        "geo:${point.latitude},${point.longitude}?q=" +
                            "${point.latitude},${point.longitude}($label)",
                    )
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
                onCall = {
                    if (patient.patientPhone.isNotBlank()) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.patientPhone}")),
                            )
                        }
                    } else {
                        callNotice = "This patient has no phone number on their link."
                    }
                },
                onSeen = { onAcknowledge(fall.id) },
            )
        }

        // Live tracking. Above the call card because when a caregiver is
        // worried enough to open this app, "where are they" comes before
        // "phone them" - and starting tracking is the thing only they can do.
        TrackingCard(
            patientName = patient.patientName,
            tracking = tracking,
            onStart = { onStartTracking(patient.patientId) },
            onStop = { onStopTracking(patient.patientId) },
            onOpenLive = { onOpenLiveTracking(patient.patientId, patient.patientName) },
        )

        // ACTION_DIAL: the number goes to the dialler and the caregiver presses
        // call. The app never starts a call on its own.
        WideActionCard(
            title = "Call Patient",
            subtitle = patient.patientName.takeIf { patient.patientPhone.isNotBlank() },
            icon = Icons.Filled.Phone,
            iconTint = Palette.CallIcon,
            iconCircle = Palette.CallCircle,
            onClick = {
                if (patient.patientPhone.isBlank()) {
                    callNotice = "This patient has no phone number on their link. " +
                        "Ask them to add one and link again."
                } else {
                    callNotice = null
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.patientPhone}")),
                        )
                    }.onFailure { callNotice = "No dialler app is available." }
                }
            },
        )
        val notice = callNotice
        if (notice != null) Banner(message = notice)

        TileRow {
            Tile(
                label = "Patient Profile",
                icon = Icons.Filled.Person,
                tint = Palette.LinkIcon,
                background = Palette.LinkBg,
                circle = Palette.LinkCircle,
                onClick = { onOpenPatient(patient.patientId) },
            )
            // Opens the live map rather than the old coordinates dialog: the
            // map is a superset of it - it shows the same position plus the
            // route and the boundary - so keeping both would be two doors to
            // the same room, one of them worse.
            Tile(
                label = "Patient Location",
                icon = Icons.Filled.LocationOn,
                tint = Palette.LocationIcon,
                background = Palette.LocationBg,
                circle = Palette.LocationCircle,
                caption = "Live location and route",
                onClick = { onOpenLiveTracking(patient.patientId, patient.patientName) },
            )
        }
        TileRow {
            Tile(
                label = "Safe Zone",
                icon = Icons.Filled.Shield,
                tint = Palette.MedicineIcon,
                background = Palette.MedicineBg,
                circle = Palette.MedicineCircle,
                caption = zone(safeZone, patientLocation),
                onClick = { onOpenSafeZone(patient.patientId) },
            )
            Tile(
                label = "Reminders",
                icon = Icons.Filled.EventAvailable,
                tint = Palette.ReminderIcon,
                background = Palette.ReminderBg,
                circle = Palette.ReminderCircle,
                onClick = { onOpenPatientReminders(patient.patientId) },
            )
        }

        // The existing timeline. SOS and safe-zone alerts are listed there, and
        // Clear all lives on that screen.
        WideActionCard(
            title = "Event history",
            subtitle = if (state.unresolvedEvents.isEmpty()) {
                "All clear"
            } else {
                "${state.unresolvedEvents.size} new"
            },
            icon = Icons.Filled.History,
            iconTint = Palette.SettingsIcon,
            iconCircle = Palette.SettingsCircle,
            onClick = onOpenTimeline,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onAddPatient) { Text("Link another patient") }
            TextButton(onClick = { confirmingUnlink = true }) {
                Text("Unlink", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Header(name: String, onOpenProfile: () -> Unit, onSignOut: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClick = onOpenProfile),
            verticalAlignment = Alignment.Top,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Palette.CaregiverAvatar, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(50.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = name.ifBlank { "Hello" },
                    color = Palette.Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Text(text = "Caregiver", color = Palette.Muted, fontSize = 17.sp)
            }
        }
        IconButton(
            onClick = onSignOut,
            modifier = Modifier
                .size(56.dp)
                .background(Palette.ChipBg, RoundedCornerShape(16.dp)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Sign out",
                tint = Palette.Ink,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Who is linked, whether their phone is reporting, and the two facts a caregiver
 * checks first: where they last were and whether that is inside the safe zone.
 *
 * "Online" means the patient's phone published a position within the staleness
 * window - it is a statement about data freshness, not a live presence signal.
 * Battery is not shown because the app does not collect it.
 */
@Composable
private fun LinkedPatientCard(
    pairing: Pairing,
    location: PatientLocation?,
    zone: SafeZone?,
    onClick: () -> Unit,
) {
    val online = location != null && !location.isStale

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Palette.Border, RoundedCornerShape(24.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Palette.PatientAvatarCircle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Palette.CallIcon,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pairing.patientName.ifBlank { "Patient" },
                    color = Palette.Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (pairing.status == PairingStatus.ACTIVE) {
                        "Linked patient"
                    } else {
                        "Not linked"
                    },
                    color = Palette.Muted,
                    fontSize = 15.sp,
                )
            }
            Row(
                modifier = Modifier
                    .background(
                        if (online) Palette.OnlineBg else Palette.OfflineBg,
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (online) Palette.OnlineDot else Palette.OfflineDot,
                            CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (online) "Online" else "Offline",
                    color = if (online) Palette.OnlineDot else Palette.Muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Palette.Ink,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(Palette.StripBg)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            StatusCell(
                icon = Icons.Filled.LocationOn,
                tint = Palette.LinkIcon,
                title = "Last location",
                value = location?.let {
                    "%.4f, %.4f".format(it.point.latitude, it.point.longitude)
                } ?: "Not yet shared",
                detail = location?.let { formatRelativeTime(it.recordedAtEpochMillis) },
            )
            StatusCell(
                icon = Icons.Filled.Shield,
                tint = if (zoneIsOutside(zone, location)) Palette.OutsideText else Palette.CallIcon,
                title = "Safe zone",
                value = zone(zone, location),
                detail = zone?.let { "${it.radiusMetres} m radius" },
            )
        }
    }
}

@Composable
private fun RowScope.StatusCell(
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String,
    detail: String?,
) {
    Row(modifier = Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.Top) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = value, color = Palette.Ink, fontSize = 15.sp)
            if (detail != null) Text(text = detail, color = Palette.Muted, fontSize = 13.sp)
        }
    }
}

/**
 * Start or stop live tracking, and get to the map.
 *
 * Start is a single tap because a caregiver reaching for it is usually already
 * worried. Stop is deliberately the same size and in the same place rather than
 * tucked away, because a caregiver who has stopped worrying should find it
 * easy to stop watching - tracking somebody indefinitely because turning it off
 * was fiddly is a failure mode of its own.
 */
@Composable
private fun TrackingCard(
    patientName: String,
    tracking: TrackingState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenLive: () -> Unit,
) {
    val active = tracking.active
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (active) Palette.OnlineBg else Color.White)
            .border(
                1.dp,
                if (active) Palette.OnlineDot else Palette.Border,
                RoundedCornerShape(24.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Palette.LocationCircle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Palette.LocationIcon,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Patient Location",
                    color = Palette.Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (active) {
                        "Tracking ${patientName.ifBlank { "your patient" }} live"
                    } else {
                        "Tracking is off"
                    },
                    color = if (active) Palette.OnlineDot else Palette.Muted,
                    fontSize = 16.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = if (active) onStop else onStart,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) Palette.OutsideText else Palette.OnlineDot,
            ),
        ) {
            Text(
                text = if (active) "Stop Tracking" else "Start Tracking Patient",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (active) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenLive,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("View live route", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun WideActionCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    iconTint: Color,
    iconCircle: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Palette.Border, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(60.dp).background(iconCircle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, color = Palette.Muted, fontSize = 16.sp)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Palette.Ink,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun RowScope.Tile(
    label: String,
    icon: ImageVector,
    tint: Color,
    background: Color,
    circle: Color,
    onClick: () -> Unit,
    caption: String? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = 140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(role = Role.Button, onClickLabel = "Open $label", onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(60.dp).background(circle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            color = Palette.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (caption != null) {
            Text(text = caption, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NoPatientCard(onAddPatient: () -> Unit) {
    WideActionCard(
        title = "Link a patient",
        subtitle = "Enter the six-character code from their phone",
        icon = Icons.Filled.Person,
        iconTint = Palette.LinkIcon,
        iconCircle = Palette.LinkCircle,
        onClick = onAddPatient,
    )
}

/**
 * The patient's latest real position, from `patients/{uid}` - the same document
 * their own phone writes. Nothing here is computed on the caregiver's device.
 */
@Composable
private fun PatientLocationDialog(
    patientName: String,
    location: PatientLocation?,
    zone: SafeZone?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${patientName.ifBlank { "Patient" }}'s location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (location == null) {
                    Text(
                        "No location yet. It appears once the patient opens Sahaaya " +
                            "with location switched on.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Text(
                        text = zone(zone, location),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (zoneIsOutside(zone, location)) Palette.OutsideText else Palette.Ink,
                    )
                    Text(
                        text = "%.5f, %.5f".format(location.point.latitude, location.point.longitude),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Last updated ${formatRelativeTime(location.recordedAtEpochMillis)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Muted,
                    )
                    location.metresFromBoundary?.takeIf { zone != null && !location.isStale }?.let { m ->
                        Text(
                            text = if (m > 0) "About $m m outside the boundary" else "About ${-m} m inside the boundary",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Muted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (location != null) {
                TextButton(
                    onClick = {
                        val p = location.point
                        val label = Uri.encode(patientName.ifBlank { "Patient" })
                        val uri = Uri.parse("geo:${p.latitude},${p.longitude}?q=${p.latitude},${p.longitude}($label)")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    },
                ) { Text("Open in Maps") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * "Possible Fall Event" - deliberately not "Fall detected": a phone sensor
 * cannot confirm a person fell, and the card must not claim more than it knows.
 * The location is the fix attached when the alert was sent, from the patient's
 * phone, not a live position.
 */
@Composable
private fun PossibleFallCard(
    event: HealthEvent,
    onOpenLocation: (GeoPoint) -> Unit,
    onCall: () -> Unit,
    onSeen: () -> Unit,
) {
    val status = event.details["Status"] ?: "Possible fall"
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(event.occurredAtEpochMillis))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.OutsideBg)
            .border(2.dp, Palette.EmergencyIcon, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (event.details.containsKey("Source")) "Possible Fall Event (TEST)" else "Possible Fall Event",
            color = Palette.OutsideText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("Patient: ${event.patientName.ifBlank { "Patient" }}", color = Palette.Ink, fontSize = 17.sp)
        Text("Time: $time (${formatRelativeTime(event.occurredAtEpochMillis)})", color = Palette.Ink, fontSize = 17.sp)
        Text("Status: $status", color = Palette.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        val point = event.location
        Text(
            text = if (point != null) {
                "Last known location: %.5f, %.5f".format(point.latitude, point.longitude)
            } else {
                "Location was not available when the alert was sent"
            },
            color = Palette.Muted,
            fontSize = 15.sp,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (point != null) {
                TextButton(onClick = { onOpenLocation(point) }) { Text("Open location") }
            }
            TextButton(onClick = onCall) { Text("Call patient") }
            TextButton(onClick = onSeen) { Text("Mark seen") }
        }
    }
}

/** One phrase for the safe-zone standing, used in the strip, tile and dialog. */
private fun zone(zone: SafeZone?, location: PatientLocation?): String = when {
    zone == null -> "Not set"
    location == null || location.isStale -> "Checking"
    location.status == SafeZoneStatus.INSIDE -> "Inside zone"
    location.status == SafeZoneStatus.OUTSIDE -> "Outside zone"
    else -> "Checking"
}

private fun zoneIsOutside(zone: SafeZone?, location: PatientLocation?): Boolean =
    zone != null && location != null && !location.isStale &&
        location.status == SafeZoneStatus.OUTSIDE

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun CaregiverDashboardPreview() {
    SahaayaTheme {
        CaregiverDashboardContent(
            state = CaregiverDashboardUiState(
                user = User(uid = "c1", email = "r@x.in", displayName = "Rahul", role = UserRole.CAREGIVER),
                patients = listOf(
                    Pairing(
                        id = "p1_c1",
                        patientId = "p1",
                        caregiverId = "c1",
                        patientName = "Asha",
                        caregiverName = "Rahul",
                        status = PairingStatus.ACTIVE,
                    ),
                ),
                isLoading = false,
            ),
            patientLocation = null,
            safeZone = null,
            onOpenProfile = {},
            onAddPatient = {},
            onOpenPatient = {},
            tracking = TrackingState.INACTIVE,
            onOpenLiveTracking = { _, _ -> },
            onStartTracking = {},
            onStopTracking = {},
            onOpenSafeZone = {},
            onOpenPatientReminders = {},
            onOpenTimeline = {},
            onUnlinkPatient = {},
            onSignOut = {},
        )
    }
}
