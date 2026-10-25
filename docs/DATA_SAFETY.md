# Play Console "Data safety" answers

Matches the privacy policy (`web-src/pages/privacy.html`). Update both together.

**Does the app collect or share user data?** Yes. **Encrypted in transit?** Yes. **Can users request deletion?** Yes (in app and at /delete-account).

| Data type (Play category) | Collected | Shared* | Optional | Purpose |
|---|---|---|---|---|
| Personal info → Name | Yes (family sign-in; names of contacts entered) | No | Yes | App functionality, account management |
| Personal info → Email address | Yes (family sign-in, support tickets) | No | Yes | Account management, developer communications |
| Personal info → Phone numbers | Yes (contacts the user adds) | No | Yes | App functionality |
| Location → Precise location | Yes, only during SOS / detected fall | Shared with linked family members (user-initiated) | Yes | App functionality |
| Health and fitness → Health info | Yes (medicine names, doses, times) | No | Yes | App functionality |
| Messages → Other in-app messages | Yes (support tickets) | No | Yes | Customer support |
| Photos and videos | No (photos stay on the device; AI photos processed, not stored) | No | — | — |
| Financial info → Purchase history | Yes (order ID, date, country) | No | No | App functionality, fraud prevention |
| App activity → App interactions | Yes (Analytics events) | No | No | Analytics |
| App info and performance → Crash logs, Diagnostics | Yes | No | No | Analytics |
| Device or other IDs | Yes (random device ID, Firebase installation ID) | No | No | App functionality, analytics |

\* Transfers to service providers (Google Firebase, Gemini, Zoho) are not "sharing" under Play's definition.

**Ephemeral processing:** text and photos sent to the AI features are processed ephemerally and not stored.
