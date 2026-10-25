// Account lifecycle: welcome email, and full account deletion (required by Google Play for
// any app that lets people create an account).

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const cfg = require("./config");
const { sendMail, SMTP_SECRETS } = require("./mailer");
const { templates } = require("./emails/templates");

/** Sends the welcome email once, the first time a family user's email appears. */
exports.onUserWritten = onDocumentWritten({ document: "users/{uid}", region: cfg.REGION, secrets: SMTP_SECRETS }, async (event) => {
  const after = event.data?.after?.data();
  if (!after?.email || after.welcomedAt) return;
  const t = templates.welcome({ name: after.displayName?.split(" ")[0] });
  if (await sendMail({ to: after.email, from: "support", ...t })) {
    await event.data.after.ref.set({ welcomedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
  }
});

/**
 * Deletes everything that belongs to the caller:
 *  - users/{uid}
 *  - their uid from every device's family list
 *  - devices they own (the senior's phone data), with contacts, medicines and events
 *  - their tickets
 *  - the Firebase Auth account
 * Purchase records are kept (anonymised) because tax law requires sales records.
 */
exports.deleteMyAccount = onCall({ region: cfg.REGION, secrets: SMTP_SECRETS }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const uid = req.auth.uid;
  const db = admin.firestore();
  const user = (await db.doc(`users/${uid}`).get()).data() || {};

  const linked = await db.collection("devices").where("family", "array-contains", uid).get();
  await Promise.all(linked.docs.map((d) => d.ref.update({ family: admin.firestore.FieldValue.arrayRemove(uid) })));

  const owned = await db.collection("devices").where("ownerUid", "==", uid).get();
  for (const d of owned.docs) await db.recursiveDelete(d.ref);

  const tickets = await db.collection("tickets").where("uid", "==", uid).get();
  for (const t of tickets.docs) await db.recursiveDelete(t.ref);

  // Subscription records are kept for tax law, without personal data. Deleting the account doesn't
  // cancel a Google Play subscription; the app and email tell users to cancel in Play first.
  const subs = await db.collection("subscriptions").where("uid", "==", uid).get();
  await Promise.all(subs.docs.map((p) => p.ref.update({ email: null, uid: "deleted", deviceIds: [] })));

  await db.recursiveDelete(db.doc(`users/${uid}`));
  await admin.auth().deleteUser(uid).catch(() => null);

  if (user.email) await sendMail({ to: user.email, from: "support", ...templates.accountDeleted({ name: user.displayName?.split(" ")[0] }) });
  return { ok: true };
});
