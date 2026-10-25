// Sends a push notification to every family phone linked to a senior's phone
// whenever that phone writes an alert (SOS, missed dose) to devices/{id}/events.
//
// Deploy:  cd firebase && npm --prefix functions install && firebase deploy --only functions,firestore:rules
// Needs the Firebase "Blaze" (pay-as-you-go) plan. The free monthly quota covers
// millions of invocations, so a small app costs ₹0 in practice.

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const admin = require("firebase-admin");

admin.initializeApp();
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

exports.notifyFamily = onDocumentCreated("devices/{deviceId}/events/{eventId}", async (event) => {
  const ev = event.data && event.data.data();
  if (!ev) return;

  const db = admin.firestore();
  const deviceRef = db.doc(`devices/${event.params.deviceId}`);
  const device = (await deviceRef.get()).data();
  if (!device || !Array.isArray(device.family) || device.family.length === 0) return;

  // SOS is free for everyone. Other alerts (missed medicine) are part of the lifetime unlock.
  if (ev.type !== "sos" && device.premium !== true) return;

  const users = await Promise.all(device.family.map((uid) => db.doc(`users/${uid}`).get()));
  const tokens = [...new Set(users.flatMap((u) => u.get("fcmTokens") || []))];
  if (tokens.length === 0) return;

  const title = ev.type === "sos" ? "🆘 SOS" : "💊 WarmHearth";
  const data = { type: String(ev.type || ""), title, body: String(ev.message || "") };
  if (typeof ev.lat === "number" && typeof ev.lng === "number") {
    data.lat = String(ev.lat);
    data.lng = String(ev.lng);
  }

  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title, body: data.body },
    data,
    android: {
      priority: "high",
      notification: { channelId: "alerts", sound: "default", defaultVibrateTimings: true },
    },
  });

  // Remove tokens of uninstalled apps so the list doesn't grow forever.
  const dead = [];
  res.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      dead.push(tokens[i]);
    }
  });
  if (dead.length) {
    await Promise.all(
      users.map((u) => u.ref.set({ fcmTokens: admin.firestore.FieldValue.arrayRemove(...dead) }, { merge: true })),
    );
  }
});
