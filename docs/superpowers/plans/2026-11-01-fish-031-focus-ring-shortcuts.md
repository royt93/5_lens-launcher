# FISH-031 Focus Ring Quick Shortcuts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Long-pressing an app icon inside the fisheye grid temporarily enlarges it by an extra discrete scale step and presents up to three of its published shortcuts arranged on a ring arc around it; sliding the finger onto a chip and releasing executes that shortcut, while releasing elsewhere falls back to the existing app menu.

**Architecture:** Pure layout helper `views/FocusRingLayout` computes arc angles, chip center points and boundary clamps; pure policy helper `views/FocusRingPolicy` gates visibility (clean mode, reduced-motion, shortcuts available), resolves the selected chip, and arbitrates release decisions against the existing drag-to-other-lens and popup-menu branches. `views/LensView` integrates the ring state into its existing long-press timer and touch stream, queries `search/AppShortcutsProvider.shortcutsFor` asynchronously, and draws the chips in the same frame as the held icon so that drawn bounds and hit-test bounds remain identical.

**Tech Stack:** Kotlin, Android `LauncherApps`/`ShortcutInfo`, `Paint`/`Canvas`, JUnit4, Robolectric, AndroidJUnitRunner.

## Global Constraints

- Device target: TECNO KJ7 `115333744A005844` only; Pixel and other devices banned by owner decision.
- No Ads/IAP/monetization work and no new TalkBack actions (A11Y-002/A11Y-003 excluded by owner).
- Existing single-lens and multi-lens behaviors are preserved: sliding off the chips past touch slop initiates a drag to another lens when 2+ lenses exist; releasing off-chip opens the existing app menu; apps without shortcuts, non-default launcher state, or Clean Mode fall back to the existing long-press behavior.
- Failed `startShortcut` calls show `R.string.error_app_not_found` via `Toast` (matches SEARCH-003); no new localized strings needed.
- Reduced-motion suppresses the extra scale step and shows chips immediately without transition.
- Gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`.
- Smoke on TECNO KJ7 with before/after app-data backup and member-by-member SHA-256 verification. Role-switching for the live shortcut smoke requires asking the owner first.
- Pre- and post-change frame metrics recorded via `dumpsys gfxinfo`.
- Local commits only; no push without explicit current authorization.

---

### Task 1: Pure ring geometry and hit-test (`views/FocusRingLayout`)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/views/FocusRingLayout.kt`
- Create: `app/src/test/java/com/mckimquyen/views/FocusRingLayoutTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class FocusChipSlot(val index: Int, val rect: RectF, val centerAngleRad: Float)
  object FocusRingLayout {
      const val EXTRA_MAGNIFICATION: Float = 1.25f
      fun computeSlots(
          iconCenterX: Float,
          iconCenterY: Float,
          iconRadius: Float,
          chipCount: Int,
          chipRadius: Float,
          ringGap: Float,
          viewportWidth: Float,
          viewportHeight: Float
      ): List<FocusChipSlot>
      fun chipAt(slots: List<FocusChipSlot>, touchX: Float, touchY: Float): Int
  }
  ```

