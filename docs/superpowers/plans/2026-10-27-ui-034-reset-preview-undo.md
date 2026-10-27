# UI-034 — Reset preview and undo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preview the exact settings a tab reset will change, then allow one in-memory undo that restores exact old preference state, including absent keys.

**Architecture:** Keep per-tab reset entries as the single source for preview and applying defaults. Build a pure reset plan from current raw preferences, snapshot only affected keys, apply defaults only after confirmation, and store one undo snapshot in `ActSettings`; undo restores it synchronously then refreshes dependent app/UI state.

**Tech Stack:** Kotlin/Java, Android `SharedPreferences`, AlertDialog, Material Snackbar, JUnit4, Robolectric, AndroidJUnitRunner.

## Global Constraints

- Preview lists only changed entries as `label: current → default`; if no entries change, show an already-default message and write nothing.
- Snapshot only reset-touched keys; absent keys must be restored as absent. Never snapshot the entire SharedPreferences file.
- Undo is single-level and in-memory; rotation or Activity exit clears undo.
- Include lens-specific key suffixes for active lens where reset touches per-lens values.
- Preserve reset side effects: `AppEventManager.notifyAppsUpdated()`, `sendNightModeBroadcast()`, `iconPackManager.invalidate()`, `sendEditAppsBroadcast()` as applicable.
- Reuse existing row-title strings. Add only required dialog/undo/already-default strings in every one of the 16 currently existing locale folders under `app/src/main/res/values-*` (excluding qualifiers `land`, `night`, and `w*dp`).
- Physical device target is locked: TECNO KJ7 `115333744A005844` only. Never run Gradle `connected*` tasks or issue commands to another device.
- If a screenshot shows an ad covering UI, stop and wait for user confirmation `done`.
- Do not stage `.idea/` or unrelated files.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/ui/settings/ResetPlan.kt`: pure reset entries, preview rows and absent-aware snapshot.
- Modify `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`: raw key snapshot and synchronous restore helpers.
- Modify `app/src/main/java/com/mckimquyen/ui/FrmLens.kt`, `FrmSettings.kt`, `FrmApps.kt`: define each tab's entries once and apply the entry defaults.
- Modify `app/src/main/java/com/mckimquyen/ui/ActSettings.java`: show preview, apply reset, keep one snapshot, show Snackbar Undo, restore and run side effects.
- Modify 16 locale `strings.xml` resources: dialog title/body, reset/cancel, undo, already-default text.
- Create `app/src/test/java/com/mckimquyen/ui/settings/ResetPlanTest.kt`: pure plan and absent-key tests.
- Create `app/src/test/java/com/mckimquyen/util/ResetPreferenceSnapshotTest.kt`: exact restore behavior through Robolectric SharedPreferences.
- Modify/create widget tests under `app/src/androidTest/java/com/mckimquyen/ui/`: preview, cancel, reset, undo and absent-key restoration.
- Update story, `doc/task/README.md`, `doc/feature.md`; create `doc/task/AUDIT_UI-034_2026-10-27.md`.

## Task 1: Build a pure reset plan

**Files:**
- Create: `app/src/main/java/com/mckimquyen/ui/settings/ResetPlan.kt`
- Create: `app/src/test/java/com/mckimquyen/ui/settings/ResetPlanTest.kt`

**Interface produced:**

```kotlin
data class ResetEntry(val key: String, val label: String, val defaultValue: Any)
data class ResetPreviewRow(val key: String, val label: String, val currentValue: Any?, val defaultValue: Any)
data class ResetSnapshot(val values: Map<String, Any?>) // null means key absent

