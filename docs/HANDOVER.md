# WarmHearth: project context and handover

Everything a new developer (or a new AI session) needs to pick this project up without the
original conversations. Keep it current: when you change something listed here, update this file.

Last updated: 26 Oct 2026, app version 1.2.0 (versionCode 3).

---

## 1. Status at a glance

| Area | State |
|---|---|
| Android app (Kotlin, Compose) | **Written in full, never compiled.** The authoring environment couldn't download the Android SDK. Expect a few compile fixes on the first Android Studio sync (renamed APIs, imports). Nothing has run on a phone yet. |
| Backend (Firebase: Firestore, Functions, Storage, Hosting) | **Code written and unit-tested (16 tests), not deployed.** No Firebase project exists yet. |
| Website (`web/`) | Built and checked in browsers (desktop + mobile); not hosted yet. |
| Admin panel (`web/admin/`) | Built and checked against mock data; needs the Firebase project. |
| Play Console | Nothing created yet (app, subscriptions, declarations). |
| Email (Zoho aliases) | Not set up. |
| Placeholders to fill | Proprietor name and address in `firebase/functions/src/config.js` and `web-src/build.py`; Firebase web config in `web-src/js/firebase-config.js`; `app/google-services.json`; Amazon tracking IDs in `web-src/build.py`. |

