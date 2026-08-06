package com.sahaaya.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoPointTest {

    // NMAM Institute of Technology, Nitte.
    private val nitte = GeoPoint(13.1836, 74.9346)

    @Test
    fun `distance to itself is zero`() {
        assertEquals(0.0, nitte.distanceMetresTo(nitte), 0.01)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val oneDegreeNorth = nitte.copy(latitude = nitte.latitude + 1.0)
        val distance = nitte.distanceMetresTo(oneDegreeNorth)
        assertEquals(111_195.0, distance, 500.0)
    }

    @Test
    fun `distance is symmetric`() {
        val other = GeoPoint(13.3409, 74.7421) // Udupi
        assertEquals(
            nitte.distanceMetresTo(other),
            other.distanceMetresTo(nitte),
            0.01,
        )
    }

    @Test
    fun `a short walk is measured in tens of metres`() {
        // Roughly 0.001 degrees of latitude, about 111 m.
        val downTheStreet = nitte.copy(latitude = nitte.latitude + 0.001)
        val distance = nitte.distanceMetresTo(downTheStreet)
        assertTrue("expected ~111 m but was $distance", distance in 100.0..120.0)
    }
}

class SafeZoneTest {

    private val centre = GeoPoint(13.1836, 74.9346)
    private val zone = SafeZone(centre = centre, radiusMetres = 200)

    @Test
    fun `the centre is inside the zone`() {
        assertTrue(zone.contains(centre))
    }

    @Test
    fun `a point well within the radius is inside`() {
        // ~55 m north.
        assertTrue(zone.contains(centre.copy(latitude = centre.latitude + 0.0005)))
    }

    @Test
    fun `a point beyond the radius is outside`() {
        // ~555 m north, comfortably past the 200 m boundary.
        assertFalse(zone.contains(centre.copy(latitude = centre.latitude + 0.005)))
    }

    @Test
    fun `a larger radius admits a point the smaller one rejected`() {
        val far = centre.copy(latitude = centre.latitude + 0.005)
        assertFalse(zone.contains(far))
        assertTrue(zone.copy(radiusMetres = 1_000).contains(far))
    }
}

class FallSensitivityTest {

    @Test
    fun `higher sensitivity means a lower impact threshold`() {
        assertTrue(
            FallSensitivity.HIGH.impactThresholdMetresPerSecondSquared <
                FallSensitivity.BALANCED.impactThresholdMetresPerSecondSquared,
        )
        assertTrue(
            FallSensitivity.BALANCED.impactThresholdMetresPerSecondSquared <
                FallSensitivity.LOW.impactThresholdMetresPerSecondSquared,
        )
    }

    @Test
    fun `every threshold is above one g so gravity alone never triggers a fall`() {
        FallSensitivity.entries.forEach { sensitivity ->
            assertTrue(
                "${sensitivity.name} would trigger on gravity alone",
                sensitivity.impactThresholdMetresPerSecondSquared > 9.81f,
            )
        }
    }

    @Test
    fun `unknown storage key falls back to balanced`() {
        assertEquals(FallSensitivity.BALANCED, FallSensitivity.fromStorageKey("nonsense"))
        assertEquals(FallSensitivity.BALANCED, FallSensitivity.fromStorageKey(null))
    }
}

class MedicationTest {

    private val medication = Medication(
        id = "m1",
        patientId = "p1",
        name = "Donepezil",
        dosage = "5 mg",
        timesOfDayMinutes = listOf(9 * 60, 21 * 60),
    )

    @Test
    fun `a daily medication is scheduled every day`() {
        (1..7).forEach { day ->
            assertTrue(medication.isScheduledOn(day))
        }
    }

    @Test
    fun `a specific-days medication is scheduled only on those days`() {
        val weekdaysOnly = medication.copy(
            schedule = MedicationSchedule.SPECIFIC_DAYS,
            daysOfWeek = setOf(1, 2, 3, 4, 5),
        )
        assertTrue(weekdaysOnly.isScheduledOn(1))
        assertTrue(weekdaysOnly.isScheduledOn(5))
        assertFalse(weekdaysOnly.isScheduledOn(6))
        assertFalse(weekdaysOnly.isScheduledOn(7))
    }

    @Test
    fun `dose ids are stable for the same medication and time`() {
        val first = MedicationDose.idFor("m1", 1_700_000_000_000L)
        val second = MedicationDose.idFor("m1", 1_700_000_000_000L)
        assertEquals(first, second)
    }

    @Test
    fun `dose ids differ across scheduled times`() {
        val morning = MedicationDose.idFor("m1", 1_700_000_000_000L)
        val evening = MedicationDose.idFor("m1", 1_700_043_200_000L)
        assertTrue(morning != evening)
    }
}

class HealthEventTest {

    @Test
    fun `falls sos and geofence exits are critical`() {
        assertEquals(EventSeverity.CRITICAL, EventType.FALL.severity)
        assertEquals(EventSeverity.CRITICAL, EventType.SOS.severity)
        assertEquals(EventSeverity.CRITICAL, EventType.GEOFENCE_EXIT.severity)
    }

    @Test
    fun `inactivity and missed doses are warnings so a caregiver can sleep`() {
        assertEquals(EventSeverity.WARNING, EventType.INACTIVITY.severity)
        assertEquals(EventSeverity.WARNING, EventType.MEDICATION_MISSED.severity)
    }

    @Test
    fun `a new event is unresolved and an acknowledged one is not`() {
        val event = HealthEvent(
            id = "e1",
            patientId = "p1",
            patientName = "Ibbani H G",
            type = EventType.FALL,
            occurredAtEpochMillis = 0L,
        )
        assertTrue(event.isUnresolved)
        assertFalse(event.copy(status = EventStatus.ACKNOWLEDGED).isUnresolved)
        assertFalse(event.copy(status = EventStatus.CANCELLED).isUnresolved)
    }
}
