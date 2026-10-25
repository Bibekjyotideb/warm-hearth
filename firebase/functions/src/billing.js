// Subscriptions: server-side verification, renewals, cancellations, refunds.
//
// Play Console product: subscription "warmhearth_premium" with base plans
//   "monthly" (P1M)  India ₹149 · rest of world US$4.99
//   "yearly"  (P1Y)  India ₹899 · rest of world US$39.99
// each with a 7-day free-trial offer for new customers (offer id "free-trial-7d").
//
// The app sends the purchase token to verifyPurchase; we ask Google whether it's real and active,
// then grant premium. Every change afterwards (renewal, cancel, payment problem, refund, expiry)
// arrives as a Real-time Developer Notification on Pub/Sub topic "play-billing" and is re-checked
// with Google. Premium is never set by the app itself (see firestore.rules).
//
//   subscriptions/{purchaseToken}: uid, deviceIds, email, state, basePlanId, offerId, inTrial,
//     autoRenew, expiryTime, regionCode, startTime, latestOrderId, createdAt, updatedAt

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");
const { androidpublisher, auth } = require("@googleapis/androidpublisher");
const cfg = require("./config");
const { sendMail, SMTP_SECRETS } = require("./mailer");
const { templates } = require("./emails/templates");

let publisher;
function api() {
  if (!publisher) {
    publisher = androidpublisher({ version: "v3", auth: new auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }) });
  }
  return publisher;
}

// States in which the user keeps premium. CANCELED = auto-renew off but paid period not over yet.
const ENTITLED = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]);
const shortState = (s) => String(s || "").replace("SUBSCRIPTION_STATE_", "").toLowerCase();

async function fetchSubscription(token) {
  const s = (await api().purchases.subscriptionsv2.get({ packageName: cfg.PACKAGE_NAME, token })).data;
  const item = (s.lineItems || []).find((l) => l.productId === cfg.SUBSCRIPTION_ID) || (s.lineItems || [])[0] || {};
  const expiry = item.expiryTime ? Date.parse(item.expiryTime) : 0;
  const offerId = item.offerDetails?.offerId || null;
  return {
    raw: s,
    state: shortState(s.subscriptionState),
    entitled: ENTITLED.has(s.subscriptionState) && expiry > Date.now(),
    expiry,
    basePlanId: item.offerDetails?.basePlanId || null,
    offerId,
    // On the trial offer and still inside the first 7 days.
    inTrial: !!offerId && offerId.includes("trial") && Date.now() < (s.startTime ? Date.parse(s.startTime) : 0) + 7 * 86_400_000 + 3_600_000,
    autoRenew: item.autoRenewingPlan?.autoRenewEnabled ?? false,
    acknowledged: s.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    regionCode: s.regionCode || null,
    startTime: s.startTime ? Date.parse(s.startTime) : Date.now(),
    latestOrderId: s.latestOrderId || null,
    linkedPurchaseToken: s.linkedPurchaseToken || null,
  };
}

/** Recomputes premium for a user and their phones from all their subscriptions. */
async function recomputeEntitlement(db, uid) {
  if (!uid || uid === "deleted") return false;
  const subs = await db.collection("subscriptions").where("uid", "==", uid).get();
  const active = subs.docs.filter((d) => d.get("entitled") === true && (d.get("expiryTime")?.toMillis?.() || 0) > Date.now());
  const premium = active.length > 0;
  const devices = new Set();
  subs.docs.forEach((d) => (d.get("deviceIds") || []).forEach((id) => id !== "-" && devices.add(id)));
  const activeDevices = new Set();
  active.forEach((d) => (d.get("deviceIds") || []).forEach((id) => id !== "-" && activeDevices.add(id)));

  const batch = db.batch();
  batch.set(db.doc(`users/${uid}`), { premium }, { merge: true });
  for (const id of devices) batch.set(db.doc(`devices/${id}`), { premium: activeDevices.has(id) }, { merge: true });
  await batch.commit();
  return premium;
}

async function saveSubscription(db, token, sub, extra = {}) {
  const ref = db.doc(`subscriptions/${token}`);
  const before = (await ref.get()).data();
  await ref.set({
    token,
    state: sub.state,
    entitled: sub.entitled,
    basePlanId: sub.basePlanId,
    offerId: sub.offerId,
    inTrial: sub.inTrial,
    autoRenew: sub.autoRenew,
    expiryTime: admin.firestore.Timestamp.fromMillis(sub.expiry || Date.now()),
    regionCode: sub.regionCode,
    startTime: admin.firestore.Timestamp.fromMillis(sub.startTime),
    latestOrderId: sub.latestOrderId,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    ...(before ? {} : { createdAt: admin.firestore.FieldValue.serverTimestamp() }),
    ...extra,
  }, { merge: true });
  // An upgrade/resubscribe replaces an older token: retire it.
  if (sub.linkedPurchaseToken) {
    await db.doc(`subscriptions/${sub.linkedPurchaseToken}`).set({ entitled: false, state: "replaced" }, { merge: true }).catch(() => null);
  }
  return before;
}

const fmtDate = (ms) => new Date(ms).toLocaleDateString("en-IN", { day: "numeric", month: "long", year: "numeric" });

