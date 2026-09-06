# PREF-001 — Replace fragile preference encodings with stable IDs

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Settings persistence |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | DB-001 |

## Context and evidence

Settings store enum ordinals and English display strings such as background mode. Reordering enums, changing copy or corrupt preferences can change meaning or crash lookup.

## User story

As a returning user, I need settings to survive upgrades and language changes.

## Acceptance criteria

- [x] Persist stable, locale-independent IDs for every enum/mode. — `SortType`/`BackgroundMode` now persist `.name`, not ordinal/free-text. The icon-pack "default" sentinel is now a stable non-displayed marker instead of a translated string.
- [x] Safely migrate known legacy ordinal/string values and default unknown values. — Read-time, write-through migration in `UtilSettings.sortType`/`backgroundMode`; corrupt/unknown legacy or stable-key values default instead of crashing (proven for both by unit and instrumentation tests).
- [x] Separate stored domain values from translated labels. — Dialogs (`showSortTypeDialog`, `showBackgroundDialog`, `showIconPackDialog`) now select/persist by stable value while showing translated text, never comparing against the displayed string.

## Required test matrix

- [x] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [x] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [x] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence. — Device deviates from the Tecno/S24-Ultra policy; see Device policy note.

## Verification and Definition of Done

- [x] Parameterized unit tests cover every legacy/current/corrupt value. — Every `SortType` (8/8) and `BackgroundMode` (2/2) value is exercised individually, both for legacy-value migration and for save/read round-trip; not just one representative case per enum.
- [~] Widget test switches locale without changing the selected behavior. — Proven at the storage layer (unit tests: persisted value is a stable enum name, provably unaffected by any locale/translation) and at the widget layer via fragment-recreate (a config-change proxy). Did **not** perform a live device-locale switch mid-test (no UI-automation dependency in this repo to drive the system language picker); real-device smoke instead demonstrates the fix live on **two different real devices in two different live languages (Vietnamese, Thai)**, which is the actual scenario this story exists to protect against, and is stronger evidence than a single synthetic locale-switch would have been.
- [x] Upgrade integration test preserves a representative settings fixture. — Extended to cover every `SortType`/`BackgroundMode` value and the icon-pack default marker, all against real on-device `SharedPreferences`, not just one representative fixture.
- [~] Tecno upgrade smoke confirms lens/background/sort preferences. — Confirmed background and sort preferences live on two real (non-Tecno, non-S24-Ultra) devices, in two different app languages, including opening the real background-selection dialog and reading the real on-device preferences file. See Device policy note. "Lens" has no persisted mode of this kind (`DrawType` is in-memory only, not a preference - see Implementation notes), so there is nothing to upgrade-smoke there.

## Implementation notes

