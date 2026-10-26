// Pushes events from a senior's phone (or from safetyWatch) to every linked family phone.

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const cfg = require("./config");

// free: sent on every plan. Safety and medicine alerts are part of Premium.
// quiet: a gentle notification instead of the alarm-style channel.
const TYPES = {
  sos:         { free: true,  title: "🆘 SOS" },
  call_me:     { free: true,  title: "📞 Call request", quiet: true },
  checkin:     { free: true,  title: "✅ All good today", quiet: true },
  here:        { free: true,  title: "👋 They answered", quiet: true },
  missed_dose: { free: false, title: "💊 Missed medicine" },
  no_checkin:  { free: false, title: "⏰ No check-in yet" },
  inactive:    { free: false, title: "⚠️ Phone not used" },
  offline:     { free: false, title: "📵 Phone offline" },
  low_battery: { free: false, title: "🔋 Battery low", quiet: true },
  silent:      { free: false, title: "🔕 Phone on silent" },
  heart_rate:  { free: false, title: "❤️ Heart rate reading", quiet: true },
};

exports.TYPES = TYPES;

exports.notifyFamily = onDocumentCreated({ document: "devices/{deviceId}/events/{eventId}", region: cfg.REGION }, async (event) => {
  const ev = event.data && event.data.data();
  const type = TYPES[ev && ev.type];
  if (!type) return;

  const db = admin.firestore();
  const device = (await db.doc(`devices/${event.params.deviceId}`).get()).data();
  if (!device || !Array.isArray(device.family) || device.family.length === 0) return;
  if (!type.free && device.premium !== true) return;
  // "She's OK today" pings can be switched off by the family.
  if (ev.type === "checkin" && device.settings && device.settings.checkIn && device.settings.checkIn.notifyOnOk === false) return;

  const users = await Promise.all(device.family.map((uid) => db.doc(`users/${uid}`).get()));
  const dead = [];

  for (const u of users) {
    const tokens = [...new Set(u.get("fcmTokens") || [])];
    if (tokens.length === 0) continue;
    const hindi = String(u.get("locale") || "").toLowerCase().startsWith("hi");
    const body = String((hindi && ev.message_hi) || ev.message || "");
    const data = { type: String(ev.type), title: type.title, body, deviceId: event.params.deviceId, quiet: type.quiet ? "1" : "0" };
    if (typeof ev.lat === "number" && typeof ev.lng === "number") {
      data.lat = String(ev.lat);
      data.lng = String(ev.lng);
    }
    // Data-only message: the app builds the notification itself (alarm channel for urgent types,
    // action buttons such as "Turn ringer on"), even when it is in the background.
    const res = await admin.messaging().sendEachForMulticast({ tokens, data, android: { priority: "high" } });
    res.responses.forEach((r, i) => {
      const code = r.error && r.error.code;
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") dead.push([u.ref, tokens[i]]);
    });
  }

  // Drop tokens of uninstalled apps so the lists don't grow forever.
  await Promise.all(dead.map(([ref, t]) => ref.set({ fcmTokens: admin.firestore.FieldValue.arrayRemove(t) }, { merge: true })));
});
