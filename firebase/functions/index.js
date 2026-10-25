// WarmHearth backend (Firebase Cloud Functions, 2nd gen, region asia-south1).
//
//   notifyFamily     push SOS / missed-medicine alerts to family phones
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
  require("./src/notify"),
  require("./src/tickets"),
  require("./src/billing"),
  require("./src/account"),
);
exports.dailyStats = require("./src/stats").dailyStats;
exports.cleanupInactive = require("./src/stats").cleanupInactive;
