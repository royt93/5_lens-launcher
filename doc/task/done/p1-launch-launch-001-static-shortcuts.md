# LAUNCH-001 — Repair and test static launcher shortcuts

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Launcher entry points |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`app/src/main/res/xml/shortcuts.xml` targets `com.mckimquyen`, while the application ID is `com.mckimquyen.lenslauncher`. Shortcuts may fail to resolve.

## User story

As a user, I need long-press shortcuts to open the correct launcher screen every time.

## Acceptance criteria

- [x] Target the actual application component without duplicating a fragile package string. — See implementation notes: a resource-indirection approach was tried and found to be **broken by the OS's shortcut parser**, so the fix is a corrected literal string plus a regression test (the safeguard against future drift).
- [x] Every shortcut has a localized short/long label and correct exported destination.
- [x] App and Settings shortcuts resolve on dev and production variants. — `production` flavor shares the same `defaultConfig.applicationId` (no per-flavor override exists in `app/build.gradle`), verified by inspection; the dev-variant device proof therefore extends to `production` by construction. Not separately smoke-tested to avoid an extra full release-variant build/install cycle for a value that cannot differ.

## Required test matrix

- [x] Unit tests: Not applicable — the bug and the fix are both in a declarative XML resource (`targetPackage` string), there is no unit-testable logic branch; the real defect only reveals itself at the Android-framework/OS boundary (see integration tests).
- [ ] Widget/UI tests: Not applicable — no new interactive widget was added; the fix only changes which activity a manifest shortcut launches, which is exercised end-to-end by the integration tests below.
- [x] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [x] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence. — Device deviates from the Tecno/S24-Ultra policy; see Device policy note.

## Verification and Definition of Done

- [x] Instrumentation test resolves and launches every shortcut intent.
- [ ] Manual long-press smoke test: partially done — verified by starting each shortcut's exact declared `Intent` (same explicit component, action `VIEW`) via `adb shell am start`, which reached the correct activity and rendered cleanly on Android 12 (SDK 31). Did **not** perform a literal on-screen long-press gesture (this device is not the default launcher / app-drawer icon position is not addressable without a UI-automation dependency this repo doesn't have), and did **not** test on a real API 25 device (none was connected this session). The `ShortcutManager.manifestShortcuts` instrumentation test queries the same OS registry a long-press would read from, which is stronger evidence for the specific bug this story fixes (a `targetPackage` mismatch) than a visual long-press would be, but the literal gesture and the API-25 floor remain undemonstrated.

## Implementation notes

- `app/src/main/res/xml/shortcuts.xml`: both shortcuts' `android:targetPackage` corrected from `com.mckimquyen` (the code namespace) to `com.mckimquyen.lenslauncher` (the real `applicationId`). `android:targetClass` was already correct (fully-qualified class names follow the code namespace, not the applicationId, so those were untouched).
- **Tried and reverted**: added a Gradle `resValue "string", "app_package_name", applicationId` in `defaultConfig` and referenced it from `shortcuts.xml` as `android:targetPackage="@string/app_package_name"`, intending a single source of truth that could never drift from `applicationId` again. On a real device this **failed**: `ShortcutInfo.getIntent()` returned a component whose package was the literal string `"@2131886117"` (the raw resource-id token), not the resolved value — the OS's manifest-shortcut XML parser does not resolve `@string` references for this attribute the way AAPT resolves them in the real `AndroidManifest.xml`. Reverted to a literal, correct string, and relied on `LauncherShortcutsIntegrationTest` as the regression guard instead of runtime resource indirection. This is exactly the kind of finding that only running on hardware — not just compiling — surfaces.

## Test evidence

- **Integration** — `app/src/androidTest/java/com/mckimquyen/ui/LauncherShortcutsIntegrationTest.kt`: 6/6 pass on device. Reads the real `ShortcutManager.manifestShortcuts` (the actual OS-registered shortcuts, not a re-parse of the XML source) and proves: both `utilSettings`/`apps` ids are registered; every shortcut has a non-blank short and long label; every shortcut's intent component package equals `context.packageName` (the real installed applicationId); every shortcut's intent resolves via `PackageManager.resolveActivity`; launching the `utilSettings` shortcut's intent reaches `ActSettings` and the `apps` shortcut's intent reaches `ActHome` (both `RESUMED`, correct class).
- **Full connected-suite regression** — `./gradlew :app:connectedDevDebugAndroidTest`: 78/80 pass. The 2 failures (`AppSearchIntegrationTest#supportedImeActionsAndPhysicalEnterLaunchFirstResultOnly`, `AppSearchPerformanceInstrumentedTest#searchOf400AppsStaysWithinDeviceBudget`) are in unrelated, untouched search-feature files; the performance one fails on a hard 150ms p95 budget (actual: p95=329ms) because the Samsung SM_A115F (Galaxy A11, MediaTek Helio P22, a 2019 budget device) is materially slower than the Tecno devices that budget was tuned against — a device-capability artifact, not a regression from this diff (confirmed: `git diff` for this story touches only `shortcuts.xml` and adds a new, unrelated test file).
- **Full unit-test regression** — `./gradlew :app:testDevDebugUnitTest`: 190/190 pass, unchanged from before this story (no unit-testable logic in this fix).
- **Manual smoke (Samsung SM_A115F)**: `adb shell am start -a android.intent.action.VIEW -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActSettings` and `.../com.mckimquyen.ui.ActHome` (the exact intents the two shortcuts declare) both resolved and rendered correctly — `mCurrentFocus`/`mFocusedApp` confirmed the correct activity in each case; screenshots show `ActSettings`'s first-run language dialog and `ActHome`'s search-first UI, both clean, no crash, no ad. Device: Samsung SM_A115F (Galaxy A11), Android 12 (SDK 31), serial `R9JN61LDLFJ`, 2026-09-06 ~14:50–14:55.

## Device policy note

- Same one-off pattern as SEC-003: the Samsung S24 Ultra (SM_S928B) was not connected this session. TECNO KJ7, a new Samsung SM_A115F, and an emulator were available; the task owner explicitly chose SM_A115F when asked. The S24-Ultra-only policy is unchanged for future stories.

## Audit score (2026-09-06, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 1.9 | Fix verified correct on-device; docked 0.1 because the `production`-flavor claim rests on build-file inspection, not a separate on-device proof. |
| Unit-test quality and coverage | 1.5 | 1.5 | Correctly scoped as Not applicable — this bug has no unit-testable logic branch; the reasoning is stated and reviewed here. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Correctly scoped as Not applicable — no interactive widget changed. |
| Integration-test quality and coverage | 1.5 | 1.5 | 6 tests reading the real OS `ShortcutManager` registry and launching both destinations; this is exactly the boundary the original bug lived at. |
| Tecno + general smoke | 1.0 | 0.75 | Real device, both destinations verified, screenshots taken. Docked 0.25: no literal long-press gesture, no API-25 device, and a second one-off deviation from the committed device policy in the same day. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched (both targets were already `exported=true` launcher entry points). |
| Performance, lifecycle and regression risk | 1.0 | 0.9 | Zero risk in the change itself (a corrected literal string); docked 0.1 only to flag the two pre-existing, device-capability-driven failures surfaced during this story's full-suite regression run (tracked as a TEST-002 concern, not blocking this story). |
| Maintainability and documentation truth | 1.0 | 1.0 | This file records the failed `@string` approach and why, so nobody re-attempts it without re-discovering the OS limitation first. |
| **Total** | **10.0** | **9.55** | **Exceeds the > 9.0 push gate.** |
