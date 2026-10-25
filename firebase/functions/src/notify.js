// Pushes SOS / missed-medicine alerts from a senior's phone to every linked family phone.

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const cfg = require("./config");

exports.notifyFamily = onDocumentCreated({ document: "devices/{deviceId}/events/{eventId}", region: cfg.REGION }, async (event) => {
  const ev = event.data && event.data.data();
  if (!ev) return;

  const db = admin.firestore();
  const device = (await db.doc(`devices/${event.params.deviceId}`).get()).data();
  if (!device || !Array.isArray(device.family) || device.family.length === 0) return;

  // SOS is free for everyone. Other alerts (missed medicine) are part of Premium.
  if (ev.type !== "sos" && device.premium !== true) return;

  const users = await Promise.all(device.family.map((uid) => db.doc(`users/${uid}`).get()));
  const tokens = [...new Set(users.flatMap((u) => u.get("fcmTokens") || []))];
  if (tokens.length === 0) return;

  const title = ev.type === "sos" ? "🆘 SOS" : `💊 ${cfg.APP_NAME}`;
  const data = { type: String(ev.type || ""), title, body: String(ev.message || "") };
  if (typeof ev.lat === "number" && typeof ev.lng === "number") {
    data.lat = String(ev.lat);
    data.lng = String(ev.lng);
  }

  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title, body: data.body },
    data,
    android: { priority: "high", notification: { channelId: "alerts", sound: "default", defaultVibrateTimings: true } },
  });

  // Drop tokens of uninstalled apps so the list doesn't grow forever.
  const dead = [];
  res.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") dead.push(tokens[i]);
  });
  if (dead.length) {
    await Promise.all(users.map((u) => u.ref.set({ fcmTokens: admin.firestore.FieldValue.arrayRemove(...dead) }, { merge: true })));
  }
});
