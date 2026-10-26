// WarmHearth backend (Firebase Cloud Functions, 2nd gen, region asia-south1).
//
//   notifyFamily     push SOS, call requests, check-ins and safety alerts to family phones
//   safetyWatch      every 15 min: missed check-in, phone unused, low battery, silent, offline
//   sendCommand      family's "Ring her phone" / "Turn ringer on" → push to the senior phone
//   onNoteDeleted    remove a photo from Storage when its note expires
//   submitTicket     create a support ticket (app + website), confirmation emails
//   addTicketMessage follow-up from the user on their ticket
//   onTicketMessage  status bookkeeping + email/push the user when support replies
//   verifyPurchase   check a Play subscription with Google, grant premium, welcome-to-premium email
//   playRtdn         renewals, cancellations, payment problems, refunds from Google Play
//   subscriptionDaily trial-ending reminders; expire anything a notification missed
//   onUserWritten    welcome email for new family accounts
//   deleteMyAccount  full account deletion (Play requirement)
//   dailyStats       nightly numbers for the admin panel
//   cleanupInactive  monthly: delete data inactive for 24 months (privacy policy)
//
// Deploy: see docs/OPERATIONS.md

const admin = require("firebase-admin");
admin.initializeApp();

Object.assign(exports,
  { notifyFamily: require("./src/notify").notifyFamily },
  require("./src/safety"),
  require("./src/tickets"),
  require("./src/billing"),
  require("./src/account"),
);
exports.dailyStats = require("./src/stats").dailyStats;
exports.cleanupInactive = require("./src/stats").cleanupInactive;