data class ResetPlan(val rows: List<ResetPreviewRow>, val snapshot: ResetSnapshot) {
    companion object {
        fun build(current: Map<String, *>, entries: List<ResetEntry>): ResetPlan
    }
}
```

- [ ] **Step 1: Write failing pure tests.** Cover: changed entries only; unchanged entries omitted; absent key has `currentValue == null` and is present in snapshot map; lens-suffixed keys are independent; no-op plan has empty rows and snapshot.

```kotlin
@Test
fun `plan lists only changed entries and remembers an absent key`() {
    val entries = listOf(
        ResetEntry("vibrate", "Vibration", false),
        ResetEntry("scale_lensA", "Scale", 1.2f),
        ResetEntry("night", "Night mode", 2)
    )
    val plan = ResetPlan.build(mapOf("vibrate" to true, "scale_lensA" to 1.2f), entries)

    assertEquals(listOf("vibrate", "night"), plan.rows.map { it.key })
    assertEquals(true, plan.snapshot.values["vibrate"])
    assertTrue(plan.snapshot.values.containsKey("night"))
    assertNull(plan.snapshot.values["night"])
}

@Test
fun `unchanged entries produce an empty plan`() {
    val plan = ResetPlan.build(mapOf("vibrate" to false), listOf(ResetEntry("vibrate", "Vibration", false)))
    assertTrue(plan.rows.isEmpty())
    assertTrue(plan.snapshot.values.isEmpty())
}
```

- [ ] **Step 2: Run focused tests and confirm compile failure** because `ResetPlan` is absent.

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.ui.settings.ResetPlanTest'`
Expected: unresolved reference to `ResetPlan` and entry types.

- [ ] **Step 3: Implement the pure data types and plan.** Compare values by equality; use `containsKey` so absent differs from stored default. Populate snapshot only for changed rows, preserving present null only if SharedPreferences ever exposes it (normally absent).

- [ ] **Step 4: Run focused tests and mutation-check no-op/absence behavior.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.ui.settings.ResetPlanTest' --rerun`
Expected: all tests pass. Temporarily change `containsKey` handling so absent equals default; verify absent-key test fails, restore and rerun green.

- [ ] **Step 5: Commit pure plan.**

```bash
git add app/src/main/java/com/mckimquyen/ui/settings/ResetPlan.kt app/src/test/java/com/mckimquyen/ui/settings/ResetPlanTest.kt
git commit -m "feat(settings): model exact reset preview and undo state"
```

## Task 2: Exact preference snapshots and restore

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
- Create: `app/src/test/java/com/mckimquyen/util/ResetPreferenceSnapshotTest.kt`

**Interface produced:**

```kotlin
fun snapshotKeys(keys: Set<String>): ResetSnapshot
fun restoreKeys(snapshot: ResetSnapshot): Boolean
```

- [ ] **Step 1: Write failing Robolectric tests** using isolated default SharedPreferences. Seed a changed Boolean and String, leave one key absent, plus unrelated sentinel keys for entitlement/search history/language. Snapshot reset keys, overwrite/remove them, restore, then assert values and absence exactly match; assert unrelated sentinels remain unchanged. Add commit-failure handling only if existing test seam permits it without introducing abstractions.

- [ ] **Step 2: Run focused tests to confirm missing-method compile failure.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.ResetPreferenceSnapshotTest'`
Expected: unresolved reference to `snapshotKeys` / `restoreKeys`.

- [ ] **Step 3: Implement raw key snapshots and synchronous restore.** Use `prefs.all`; snapshot every requested key with `containsKey`, using `null` for absent. Restore only snapshot keys: remove absent keys; write supported `SharedPreferences` types (`String`, `Boolean`, `Int`, `Long`, `Float`, `Set<String>`). Use `commit()` and return its Boolean. Never clear all preferences.

- [ ] **Step 4: Run tests and mutate scope.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.ResetPreferenceSnapshotTest' --rerun`
Expected: PASS. Temporarily replace the requested-key iteration with all current keys; unrelated-sentinel assertion must fail. Restore and rerun.

- [ ] **Step 5: Commit snapshot helper and tests.**

```bash
git add app/src/main/java/com/mckimquyen/util/UtilSettings.kt app/src/test/java/com/mckimquyen/util/ResetPreferenceSnapshotTest.kt
git commit -m "feat(settings): snapshot and restore selected preference keys"
```

