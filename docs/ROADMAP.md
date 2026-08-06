# Roadmap

Phases 1, 2 and 3 are complete. This records what shipped, and what each later
phase adds.

## Phase 3 — Detection ✅ SHIPPED

The point of the project. Everything before it existed so these could be built
without restructuring anything, and none of it required an architectural change.

| Feature | Where it lives |
|---|---|
| Fall detection | `:sensor/fall/FallDetectionEngine` + `:feature:monitoring` |
| Geofencing | `:sensor/geofence` + `MonitoringSettings.safeZone` |
| Inactivity | `:sensor/activity` + `InactivityCheckWorker` |
| Medication | `:feature:medication` |
| SOS | `:feature:monitoring/sos` |
| Alert delivery | `functions/index.js` |

The design notes below are kept for the reasoning behind each choice.

### Fall detection

- **New module:** `:feature:monitoring`, plus a `FallDetector` in a new
  `:sensor` module wrapping `SensorManager`.
- **Approach:** free-fall (magnitude below ~0.5g) → impact (above ~2.5g) →
  orientation change → a confirmation window before the alert fires.
- **Why the confirmation window matters:** threshold-only detectors are what
  give existing products their false-alarm rate, and an alert that cries wolf
  gets muted — after which the system is worse than nothing.
- **Writes to:** `events/{eventId}` - top-level and keyed by patientId, so a
  caregiver's timeline is a single query.

### GPS geofencing

- Adds a safe-zone (centre + radius) to `PatientProfile`; that document and its
  caregiver-read rule already exist.
- Uses the Geofencing API rather than continuous polling, for battery.
- Boundary crossings become events in the same collection.

### Inactivity detection

- Activity Recognition API plus a no-movement timer.
- Threshold defaults keyed off `DementiaStage`, which Phase 2 captures for
  exactly this reason.

### Medication reminders

- New `reminders` sub-collection under the patient.
- `AlarmManager` for exact prompts, `WorkManager` for the missed-dose report
  back to the caregiver.

### Alert delivery

- A Cloud Function watching `events`, resolving active pairings,
  and sending to `users/{uid}.fcmTokens`.
- Nothing about this needs Phase 2 to change: tokens are registered at sign-up
  rather than on first alert, so every account already has a live token before
  the first fall.
- Escalation walks `EmergencyContact.priority` in order, and skips caregivers
  with `isAvailableForAlerts = false`.

## Phase 4 — Intelligence (next)

- On-device classifier (TensorFlow Lite) trained on public fall datasets, run
  alongside the threshold detector to cut false alarms further.
- Caregiver analytics: mobility, sleep and adherence trends over the event
  history Phase 3 accumulates.
- Multi-caregiver escalation with acknowledgement — if nobody responds within
  N minutes, move to the next contact.

## Phase 5 — Sahaaya 360

- **Clinician access.** A new `Role` entry. Every role-dependent decision in the
  app is an exhaustive `when` over `Role`, so adding a case produces compile
  errors at precisely the places that need a decision rather than silently
  defaulting.
- Hospital integration, smartwatch companion, voice assistant.
- Predictive risk scoring over accumulated event history.

## Still reserved, still unused

| Thing | Looks unused today | Exists because |
|---|---|---|
| `EmergencyContact.priority` | Only orders a list | Phase 4 escalation walks it as a chain |
| `isAvailableForAlerts` | Only a switch | Phase 4 uses it to route alerts during handover |
| `primaryCaregiverId` | Unset | Phase 4 alert ordering |

## Known gaps in the shipped MVP

Recorded so they are not mistaken for finished work:

- **Fall thresholds are validated against synthetic traces, not real falls.**
  They come from published fall-detection work and are unit-tested for the right
  *behaviour* (walking rejected, dropped phone rejected), but have not been tuned
  against real bodies. This is the single most valuable thing Phase 4 can fix.
- **Push to a killed app requires the Cloud Function deployed** (Blaze plan).
  The in-app listener fallback only works while the caregiver app is running.
- **No end-to-end instrumented tests.** The logic is unit-tested; the Firestore
  round trip and the sensor-to-notification path are verified by hand.
- **Time zones.** Dose times are minutes-past-midnight in device local time; a
  patient who travels across a time zone would see reminders shift.

## Not planned

- **Camera or audio monitoring.** Rejected on privacy grounds. It is the single
  most common reason families abandon monitoring products, and Sahaaya's whole
  argument is that a phone's motion sensors are enough.
- **Raw sensor streams leaving the device.** Detection runs on-device; only
  events are uploaded. This is the answer to the privacy question, and it also
  keeps Firestore costs flat.
