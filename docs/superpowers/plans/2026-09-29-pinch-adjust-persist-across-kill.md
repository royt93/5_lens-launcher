# Pinch-Adjust Persist Across Kill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `FISH-009`'s pinch-to-adjust lens curvature must survive a process kill (OS memory reclaim, OEM aggressive background management, or a manual `am kill`) that happens before the user answers the "Save as default" Snackbar — the adjustment is currently lost silently.

**Architecture:** Add a per-lens **pending** `SharedPreferences` key (`UtilSettings`), written the instant a pinch gesture ends (before the Snackbar even shows) and cleared the instant the user answers it (Save or dismiss/timeout) — both already-existing code paths. On every `LensView` page bind (`ActHome.bindLensView`), check that lens's pending key; if one is left over from an interrupted session, restore it into the view and, only for the page currently on screen, re-show the exact same Snackbar so the user gets the choice they were mid-way through.

**Tech Stack:** Kotlin + Java (existing mixed codebase), AndroidX `SharedPreferences` (`androidx.core.content.edit`, already used by `UtilSettings.deleteLensSettings`), JUnit4 + Robolectric (unit), AndroidX Test + `AndroidJUnit4` + `ActivityScenario` (androidTest) — all already in this project, no new dependency.

**Design spec:** `docs/superpowers/specs/2026-09-29-pinch-adjust-persist-across-kill-design.md` (read this first).

## Global Constraints

