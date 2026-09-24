package com.sahaaya.data.demo

import com.sahaaya.core.demo.DemoConfig
import com.sahaaya.domain.model.CareReminder
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.DementiaStage
import com.sahaaya.domain.model.DoseStatus
import com.sahaaya.domain.model.EmergencyContact
import com.sahaaya.domain.model.EventStatus
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.Gender
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.model.Medication
import com.sahaaya.domain.model.MedicationDose
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingCode
import com.sahaaya.domain.model.PairingStatus
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The whole backend, in memory.
 *
 * One singleton holds every collection the Firestore implementation would own,
 * as a [MutableStateFlow] per collection. That is what makes the demo behave
 * like the real thing rather than like a set of fixtures: a write here pushes
 * through the same `observe…` flows the screens already collect, so raising an
 * SOS updates the caregiver timeline live, exactly as a Firestore snapshot
 * listener would.
 *
 * State lives for the life of the process. Killing the app resets it to the
 * seed below - which is a feature before a demonstration, and the reason the
 * two seeded accounts exist at all.
 */
@Singleton
class DemoDataStore @Inject constructor() {

    val users = MutableStateFlow<Map<String, User>>(emptyMap())

    /** Lower-cased email to password. Plain text is acceptable: nothing here is real. */
    val passwords = MutableStateFlow<Map<String, String>>(emptyMap())

    val patientProfiles = MutableStateFlow<Map<String, PatientProfile>>(emptyMap())
    val caregiverProfiles = MutableStateFlow<Map<String, CaregiverProfile>>(emptyMap())

    /** patientId to their contacts. */
    val emergencyContacts = MutableStateFlow<Map<String, List<EmergencyContact>>>(emptyMap())

    val pairings = MutableStateFlow<Map<String, Pairing>>(emptyMap())
    val pairingCodes = MutableStateFlow<Map<String, PairingCode>>(emptyMap())

    /** patientId to their most recent fix. */
    val patientLocations = MutableStateFlow<Map<String, PatientLocation>>(emptyMap())

    val events = MutableStateFlow<Map<String, HealthEvent>>(emptyMap())
    val reminders = MutableStateFlow<Map<String, CareReminder>>(emptyMap())
    val medications = MutableStateFlow<Map<String, Medication>>(emptyMap())
    val doses = MutableStateFlow<Map<String, MedicationDose>>(emptyMap())
    val settings = MutableStateFlow<Map<String, MonitoringSettings>>(emptyMap())

    /** The signed-in uid, or null. Mirrors Firebase Auth's current user. */
    val currentUid = MutableStateFlow<String?>(null)

    private var idCounter = 0L

    @Synchronized
    fun nextId(prefix: String): String = "${prefix}_${++idCounter}"