## Task 3: Make tab reset entries the source of truth

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmLens.kt`, `FrmSettings.kt`, `FrmApps.kt`
- Modify: `app/src/test/java/com/mckimquyen/ui/settings/ResetPlanTest.kt`

**Interface produced:** each tab exposes `resetEntries(): List<ResetEntry>` and `applyReset(entries: List<ResetEntry>)`; lens-specific keys are resolved using current `activeLensId` at plan creation.

- [ ] **Step 1: Write drift-contract tests** asserting each tab's entry keys equal exactly these sets (taken from the current `resetToDefault` code on 2026-10-27; `<lens>` means `lensKey(base, activeLensId)`, i.e. the unsuffixed key for the default lens):

```kotlin
private val lensKeys = setOf(
    "icon_size", "distortion_factor", "scale_factor", "animation_time", "lens_elastic_overscroll"
) // icon_size and distortion_factor are <lens> keys

private val settingsKeys = setOf(
    "vibrate_app_hover", "vibrate_app_launch", "haptic_intensity", "show_name_app_hover", "show_touch_selection",
    "show_focus_halo", "show_search_bar", "recent_apps_quick_panel_enabled", "keep_screen_on", "clean_lens_mode",
    "depth_of_field", "smart_focus_bias", // <lens> key
    "quick_action_calculator", "quick_action_unit", "quick_action_timer", "quick_action_battery",
    "quick_action_settings", "search_hint_text", "show_new_app_tag", "show_notification_badges",
    "background_mode", "background_color", "highlight_color", "icon_pack_package_name", "icon_pack_label_name",
    "night_mode"
)

private val appsKeys = setOf("sort_type_name")
```

Before writing the sets, read each `UtilSettings.KEY_*` constant used in `FrmSettings.resetToDefault` and `FrmLens.resetToDefault` and replace any literal above that differs from the constant's real value; the test must reference the constants, not literals, in the final code. Also assert keys are unique within each list.

- [ ] **Step 2: Run the contract tests and confirm red** because tabs do not expose reset entries.

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.ui.settings.ResetPlanTest'`
Expected: unresolved references or missing reset-key contract.

- [ ] **Step 3: Extract one named entry list per tab and use it to apply defaults.** Keep labels linked to existing localized row titles where available; do not duplicate default constants. Replace duplicated `save(...)` calls in each `resetToDefault` with applying the current entries through typed saves. Preserve side effects currently performed after reset; `ActSettings` orchestrates each tab's post-reset/undo refresh.

- [ ] **Step 4: Run focused unit tests and mutation-check one entry per tab.** Remove one entry from Lens, Settings, then Apps list in turn; its contract test must fail each time. Restore all changes and rerun green.

- [ ] **Step 5: Commit entry extraction.**

```bash
git add app/src/main/java/com/mckimquyen/ui/FrmLens.kt app/src/main/java/com/mckimquyen/ui/FrmSettings.kt app/src/main/java/com/mckimquyen/ui/FrmApps.kt app/src/test/java/com/mckimquyen/ui/settings/ResetPlanTest.kt
git commit -m "refactor(settings): define reset entries per settings tab"
```