- minSdk 25 / compileSdk 37 / targetSdk 37 (unchanged) — every API used below (`SharedPreferences`, no new Android API) already runs on API 25+.
- No new Gradle dependency.
- Device policy (locked this session — see `feedback_device_target` memory): **TECNO KJ7, serial `115333744A005844`, is the only device to build/install/test on.** Standing fallback if it drops off `adb devices`: TECNO BG6 (`118743744X002560`). Never use Samsung S24 Ultra or Pixel 7 Pro. If more than one device is attached, do **not** run `./gradlew connected*AndroidTest` (fans out to every attached device) — install with `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest`, then run tests with `adb -s 115333744A005844 shell am instrument -w -e class <FullyQualifiedTestClassName> com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`.
- No new user-visible string is introduced anywhere in this plan — the resurrected Snackbar reuses the exact `R.string.pinch_curvature_preview` / `R.string.pinch_save_default` / `R.string.setting_distortion_factor` resources `FISH-009` already shipped and translated into all 16 locales. No i18n task is needed.
- Each task's final step runs the **full** unit suite (`./gradlew testDevDebugUnitTest -q`) and reports the exact pass count (baseline + the task's new tests), not just the new test(s).
- `./gradlew lintDevDebug -q` must stay at 0 errors after every task.
- Any test whose whole purpose is proving a specific fix/behavior gets a mutation check: temporarily break the production code, confirm the test fails with a clear message, restore it, confirm green again — before checking the task off.
- Global rule (this session's CLAUDE.md, R5): no case in this plan ships without unit **and** widget **and** integration coverage. This plan's task breakdown maps directly onto that: Task 2 = unit, Task 3 = integration (this repo's own established tier for `LensView`-level tests, see `LensViewPinchIntegrationTest`), Task 4 = widget (`ActHome`/Activity-level, see `ActHomePinchWidgetTest`) + a second integration test (cross-lens/fresh-launch boundary).

---

### Task 1: Backlog docs — open stub stories for the two deferred findings

**Files:**
- Create: `doc/task/todo/p2-ui-ui-023-dialog-edittext-loses-text-on-rotation.md`
- Create: `doc/task/todo/p2-fish-fish-013-auto-export-lens-race.md`
- Modify: `doc/task/README.md`

**Interfaces:** None (docs only, no code, no tests — nothing here is implemented yet).

- [ ] **Step 1: Create the UI-023 stub**

Create `doc/task/todo/p2-ui-ui-023-dialog-edittext-loses-text-on-rotation.md`:

```markdown
# UI-023 — Dialog EditText loses typed text on rotation

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Quality |
| Estimate | 1 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Found during the `FISH-012` transient-state audit (2026-09-29). Two programmatic
`EditText`s are created without `setId()`, so Android's default
`onSaveInstanceState` view-hierarchy walk skips them entirely — a config change
(rotation, fold, locale switch) mid-typing loses whatever the user had typed,
silently:

- `adt/AppAdapter.java:613-641` — the "Set folder" dialog's name field. Persist
  only happens on the dialog's positive/neutral button (`:600-611`,
  `applyOrganization`).
- `ui/FrmSettings.kt:212-234` — the custom search-hint-text dialog's field.
  Persist only happens on the positive button (`:229`,
  `utilSettings?.save(UtilSettings.KEY_SEARCH_HINT_TEXT, ...)`).

The exact same bug, for the lens-rename dialog, was already found and fixed at
`ui/ActHome.java:765-767` (`input.setId(android.R.id.edit)`) — it was just never
applied to these other two call sites.

## User story

As a user renaming a folder or setting a custom search hint, I want my typed
text to survive an accidental rotation, so a config change doesn't silently
wipe what I just typed.

## Acceptance criteria

- [ ] Both `EditText`s get a stable id (`android.R.id.edit`, matching the
      already-fixed lens-rename dialog) so the framework's default
      `onSaveInstanceState` preserves their text across a config change.
- [ ] A widget test proves it for each dialog: type text, rotate (or simulate
      the equivalent config-change save/restore cycle), reopen/observe the
      dialog, confirm the text survived.

## Implementation notes

One line per file (`input.setId(android.R.id.edit)`, added right after each
`EditText` is constructed) — no behavior change beyond preserving state across
a config change. `android.R.id.edit` is safe to reuse across both dialogs
since each is a distinct `AlertDialog` instance with its own view hierarchy —
`ActHome`'s lens-rename dialog already establishes this is a safe, working
pattern in this codebase.

## Verification and Definition of Done

- [ ] Unit tests: not applicable — no pure logic changes; the whole fix is a
      framework-recognized id enabling default state-saving behavior.
- [ ] Widget/UI tests cover the config-change-survives-with-typed-text
      behavior for both dialogs.
- [ ] Integration tests: not applicable — no cross-subsystem boundary.
- [ ] Smoke tests pass on the designated Tecno device; record model, Android
      version, build and timestamp.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.
```

- [ ] **Step 2: Create the FISH-013 stub**

Create `doc/task/todo/p2-fish-fish-013-auto-export-lens-race.md`:

```markdown
# FISH-013 — Auto-export-lens intent extra can be silently lost

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 3 SP (needs its own design pass before implementation — not a one-line fix) |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Found during the `FISH-012` transient-state audit (2026-09-29). `FrmLens`'s
share button (`ui/FrmLens.kt:141-146`) starts `ActHome` carrying a one-shot
`EXTRA_AUTO_EXPORT_LENS` intent extra. `ActHome.consumeAutoExportExtra`
(`ui/ActHome.java:313-314`) **removes** that extra from the `Intent` the moment
it reads it — before the export it guards has actually run
(`ui/ActHome.java:521-523`, `:575-578`). The story's own comment at `:570-574`
already discloses a related bind-timing race this exact design has to work
around.

If the process dies (or that disclosed race resolves unluckily) in the window
between "extra consumed" and "export actually completes", the share request
is gone with no way to retry it — the `Intent` no longer carries the flag, and
nothing else remembers it was requested. The user taps "Share lens image" and,
on some devices/timings, nothing ever happens.

## User story

As a user tapping "Share lens image" in `FrmLens`, I want the share to either
happen or fail with a visible message, never silently do nothing.

## Acceptance criteria

- [ ] The one-shot request survives a process kill between "extra consumed"
      and "export completes" — either by not clearing the request until the
      export truly finishes (success or failure), or by persisting a pending
      flag the same way `FISH-012` persists the pinch adjustment (a
      pending-key + resurrect-on-next-bind pattern — reuse that shape if it
      fits once actually designed).
- [ ] On unrecoverable failure (e.g. the requesting lens no longer exists),
      the user sees an explicit message rather than silence.

## Implementation notes

**Needs its own brainstorm/design pass** before implementation — the two
candidate shapes above trade off differently (persisting *intent* to export
vs. deferring when the extra is cleared), and `ActHome`'s existing
`pendingAutoExportLens` bind-race comment (`:570-574`) needs to be re-read
carefully so a fix here doesn't reopen that already-solved race. Not scoped
further than this in `FISH-012`'s audit, per the owner's explicit decision to
keep that story limited to the pinch-adjust case only.

## Verification and Definition of Done

- [ ] Unit tests cover pure logic once a design exists.
- [ ] Widget/UI tests cover visible behavior (success and failure messaging).
- [ ] Integration tests cover the process-boundary/timing scenario this story
      exists to fix.
- [ ] Smoke tests pass on the designated Tecno device; record model, Android
      version, build and timestamp.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.
```

- [ ] **Step 3: Add both to the backlog index**

In `doc/task/README.md`, find:

```markdown
## 💭 Ideas
```

Replace with:

```markdown
## 💭 Ideas

- `UI-023`/`FISH-013` (2026-09-29, found during `FISH-012`'s transient-state
  audit, deliberately not fixed as part of that story per owner decision —
  see `FISH-012`'s own entry above): two dialogs lose typed text across a
  rotation (`UI-023`, one-line fix each, ready to implement) and `FrmLens`'s
  share button can silently no-op if the process dies mid-export
  (`FISH-013`, needs its own design pass first). Both filed in `todo/`.
```

- [ ] **Step 4: Commit**

```bash
git add doc/task/todo/p2-ui-ui-023-dialog-edittext-loses-text-on-rotation.md \
  doc/task/todo/p2-fish-fish-013-auto-export-lens-race.md doc/task/README.md
git commit -m "docs(backlog): file UI-023 and FISH-013 stubs from the FISH-012 transient-state audit"
```

---

### Task 2: `UtilSettings` pending-distortion-factor API

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
- Test: Create `app/src/test/java/com/mckimquyen/util/UtilSettingsPendingDistortionTest.kt`

**Interfaces:**
- Produces (used by Task 3's `LensView` and Task 4's `ActHome`):
  - `UtilSettings.KEY_PENDING_DISTORTION_FACTOR: String` (companion constant).
  - `UtilSettings.getPendingDistortionFactor(lensId: String?): Float?` — `null` when unset.
  - `UtilSettings.savePendingDistortionFactor(lensId: String?, value: Float): Unit`.
  - `UtilSettings.clearPendingDistortionFactor(lensId: String?): Unit`.

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/mckimquyen/util/UtilSettingsPendingDistortionTest.kt`:

```kotlin
package com.mckimquyen.util

import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * FISH-012: the pending-distortion-factor key is what survives a process kill between a pinch
 * gesture ending and the user answering the confirmation Snackbar (see LensView.reportPinchFinished
 * and ActHome.bindLensView's resurrect check). It follows the exact same per-lens `lensKey`
 * suffixing convention as the real KEY_DISTORTION_FACTOR (UtilSettingsPerLensTest), on purpose -
 * these tests only prove the pending key's own contract, not that convention again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsPendingDistortionTest {

    private val work = "work-lens-id"
    private val personal = "personal-lens-id"

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `pending distortion is null when never set`() {
        assertNull(freshSettings().getPendingDistortionFactor(work))
    }

    @Test
    fun `saved pending distortion reads back for that lens`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(work, 3.7f)
        assertEquals(3.7f, settings.getPendingDistortionFactor(work)!!, 0.001f)
    }

    @Test
    fun `pending distortion is independent per lens`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(work, 3.7f)
        settings.savePendingDistortionFactor(personal, 1.2f)

        assertEquals(3.7f, settings.getPendingDistortionFactor(work)!!, 0.001f)
        assertEquals(1.2f, settings.getPendingDistortionFactor(personal)!!, 0.001f)
    }

    @Test
    fun `clearing pending distortion removes only that lens's key`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(work, 3.7f)
        settings.savePendingDistortionFactor(personal, 1.2f)

        settings.clearPendingDistortionFactor(work)

        assertNull(settings.getPendingDistortionFactor(work))
        assertEquals(1.2f, settings.getPendingDistortionFactor(personal)!!, 0.001f)
    }

    @Test
    fun `the default lens uses the unsuffixed key, same convention as the real distortion key`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 2.2f)

        val rawPrefs = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
        assertEquals(2.2f, rawPrefs.getFloat(UtilSettings.KEY_PENDING_DISTORTION_FACTOR, -1f), 0.001f)
    }

    @Test
    fun `deleteLensSettings also clears a lens's pending key`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(work, 3.7f)

        settings.deleteLensSettings(work)

        assertNull(settings.getPendingDistortionFactor(work))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests UtilSettingsPendingDistortionTest -q`
Expected: FAIL to compile — `getPendingDistortionFactor`/`savePendingDistortionFactor`/`clearPendingDistortionFactor`/`KEY_PENDING_DISTORTION_FACTOR` don't exist yet.

- [ ] **Step 3: Add the constant**

In `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`, find:

```kotlin
        const val KEY_ICON_SIZE = "min_icon_size"
        const val KEY_DISTORTION_FACTOR = "distortion_factor"
        const val KEY_SCALE_FACTOR = "scale_factor"
