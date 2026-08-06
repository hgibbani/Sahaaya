# Firebase

## Setup

The app will not build until you supply your own `google-services.json`. That
file is gitignored because it identifies your Firebase project.

1. Create a project at <https://console.firebase.google.com>.
2. **Add app → Android**, package name **`com.sahaaya.app`**.
   The debug build uses the same package name, so one registration covers both.
3. Download `google-services.json` and save it to `app/google-services.json`.
   (`app/google-services.json.template` shows the expected shape.)
4. In the console enable:
   - **Authentication → Sign-in method → Email/Password**
   - **Firestore Database** → start in *production* mode, then deploy the rules
     below. Do not leave it in test mode: test mode is world-readable, and this
     database holds medical information.
   - **Cloud Messaging** (enabled by default)
5. Deploy the security rules:
   ```bash
   firebase deploy --only firestore:rules
   ```
   The rules live at `firebase/firestore.rules`.

### If you later want debug and release installed side by side

Add `applicationIdSuffix = ".debug"` to the debug build type in
`app/build.gradle.kts`, then register a **second** Android app in Firebase with
package name `com.sahaaya.app.debug` and re-download `google-services.json`.
Without the second registration the build fails at `processDebugGoogleServices`.

## Data model

Identity is separated from clinical data so that security rules can grant a
caregiver access to one without the other.

```
users/{uid}                                  identity + role
patients/{uid}                               clinical detail
  └── emergencyContacts/{contactId}          who to call, in order
caregivers/{uid}                             caregiver detail
pairings/{patientId}_{caregiverId}           the consent record
pairingCodes/{code}                          short-lived redemption codes
settings/{patientId}                         monitoring thresholds + safe zone
events/{eventId}                             falls, geofence, inactivity, SOS, missed doses
medications/{medicationId}                   what to take and when
doses/{medicationId}_{scheduledAt}           one row per scheduled occurrence
notifications/{id}                           delivery log written by the Cloud Function
```

Events, medications and doses are **top-level and keyed by `patientId`**, not
nested under the patient. A caregiver looking after several people then gets one
timeline from one `whereIn` query; a sub-collection would need one listener per
patient and a client-side merge to order them.

The composite indexes these queries need are in `firebase/firestore.indexes.json`.
Deploy them, or the timeline fails at runtime with `FAILED_PRECONDITION`.

### `users/{uid}`

Every signed-in account has exactly one. Kept deliberately small: it is read on
every launch and by every paired caregiver.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | Matches the document id and the Auth uid |
| `email` | string | |
| `displayName` | string | Shown to the other side of a pairing |
| `role` | string | `patient` or `caregiver`. **Immutable** — enforced in rules |
| `phoneNumber` | string? | |
| `photoUrl` | string? | Reserved; not set yet |
| `fcmTokens` | array\<string\> | One per device. Array, not a single value, so a caregiver with a phone and a tablet gets alerts on both |
| `createdAt` / `updatedAt` | number | Epoch millis |

### `patients/{uid}`

| Field | Type | Notes |
|---|---|---|
| `uid` | string | |
| `dateOfBirth` | string? | Free text; not parsed |
| `gender` | string | `female` / `male` / `other` / `unspecified` |
| `bloodGroup` | string? | |
| `address` | string? | Where a caregiver should go |
| `diagnosisStage` | string | `early` / `moderate` / `advanced` / `unspecified`. Informs sensible monitoring defaults |
| `diagnosedOn` | string? | |
| `medicalNotes` | string? | Max 1000 chars |
| `allergies` | string? | Max 300 chars |
| `primaryCaregiverId` | string? | Reserved for alert ordering |
| `updatedAt` | number | |

### `patients/{uid}/emergencyContacts/{contactId}`

| Field | Type | Notes |
|---|---|---|
| `id`, `name`, `phoneNumber`, `relationship` | string | |
| `priority` | number | Calling order; 1 is tried first. A stable integer, not a list position, so the escalation chain can walk it |
| `isPrimary` | boolean | Exactly one contact is primary; saving a new one demotes the old |

Capped at five. An escalation chain nobody can recall the shape of is not a plan.

### `caregivers/{uid}`

| Field | Type | Notes |
|---|---|---|
| `uid` | string | |
| `relationshipToPatient` | string? | |
| `address` | string? | |
| `isAvailableForAlerts` | boolean | Care handover switch, not a notification preference |
| `updatedAt` | number | |

### `pairings/{patientId}_{caregiverId}`

The most important document in the system. Every rule granting a caregiver
access to a patient consults it.

