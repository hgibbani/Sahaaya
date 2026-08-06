package com.sahaaya.feature.monitoring.fall

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.Banner
import com.sahaaya.common.components.BannerTone
import com.sahaaya.common.components.DestructiveButton
import com.sahaaya.common.components.PrimaryButton
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.MonitoringSettings

@Composable
fun FallCountdownScreen(
    eventId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FallCountdownViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(eventId) { viewModel.start(eventId) }

    FallCountdownContent(
        state = state,
        onCancel = viewModel::cancel,
        onConfirmNow = viewModel::confirmNow,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/**
 * The screen a person sees while lying on the floor.
 *
 * Every choice here is about that situation: one enormous number, two buttons
 * the size of a palm, no navigation, no back arrow, and text that says exactly
 * what is about to happen. The cancel button is the *secondary* action - if the
 * patient does nothing at all, help is called.
 */
@Composable
private fun FallCountdownContent(
    state: FallCountdownUiState,
    onCancel: () -> Unit,
    onConfirmNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                when (state.outcome) {
                    CountdownOutcome.COUNTING -> MaterialTheme.colorScheme.errorContainer
                    CountdownOutcome.CANCELLED -> MaterialTheme.colorScheme.surface
                    CountdownOutcome.CONFIRMED -> MaterialTheme.colorScheme.errorContainer
                },
            )
            .padding(Dimens.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        ) {
            when (state.outcome) {
                CountdownOutcome.COUNTING -> Counting(state, onCancel, onConfirmNow)
                CountdownOutcome.CANCELLED -> Cancelled(state, onDismiss)
                CountdownOutcome.CONFIRMED -> Confirmed(onDismiss)
            }
        }
    }
}

@Composable
private fun Counting(
    state: FallCountdownUiState,
    onCancel: () -> Unit,
    onConfirmNow: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = state.secondsRemaining.toFloat() /
            MonitoringSettings.FALL_CONFIRMATION_SECONDS,
        animationSpec = tween(durationMillis = 900),
        label = "countdown",
    )

    Text(
        text = "Are you alright?",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        textAlign = TextAlign.Center,
    )

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(180.dp),
            strokeWidth = 12.dp,
            color = MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f),
        )
        Text(
            text = state.secondsRemaining.toString(),
            fontSize = 88.sp,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            // Announced on every tick so a patient who cannot see the screen
            // still hears how long they have.
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = "${state.secondsRemaining} seconds until help is called"
            },
        )
    }

    Text(
        text = "We think you may have fallen. If you do not answer, your caregiver " +
            "will be told and sent your location.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onErrorContainer,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(Dimens.SpaceSm))

    // "I'm fine" is deliberately the quieter of the two. Doing nothing must be
    // the safe default.
    PrimaryButton(text = "I'm fine - cancel", onClick = onCancel)
    DestructiveButton(text = "I need help now", onClick = onConfirmNow)
}

@Composable
private fun Cancelled(state: FallCountdownUiState, onDismiss: () -> Unit) {
    Text(
        text = "Alert cancelled",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "Nobody has been called. Sahaaya is still watching over you.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (state.errorMessage != null) {
        Banner(message = state.errorMessage, tone = BannerTone.Error)
    }
    Spacer(modifier = Modifier.height(Dimens.SpaceSm))
    PrimaryButton(text = "Close", onClick = onDismiss)
}

@Composable
private fun Confirmed(onDismiss: () -> Unit) {
    Text(
        text = "Help is on the way",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "Your caregiver has been alerted and sent your location. " +
            "Stay where you are if you can.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onErrorContainer,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(Dimens.SpaceSm))
    PrimaryButton(text = "Close", onClick = onDismiss)
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun FallCountdownPreview() {
    SahaayaTheme {
        FallCountdownContent(
            state = FallCountdownUiState(secondsRemaining = 4),
            onCancel = {},
            onConfirmNow = {},
            onDismiss = {},
        )
    }
}