## Task 4: Preview, confirmation and one-level undo

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActSettings.java`
- Modify: `app/src/main/res/values*/strings.xml` (all 16 locale folders)
- Create/modify: `app/src/androidTest/java/com/mckimquyen/ui/ResetDefaultsWidgetTest.kt`

**Consumes:** `ResetPlan`, `UtilSettings.snapshotKeys/restoreKeys`, each tab's reset entries/apply method.

- [ ] **Step 1: Add localized strings** to all 16 locale resources: reset-preview title, changed-items body format, Reset, Cancel, Undo, and Already-default. Reuse existing row-title resources for each setting. Add a script check that resource names match across all 16 locales; do not copy English text into non-English locale files.

- [ ] **Step 2: Write widget tests first.** Cover changed row display; Cancel leaves all preferences untouched; Reset applies defaults and Snackbar exposes Undo; Undo restores both stored values and absent keys; unrelated sentinel prefs remain untouched; no-op plan shows Already-default and writes nothing. Tests call the actual menu route and tap actual dialog/Snackbar controls.

- [ ] **Step 3: Run widget tests and confirm red** because reset still applies immediately and shows success Snackbar without preview/Undo.

Run: `./gradlew assembleDevDebugAndroidTest`; run only `ResetDefaultsWidgetTest` on locked TECNO KJ7 after device backup (Task 5 policy below).
Expected: preview/Undo assertions fail against current behavior.

- [ ] **Step 4: Implement dialog and Snackbar flow.** `ActSettings.resetTabDefaults(tab)` gathers entries/current raw prefs, builds plan, and:
  - if `rows.isEmpty()`, show Already-default and return without writes;
  - otherwise show a dialog with the exact changed rows and Reset/Cancel;
  - on Reset, snapshot only `plan.snapshot` keys, apply defaults, run per-tab side effects, then show Snackbar Undo;
  - on Undo, restore via `commit()`, show existing save-failure feedback if commit returns false, re-run side effects and refresh visible tab values; clear the stored snapshot so action cannot replay.
  - Clear any previous pending undo before starting a different reset. Do not retain an Activity or Fragment view in Snackbar callbacks; resolve live interfaces at callback time and guard null.

- [ ] **Step 5: Run tests and mutation-check dialog/undo branches.**

Run: focused widget plus `testDevDebugUnitTest --tests '*Reset*' --rerun`.
Expected: PASS. Mutate Cancel to execute reset, remove absent-key restoration, and skip side-effect refresh; each corresponding assertion must fail. Restore and rerun green.

- [ ] **Step 6: Commit UI flow and locale strings.**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActSettings.java app/src/main/res/values*/strings.xml app/src/androidTest/java/com/mckimquyen/ui/ResetDefaultsWidgetTest.kt
git commit -m "feat(settings): preview resets and allow one-level undo"
```

## Task 5: KJ7 smoke, full gate, audit, close and push

**Files:**
- Create: `doc/task/AUDIT_UI-034_2026-10-27.md`
- Move: story `doc/task/todo/p3-ui-ui-034-reset-undo-preview.md` → `doc/task/inprogress/` → `doc/task/done/`
- Modify: `doc/task/README.md`, `doc/feature.md`

- [ ] Back up app `databases/` and `shared_prefs/` before install; ensure backup tar is non-empty. Record model/API, locale, font scale, Contacts permission and timestamp. Use only `adb -s 115333744A005844`; never run `connected*` tasks.
- [ ] Build/install `devDebug` and `devDebugAndroidTest`; run `ResetDefaultsWidgetTest` plus relevant settings widget regressions one class at a time. If any screenshot contains ad overlay, stop and wait for `done`.
- [ ] Restore app data byte-for-byte and verify with `diff -rq`; recheck locale, font scale and permission.
- [ ] Run full gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun`; record unit totals, lint errors/warnings, device results, mutation outcomes.
- [ ] Run `git diff --check` and `bash scripts/check-secrets.sh`.
- [ ] Audit exact per-tab key lists, absent-key behavior, selected-key scope, side effects on reset/undo, locale completeness, lifecycle behavior, and score > 9.0 before push.
- [ ] Tick story checkboxes; update README and feature tracker (exactly one `## 🟡 In progress` heading); move to `done/` only if all checks pass. Commit docs and push `origin/dev` only if score > 9.0. Never stage `.idea/`.

## Self-review

- Spec coverage: per-tab reset list, preview changed-only, no-op behavior, exact absent-aware snapshot, single-level in-memory undo, side effects, 16 locale folders, JVM/Robolectric/widget tests, KJ7 restore, full gate and audit all have tasks.
- Scope boundary: no snapshot of unrelated preferences, no persistence across rotation/process death, no multi-level undo, no new settings switch.
- Critical constraint: dialog/apply/undo must not retain Fragment views past lifecycle; use existing host interfaces and re-read live references for refresh.
