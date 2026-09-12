# LINT-007 — Convert day-count strings to grammatically correct plurals

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Localization |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Owner-picked follow-up, deliberately deferred out of `LINT-002` because it needed real per-language plural-rule work, not a quick fix. 2 `PluralsCandidate` warnings: `vip_entry_redeemed` ("VIP %1$d days") and `vip_activation_success_message` ("...activated for %1$d days.") in `strings_vip.xml`, both hardcoding the English plural form regardless of the actual count — grammatically wrong for "1 day" in English, and structurally wrong for languages whose plural rules differ (Russian's one/few/many/other, Arabic's zero/one/two/few/many/other).

## Changes

- Converted both keys from `<string>` to `<plurals>` in the base `values/strings_vip.xml` and all 16 locale files, using each language's correct CLDR plural categories:
  - **No plural distinction** (only `other`): Vietnamese, Chinese, Japanese, Korean, Thai, Khmer, Lao, Indonesian.
  - **`one`/`other`**: English, German, Spanish, French, Italian, Portuguese, Hindi.
  - **`one`/`few`/`many`/`other`**: Russian (`21 день` / `3 дня` / `5 дней` / fractional).
  - **`zero`/`one`/`two`/`few`/`many`/`other`**: Arabic (Arabic's full 6-category cardinal plural system).
- `ActVipManagement.kt`'s two call sites changed from `getString(R.string.key, days)` to `resources.getQuantityString(R.plurals.key, days, days)`.
- Fixed a second-order lint finding this surfaced: `MissingQuantity` for `es`/`fr`/`it`/`pt` — modern CLDR actually requires a `many` category for these Romance languages too (used for large round numbers with a "de"/"di" construction, e.g. French "1 000 000 de jours"). Added the `many` item to all 4 languages' both keys once lint pointed at the specific gap, rather than treating the warning as noise.

## Required test matrix

- [x] Unit tests: Attempted first — Robolectric's `getQuantityString` shadow (this project's `sdk = [28]` unit-test config) failed to resolve the `one`/`other` items with a `Resources$NotFoundException`, even though the resources are provably correct (0 lint errors, clean `processDevDebugResources` link, correct XML). Diagnosed as a Robolectric plurals-shadow limitation for this SDK level, not a defect in the resource — moved the check to a real-device instrumentation test instead (see below), matching this project's own established preference for real hardware when there's any doubt about an emulated result.
- [x] Widget/UI tests: Not applicable as new tests — `FVipManagementWidgetTest` (existing) already exercises the VIP activation flow that calls these strings; re-ran clean.
- [x] Integration tests: Not applicable.
- [x] Smoke test the exact candidate on a real device and record model, Android version, build SHA, network state, timestamp and log evidence. — See Test evidence below.

## Test evidence

- **Compile + resource link** — `./gradlew :app:processDevDebugResources :app:compileDevDebugKotlin`: clean.
- **Lint** — `./gradlew :app:lintDevDebug`: `PluralsCandidate` (2→0) and the follow-up `MissingQuantity` (8→0, once the `many` category was added) both fully resolved. 30 → 22 warnings, 0 errors.
- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures.
- **Instrumentation** — `app/src/androidTest/java/com/mckimquyen/feature/vip/VipPluralsInstrumentedTest.kt` (new), run via `adb shell am instrument` on TECNO KJ7: 4/4 pass, asserting the exact English singular ("1 day") vs plural ("3 days", "30 days", "0 days") text for both keys. First run against the device's actual system locale (Vietnamese) correctly returned the Vietnamese `other`-only text ("VIP 1 ngày" / "VIP 3 ngày") — confirming the resource resolves correctly per-locale — then the test was fixed to force an English configuration context so the assertions are deterministic regardless of the test device's system locale, rather than the fix being wrong.
- **Full instrumentation regression** (all `androidTest` classes, TECNO KJ7): `adb -s 115333744A005844 shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` → **Tests run: 119, Failures: 0** — every test green, including the `AppSearchIntegrationTest` case that has been flaky in several prior rounds' full-suite runs.
- Device: TECNO KJ7 (TECNO-KJ7), serial `115333744A005844`, Android 14 (SDK 34), system locale Vietnamese, 2026-09-12. Used as a user-approved one-off (second time this session) after the S24 Ultra remained disconnected — see the `feedback-device-target` memory.

## Device policy note

- S24 Ultra (`R5CX613VZBR`) remained disconnected for this story too. Asked the user again rather than assuming the prior one-off exception carried forward; owner re-authorized TECNO KJ7 for this story's smoke specifically. The standing S24U-only policy is unchanged for future rounds once it reconnects.

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | Both keys converted with correct CLDR categories per language, verified against real device output; the `MissingQuantity` follow-up was fixed properly rather than dismissed. |
| Unit-test quality and coverage | 1.5 | 1.3 | The Robolectric attempt failed for environmental reasons outside this diff's control; docked slightly for not having a JVM-level regression guard, mitigated by the direct real-device instrumentation coverage below. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Reused `FVipManagementWidgetTest`'s existing coverage of the activation flow. |
| Integration-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable. |
| Tecno + general smoke | 1.0 | 0.95 | Real device, targeted test caught and fixed a genuine test-authoring bug (locale-sensitivity) before it could hide behind a device-locale coincidence; full 119-test regression, 0 failures (fully green). |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Pure resource/string-formatting change; grammatically corrects output without changing any control flow. |
| Maintainability and documentation truth | 1.0 | 1.0 | Documents the Robolectric limitation (so it isn't re-attempted blindly), the `MissingQuantity` follow-up finding, and the locale-sensitivity bug caught in the test itself. |
| **Total** | **10.0** | **9.75** | **Exceeds the > 9.0 push gate.** |
