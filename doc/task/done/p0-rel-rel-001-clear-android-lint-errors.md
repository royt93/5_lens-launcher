# REL-001 — Clear Android lint release blockers

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P0 |
| Evidence | confirmed |
| Epic | Release readiness |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | None |
| External prerequisites | Translation review |

## Context and evidence

Before this change, `./gradlew lintDevDebug` failed with 5 errors: two missing `android.permission.VIBRATE` findings in `ActVipManagement.kt`, `android:tint` in two language layouts, and `no_internet` missing from 15 locales.

## User story

As a release engineer, I need lint to be green so actionable regressions cannot be hidden by a broken gate.

## Acceptance criteria

- [x] Declare/use vibration safely or replace it with permission-free view haptic feedback.
- [x] Replace incompatible tint attributes with AppCompat equivalents.
- [x] Add reviewed translations for every shipped locale; do not suppress a translatable string.
- [x] Triage 166 remaining warnings into the existing accessibility, performance, build and release-readiness backlog; no lint baseline was added.

## Required test matrix

- [x] Unit tests: `LocaleHelperTest` verifies every supported locale owns a non-empty, non-English-fallback `no_internet` resource.
- [x] Widget/UI tests: `LanguageBottomSheetWidgetTest` verifies both language-picker icons receive their configured AppCompat tint.
- [x] Integration tests: `MultiLanguageIntegrationTest` verifies the installed manifest permission and loads English, Vietnamese, Arabic and CJK resources.
- [x] Tecno device smoke (user-approved Pixel substitute): Pixel 7 Pro, Android 17/API 37, online through a validated VPN, devDebug `2026.09.05`, 2026-09-05 19:54 ICT. Combined-wave cold launch completed in 658 ms with no app crash in logcat. Final commit SHA is recorded by the wave audit after commit.

## Verification and Definition of Done

- [x] `./gradlew testDevDebugUnitTest lintDevDebug` passes: 137 unit tests, zero lint errors and 166 warnings.
- [x] Vibration is declared as a normal permission; the existing implementation keeps its API 25 legacy branch and current-API haptic branch. The current target was verified on API 37.
- [x] Language resource smoke covers English, Vietnamese, Arabic, Chinese and Japanese; the complete 42-test widget/integration suite passes on Pixel 7 Pro.

## Implementation notes

- Added `android.permission.VIBRATE` to the app manifest.
- Converted the two affected language-picker image views to `AppCompatImageView` and applied `app:tint`.
- Added `no_internet` to all 15 previously missing shipped locale files.
- Renamed 11 existing VIP instrumentation test methods whose space-containing JVM names could not be dexed for minSdk 25; test behavior is unchanged.
- The remaining lint warnings are dominated by `UnusedResources` (56), `UseKtx` (37), `HardcodedText` (16), accessibility (`ContentDescription`, 7), overdraw (6), SDK cleanup and launcher-icon checks. These are covered by `PERF-002`, `ARCH-001`, `BUILD-001`, `A11Y-001` and `REL-002`.