```

Replace with:

```kotlin
        const val KEY_ICON_SIZE = "min_icon_size"
        const val KEY_DISTORTION_FACTOR = "distortion_factor"
        // FISH-012: a pinch adjustment survives here from the instant the gesture ends until the
        // user answers the confirmation Snackbar (Save or dismiss) - both of which clear it. A
        // leftover value found on the next bind means the process died before the user answered.
        const val KEY_PENDING_DISTORTION_FACTOR = "pending_distortion_factor"
        const val KEY_SCALE_FACTOR = "scale_factor"
```

- [ ] **Step 4: Add the three functions**

In the same file, find:

```kotlin
    fun saveDistortionFactor(lensId: String?, value: Float) {
        save(lensKey(KEY_DISTORTION_FACTOR, lensId), value)
    }

    fun isSmartFocusBias(lensId: String?): Boolean {
```

Replace with:

```kotlin
    fun saveDistortionFactor(lensId: String?, value: Float) {
        save(lensKey(KEY_DISTORTION_FACTOR, lensId), value)
    }

    /** FISH-012: null means no pinch was left unresolved for this lens. */
    fun getPendingDistortionFactor(lensId: String?): Float? {
        val key = lensKey(KEY_PENDING_DISTORTION_FACTOR, lensId)
        return if (prefs.contains(key)) prefs.getFloat(key, 0f) else null
    }

    fun savePendingDistortionFactor(lensId: String?, value: Float) {
        save(lensKey(KEY_PENDING_DISTORTION_FACTOR, lensId), value)
    }

    fun clearPendingDistortionFactor(lensId: String?) {
        prefs.edit { remove(lensKey(KEY_PENDING_DISTORTION_FACTOR, lensId)) }
    }

    fun isSmartFocusBias(lensId: String?): Boolean {
```

- [ ] **Step 5: Clear the pending key on lens delete**

In the same file, find:

```kotlin
    fun deleteLensSettings(lensId: String) {
        if (lensId.isNotEmpty() && lensId != LensWorkspace.DEFAULT_LENS_ID) {
            prefs.edit {
                remove("${KEY_DISTORTION_FACTOR}_$lensId")
                remove("${KEY_SMART_FOCUS_BIAS}_$lensId")
            }
        }
    }
```

Replace with:

```kotlin
    fun deleteLensSettings(lensId: String) {
        if (lensId.isNotEmpty() && lensId != LensWorkspace.DEFAULT_LENS_ID) {
            prefs.edit {
                remove("${KEY_DISTORTION_FACTOR}_$lensId")
                remove("${KEY_SMART_FOCUS_BIAS}_$lensId")
                remove("${KEY_PENDING_DISTORTION_FACTOR}_$lensId")
            }
        }
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests UtilSettingsPendingDistortionTest -q`
Expected: PASS (6 tests, 0 failures).

- [ ] **Step 7: Run the full unit suite and lint**

Run: `./gradlew testDevDebugUnitTest -q && ./gradlew lintDevDebug -q`
Expected: full suite passes (baseline + 6 new tests — report the exact total), 0 lint errors.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/UtilSettings.kt \
  app/src/test/java/com/mckimquyen/util/UtilSettingsPendingDistortionTest.kt
git commit -m "feat(fish-012): add UtilSettings pending-distortion-factor API"
```

---

### Task 3: `LensView` — persist pending value at gesture end, clear on resolve, add restore

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Test: Modify `app/src/androidTest/java/com/mckimquyen/views/LensViewPinchIntegrationTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.getPendingDistortionFactor` / `.savePendingDistortionFactor` / `.clearPendingDistortionFactor` (Task 2).
- Produces (used by Task 4's `ActHome` wiring):
  - `LensView.restoreLiveDistortionFactor(value: Float): Unit`.
  - (unchanged signatures, new side effect) `LensView.commitLiveDistortionFactor()` and
    `.resetLiveDistortionFactor()` now also clear the pending key.

- [ ] **Step 1: Write the failing integration test first**

In `app/src/androidTest/java/com/mckimquyen/views/LensViewPinchIntegrationTest.kt`, add this
import alongside the existing ones:

```kotlin
import org.junit.Assert.assertNull
```

(already present — confirm, do not duplicate). Then add, right before the final closing `}` of
the `LensViewPinchIntegrationTest` class:

```kotlin

    @Test
    fun pinchFinishing_persistsAPendingValueBeforeEitherResolutionBranch() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.liveDistortionFactor = 4.4f
            lensView.onCurvatureAdjustedListener?.onCurvatureAdjusted(4.4f, true)
        }

        assertEquals(
            "gesture-end must persist a pending value before the user answers Save/dismiss",
            4.4f,
            utilSettings.getPendingDistortionFactor(lensView.lensId)!!,
            0.001f
        )
    }

    @Test
    fun committing_clearsThePendingValue() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.liveDistortionFactor = 4.4f
            lensView.onCurvatureAdjustedListener?.onCurvatureAdjusted(4.4f, true)
            lensView.commitLiveDistortionFactor()
        }

        assertNull(
            "Save as default must clear the pending key, not just commit the real one",
            utilSettings.getPendingDistortionFactor(lensView.lensId)
        )
    }

    @Test
    fun resetting_clearsThePendingValue() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.liveDistortionFactor = 4.4f
            lensView.onCurvatureAdjustedListener?.onCurvatureAdjusted(4.4f, true)
            lensView.resetLiveDistortionFactor()
        }

        assertNull(
            "dismiss/timeout must clear the pending key too, not just revert in-memory",
            utilSettings.getPendingDistortionFactor(lensView.lensId)
        )
    }

    @Test
    fun restoreLiveDistortionFactor_setsTheLiveValueAndInvalidates() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.restoreLiveDistortionFactor(5.1f)
            assertEquals(5.1f, lensView.liveDistortionFactor!!, 0.001f)
        }
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Expected: FAILS to compile — `restoreLiveDistortionFactor` does not exist on `LensView` yet
(`UtilSettings.getPendingDistortionFactor` already exists from Task 2, so that part alone would
compile, but the fourth new test's `restoreLiveDistortionFactor` call blocks the whole file).

- [ ] **Step 3: Add `restoreLiveDistortionFactor`, clear-pending on commit/reset**

In `app/src/main/java/com/mckimquyen/views/LensView.kt`, find:

```kotlin
    fun commitLiveDistortionFactor() {
        val factor = liveDistortionFactor ?: return
        mUtilSettings?.saveDistortionFactor(lensId, factor)
        liveDistortionFactor = null
        invalidate()
    }

    fun resetLiveDistortionFactor() {
        if (liveDistortionFactor != null) {
            liveDistortionFactor = null
            invalidate()
        }
    }
```

Replace with:

```kotlin
    fun commitLiveDistortionFactor() {
        val factor = liveDistortionFactor ?: return
        mUtilSettings?.saveDistortionFactor(lensId, factor)
        mUtilSettings?.clearPendingDistortionFactor(lensId)
        liveDistortionFactor = null
        invalidate()
    }

    fun resetLiveDistortionFactor() {
        if (liveDistortionFactor != null) {
            liveDistortionFactor = null
            invalidate()
        }
        mUtilSettings?.clearPendingDistortionFactor(lensId)
    }

    /** FISH-012: restores a pinch adjustment that was persisted to the pending key but never
     *  resolved (Save/dismiss) before the process died - called by ActHome.bindLensView on every
     *  page bind when UtilSettings.getPendingDistortionFactor(lensId) is non-null. Does not touch
     *  the pending key itself; commitLiveDistortionFactor()/resetLiveDistortionFactor() clear it
     *  the normal way once the user answers again. */
    fun restoreLiveDistortionFactor(value: Float) {
        liveDistortionFactor = value
        invalidate()
    }

    /** FISH-012: persists the just-finished pinch value to the pending key *before* notifying the
     *  listener (which triggers ActHome's confirmation Snackbar) - so a process kill between
     *  gesture-end and the user answering that Snackbar doesn't lose the adjustment silently.
     *  Both call sites below (onScaleEnd, ACTION_UP) previously duplicated this same
     *  mPinchReported-guarded block; centralizing it here also closes that duplication. */
    private fun reportPinchFinished(finalDistortion: Float) {
        if (mPinchReported) return
        mPinchReported = true
        mUtilSettings?.savePendingDistortionFactor(lensId, finalDistortion)
        onCurvatureAdjustedListener?.onCurvatureAdjusted(finalDistortion, true)
    }
```

- [ ] **Step 4: Route both gesture-end call sites through `reportPinchFinished`**

Find:

```kotlin
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                liveDistortionFactor?.let { finalDistortion ->
                    if (!mPinchReported) {
                        mPinchReported = true
                        onCurvatureAdjustedListener?.onCurvatureAdjusted(finalDistortion, true)
                    }
                }
            }
```

Replace with:

```kotlin
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                liveDistortionFactor?.let(::reportPinchFinished)
            }
```

Find:

```kotlin
                    liveDistortionFactor?.let { finalDistortion ->
                        if (!mPinchReported) {
                            mPinchReported = true
                            onCurvatureAdjustedListener?.onCurvatureAdjusted(finalDistortion, true)
                        }
                    }
                    invalidate()
                    return true
                }
```

Replace with:

```kotlin
                    liveDistortionFactor?.let(::reportPinchFinished)
                    invalidate()
                    return true
                }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewPinchIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: `OK (13 tests)` (9 pre-existing + 4 new).