- [ ] **Step 0: Start the story.** `git mv doc/task/todo/p3-fish-fish-031-focus-ring-quick-shortcuts.md doc/task/inprogress/` and update its `Status`, the `FISH-031` row in `doc/task/README.md` and `doc/feature.md` (exactly one `## 🟡 In progress` heading). Do not stage `.idea/`.
- [ ] **Step 1: Write the failing unit tests** in `FocusRingLayoutTest.kt`:
  - `computeSlots` with 0 chips returns an empty list.
  - With 1, 2, and 3 chips, slots have non-overlapping `rect` bounds centered at expected arc angles (distributed evenly above the icon between -150° and -30° when far from top edge).
  - When the icon is near the top screen boundary, the arc pivots downward so chips never cross `viewportTop`.
  - When the icon is near left/right screen edges, chips are clamped inside `[0, viewportWidth]`.
  - `chipAt` returns the exact slot index when `(touchX, touchY)` is inside a chip slot rect, and `-1` when outside all slots.
  - Every chip rect has finite, positive width and height equal to `2 * chipRadius`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests '*FocusRingLayoutTest'`
  Expected: FAIL with compilation error (unresolved `FocusRingLayout`).

- [ ] **Step 3: Implement minimal `FocusRingLayout`**
  ```kotlin
  package com.mckimquyen.views

  import android.graphics.RectF
  import kotlin.math.PI
  import kotlin.math.cos
  import kotlin.math.sin

  data class FocusChipSlot(val index: Int, val rect: RectF, val centerAngleRad: Float)

  object FocusRingLayout {
      const val EXTRA_MAGNIFICATION: Float = 1.25f

      fun computeSlots(
          iconCenterX: Float,
          iconCenterY: Float,
          iconRadius: Float,
          chipCount: Int,
          chipRadius: Float,
          ringGap: Float,
          viewportWidth: Float,
          viewportHeight: Float
      ): List<FocusChipSlot> {
          if (chipCount <= 0) return emptyList()
          val centerRadius = iconRadius + ringGap + chipRadius
          val baseAngles = when (chipCount) {
              1 -> listOf(-PI.toFloat() / 2f)
              2 -> listOf(-2f * PI.toFloat() / 3f, -PI.toFloat() / 3f)
              else -> listOf(-5f * PI.toFloat() / 6f, -PI.toFloat() / 2f, -PI.toFloat() / 6f)
          }
          val pivotDown = (iconCenterY - centerRadius - chipRadius) < 0f
          val angles = if (pivotDown) baseAngles.map { -it } else baseAngles
          return angles.mapIndexed { index, angle ->
              val rawX = iconCenterX + centerRadius * cos(angle)
              val rawY = iconCenterY + centerRadius * sin(angle)
              val clampedX = rawX.coerceIn(chipRadius, viewportWidth - chipRadius)
              val clampedY = rawY.coerceIn(chipRadius, viewportHeight - chipRadius)
              FocusChipSlot(
                  index = index,
                  rect = RectF(clampedX - chipRadius, clampedY - chipRadius, clampedX + chipRadius, clampedY + chipRadius),
                  centerAngleRad = angle
              )
          }
      }

      fun chipAt(slots: List<FocusChipSlot>, touchX: Float, touchY: Float): Int {
          for (slot in slots) {
              if (slot.rect.contains(touchX, touchY)) return slot.index
          }
          return -1
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests '*FocusRingLayoutTest'`
  Expected: PASS.

- [ ] **Step 5: Mutation check & commit**
  Mutate angle math, clamp bounds, and `chipAt` condition; assert tests catch every mutation. Then commit:
  ```bash
  git add app/src/main/java/com/mckimquyen/views/FocusRingLayout.kt app/src/test/java/com/mckimquyen/views/FocusRingLayoutTest.kt
  git commit -m "$(cat <<'EOF'
  feat(lens): pure focus ring layout and hit-test (FISH-031)

  Co-Authored-By: Claude Code <noreply@anthropic.com>
  EOF
  )"
  ```

---

### Task 2: Pure ring policy and release decisions (`views/FocusRingPolicy`)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/views/FocusRingPolicy.kt`
- Create: `app/src/test/java/com/mckimquyen/views/FocusRingPolicyTest.kt`

**Interfaces:**
- Consumes: `FocusChipSlot` from Task 1.
- Produces:
  ```kotlin
  sealed interface FocusRingReleaseAction {
      data class RunShortcut(val index: Int) : FocusRingReleaseAction
      data object OpenAppMenu : FocusRingReleaseAction
      data object None : FocusRingReleaseAction
  }
  object FocusRingPolicy {
      fun shouldShowRing(cleanMode: Boolean, shortcutCount: Int): Boolean
      fun shouldSuppressExtraScale(reduceMotion: Boolean): Boolean
      fun shouldAllowDragStart(hasOtherLenses: Boolean, activeChipIndex: Int, movedPastSlop: Boolean): Boolean
      fun resolveRelease(hasRingActive: Boolean, activeChipIndex: Int, dragStarted: Boolean): FocusRingReleaseAction
  }
  ```

- [ ] **Step 1: Write the failing unit tests** in `FocusRingPolicyTest.kt`:
  - `shouldShowRing` returns false when `cleanMode` is true (regardless of shortcut count).
  - `shouldShowRing` returns false when `shortcutCount == 0`.
  - `shouldShowRing` returns true only when Clean Mode is off and `shortcutCount > 0`.
  - `shouldSuppressExtraScale` returns true when `reduceMotion` is true.
  - `shouldAllowDragStart`: true only when `hasOtherLenses` is true, `activeChipIndex < 0`, and `movedPastSlop` is true (finger hovering over a chip never triggers drag).
  - `resolveRelease` with `activeChipIndex >= 0` returns `RunShortcut(activeChipIndex)`.
  - `resolveRelease` with `activeChipIndex < 0` and `dragStarted == false` returns `OpenAppMenu`.
  - `resolveRelease` when `dragStarted == true` returns `None`, even with an active chip.
  - `resolveRelease(hasRingActive = false, activeChipIndex = 3, dragStarted = false)` returns `OpenAppMenu` (a stale chip index is ignored when no ring is active).

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests '*FocusRingPolicyTest'`
  Expected: FAIL with compilation error (unresolved `FocusRingPolicy`).

- [ ] **Step 3: Implement minimal `FocusRingPolicy`**
  ```kotlin
  package com.mckimquyen.views

  sealed interface FocusRingReleaseAction {
      data class RunShortcut(val index: Int) : FocusRingReleaseAction
      data object OpenAppMenu : FocusRingReleaseAction
      data object None : FocusRingReleaseAction
  }

  object FocusRingPolicy {
      fun shouldShowRing(cleanMode: Boolean, shortcutCount: Int): Boolean =
          !cleanMode && shortcutCount > 0

      fun shouldSuppressExtraScale(reduceMotion: Boolean): Boolean = reduceMotion

      fun shouldAllowDragStart(hasOtherLenses: Boolean, activeChipIndex: Int, movedPastSlop: Boolean): Boolean =
          hasOtherLenses && activeChipIndex < 0 && movedPastSlop

      fun resolveRelease(hasRingActive: Boolean, activeChipIndex: Int, dragStarted: Boolean): FocusRingReleaseAction {
          if (dragStarted) return FocusRingReleaseAction.None
          if (hasRingActive && activeChipIndex >= 0) return FocusRingReleaseAction.RunShortcut(activeChipIndex)
          return FocusRingReleaseAction.OpenAppMenu
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests '*FocusRingPolicyTest'`
  Expected: PASS.

- [ ] **Step 5: Mutation check & commit**
  Mutate each policy boolean; assert tests catch every mutation. Commit:
  ```bash
  git add app/src/main/java/com/mckimquyen/views/FocusRingPolicy.kt app/src/test/java/com/mckimquyen/views/FocusRingPolicyTest.kt
  git commit -m "$(cat <<'EOF'
  feat(lens): focus ring display and release policy (FISH-031)

  Co-Authored-By: Claude Code <noreply@anthropic.com>
  EOF
  )"
  ```

---

### Task 3: Dimens, colors, and drawing assets

**Files:**
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`

**Interfaces:**
- Produces resource tokens used by `LensView` in Task 4:
  - `R.dimen.focus_ring_chip_radius` (18dp)
  - `R.dimen.focus_ring_gap` (12dp)
  - `R.dimen.focus_ring_chip_stroke` (2dp)
  - `R.color.focus_ring_chip_bg` (semi-transparent surface variant, `#E6F2F5F0`)
  - `R.color.focus_ring_chip_bg_active` (accent tint, `#FFD8E2D6`)

- [ ] **Step 1: Write verification test** in `app/src/test/java/com/mckimquyen/res/FocusRingResourcesContractTest.kt`:
  Assert the five resource IDs resolve in a robolectric context, have positive dimension values (`chipRadius > 0`, `chipStroke > 0`), and that the night-qualified context resolves a different `focus_ring_chip_bg` than the default one.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests '*FocusRingResourcesContractTest'`
  Expected: FAIL with compilation error on missing resource IDs.

- [ ] **Step 3: Add values to `dimens.xml` and `colors.xml`**
  ```xml
  <!-- values/dimens.xml -->
  <dimen name="focus_ring_chip_radius">18dp</dimen>
  <dimen name="focus_ring_gap">12dp</dimen>
  <dimen name="focus_ring_chip_stroke">2dp</dimen>

  <!-- values/colors.xml -->
  <color name="focus_ring_chip_bg">#E6F2F5F0</color>
  <color name="focus_ring_chip_bg_active">#FFD8E2D6</color>

  <!-- values-night/colors.xml: same names, dark surface tones so chips stay legible -->
  <color name="focus_ring_chip_bg">#E62B3330</color>
  <color name="focus_ring_chip_bg_active">#FF3F5A4D</color>
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests '*FocusRingResourcesContractTest'`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/res/values/dimens.xml app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml app/src/test/java/com/mckimquyen/res/FocusRingResourcesContractTest.kt
  git commit -m "$(cat <<'EOF'
  res: dimens and colors for focus ring chips (FISH-031)

  Co-Authored-By: Claude Code <noreply@anthropic.com>
  EOF
  )"
  ```

---

### Task 4: Integrate ring state and rendering into `LensView`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/views/LensViewFocusRingIntegrationTest.kt`

**Interfaces:**
- Consumes: `FocusRingLayout`, `FocusRingPolicy`, `FocusChipSlot`, and resources from Tasks 1-3.
- Injects test hooks:
  - `var shortcutProviderForTest: ((Context, String) -> List<QuickShortcut>)? = null`
  - `var shortcutLauncherForTest: ((Context, QuickShortcut, Rect?) -> Boolean)? = null`
  - `fun isFocusRingActiveForTest(): Boolean`
  - `fun activeFocusChipIndexForTest(): Int`
  - `fun focusRingSlotsForTest(): List<FocusChipSlot>`

- [ ] **Step 1: Write failing AndroidTest** in `LensViewFocusRingIntegrationTest.kt`:
  - When `mLongPressRunnable` triggers for an icon whose provider returns 2 shortcuts and Clean Mode is false:
    - `isFocusRingActiveForTest()` becomes true.
    - `focusRingSlotsForTest().size` equals 2.
    - Moving touch into slot 0 updates `activeFocusChipIndexForTest()` to 0.
    - Releasing at `activeFocusChipIndex == 0` invokes the launcher with shortcut 0 and resets ring state.
    - Releasing with touch outside any slot falls back to `showAppOptionsAtIndex`.
  - When Clean Mode is true: ring never activates; fallback menu opens as before.
  - When reduced-motion is enabled: ring activates without extra icon scale factor.
  - When the provider returns an empty list: ring never activates; fallback menu opens immediately on single-lens and on release on multi-lens.
  - During an active ring, touch moving onto a chip does NOT call `dragStarter`.

- [ ] **Step 2: Run test to verify it fails**
  Run on KJ7: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewFocusRingIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
  Expected: FAIL (methods not resolved).

- [ ] **Step 3: Implement in `LensView.kt`**
  - Single-lens case (spec "Trường hợp một lens"): in `mLongPressRunnable`, when `LensDragPolicy.shouldOpenMenuOnTimeout(mHasOtherLenses)` is true, do NOT call `showAppOptionsAtIndex` immediately; start the shortcut load first. The load callback opens the menu at once when there are no shortcuts, Clean Mode is on, or the touch has already ended; when a ring becomes active the menu opens on release off-chip instead. The gesture must still not wait more than one posted callback for apps without shortcuts.
  - Extra magnification (spec "phóng thêm một bậc"): while `mFocusRingActive` and `!FocusRingPolicy.shouldSuppressExtraScale(mGestureReduceMotion == true)`, draw the held icon rect scaled by `FocusRingLayout.EXTRA_MAGNIFICATION` about its centre; the scaled rect is the one passed to both `FocusRingLayout.computeSlots` (as `iconRadius = scaledWidth / 2`) and the drawn bitmap, so draw and hit-test cannot drift.
  - Add state variables:
    ```kotlin
    private var mFocusRingShortcuts: List<QuickShortcut> = emptyList()
    private var mFocusRingSlots: List<FocusChipSlot> = emptyList()
    private var mActiveFocusChipIndex: Int = -1
    private var mFocusRingActive = false
    // LensView has no CoroutineScope; it owns one single-thread executor and a generation counter so a stale load can never apply after release/cancel/detach.
    private val mFocusRingExecutor: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var mFocusRingGeneration = 0
    internal var shortcutProviderForTest: ((Context, String) -> List<QuickShortcut>)? = null
    internal var shortcutLauncherForTest: ((Context, QuickShortcut, Rect?) -> Boolean)? = null
    ```
  - In `mLongPressRunnable`:
    - Instead of calling `showAppOptionsAtIndex` immediately when `mHasOtherLenses == false`, query shortcuts off the main thread on `mFocusRingExecutor` (this runs for both single- and multi-lens, since the ring must appear in both). The `else if (!mHasOtherLenses)` fallback below keeps the old single-lens "open menu at timeout" outcome:
      ```kotlin
      val app = list.getOrNull(mSelectIndex)
      if (app != null) {
          mHoldIndex = mSelectIndex
          val pkg = app.packageName.toString()
          val generation = ++mFocusRingGeneration
          mFocusRingExecutor.execute {
              val shortcuts = shortcutProviderForTest?.invoke(context, pkg) ?: AppShortcutsProvider.shortcutsFor(context, pkg)
              post {
                  // Stale (released/cancelled/detached/new touch): the release path already decided; do nothing here.
                  if (generation != mFocusRingGeneration || !mLongPressTriggered || !isAttachedToWindow) return@post
                  val cleanMode = mUtilSettings?.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE) == true
                  if (FocusRingPolicy.shouldShowRing(cleanMode, shortcuts.size)) {
                      mFocusRingShortcuts = shortcuts
                      mFocusRingActive = true
                      computeRingSlots()
                      invalidate()
                  } else if (!mHasOtherLenses) {
                      showAppOptionsAtIndex(mHoldIndex)
                  }
              }
          }
      }
      ```
  - In `drawFocusHalo`:
    - If `mFocusRingActive` is true, enlarge icon bounds and draw chip slots via a helper `drawFocusRing(canvas)`.
  - In `MotionEvent.ACTION_MOVE`:
    - If `mFocusRingActive`, update `mActiveFocusChipIndex = FocusRingLayout.chipAt(mFocusRingSlots, event.x, event.y)`.
    - Gate `maybeStartDrag` using `FocusRingPolicy.shouldAllowDragStart`.
  - In `MotionEvent.ACTION_UP`:
    - Replace the existing `if (mLongPressTriggered) { ... }` release branch (the one that reads `heldIndex` and `LensDragPolicy.shouldOpenMenuOnRelease`) with a decision through `FocusRingPolicy.resolveRelease`, keeping its `mLongPressTriggered = false`, `mHoldIndex = -1`, `mDragStarted = false` resets and the `return true`. When no ring is active the result must still equal the old `openMenu` rule (hold on an icon, no drag, other lenses exist), so apps without shortcuts behave exactly as before:
      ```kotlin
      when (val action = FocusRingPolicy.resolveRelease(mFocusRingActive, mActiveFocusChipIndex, mDragStarted)) {
          is FocusRingReleaseAction.RunShortcut -> {
              val sc = mFocusRingShortcuts.getOrNull(action.index)
              val slot = mFocusRingSlots.getOrNull(action.index)
              if (sc != null && slot != null) {
                  val rect = Rect(slot.rect.left.toInt(), slot.rect.top.toInt(), slot.rect.right.toInt(), slot.rect.bottom.toInt())
                  val started = shortcutLauncherForTest?.invoke(context, sc, rect)
                      ?: AppShortcutsProvider.startShortcut(context, sc, rect)
                  if (!started) Toast.makeText(context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
              }
          }
          is FocusRingReleaseAction.OpenAppMenu -> {
              // Old rule preserved: with no ring, only a hold that never dragged and has other lenses opens the menu on release
              // (single lens already opened it at timeout). With a ring active, an off-chip release opens it on every lens count.
              val openMenu = heldIndex >= 0 &&
                  (mFocusRingActive || LensDragPolicy.shouldOpenMenuOnRelease(true, mDragStarted, mHasOtherLenses))
              if (openMenu) showAppOptionsAtIndex(heldIndex)
          }
          FocusRingReleaseAction.None -> Unit
      }
      resetFocusRingState()
      ```
  - `resetFocusRingState()` increments `mFocusRingGeneration` (invalidating any in-flight load), clears shortcuts, slots, `mActiveFocusChipIndex` and `mFocusRingActive`. Call it from `ACTION_CANCEL`, `ACTION_DOWN` of a new touch, lens change, and `onDetachedFromWindow`, where `mFocusRingExecutor.shutdownNow()` is also called and the executor is recreated in `onAttachedToWindow` if it was shut down (the view may be re-attached).

- [ ] **Step 4: Run integration test to verify it passes**
  Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewFocusRingIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
  Expected: PASS (all tests OK).

- [ ] **Step 5: Mutation check & commit**
  Mutate release routing (e.g. swap RunShortcut and OpenAppMenu branches, invert Clean Mode gate); assert tests catch each. Commit:
  ```bash
  git add app/src/main/java/com/mckimquyen/views/LensView.kt app/src/androidTest/java/com/mckimquyen/views/LensViewFocusRingIntegrationTest.kt
  git commit -m "$(cat <<'EOF'
  feat(lens): render and dispatch focus ring shortcuts in LensView (FISH-031)

  Co-Authored-By: Claude Code <noreply@anthropic.com>
  EOF
  )"
  ```

---

### Task 5: Frame rendering performance & non-regression verification

**Files:**
- Test: `app/src/androidTest/java/com/mckimquyen/views/FocusRingPerformanceSmokeTest.kt`

**Interfaces:**
- Measures frame times on TECNO KJ7 with `dumpsys gfxinfo com.mckimquyen.lenslauncher` before and after ring activation.

- [ ] **Step 1: Write instrumentation test** that dispatches 20 hold-then-slide-to-chip-then-release gestures on a real `LensView` (MotionEvents with `SystemClock.sleep` so frames are actually drawn), stubbing `shortcutProviderForTest` with two shortcuts and `shortcutLauncherForTest` to return true so no external app launches.
- [ ] **Step 2: Collect baseline metrics** on KJ7:
  `adb -s 115333744A005844 shell dumpsys gfxinfo com.mckimquyen.lenslauncher reset`
  Run test, then dump metrics:
  `adb -s 115333744A005844 shell dumpsys gfxinfo com.mckimquyen.lenslauncher`
  Record the same numbers on the pre-FISH-031 APK (baseline `git stash`-free: build from `HEAD~` commit) and on the new APK with an identical script; the change passes if jank percentage and the 90th/95th percentile frame times are not worse than baseline by more than 10% relative, and absolute 90th percentile stays under 16 ms. Report both sets of numbers in the audit instead of asserting a fixed threshold only.
- [ ] **Step 3: Run existing regression suite** on KJ7:
  - `com.mckimquyen.views.LensViewProfileIntegrationTest`
  - `com.mckimquyen.views.LensViewPinchIntegrationTest`
  - `com.mckimquyen.views.LensViewSmartFocusIntegrationTest`
  - `com.mckimquyen.views.LensViewFocusHaloLiveVisualSmokeTest`
  - `com.mckimquyen.ui.LensSettingsActivityWidgetTest`
  - `com.mckimquyen.ui.ActHomeLensManagementWidgetTest`
  - `com.mckimquyen.util.WorkspaceBackupImportIntegrationTest`
- [ ] **Step 4: Commit** performance test and report:
  ```bash
  git add app/src/androidTest/java/com/mckimquyen/views/FocusRingPerformanceSmokeTest.kt
  git commit -m "$(cat <<'EOF'
  test(lens): focus ring frame rendering benchmark (FISH-031)

  Co-Authored-By: Claude Code <noreply@anthropic.com>
  EOF
  )"
  ```

---

### Task 6: Full gate, live smoke on TECNO KJ7, audit and story closure

- [ ] **Step 1: Host full gate**
  `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`
  `git diff --check`
  `bash scripts/check-secrets.sh`
- [ ] **Step 2: Backup TECNO KJ7**
  Save app/test APK and full data tar (`/tmp/b-f031-kj7/before.tar`); record member hashes.
- [ ] **Step 3: Live device smoke**
  - Install freshly built APKs; verify SHA matches build output.
  - Ask owner via `AskUserQuestion` before temporarily setting the app as default launcher (required for `LauncherApps.getShortcuts`).
  - Open ActHome; long-press an app known to publish dynamic/manifest shortcuts (e.g. YouTube, Messages, Chrome).
  - Verify visually: extra magnification step engages, up to 3 chip icons appear on the arc above the icon, dragging to a chip highlights it, releasing launches the shortcut target.
  - Long-press an app with no shortcuts: verify fallback to the standard app options popup.
  - Long-press with Clean Mode on: verify ring is suppressed.
  - Capture screenshots; **if any ad appears, stop immediately per R4 and wait for owner `done`**.
  - Revert default launcher role if changed.
- [ ] **Step 4: Restore device data**
  Reinstall baseline APKs and restore app data tar; compare all member hashes (must match exactly).
- [ ] **Step 5: Write audit, update backlog and feature tracker**
  - Write `doc/task/AUDIT_FISH-031_<date>.md` with frame metrics, smoke screenshots, device version, APK hashes, mutation record, and an honest score.
  - Move the story from `inprogress/` to `done/` only if the audit score exceeds 9.0/10 and the smoke evidence exists; otherwise leave it in `inprogress/` and say so in the audit.
  - Update `doc/task/README.md` and `doc/feature.md`.
  - No push without explicit owner request.

---

## Self-Review Checklist

1. **Spec coverage:**
   - Extra discrete scale step: Task 1 (`EXTRA_MAGNIFICATION = 1.25f`), Task 4 (`drawFocusRing`).
   - Up to 3 chips on arc around icon: Task 1 (`FocusRingLayout.computeSlots`), Task 3 (dimens/colors).
   - Sliding onto chip highlights, release launches shortcut: Task 2 (`resolveRelease`), Task 4 (`ACTION_MOVE`/`ACTION_UP`).
   - Fallback to app menu on off-chip release or missing shortcuts: Task 2 (`OpenAppMenu`), Task 4.
   - Reduced-motion gate: Task 2 (`shouldSuppressExtraScale`), Task 4.
   - Clean Mode gate: Task 2 (`shouldShowRing`), Task 4.
   - No TalkBack work / no Ads / TECNO KJ7 only: specified in Global Constraints.
   - Ask owner before changing launcher role: Task 6, Step 3.
   - `dumpsys gfxinfo` frame measurement: Task 5, Step 2.
2. **Placeholder scan:** None found. No "TBD", "TODO", or missing blocks.
3. **Type consistency:** `FocusChipSlot`, `FocusRingReleaseAction`, `FocusRingLayout`, `FocusRingPolicy` signatures match across all tasks.
