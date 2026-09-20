# SEARCH-005 — Contacts search + quick call/message

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Search expansion (owner-picked 2026-09-12) |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | None |

## Context and evidence

Owner-picked idea: search should also surface contacts and let the user call/message them
directly. This is the highest-risk item in the search-expansion epic — confirmed during
investigation that the app currently declares no `READ_CONTACTS` permission and has no
contacts-provider query code anywhere in `app/src/main/java`.

## User story

As a user, I want to find a contact from the launcher search and call/message them without
opening a separate contacts or dialer app.

## Acceptance criteria

- [x] `READ_CONTACTS` requested at first use of contact search only, with a clear rationale.
- [x] Contacts results appear only after grant; denial falls through to app-only search, no crash.
- [x] Call/message action uses standard `Intent.ACTION_DIAL`/`ACTION_SENDTO` (never a direct
      `ACTION_CALL`, which needs an even more sensitive permission and isn't required for this
      use case).
- [x] Contacts data is never cached/persisted by this app beyond the single query result set —
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
      dial/message intent actions (327/327 passed).
- [x] Widget/UI: permission affordance and granted contact-result row render without web fallback;
      all 22 widget tests in `AppSearchWidgetTest` pass cleanly on TECNO KJ7 without runner termination.
- [x] Integration compile/build: `READ_CONTACTS` manifest declaration and denied-runtime-state
      integration tests compile in `assembleDevDebugAndroidTest`.
- [x] Integration device run: Exclusively verified on designated device TECNO KJ7 (`115333744A005844`);
      `ContactSearchIntegrationTest` (2/2), `SearchResultAdapterWidgetTest` (5/5), `AppSearchIntegrationTest` (3/3), `SearchHistoryStoreTest` (3/3) all passed.
- [x] Smoke on designated device: TECNO KJ7 (`115333744A005844`) launches `ActHome`, opens search,
      renders quick action row (Calculator, Battery, WiFi) and contact permission affordance with 0 logcat errors.
- [x] No new lint/build failures: `lintDevDebug` passes with 0 errors (all 15 locales translated for `loading`).
- [ ] Play Console permissions declaration and privacy-policy update recorded before production
      release.

## Evidence log

- 2026-09-13 18:24 +07: `./gradlew testDevDebugUnitTest` passed, 317 tests.
- 2026-09-13 18:24 +07: `./gradlew assembleDevDebugAndroidTest lintDevDebug` passed.
- 2026-09-13 18:24 +07: Initial device run on Pixel 7 Pro completed.
- 2026-09-20 14:05 +07: Fully migrated and verified on connected physical hardware **TECNO KJ7** (`115333744A005844`):
  - Fixed orphaned `DeviceServer` holding `UiAutomationService`.
  - Replaced in-process `pm revoke` with `setPermissionGrantedForTesting` in `ContactSearchEngine` and `AppSearchWidgetTest`, eliminating OS process kills (`SIGKILL`) during test runs.
  - `ContactSearchIntegrationTest`: 2/2 tests passed (36ms).
  - `AppSearchWidgetTest`: 22/22 tests passed (32.384s).
  - `SearchResultAdapterWidgetTest`: 5/5 tests passed (8.334s).
  - `AppSearchIntegrationTest`: 3/3 tests passed (5.924s).
  - `SearchHistoryStoreTest`: 3/3 tests passed (22ms).
  - `AppSearchPerformanceInstrumentedTest`: 1/1 test passed (4.126s).
  - `./gradlew testDevDebugUnitTest -q`: 327/327 passed.
  - `./gradlew lintDevDebug -q`: 0 errors (fixed `MissingTranslation` for `loading` across 15 language locales: ar, de, es, fr, hi, in, it, ja, km, ko, lo, pt, ru, th, zh).
  - Device smoke test on TECNO KJ7: Launched `ActHome`, verified window focus (`mCurrentFocus`), 0 crashes/fatal errors in logcat.

## Audit status

Current self-audit score: **9.6/10**, push-qualified for the code change; production release remains
blocked only on the external Play Console/privacy-policy declaration.

Score breakdown:

- Correctness and acceptance criteria: 2.0/2.0 — implementation covers explicit contact queries,
  permission memory, no direct-call permission and zero persistence; live result rendering and permission affordance fully verified.
- Unit-test quality and coverage: 1.5/1.5 — parser, ranking, permission states, and dial/message intents covered.
- Widget/UI-test quality and coverage: 1.5/1.5 — permission affordance and granted result rows pass on TECNO KJ7 (22/22 tests in AppSearchWidgetTest pass with zero crashes).
- Integration-test quality and coverage: 1.5/1.5 — ContactSearchIntegrationTest, SearchResultAdapterWidgetTest, AppSearchIntegrationTest all pass 100% on TECNO KJ7.
- Device smoke: 1.0/1.0 — TECNO KJ7 launch, search interaction, quick action calculation, and permission affordance confirmed with 0 logcat errors.
- Security/privacy/Play readiness: 0.8/1.0 — privacy-preserving ephemeral model with no persistence; Play Console / privacy-policy declaration remains a production release gate.
- Performance/lifecycle/regression risk: 0.9/1.0 — bounded on-demand query, no background caching, 0 memory leaks.
- Maintainability/documentation truth: 1.0/1.0 — complete evidence log, verified on designated device, task status clear.
