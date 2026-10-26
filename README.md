# WarmHearth

**Play Store title:** `Elderly Launcher: WarmHearth` · **Package:** `com.lusanetech.warmhearth` ·
**Site:** warmhearth.lusanetech.com · **By:** Lusane Technologies

One Android app with two modes:

- **On the parent's phone:** a calm, big-button home screen (big tiles, faces to tap) that still
  looks like a normal phone, not a toy.
  - Hold-to-SOS with location, and fall detection.
  - Medicine alarms that can speak the medicine's name.
  - Daily "I'm OK" and "Ask my family to call me".
  - Photos from family on the home screen.
  - A scam call shield, plus AI "Is this a scam?" and "Read it to me".
  - Voice commands, in English and Hindi.
- **On the son's or daughter's phone:** a dashboard for each parent.
  - Is the phone OK: battery, on silent, last used.
  - Safety alerts, with one-tap "Ring phone" and "Turn ringer on".
  - Today's medicines, taken or missed.
  - Watch readings through Health Connect.
  - Remote control of contacts (with photos and home-screen faces), medicine times and sounds,
    text size, buttons, fall detection and the PIN.

Kotlin · Jetpack Compose · Material 3 · Room · WorkManager · Health Connect · Play Billing 8 ·
Firebase (Auth, Firestore, Storage, Functions, Messaging, AI Logic/Gemini, App Check, Remote Config,
In-App Messaging, Hosting) · English + Hindi.

> **Status (v1.2.0):** the app, backend, website and admin panel are all written. The Android app has
> **not been compiled or run** yet. The backend is **not deployed**, because no Firebase project
> exists yet. Start with [`docs/HANDOVER.md`](docs/HANDOVER.md).

## Documentation

| Read this | For |
|---|---|
| [`docs/HANDOVER.md`](docs/HANDOVER.md) | Full project context: status, decisions and why, architecture, data model, conventions, risks, history |
| [`docs/OPERATIONS.md`](docs/OPERATIONS.md) | Step-by-step setup and runbooks: Zoho email, Firebase, deploy, domain, Play Console, admin panel, releases, monitoring, Remote Config keys, referral links, breach response |
| [`docs/PLAY_COMPLIANCE.md`](docs/PLAY_COMPLIANCE.md) | Why each feature is allowed on Google Play, every Play Console form answer, Indian law checklist |
| [`docs/DATA_SAFETY.md`](docs/DATA_SAFETY.md) | Play Data safety answers and the Health Connect declaration text |
| [`store-listing/`](store-listing) | Play listing copy (en-US, hi-IN), keyword research results |
| [`CLAUDE.md`](CLAUDE.md) | Rules for AI coding sessions on this repo |
| [`docs/app-preview.html`](docs/app-preview.html), [`docs/screens/`](docs/screens) | All app screens and website screenshots |

## How it makes money

WarmHearth Premium is a Google Play subscription (`warmhearth_premium`) with a 7-day free trial.
One subscription covers every parent phone the subscriber links.

| | Monthly | Yearly |
|---|---|---|
| India | ₹149 | ₹899 |
| Rest of world | US$4.99 | US$39.99 |

- **Free:** SOS, check-ins, call requests, photo notes, call shield, 4 contacts, 3 AI checks a day.
- **Premium:** safety alerts, ring phone / ringer on, remote control, medicine tracking, fall
  detection, health readings, unlimited contacts and AI.
- **Fees:** Google keeps 15%.
- **Referral links:** recommended watches on `/devices` earn a small extra (for now through a
  partner's Amazon Associates account).

## Repository

```
app/            Android app (see docs/HANDOVER.md §5 for every package)
firebase/       Firestore + Storage rules, indexes/TTL, Cloud Functions (Node 22) + tests
web-src/        website source (pages, phone mock-ups, devices.json) → build.py → web/
web/            built website + admin panel (web/admin)
store-listing/  Play listing copy
tools/play-aso/ Play keyword scorer used for the title
docs/           handover, operations, compliance, data safety, screenshots
.claude/skills/ design, Android and ASO skills used while building
```

## Get it running

1. **Android:** open the folder in Android Studio (latest stable). Put `google-services.json` in
   `app/`, let Gradle sync, and fix any first-compile errors. Run it on two phones: one in senior
   mode, one in family mode, linked with the code.
2. **Backend:** follow `docs/OPERATIONS.md` §2–3:
   ```bash
   cd firebase/functions && npm install && npm test
   cd ../ && firebase deploy --only firestore,storage,functions,hosting
   ```
3. **Website:** `cd web-src && npm install && npm run build`. Hosting deploys `web/`.
4. **Play Console:** create the subscription and fill in the forms listed in
   `docs/PLAY_COMPLIANCE.md` §2. Start with internal testing, then closed testing.

### Placeholders to fill before launch
- Proprietor's legal name and address: `firebase/functions/src/config.js` and `web-src/build.py`.
- Firebase web config: `web-src/js/firebase-config.js`.
- Amazon tracking IDs, from the partner account: `web-src/build.py` (`amazon_tag_in`, `amazon_tag_us`).

## Keyword research

```bash
cd tools/play-aso && npm install
node title.mjs us en "elderly launcher" "senior launcher" "big launcher"   # live scores
```
Results and where each keyword is used: `store-listing/en-US.md`.
