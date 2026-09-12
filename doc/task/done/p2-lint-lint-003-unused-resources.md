# LINT-003 — Remove unused resources (drawables, dimens, strings, a style)

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Code health |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Owner explicitly asked for this after `LINT-001`/`LINT-002`/`PERF-003`, picking the `UnusedResources` bucket (58 warnings) that those earlier rounds had deliberately deferred as the largest/riskiest one — including the caution that a resource can look unused to lint while actually being reached dynamically (the exact failure mode `LAUNCH-001` already hit once with `@string` resource indirection). Every one of the 58 flagged resources was independently verified before deletion — not just trusted from the lint report — using three checks: (1) `grep` across every `.kt`/`.java`/`.xml` source file for `@type/name` and `R.type.name` references, (2) a manifest check, and (3) confirming the app's only dynamic resource lookups (`Resources.getIdentifier`, in `UtilIconPackManager`) always target a *third-party icon pack's* resources via its own package name, never this app's own resources — so no bundled drawable/string/dimen/style here can be reached reflectively.

## Changes

- **Drawables removed (22 files)**: `bg_button_gradient`, `bg_glass_card`, `bg_glass_gradient`, `bg_glass_header`, `bg_language_item_selected`, `bg_vip_badge`, `bg_vip_button_primary`, `bg_vip_edit_text`, `bg_vip_input_container`, `ic_close_24dp`, `startup` (only reference anywhere was inside an already-commented-out line in `styles.xml`), `sw_thumb_material_you`/`sw_thumb_modern`/`sw_thumb_selector`/`sw_track_material_you`/`sw_track_modern`/`sw_track_selector` (6 unused switch-thumb/track drawables), `workspace_bg.9.png` (4 density copies: hdpi/mdpi/xhdpi/xxhdpi), and `drawable-hdpi/ic_launcher.png` — a stray orphan duplicate of the real launcher icon, which lives in `mipmap-*` and is what the manifest/`ActBase` actually reference (confirmed: `@mipmap/ic_launcher` ≠ `R.drawable.ic_launcher`, two different resource IDs).
- **Dimens removed (10, from `values/dimen.xml`, plus 2 qualifier-specific duplicates)**: `text_stroke_lens`, `offset_system_vertical`/`offset_system_horizontal` (also removed their `values-land/dimen.xml` overrides — the base id was never referenced, so the land-specific value was dead too), `text_size_about`, `padding_about`, `header_margin_about`, `radius_corner_card`, `activity_horizontal_margin_apps` (also removed its `values-w820dp/dimen.xml` override), `activity_vertical_margin`, `margin_padding_small`.
- **Strings removed (16 keys from `strings.xml`, 12 keys from `strings_vip.xml`, each across all 17 locale files = 476 individual `<string>` lines)**: legacy/orphaned keys from prior feature iterations — e.g. `activity_title_arranger`/`setting_app_arranger` (a small dead reference chain: the latter's value pointed at the former, and nothing live pointed at either), `pro`/`get_pro`/`start`/`title_activity_main` (pre-rename remnants), and 12 `vip_*` keys left behind by the VIP screen's Kotlin/ViewBinding rewrite. Removed with a script that matched `<string name="…">` by exact key across every locale file, then every resulting file was verified to still be well-formed XML (`xml.etree.ElementTree.parse`) before proceeding.
- **Style removed**: `RoundedDialogTheme` (`values/styles.xml`) — an old "Color Chooser" dialog theme referencing an icon-pack-style dialog that no longer exists in code; zero references anywhere.

## Side effect (disclosed, not a regression)

Deleting the sole remaining files in `drawable-mdpi`/`drawable-xhdpi`/`drawable-xxhdpi` (the `workspace_bg` 9-patch copies) left those density folders empty, which surfaced a *new* lint suggestion, `IconMissingDensityFolder`, pointing at a **pre-existing, unrelated** gap: several `drawable-hdpi`-only icons (`ic_apps_white_24dp.png`, etc.) have never had `mdpi`/`xhdpi`/`xxhdpi` siblings. This is an icon-asset completeness matter — out of scope here, same as the `IconLauncherShape`/`IconDensities`/`IconLocation` categories already excluded from `LINT-001`/`LINT-002` (design/asset work, not code).

## Required test matrix

- [x] Unit tests: Not applicable — every change is a resource deletion; the compiler and resource linker are the correctness proof (a dangling `@string`/`@drawable`/`R.type.name` reference would fail `compileDevDebugKotlin`/`compileDevDebugJavaWithJavac`/`processDevDebugResources`, all three of which were run clean after every batch of deletions).
- [x] Widget/UI tests: Not applicable as new tests — the existing widget/integration suite already exercises every screen whose resources were touched (`ActAboutWidgetTest`, `FVipManagementWidgetTest`, `FrmLensWidgetTest`, `LanguageBottomSheetWidgetTest`, `FrmSettingsBackgroundWidgetTest`, etc.); a broken resource reference would surface as an inflate/resource-not-found crash in these, not silently pass.
- [x] Integration tests: Not applicable — no persistence/SDK boundary is touched.
- [x] Smoke test the exact candidate on the designated device and record model, Android version, build SHA, network state, timestamp and log evidence. — See Test evidence below.

## Test evidence

- **Verification method** — every one of the 58 resource names was checked with `grep -rn -E "@(type)/name\b|R\.type\.name\b"` across `app/src/main`, `app/src/androidTest`, `app/src/test`, plus a manifest check, before deletion; ambiguous short names (`pro`, `start`) were re-checked with the strict `@string/`/`R.string.` pattern after a naive substring search produced false-positive noise (matches inside unrelated English text).
- **Compile + resource link** — `./gradlew :app:compileDevDebugKotlin :app:compileDevDebugJavaWithJavac :app:processDevDebugResources`: clean, 0 errors, after every deletion batch.
- **XML well-formedness** — all 34 touched locale string files (`strings.xml` × 17, `strings_vip.xml` × 17) plus the 3 touched dimen files parsed successfully with `xml.etree.ElementTree` after the scripted removal.
- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures.
- **Lint** — `./gradlew :app:lintDevDebug`: **148 → 87 warnings** (0 errors throughout). `UnusedResources` is fully gone (58 → 0). See the disclosed side effect above for the one new, unrelated, out-of-scope suggestion this surfaced.
- **Full instrumentation regression** (all `androidTest` classes, single S24 Ultra run): `adb -s R5CX613VZBR shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` → **Tests run: 115, Failures: 0** — every test green, including the intermittent `AppSearchIntegrationTest` case that had been flaky in the last few rounds' full-suite runs.
- Device: Samsung SM_S928B (Galaxy S24 Ultra), serial `R5CX613VZBR`, Android 16 (SDK 36), 2026-09-12. App and test APKs installed/uninstalled via `adb -s R5CX613VZBR install`/`uninstall`; no leftover install afterward.

## Device policy note

- Only the S24 Ultra (`R5CX613VZBR`) was used for on-device verification, per the standing hard device-target ban reaffirmed 2026-09-12 — see the project's `feedback-device-target` memory and `doc/task/README.md`'s Product decisions log.

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | All 58 resources verified unreferenced via three independent methods (grep, manifest, dynamic-lookup-target check) before deletion; zero build/link/runtime failures across a 115-test full regression. |
| Unit-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable; the compiler/resource-linker is the correct and sufficient proof for pure resource deletions. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Reused the existing widget suite covering every touched screen rather than writing padding tests for a change with no new logic. |
| Integration-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable. |
| Tecno + general smoke | 1.0 | 0.9 | Real S24 Ultra device, full 115/115 regression pass (a genuine improvement over prior rounds' disclosed flakes); docked only for the device-vs-"Tecno" naming under the standing policy. |
| Security/privacy/Play readiness | 1.0 | 1.0 | Removing unused resources reduces APK size with no security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Smaller APK, zero behavior change; the one side effect (a new, unrelated `IconMissingDensityFolder` lint hint) is disclosed rather than hidden. |
| Maintainability and documentation truth | 1.0 | 1.0 | Documents exactly which resources were removed and why, and explains the one lint-output side effect so it isn't mistaken for a regression later. |
| **Total** | **10.0** | **9.9** | **Exceeds the > 9.0 push gate.** |
