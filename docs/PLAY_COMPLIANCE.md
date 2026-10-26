# Google Play and legal compliance

Checked against Google Play policies and Indian law on 26 Oct 2026. Re-check when Play emails a
policy update (usually twice a year) and before every major feature. "Done" means done in code /
docs; the **Play Console** column is what you fill in yourself before the first release.

## 1. Design decisions that keep WarmHearth on the right side of Play policy

| Risk | What Play says | How WarmHearth handles it |
|---|---|---|
| **Being classed as a "monitoring app" (stalkerware)** | Apps that send personal data to *another person* for monitoring are only allowed for parents→children or employers→employees, must declare `isMonitoringTool`, and can't be used on other adults even with consent. | WarmHearth is a tool the **senior uses to share their own safety information** with family they choose: (1) the pairing code only appears after the senior reads "what your family will see and do" and taps *I agree*; (2) **Privacy & sharing** is reachable from the PIN screen **without the PIN**, so the senior can stop status sharing, stop watch readings or unlink everyone themselves; (3) what family see comes from things the senior **does** (taps "I'm OK", "I took it", "Call me") plus *how the phone is* (battery, ringer) and a coarse "active recently" signal (15-minute rounding, own switch), never messages, calls, photos, browsing or app usage (the installed-apps list upload was **removed**); (4) store listing and website say "share", not "monitor/track". **Do not** add features that read messages, call logs, browsing, screen time per app, or continuous location; that would turn it into a monitoring app and get it removed. Do **not** declare `isMonitoringTool`. |
| User Data policy (prominent disclosure + consent) | Disclose in-app before collecting, get affirmative consent. | Consent screen before pairing; separate consent dialog before Health Connect; location permission explained in onboarding before the system dialog. |
| Health Connect | Permitted: medication tracking, vitals monitoring. Banned: ads, selling, credit/insurance, life-critical use. Each permission justified. | Read-only, consented, family-only; heart-rate alert is off by default and labelled "not a medical alarm"; device list is identical for everyone and never driven by health data; 90-day retention. |
| Generative AI content | In-app reporting of offensive content **without leaving the app**. | `ReportAnswerLink` on every AI answer (scam check, read-it-to-me, medicine reader) → `reportAiAnswer` callable → `aiReports` + email to admin inbox. Only the AI answer is sent, not the user's message/photo. |
| Device & Network Abuse (no code download) | Apps can't download executable code. | Remote Config / In-App Messaging change data and switches only. |
| Full-screen intents (Android 14+) | Auto-granted only to alarm/calling apps; others must ask and degrade gracefully. | Medicine reminders are alarms (core feature); Settings shows "Allow full-screen alerts" when not granted; notifications still work without it. |
| Exact alarms | `USE_EXACT_ALARM` only for alarm/calendar apps; `SCHEDULE_EXACT_ALARM` needs user grant. | Uses `SCHEDULE_EXACT_ALARM` + setAlarmClock, with a Settings prompt if denied. |
| Foreground service types | Declare each type with a video. | One service: fall detection, type `health` (+ `HIGH_SAMPLING_RATE_SENSORS`), user turns it on, persistent notification. |
| Photo / video permissions | Use the system photo picker unless core. | Photo picker and camera intent only; no READ_MEDIA_* or CAMERA permission. |
| SMS / Call log | Restricted permissions. | Not used. SOS opens the SMS app pre-filled. Call shield uses the call-screening **role**, no call-log permission; numbers never leave the phone. |
| Subscriptions | Clear price, period, trial terms, how to cancel. | Paywall shows price/period, trial terms line, Restore, Manage subscription; website terms + refund page. |
| Account deletion | In-app and on the web. | Settings → Delete (both modes) + `/delete-account`; `deleteMyAccount` removes Firestore + Storage data. |
| Advertising ID | Declare if used. | **Not used**: AD_ID permission removed, Analytics ad signals disabled. Declare "No". |
| Store listing metadata | ≤30-char title, no rankings/testimonials/"best", no keyword stuffing, no emoji in title. | Title "Elderly Launcher: WarmHearth" (28). Description reviewed: removed "families trust" and the keyword-list sentence. |
| Medical claims | No unsupported medical claims; medical devices must declare. | Everywhere: "not an emergency service or medical device"; readings "can be wrong". Not a regulated device. |
| Affiliate links | Must not be deceptive; health data must not drive recommendations. | Links live on the website `/devices` with a disclosure next to them; app list says "affiliate links"; same list for everyone. |
| Families / children | Not for kids → set target audience 18+. | Terms: 18+ for family mode and purchases. |

