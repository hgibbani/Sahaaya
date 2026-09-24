package com.sahaaya.feature.dashboard.timeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.EmptyState
import com.sahaaya.common.components.LoadingState
import com.sahaaya.common.components.SahaayaCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.StatusChip
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.EventStatus
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.HealthEvent

@Composable
fun TimelineScreen(
    onNavigateBack: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TimelineContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onOpenEvent = onOpenEvent,
        onFilter = viewModel::setFilter,
        onClearAll = viewModel::clearAll,
        modifier = modifier,
    )
}

@Composable
private fun TimelineContent(
    state: TimelineUiState,
    onNavigateBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onFilter: (EventType?) -> Unit,
    onClearAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(label = "Loading alerts")
        return
    }

    // Confirmation is not optional here. Clearing 61 alerts is one tap that
    // cannot be undone from this screen, and a stray tap next to the filter
    // chips should not silently mark a genuine emergency as seen.
    var confirmingClearAll by remember { mutableStateOf(false) }

    if (confirmingClearAll) {
        AlertDialog(
            onDismissRequest = { confirmingClearAll = false },
            title = { Text("Clear all alerts?") },
            text = {
                Text(
                    "This marks all ${state.clearableEventIds.size} outstanding " +
                        "alert(s) as seen. They stay in your history with their " +
                        "time and details - nothing is deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClearAll = false
                        onClearAll()
                    },
                ) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearAll = false }) { Text("Cancel") }
            },
        )
    }

    SahaayaScreen(
        title = "Alerts",
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        actions = {
            if (state.canClearAll) {
                TextButton(onClick = { confirmingClearAll = true }) { Text("Clear all") }
            } else if (state.isClearingAll) {
                Text(
                    text = "Clearing…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        if (state.errorMessage != null) Banner(message = state.errorMessage)

        // The single most important line on the screen: is anything outstanding?
        if (state.unresolvedCount == 0) {
            Banner(
                message = "Nothing needs your attention. Everything has been seen.",
                tone = BannerTone.Success,
            )
        } else {
            Banner(
                message = if (state.hasCriticalUnresolved) {
                    "${state.unresolvedCount} alert(s) need your attention, " +
                        "including urgent ones."
                } else {
                    "${state.unresolvedCount} alert(s) need your attention."
                },
                tone = if (state.hasCriticalUnresolved) BannerTone.Error else BannerTone.Info,
            )
        }

        SummaryCards(
            counts = state.counts,
            selected = state.filter,
            onSelect = onFilter,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXs))

        Text(
            text = if (state.filter == null) {
                "Everything, newest first"
            } else {
                "${EventVisuals.shortLabel(state.filter)}, newest first"
            },
            style = MaterialTheme.typography.titleMedium,
        )

        if (state.visibleEvents.isEmpty()) {
            EmptyState(
                title = if (state.filter == null) "No alerts yet" else "None of these",
                description = if (state.filter == null) {
                    "When something happens to a patient you are linked to, it will " +
                        "appear here straight away."
                } else {
                    "Nothing of this kind has been recorded."
                },
                actionLabel = if (state.filter != null) "Show everything" else null,
                onAction = if (state.filter != null) {
                    { onFilter(null) }
                } else {
                    null
                },
            )
        } else {
            state.visibleEvents.forEach { event ->
                TimelineRow(event = event, onClick = { onOpenEvent(event.id) })
            }
        }
    }
}

/**
 * The counts row.
 *
 * Counts are of *unresolved* events only. A caregiver does not need to be told
 * about three falls they have already dealt with; they need to know what is
 * still open. Tapping a card filters the list below.
 */
@Composable
private fun SummaryCards(
    counts: Map<EventType, Int>,
    selected: EventType?,
    onSelect: (EventType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        EventType.entries.forEach { type ->
            val count = counts[type] ?: 0
            SummaryCard(
                type = type,
                count = count,
                isSelected = selected == type,
                onClick = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun SummaryCard(
    type: EventType,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (count > 0) {
        EventVisuals.containerColor(type)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val content = if (count > 0) {
        EventVisuals.contentColor(type)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .width(116.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(Dimens.SpaceMd)
            .clearAndSetSemantics {
                contentDescription = "${EventVisuals.shortLabel(type)}: " +
                    if (count == 0) "none outstanding" else "$count outstanding"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Icon(
            imageVector = EventVisuals.icon(type),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = content,
        )
        Text(
            text = EventVisuals.shortLabel(type),
            style = MaterialTheme.typography.bodySmall,
            color = content,
            textAlign = TextAlign.Center,
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(content, CircleShape),
            )
        }
    }
}

@Composable
private fun TimelineRow(
    event: HealthEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .animateContentSize()
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(event.type.displayName)
                    append(". ")
                    append(event.patientName.ifBlank { "Patient" })
                    append(". ")
                    append(formatRelativeTime(event.occurredAtEpochMillis))
                    append(". ")
                    append(
                        if (event.isUnresolved) {
                            "Not yet acknowledged."
                        } else {
                            event.status.displayName
                        },
                    )
                }
            },
        containerColor = if (event.isUnresolved) {
            EventVisuals.containerColor(event.type)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = EventVisuals.icon(event.type),
                contentDescription = null,
                tint = if (event.isUnresolved) {
                    EventVisuals.contentColor(event.type)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(32.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.type.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = event.patientName.ifBlank { "Patient" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatRelativeTime(event.occurredAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (event.status) {
                EventStatus.NEW -> StatusChip(
                    label = "New",
                    tone = if (event.isCritical) BannerTone.Error else BannerTone.Info,
                )
                EventStatus.ACKNOWLEDGED -> StatusChip(
                    label = "Seen",
                    tone = BannerTone.Success,
                )
                EventStatus.CANCELLED -> StatusChip(
                    label = "Cancelled",
                    tone = BannerTone.Info,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun TimelinePreview() {
    SahaayaTheme {
        TimelineContent(
            state = TimelineUiState(
                events = listOf(
                    HealthEvent(
                        id = "e1",
                        patientId = "p1",
                        patientName = "Ibbani H G",
                        type = EventType.FALL,
                        occurredAtEpochMillis = System.currentTimeMillis() - 600_000,
                        summary = "Possible fall detected",
                    ),
                    HealthEvent(
                        id = "e2",
                        patientId = "p1",
                        patientName = "Ibbani H G",
                        type = EventType.MEDICATION_MISSED,
                        status = EventStatus.ACKNOWLEDGED,
                        occurredAtEpochMillis = System.currentTimeMillis() - 7_200_000,
                        summary = "Donepezil was not taken",
                    ),
                ),
                counts = mapOf(EventType.FALL to 1),
                isLoading = false,
            ),
            onNavigateBack = {},
            onOpenEvent = {},
            onFilter = {},
        )
    }
}
