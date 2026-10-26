// Family safety: alerts the server raises on its own, remote commands, and photo-note cleanup.
//
//   safetyWatch     every 15 min: missed "I'm OK", phone unused, low battery, on silent, offline.
//                   Server-side on purpose: a phone that died can't report that it died.
//   sendCommand     family taps "Ring her phone" / "Turn ringer on": push a data message to the senior phone.
//   onNoteDeleted   a photo note expired (TTL) or was deleted: remove the photo from Storage.

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const cfg = require("./config");
const { evaluate } = require("./safetyRules");

const NINETY_DAYS = 90 * 86_400_000;

exports.safetyWatch = onSchedule({ schedule: "every 15 minutes", region: cfg.REGION, timeoutSeconds: 300 }, async () => {
  const db = admin.firestore();
  // Safety alerts are a Premium feature. (A few thousand reads per run at most; fine for years.)
  let cursor = null;
  for (;;) {
    let q = db.collection("devices").where("premium", "==", true).orderBy(admin.firestore.FieldPath.documentId()).limit(300);
    if (cursor) q = q.startAfter(cursor);
    const page = await q.get();
    if (page.empty) break;
    await Promise.all(page.docs.map((d) => checkDevice(db, d).catch((e) => console.error("safety", d.id, e))));
    cursor = page.docs[page.docs.length - 1];
    if (page.size < 300) break;
  }
});

async function checkDevice(db, snap) {
  const device = snap.data();
  if (!Array.isArray(device.family) || device.family.length === 0 || !device.status) return;
  const stateRef = db.doc(`alertState/${snap.id}`);
  const state = (await stateRef.get()).data() || {};
  const { alerts, state: next } = evaluate(device, state, new Date());
  if (JSON.stringify(next) !== JSON.stringify({ sent: state.sent || {} })) await stateRef.set(next);
  // Each alert becomes an event: it shows in the family app's history and notifyFamily pushes it.
  await Promise.all(alerts.map((a) => snap.ref.collection("events").add({
    ...a,
    source: "server",
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + NINETY_DAYS),
  })));
}

exports.sendCommand = onDocumentCreated({ document: "devices/{deviceId}/commands/{commandId}", region: cfg.REGION }, async (event) => {
  const cmd = event.data && event.data.data();
  if (!cmd) return;
  const db = admin.firestore();
  const tokenDoc = await db.doc(`deviceTokens/${event.params.deviceId}`).get();
  const token = tokenDoc.get("token");
  if (!token) return event.data.ref.update({ error: "no_token" });
  try {
    await admin.messaging().send({
      token,
      data: { kind: "command", id: event.params.commandId, type: String(cmd.type || ""), byName: String(cmd.byName || "").slice(0, 40) },
      // High priority wakes the phone from Doze. Commands older than 10 minutes are pointless.
      android: { priority: "high", ttl: 600_000 },
    });
    await event.data.ref.update({ sentAt: admin.firestore.FieldValue.serverTimestamp() });
  } catch (e) {
    await event.data.ref.update({ error: String(e.code || e.message).slice(0, 100) });
  }
});

exports.onNoteDeleted = onDocumentDeleted({ document: "devices/{deviceId}/notes/{noteId}", region: cfg.REGION }, async (event) => {
  const path = event.data && event.data.get("storagePath");
  if (typeof path === "string" && path.startsWith(`notes/${event.params.deviceId}/`)) {
    await admin.storage().bucket().file(path).delete({ ignoreNotFound: true });
  }
});
