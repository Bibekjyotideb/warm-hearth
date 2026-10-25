# WarmHearth

**Play Store title:** `Elderly Launcher: WarmHearth`

A big-button Android launcher for elderly parents, with SOS, fall detection, medicine reminders,
AI helpers (scam checker, "read it to me", medicine-box reader) and voice commands. The same app
has a **family mode**: the son or daughter installs it too, links with an 8-letter code, and manages
the parent's phone remotely.

Kotlin · Jetpack Compose · Material 3 · Room · Firebase (Auth, Firestore, Messaging, AI Logic, App Check) · Play Billing 8 · English + Hindi.

> **Status:** written in full but **not yet compiled** (the cloud workspace it was written in can't
> download Android build tools). The first Android Studio sync may flag a few small things,
> usually a library version or a renamed API. Fix those first.

---

## How it makes money

| Free | One-time "lifetime unlock" (`premium_lifetime`) |
|---|---|
| Big-icon home screen, big dialer | Family remote setup (edit contacts & medicines from your phone) |
| Up to 4 photo contacts | Unlimited contacts |
| SOS: calls family + alerts linked phones with location | Medicine reminders + missed-dose alerts |
| 3 AI scam checks a day | Fall detection |
| Voice commands | 30 AI requests a day (scam, read-it-to-me, medicine reader) |

The **family member** usually buys: the paywall appears on *their* phone when they try to manage
the parent's phone. Suggested prices: US $9.99, India ₹399. Google keeps 15%.

## Project map

```
app/src/main/java/com/lusanetech/warmhearth/
  MainActivity.kt          senior-side navigation (flat screen enum, Home button always returns home)
  WarmHearthApp.kt         app start, tiny dependency container (Graph)
  ui/                      design system: Theme (colours, type, shapes, spacing), shared components
  launcher/                home screen, all-apps list
  calls/                   big dialer, photo contacts, contact editor
  sos/                     hold-to-SOS, location, SMS share
  fall/                    accelerometer fall detection service + "Did you fall?" screen
  meds/                    alarms, full-screen reminder, medicine screens
  ai/                      Gemini calls + quota, scam checker, read-it-to-me, camera, voice, text-to-speech
  family/                  Firebase sync, pairing, family-mode app, push messages
  billing/                 Play Billing + paywall
  settings/                PIN, settings, home-screen buttons
firebase/                  Firestore security rules + Cloud Function that pushes alerts to family
store-listing/             Play listing in English & Hindi (keyword-researched)
tools/play-aso/            Play Store keyword scorers used for the listing
.claude/skills/            17 design, Android and Play Store skills for Claude Code
```

## Setup (about 30 minutes, once)

### 1. Firebase
1. Create a project at console.firebase.google.com → add an **Android app** with package
   `com.lusanetech.warmhearth` → download `google-services.json` into `app/`.
2. **Authentication** → enable **Anonymous**.
3. **Firestore** → create database (region `asia-south1` recommended).
4. **AI Logic** → get started → choose **Gemini Developer API**.
5. **App Check** → register the app with **Play Integrity** (add your app's SHA-256 later from Play Console).
6. Upgrade to the **Blaze** plan (needed for the Cloud Function; the free quota covers a small app).
7. Deploy rules and the function:
   ```bash
   npm i -g firebase-tools && firebase login
   cd firebase && npm --prefix functions install
   firebase use --add            # pick your project
   firebase deploy --only firestore:rules,functions
   ```

### 2. Android Studio
1. Open the folder in Android Studio (latest stable) and let Gradle sync.
2. Set `SUPPORT_EMAIL` in `ai/AiScreens.kt` (Play requires a way to report AI answers).
3. Run on a real phone. Test: onboarding → set as home → add a contact → SOS → medicine alarm.
4. On a second phone choose **I'm a family member** and link with the code from Settings → Link a family member's phone.

### 3. Play Console
1. Create the app (one-time $25 developer fee).
2. **Monetize → In-app products** → create `premium_lifetime` (one-time) and set prices per country.
3. Store listing: copy from `store-listing/en-US.md` and `store-listing/hi-IN.md`.
4. App content declarations you will be asked for:
   - **Foreground service: health**: fall detection (accelerometer).
   - **Full-screen intent**: medicine alarms and fall alerts.
   - **Exact alarms**: medicine reminders (the app asks the user; it falls back gracefully).
   - **Data safety**: location (SOS only, shared with linked family), contacts the user enters, photos stay on device, messages/photos sent to Google's Gemini only when the user taps Check/Read.
   - **AI-generated content**: in-app "Report a wrong answer" link is included.
5. Start with **closed testing** (Play requires 12 testers for 14 days on new personal accounts).

## Keyword research

```bash
cd tools/play-aso && npm install
node title.mjs us en "elderly launcher" "senior launcher" "big launcher"   # live scores
node score.mjs                                                             # long-tail expansion from seeds.json
```
Results from 25 Oct 2026 are in `store-listing/en-US.md`.

## Before scaling (known TODOs)
- Verify purchases server-side (Cloud Function + Play Developer API) before trusting `premium`.
- Replace the placeholder launcher icon and make the 1024×500 feature graphic.
- Add Play In-App Review prompt after a success moment (first SOS test or 7 days of medicines taken).
- Silent SOS SMS needs the restricted `SEND_SMS` permission and a Play declaration; today SOS uses push + call + SMS composer.
