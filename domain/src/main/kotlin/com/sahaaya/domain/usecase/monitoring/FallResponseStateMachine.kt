package com.sahaaya.domain.usecase.monitoring

/** What the patient told us - or did not - after a possible fall. */
enum class FallConfirmation(val storageKey: String, val displayName: String) {
    /** Patient pressed "Need help". */
    HELP_REQUESTED("help_requested", "Help requested by patient"),

    /** Nobody answered before the response window closed. */
    NO_RESPONSE("no_response", "No response from patient"),
}

/** What the caller must do after a state-machine transition. */
sealed interface FallResponseAction {
    /** Show the "Are you okay?" screen and start the response timer. */
    data object Prompt : FallResponseAction

    /** Patient is fine. Close the prompt, tell nobody. */
    data object Dismiss : FallResponseAction

    /** Write a fall event and notify the caregiver. */
    data class Report(val confirmation: FallConfirmation) : FallResponseAction

    /** Nothing to do - duplicate or late input. */
    data object Ignore : FallResponseAction
}

/**
 * The patient-response half of fall detection, with no Android in it.
 *
 * A possible fall never reaches a caregiver directly. It opens a window in which
 * the patient is asked "Are you okay?":
 *
 * ```
 * IDLE ──possible fall──▶ AWAITING_RESPONSE ──"I'm okay"────▶ IDLE   (nobody told)
 *                               │           ──"Need help"───▶ IDLE   (report HELP_REQUESTED)
 *                               └───────────── timeout ─────▶ IDLE   (report NO_RESPONSE)
 * ```
 *
 * Exactly one outcome per prompt. A second possible fall while a prompt is open
 * is ignored rather than stacking a second screen and a second event; a tap that
 * arrives after the timeout already reported is ignored rather than cancelling
 * an alert the caregiver has already received.
 */
class FallResponseStateMachine {

    enum class State { IDLE, AWAITING_RESPONSE }

    var state: State = State.IDLE
        private set

    fun onPossibleFall(): FallResponseAction =
        if (state == State.AWAITING_RESPONSE) {
            FallResponseAction.Ignore
        } else {
            state = State.AWAITING_RESPONSE
            FallResponseAction.Prompt
        }

    fun onImOkay(): FallResponseAction = resolve(FallResponseAction.Dismiss)

    fun onNeedHelp(): FallResponseAction =
        resolve(FallResponseAction.Report(FallConfirmation.HELP_REQUESTED))

    fun onTimeout(): FallResponseAction =
        resolve(FallResponseAction.Report(FallConfirmation.NO_RESPONSE))

    private fun resolve(action: FallResponseAction): FallResponseAction {
        if (state != State.AWAITING_RESPONSE) return FallResponseAction.Ignore
        state = State.IDLE
        return action
    }
}