    fun userByEmail(email: String): User? =
        users.value.values.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }

    init {
        seed()
    }

    /**
     * Two linked accounts with enough history that both dashboards have
     * something to show the moment they are opened.
     *
     * A caregiver dashboard that is empty on first sight demonstrates nothing,
     * and the point of the seed is that the demo starts from the state a family
     * would be in after a week of use, not from zero.
     */
    private fun seed() {
        val now = System.currentTimeMillis()
        val patientId = "demo-patient"
        val caregiverId = "demo-caregiver"

        val patient = User(
            uid = patientId,
            email = DemoConfig.PATIENT_EMAIL,
            displayName = "Asha Nair",
            role = Role.PATIENT,
            phoneNumber = "9876543210",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val caregiver = User(
            uid = caregiverId,
            email = DemoConfig.CAREGIVER_EMAIL,
            displayName = "Ravi Nair",
            role = Role.CAREGIVER,
            phoneNumber = "9876543211",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )

        users.value = mapOf(patientId to patient, caregiverId to caregiver)
        passwords.value = mapOf(
            patient.email.lowercase() to DemoConfig.SEED_PASSWORD,
            caregiver.email.lowercase() to DemoConfig.SEED_PASSWORD,
        )

        patientProfiles.value = mapOf(
            patientId to PatientProfile(
                uid = patientId,
                dateOfBirth = "1948-03-12",
                gender = Gender.FEMALE,
                bloodGroup = "B+",
                address = "14 Rose Lane, Kochi",
                diagnosisStage = DementiaStage.MODERATE,
                diagnosedOn = "2023-06-01",
                medicalNotes = "Hypertension. Uses a walking stick outdoors.",
                allergies = "Penicillin",
                primaryCaregiverId = caregiverId,
                updatedAtEpochMillis = now,
            ),
        )
        caregiverProfiles.value = mapOf(
            caregiverId to CaregiverProfile(
                uid = caregiverId,
                relationshipToPatient = "Son",
                address = "14 Rose Lane, Kochi",
                isAvailableForAlerts = true,
                updatedAtEpochMillis = now,
            ),
        )

        emergencyContacts.value = mapOf(
            patientId to listOf(
                EmergencyContact(
                    id = "contact-1",
                    name = "Ravi Nair",
                    phoneNumber = "9876543211",
                    relationship = "Son",
                    priority = 1,
                    isPrimary = true,
                ),
                EmergencyContact(
                    id = "contact-2",
                    name = "Dr Meera Menon",
                    phoneNumber = "9876500011",
                    relationship = "Physician",
                    priority = 2,
                ),
            ),
        )

        val pairing = Pairing(
            id = Pairing.idFor(patientId, caregiverId),
            patientId = patientId,
            caregiverId = caregiverId,
            patientName = patient.displayName,
            caregiverName = caregiver.displayName,
            status = PairingStatus.ACTIVE,
            createdAtEpochMillis = now - 7 * DAY_MILLIS,
        )
        pairings.value = mapOf(pairing.id to pairing)

        settings.value = mapOf(patientId to MonitoringSettings(patientId = patientId))

        val morning = Medication(
            id = "med-1",
            patientId = patientId,
            name = "Donepezil",
            dosage = "10 mg",
            timesOfDayMinutes = listOf(8 * 60),
            notes = "After breakfast",
            createdAtEpochMillis = now - 7 * DAY_MILLIS,
        )
        val evening = Medication(
            id = "med-2",
            patientId = patientId,
            name = "Amlodipine",
            dosage = "5 mg",
            timesOfDayMinutes = listOf(20 * 60),
            createdAtEpochMillis = now - 7 * DAY_MILLIS,
        )
        medications.value = mapOf(morning.id to morning, evening.id to evening)

        // Three doses, each doing a job. The recent one keeps a live prompt on
        // the patient dashboard; the older one is already past the 30-minute
        // grace period, so the missed-dose sweep on the Demo Mode screen has
        // something real to flag rather than reporting "nothing overdue"; the
        // taken one stops the history being empty.
        val pendingAt = now - 10 * MINUTE_MILLIS
        val overdueAt = now - 90 * MINUTE_MILLIS
        val takenAt = now - DAY_MILLIS
        doses.value = listOf(
            MedicationDose(
                id = MedicationDose.idFor(morning.id, pendingAt),
                medicationId = morning.id,
                patientId = patientId,
                medicationName = morning.name,
                dosage = morning.dosage,
                scheduledAtEpochMillis = pendingAt,
                status = DoseStatus.PENDING,
            ),
            MedicationDose(
                id = MedicationDose.idFor(evening.id, overdueAt),
                medicationId = evening.id,
                patientId = patientId,
                medicationName = evening.name,
                dosage = evening.dosage,
                scheduledAtEpochMillis = overdueAt,
                status = DoseStatus.PENDING,
            ),
            MedicationDose(
                id = MedicationDose.idFor(morning.id, takenAt),
                medicationId = morning.id,
                patientId = patientId,
                medicationName = morning.name,
                dosage = morning.dosage,
                scheduledAtEpochMillis = takenAt,
                status = DoseStatus.TAKEN,
                respondedAtEpochMillis = takenAt + 5 * MINUTE_MILLIS,
            ),
        ).associateBy { it.id }

        events.value = listOf(
            HealthEvent(
                id = "event-1",
                patientId = patientId,
                patientName = patient.displayName,
                type = EventType.MEDICATION_MISSED,
                status = EventStatus.ACKNOWLEDGED,
                occurredAtEpochMillis = now - 2 * DAY_MILLIS,
                summary = "Amlodipine was not taken",
                details = mapOf("Medicine" to "Amlodipine", "Dosage" to "5 mg"),
                acknowledgedByCaregiverId = caregiverId,
                acknowledgedAtEpochMillis = now - 2 * DAY_MILLIS + 30 * MINUTE_MILLIS,
            ),
            HealthEvent(
                id = "event-2",
                patientId = patientId,
                patientName = patient.displayName,
                type = EventType.INACTIVITY,
                status = EventStatus.NEW,
                occurredAtEpochMillis = now - 5 * 60 * MINUTE_MILLIS,
                summary = "No movement detected for 2 h",
                details = mapOf("Time without movement" to "2 h"),
            ),
        ).associateBy { it.id }
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val DAY_MILLIS = 24 * 60 * 60_000L
    }
}

/** Convenience for the repositories below: replace one entry in a map flow. */
internal fun <V> MutableStateFlow<Map<String, V>>.put(key: String, value: V) {
    update { it + (key to value) }
}
