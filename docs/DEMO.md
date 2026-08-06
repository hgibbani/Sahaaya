# Demo script

Written for Demo-1. Roughly 8 minutes end to end.

## Before you start

**Two devices.** One phone as the patient, one phone or emulator as the
caregiver. You cannot demonstrate this properly on one screen — the whole point
is that something happens *here* and arrives *there*.

**Checklist**

- [ ] Both devices signed in to different accounts (one patient, one caregiver)
- [ ] They are paired (see step 2 if not)
- [ ] Notification permission granted on both
- [ ] Location set to **Allow all the time** on the patient device
- [ ] Physical activity permission granted on the patient device
- [ ] Both devices online
- [ ] Debug build installed on the patient device — Developer mode is not in release
- [ ] Caregiver app **open on the alerts screen** if you have not deployed the
      Cloud Function (see [Delivery](#a-note-on-delivery) below)

---

## 1. Sign in — 1 min

Open both apps. Show that each lands on a different dashboard from the same
build: the patient sees a large SOS button, the caregiver sees a list of
patients.

> "The role is chosen once at registration. It decides the dashboard, the
> Firestore security rules, and which half of a pairing the account occupies."

## 2. Pair — 1 min

**Patient:** tap *Link a caregiver* → *Make a code*. A six-character code
appears with a live countdown.

**Caregiver:** tap *Link another patient* → type the code → *Link patient*.

Show the patient's screen updating with the caregiver's name **without a
refresh** — that is the Firestore listener.

> "Possession of a 15-minute code is the patient's consent. The alphabet
> excludes I, O, 0 and 1 because it gets read aloud over a phone. Either side
> can revoke, and revoking cuts data access server-side immediately."

## 3. Fall detection — 2 min

**Patient device:** *Developer mode* → *Simulate a fall*.

The countdown screen appears immediately — five seconds, one huge number, two
palm-sized buttons.

**Run it twice:**

| Take | What you do | What to say |
|---|---|---|
| First | Tap **I'm fine — cancel** | "The alert is withdrawn. It stays in the log as cancelled, because a pattern of cancelled falls means the sensitivity is wrong." |
| Second | Let it run to zero | "Nobody answered, so the alert stands." |

**Caregiver device:** the fall appears at the top of the dashboard. Tap it —
event detail with impact force, orientation change, timestamp and location.
Tap *Open in maps*.

> "The event is written to Firestore **before** the countdown, not after. A
> phone that dies on impact has already reported the fall. Waiting five seconds
> first would lose exactly the falls that matter most."

Tap **I am dealing with this** on the caregiver device, and show the patient's
alert count drop.

## 4. SOS — 1 min

**Patient:** press and hold the red button. Show the ring filling and the count
down from 3. **Release early once** to show nothing is sent, then hold the full
three seconds.

**Caregiver:** the SOS arrives with the patient's location.

> "Hold, not tap. It sits where a pocket will brush against it, and a
> tap-to-send button would produce false emergencies until caregivers learned to
> ignore it."

## 5. Safe zone — 1 min

**Patient:** *Monitoring settings* → *Safe zone* → *Set safe zone here* →
adjust the radius slider.

Then *Developer mode* → *Simulate leaving the safe zone*.

**Caregiver:** the geofence alert arrives with the distance beyond the boundary.

> "The radius floor is 100 metres. Below that, ordinary GPS drift indoors
> manufactures exits that never happened."

## 6. Medication — 1.5 min

**Patient:** *My medicines* → *Add a medicine* → name, dosage, tap a couple of
preset times → *Add medicine*.

Then *Developer mode* → *Sweep for missed doses*.

> If nothing is overdue it says so honestly rather than inventing an event. To
> show a real missed dose, add a medicine with a time earlier today, wait for
> the reminder, ignore it, then run the sweep.

**Caregiver:** open the patient's *Medicines* — adherence percentage and the
missed-dose count.

> "Skipped and missed are different. A patient who declines has told us
> something; one who never answered has not, and only the second is worth
> waking a caregiver for."

## 7. Timeline — 30 s

**Caregiver:** *All alerts*. Everything from the demo, newest first. Tap a
summary card to filter.

> "Counts are of unresolved events only. A caregiver does not need to be told
> about three falls they have already dealt with."

---

## A note on delivery

Two paths exist, and it is worth being straight about which is running:

| Path | Reaches a caregiver whose app is closed | Needs |
|---|---|---|
| **Cloud Function** (`functions/`) | Yes | Firebase **Blaze** plan |
| **In-app listener** (`InAppAlertNotifier`) | No — app must be running | Nothing |

Sending FCM to another device requires Admin credentials, which cannot go in
the APK: anyone who unpacked it could push notifications to every Sahaaya user.
So the Cloud Function is the real answer.

**If you have not deployed it,** keep the caregiver app open on the alerts
screen. Everything still demonstrates end to end — the same Firestore write, the
same security rules, the same timeline — only the push-while-closed step is
missing. Say so if asked; it is a deployment step, not a gap in the code.

**To deploy** (needs Blaze, which is free at this volume):

```bash
cd functions && npm install && cd ..
firebase deploy --only functions,firestore:rules,firestore:indexes
```

---

## If something goes wrong

| Symptom | Cause | Fix |
|---|---|---|
| Alerts do not arrive at all | Composite indexes not deployed | `firebase deploy --only firestore:indexes` |
| Geofence never fires | Location is "While using the app" | Set to **Allow all the time** in Android settings |
| Reminders arrive late | Exact alarms not granted | Android settings → Alarms & reminders |
| Developer mode card missing | Release build installed | Install the debug build |
| Nothing on the caregiver screen | Pairing revoked, or wrong account | Check *Your patients* on the caregiver dashboard |

## Questions to expect

**"Is the fall detection real, or just the button?"**
Real. `FallDetectionEngine` is a three-stage state machine — near free fall,
impact above threshold, then stillness in a new orientation — with 10 unit tests
covering walking, sitting, a dropped phone and a caught stumble. Developer mode
calls the same use case the sensor calls; only the accelerometer trace is
skipped. Run `gradle :sensor:test` to show the tests.

**"Why not just check for a big impact?"**
Because setting a phone down hard produces the same spike. The orientation
check is what separates a dropped phone from a fallen person.

**"What stops a caregiver reading anyone's medical data?"**
The Firestore rules. A caregiver can read a patient's documents only while an
`active` pairing exists. Show `firebase/firestore.rules`.

**"What happens offline?"**
Firestore persistence serves reads from cache and queues writes. An event
raised offline is delivered when the connection returns.
