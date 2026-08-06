package com.sahaaya.feature.monitoring.sos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import kotlinx.coroutines.delay

/**
 * The emergency button.
 *
 * Long press, not tap. It sits on the patient's home screen where a phone in a
 * pocket will brush against it, and a tap-to-send button would produce a stream
 * of false emergencies that teaches caregivers to ignore it.
 *
 * Three seconds, with a filling ring and haptics each second so the patient can
 * feel the progress without looking. Releasing early cancels silently.
 *
 * The accessibility path is different on purpose: TalkBack users get a single
 * activate action rather than a timed press, because holding a gesture for
 * three seconds is not something a screen-reader user can reliably do.
 */
@Composable
fun SosButton(
    onTriggered: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSending: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    var elapsedMillis by remember { mutableStateOf(0L) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(pressed) {
        if (!pressed) {
            elapsedMillis = 0L
            return@LaunchedEffect
        }
        var lastTickSecond = 0
        while (elapsedMillis < HOLD_DURATION_MILLIS) {
            delay(TICK_MILLIS)
            elapsedMillis += TICK_MILLIS
            val second = (elapsedMillis / 1_000L).toInt()
            if (second > lastTickSecond) {
                lastTickSecond = second
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        pressed = false
        onTriggered()
    }

    val progress by animateFloatAsState(
        targetValue = if (pressed) {
            (elapsedMillis.toFloat() / HOLD_DURATION_MILLIS).coerceIn(0f, 1f)
        } else {
            0f
        },
        animationSpec = tween(durationMillis = 100),
        label = "sosProgress",
    )

    val secondsLeft = ((HOLD_DURATION_MILLIS - elapsedMillis) / 1_000L).toInt() + 1

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (pressed) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(BUTTON_SIZE_DP.dp + 16.dp),
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.errorContainer,
                )
            }

            Box(
                modifier = Modifier
                    .size(BUTTON_SIZE_DP.dp)
                    .background(
                        color = if (enabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    )
                    .pointerInput(enabled, isSending) {
                        if (!enabled || isSending) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                // Suspends until the finger lifts; if that
                                // happens before the timer completes, the
                                // LaunchedEffect above is cancelled and nothing
                                // is sent.
                                tryAwaitRelease()
                                pressed = false
                            },
                        )
                    }
                    .semantics {
                        contentDescription = "Emergency SOS. Activate to alert your caregiver."
                        onClick(label = "Send emergency alert") {
                            onTriggered()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isSending -> CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 4.dp,
                    )

                    pressed -> Text(
                        text = secondsLeft.coerceAtLeast(1).toString(),
                        fontSize = 56.sp,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onError,
                    )

                    else -> Text(
                        text = "SOS",
                        fontSize = 40.sp,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }

        Text(
            text = when {
                isSending -> "Sending your alert…"
                pressed -> "Keep holding…"
                else -> "Press and hold for 3 seconds to call for help"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val BUTTON_SIZE_DP = 180
private const val HOLD_DURATION_MILLIS = 3_000L
private const val TICK_MILLIS = 50L

@Preview(showBackground = true)
@Composable
private fun SosButtonPreview() {
    SahaayaTheme {
        SosButton(onTriggered = {})
    }
}
