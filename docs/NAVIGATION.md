# Authentication and navigation flow

## The single rule

**Nothing navigates on success.** Screens do not say "sign-in worked, go to the
dashboard". They change the session, and one observer in `:app` decides where
the user belongs.

```kotlin
// SahaayaNavHost.kt
LaunchedEffect(sessionState) {
    when (sessionState) {
        Resolving  -> Unit
        SignedOut  -> navigate(AuthRoutes.GRAPH)   { popUpTo(graph) { inclusive = true } }
        SignedIn   -> navigate(dashboardFor(role)) { popUpTo(graph) { inclusive = true } }
    }
}
```

Registration, sign-in, sign-out and a session expiring in the background all
produce the same signal. There is no path that leaves a signed-out user on a
dashboard, or sends a patient to the caregiver side.

## Session states

| State | When | Shown |
|---|---|---|
| `Resolving` | Cold start, before Firebase restores the cached session | Splash |
| `SignedOut` | No Auth user, or `users/{uid}` is unreadable | Auth graph |
| `SignedIn(user)` | Auth user **and** their `users/{uid}` document loaded | Dashboard for their role |

`Resolving` is a real state, not a loading flag. Restoring a session takes a few
hundred milliseconds; without this state the app flashes the welcome screen at a
user who is already signed in.

`SignedIn` requires **both** halves. Firebase Auth knows *that* someone is signed
in; only the Firestore document knows their role, and the role decides the
destination.

## Registration

```
Welcome (role selection)
   │
   ├─ "I am the patient"   ─┐
   └─ "I am a caregiver"   ─┴─► Register(role)
                                    │
                                    ▼
                    RegisterUseCase validates locally
                                    │
                    ┌───────────────┴───────────────┐
                    │ 1. Firebase Auth create       │
                    │ 2. write users/{uid}          │
                    │ 3. write patients|caregivers  │
                    │ 4. register FCM token         │
                    └───────────────┬───────────────┘
                                    │
                       session flips to SignedIn
                                    │
                                    ▼
                    Patient dashboard / Caregiver dashboard
```

Two decisions inside step 1–3:

- **Roll back on partial failure.** If the Auth account is created but the
  Firestore write fails, the Auth account is deleted. An account with no
  `users/{uid}` document has no role, matches no security rule, and cannot be
  repaired from inside the app — the user would be permanently stuck.
- **Token registration cannot fail the sign-up.** If step 4 fails the account is
  still valid; the token is retried on the next sign-in.

Role is chosen on its own screen because it is permanent. It fixes the security
rules that apply, which dashboard loads, and which half of a pairing the account
occupies.

## Route map

| Route | Owner | Purpose |
|---|---|---|
| `splash` | `:app` | Held while the session resolves |
| `auth/role` | `:feature:auth` | Patient or caregiver |
| `auth/register/{role}` | `:feature:auth` | Create account |
| `auth/login` | `:feature:auth` | Sign in |
| `auth/forgot` | `:feature:auth` | Password reset |
| `dashboard/patient` | `:feature:dashboard` | Patient home |
| `dashboard/caregiver` | `:feature:dashboard` | Caregiver home |
| `profile/patient/{patientId}` | `:feature:profile` | `self` = own profile (editable); a uid = caregiver's read-only view |
| `profile/caregiver` | `:feature:profile` | Caregiver's own profile |
| `profile/emergency` | `:feature:profile` | Emergency contacts |
| `pairing/code` | `:feature:pairing` | Patient shows a code |
| `pairing/redeem` | `:feature:pairing` | Caregiver enters a code |

## How features stay independent

Each feature module exposes one function that registers its routes:

```kotlin
// :feature:auth
fun NavGraphBuilder.authGraph(navController: NavController)

// :feature:dashboard — callbacks, because it links to other features' screens
fun NavGraphBuilder.patientDashboard(
    onOpenProfile: () -> Unit,
    onOpenEmergencyContacts: () -> Unit,
    onOpenPairing: () -> Unit,
)
```

A dashboard needs to open a profile screen, but `:feature:dashboard` must not
depend on `:feature:profile` — that would make the two impossible to build or
test apart. So the dashboard takes lambdas and `:app` supplies them. Adding a
screen inside a feature never touches the app module; only cross-feature links
do.

## Back-stack behaviour

- Signing in or out clears the entire back stack (`popUpTo(graph) { inclusive = true }`).
  Pressing back from a dashboard must not return to a sign-in screen for a
  session that is now live.
- Within a feature, back pops normally.
- After a caregiver links a patient, the success state is shown **first** and
  dismissed by the user. Linking grants access to someone's medical profile; a
  silent redirect would leave the caregiver unsure it worked.
