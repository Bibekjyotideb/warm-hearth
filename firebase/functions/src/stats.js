// Nightly snapshot of the numbers the admin panel charts over time.
// (The panel also runs live counts; this keeps history cheap: one small doc per day.)
//
// Phones report activity themselves (see app util/Telemetry.kt):
//   devices/{id}: createdAt, lastActiveAt, activeDays, appVersion, premium,
//                 funnel.{setHome, addedContact, linkedFamily, addedMedicine, testedSos}
//   users/{uid}:  createdAt, lastActiveAt, premium, email

const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const cfg = require("./config");

const DAY = 86_400_000;
const count = async (q) => (await q.count().get()).data().count;
const ts = (ms) => admin.firestore.Timestamp.fromMillis(ms);

async function snapshot(db, now = Date.now()) {
  const devices = db.collection("devices");
  const users = db.collection("users");
  const since = (days) => ts(now - days * DAY);

  const [
    seniorTotal, seniorNew, seniorDau, seniorWau, seniorMau, seniorPremium,
    familyTotal, familyNew, familyMau, familyPremium,
    fSetHome, fContact, fFamily, fMed, fSos,
    subsNew, refundsNew, ticketsOpen, ticketsNew,
    subsActive, subsTrial, subsCanceled30, subsYearly, subsIndia,
  ] = await Promise.all([
    count(devices), count(devices.where("createdAt", ">=", since(1))),
    count(devices.where("lastActiveAt", ">=", since(1))), count(devices.where("lastActiveAt", ">=", since(7))),
    count(devices.where("lastActiveAt", ">=", since(30))), count(devices.where("premium", "==", true)),
    count(users), count(users.where("createdAt", ">=", since(1))),
    count(users.where("lastActiveAt", ">=", since(30))), count(users.where("premium", "==", true)),
    count(devices.where("funnel.setHome", "==", true)), count(devices.where("funnel.addedContact", "==", true)),
    count(devices.where("funnel.linkedFamily", "==", true)), count(devices.where("funnel.addedMedicine", "==", true)),
    count(devices.where("funnel.testedSos", "==", true)),
    count(db.collection("subscriptions").where("createdAt", ">=", since(1))),
    count(db.collection("subscriptions").where("refundedAt", ">=", since(1))),
    count(db.collection("tickets").where("status", "in", ["open", "waiting_on_user"])),
    count(db.collection("tickets").where("createdAt", ">=", since(1))),
    count(db.collection("subscriptions").where("entitled", "==", true)),
    count(db.collection("subscriptions").where("entitled", "==", true).where("inTrial", "==", true)),
    count(db.collection("subscriptions").where("canceledAt", ">=", since(30))),
    count(db.collection("subscriptions").where("entitled", "==", true).where("basePlanId", "==", "yearly")),
    count(db.collection("subscriptions").where("entitled", "==", true).where("regionCode", "==", "IN")),
  ]);

  return {
    date: new Date(now).toLocaleDateString("en-CA", { timeZone: cfg.TIMEZONE }),
    seniorPhones: { total: seniorTotal, new: seniorNew, dau: seniorDau, wau: seniorWau, mau: seniorMau, premium: seniorPremium },
    familyUsers: { total: familyTotal, new: familyNew, mau: familyMau, premium: familyPremium },
    funnel: { installed: seniorTotal, setHome: fSetHome, addedContact: fContact, linkedFamily: fFamily, addedMedicine: fMed, testedSos: fSos, premium: seniorPremium },
    purchases: { new: subsNew, refunds: refundsNew },
    subscriptions: { active: subsActive, trial: subsTrial, paying: subsActive - subsTrial, yearly: subsYearly, india: subsIndia, canceled30d: subsCanceled30 },
    tickets: { open: ticketsOpen, new: ticketsNew },
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  };
}

exports.dailyStats = onSchedule({ schedule: "every day 00:30", timeZone: cfg.TIMEZONE, region: cfg.REGION }, async () => {
  const db = admin.firestore();
  // Snapshot "yesterday" as of just after midnight IST.
  const s = await snapshot(db, Date.now() - 31 * 60_000);
  await db.doc(`stats/${s.date}`).set(s);
});

/**
 * Privacy policy promise: data of phones and accounts inactive for 24 months is deleted.
 * Runs on the 1st of every month.
 */
exports.cleanupInactive = onSchedule({ schedule: "0 3 1 * *", timeZone: cfg.TIMEZONE, region: cfg.REGION }, async () => {
  const db = admin.firestore();
  const cutoff = ts(Date.now() - 730 * DAY);
  const old = await db.collection("devices").where("lastActiveAt", "<", cutoff).limit(500).get();
  for (const d of old.docs) {
    await db.recursiveDelete(d.ref);
    await db.doc(`deviceTokens/${d.id}`).delete().catch(() => null);
    await db.doc(`alertState/${d.id}`).delete().catch(() => null);
    await admin.storage().bucket().deleteFiles({ prefix: `notes/${d.id}/` }).catch(() => null);
  }
  const oldUsers = await db.collection("users").where("lastActiveAt", "<", cutoff).limit(500).get();
  for (const u of oldUsers.docs) {
    await db.recursiveDelete(u.ref);
    await admin.auth().deleteUser(u.id).catch(() => null);
  }
});

exports.snapshot = snapshot;
