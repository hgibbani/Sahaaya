package com.sahaaya.feature.dashboard.patient

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
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.Role as UserRole
import com.sahaaya.domain.model.SafeZoneStatus
import com.sahaaya.domain.model.TrackingState
import com.sahaaya.domain.model.User
import com.sahaaya.feature.dashboard.ui.Palette
import com.sahaaya.feature.monitoring.sos.SosButton

@Composable
fun PatientDashboardScreen(
    onOpenProfile: () -> Unit,
    onOpenEmergencyContacts: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenMedications: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Kept for navigation compatibility; the developer screen was removed. */
    @Suppress("UNUSED_PARAMETER") onOpenDemoMode: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenReminders: () -> Unit = {},
    onOpenTrackingStatus: () -> Unit = {},
    viewModel: PatientDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val safeZoneStatus by viewModel.safeZoneStatus.collectAsStateWithLifecycle()
    val ownLocation by viewModel.ownLocation.collectAsStateWithLifecycle()
    val tracking by viewModel.trackingState.collectAsStateWithLifecycle()

    PatientDashboardContent(
        state = state,
        safeZoneStatus = safeZoneStatus,
        ownLocation = ownLocation,
        tracking = tracking,
        onOpenTrackingStatus = onOpenTrackingStatus,
        onOpenProfile = onOpenProfile,
        onOpenEmergencyContacts = onOpenEmergencyContacts,
        onOpenPairing = onOpenPairing,
        onOpenMedications = onOpenMedications,
        onOpenReminders = onOpenReminders,
        onOpenSettings = onOpenSettings,
        onSos = viewModel::triggerSos,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

/**
 * The patient's home screen.
 *
 * Laid out to one rule: the way to get help is the largest, most obvious thing
 * on the screen, and everything else is a big, clearly labelled tile. Someone
 * with moderate dementia should be able to look at this and find SOS without
 * reading anything, and find their medicines by the picture alone.
 */
@Composable
private fun PatientDashboardContent(
    state: PatientDashboardUiState,
    safeZoneStatus: SafeZoneStatus,
    ownLocation: PatientLocation?,
    tracking: TrackingState,
    onOpenTrackingStatus: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenEmergencyContacts: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenMedications: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    onSos: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(label = "Loading your details")
        return
    }

    val context = LocalContext.current
    var callNotice by remember { mutableStateOf<String?>(null) }
    var showingLocation by remember { mutableStateOf(false) }

    if (showingLocation) {
        LocationDialog(
            location = ownLocation,
            status = safeZoneStatus,
            safeZoneSet = state.settings?.geofenceEnabled == true && state.settings.safeZone != null,
            onDismiss = { showingLocation = false },
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
            name = state.user?.firstName ?: state.user?.displayName ?: "",
            onOpenProfile = onOpenProfile,
            onSignOut = onSignOut,
        )

        if (state.errorMessage != null) Banner(message = state.errorMessage)
        if (state.sosSentMessage != null) {
            Banner(message = state.sosSentMessage, tone = BannerTone.Success)
        }

        // Above SOS, because a patient who has been told they are being watched
        // should see that on the first screen rather than having to find it.
        // Informational only - there is no control here to switch it off.
        if (tracking.active) {
            TrackingActiveCard(onClick = onOpenTrackingStatus)
        }

        SosButton(
            onTriggered = onSos,
            isSending = state.isSendingSos,
            enabled = !state.hasNoCaregiver,
        )

        if (state.hasNoCaregiver) {
            Banner(
                message = "Link a caregiver first - there is nobody to send an " +
                    "emergency alert to yet. Tap \"Link to Caregiver\" below.",
                tone = BannerTone.Info,
            )
        }

        // ACTION_DIAL, not ACTION_CALL: the number is placed in the dialler and
        // Android asks for the final press, so the app can never silently start
        // a call - and it needs no CALL_PHONE permission.
        CallCaregiverCard(
            caregiverName = state.callableCaregiver?.caregiverName,
            onClick = {
                val callable = state.callableCaregiver
                if (callable == null) {
                    callNotice = if (state.hasNoCaregiver) {
                        "Please link a caregiver first."
                    } else {
                        "Your caregiver has not saved a phone number yet. Ask " +
                            "them to add one, then link again."
                    }
                } else {
                    callNotice = null
                    val intent = Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:${callable.caregiverPhone}"),
                    )
                    runCatching { context.startActivity(intent) }
                        .onFailure { callNotice = "No dialler app is available." }
                }
            },
        )

        val notice = callNotice
        if (notice != null) Banner(message = notice, tone = BannerTone.Info)

        FeatureRow {
            FeatureTile(
                label = "Link to Caregiver",
                icon = Icons.Filled.Link,
                tint = Palette.LinkIcon,
                background = Palette.LinkBg,
                circle = Palette.LinkCircle,
                onClick = onOpenPairing,
            )
            FeatureTile(
                label = "Location",
                icon = Icons.Filled.LocationOn,
                tint = Palette.LocationIcon,
                background = Palette.LocationBg,
                circle = Palette.LocationCircle,
                onClick = { showingLocation = true },
            )
            FeatureTile(
                label = "Medicines",
                icon = Icons.Filled.Medication,
                tint = Palette.MedicineIcon,
                background = Palette.MedicineBg,
                circle = Palette.MedicineCircle,
                badge = state.pendingDoseCount.takeIf { it > 0 }?.let { "$it due" },
                onClick = onOpenMedications,
            )
        }

        FeatureRow {
            FeatureTile(
                label = "Reminders",
                icon = Icons.Filled.EventAvailable,
                tint = Palette.ReminderIcon,
                background = Palette.ReminderBg,
                circle = Palette.ReminderCircle,
                onClick = onOpenReminders,
            )
            FeatureTile(
                label = "Emergency Contacts",
                icon = Icons.Filled.CrisisAlert,
                tint = Palette.EmergencyIcon,
                background = Palette.EmergencyBg,
                circle = Palette.EmergencyCircle,
                onClick = onOpenEmergencyContacts,
            )
            FeatureTile(
                label = "Settings",
                icon = Icons.Filled.Settings,
                tint = Palette.SettingsIcon,
                background = Palette.SettingsBg,
                circle = Palette.SettingsCircle,
                onClick = onOpenSettings,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun Header(
    name: String,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // The avatar and name together open the profile, as one large target.
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
                        .size(88.dp)
                        .background(Palette.Avatar, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = name.ifBlank { "Hello" },
                    color = Palette.Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Text(
                    text = "Patient Profile",
                    color = Palette.Muted,
                    fontSize = 17.sp,
                )
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
 * "Tracking Active" on the patient's own dashboard.
 *
 * Tappable, but only to open the explanation screen - there is no stop control
 * behind it. Someone being tracked is entitled to know it is happening and to
 * read why, even though this app does not let them end it; showing nothing at
 * all would be covert monitoring, which is a different and much worse product.
 */
@Composable
private fun TrackingActiveCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.OnlineBg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(20.dp).background(Palette.OnlineDot, CircleShape),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tracking Active",
                color = Palette.Ink,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Your caregiver is monitoring your location for your safety.",
                color = Palette.Muted,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun CallCaregiverCard(caregiverName: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Palette.Border, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Palette.CallCircle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = null,
                tint = Palette.CallIcon,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Call Caregiver",
                color = Palette.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!caregiverName.isNullOrBlank()) {
                Text(text = caregiverName, color = Palette.Muted, fontSize = 16.sp)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Palette.Ink,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * A row of equal tiles. IntrinsicSize.Min makes every tile in the row as tall as
 * the tallest, so a two-line label ("Emergency Contacts") does not leave its
 * neighbours shorter - and nothing is clipped at large font sizes, which older
 * users often have switched on.
 */
@Composable
private fun FeatureRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun RowScope.FeatureTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    background: Color,
    circle: Color,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = 132.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(role = Role.Button, onClickLabel = "Open $label", onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(circle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            color = Palette.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        if (badge != null) {
            Text(
                text = badge,
                color = tint,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Where the phone last reported, and how that compares with the safe zone.
 *
 * Reads the same `patients/{uid}` document the caregiver's card reads, so the
 * patient sees exactly what their caregiver sees - not a separate reading taken
 * on this screen that could disagree with it.
 */
@Composable
private fun LocationDialog(
    location: PatientLocation?,
    status: SafeZoneStatus,
    safeZoneSet: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        !safeZoneSet -> "Your caregiver has not set a safe area yet."
                        location == null || location.isStale ->
                            "Checking where you are. This needs location switched on."
                        status == SafeZoneStatus.INSIDE ->
                            "You are inside the area your caregiver set."
                        status == SafeZoneStatus.OUTSIDE ->
                            "You are outside the area your caregiver set. They have been told."
                        else -> "Checking where you are."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (location != null) {
                    Text(
                        text = "%.5f, %.5f".format(
                            location.point.latitude,
                            location.point.longitude,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Your location is shared with your caregiver.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Your location will appear once the phone has a GPS fix.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (location != null) {
                TextButton(
                    onClick = {
                        val p = location.point
                        val uri = Uri.parse(
                            "geo:${p.latitude},${p.longitude}?q=${p.latitude},${p.longitude}",
                        )
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    },
                ) { Text("Open in Maps") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun PatientDashboardPreview() {
    SahaayaTheme {
        PatientDashboardContent(
            state = PatientDashboardUiState(
                user = User(
                    uid = "u1",
                    email = "asha@example.com",
                    displayName = "Asha Nair",
                    role = UserRole.PATIENT,
                ),
                profile = PatientProfile(uid = "u1"),
                isLoading = false,
            ),
            safeZoneStatus = SafeZoneStatus.UNKNOWN,
            ownLocation = null,
            onOpenProfile = {},
            onOpenEmergencyContacts = {},
            onOpenPairing = {},
            onOpenMedications = {},
            tracking = TrackingState.INACTIVE,
            onOpenTrackingStatus = {},
            onOpenReminders = {},
            onOpenSettings = {},
            onSos = {},
            onSignOut = {},
        )
    }
}
