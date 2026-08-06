# Sahaaya

**An Intelligent Co-Care System for Dementia Monitoring**

Sahaaya (*sahāya* — support) turns the Android phone a family already owns into
a monitoring system for someone living with dementia. It watches for falls,
wandering, prolonged inactivity and missed medicine, and reaches the caregiver
when something happens. It does not replace the caregiver. It stands beside them.

> Major Project — NMAM Institute of Technology, Nitte
> Department of Information Science & Engineering · 7th Semester · Team 36
> Ibbani H G (NNM23IS077) · Chirag Rai (NNM23IS037)
> Guide: Dr. Karuna Pandit

---

## The problem

Existing dementia-monitoring products each solve one risk — a fall, or a wander,
or a missed dose — usually on hardware most Indian families do not own, and
almost always stopping short of the person who has to act. The caregiver finds
out late, or not at all.

Sahaaya puts all four in one app, on an ordinary phone, with the caregiver
connected to every one of them.

## Status

| Phase | Scope | State |
|---|---|---|
| **Phase 1** | Multi-module architecture, Firebase, Hilt, Material 3 design system | ✅ |
| **Phase 2** | Auth, roles, dashboards, profiles, emergency contacts, pairing | ✅ |
| **Phase 3** | Fall detection, geofencing, inactivity, medication, SOS, timeline | ✅ |
| Phase 4+ | AI prediction, smartwatch, analytics, clinician portal | Planned |

**This is a working MVP.** 12 Gradle modules, 51 unit tests, `assembleDebug`
green.

---

## What it does

### Fall detection
Three-stage state machine on the accelerometer, sampled every 20 ms: near free
fall → impact above a configurable threshold → **stillness in a new
orientation**. That last stage is the point — checking the impact spike alone is
what gives naive detectors their false-alarm rate, because setting a phone down
hard produces the same spike.

A detected fall opens a 5-second countdown over the lock screen. The event is
written to Firestore **before** the countdown and withdrawn if the patient
cancels, so a phone that dies on impact has already reported the fall.

Three sensitivity levels. 10 unit tests cover walking, sitting still, a dropped
phone, a caught stumble and free fall with no impact — all correctly rejected.

### GPS geofencing
A safe zone the patient sets by standing where the centre should be. Registered
with the OS geofencing service rather than polled, EXIT transitions only, with a
loitering delay so GPS jitter across the boundary stays quiet. Radius floor is
100 m because below that indoor drift manufactures exits that never happened.

### Inactivity
Activity Transition API resets a movement clock; a periodic worker compares
elapsed time against a configurable timeout. Warning severity, not critical — a
caregiver must be able to sleep through this and still be woken by a fall.

### Medication reminders
Exact alarms at each dose time, with **Taken** and **Skip** as notification
actions so answering costs one tap from the lock screen. Unanswered doses become
missed-dose events through an hourly sweep. Skipped and missed are stored
separately — a patient who declines has told us something, one who never
answered has not.

### Emergency SOS
Press and hold for 3 seconds, with a filling ring and a haptic tick each second.
Hold rather than tap, because it sits where a pocket will brush against it.
Sends location, timestamp and patient name.

### Caregiver dashboard
Live Firestore listeners. Outstanding alerts are the first thing on the screen —
nobody should scroll past a profile card during an emergency. Summary cards per
type, a newest-first timeline, and event detail with a hand-off to a maps app.

### Developer mode
Debug builds only. Simulates each detector by calling **the same use case the
sensor calls** — same Firestore write, same rules, same notification. Nothing is
mocked. If a simulated fall reaches the caregiver, a real one will too.

---

## Architecture

```
:app                  Application, MainActivity, root NavHost, session routing
:core                 Outcome/AppError, dispatchers, validators   (pure Kotlin)
:domain               Models, repository contracts, use cases     (pure Kotlin)
:data                 Repository implementations, DTO mapping, DI bindings
:firebase             Auth/Firestore/FCM sources, error mapping, security rules
:sensor               Accelerometer, geofencing, activity recognition, location
:common               Material 3 design system, accessible components
:feature:auth         Role selection, register, sign in, reset
:feature:dashboard    Both home screens, alert timeline, event detail
:feature:profile      Both profiles, emergency contacts
:feature:pairing      Code generation and redemption
:feature:monitoring   Foreground service, fall countdown, SOS, settings, demo
:feature:medication   Medicines, reminders, dose history
functions/            Cloud Function: FCM fan-out to caregivers
docs/                 Architecture, Firebase model, navigation, demo, roadmap
```

Dependencies point inward. `:domain` knows nothing about Android or Firebase;
`:firebase` is the only module importing `com.google.firebase`; feature modules
never depend on each other — `:app` joins them with lambdas.

Full detail: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.2.21 |
| UI | Jetpack Compose, Material 3 |
| Architecture | Clean Architecture · MVVM · Repository pattern |
| DI | Hilt 2.57.2 (KSP) |
| Async | Coroutines + Flow / StateFlow |
| Sensors | SensorManager, Geofencing API, Activity Recognition API |
| Background | Foreground Service, AlarmManager, WorkManager |
| Backend | Firebase Auth · Cloud Firestore · Cloud Messaging · Cloud Functions |
| Build | AGP 8.13.2 · Gradle 8.14.3 · version catalog |
| SDK | min 26 (Android 8.0) · target/compile 36 |

---