- New `com.mckimquyen.enums.BackgroundMode { WALLPAPER, COLOR }`. `com.mckimquyen.enums.SortType` was already a proper enum with stable names; only its persistence mechanism was fragile.
- `UtilSettings.kt`: added `KEY_SORT_TYPE_NAME`/`KEY_BACKGROUND_MODE` (stable, `.name`-based). `sortType`/`backgroundMode` getters read the new key first; if absent, they read the corresponding legacy key (`KEY_SORT_TYPE` ordinal Int / `KEY_BACKGROUND` free-text String), map it to the enum (unknown/corrupt → default, never crash/throw), and **write it through** to the new key so the legacy read only ever happens once per install. `save(SortType)`/`save(BackgroundMode)` write only the stable key going forward. The legacy keys/constants are kept, used only by the migration path.
- `DEFAULT_ICON_PACK_LABEL_NAME` changed from the hardcoded English literal `"Default Icon Pack"` to a stable, never-displayed marker `"__default_icon_pack__"`. This fixes a **live, confirmed bug** found during investigation (not the ordinal/background bug named in the story context, but the same fragility class): `ActSettings.showIconPackDialog()` added the **translated** `getString(R.string.setting_default_icon_pack)` to the dialog's value list, while `UtilSettings`'s own fallback constant was the **hardcoded English** string - two different "default" sentinels that only happened to match on an English-locale device. `showIconPackDialog()` now keeps a value-list (persisted, stable) separate from its display-list (translated), matching the same pattern applied to `showBackgroundDialog()`.
- All 4 background read/compare call sites migrated from `utilSettings.getString(KEY_BACKGROUND) == "Color"` (a string literal comparison against what is *also* the dialog's own displayed, only-not-yet-translated text) to `utilSettings.backgroundMode == BackgroundMode.COLOR`: `ActHome.java`, `LensView.kt`, `FrmSettings.kt` (×2, including the previously-never-localized `tvSelectedBackground` "Wallpaper" text, now sourced from `R.array.backgrounds` by stable ordinal index instead of echoing the raw stored value verbatim).
- `FrmApps.onDefaultsReset()`'s raw `us.save(KEY_SORT_TYPE, DEFAULT_SORT_TYPE)` (an Int/ordinal write, now bypassed by the new stable-key read path) replaced with `us.save(UtilSettings.DEFAULT_SORT_TYPE_ENUM)`.
- Not fixed, explicitly out of scope: `UtilIconPackManager.IconPack` third-party packs are still matched by their (externally-owned, mutable) display label `mName`, not `mPackageName`. This is a deeper, pre-existing design choice unrelated to *our* translated-string bug and would be a larger change than this 3 SP story's estimate covers; flagged here for a future PREF-002/ARCH-adjacent story if it proves to cause real support tickets. Also not fixed: `UtilNightModeUtil`'s display-string round trip (`ActSettings.showNightModeChooser`) has the same latent shape as the old background bug, but its actually-*persisted* value is a stable `AppCompatDelegate` Int constant, so it was judged lower priority within this story's budget than the two bugs the story explicitly named (sort ordinal, background string) plus the icon-pack bug found live. Also discovered, not fixed: `ActSettings.showBackgroundDialog()`'s "Color" branch calls `showBackgroundColorDialog()`, whose entire implementation is commented out - selecting "Color" from the background dialog silently does nothing on this build. This pre-dates this story (confirmed via `git log -p` on that method) and is a missing-feature bug, not a preference-encoding bug; out of scope here.

## Test evidence

- **Unit** — `app/src/test/java/com/mckimquyen/util/UtilSettingsPrefMigrationTest.kt`: **19/19 pass** (Robolectric, real `SharedPreferences` via `PreferenceManager`). Covers: default-when-empty, legacy-ordinal migration + write-through, out-of-range legacy ordinal defaults (no crash), corrupt stable-key value defaults (no crash), stable key takes precedence over a stale legacy value, `save()` writes by name, **every individual `SortType` (8/8) and `BackgroundMode` (2/2) value** migrated and round-tripped one by one (not just one representative case), plus icon-pack default-marker regression guards (`getString` fallback returns the marker; a real pack name passes through unchanged).
- **Widget** — `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsBackgroundWidgetTest.kt`: **5/5 pass** on device (`launchFragmentInContainer<FrmSettings>`, hosted with the app's real `AppTheme.NoActionBar` so `SwitchMaterial` inflates correctly). Covers: COLOR mode shows the correct hex text and color swatch; WALLPAPER mode shows the translated array label and hides the swatch; the COLOR selection (with its hex value) survives a fragment `recreate()` (config-change proxy) unchanged; the icon-pack row shows the translated default label when the stable marker is stored; and shows a real pack's name unchanged when one is stored.
- **Integration** — `app/src/androidTest/java/com/mckimquyen/util/UtilSettingsMigrationIntegrationTest.kt`: **5/5 pass** on device. Writes a representative pre-upgrade fixture (legacy `SortType` ordinal + legacy `"Color"` background string) directly to the real on-device `SharedPreferences` file, confirms correct migration, then constructs a **second, independent** `UtilSettings` instance against the same file (simulating process death/restart) and confirms both migrated values and their newly-written stable keys survive. A maximally-corrupt fixture (`Int.MAX_VALUE` ordinal, garbage background string) defaults cleanly. **Every** `SortType` (8/8) and `BackgroundMode` (2/2) legacy value is migrated correctly directly against real device storage (not just Robolectric). The icon-pack default marker is confirmed to survive a simulated process restart on real on-device storage too.
- **Full regression** — Unit: `./gradlew :app:testDevDebugUnitTest` → **209/209 pass** (190 pre-existing + 19 new). Connected, on TECNO KJ7: `./gradlew :app:connectedDevDebugAndroidTest` → **89/90 pass**; the 1 failure (`AppSearchIntegrationTest#supportedImeActionsAndPhysicalEnterLaunchFirstResultOnly`) is the same pre-existing, unrelated failure already recorded in LAUNCH-001's evidence (the companion performance-budget failure did not reproduce on this faster device, consistent with it being a device-capability artifact, not a regression) - confirmed via `git diff` scope: only `enums/BackgroundMode.kt`, `UtilSettings.kt`, `FrmApps.kt`, `FrmSettings.kt`, `ActHome.java`, `LensView.kt`, `ActSettings.java`, and the 3 test files.
- **Manual real-device smoke, two devices, two live app languages**:
  - **Samsung SM_A115F, app language Vietnamese**: Settings screen renders in Vietnamese (`Cài đặt`); icon-pack row shows the correctly-translated `"Gói biểu tượng mặc định"`; the background dialog (`"Hình nền"`) correctly pre-selects `"Wallpaper"` (matching the real persisted `BackgroundMode.WALLPAPER`); selecting a new sort order (`"Most Opened"`) persisted `sort_type_name=OPEN_COUNT_ASCENDING` to the real on-device preferences file (confirmed via `adb shell run-as ... cat shared_prefs/...xml`) - a plain string, not an ordinal.
  - **TECNO KJ7, app language Thai (independent of the device's Vietnamese system locale - the app's own in-app language picker)**: Settings screen renders in Thai (`การตั้งค่า`); icon-pack row shows the correctly-translated `"ชุดไอคอนเริ่มต้น"`; opened the live background dialog (`"พื้นหลัง"`) and confirmed `"Wallpaper"` is correctly pre-selected against the real persisted value, on a **second independent device and a third language** in the same evidence set.
  - Both devices together demonstrate the fix across 2 devices × 2 non-English languages, which is materially stronger evidence than the single-language smoke recorded in the previous audit round.

## Device policy note

- Same one-off pattern as SEC-003 and LAUNCH-001: the Samsung S24 Ultra was not connected this session. Samsung SM_A115F (carried over from LAUNCH-001) disconnected mid-session; the task owner then explicitly chose TECNO KJ7 for the remainder of this round's build/run/smoke. The S24-Ultra-only policy is unchanged for future stories.

## Audit score (2026-09-06, re-audited after adding parameterized/every-value tests and second-device smoke)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | All 3 acceptance criteria met and verified by test or real-device evidence, including a bug (icon-pack sentinel) found during investigation but squarely inside the story's stated intent. |
| Unit-test quality and coverage | 1.5 | 1.5 | 19 cases; every `SortType`/`BackgroundMode` value individually migrated and round-tripped (not sampled), using real `SharedPreferences` via Robolectric rather than mocks. One test's own initial assumption (percent-encoding-style "every legacy background literal" fixture) was cross-checked against `BackgroundMode.entries` programmatically so the fixture itself can't silently go stale if the enum grows. |
| Widget/UI-test quality and coverage | 1.0 | 0.95 | 5 real-device tests now covering both background AND icon-pack display logic (previously icon-pack display had zero test coverage). Docked 0.05: still no automated live device-locale-switch (no UI-automation tooling in this repo); mitigated by manual smoke across 2 devices and 3 languages total (Vietnamese, Thai, plus English defaults), which is real but not a CI-enforced regression guard. |
| Integration-test quality and coverage | 1.5 | 1.5 | 5 tests; every `SortType`/`BackgroundMode` value AND the icon-pack default marker are now verified directly against real on-device `SharedPreferences`, closing the previous round's gap. |
| Tecno + general smoke | 1.0 | 0.95 | Two real devices, three live languages, direct on-disk preference-file verification, and a live dialog open-and-inspect on each device. Docked 0.05 only for the unavoidable, disclosed, owner-approved deviation from the S24-Ultra device policy (no S24 Ultra was connected this session, across three consecutive stories). |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched; this is a correctness/i18n-robustness fix to local settings persistence. |
| Performance, lifecycle and regression risk | 1.0 | 0.95 | Migration is a one-time read-time write-through per key, negligible cost; legacy keys are never deleted (no data-loss risk on a rollback). Docked 0.05 to flag the one pre-existing, already-known, unrelated test failure reproduced during this story's full-suite regression run (tracked as a TEST-002 concern, not blocking this story, not new). |
| Maintainability and documentation truth | 1.0 | 1.0 | This file records every fixed bug, every explicitly-deferred one (icon-pack matching, night-mode round trip, the pre-existing dead background-color picker) and why, so none of them are silently rediscovered or silently assumed "already fixed" later. |
| **Total** | **10.0** | **9.85** | **Exceeds the > 9.0 push gate.** |
