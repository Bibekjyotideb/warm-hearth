// Gives an account access to the admin panel.
// Usage (once, from your PC, after `gcloud auth application-default login`):
//   npm run set-admin -- you@lusanetech.com
const admin = require("firebase-admin");
admin.initializeApp();
const email = process.argv[2];
if (!email) { console.error("Usage: npm run set-admin -- email@example.com"); process.exit(1); }
admin.auth().getUserByEmail(email)
  .then((u) => admin.auth().setCustomUserClaims(u.uid, { ...(u.customClaims || {}), admin: true }).then(() => u))
  .then((u) => console.log(`✓ ${email} (${u.uid}) is now an admin. Sign out and back in to the admin panel.`))
  .catch((e) => { console.error(e.message, "\nSign in to the admin panel once with this Google account first, then run again."); process.exit(1); });
