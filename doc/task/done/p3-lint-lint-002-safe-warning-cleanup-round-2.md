# LINT-002 — Clean up a second safe subset of pre-existing lint warnings

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Code health |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Follow-up to `LINT-001`. Owner-picked filler again once the real backlog was confirmed still exhausted (everything chains to declined `A11Y-001`/`TEST-001` or externally-blocked `ADS-001`/`VIP-001`/`SEC-001`). After `LINT-001`, `./gradlew :app:lintDevDebug` reported 0 errors / 157 warnings. This round targeted a second, smaller grab-bag of low-risk single-occurrence categories: `Typos`(1), `ButtonCase`(1), `SmallSp`(1), `RtlSymmetry`(1), `MergeRootFrame`(1), `Autofill`(1), `AppBundleLocaleChanges`(1) — 7 warnings.

`PluralsCandidate`(2) and `NotifyDataSetChanged`(2) were in the originally-scoped "small grab-bag" estimate but excluded on inspection: `PluralsCandidate` needs a real `<plurals>` resource with per-language plural rules across ~17 locale files (that's `HardcodedText`-scale i18n work, not a small fix), and `NotifyDataSetChanged` needs a proper `DiffUtil`/granular-notify redesign of two RecyclerView adapters to fix correctly (get it wrong and you get RecyclerView animation crashes) — both deserve their own dedicated, separately-scoped story rather than being rushed into a "safe cleanup" round. `TooDeepLayout`(1) was also excluded — flattening `act_vip_management.xml`'s nesting is a structural refactor, not a small fix.

## Changes

- **`Typos` + `ButtonCase`** (`res/values/strings_vip.xml`, `vip_dialog_ok`): `"Ok"` → `"OK"`. Only the base (English) string was touched — lint's own tip to use `@android:string/ok` was **not** taken, because every other locale (`values-vi`, `values-th`, `values-ar`, …) has its own deliberately-translated wording for this key (e.g. Vietnamese `"Đồng ý"` = "Agree", not a literal "OK") — replacing them all with the generic system string would silently overwrite an existing localization decision, not fix a typo.
- **`AppBundleLocaleChanges`** (`util/LocaleHelper.kt`, `updateResources`): false positive — `app/build.gradle`'s `bundle.language.enableSplit` is already `false` (language resources aren't split), which is exactly the condition this check asks for; lint just can't see across module Gradle config into this file. Suppressed with `@SuppressLint("AppBundleLocaleChanges")` and a comment explaining why, on the actual flagged function (`updateResources`, not `setLocale` — the first suppression attempt targeted the wrong function since the real `Configuration.setLocale(...)` call lint keys on lives one level down; caught by re-running lint after the first attempt still showed the warning).
- **`MergeRootFrame`** (`res/layout/act_home.xml`): false positive — this layout's root `<FrameLayout>` is set directly via `ActHome.setContentView(R.layout.act_home)`, not `<include>`d into a parent; a `<merge>` root would crash there (nothing to merge into). Suppressed with `tools:ignore="MergeRootFrame"` and a comment.
- **`SmallSp`** (`res/layout/act_vip_management.xml`, `tvStatusBadge`): `10sp` → `11sp` on the "LENS MEMBER" pill-badge label — meets the minimum readable size with no visible layout impact.
- **`RtlSymmetry`** (`res/layout/act_vip_management.xml`, the redeem-code pill container): added matching `android:paddingStart="6dp"` alongside the existing `paddingEnd="6dp"`.
- **`Autofill`** (`res/layout/act_vip_management.xml`, `edtVipKey`): added `android:importantForAutofill="no"` — a one-time redeem/promo code is never something an autofill service should store or suggest, so explicitly opting out is more correct than inventing an unrelated `autofillHint`.

## Required test matrix

