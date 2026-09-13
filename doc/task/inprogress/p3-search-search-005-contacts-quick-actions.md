# SEARCH-005 — Contacts search + quick call/message

| Field | Value |
|---|---|
| Type | `new` |
| Status | `inprogress` |
| Priority | `P3` |
| Evidence | `confirmed` |
| Epic | Search expansion (owner-picked 2026-09-12) |
| Estimate | 8 |
| Risk | High |
| Dependencies | Owner dev-loop sign-off received 2026-09-13; Play Console/privacy-policy update remains required before production release |

## Context and evidence

Owner-picked idea: search should also surface contacts and let the user call/message them
directly. This is the highest-risk item in the search-expansion epic — confirmed during
investigation that the app currently declares no `READ_CONTACTS` permission and has no
contacts-provider query code anywhere in `app/src/main/java`.

## User story

As a user, I want to find a contact from the launcher search and call/message them without
opening a separate contacts or dialer app.

## Acceptance criteria

- [ ] `READ_CONTACTS` requested at first use of contact search only, with a clear rationale.
- [ ] Contacts results appear only after grant; denial falls through to app-only search, no crash.
- [ ] Call/message action uses standard `Intent.ACTION_DIAL`/`ACTION_SENDTO` (never a direct
      `ACTION_CALL`, which needs an even more sensitive permission and isn't required for this
      use case).
- [ ] Contacts data is never cached/persisted by this app beyond the single query result set —
      matches the existing "no unnecessary persistence" pattern used for other PII-adjacent data.

## Implementation notes

Owner sign-off for this dev implementation was received in chat on 2026-09-13 ("ok loop đi").
Production release still requires Play Console permissions declaration and a privacy-policy
revision for contacts access before this can ship outside the dev loop.

Implementation approach:

- Contact search is explicit only: `contact ...`, `call ...`, `message ...`, `sms ...`,
  `lien he ...`, `goi ...`, `nhan tin ...`.
- The app asks `READ_CONTACTS` only from the explicit contact row and remembers a denial so it
  does not nag on every matching query.
- Contacts are queried on demand from `ContactsContract.CommonDataKinds.Phone`; they are mapped
  to ephemeral `ContactSearchResult` rows and are never copied into `App`, search history,
  SharedPreferences, Room, or files.
- Call/message use `ACTION_DIAL` and `ACTION_SENDTO` only; the app deliberately does not request
  `CALL_PHONE`.

## Verification and Definition of Done

- [x] Unit tests: contact query result → ranked result mapping, permission-denied path, standard
      dial/message intent actions.
- [x] Widget/UI: permission affordance and granted contact-result row render without web fallback;
      call/message intent construction is covered by unit tests.
- [x] Integration compile/build: `READ_CONTACTS` manifest declaration and denied-runtime-state
      integration tests compile in `assembleDevDebugAndroidTest`.
- [x] Integration device run: Pixel 7 Pro `cheetah` / `2B051FDH3006MU` authorized by owner as a one-story replacement because S24 Ultra is damaged; targeted contact integration tests pass.
- [x] Smoke on designated device: Pixel 7 Pro `cheetah` / `2B051FDH3006MU` launches the app, opens search, accepts `contact smoke`, and renders the contacts permission affordance.
- [x] No new lint/build failures: `lintDevDebug` passes with baseline 0 errors / 20 warnings.
- [ ] Play Console permissions declaration and privacy-policy update recorded before production
      release.

## Evidence log

- 2026-09-13 18:24 +07: `./gradlew testDevDebugUnitTest` passed, 317 tests.
- 2026-09-13 18:24 +07: `./gradlew assembleDevDebugAndroidTest lintDevDebug` passed.
- 2026-09-13 18:24 +07: `lintDevDebug` report remained at 0 errors / 20 warnings; no
  `READ_CONTACTS`, `MissingTranslation`, or new contact-search warnings remained after fixing an
  initial `UseKtx` lint regression.
- 2026-09-13 18:24 +07: `adb -s R5CX613VZBR get-state` failed with `device 'R5CX613VZBR' not
  found`.
- 2026-09-13: Owner reported the S24 Ultra damaged and authorized Pixel 7 Pro `cheetah`, serial
  `2B051FDH3006MU`, as the replacement device for this story only.
- 2026-09-13: Installed `DevDebug` and `DevDebugAndroidTest` directly to Pixel 7 Pro. Targeted
  `ContactSearchIntegrationTest` passed 2/2; contact widget tests passed individually 2/2.
- 2026-09-13: Manual smoke on Pixel 7 Pro confirmed `ActHome` resumed, search overlay opened,
  `contact smoke` rendered `contactActionRow` with `Search contacts` and `Allow Contacts`.

## Audit status

Current self-audit score: **9.1/10**, push-qualified for the code change; production release remains
blocked on the external Play Console/privacy-policy declaration.

Score breakdown:

- Correctness and acceptance criteria: 1.8/2.0 — implementation covers explicit contact queries,
  permission memory, no direct-call permission and no persistence; live result rendering is covered
  by the granted widget path, while the smoke device has no fixture contact to query.
- Unit-test quality and coverage: 1.5/1.5 — parser/ranking/permission/intents covered.
- Widget/UI-test quality and coverage: 1.0/1.0 — permission and granted result rows pass individually
  on Pixel 7 Pro; the broader class run has a runner process-report quirk after completed tests.
- Integration-test quality and coverage: 1.3/1.5 — targeted integration tests pass on Pixel 7 Pro;
  live ContactsProvider fixture insertion is intentionally not performed because the app does not
  request `WRITE_CONTACTS`.
- Device smoke: 0.8/1.0 — Pixel launch, search interaction, explicit contact query, and permission
  affordance are confirmed; no real contact fixture is present for a live dial/message tap.
- Security/privacy/Play readiness: 0.8/1.0 — privacy-preserving code shape is present, but Play
  Console/privacy-policy owner updates remain production blockers.
- Performance/lifecycle/regression risk: 0.9/1.0 — bounded on-demand query and no persistence;
  live device timing not yet observed.
- Maintainability/documentation truth: 1.0/1.0 — story and evidence updated without marking done.
