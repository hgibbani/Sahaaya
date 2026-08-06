# Architecture

## Why it is shaped this way

Sahaaya is a monitoring app for people with dementia. Two constraints drove
every structural decision:

1. **The consequence of a bug is not a bad screen, it is a missed alert.** A
   mistyped Firestore path or a silently dropped exception means a fall is not
   reported. So failures are values that must be handled, not exceptions that
   can be forgotten, and every collection name is a constant rather than a
   string literal.
2. **Later phases must not require a rewrite.** Fall detection, geofencing,
   inactivity monitoring and reminders all need the same substrate: an identity,
   a caregiver link, a push token and a device that can write events. Phases 1
   and 2 built exactly that; Phase 3 then landed on it without moving anything.
   The "Extension points" table below records which seam each feature used.

## Layers

```
                    ┌──────────────────────────────┐
                    │            :app              │  Navigation host, session
                    │  MainActivity, NavHost, DI   │  routing, Application
                    └───────────────┬──────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
┌───────▼────────┐  ┌───────────────▼──────────┐  ┌─────────────▼────────┐
│ :feature:auth  │  │  :feature:dashboard      │  │  :feature:profile    │
│ :feature:      │  │                          │  │  :feature:pairing    │
│   pairing      │  │  Compose + ViewModel     │  │                      │
└───────┬────────┘  └───────────────┬──────────┘  └─────────────┬────────┘
        │                           │                           │
        └───────────────────────────┼───────────────────────────┘
                                    │  (features depend on domain,
                                    │   never on each other)
                    ┌───────────────▼──────────────┐
                    │          :domain             │  Pure Kotlin
                    │  Models, repository contracts │  No Android
                    │  Use cases                   │  No Firebase
                    └───────────────┬──────────────┘
                                    │ implemented by
                    ┌───────────────▼──────────────┐
                    │           :data              │  DTO mapping,
                    │  Repository implementations  │  Hilt bindings
                    └───────────────┬──────────────┘
                                    │
       ┌────────────────────────────┴──────────────────────────┐
       │                                                        │
┌──────▼───────────────────────┐              ┌─────────────────▼──────────┐
│         :firebase            │              │          :sensor           │
│  Auth, Firestore, Messaging  │              │  Accelerometer, geofence,  │
│  The ONLY module importing   │              │  activity, location.       │
│  com.google.firebase         │              │  No Firebase, no UI.       │
└──────────────────────────────┘              └────────────────────────────┘

  :core    — Outcome, AppError, DispatcherProvider, Validators (pure Kotlin)
  :common  — Material 3 design system, accessible components (Compose)

  Feature modules: auth · dashboard · profile · pairing · monitoring · medication
```

### The dependency rule

Arrows point inward. `:domain` depends on nothing but `:core`. That is what
makes the business rules testable in milliseconds on the JVM, and what means a
decision to leave Firebase would touch `:firebase` and `:data` and stop there.

`:feature:*` modules never depend on one another. When a dashboard needs to open
a screen owned by `:feature:profile`, it takes a lambda; `:app` supplies it.

## Module reference

| Module | Type | Contains |
|---|---|---|
| `:core` | Kotlin JVM | `Outcome`/`AppError`, `DispatcherProvider`, `Validators` |
| `:domain` | Kotlin JVM | Models, repository interfaces, use cases |
| `:data` | Android lib | Repository implementations, DTO mappers, Hilt bindings |
| `:firebase` | Android lib | Auth/Firestore/FCM data sources, error mapping, collection constants, FCM service |
| `:sensor` | Android lib | Accelerometer fall engine, geofencing, activity recognition, location |
| `:common` | Android lib | Theme, typography, spacing, shared composables |
| `:feature:auth` | Android lib | Role selection, register, sign in, password reset |
| `:feature:dashboard` | Android lib | Patient and caregiver home screens |
| `:feature:profile` | Android lib | Both profiles, emergency contacts |
| `:feature:pairing` | Android lib | Code generation and redemption |
| `:feature:monitoring` | Android lib | Foreground service, fall countdown, SOS, settings, demo mode |
| `:feature:medication` | Android lib | Medicines, reminders, dose history |
| `:app` | Application | `SahaayaApplication`, `MainActivity`, root `NavHost` |

## Patterns

### Errors are values

Repositories and use cases return `Outcome<T>`, never throw for an expected
failure. A dropped connection, a wrong password and a missing document are
ordinary events in a care app running on a phone in a house with poor signal.

```kotlin
when (val result = signInUseCase(params)) {
    is Outcome.Success -> // routed by the session observer
    is Outcome.Failure -> showBanner(result.error.message)
}
```

`AppError` is a closed set of the app's *own* failures. Firebase exceptions are
translated at the `:firebase` boundary by `FirebaseErrorMapper`, so a caregiver
never reads `ERROR_INVALID_LOGIN_CREDENTIALS` at 2am.

### One state object per screen

Each screen exposes a single immutable `UiState` through `StateFlow`. There is
no combination of separate flows that can paint a half-updated screen — a
spinner running next to a stale error message, for instance.

### Screens split in two

Every screen is a Hilt-injected wrapper plus a stateless `…Content` composable.
The body previews and tests without Hilt, without Firebase and without a
network.

### Live reads, one-shot writes

Reads are `Flow` because Firestore pushes: a caregiver editing a patient's
address should change the patient's screen without a refresh. Writes are
suspend functions returning `Outcome`.

## Extension points

Phase 3 shipped through these seams without changing the architecture:

| Phase 3 feature | Where it landed | Seam it used |
|---|---|---|
| Fall detection | `:sensor` + `:feature:monitoring` | `events` collection name and rules were already fixed |
| Geofencing | `MonitoringSettings.safeZone` | `SettingsRepository` contract in `:domain` |
| Inactivity | `InactivityCheckWorker` | `DementiaStage`, captured in Phase 2 for exactly this |
| Medication | `:feature:medication` | `AlarmManager`/`WorkManager`, declared in the Phase 1 stack |
| Alert delivery | `functions/index.js` | `pairings` + `users.fcmTokens`, populated since Phase 2 |

Still open for Phase 4+:

| Phase | Where it plugs in | Why it already fits |
|---|---|---|
| Escalation chain | `EmergencyContact.priority` walked in order | Priority is a stable integer, not a UI list position |
| Caregiver handover | `CaregiverProfile.isAvailableForAlerts` | Recorded and editable since Phase 2 |
| ML fall classifier | Alongside `FallDetectionEngine` | The engine is a pure class fed samples; a model can consume the same stream |
| Analytics | `events` + `doses` history | Both are append-only and already accumulating |
| Doctor / hospital portal | New `Role` entries | Every role decision is an exhaustive `when` over `Role`, so the compiler points at each place needing a new branch |

### What would change

Adding a role is the largest of these. `Role` is an enum with exhaustive `when`
blocks at each decision point, so a new `DOCTOR` case produces compile errors at
precisely the places that need a decision, rather than silently defaulting.

## Deliberate omissions

- **No `build-logic` convention plugins.** At twelve modules the duplication in
  the build files is still smaller than the indirection would cost. Worth
  revisiting past roughly fifteen.
- **No dynamic colour.** Emergency red carries meaning; letting the wallpaper
  repaint it would break the one visual rule the app relies on.
- **No offline write queue of our own.** Firestore's persistence already does
  this, and a second queue on top would be a second source of truth.