- [ ] **Step 6: Mutation-check the pending-write**

Temporarily comment out `mUtilSettings?.savePendingDistortionFactor(lensId, finalDistortion)` inside `reportPinchFinished`. Reinstall and rerun just the new persist test:
Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewPinchIntegrationTest#pinchFinishing_persistsAPendingValueBeforeEitherResolutionBranch com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAILS (`getPendingDistortionFactor` returns `null`).

Restore the line, reinstall, rerun the same command.
Expected: `OK (1 test)`.

- [ ] **Step 7: Mutation-check the clear-on-commit**

Temporarily comment out `mUtilSettings?.clearPendingDistortionFactor(lensId)` inside `commitLiveDistortionFactor()`. Reinstall and rerun:
Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewPinchIntegrationTest#committing_clearsThePendingValue com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAILS (pending key still present after commit).

Restore the line, reinstall, rerun the same command.
Expected: `OK (1 test)`.

- [ ] **Step 8: Run the full unit suite and lint**

Run: `./gradlew testDevDebugUnitTest -q && ./gradlew lintDevDebug -q`
Expected: full suite passes (unchanged from Task 2 — all new tests this task are androidTest), 0 lint errors.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/mckimquyen/views/LensView.kt \
  app/src/androidTest/java/com/mckimquyen/views/LensViewPinchIntegrationTest.kt