exports.verifyPurchase = onCall({ region: cfg.REGION, secrets: SMTP_SECRETS, enforceAppCheck: true }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const token = String(req.data?.purchaseToken || "");
  const productId = String(req.data?.productId || cfg.SUBSCRIPTION_ID);
  const deviceIds = (Array.isArray(req.data?.deviceIds) ? req.data.deviceIds : []).slice(0, 10).map(String);
  if (!token || productId !== cfg.SUBSCRIPTION_ID) throw new HttpsError("invalid-argument", "Bad purchase.");

  let sub;
  try {
    sub = await fetchSubscription(token);
  } catch (e) {
    logger.warn("Play verify failed", e.message);
    throw new HttpsError("failed-precondition", "Could not verify with Google Play. Try again shortly.");
  }
  if (!sub.acknowledged && sub.entitled) {
    await api().purchases.subscriptions.acknowledge({ packageName: cfg.PACKAGE_NAME, subscriptionId: productId, token, requestBody: {} }).catch(() => null);
  }

  const db = admin.firestore();
  const uid = req.auth.uid;
  const allowed = [];
  for (const id of deviceIds) {
    const d = (await db.doc(`devices/${id}`).get()).data();
    if (d && (d.ownerUid === uid || (d.family || []).includes(uid))) allowed.push(id);
  }
  const user = (await db.doc(`users/${uid}`).get()).data() || {};
  const before = await saveSubscription(db, token, sub, {
    uid,
    email: user.email || null,
    deviceIds: admin.firestore.FieldValue.arrayUnion(...(allowed.length ? allowed : ["-"])),
  });
  const premium = await recomputeEntitlement(db, uid);

  if (!before && user.email && sub.entitled) {
    const t = templates.subscriptionStarted({
      name: user.displayName?.split(" ")[0],
      plan: sub.basePlanId === "yearly" ? "Yearly" : "Monthly",
      inTrial: sub.inTrial,
      nextDate: fmtDate(sub.expiry),
      orderId: sub.latestOrderId,
    });
    await sendMail({ to: user.email, from: "billing", ...t });
  }
  return { premium, state: sub.state, inTrial: sub.inTrial, expiry: sub.expiry, devices: allowed };
});

/** Real-time Developer Notifications from Google Play. */
exports.playRtdn = onMessagePublished({ topic: "play-billing", region: cfg.REGION, secrets: SMTP_SECRETS }, async (event) => {
  const msg = event.data.message.json || {};
  if (msg.testNotification) { logger.info("RTDN test received"); return; }
  const db = admin.firestore();

  // Refund or chargeback of a subscription payment
  if (msg.voidedPurchaseNotification) {
    const token = msg.voidedPurchaseNotification.purchaseToken;
    const ref = db.doc(`subscriptions/${token}`);
    const s = (await ref.get()).data();
    if (!s) return;
    await ref.set({ entitled: false, state: "refunded", refundedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
    await recomputeEntitlement(db, s.uid);
    if (s.email) await sendMail({ to: s.email, from: "billing", ...templates.refundProcessed({ orderId: msg.voidedPurchaseNotification.orderId || s.latestOrderId }) });
    return;
  }

  const n = msg.subscriptionNotification;
  if (!n?.purchaseToken) return;
  const ref = db.doc(`subscriptions/${n.purchaseToken}`);
  const stored = (await ref.get()).data();
  let sub;
  try { sub = await fetchSubscription(n.purchaseToken); } catch (e) { logger.error("RTDN fetch failed", e.message); throw e; } // retry
  await saveSubscription(db, n.purchaseToken, sub, n.notificationType === 3 ? { canceledAt: admin.firestore.FieldValue.serverTimestamp() } : {});
  if (!stored?.uid) return; // purchase not yet verified by the app; verifyPurchase will link it
  await recomputeEntitlement(db, stored.uid);

  const email = stored.email;
  if (!email) return;
  // notificationType: 3 CANCELED, 5 ON_HOLD, 6 IN_GRACE_PERIOD, 12 REVOKED, 13 EXPIRED
  if (n.notificationType === 3) {
    await sendMail({ to: email, from: "billing", ...templates.subscriptionCanceled({ endDate: fmtDate(sub.expiry) }) });
  } else if (n.notificationType === 5 || n.notificationType === 6) {
    await sendMail({ to: email, from: "billing", ...templates.paymentProblem({ onHold: n.notificationType === 5 }) });
  }
});

/**
 * Daily: emails a reminder 2 days before a free trial turns into a paid subscription (fair, and it
 * prevents angry refund requests), and expires premium if a notification was ever missed.
 */
exports.subscriptionDaily = onSchedule({ schedule: "every day 10:00", timeZone: cfg.TIMEZONE, region: cfg.REGION, secrets: SMTP_SECRETS }, async () => {
  const db = admin.firestore();
  const now = Date.now();
  const inTwoDays = await db.collection("subscriptions")
    .where("inTrial", "==", true).where("autoRenew", "==", true)
    .where("expiryTime", ">=", admin.firestore.Timestamp.fromMillis(now + 36 * 3_600_000))
    .where("expiryTime", "<", admin.firestore.Timestamp.fromMillis(now + 60 * 3_600_000)).get();
  for (const d of inTwoDays.docs) {
    const s = d.data();
    if (!s.email || s.trialReminderSent) continue;
    await sendMail({ to: s.email, from: "billing", ...templates.trialEnding({ plan: s.basePlanId === "yearly" ? "Yearly" : "Monthly", chargeDate: fmtDate(s.expiryTime.toMillis()) }) });
    await d.ref.set({ trialReminderSent: true }, { merge: true });
  }

  const lapsed = await db.collection("subscriptions").where("entitled", "==", true).where("expiryTime", "<", admin.firestore.Timestamp.fromMillis(now - 3_600_000)).limit(200).get();
  for (const d of lapsed.docs) {
    try {
      const sub = await fetchSubscription(d.id);
      await saveSubscription(db, d.id, sub);
    } catch {
      await d.ref.set({ entitled: false, state: "expired" }, { merge: true });
    }
    await recomputeEntitlement(db, d.get("uid"));
  }
});

