# Play Console "Data safety" answers

Matches the privacy policy (`web-src/pages/privacy.html`). Update both together.

**Does the app collect or share user data?** Yes. **Encrypted in transit?** Yes. **Can users request deletion?** Yes (in app and at /delete-account).

| Data type (Play category) | Collected | Shared* | Optional | Purpose |
|---|---|---|---|---|
| Personal info → Name | Yes (family sign-in; names of contacts entered) | No | Yes | App functionality, account management |
| Personal info → Email address | Yes (family sign-in, support tickets) | No | Yes | Account management, developer communications |
| Personal info → Phone numbers | Yes (contacts the user adds) | No | Yes | App functionality |
| Location → Precise location | Yes, only during SOS / detected fall | Shared with linked family members (user-initiated) | Yes | App functionality |
| Health and fitness → Health info | Yes (medicine names, doses, times, taken/missed; blood pressure, blood glucose, blood oxygen from Health Connect, with consent) | Shown to linked family (user-initiated, consented) | Yes | App functionality |
| Health and fitness → Fitness info | Yes (steps, heart rate, sleep from Health Connect, with consent) | Shown to linked family (user-initiated, consented) | Yes | App functionality |
| Messages → Other in-app messages | Yes (support tickets) | No | Yes | Customer support |
| Photos and videos → Photos | Yes: photos a family member chooses to send to the senior's home screen (deleted after 30 days). Contact/pill photos stay on the device; AI photos processed, not stored | Shared with the linked senior's phone (user-initiated) | Yes | App functionality |
| Financial info → Purchase history | Yes (order ID, date, country) | No | No | App functionality, fraud prevention |
| App activity → App interactions | Yes (Analytics events; "last used" time and daily check-ins for family safety alerts) | Check-ins / last used shown to linked family | No | Analytics, app functionality |
| App activity → Installed apps | Yes (names of launcher apps, so family can pick home-screen tiles) | Shown to linked family | Yes | App functionality |
| Device or other IDs / diagnostics | Battery, ringer mode, network type, time zone (family safety alerts) | Shown to linked family | Yes | App functionality |
| App info and performance → Crash logs, Diagnostics | Yes | No | No | Analytics |
| Device or other IDs | Yes (random device ID, Firebase installation ID) | No | No | App functionality, analytics |

\* Transfers to service providers (Google Firebase, Gemini, Zoho) are not "sharing" under Play's definition.

**Ephemeral processing:** text and photos sent to the AI features are processed ephemerally and not stored.

**Call screening (scam call shield):** numbers of incoming calls are checked on the device only and never transmitted; declare the CallScreeningService under "Phone" permissions if Play asks. Only a daily count is uploaded.

**Permissions to justify in Play Console (v1.1):** ACCESS_NOTIFICATION_POLICY (family can turn the ringer back on after the senior consents in Settings), call screening role (scam call shield, user grants it via Android's role dialog).

## Health Connect declaration (Play Console → App content → Health apps / Health Connect)

- **Use case:** "Medical care: medication tracking and vitals monitoring" by family caregivers of an older adult.
- **Permissions and why** (read only):
  - READ_STEPS, READ_HEART_RATE, READ_RESTING_HEART_RATE, READ_SLEEP: daily activity, heart rate and sleep summary shown to the senior's linked family caregivers.
  - READ_OXYGEN_SATURATION, READ_BLOOD_PRESSURE, READ_BLOOD_GLUCOSE: latest readings from the senior's own monitors for caregivers managing hypertension / diabetes alongside medicine reminders.
  - READ_HEALTH_DATA_IN_BACKGROUND: refresh the caregiver view every ~30 minutes without the senior opening the app.
- **Consent:** explicit in-app consent screen on the senior's phone before the Health Connect permission dialog; stop sharing any time (deletes the shared copy).
- **Not used for:** advertising, product recommendations (the device list is static and identical for all users), sale, credit/insurance decisions. No data leaves Firebase except to linked family devices.
- **Retention:** latest values until sharing stops; daily summaries 90 days (Firestore TTL).
- Privacy policy section: `/privacy#health`.