git commit -m "feat(fish-012): persist pinch value to a pending key at gesture end, clear on resolve"
```

**Deviation found and fixed during execution:** the three new tests as originally drafted called
`lensView.onCurvatureAdjustedListener?.onCurvatureAdjusted(4.4f, true)` directly (mirroring this
file's own pre-existing `testPinchUpdatesLiveDistortionAndNotifiesListener` pattern for
"simulating" a finished pinch without driving a real two-finger `ScaleGestureDetector` sequence).
That pattern invokes the listener directly and never goes through `reportPinchFinished` at all -
so it silently skipped the very code this task added. Caught immediately by the first test run
(`NullPointerException` on the `!!` in `pinchFinishing_persistsAPendingValueBeforeEitherResolutionBranch`,
not a false pass). Fixed by making `reportPinchFinished` `@VisibleForTesting internal` (same seam
shape as `setLensStateForTest` elsewhere in this file) and calling it directly from the three
tests instead - still exercises the real production function body, just without needing a
flaky synthetic multi-touch gesture to reach it.

---

### Task 4: `ActHome` — resurrect the pending adjustment on bind

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Test: Modify `app/src/androidTest/java/com/mckimquyen/ui/ActHomePinchWidgetTest.kt`
- Test: Create `app/src/androidTest/java/com/mckimquyen/ui/ActHomePinchPersistenceIntegrationTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.getPendingDistortionFactor` (Task 2), `LensView.restoreLiveDistortionFactor` (Task 3).

- [ ] **Step 1: Write the failing widget test first (single-lens resurrect)**

In `app/src/androidTest/java/com/mckimquyen/ui/ActHomePinchWidgetTest.kt`, add this import:

```kotlin
import com.mckimquyen.model.LensWorkspace
```

Then add, right before the final closing `}` of `ActHomePinchWidgetTest`:

```kotlin

    @Test
    fun aLeftoverPendingValue_resurrectsTheSnackbarOnTheNextBind() {
        utilSettings.savePendingDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 3.9f)

        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            assertEquals(
                "a leftover pending value must be restored into the live/preview state on bind",
                3.9f,
                activity.lensViews.liveDistortionFactor!!,
                0.001f
            )
            val snackbar = activity.pinchCurvatureSnackbar
            assertNotNull("the confirmation Snackbar must reappear for the active page", snackbar)
            assertTrue(snackbar?.isShown == true || snackbar?.isShownOrQueued == true)
        }
        scenario.close()
    }

    @Test
    fun resurrectedSnackbar_saveActionStillPersistsAndClearsPending() {
        utilSettings.savePendingDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 3.9f)

        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            val actionView = activity.pinchCurvatureSnackbar!!.view.findViewById<android.widget.Button>(
                com.google.android.material.R.id.snackbar_action
            )
            actionView.performClick()

            assertEquals(3.9f, utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR), 0.001f)
            assertNull(utilSettings.getPendingDistortionFactor(LensWorkspace.DEFAULT_LENS_ID))
        }
        scenario.close()
    }

    @Test
    fun noPendingValue_noSnackbarOnBind() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            assertNull(
                "a fresh session with nothing pending must not pop the confirmation Snackbar",
                activity.pinchCurvatureSnackbar
            )
        }
        scenario.close()
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomePinchWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: the two new resurrect-dependent tests FAIL (`liveDistortionFactor` stays `null`, no Snackbar) — `bindLensView` doesn't check the pending key yet. `noPendingValue_noSnackbarOnBind` passes already (nothing to regress).

- [ ] **Step 3: Wire the resurrect check into `bindLensView` AND `onPageSelected`**

**Why both places:** `bindLensView` only runs when a page is (re)bound by the `RecyclerView`
underlying `ViewPager2` — the initial creation of each page's `LensView`, or a genuine rebind
(rotation, `notifyDataSetChanged`). It does **not** run again just because the user swipes onto
an already-bound neighbouring page — `ViewPager2`'s `RecyclerView` prefetches and keeps adjacent
pages bound, so swiping to lens 2 after cold launch typically reuses a `LensView` that was already
silently bound (and, with this fix, already had its pending value restored) while lens 1 was
showing. If the resurrect Snackbar were only shown from inside `bindLensView`'s active-page
branch, it would never appear for that already-bound neighbour once the user actually swipes to
it — `lensPageChangeCallback.onPageSelected` (the callback that already exists for exactly this
"a different page is now the one on screen" moment, see its existing body below) is the only
place that reliably fires every time, regardless of bind timing.

In `app/src/main/java/com/mckimquyen/ui/ActHome.java`, find this exact block:

```java
    private void bindLensView(LensView view, LensWorkspace lens) {
        view.setPackageManager(getPackageManager());
        view.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        view.setOnCurvatureAdjustedListener((curvature, finished) -> {
            if (finished) {
                showPinchCurvatureSnackbar(curvature);
            }
        });
        // FISH-008 Phase 3: the dots indicator - the menu's only other entry point - is hidden
        // while a single lens exists, so long-pressing empty grid space has to reach it too.
        // Otherwise no single-lens install (i.e. everyone, right after the v11 migration) can
        // ever create a second lens. Anchored on the page itself, same as UI-022's icon menu.
        view.setOnEmptySpaceLongPressListener(() -> showLensManagementMenu(lensMenuAnchor()));
        // FISH-008 Phase 3 fix: after a configuration change every page rebinds while `listApp`
        // is already populated, but `lensViews` still points at the destroyed Activity's
        // LensView - so no page matched here and the restored page was left with an empty grid
        // until the next app-list broadcast. The pager cannot resolve its holder mid-rebind, so
        // match on the lens id instead: the page whose lens is the active one is the visible one.
        // Found by rotating a real TECNO KJ7 with two lenses on the second page.
        boolean isActiveLensPage = lens != null && utilSettings != null
                && lens.getId().equals(utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID));
        if (lensViews == null || lensViews == view || isActiveLensPage) {
            lensViews = view;
            if (listApp != null) {
                view.setApps(listApp);
            }
            // B3 (test-audit): must fire from here, the exact point lensViews first becomes
            // non-null, not from refreshLensList()'s DB-load callback via a separate
            // lensPager.post() - that raced two independent queuing mechanisms (a plain
            // Handler.post against the RecyclerView's own Choreographer-scheduled bind pass) and
            // could silently lose the one-shot auto-export forever if this bind lost the race.
            if (pendingAutoExportLens) {
                pendingAutoExportLens = false;
                view.post(this::exportActiveLensImage);
            }
        }
    }
```