## 2. Play Console forms to complete (App content)

| Form | Answer |
|---|---|
| Privacy policy | `https://warmhearth.lusanetech.com/privacy` |
| App access | Provide a test account: a Google account for family mode + steps to link (reviewers need two devices or an emulator; give them a pre-linked demo code process in the notes). |
| Ads | **No ads** |
| Content rating | Questionnaire: utility, no user-to-user public content (photo notes are private between linked family) |
| Target audience | **18 and over** |
| News app | No |
| Data safety | From `docs/DATA_SAFETY.md` |
| Health apps declaration | Categories: **Medication and Treatment Management**, **Healthcare Services and Management** (elderly care), **Emergency and First Aid** (SOS), **Activity and Fitness**, **Sleep Management**. Not a medical device. |
| Health Connect permissions | Text in `docs/DATA_SAFETY.md` → "Health Connect declaration". Keep Remote Config `vitals_enabled=false` until approved. |
| Foreground service | Type **health**, use case "fall detection for older adults (continuous accelerometer)". Video: Settings → Fall detection on → notification appears → shake/drop test → "Did you fall?" screen. |
| Full-screen intent | Core function: **alarms** (medicine reminders). Video of a reminder ringing on the lock screen. |
| Exact alarm | Uses `SCHEDULE_EXACT_ALARM` (no declaration needed); mention medicine reminders. |
| Advertising ID | **No** |
| Financial features | None |
| Government apps | No |
| Account deletion | URL `https://warmhearth.lusanetech.com/delete-account`; in-app path Settings → Delete |
| Default launcher | No form; describe in the listing that it replaces the home screen and how to switch back. |

## 3. Indian law (not legal advice: have a lawyer review once before launch)

| Law | What it needs | Status |
|---|---|---|
| **DPDP Act 2023 + DPDP Rules 2025** (notified 14 Nov 2025; notice/consent/breach rules apply from ~May 2027) | Clear notice of data + purpose, consent as easy to withdraw as to give, grievance officer, breach notification to users and the Data Protection Board, deletion when purpose ends | Privacy policy sections 1–10; consent screens; withdrawal without PIN; grievance officer section; breach steps in `OPERATIONS.md`; 24-month inactivity deletion + TTLs. Fill in the proprietor's name/address placeholders. |
| IT Act 2000 / SPDI Rules 2011 | Reasonable security, privacy policy, grievance officer | Covered by the same pages. |
| Consumer Protection Act 2019 + E-commerce Rules | Clear pricing, refund policy, no dark patterns (auto-renew disclosed, easy cancel) | Refund page, trial reminder email 2 days before charge, cancel via Play. |
| ASCI guidelines (affiliate / influencer disclosure) | Disclose material connection clearly next to the link | Disclosure on `/devices` and in-app. |
| Amazon Associates agreement | Links only on sites listed in the associate's account; required disclosure statement | **Brother must add `warmhearth.lusanetech.com` to his Associates "Website and mobile app list".** Tracking ID goes in `web-src/build.py` `SITE["amazon_tag_in"/"amazon_tag_us"]`. Amazon closes accounts with no sales in 180 days (his account, his risk). |
| CDSCO (medical devices) | Software for diagnosis/treatment can be a medical device | WarmHearth doesn't diagnose or treat; reminders + display only, with disclaimers. Don't add dosage advice or "detects heart problems" claims. |
| GST | Registration over ₹20 lakh/yr turnover; Google Play doesn't file your GST | Ask your CA when revenue starts. |
| Trademark | "WarmHearth" | Search the IP India register and Play before filing class 9/42; file once you have traction. |

## 4. Before each release

- [ ] Every new permission justified here and in the Data safety form
- [ ] No new personal data sent off the phone without a disclosure + consent line in-app and a row in the privacy policy
- [ ] Store listing: no rankings, testimonials, "best", or emoji in the title
- [ ] AI features still have the in-app report button
- [ ] `versionCode` raised; staged rollout
