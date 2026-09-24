package com.sahaaya.domain.usecase.monitoring

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.usecase.event.CancelEventUseCase
import com.sahaaya.domain.usecase.event.RaiseEventUseCase
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Records a possible fall once the patient has answered - or failed to answer -
 * the "Are you okay?" prompt.
 *
 * Nothing is written while the prompt is open: a patient who taps "I'm okay"
 * produces no event and no notification at all. This trades away one thing,
 * stated plainly: if the phone is destroyed during the response window, the
 * fall is not reported. The alternative - write first, cancel later - was
 * rejected because it notified caregivers of falls the patient had already
 * dismissed.
 *
 * Uses the existing event pipeline, so the fall lands on the same timeline, with
 * the same security rules, as every other alert. [RaiseEventUseCase] attaches a
 * fresh GPS fix taken now, at the moment of reporting.
 */
class ReportFallUseCase @Inject constructor(
    private val raiseEvent: RaiseEventUseCase,
) {

    data class Params(
        /** Peak acceleration at impact, m/s². */
        val impactMagnitude: Float,
        /** Degrees of orientation change across the impact. */
        val orientationChangeDegrees: Float,
        val detectedAtEpochMillis: Long = System.currentTimeMillis(),
        val confirmation: FallConfirmation = FallConfirmation.NO_RESPONSE,
        /** Developer test path. Labelled on the event so nobody mistakes it. */
        val simulated: Boolean = false,
    )

    suspend operator fun invoke(params: Params): Outcome<HealthEvent> {
        val gForce = params.impactMagnitude / EARTH_GRAVITY
        return raiseEvent(
            RaiseEventUseCase.Params(
                type = EventType.FALL,
                summary = buildString {
                    if (params.simulated) append("[TEST] ")
                    append("Possible fall detected - ")
                    append(params.confirmation.displayName.lowercase())
                },
                details = buildMap {
                    put("Status", params.confirmation.displayName)
                    put(CONFIRMATION_KEY, params.confirmation.storageKey)
                    put("Impact force", "${(gForce * 10).roundToInt() / 10.0} g")
                    put("Orientation change", "${params.orientationChangeDegrees.roundToInt()}°")
                    put("Location", "Taken when the alert was sent")
                    if (params.simulated) put("Source", "Simulated test event")
                },
                occurredAtEpochMillis = params.detectedAtEpochMillis,
            ),
        )
    }

    companion object {
        private const val EARTH_GRAVITY = 9.81f

        /** Machine-readable confirmation status, stored in the event details. */
        const val CONFIRMATION_KEY = "confirmationStatus"
    }
}

/**
 * The patient presses "I'm fine" during the countdown.
 *
 * Thin wrapper over [CancelEventUseCase] so the fall flow reads in its own
 * vocabulary at the call site rather than as a generic status update.
 */
class CancelFallAlertUseCase @Inject constructor(
    private val cancelEvent: CancelEventUseCase,
) {
    suspend operator fun invoke(eventId: String): Outcome<Unit> = cancelEvent(eventId)
}