Replace with:

```java
    private void bindLensView(LensView view, LensWorkspace lens) {
        view.setPackageManager(getPackageManager());
        view.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        view.setOnCurvatureAdjustedListener((curvature, finished) -> {
            if (finished) {
                showPinchCurvatureSnackbar(curvature);
            }
        });
        // FISH-008 Phase 3: the dots indicator - the menu's only other entry point - is hidden
        // while a single lens exists, so long-pressing empty grid space has to reach it too.
        // Otherwise no single-lens install (i.e. everyone, right after the v11 migration) can
        // ever create a second lens. Anchored on the page itself, same as UI-022's icon menu.
        view.setOnEmptySpaceLongPressListener(() -> showLensManagementMenu(lensMenuAnchor()));
        // FISH-012: a pinch adjustment that was never resolved (Save/dismiss) before the process
        // died is restored here silently, on every bind - not just the active page - so it's
        // ready the instant the user swipes to whichever lens it belonged to. The confirmation
        // Snackbar itself is only re-shown for the page currently on screen (below) - see
        // maybeShowResurrectSnackbar's own doc comment for why onPageSelected also needs it.
        restorePendingPinchIfAny(view, lens);
        // FISH-008 Phase 3 fix: after a configuration change every page rebinds while `listApp`
        // is already populated, but `lensViews` still points at the destroyed Activity's
        // LensView - so no page matched here and the restored page was left with an empty grid
        // until the next app-list broadcast. The pager cannot resolve its holder mid-rebind, so
        // match on the lens id instead: the page whose lens is the active one is the visible one.
        // Found by rotating a real TECNO KJ7 with two lenses on the second page.
        boolean isActiveLensPage = lens != null && utilSettings != null
                && lens.getId().equals(utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID));
        if (lensViews == null || lensViews == view || isActiveLensPage) {
            lensViews = view;
            if (listApp != null) {
                view.setApps(listApp);
            }
            maybeShowResurrectSnackbar(lens);
            // B3 (test-audit): must fire from here, the exact point lensViews first becomes
            // non-null, not from refreshLensList()'s DB-load callback via a separate
            // lensPager.post() - that raced two independent queuing mechanisms (a plain
            // Handler.post against the RecyclerView's own Choreographer-scheduled bind pass) and
            // could silently lose the one-shot auto-export forever if this bind lost the race.
            if (pendingAutoExportLens) {
                pendingAutoExportLens = false;
                view.post(this::exportActiveLensImage);
            }
        }
    }

    /** FISH-012: silently restores {@code lens}'s pending pinch value (if any) into {@code view}'s
     *  live/preview state. Safe to call on every bind, active page or not, so a not-yet-visible
     *  page already shows the right curvature the instant the user swipes to it. */
    private void restorePendingPinchIfAny(LensView view, LensWorkspace lens) {
        if (view == null || lens == null || utilSettings == null) return;
        Float pending = utilSettings.getPendingDistortionFactor(lens.getId());
        if (pending != null) {
            view.restoreLiveDistortionFactor(pending);
        }
    }

    /** FISH-012: re-shows the confirmation Snackbar for {@code lens}'s pending pinch value, if
     *  any - call this ONLY for the page the user is actually looking at right now. Called from
     *  both bindLensView (covers the active page being freshly bound or rebound) and
     *  lensPageChangeCallback.onPageSelected (covers swiping onto a page that was already bound
     *  as a prefetched neighbour and therefore never goes through bindLensView again -
     *  ViewPager2's underlying RecyclerView keeps adjacent pages bound without rebinding them on
     *  selection). showPinchCurvatureSnackbar already dismisses any prior instance before showing
     *  a new one, so calling this from both places for the same cold-launch page is harmless. */
    private void maybeShowResurrectSnackbar(LensWorkspace lens) {
        if (lens == null || utilSettings == null) return;
        Float pending = utilSettings.getPendingDistortionFactor(lens.getId());
        if (pending != null) {
            showPinchCurvatureSnackbar(pending);
        }
    }
```

- [ ] **Step 4: Wire the same resurrect check into `onPageSelected`**

Find this exact block:

```java
    private final ViewPager2.OnPageChangeCallback lensPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            LensView view = lensViewAt(position);
            if (view != null) {
                lensViews = view;
                if (listApp != null) {
                    view.setApps(listApp);
                }
            }
            if (position >= 0 && position < currentLenses.size()) {
                LensWorkspace lens = currentLenses.get(position);
                String currentActive = utilSettings != null
                        ? utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                        : null;
                if (lens.getId() != null && !lens.getId().equals(currentActive)) {
                    if (utilSettings != null) {
                        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lens.getId());
                    }
                    Object application = getApplication();
                    if (application instanceof RApplication) {
                        ((RApplication) application).getAppRefreshPipeline().switchLens(lens.getId());
                    }
                }
            }
        }
    };
```

Replace with:

```java
    private final ViewPager2.OnPageChangeCallback lensPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            LensView view = lensViewAt(position);
            if (view != null) {
                lensViews = view;
                if (listApp != null) {
                    view.setApps(listApp);
                }
            }
            if (position >= 0 && position < currentLenses.size()) {
                LensWorkspace lens = currentLenses.get(position);
                String currentActive = utilSettings != null
                        ? utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                        : null;
                if (lens.getId() != null && !lens.getId().equals(currentActive)) {
                    if (utilSettings != null) {
                        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lens.getId());
                    }
                    Object application = getApplication();
                    if (application instanceof RApplication) {
                        ((RApplication) application).getAppRefreshPipeline().switchLens(lens.getId());
                    }
                }
                // FISH-012: this page may already have been bound (and its pending value already
                // silently restored) as a prefetched neighbour before the user ever swiped to it
                // - bindLensView will not run again just because it's now selected, so the
                // confirmation Snackbar has to be (re-)offered from here too.
                maybeShowResurrectSnackbar(lens);
            }
        }
    };
```

