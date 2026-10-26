# WarmHearth operations guide

Everything needed to run WarmHearth in production, in the order you'll do it.
Company details live in two places: `firebase/functions/src/config.js` and `web-src/build.py`. Fill in the
proprietor's name and registered address in both before launch.

---

## 1. Email: support@ and billing@ without new mailboxes (Zoho)

Both addresses are **aliases** of your existing Zoho mailbox. Mail to either lands in your normal inbox,
and you can reply *as* either address.

1. Zoho Mail **Admin Console** → **Users** → click your user → **Mail aliases** (Email alias) → add
   `support@lusanetech.com` and `billing@lusanetech.com`.
2. In Zoho Mail, **Settings → Mail → Send mail as**: both aliases appear automatically. When replying to a
   customer, pick the matching "From".
3. Optional: **Settings → Filters** → "To contains support@" → label *WarmHearth Support*; same for billing@.
4. **App password for the server** (the Cloud Functions send email through Zoho SMTP):
   Zoho **Accounts → Security → App passwords** → *Generate* → name it "WarmHearth server". Copy it.
5. Check deliverability: Zoho Admin → **Domains → lusanetech.com → Email configuration**: SPF, DKIM and
   MX must all be green. (If DKIM isn't set up yet, add the TXT record it shows in Spaceship DNS.)

**SMTP host:** if you log in at `mail.zoho.in`, the host is `smtp.zoho.in` (the default here).
If you log in at `mail.zoho.com`, set `SMTP_HOST=smtp.zoho.com` when deploying.

### Billing emails: decision
Emails are **branded WarmHearth** with a **Lusane Technologies** footer (the legal seller), sent from
`billing@` for purchases/refunds and `support@` for everything else. Users recognise the product, the
footer satisfies the "who is selling" requirement, and the same layout serves future Lusane apps by
changing `config.js` only. Google Play still sends the official payment receipt; ours confirms what was
unlocked and how to get help. Preview every email: `cd firebase/functions && npm run preview-emails`.

| Email | From | When |
|---|---|---|
| Welcome | support@ | First Google sign-in on a family phone |
| Trial started / Premium started | billing@ | Subscription verified by the server |
| Trial ends in 2 days | billing@ | Daily job, 2 days before the first charge |
| Subscription cancelled | billing@ | User cancels in Google Play |
| Payment problem | billing@ | Renewal failed (grace period / on hold) |
| Refund processed | billing@ | Google Play refund / chargeback (RTDN) |
| Ticket received | support@ | New ticket from app or website (also alerts you) |
| Reply from support | support@ / billing@ | You reply in the admin panel |
| Account deleted | support@ | After deletion |

---

## 2. Firebase project (one time)

1. console.firebase.google.com → **Add project** "warmhearth" → enable Google Analytics.
2. Upgrade to **Blaze** (pay-as-you-go). Then Google Cloud console → **Billing → Budgets & alerts** →
   create a ₹1,000/month budget with email alerts at 50/90/100%.
3. **Authentication → Sign-in method:** enable **Anonymous** and **Google**.
4. **Firestore** → create database, location **asia-south1 (Mumbai)**, production mode.
5. **Project settings → Your apps:**
   - Android app `com.lusanetech.warmhearth` → download `google-services.json` into `app/`.
     Add your **debug** and (later) **Play app-signing** SHA-1 and SHA-256 fingerprints here, or Google
     sign-in fails.
   - Web app "WarmHearth web" → copy the config into `web-src/js/firebase-config.js`.
6. **AI Logic** → Get started → **Gemini Developer API**. Because the project is on Blaze, this uses the
   paid tier (Google doesn't use prompts to improve its models), which the privacy policy relies on.
7. **App Check** → Android app → **Play Integrity** → register. Enforce for Firestore, Functions and
   AI Logic once a test build works. Debug builds print a debug token in Logcat; add it under
   **App Check → Manage debug tokens**.
8. **Crashlytics** → enable.
9. **Hosting** → Get started (just click through; the config is already in `firebase/firebase.json`).
10. **Storage** → Get started, location **asia-south1**. Holds photos family send to a parent's home
    screen. Deleted automatically 30 days later (TTL on the note + `onNoteDeleted`). As a safety net,
    add a lifecycle rule: Google Cloud console → Cloud Storage → bucket → Lifecycle → *Delete object,
    age 35 days, prefix `notes/`*.
11. **In-App Messaging** → nothing to set up; it's ready for pop-up campaigns (see below).

### Remote Config (the "over-the-air" switches)
Add these parameters (defaults already in `app/.../util/RemoteFlags.kt`). Publish to change behaviour
on every phone within about an hour, with no app update.

| Parameter | Default | Use it when |
|---|---|---|
| `ai_enabled` | true | Gemini outage or a bad AI answer going viral: switch AI off instantly |
| `ai_model` | gemini-2.5-flash-lite | Google retires the model or a cheaper one ships |
| `ai_free_scam_per_day` / `ai_premium_per_day` | 3 / 30 | AI costs need tuning |
| `ai_prompt_note` | "" | Patch AI behaviour, e.g. "Never mention X" |
| `fall_detection_enabled` | true | False alarms on some phones: stops the service everywhere |
| `voice_enabled` | true | Speech recognition problem |
| `min_version_code` | 1 | An old version is broken: forces an immediate Play update |
| `recommended_version_code` | 1 | Nudge everyone to a new version (background update) |
| `announcement_en` / `announcement_hi` | "" | Banner on every senior's home screen |
| `family_promo_en` / `family_promo_hi` | "" | Card at the top of the family app: tips, "new in this version", offers |
| `paywall_headline_en` / `_hi` | "" | Test a different paywall pitch without a release |
| `paywall_offer_en` / `_hi` | "" | Highlighted offer line on the paywall (pair it with a Play Console offer) |
| `paywall_default_plan` | yearly | Pre-select monthly instead, for a test |
| `default_tiles` | "" | Change the home-screen buttons for **new** installs, e.g. `phone,contacts,whatsapp,meds,scam,apps` |
| `help_faq_json` | "" | Add answers to Help & support: `[{"q_en":"…","a_en":"…","q_hi":"…","a_hi":"…"}]` |
| `checkin_enabled` | true | Kill switch for the daily "I'm OK" card |
| `notes_enabled` | true | Kill switch for photos & notes |
| `call_shield_enabled` | true | Kill switch for the scam call shield UI (if a phone maker misbehaves) |
| `remote_ring_enabled` | true | Hide "Ring phone" in the family app |
| `vitals_enabled` | true | Kill switch for watch / health readings |
| `shop_enabled` | true | Show "Which watch to buy?" in the family app |
| `shop_json` | "" | Replace the device list without a release (same shape as `DEFAULT_SHOP` in `RemoteFlags.kt`; optional `url_in` / `url_intl` per item) |
| `shop_disclosure_en` / `_hi` | "" | Override the affiliate disclosure line |

**Real-time:** the app listens for Remote Config updates, so a published change reaches open apps in
seconds, and everyone else on their next app start (at most an hour).

**Prices** are also over the air: they're read live from Play Console. Change a price or create an
intro offer in Play Console → Monetize → Subscriptions; no release.

**Pop-ups and campaigns:** Firebase console → Messaging → **In-App Messaging** → new campaign. Image,
text, button and link, targeted by language, app version or Analytics audience (e.g. "family users
without Premium"). No release.

**Safety alert rules** (thresholds, wording) live in Cloud Functions (`functions/src/safetyRules.js`):
change them and run `firebase deploy --only functions`. That's also over the air for every phone.

**What can't be changed over the air:** Kotlin code, new screens, permissions, libraries. Google Play's
policy forbids apps from downloading executable code, so those ship as a release (section 6). Phones
install those updates automatically in the background; `min_version_code` forces it when needed.

---

## 3. Deploy the backend and website

```bash
npm i -g firebase-tools
firebase login
cd firebase
firebase use --add                       # choose the project, alias "prod"

firebase functions:secrets:set SMTP_USER # your Zoho login, e.g. you@lusanetech.com
firebase functions:secrets:set SMTP_PASS # the Zoho app password from step 1.4

cd functions && npm install && npm test && cd ..
cd ../web-src && npm install && npm run build && cd ../firebase

firebase deploy --only firestore,storage,functions,hosting
```

Deploy creates the Firestore indexes and the TTL policies (rate limits; alerts and doses after 90 days;
photo notes after 30 days) and the Storage rules.

### Custom domain: warmhearth.lusanetech.com (Spaceship DNS)
1. Firebase console → **Hosting → Add custom domain** → `warmhearth.lusanetech.com`.
2. Firebase shows a **TXT** record (verification) and one or more **A** records.
3. Spaceship → **Domain list → lusanetech.com → Advanced DNS → Add record**:
   - Type **TXT**, Host `warmhearth` (or what Firebase says), Value: the TXT value.
   - Type **A**, Host `warmhearth`, Value: each IP Firebase gives. TTL: Automatic.
   - Remove any other A/AAAA/CNAME records for `warmhearth`.
4. Back in Firebase click **Verify**. The SSL certificate is issued automatically (up to 24 hours).

---

## 4. Google Play Console

1. Create the app "Elderly Launcher: WarmHearth" (the listing title; the app name stays WarmHearth).
2. **Monetize → Products → Subscriptions** → create `warmhearth_premium` ("WarmHearth Premium"):
   - Base plan `monthly`, auto-renewing, 1 month. Prices: India **₹149**, United States **$4.99**; then
     "Update other prices" from the US price so every other country gets a local equivalent.
   - Base plan `yearly`, auto-renewing, 1 year. India **₹899**, United States **$39.99**, same for the rest.
   - On each base plan: **Add offer** → id `free-trial-7d`, eligibility *New customer acquisition: never had
     this subscription*, phase **Free trial, 7 days**. Activate.
   - Grace period: 7 days; account hold: 30 days (Play defaults are fine).
3. **Server purchase verification** (so premium can't be faked):
   1. Google Cloud console (same project) → **APIs** → enable **Google Play Android Developer API**.
   2. Find the Functions service account: **IAM** → `PROJECT_NUMBER-compute@developer.gserviceaccount.com`.
   3. Play Console → **Users and permissions → Invite new users** → that email → app permissions:
      *View app information*, *View financial data*, *Manage orders and subscriptions*.
4. **Refunds in real time (RTDN):**
   1. Google Cloud → **Pub/Sub → Create topic** `play-billing`.
   2. Topic → **Permissions → Add principal** `google-play-developer-notifications@system.gserviceaccount.com`,
      role **Pub/Sub Publisher**.
   3. Play Console → **Monetize → Monetization setup → Real-time developer notifications** → topic
      `projects/YOUR_PROJECT/topics/play-billing` → **Send test notification** (shows in function logs).
5. **App integrity → App signing:** copy the SHA-1 and SHA-256 into Firebase (section 2.5).
6. **Store listing:** copy `store-listing/en-US.md` and `store-listing/hi-IN.md`. Privacy policy URL:
   `https://warmhearth.lusanetech.com/privacy`. Website: `https://warmhearth.lusanetech.com`.
   Support email: `support@lusanetech.com`.
7. **App content:**
   - Data safety: see `docs/DATA_SAFETY.md`.
   - Account deletion URL: `https://warmhearth.lusanetech.com/delete-account`.
   - Foreground service (health): fall detection. Full-screen intent: medicine and fall alarms.
     Exact alarms: medicine reminders. Location: SOS only, with in-app prominent disclosure.
   - Health apps declaration: "Medication and treatment management" + "Emergency & safety"; not a
     medical device.
   - AI-generated content: in-app reporting is included ("Report a wrong answer").
   - Target audience: 18+.
8. **Testing:** new personal developer accounts must run a **closed test with at least 12 testers for
   14 days** before production. Invite family and friends early.

---

## 5. Admin panel

1. Open `https://warmhearth.lusanetech.com/admin` and sign in with your Google account once.
2. Make yourself admin (once, from your PC):
   ```bash
   gcloud auth application-default login
   cd firebase/functions && npm run set-admin -- you@lusanetech.com
   ```
3. Sign out and back in. You'll see: live users and activity, purchases, refunds, the setup funnel
   (where users drop), usage length, and the support inbox with replies, canned answers and close.

Add a teammate later the same way. Remove access with the Firebase Admin SDK or by deleting the claim.

---

## 6. Releases and updates

- **Versioning:** bump `versionCode` (+1) and `versionName` (semver) in `app/build.gradle.kts` every release.
- **Roll out gradually:** Play Console → Production → **staged rollout** 10% → watch Crashlytics for
  24 hours → 50% → 100%. Halt the rollout if the crash-free rate drops below 99%.
- **Phones update themselves:** the app checks on every open (`util/AppUpdates.kt`). Normal releases
  download in the background and install when the app is closed. If an old version is broken, set
  `min_version_code` in Remote Config to the fixed version: everyone below it gets a full-screen update.
- **Things that will break eventually, and the fix:**

| Will break | Early warning | Fix without release | Real fix |
|---|---|---|---|
| Gemini model retired | Google deprecation email, AI errors in logs | change `ai_model` | none needed |
| Play Billing library deadline (every 2 years, end of August) | Play Console warning | — | upgrade library, release, then raise `min_version_code` |
| Target SDK requirement (yearly, end of August) | Play Console warning | — | raise `targetSdk`, test alarms + launcher, release |
| Phone maker kills alarms / fall service | tickets "alarm didn't ring" | `announcement_*` with steps | add maker-specific guidance screen |
| Firebase SDK breaking change | Firebase release notes | — | upgrade BoM, release |
| Zoho app password revoked | emails stop, function logs "Email failed" | set a new `SMTP_PASS` secret, redeploy | — |
| Something unknown in the AI | user reports | `ai_enabled=false` or `ai_prompt_note` | prompt fix in a release |
| Phone makers delay the 30-min status report (aggressive battery savers) | false "offline" alerts in tickets | raise `OFFLINE_HOURS` in `safetyRules.js`, deploy functions | battery-optimisation guidance screen |
| Android changes call screening / DND access rules | Android beta notes, tickets | `call_shield_enabled=false` | update code, release |

---

## 7. Monitoring

- **Crashlytics** → Alerts → enable "new fatal issue" and "regressed issue" emails.
- **Functions logs**: Firebase console → Functions → Logs. Search "Email failed", "Play verify failed".
- **Budget alerts** from section 2.2.
- **Admin panel** daily: open tickets badge, purchases vs refunds, funnel drop.
- **Play Console** → Android vitals (ANR rate < 0.47%, crash rate < 1.09% to stay visible in search).

---

## 8. Production checklist

| Item | Status |
|---|---|
| Privacy, terms, refunds, account deletion pages | Done (web) – get them reviewed by a lawyer |
| In-app links to privacy and terms | Done |
| Account deletion in app (family + senior phone) | Done |
| Server-side purchase verification + refunds | Done (needs Play Console setup, section 4.3–4.4) |
| Firestore rules: premium only by server, admin claim | Done |
| App Check (Play Integrity) | Done in code; enforce in console after testing |
| Crashlytics, Analytics, funnel telemetry | Done |
| Remote Config kill switches + forced updates | Done |
| Support tickets: app, website, email, admin replies | Done |
| Transactional emails via Zoho aliases | Done (needs SMTP secrets) |
| Data retention jobs (alerts 90 days, inactive 24 months) | Done |
| Hindi + English app and listing | Done (website English only for now) |
| Subscriptions: verification, renewals, cancellations, refunds, trial reminders | Done (needs section 4.2–4.4) |
| Release signing key + Play App Signing | To do in Play Console |
| Real app icon + feature graphic + screenshots | To do (screen previews: `docs/app-preview.html`) |
| GST registration check | Ask your CA whether you need GST registration for Play income and export of services |
| Compile and test on real phones (Xiaomi, Samsung, Pixel) | To do |

---

## 9. Watches, Health Connect and referral income

**Health Connect (vitals).** Google Fit's APIs shut down at the end of 2026; WarmHearth reads from
Health Connect instead. In Play Console → App content, fill in the Health Connect declaration using
`docs/DATA_SAFETY.md` (use case, one line per permission). Review takes a few days; until approved,
keep `vitals_enabled = false` in Remote Config for production users.

**Referral links.** The family app's "Which watch to buy?" opens `warmhearth.lusanetech.com/devices`,
which holds the affiliate links and the disclosure (cleanest for Play policy and for the affiliate
programme's rules).
1. Apply to **Amazon Associates India** (affiliate-program.amazon.in) and, for other countries,
   **Amazon Associates US**. They want a live website with content: `/devices` is that page.
2. Put your tracking IDs in `web-src/build.py` → `SITE["amazon_tag_in"]` / `["amazon_tag_us"]`,
   rebuild and deploy hosting. Links become `amazon.in/s?k=…&tag=yourtag-21`.
3. Optional: Flipkart Affiliate or an aggregator (EarnKaro, Cuelinks) for more stores: change the
   `href` pattern in `devices_html()`.
4. Update the list any time in `web-src/data/devices.json` (website) and `shop_json` (app).
Rules to keep: the list is the same for everyone and never based on health data (Health Connect
policy), every page with links shows the disclosure, links say "(affiliate link)".
