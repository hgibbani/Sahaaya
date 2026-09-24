# Run modes

Sahaaya has two run modes, chosen by one constant. **The project currently ships
in LIVE MODE**, which is the mode the two-device demonstration requires.

| | LIVE MODE | LOCAL DEMO MODE |
| --- | --- | --- |
| `DemoConfig.ENABLED` | `false` (current) | `true` |
| Backend | Firebase Auth + Cloud Firestore | in-memory `DemoDataStore` |
| Devices | two or more, over the network | one, isolated |
| Cross-device alerts | **yes** | **no — impossible** |
| Needs a Firebase project | yes | no |

### Why LOCAL DEMO MODE cannot do a two-device demo

`DemoDataStore` is a `@Singleton` holding `MutableStateFlow` maps in the app's
own process. A second device runs a second process with its own empty copy.
Nothing raised on one phone can reach another. This is a property of the design,
not a bug, and it is the reason the two-device demonstration runs on Firestore.

Local demo mode remains useful for UI work, for developing without a Firebase
project, and for showing the screens on a single device.

## The switch

`core/src/main/kotlin/com/sahaaya/core/demo/DemoConfig.kt`

```kotlin
object DemoConfig {
    const val ENABLED: Boolean = false  // false = Firebase, true = in-memory
}
```

That constant is the only thing to change. Nothing above the repository
contracts recompiles differently or knows which side won.

## What LIVE MODE needs in the Firebase console

For the project named in `app/google-services.json` (`sahaaya-72a9f`):

1. **Authentication → Sign-in method →** enable **Email/Password**.
2. **Firestore Database → Create database** (any region; asia-south1 is closest).
3. **Deploy the rules** so the client is not blocked by default locked-mode:

   ```bash
   firebase login
   firebase use sahaaya-72a9f
   firebase deploy --only firestore:rules
   ```

All three are available on the free **Spark** plan. Only `functions/index.js`
(FCM push to a closed app) requires the Blaze plan.

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

## Seeded accounts (LOCAL DEMO MODE only)

In LIVE MODE these accounts do not exist until they are registered against
Firebase Auth from the app, because accounts live in the Firebase project rather
than in a seeded map. Register them once and they persist.


| Role | Email | Password |
| --- | --- | --- |
| Patient | `asha@demo.in` | `demo1234` |
| Caregiver | `ravi@demo.in` | `demo1234` |

They are already paired, and come with two medicines, three doses (one recent,
one past its grace period, one taken) and two events, so both dashboards have
something to show the moment they open.

Registration still works and creates further accounts alongside these.

## The badge

`common…components.DemoModeBadge` renders a pill in the top bar of the sign-in,
registration, patient dashboard, caregiver dashboard and developer screens. It
shows an amber **DEMO MODE** in local mode and a blue **LIVE** in live mode —
never nothing, so a viewer is never left guessing which they are looking at.
`DemoModeBanner` on the sign-in screen spells out what the current mode means.

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