- [ ] **Step 5: Run the widget test to verify it passes**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomePinchWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: `OK (6 tests)` (3 pre-existing + 3 new).

- [ ] **Step 6: Mutation-check both resurrect call sites independently**

First, temporarily delete `maybeShowResurrectSnackbar(lens);` from inside `bindLensView`'s active
branch only (leave `onPageSelected`'s call in place). Reinstall and rerun:
Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomePinchWidgetTest#aLeftoverPendingValue_resurrectsTheSnackbarOnTheNextBind com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAILS (`pinchCurvatureSnackbar` is `null` — a single-lens cold launch never reaches
`onPageSelected` before this assertion runs). Restore the line.

Second, temporarily delete `maybeShowResurrectSnackbar(lens);` from inside `onPageSelected` only
(leave `bindLensView`'s call in place). Reinstall and rerun the Task 4 Step 8 integration test
below:
Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomePinchPersistenceIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAILS (the Snackbar never reappears after swiping to the second lens — this is exactly
the real gap this task exists to close). Restore the line, reinstall, rerun both commands above.
Expected: both `OK (1 test)`.

- [ ] **Step 7: Write and run the cross-lens integration test**

Create `app/src/androidTest/java/com/mckimquyen/ui/ActHomePinchPersistenceIntegrationTest.kt`:

```kotlin
package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-012: a pinch adjustment made on a lens that was NOT the active one when the process died
 * must still resurrect correctly once the user swipes to that lens - this is the scenario
 * ActHome.maybeShowResurrectSnackbar's onPageSelected call site exists for (the lens's own
 * pending value is restored silently the moment its page is first bound, by bindLensView, but
 * the confirmation Snackbar itself only reappears once the user actually swipes onto that page).
 */
@RunWith(AndroidJUnit4::class)
class ActHomePinchPersistenceIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var utilSettings: UtilSettings

    private val secondLensId = "second-lens-pinch-test"

    @Before
    fun setup() {
        utilSettings = UtilSettings(context)
        cleanDb()
    }

    @After
    fun tearDown() {
        utilSettings.clearPendingDistortionFactor(secondLensId)
        utilSettings.deleteLensSettings(secondLensId)
        cleanDb()
    }

    private fun cleanDb(): Unit = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        dao.insertOrUpdate(LensWorkspace(id = secondLensId, name = "Second Lens", orderIndex = 1))
        Unit
    }

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
    }

    @Test
    fun pendingValueOnANonActiveLens_resurrectsSilentlyThenShowsSnackbarOnceSwipedTo() {
        utilSettings.savePendingDistortionFactor(secondLensId, 4.6f)

        val scenario = ActivityScenario.launch(ActHome::class.java)
        idle()

        // The default (active) lens page must NOT have popped a Snackbar for a value that
        // belongs to a different, not-yet-visible lens.
        scenario.onActivity { activity ->
            assertNull(
                "the active page must not show a resurrect Snackbar for another lens's pending value",
                activity.pinchCurvatureSnackbar
            )
        }

        scenario.onActivity { activity ->
            activity.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(1, false)
        }
        idle()

        scenario.onActivity { activity ->
            assertEquals(
                "swiping to the lens with a pending value must restore it into that page's live state",
                4.6f,
                activity.lensViews.liveDistortionFactor!!,
                0.001f
            )
            val snackbar = activity.pinchCurvatureSnackbar
            assertNotNull("swiping to the pending lens must now show its resurrect Snackbar", snackbar)
            assertTrue(snackbar?.isShown == true || snackbar?.isShownOrQueued == true)
        }

        scenario.close()
    }
}
```

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomePinchPersistenceIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: `OK (1 test)`.

- [ ] **Step 8: Run the full unit suite and lint**

Run: `./gradlew testDevDebugUnitTest -q && ./gradlew lintDevDebug -q`
Expected: full suite passes (unchanged from Task 2 — all new tests this task are androidTest), 0 lint errors.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActHome.java \
  app/src/androidTest/java/com/mckimquyen/ui/ActHomePinchWidgetTest.kt \
  app/src/androidTest/java/com/mckimquyen/ui/ActHomePinchPersistenceIntegrationTest.kt
git commit -m "feat(fish-012): resurrect the confirmation Snackbar for a leftover pending pinch value"
```

**Deviations found and fixed during execution (all via real device testing, not guessed):**

1. **A real regression the tests themselves caught**: `onPageSelected` DOES fire for position 0 on
   a genuine cold launch (confirmed live), so both `bindLensView`'s and `onPageSelected`'s calls to
   `maybeShowResurrectSnackbar` fire for the same page. `showPinchCurvatureSnackbar`'s existing
   "dismiss any prior instance" behavior meant the second call dismissed the first Snackbar - and
   that dismissal's callback calls `resetLiveDistortionFactor()`, which (after Task 3's change)
   also clears the pending key. Net effect: the resurrect prompt appeared and then immediately
   un-resurrected itself, silently. Fixed with a `resurrectPromptedLensIds` guard (a
   per-Activity-instance `Set<String>`) so only the first of the two calls for a given lens id
   actually shows anything - the original plan's assumption that calling both was "harmless" was
   wrong specifically because of Task 3's own new side effect.
2. **Mutation-checks for both call sites turned out inconclusive**, honestly disclosed rather than
   forced: removing either call site alone still left all current tests green, because (a)
   `onPageSelected` reliably covers the cold-launch case the original bindLensView-side check was
   meant to prove, and (b) in the cross-lens integration test, `ActivityScenario`/`ViewPager2`'s
   real bind timing for the second page's `setCurrentItem` call happened to trigger a fresh
   `bindLensView` rather than reusing an already-prefetched one. Both call sites are kept on the
   architectural grounds already documented in this codebase (`bindLensView`'s own pre-existing
   "FISH-008 Phase 3 fix" comment: a config-change rebind does not refire `onPageSelected`) -
   this just was not the specific path either automated test happened to exercise. The dedup guard
   above means keeping both is safe either way.
3. **The cross-lens integration test's own `setCurrentItem` swipe persists
   `KEY_ACTIVE_LENS_ID = secondLensId` as real production behavior, and the test's `tearDown()`
   originally didn't reset it** - this leaked into a *separate, later* `am instrument` invocation
   of `ActHomePinchWidgetTest` (SharedPreferences persist on-device across separate instrumentation
   runs, not just within one), breaking its pre-existing
   `saveAsDefaultAction_persistsValueAndCommitsLiveDistortion` test. Fixed by having this test's
   `tearDown()` reset `KEY_ACTIVE_LENS_ID` back to `LensWorkspace.DEFAULT_LENS_ID`. Confirmed fixed
   by rerunning both test classes back-to-back in the order that originally broke it.
4. **One single, non-reproducing failure** in the pre-existing
   `ActHomeLensManagementWidgetTest#deleteLens_alsoClearsThatLensOwnSettings` appeared once during
   a full-class run; it passed standalone and passed on two subsequent full-class reruns
   (16/16 both times). Disclosed as a one-off flake, consistent with this repo's own established
   pattern of disclosed-not-chased non-reproducible flakes - not traced to this diff.

---

### Task 5: Full regression + Tecno smoke (real `am kill` repro) + close the story

**Files:**
- Create: `doc/task/done/p2-fish-fish-012-pinch-persist-across-kill.md`
- Modify: `doc/task/README.md`

- [ ] **Step 1: Full unit + full instrumented regression**

Run: `./gradlew testDevDebugUnitTest -q`
Expected: full suite passes — report the exact count (baseline + 10 new unit tests from Task 2).

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` (no `-e class` filter — every instrumented test in the APK).
Expected: 0 failures beyond any already-known pre-existing flake — cross-check any red against the most recent `doc/task/done/*.md` entry's disclosed flakes before treating it as a new regression.

Run: `./gradlew lintDevDebug -q`
Expected: 0 errors.

- [ ] **Step 2: Manual smoke — normal Save/dismiss flows unaffected**

On TECNO KJ7 (`115333744A005844`):
1. `adb -s 115333744A005844 shell am start -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActHome`
2. Two-finger pinch on the lens grid, release. Confirm the Snackbar still appears exactly as before.
3. Tap "Save as default". Confirm the curvature actually changed and persisted (reopen the app, curvature is still applied).
4. Pinch again, release, this time let the Snackbar time out without tapping. Confirm the grid reverts to the previously-saved curvature.
5. **Stop and check for an ad per this repo's standing screenshot-test rule (R4)**: if any ad overlay is visible at any point, stop, note the ad type/position, wait for confirmation before continuing.

- [ ] **Step 3: Manual smoke — the actual `am kill` repro this story exists to fix**

1. `adb -s 115333744A005844 shell am start -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActHome`
2. Two-finger pinch on the lens grid, release — confirm the Snackbar is showing.
3. `adb -s 115333744A005844 shell am kill com.mckimquyen.lenslauncher`
4. `adb -s 115333744A005844 shell pidof com.mckimquyen.lenslauncher` — confirm empty (process is dead).
5. `adb -s 115333744A005844 shell input keyevent KEYCODE_HOME`
6. Confirm the app resurrects and the confirmation Snackbar reappears showing the **same curvature value** that was showing before the kill.
7. Tap "Save as default". Confirm it actually applied (reopen the app, curvature is still applied, matching what was shown pre-kill).
8. Repeat steps 1-6 once more, this time letting the resurrected Snackbar time out instead of tapping — confirm the grid reverts to the last real saved curvature, matching the non-killed dismiss/timeout behavior from Step 2.4.
9. Screenshot the resurrected Snackbar and capture logcat around the kill/resurrect window for the story file's evidence (matches the manual repro already done earlier this session).

- [ ] **Step 4: Write the story file**

Create `doc/task/done/p2-fish-fish-012-pinch-persist-across-kill.md` following the exact structure
of `doc/task/done/p2-fish-fish-011-polaroid-lens-export.md` (fields table, Context and evidence —
cite the original `am kill` repro from this session, Verification/Definition of Done with the
real test counts from Steps 1-3 above filled in, Device policy note, Audit score table scored
honestly against `doc/task/README.md`'s rubric). Reference the design spec
(`docs/superpowers/specs/2026-09-29-pinch-adjust-persist-across-kill-design.md`) and this plan
(`docs/superpowers/plans/2026-09-29-pinch-adjust-persist-across-kill.md`) in the Context section.
Also note `UI-023`/`FISH-013` as the two findings from the same audit deliberately deferred to
their own stories (Task 1 above), so a reviewer doesn't wonder why the audit surfaced more than
this story fixes.

- [ ] **Step 5: Update the backlog index**

In `doc/task/README.md`, add an entry to the `## ✅ Implemented` list (matching the format of the
existing `FISH-011`/`FISH-010` entries) summarizing `FISH-012`'s fix, real test counts, and the
Tecno smoke evidence from Step 3.

- [ ] **Step 6: Self-audit and push gate**

Score the round against `doc/task/README.md`'s rubric (Correctness 2.0 / Unit 1.5 / Widget 1.0 /
Integration 1.5 / Smoke 1.0 / Security 1.0 / Performance-lifecycle 1.0 / Maintainability 1.0).
Push only if the total is strictly greater than 9.0, matching this session's established push
gate (see `feedback_step_done_gate` memory).

```bash
git add doc/task/done/p2-fish-fish-012-pinch-persist-across-kill.md doc/task/README.md
git commit -m "docs(fish-012): close pinch-adjust persist-across-kill story"
git push origin dev
```
