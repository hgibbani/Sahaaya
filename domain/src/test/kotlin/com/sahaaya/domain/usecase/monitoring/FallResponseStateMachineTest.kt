package com.sahaaya.domain.usecase.monitoring

import com.sahaaya.domain.model.AlertChannel
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.HealthEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallResponseStateMachineTest {

    private val machine = FallResponseStateMachine()

    @Test
    fun `a possible fall prompts the patient and notifies nobody yet`() {
        assertEquals(FallResponseAction.Prompt, machine.onPossibleFall())
        assertEquals(FallResponseStateMachine.State.AWAITING_RESPONSE, machine.state)
    }

    @Test
    fun `I am okay cancels without reporting`() {
        machine.onPossibleFall()
        assertEquals(FallResponseAction.Dismiss, machine.onImOkay())
        assertEquals(FallResponseStateMachine.State.IDLE, machine.state)
        // A late timeout must not report a fall the patient dismissed.
        assertEquals(FallResponseAction.Ignore, machine.onTimeout())
    }

    @Test
    fun `need help reports a help-requested fall`() {
        machine.onPossibleFall()
        assertEquals(
            FallResponseAction.Report(FallConfirmation.HELP_REQUESTED),
            machine.onNeedHelp(),
        )
    }

    @Test
    fun `no response reports a no-response fall`() {
        machine.onPossibleFall()
        assertEquals(
            FallResponseAction.Report(FallConfirmation.NO_RESPONSE),
            machine.onTimeout(),
        )
        // A tap after the timeout already reported is ignored.
        assertEquals(FallResponseAction.Ignore, machine.onImOkay())
    }

    @Test
    fun `a second possible fall while the prompt is open is ignored`() {
        machine.onPossibleFall()
        assertEquals(FallResponseAction.Ignore, machine.onPossibleFall())
        machine.onNeedHelp()
        // Exactly one outcome per prompt.
        assertEquals(FallResponseAction.Ignore, machine.onNeedHelp())
    }

    @Test
    fun `a new fall after resolution prompts again`() {
        machine.onPossibleFall()
        machine.onImOkay()
        assertEquals(FallResponseAction.Prompt, machine.onPossibleFall())
    }

    // --- Alarm policy ----------------------------------------------------

    private fun event(type: EventType) = HealthEvent(
        id = "e",
        patientId = "p",
        patientName = "Asha",
        type = type,
        occurredAtEpochMillis = 0L,
    )

    @Test
    fun `only an explicit SOS uses the loud alarm`() {
        assertTrue(event(EventType.SOS).usesEmergencyAlarm)
        assertEquals(AlertChannel.ALARM, event(EventType.SOS).alertChannel)
    }

    @Test
    fun `a fall event never uses the loud alarm`() {
        assertFalse(event(EventType.FALL).usesEmergencyAlarm)
        assertEquals(AlertChannel.SAFETY, event(EventType.FALL).alertChannel)
    }

    @Test
    fun `routine events use neither alarm nor safety`() {
        assertFalse(event(EventType.GEOFENCE_EXIT).usesEmergencyAlarm)
        assertEquals(AlertChannel.UPDATE, event(EventType.MEDICATION_MISSED).alertChannel)
        assertEquals(AlertChannel.UPDATE, event(EventType.INACTIVITY).alertChannel)
    }
}
