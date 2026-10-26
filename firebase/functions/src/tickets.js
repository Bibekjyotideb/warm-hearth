// Support tickets from the app and the website.
//
//   tickets/{id}              number, uid?, name, email, category, subject, status, source, createdAt, updatedAt, lastFrom
//   tickets/{id}/messages/{m} from ("user" | "admin"), text, createdAt, authorEmail?
//
// Users create tickets and follow-ups through the callables below (validated, rate-limited).
// The admin panel replies by writing a message with from = "admin"; onTicketMessage emails
// the user and pushes a notification to their phone.

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const crypto = require("crypto");
const cfg = require("./config");
const { sendMail, SMTP_SECRETS } = require("./mailer");
const { templates } = require("./emails/templates");

const CATEGORIES = ["question", "problem", "billing", "refund", "feature", "deletion", "other"];
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

function clean(s, max) {
  return String(s ?? "").replace(/\u0000/g, "").trim().slice(0, max);
}

async function nextNumber(db) {
  // Short, readable, non-sequential: WH-7K3QP9
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = crypto.randomBytes(6);
  return "WH-" + Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}

/** Max 5 tickets per hour per IP / account. */
async function rateLimit(db, key) {
  const ref = db.doc(`rateLimits/${crypto.createHash("sha256").update(key).digest("hex").slice(0, 32)}`);
  const hour = Math.floor(Date.now() / 3_600_000);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const d = snap.exists && snap.get("hour") === hour ? snap.data() : { hour, count: 0 };
    if (d.count >= 5) throw new HttpsError("resource-exhausted", "Too many messages. Please try again in an hour.");
    tx.set(ref, { hour, count: d.count + 1, expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + 2 * 3_600_000) });
  });
}

exports.submitTicket = onCall({ region: cfg.REGION, secrets: SMTP_SECRETS }, async (req) => {
  const db = admin.firestore();
  const d = req.data || {};
  const uid = req.auth?.uid || null;
  const tokenEmail = req.auth?.token?.email || null;

  const ticket = {
    name: clean(d.name, 80),
    email: clean(tokenEmail || d.email, 120).toLowerCase(),
    category: CATEGORIES.includes(d.category) ? d.category : "question",
    subject: clean(d.subject, 140) || "Support request",
    source: d.source === "app" ? "app" : "web",
    appVersion: clean(d.appVersion, 20) || null,
    deviceId: clean(d.deviceId, 64) || null,
    locale: clean(d.locale, 16) || null,
  };
  const message = clean(d.message, 5000);
  if (!EMAIL_RE.test(ticket.email)) throw new HttpsError("invalid-argument", "Please enter a valid email address.");
  if (message.length < 5) throw new HttpsError("invalid-argument", "Please describe the problem.");
  if (clean(d.website, 10)) return { ok: true }; // honeypot field filled by bots

  await rateLimit(db, uid || req.rawRequest?.ip || ticket.email);

  const now = admin.firestore.FieldValue.serverTimestamp();
  const number = await nextNumber(db);
  const ref = db.collection("tickets").doc();
  await ref.set({ ...ticket, number, uid, status: "open", lastFrom: "user", createdAt: now, updatedAt: now, messageCount: 0 });
  await ref.collection("messages").add({ from: "user", text: message, createdAt: now });

  const t = templates.ticketReceived({ name: ticket.name, number, subject: ticket.subject, message });
  await sendMail({ to: ticket.email, from: "support", ...t });
  const a = templates.adminNewTicket({ number, ...ticket, message });
  await sendMail({ to: cfg.EMAIL.adminInbox, from: "support", replyTo: ticket.email, ...a });

  return { ok: true, ticketId: ref.id, number };
});

