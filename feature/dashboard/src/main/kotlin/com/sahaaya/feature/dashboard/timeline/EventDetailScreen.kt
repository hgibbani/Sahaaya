package com.sahaaya.feature.dashboard.timeline

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.DetailRow
import com.sahaaya.common.components.IconBadge
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.SecondaryButton
import com.sahaaya.common.components.StatusChip
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.EventStatus
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.HealthEvent

@Composable
fun EventDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    EventDetailContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onAcknowledge = viewModel::acknowledge,
        onOpenMap = { point ->
            // A geo: URI so the caregiver lands in whatever maps app they use,
            // with the pin already dropped. The label carries the patient's name
            // so the pin is identifiable once they are driving.
            val label = Uri.encode(state.event?.patientName?.ifBlank { "Patient" } ?: "Patient")
            val uri = Uri.parse(
                "geo:${point.latitude},${point.longitude}?q=" +
                    "${point.latitude},${point.longitude}($label)",
            )
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        },
        modifier = modifier,
    )
}

@Composable
private fun EventDetailContent(
    state: EventDetailUiState,
    onNavigateBack: () -> Unit,
    onAcknowledge: () -> Unit,
    onOpenMap: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = state.event
    if (state.isLoading) {
        LoadingState(label = "Loading alert")
        return
    }
    if (event == null) {
        SahaayaScreen(title = "Alert", onNavigateBack = onNavigateBack, modifier = modifier) {
            Banner(message = state.errorMessage ?: "This alert is no longer available.")
        }
        return
    }

    SahaayaScreen(
        title = event.type.displayName,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        if (state.errorMessage != null) Banner(message = state.errorMessage)

        SahaayaCard(containerColor = EventVisuals.containerColor(event.type)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    icon = EventVisuals.icon(event.type),
                    tint = EventVisuals.contentColor(event.type),
                    background = MaterialTheme.colorScheme.surface,
                    size = Dimens.IconBadgeLarge,
                )
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.summary.ifBlank { event.type.displayName },
                        style = MaterialTheme.typography.titleLarge,
                        color = EventVisuals.contentColor(event.type),
                    )
                    Text(
                        text = formatRelativeTime(event.occurredAtEpochMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = EventVisuals.contentColor(event.type),
                    )
                }
            }
        }

        SahaayaCard {
            DetailRow(label = "Patient", value = event.patientName.ifBlank { "Unknown" })
            DetailRow(label = "When", value = formatAbsoluteTime(event.occurredAtEpochMillis))
            event.details.forEach { (label, value) ->
                DetailRow(label = label, value = value)
            }
        }

        // Location is the single most actionable thing on this screen for a
        // fall, an SOS or a geofence exit, so it gets its own card and a button
        // that hands off to a maps app rather than an embedded map the
        // caregiver cannot navigate with.
        val location = event.location
        if (location != null) {
            SahaayaCard {
                Text(text = "Where", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "%.5f, %.5f".format(location.latitude, location.longitude),
                    style = MaterialTheme.typography.bodyLarge,
                )
                location.accuracyMetres?.let { accuracy ->
                    Text(
                        text = "Accurate to about ${accuracy.toInt()} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SecondaryButton(
                    text = "Open in maps",
                    onClick = { onOpenMap(location) },
                    leadingIcon = Icons.Filled.Map,
                )
            }
        } else {
            Banner(
                message = "No location was recorded for this alert. The phone may have " +
                    "had location turned off, or been indoors without a fix.",
                tone = BannerTone.Info,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        when (event.status) {
            EventStatus.NEW -> {
                Text(
                    text = "Acknowledging tells other caregivers that you are dealing " +
                        "with this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(
                    text = "I am dealing with this",
                    onClick = onAcknowledge,
                    loading = state.isAcknowledging,
                )
            }

            EventStatus.ACKNOWLEDGED -> {
                StatusChip(label = "Acknowledged", tone = BannerTone.Success)
                Text(
                    text = "Someone has taken responsibility for this alert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            EventStatus.CANCELLED -> {
                StatusChip(label = "Cancelled by patient", tone = BannerTone.Info)
                Text(
                    text = "The patient cancelled this during the countdown - they said " +
                        "they were alright. It is kept here because a pattern of " +
                        "cancelled falls is worth noticing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun EventDetailPreview() {
    SahaayaTheme {
        EventDetailContent(
            state = EventDetailUiState(
                event = HealthEvent(
                    id = "e1",
                    patientId = "p1",
                    patientName = "Ibbani H G",
                    type = EventType.FALL,
                    occurredAtEpochMillis = System.currentTimeMillis() - 600_000,
                    location = GeoPoint(13.1836, 74.9346, 12f),
                    summary = "Possible fall detected",
                    details = mapOf(
                        "Impact force" to "2.9 g",
                        "Orientation change" to "78°",
                    ),
                ),
                isLoading = false,
            ),
            onNavigateBack = {},
            onAcknowledge = {},
            onOpenMap = {},
        )
    }
}
