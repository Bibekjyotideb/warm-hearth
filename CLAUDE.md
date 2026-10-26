# CLAUDE.md: context for AI coding sessions

Read `docs/HANDOVER.md` first: status, decisions, data model, conventions. Then the doc for the task:
`docs/OPERATIONS.md` (setup, deploy, Remote Config keys), `docs/PLAY_COMPLIANCE.md` (what keeps the
app allowed on Google Play), `docs/DATA_SAFETY.md` (Play forms).

## Hard rules
- **Not compiled yet.** If you can build (`./gradlew :app:assembleDebug`), do it and fix errors first.
- Every UI string goes in **both** `app/src/main/res/values/strings.xml` and `values-hi/strings.xml`
  (escape `'` and `"`, same placeholders).
- Use `ui/Theme.kt` (`Palette`, `Tone`) and `ui/Components.kt`; senior touch targets ≥ 72dp.
- Never add features that read messages, call logs, browsing, per-app usage or continuous location,
  or that send data off the senior's phone without an in-app disclosure + consent. WarmHearth must
  stay a *sharing* tool, not a *monitoring* app (Play stalkerware policy). Privacy & sharing must stay
  reachable without the PIN.
- Health Connect data: read-only, family-only, never for ads or product suggestions.
- `premium` is set only by Cloud Functions. Don't let clients write it.
- No code download; OTA is Remote Config (`util/RemoteFlags.kt`) + server rules.
- Backend: Firebase Functions (Node 22) in `asia-south1`; run `cd firebase/functions && npm test`.
- Website: edit `web-src/`, then `cd web-src && python3 build.py`; never hand-edit `web/*.html`.
- When you change behaviour, update `docs/HANDOVER.md` (and the privacy policy / Data safety if data changes).

## Quick commands
```bash
cd firebase/functions && npm install && npm test          # backend tests
cd web-src && npm install && npm run build                  # website pages + admin/support JS
```