**Do next, in order:** compile and run on two phones (senior and family) → create the Firebase
project and deploy (`docs/OPERATIONS.md` §2–3) → Play Console internal testing with the forms in
`docs/PLAY_COMPLIANCE.md` → closed testing (personal developer accounts created after Nov 2023 need 12
testers for 14 days before production; organisation accounts don't) → production.

---

## 2. Owner and business

- **Owner:** sole proprietor of **Lusane Technologies** (Tripura, India), domain `lusanetech.com`.
  MCA background. Also runs YouTube and Instagram automation pipelines (separate projects).
- **Site:** `warmhearth.lusanetech.com` (Firebase Hosting, DNS at Spaceship).
- **Email:** Zoho Mail on `lusanetech.com`. `support@` and `billing@` are **aliases** of the existing
  mailbox (no new mailboxes); functions send through Zoho SMTP with an app password.
- **Referral links:** for now the **owner's brother's Amazon Associates account** (the owner's own
  account would be closed after 180 days without sales). The brother must list
  `warmhearth.lusanetech.com` in his Associates account.
- **Stores:** Google Play first; Apple App Store later, once revenue covers the yearly fee.
- **Next product after WarmHearth:** an exam-prep app (niche competitive exams), same ASO-first approach.

---

## 3. Product

**One app, two modes.**
- **Senior mode** (the parent's phone): replaces the home screen with big buttons, faces to tap,
  SOS (hold 3 s), medicine alarms, voice commands, AI helpers, daily "I'm OK", photos from family,
  scam call shield.
- **Family mode** (son/daughter's phone, Google sign-in): a dashboard per parent with status, alerts,
  one-tap fixes, today's medicines, health readings, and remote control of almost everything on the
  parent's phone.

**Why this shape (research, Oct 2026):**
- ASO, measured on live Play data with `tools/play-aso`: "elderly launcher" has opportunity score 45 (US)
  / 42 (IN) and **no top-10 app uses it in its title**, so the title is "Elderly Launcher: WarmHearth".
  "senior launcher" scores 37 and leads the short description. Hindi users search the English terms.
- Dignity: a 2023 study of senior launchers found toy-like designs embarrass users, who then switch
  back. So the look is calm and grown-up: tinted tiles, dark text, solid colour only for SOS/call.
- Worries differ: 66% of Indian elders living alone worry most about social life and daily needs;
  67% of their children worry most about health. Hence two halves: connection for the parent
  (photos, call requests), reassurance for the child (check-ins, alerts).
- Top caregiver frustration: "her phone is on silent / dead and I can't reach her" → silent and
  battery alerts, "Ring phone" and "Turn ringer on".

**Free vs Premium** (Premium = Play subscription `warmhearth_premium`, one subscription covers every
parent phone the subscriber links):

| Free | Premium |
|---|---|
| Big home screen, dialer, up to 4 photo contacts | Unlimited contacts |
| SOS with family alerts + location | Safety alerts: no check-in, silent, low battery, offline, unused, heart rate |
| Daily "I'm OK", "Call me" requests, photo notes from family | Ring phone, turn ringer on |
| Scam call shield, voice commands | Remote control: contacts, medicines, sounds, text size, tiles, fall detection, PIN |
| 3 AI scam checks/day | Medicine reminders + taken/missed tracking, fall detection |
| | Health readings (Health Connect), 30 AI requests/day |

**Prices:** India ₹149/month or ₹899/year; elsewhere US$4.99/month or US$39.99/year; 7-day free trial
(offer `free-trial-7d`), reminder email 2 days before the first charge. Google takes 15% on subscriptions.

---

## 4. Key decisions (and why)

| Decision | Why |
|---|---|
| Kotlin + Jetpack Compose, minSdk 26, target 36 | Native launcher + alarms + call screening need platform APIs; Compose for the custom large UI. |
| Firebase in `asia-south1` (Mumbai) | Indian users first; one vendor for auth, DB, push, functions, AI, hosting. |
| Manual DI (`Graph` in `WarmHearthApp.kt`) | Small app; no Hilt complexity. |
| Senior phone uses **anonymous** auth; family uses **Google** sign-in | Seniors never see a login; family needs an identity for purchases, email and multiple devices. |
| Premium is **server-only** (`verifyPurchase` / `playRtdn` with the Play Developer API) | Firestore rules forbid clients from setting `premium`. |
| Safety alerts computed **on the server** (`safetyWatch`, every 15 min) | A dead phone can't report that it died. Rules are pure and unit-tested (`safetyRules.js`). |
| OTA = Remote Config + In-App Messaging + server rules | Play forbids downloading code; data and switches are fine. See §7. |
| AI via Firebase AI Logic (Gemini), model name from Remote Config | Swap models without a release; App Check protects the key; paid tier (no training on prompts). |
| Sharing, not monitoring | Play only allows "monitoring apps" for parents→children and employers→employees. WarmHearth is the senior sharing their own safety info, with consent before pairing and **withdrawal without the PIN** (Privacy & sharing). See `docs/PLAY_COMPLIANCE.md` §1. Don't add message/call/browsing/app-usage/continuous-location features. |
| Health data via **Health Connect** (not Google Fit) | Google Fit APIs end in 2026. Read-only, consented, family-only, 90-day retention, never used for ads or product picks. |
| Referral links on the **website** (`/devices`), not inside the app | Disclosure sits next to the links, affiliate programme rules are simplest, list is editable without a release. |
| No advertising ID | No ads; simpler Data safety; AD_ID removed in the manifest. |
| Emails: WarmHearth branding with a Lusane Technologies footer | Customers recognise the app; footer shows the legal seller; reusable for future apps via `config.js`. |
| English + Hindi from day one | India is the first market; every string exists in both (`values/` and `values-hi/`). |

---

## 5. Repository map

```
app/                                 Android app (com.lusanetech.warmhearth)
  src/main/java/com/lusanetech/warmhearth/
    MainActivity.kt                  senior navigation (flat Screen enum), share-to-scam-check, OTA update check
    WarmHearthApp.kt                 startup + Graph (db, prefs, family, billing, remote)
    ui/                              Theme.kt (palette, Tone, type scale), Components.kt (BigTile, BigButton, SoftButton, Chip, NavRow, Page…)
    launcher/                        HomeScreen (clock, check-in card, photo note, faces, tiles, Speak + SOS), app drawer
    calls/                           dialer, contacts, contact editor, CallShield (call screening service)
    sos/  fall/                      hold-to-SOS; accelerometer fall detection service + "Did you fall?"
    meds/                            scheduler (exact alarms), full-screen reminder (sound / ringtone / spoken), screens
    ai/                              AiHelper (Gemini), AiScreens (scam check, read it to me, in-app report), Speaker (TTS), Voice
    family/                          FamilyRepository (linking + senior-side sync), RemoteControl (family-side API),
                                     DeviceModels, FamilyApp (routes), FamilyDashboard, FamilyManage, FamilyEditors,
                                     FamilyVitals (readings, devices list), PairingScreen, FamilyMessagingService (FCM, both modes)
    safety/                          SafetyMonitor (status reports, StatusWorker), RemoteCommands (ring, ringer on), FindPhoneActivity
    health/                          HealthVitals (Health Connect read + sync), HealthPrivacyActivity
    settings/                        Settings, PIN, Tiles, SharingScreen (consent + Privacy & sharing)
    billing/                         Play Billing 8, paywall (Remote Config headline/offer)
    support/                         tickets in-app (+ OTA FAQ)
    util/                            RemoteFlags (all OTA keys), Telemetry, Notifications, AlarmSound, Tones, PhotoStore, Links…
  src/main/res/values{,-hi}/strings.xml   ~400 strings, English + Hindi, kept in parity
firebase/
  firestore.rules  storage.rules  firestore.indexes.json (incl. TTL)  firebase.json
  functions/                         Node 22, firebase-functions v7
    src/notify.js                    events → family push (per-user language)
    src/safety.js  safetyRules.js    safetyWatch, sendCommand, onNoteDeleted, cleanupMedia*
    src/billing.js                   verifyPurchase, playRtdn, subscriptionDaily
    src/tickets.js                   submitTicket, addTicketMessage, onTicketMessage, reportAiAnswer
    src/account.js                   welcome email, deleteMyAccount
    src/stats.js                     dailyStats, cleanupInactive (24 months)
    src/emails/templates.js          all transactional emails
    test/                            node --test (templates, safety rules)
web-src/                             website source → `python3 build.py` → web/
  pages/ screens/ (phone mock-ups) data/devices.json js/ build.py
web/                                 built site + admin panel (web/admin)
store-listing/                       Play listing en-US and hi-IN (keyword-researched)
tools/play-aso/                      keyword scorer used for the title
docs/                                OPERATIONS (setup/deploy/runbooks), PLAY_COMPLIANCE, DATA_SAFETY, HANDOVER, screens/
.claude/skills/                      23 design / Android / ASO skills used while building
```

---

## 6. Data model

**Firestore**
```
devices/{deviceId}                       one per senior phone (random UUID)
  ownerUid, name, family:[uid], premium*, premiumOrderId*, createdAt, lastPairCode
  lastActiveAt, activeDays, appVersion, model, locale, funnel{…}          (telemetry)
  status{battery, charging, ringer, ringVolume, dnd, silentSince, lastActiveAt, network, tz,
         callsScreenedToday, canRestoreRinger, fallDetection, tones[], reportedAt}
  settings{textScale, tiles[], callShield, checkIn{enabled,by,notifyOnOk}, sleep{start,end},
           inactiveHours, alerts{…}, fallDetection, voiceCommands, pin(write-only from family),
           medTone, vitals{hrAlerts,hrHigh,hrLow}, updatedAt}
  lastCheckInAt
  vitals{heartRate, heartRateAt, restingHeartRate, hrMin, hrMax, stepsToday, sleepMinutes, spo2,
         bpSystolic, bpDiastolic, bpAt, glucoseMgDl, glucoseAt, sources[], syncedAt}
  /contacts/{id}   name, phone, position, isEmergency, onHome, photoUrl, updatedAt, deleted
  /medicines/{id}  name, dose, times "08:00,20:00", active, tone, photoUrl, updatedAt, deleted
  /events/{id}     type, message, message_hi?, lat?, lng?, source?, createdAt, expireAt(90d)
  /doses/{med_date_time}  medicineId, name, date, time, status taken|missed, at, expireAt(90d)
  /notes/{id}      fromUid, fromName, text, photoUrl, storagePath, createdAt, seenAt, expireAt(30d)
  /commands/{id}   type ring|ringer_on|sync, byUid, byName, createdAt, sentAt, doneAt, result
  /vitals/{date}   daily summary, expireAt(90d)
deviceTokens/{deviceId}   token (server-only read)
alertState/{deviceId}     sent{…} de-duplication for safety alerts (server only)
pairCodes/{code}          deviceId, ownerUid, expiresAt (30 min)
users/{uid}               family: email, displayName, role, fcmTokens[], premium*, locale, lastActiveAt
subscriptions/{token}*    Play subscription state, entitled, inTrial, basePlanId, regionCode…
tickets/{id} + /messages  support
aiReports/{id}            reported AI answers (admin)
stats/{yyyy-mm-dd}        nightly numbers for the admin panel
rateLimits/{hash}         server only, TTL
(* = written only by Cloud Functions)
```
Deletes of contacts/medicines are tombstones (`deleted: true`) so an offline phone can't resurrect them.
Settings: newest `updatedAt` wins; the phone pushes its own changes with `pushLocalSettings()`.

**Storage:** `notes/{deviceId}/{noteId}.jpg` (photo notes, 30 days) and
`media/{deviceId}/{contacts|medicines}/{id}-{time}.jpg` (family-added photos; old ones cleaned up).

**Room (on the senior phone), version 2:** `contacts`, `medicines`, `dose_logs`. Migration 1→2 adds
`onHome`, `photoUrl`, `tone`.

---

## 7. Over-the-air control

| Changes without a release | How |
|---|---|
| Feature kill switches: AI, voice, fall detection, check-ins, photo notes, call shield, remote ring, vitals, shop | Remote Config (keys in `util/RemoteFlags.kt` and `docs/OPERATIONS.md` §2) |
| Home banner, family promo card, paywall headline + offer line, default plan, default tiles, help FAQ, device list | Remote Config |
| AI model, AI limits, AI prompt patch | Remote Config |
| Forced / suggested app update | `min_version_code` / `recommended_version_code` |
| Prices, intro offers | Play Console |
| Pop-up campaigns | Firebase In-App Messaging |
| Safety alert thresholds and wording | Cloud Functions (`safetyRules.js`) + deploy |
| Website, legal pages, `/devices` | `web-src` + deploy hosting |

Remote Config is real-time while the app is open.

---

## 8. Conventions

- **Strings:** never hard-code UI text. Add to `values/strings.xml` **and** `values-hi/strings.xml`;
  escape `'` and `"`; keep `%1$s` placeholders identical. The check script used during development
  compares keys, placeholders and escapes. Run it (or an equivalent) before committing.
- **Design:** use `Palette` / `Tone` / components from `ui/`. Senior side: touch targets ≥ 72dp, text
  scale from settings. Solid colour only for SOS, call, "I'm OK", Speak.
- **Senior navigation:** flat `Screen` enum; Back never leaves the launcher; the paywall can't bypass
  the PIN; Privacy & sharing must stay reachable **without** the PIN.
- **Privacy:** any new data leaving the phone needs an in-app disclosure line, a privacy-policy row,
  a Data safety update and a check against `docs/PLAY_COMPLIANCE.md`.
- **Backend:** functions in `asia-south1`; emails never throw (`sendMail`); pure logic goes in
  testable modules with `node --test`.
- **Website:** edit `web-src`, run `python3 build.py` (and `npm run build` for the admin/support JS).
  Don't edit `web/*.html` by hand.
- **Commits:** end with the co-author / session lines used in this repo's history.

---

## 9. Known gaps and risks

- Not compiled or device-tested (biggest risk). Likely spots: Health Connect API names, Compose
  experimental APIs, the Room migration, `CallScreeningService` behaviour on Xiaomi/Oppo/Vivo.
- Background limits on Chinese OEM phones can delay status reports (false "offline"); guidance
  in Help + `OFFLINE_HOURS` in `safetyRules.js`.
- Play review may still question the family-status features; the consent + Privacy & sharing design
  and the store wording are the defence (`PLAY_COMPLIANCE.md` §1). Keep screenshots showing the
  consent screen handy for an appeal.
- Health Connect approval can take time; keep `vitals_enabled=false` until approved.
- Amazon referral relies on the brother's account and his site list.
- Legal pages need a lawyer's review and the proprietor's real name/address.

---

## 10. History

| Version | Commit | What |
|---|---|---|
| 1.0 | `1058229` | Launcher, SOS, family link, medicine reminders, AI helpers, Hindi |
| 1.0 production layer | `cc8e57e` | Subscriptions + trial, website, legal pages, admin panel, tickets, emails, OTA basics |
| 1.1 | `90c282b` | Redesign, family dashboard, check-ins, safety alerts, ring/ringer, photo notes, call shield, wider OTA |
| 1.2 | `e0989fe` | Full remote control, reminder sounds incl. spoken, Health Connect vitals, device list + `/devices` |
| 1.2 (compliance) | this commit | Consent before pairing, Privacy & sharing without PIN, installed-apps upload removed, in-app AI reporting, no AD_ID, legal + listing fixes, docs |
