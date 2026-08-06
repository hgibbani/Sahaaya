# Offline Demo Mode

The Firebase project the app is registered to is not currently billable, so
every Firestore read and write fails. Rather than remove the Firebase layer —
which is the real implementation and stays in the source tree untouched — the
app can run against an in-memory backend instead.

## The switch

`core/src/main/kotlin/com/sahaaya/core/demo/DemoConfig.kt`

```kotlin
object DemoConfig {
    const val ENABLED: Boolean = true   // false = back on Firebase
}
```

That constant is the only thing to change. Setting it to `false` puts the app
back on Firestore with no other edit anywhere in the tree.

## How it is wired

Nothing about the architecture changed. The domain contracts, use cases,
ViewModels and screens are byte-for-byte the same code in both modes; only the
Hilt bindings differ:

| Contract | Firebase implementation | Demo implementation |
| --- | --- | --- |
| `AuthRepository` | `data…AuthRepositoryImpl` | `data…demo.DemoAuthRepository` |
| `ProfileRepository` | `data…ProfileRepositoryImpl` | `data…demo.DemoProfileRepository` |
| `PairingRepository` | `data…PairingRepositoryImpl` | `data…demo.DemoPairingRepository` |
| `EventRepository` | `data…EventRepositoryImpl` | `data…demo.DemoEventRepository` |
| `MedicationRepository` | `data…MedicationRepositoryImpl` | `data…demo.DemoMedicationRepository` |
| `SettingsRepository` | `data…SettingsRepositoryImpl` | `data…demo.DemoSettingsRepository` |
| `MessagingRepository` | `data…MessagingRepositoryImpl` | `data…demo.DemoMessagingRepository` |
| `LocationRepository` | `sensor…LocationRepositoryImpl` | `sensor…demo.DemoLocationRepository` |
| `MonitoringController` | `feature.monitoring…MonitoringControllerImpl` | `feature.monitoring…demo.DemoMonitoringController` |

The selection happens in `DataModule`, `SensorBindingsModule` and
`MonitoringModule`. Each provider takes **both** sides as `javax.inject.Provider`
and calls `.get()` on one of them. That is deliberate: taking the Firebase
implementation by value would make Dagger construct `FirebaseAuth` and
`FirebaseFirestore` even in demo mode, against a project that is not reachable.

`DemoDataStore` holds every collection as a `MutableStateFlow`, so a write
re-emits to every collector immediately — the same live-update behaviour a
Firestore snapshot listener gives. A simulated SOS lands on the caregiver
timeline with no refresh.

State lives for the life of the process. Killing the app resets it to the seed.

## Seeded accounts

| Role | Email | Password |
| --- | --- | --- |
| Patient | `asha@demo.in` | `demo1234` |
| Caregiver | `ravi@demo.in` | `demo1234` |

They are already paired, and come with two medicines, three doses (one recent,
one past its grace period, one taken) and two events, so both dashboards have
something to show the moment they open.

Registration still works and creates further accounts alongside these.

## The badge

`common…components.DemoModeBadge` renders a "DEMO MODE" pill in the top bar of
the sign-in, registration, patient dashboard, caregiver dashboard and developer
screens. `DemoModeBanner` on the sign-in screen spells out the seeded
credentials. Both compose to nothing when `DemoConfig.ENABLED` is `false`.

## What to demonstrate

Everything on the one device, signing out and back in to switch roles:

1. **Register** a new patient — lands on the patient dashboard.
2. **Pair** — patient taps *Link a caregiver* → *Make a code*; sign out; sign in
   as `ravi@demo.in`; *Link another patient*; type the code.
3. **Patient dashboard** — SOS button, care circle, medicines, pending dose.
4. **Fall / SOS / inactivity / geofence / missed dose** — patient dashboard →
   *Developer mode*. Each button drives the production use case.
5. **Caregiver dashboard and timeline** — sign back in as the caregiver; the
   alerts raised in step 4 are there, newest first, and acknowledging one moves
   its status.

## What is not exercised offline

Firestore security rules, Cloud Functions and FCM push delivery. Those need a
live project; the code for all three is unchanged and still in the tree
(`firebase/firestore.rules`, `functions/index.js`, `firebase…messaging`).