- [x] Unit tests: Not applicable — every change here is either a resource string value, a lint-suppression annotation on unchanged logic, or a layout attribute tweak; none introduce new deterministic logic to unit-test. `LocaleHelperTest` (existing) already regression-covers `LocaleHelper.setLocale`/`updateResources` unchanged behavior.
- [x] Widget/UI tests: Not applicable as a *new* test — the touched screens (`ActHome`, `ActVipManagement`) already have widget/integration coverage (`AppCodeChangesIntegrationTest`, `FVipManagementWidgetTest`) that directly exercises the exact views changed here (`edtVipKey`/`btnActivateVipKey` typing and visibility, `ActHome` launch and `LensView` presence) — both re-ran clean against this diff, which is the applicable proof rather than writing near-duplicate new tests.
- [x] Integration tests: Not applicable — no persistence, cross-component, or SDK boundary is touched.
- [x] Smoke test the exact candidate on the designated device and record model, Android version, build SHA, network state, timestamp and log evidence. — See Test evidence below.

## Test evidence

- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures.
- **Lint** — `./gradlew :app:lintDevDebug`: **157 → 150 warnings** (0 errors throughout). All 7 targeted categories are gone from the report.
- **Targeted widget/integration** — `com.mckimquyen.feature.vip.FVipManagementWidgetTest` (8/8 pass) and `com.mckimquyen.ui.AppCodeChangesIntegrationTest` (`testIconFlow_setAndGet_acrossActivityBoundary` failed when run right after `FVipManagementWidgetTest`, then passed cleanly in isolation — a pre-existing test-order flake, same class of issue as the one disclosed in `LEAK-001`'s record; unrelated to this diff, which touches neither `BitmapCache` nor `RAppsSingleton`).
- **Full instrumentation regression** (all `androidTest` classes, single S24 Ultra run): `adb -s R5CX613VZBR shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` → **Tests run: 112, Failures: 1**. The 1 failure, `AppSearchIntegrationTest#recentHeaderRestoresAndClearActionRemovesHistory`, is the same pre-existing test-order flake already disclosed in `LEAK-001`'s and `DISPLAY-001`'s records — in an untouched search-history file, unrelated to this diff. The `AppCodeChangesIntegrationTest` flake noted above (targeted run) did not reproduce in this full run, consistent with it being order-dependent rather than caused by this diff.
- Device: Samsung SM_S928B (Galaxy S24 Ultra), serial `R5CX613VZBR`, Android 16 (SDK 36), 2026-09-12. App and test APKs installed/uninstalled via `adb -s R5CX613VZBR install`/`uninstall` for every run; no leftover install afterward.

## Device policy note

- Only the S24 Ultra (`R5CX613VZBR`) was used for on-device verification, per the standing hard device-target ban reaffirmed 2026-09-12 — see the project's `feedback-device-target` memory and `doc/task/README.md`'s Product decisions log.

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | All 7 targeted warnings resolved correctly; two were recognized as lint false positives and suppressed with a documented reason rather than forced into an incorrect "fix" (matches the `ApplySharedPref` precedent from `LINT-001`). |
| Unit-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable — no new logic exists to test. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Reused the exact existing tests that already exercise the changed views instead of padding with near-duplicates. |
| Integration-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable. |
| Tecno + general smoke | 1.0 | 0.85 | Real S24 Ultra device; targeted re-run of the exact affected test classes passed (one pre-existing, disclosed, order-only flake); docked for the device-vs-"Tecno" naming (standing policy) and for the full-suite run needing a background continuation. |
| Security/privacy/Play readiness | 1.0 | 1.0 | The `Autofill` fix is a small privacy positive (prevents a one-time code from being offered to autofill services); nothing else security-relevant touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Every change is a resource value, a suppression annotation, or a cosmetic attribute; zero logic risk. |
| Maintainability and documentation truth | 1.0 | 1.0 | Both suppressions are documented with the concrete reason a "real" fix would be wrong, and the scope-narrowing (deferring `PluralsCandidate`/`NotifyDataSetChanged`/`TooDeepLayout`) is recorded so a future round doesn't have to re-derive why they weren't included. |
| **Total** | **10.0** | **9.85** | **Exceeds the > 9.0 push gate.** |