| Field | Type | Notes |
|---|---|---|
| `id` | string | `{patientId}_{caregiverId}` — deterministic, so the same pair cannot produce two competing documents |
| `patientId`, `caregiverId` | string | |
| `patientName`, `caregiverName` | string | Denormalised so a list renders without N extra reads |
| `status` | string | `active` or `revoked`. Can only ever move to `revoked` |
| `createdAt` / `revokedAt` | number | |

Pairings are never deleted, so a family keeps the record of who had access and
when it ended.

### `pairingCodes/{code}`

| Field | Type | Notes |
|---|---|---|
| `code` | string | Document id. 6 chars from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` |
| `patientId`, `patientName` | string | |
| `createdAt` / `expiresAt` | number | 15-minute validity, enforced in rules |
| `redeemedByCaregiverId` | string? | Written as explicit `null`, because Firestore cannot query on an absent field |

The alphabet excludes **I, O, 0 and 1**: these codes are read aloud, often over
a phone, sometimes by someone who is already confused.

## Access control

Full rules in `firebase/firestore.rules`. The model in one line: **a caregiver
can reach a patient's data only while an `active` pairing document exists.**

| Document | Read | Write |
|---|---|---|
| `users/{uid}` | Owner, or actively paired caregiver | Owner; `role` immutable |
| `patients/{uid}` | Owner, or actively paired caregiver | Owner only |
| `…/emergencyContacts` | Owner, or actively paired caregiver | Owner only |
| `caregivers/{uid}` | Owner, or actively paired patient | Owner only |
| `pairings/{id}` | Either participant | Caregiver creates; either revokes |
| `pairingCodes/{code}` | `get` by exact id; `list` only your own | Patient creates; caregiver marks redeemed |
| `settings/{patientId}` | Owner, or actively paired caregiver | Owner only |
| `events/{eventId}` | Patient, or actively paired caregiver | Patient creates; append-only afterwards |
| `medications/{id}` | Patient, or actively paired caregiver | Patient only; removal is a soft delete |
| `doses/{id}` | Patient, or actively paired caregiver | Patient only |
| `notifications/{id}` | The caregiver or patient named on it | Server only — the Admin SDK bypasses rules |

Consequences worth stating explicitly:

- Revoking a pairing revokes data access **immediately**, server-side. It does
  not depend on the caregiver's app cooperating.
- Codes cannot be enumerated. A caregiver can `get` the exact code they were
  told and nothing else.
- A revoked pairing cannot be reactivated. Restoring access requires a fresh
  code, i.e. fresh consent from the patient.
- Everything not explicitly matched is denied.
- **The event log is append-only.** `type`, `patientId` and `occurredAt` are
  pinned on update, so nobody can rewrite what happened — only record that it
  was seen (caregiver) or withdrawn (patient). Delete is denied for every party:
  the record of an emergency is not the patient's to erase, and not the
  caregiver's either.
- Only the patient's own device may create events about them, so a caregiver
  cannot manufacture a fall on someone else's record.

## Pairing sequence

```
Patient device                Firestore                 Caregiver device
      │                           │                            │
      │  create pairingCode       │                            │
      │  (deletes any previous    │                            │
      │   unredeemed code)        │                            │
      ├──────────────────────────►│                            │
      │                           │                            │
      │   "K7M2PQ" ── read aloud ─────────────────────────────► │
      │                           │                            │
      │                           │  TRANSACTION:              │
      │                           │◄───────────────────────────┤
      │                           │  1. get pairingCodes/K7M2PQ│
      │                           │  2. reject if redeemed,    │
      │                           │     expired or missing     │
      │                           │  3. reject if already      │
      │                           │     actively paired        │
      │                           │  4. create pairings/{id}   │
      │                           │  5. mark code redeemed     │
      │                           │                            │
      │  listener fires:          │                            │
      │  caregiver appears        │                            │
      │◄──────────────────────────┤                            │
```

A transaction, not two writes: both devices are online at this moment, and a
consumed code with no pairing would leave the caregiver unable to retry and the
patient unable to see who has access.

## Cost and quota notes

- **Offline persistence is on.** Reads are served from the local cache when the
  device is offline, which is load-bearing on a patient's phone in a house with
  poor signal.
- Listeners are scoped with `SharingStarted.WhileSubscribed(5_000)` so they
  detach when the user leaves a screen. Firestore listeners cost battery, and
  this app sits on a phone all day.
- `patientName` / `caregiverName` are denormalised onto the pairing so listing
  linked people costs one query, not one query plus N document reads.
