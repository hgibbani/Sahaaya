package com.sahaaya.domain.usecase.monitoring

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.usecase.event.CancelEventUseCase
import com.sahaaya.domain.usecase.event.RaiseEventUseCase
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Turns a detected fall into an alert, after the patient has had a chance to
 * cancel it.
 *
 * The order matters and is the opposite of what looks natural. The event is
 * written to Firestore **first**, then the countdown runs, then the event is
 * cancelled if the patient says they are fine.
 *
 * Writing first means a phone that dies on impact, or is thrown out of reach,
 * has already reported the fall. The alternative - wait five seconds, then
 * write - loses exactly the falls that matter most, because the hardest falls
 * are the ones where nobody reaches the phone afterwards.
 *
 * The cost is that a cancelled fall leaves a CANCELLED document behind. That is
 * a feature: a pattern of cancelled falls means the sensitivity is wrong, or
 * that the person is stumbling often without injury, and both are worth seeing.
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
    )

    suspend operator fun invoke(params: Params): Outcome<HealthEvent> {
        val gForce = params.impactMagnitude / EARTH_GRAVITY
        return raiseEvent(
            RaiseEventUseCase.Params(
                type = EventType.FALL,
                summary = "Possible fall detected",
                details = mapOf(
                    "Impact force" to "${(gForce * 10).roundToInt() / 10.0} g",
                    "Orientation change" to "${params.orientationChangeDegrees.roundToInt()}°",
                ),
                occurredAtEpochMillis = params.detectedAtEpochMillis,
            ),
        )
    }

    private companion object {
        const val EARTH_GRAVITY = 9.81f
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