## Getting started

**Requirements:** Android Studio (Ladybug or newer), JDK 17+, a Firebase project.

```bash
git clone https://github.com/hgibbani/majorprojectSahaaya.git
cd majorprojectSahaaya
```

### 1. Firebase — the app will not build without this

1. Create a project at <https://console.firebase.google.com>
2. **Add app → Android**, package name **`com.sahaaya.app`**
3. Download `google-services.json` into `app/`
   (`app/google-services.json.template` shows the expected shape)
4. Enable **Authentication → Email/Password**
5. Create a **Firestore** database in *production* mode — not test mode, which
   is world-readable, and this database holds medical information

### 2. Deploy rules and indexes

```bash
firebase login
firebase use --add        # select your project
firebase deploy --only firestore:rules,firestore:indexes
```

The indexes are not optional. Without them the caregiver timeline fails at
runtime with `FAILED_PRECONDITION`.

### 3. Cloud Function — optional, needs the Blaze plan

```bash
cd functions && npm install && cd ..
firebase deploy --only functions
```

Without it, alerts still reach a caregiver whose **app is open** (see
[docs/DEMO.md](docs/DEMO.md#a-note-on-delivery)). With it, they arrive on a
locked phone. Blaze is free at this volume but requires a card on file.

### 4. Build and run

```bash
./gradlew assembleDebug     # build
./gradlew test              # 51 unit tests
./gradlew installDebug      # install on a connected device
```

Full Firebase walkthrough: **[docs/FIREBASE.md](docs/FIREBASE.md)**

### 5. Permissions to grant on the patient device

| Permission | Needed for | Note |
|---|---|---|
| Notifications | Every alert | Requested on first launch |
| Location — **Allow all the time** | Geofencing | Must be granted separately after foreground location; Android silently denies it otherwise |
| Physical activity | Inactivity detection | Android 10+ |
| Alarms & reminders | Exact dose reminders | Android 14 does not grant by default; the app degrades to inexact and says so |

---

## Trying it

You need **two accounts** — register one patient and one caregiver. Two devices,
or one device and an emulator.

```
Patient signs in ──► Link a caregiver ──► reads out a 6-character code
                                              │
Caregiver signs in ──► Link a patient ────────┘
                                              │
                                              ▼
Patient: Developer mode ──► Simulate a fall ──► 5s countdown ──► Firestore
                                                                     │
                                              Caregiver timeline ◄───┘
```

Step-by-step demo script with timings and the questions to expect:
**[docs/DEMO.md](docs/DEMO.md)**

---

## Design decisions worth knowing

Fuller reasoning in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); the short
version:

- **The fall event is written before the countdown, not after.** Waiting would
  lose exactly the falls that matter most — the hardest ones, where nobody
  reaches the phone afterwards. A cancelled event stays in the log, because a
  pattern of cancelled falls means the sensitivity is wrong.
- **Severity belongs to the event type, not the event.** A fall is always
  urgent. Letting a detector downgrade one would eventually let a bug silence
  it. Criticals bypass Do Not Disturb; warnings do not.
- **Every alert goes through one use case.** `RaiseEventUseCase` is the only
  path into the event log. A second path would diverge, and the divergence would
  surface in the situation nobody is watching.
- **Pairing is the consent record.** A caregiver reaches a patient's data only
  while an `active` pairing exists — revoking it cuts access server-side,
  immediately, without the caregiver's app cooperating.
- **The event log is append-only.** Security rules pin `type`, `patientId` and
  `occurredAt` on update; delete is denied for every party. The record of an
  emergency is not the patient's to erase, and not the caregiver's either.
- **Failures are values.** Every repository returns `Outcome<T>`. In an app
  where a swallowed exception is a missed alert, the compiler should force the
  question.
- **Location is never streamed.** One point is attached to one event. A
  continuous trail would be far more sensitive than anything the app needs.
- **Detection runs on-device.** Only events leave the phone — never raw sensor
  data, never video.
- **Type is one step larger than Material 3's default**, nothing below 14sp,
  touch targets 56dp not 48dp. The patient side is used by people with reduced
  near vision and, often, tremor.

## Testing

```bash
./gradlew test
```

51 unit tests. The ones worth looking at are in
`sensor/src/test/.../FallDetectionEngineTest.kt` — the fall algorithm is a plain
class with a `feed()` function precisely so it can be tested by replaying
synthetic accelerometer traces rather than by repeatedly dropping a phone.

## Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, module graph, patterns, extension points |
| [FIREBASE.md](docs/FIREBASE.md) | Setup, every collection and field, security model |
| [NAVIGATION.md](docs/NAVIGATION.md) | Session states, auth flow, route map |
| [DEMO.md](docs/DEMO.md) | Demo script, delivery paths, troubleshooting, likely questions |
| [ROADMAP.md](docs/ROADMAP.md) | What each phase adds and where it plugs in |

## Known limits

Stated plainly, because a demo that oversells is worse than one that does not:

- **Fall detection is validated against synthetic traces, not real falls.** The
  thresholds come from published fall-detection work; they have not been tuned
  against a real dataset on real bodies.
- **Push to a closed app needs the Cloud Function deployed** (Blaze plan).
- **No screenshots in this README yet** — they are added once the app has run on
  a physical device with a real Firebase project.
- Not clinically validated. Not a medical device.

## Licence

Academic project. Not licensed for clinical use.