/** Follow-up message from a signed-in user on their own ticket (in-app). */
exports.addTicketMessage = onCall({ region: cfg.REGION }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const db = admin.firestore();
  const ticketId = clean(req.data?.ticketId, 64);
  const text = clean(req.data?.text, 5000);
  if (!ticketId || text.length < 1) throw new HttpsError("invalid-argument", "Empty message.");
  const ref = db.doc(`tickets/${ticketId}`);
  const snap = await ref.get();
  if (!snap.exists || snap.get("uid") !== req.auth.uid) throw new HttpsError("permission-denied", "Not your ticket.");
  await rateLimit(db, "msg:" + req.auth.uid);
  await ref.collection("messages").add({ from: "user", text, createdAt: admin.firestore.FieldValue.serverTimestamp() });
  return { ok: true };
});

/** Keeps ticket status in sync, and tells the user when support replies. */
exports.onTicketMessage = onDocumentCreated(
  { document: "tickets/{ticketId}/messages/{messageId}", region: cfg.REGION, secrets: SMTP_SECRETS },
  async (event) => {
    const msg = event.data?.data();
    if (!msg) return;
    const db = admin.firestore();
    const ref = db.doc(`tickets/${event.params.ticketId}`);
    const ticket = (await ref.get()).data();
    if (!ticket) return;

    await ref.update({
      lastFrom: msg.from,
      status: msg.close === true ? "closed" : msg.from === "admin" ? "waiting_on_user" : "open",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      messageCount: admin.firestore.FieldValue.increment(1),
      ...(msg.from === "admin" && !ticket.firstResponseAt ? { firstResponseAt: admin.firestore.FieldValue.serverTimestamp() } : {}),
    });

    if (msg.from !== "admin") return;
    const t = templates.ticketReply({ name: ticket.name, number: ticket.number, reply: msg.text });
    await sendMail({ to: ticket.email, from: ticket.category === "billing" || ticket.category === "refund" ? "billing" : "support", ...t });

    if (ticket.uid) {
      const user = (await db.doc(`users/${ticket.uid}`).get()).data();
      const tokens = user?.fcmTokens || [];
      if (tokens.length) {
        await admin.messaging().sendEachForMulticast({
          tokens,
          notification: { title: `${cfg.APP_NAME} Support replied`, body: msg.text.slice(0, 120) },
          data: { type: "ticket_reply", ticketId: event.params.ticketId, title: `${cfg.APP_NAME} Support`, body: msg.text.slice(0, 200) },
          android: { priority: "high", notification: { channelId: "alerts" } },
        }).catch(() => null);
      }
    }
  },
);

/**
 * Google Play's AI-generated content policy: users flag a bad AI answer without leaving the app.
 * Stores the answer (not the user's own message) for review; the admin inbox gets an email.
 */
exports.reportAiAnswer = onCall({ region: cfg.REGION, secrets: SMTP_SECRETS }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign-in required.");
  const db = admin.firestore();
  const d = req.data || {};
  const report = {
    uid: req.auth.uid,
    feature: ["scam", "read", "medicine"].includes(d.feature) ? d.feature : "other",
    reason: ["wrong", "harmful", "offensive", "other"].includes(d.reason) ? d.reason : "other",
    answer: clean(d.answer, 2000),
    appVersion: clean(d.appVersion, 20) || null,
    status: "new",
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  };
  await rateLimit(db, "ai:" + req.auth.uid);
  await db.collection("aiReports").add(report);
  await sendMail({
    to: cfg.EMAIL.adminInbox, from: "support",
    subject: `AI answer reported (${report.feature}, ${report.reason})`,
    text: `An AI answer was reported in the app.\n\nFeature: ${report.feature}\nReason: ${report.reason}\n\nAnswer:\n${report.answer}`,
    html: `<p>An AI answer was reported in the app.</p><p>Feature: ${report.feature}<br>Reason: ${report.reason}</p><pre style="white-space:pre-wrap">${report.answer.replace(/[<>&]/g, (c) => ({ "<": "&lt;", ">": "&gt;", "&": "&amp;" })[c])}</pre>`,
  });
  return { ok: true };
});
