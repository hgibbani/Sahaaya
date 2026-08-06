/**
 * SAHAAYA - Cloud Functions
 *
 * One job: when an event is written to Firestore, push it to every caregiver
 * actively paired with that patient.
 *
 * This has to be server-side. Sending FCM to another device requires the
 * Firebase Admin credentials, and embedding those in the app would let anyone
 * who unpacks the APK send push notifications to any Sahaaya user. There is no
 * client-only version of this that is safe.
 *
 * Deploy:  firebase deploy --only functions
 * Requires the Blaze plan. See docs/FIREBASE.md for what works without it.
 */

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const logger = require("firebase-functions/logger");

initializeApp();

// asia-south1 (Mumbai) - closest region to the users this is built for, and
// latency matters when the payload is a fall alert.
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

const db = getFirestore();

/** Channel ids must match SahaayaNotificationChannels in the app. */
const EMERGENCY_CHANNEL = "sahaaya_emergency";
const UPDATES_CHANNEL = "sahaaya_updates";

/** Mirrors EventType.severity in the domain layer. */
const CRITICAL_TYPES = new Set(["fall", "sos", "geofence_exit"]);

/**
 * Fans an event out to the patient's caregivers.
 *
 * Fires on create only. Updates are status changes - an acknowledgement or a
 * cancellation - and re-notifying on those would mean a caregiver's phone
 * buzzing again the moment they answered it.
 */
exports.onEventCreated = onDocumentCreated("events/{eventId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return;

  const data = snapshot.data();
  const eventId = event.params.eventId;
  const patientId = data.patientId;

  if (!patientId) {
    logger.warn("Event has no patientId, ignoring", { eventId });
    return;
  }

  // A cancelled event should never have been created, but if the patient
  // cancels inside the write window there is no point waking anybody.
  if (data.status === "cancelled") {
    logger.info("Event already cancelled, not notifying", { eventId });
    return;
  }

  const isCritical = CRITICAL_TYPES.has(data.type);

  // --- who should hear about this ---
  const pairings = await db
    .collection("pairings")
    .where("patientId", "==", patientId)
    .where("status", "==", "active")
    .get();

  if (pairings.empty) {
    logger.info("No active caregivers for patient", { patientId, eventId });
    return;
  }

  const caregiverIds = pairings.docs.map((doc) => doc.data().caregiverId);

  // --- collect their device tokens ---
  const caregiverDocs = await db.getAll(
    ...caregiverIds.map((id) => db.collection("users").doc(id))
  );

  /** @type {{caregiverId: string, tokens: string[]}[]} */
  const targets = [];
  for (const doc of caregiverDocs) {
    if (!doc.exists) continue;
    const caregiver = doc.data();
    const tokens = Array.isArray(caregiver.fcmTokens) ? caregiver.fcmTokens : [];
    if (tokens.length > 0) {
      targets.push({ caregiverId: doc.id, tokens });
    }
  }

  if (targets.length === 0) {
    logger.warn("Caregivers exist but none have a device token", {
      patientId,
      eventId,
    });
    return;
  }

  const patientName = data.patientName || "Your patient";
  const title = isCritical
    ? `${patientName}: ${titleFor(data.type)}`
    : titleFor(data.type);
  const body = data.summary || titleFor(data.type);

  const allTokens = targets.flatMap((t) => t.tokens);

  const message = {
    tokens: allTokens,
    // Data-only would be dropped when the app is killed on many OEM builds, and
    // a notification payload is what guarantees the system tray shows it.
    notification: { title, body },
    data: {
      eventId,
      patientId,
      type: String(data.type || ""),
      category: isCritical ? "emergency" : "update",
      title,
      body,
    },
    android: {
      priority: "high",
      notification: {
        channelId: isCritical ? EMERGENCY_CHANNEL : UPDATES_CHANNEL,
        // Collapse repeats of the same event, never across different events -
        // two separate falls must produce two notifications.
        tag: eventId,
        sound: isCritical ? "default" : undefined,
      },
      // Critical alerts must survive Doze. Normal priority would let Android
      // hold a fall alert until the next maintenance window.
      ttl: isCritical ? 0 : 3600 * 1000,
    },
  };

  let response;
  try {
    response = await getMessaging().sendEachForMulticast(message);
  } catch (error) {
    logger.error("FCM send failed", { eventId, error: String(error) });
    return;
  }

  logger.info("Alert dispatched", {
    eventId,
    type: data.type,
    sent: response.successCount,
    failed: response.failureCount,
  });

  await Promise.all([
    recordDelivery(eventId, patientId, targets, response, allTokens),
    pruneDeadTokens(targets, response, allTokens),
  ]);
});

/**
 * Writes the delivery log.
 *
 * So a caregiver can tell the difference between "no alert was sent" and "an
 * alert was sent but my phone never showed it" - which are very different
 * conversations to have after an incident.
 */
async function recordDelivery(eventId, patientId, targets, response, allTokens) {
  const perCaregiver = targets.map((target) => {
    const delivered = target.tokens.some((token) => {
      const index = allTokens.indexOf(token);
      return index >= 0 && response.responses[index]?.success;
    });
    return { caregiverId: target.caregiverId, delivered };
  });

  const writes = perCaregiver.map((entry) =>
    db.collection("notifications").add({
      eventId,
      patientId,
      caregiverId: entry.caregiverId,
      delivered: entry.delivered,
      sentAt: FieldValue.serverTimestamp(),
    })
  );

  await Promise.all(writes);
}

/**
 * Removes tokens FCM has told us are dead.
 *
 * Tokens rotate on reinstall and restore, and a stale one fails silently: the
 * send reports success and nobody's phone rings. Left unpruned, a caregiver who
 * has reinstalled twice accumulates tokens that quietly swallow their alerts.
 */
async function pruneDeadTokens(targets, response, allTokens) {
  const dead = new Set();

  response.responses.forEach((result, index) => {
    if (result.success) return;
    const code = result.error?.code || "";
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token" ||
      code === "messaging/invalid-argument"
    ) {
      dead.add(allTokens[index]);
    }
  });

  if (dead.size === 0) return;

  const writes = targets
    .map((target) => {
      const stale = target.tokens.filter((token) => dead.has(token));
      if (stale.length === 0) return null;
      return db
        .collection("users")
        .doc(target.caregiverId)
        .update({ fcmTokens: FieldValue.arrayRemove(...stale) });
    })
    .filter(Boolean);

  await Promise.all(writes);
  logger.info("Pruned dead FCM tokens", { count: dead.size });
}

/** Human-readable titles. Kept in step with EventType in the domain layer. */
function titleFor(type) {
  switch (type) {
    case "fall":
      return "Possible fall";
    case "sos":
      return "Emergency SOS";
    case "geofence_exit":
      return "Left the safe zone";
    case "inactivity":
      return "No movement for a long time";
    case "medication_missed":
      return "Medicine not taken";
    default:
      return "Sahaaya alert";
  }
}
