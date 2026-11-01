# FISH-037 Polaroid Optical Caption and Contact Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The shared Polaroid image states the lens's optical settings (profile, distortion, scale, hood), and users with two or more lenses can pick up to six lenses and share them as one contact sheet.

**Architecture:** Two pure helpers (`PolaroidCaptionFormatter`, `PolaroidContactSheetLayout`) hold all string and geometry decisions. `PolaroidExportHelper` keeps its single-lens API and gains a contact-sheet path that captures lenses one at a time and composites them into one bounded bitmap. `LensMenuCoordinator.exportActiveLensImage()` decides between the single and multi-lens path and shows a lens picker built on `LensDialogs.multiChoice`, extended with an optional selection cap.

**Tech Stack:** Kotlin, Java, Android `Bitmap`/`Canvas`, `FileProvider`, Material dialogs, JUnit4 + Robolectric, AndroidJUnitRunner.

## Global Constraints

- No Ads/IAP/monetization and no TalkBack work.
- Device policy: TECNO only. KJ7 `115333744A005844` preferred, BG6 `118743744X002560` approved fallback; never Pixel. OPPO `FUJZIFIR7DQCNRWW` still holds a FISH-031 dev APK; do not touch it, and ask the owner before restoring it.
- Caption line 2 is `<Profile> · d=<x.x> · s=<x.x> · hood=<n>%`; the old "via <app>" line moves to a smaller line 3. Profile names reuse the existing `lens_profile_*` strings.
- Single lens: export goes straight through as today. Two or more lenses: a picker opens, default ticked lens is the one being viewed, maximum **6** lenses, confirm disabled when none is ticked.
- Contact sheet: 2-column grid, order follows the pager, one miniature polaroid per cell with its own name and optical caption, "via <app>" printed once at the foot of the sheet.
- Limits: longest side of the final bitmap `<= 4096` px and `width * height * 4 <= 64 MB`. Cells are recycled right after being composited.
- Failures return `null` to the callback and show the existing `error_lens_share_failed` toast. Cancelling the picker clears the durable `pending_auto_export_lens` flag (FISH-013 semantics: cleared only when the request reaches a terminal outcome).
- Resource files are `values/dimen.xml` (not `dimens.xml`) and `values/colors.xml`. There are 17 locale `strings.xml` files (`values` plus 16 translated); the existing translation tests force real translations, so no English text may be copied into a locale file.
- No new dependency, permission, preference key, Room change or `file_paths.xml` change (still `cache/polaroid/`).
- Gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`.
- Mutation checks must be re-run with `--rerun-tasks` or on a freshly built APK; a result that comes from an UP-TO-DATE Gradle task is not a verdict.
- Local commits only; no push without explicit current authorization. Never stage `.idea/`.

## File Map

- Create `app/src/main/java/com/mckimquyen/util/PolaroidCaptionFormatter.kt` and test `app/src/test/java/com/mckimquyen/util/PolaroidCaptionFormatterTest.kt`.
- Create `app/src/main/java/com/mckimquyen/util/PolaroidContactSheetLayout.kt` and test `app/src/test/java/com/mckimquyen/util/PolaroidContactSheetLayoutTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/util/PolaroidExportHelper.kt` and `app/src/test/java/com/mckimquyen/util/PolaroidExportHelperTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/ui/dialog/LensDialogs.kt` (`multiChoice` cap) and add `app/src/androidTest/java/com/mckimquyen/ui/dialog/LensDialogsMultiChoiceCapWidgetTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/ui/home/LensMenuCoordinator.java` and `app/src/main/java/com/mckimquyen/ui/ActHome.java` (shared scope filter).
- Modify `app/src/main/res/values*/strings.xml` (17 files).
- Test `app/src/androidTest/java/com/mckimquyen/util/PolaroidContactSheetIntegrationTest.kt` and `app/src/androidTest/java/com/mckimquyen/ui/home/LensShareLensPickerWidgetTest.kt`.
- Docs: move `doc/task/todo/p3-fish-fish-037-polaroid-optical-caption.md` to `inprogress/`, update `doc/task/README.md`, `doc/feature.md`, add `doc/task/AUDIT_FISH-037_<date>.md`.

## Task 1: Optical caption formatter (pure)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/PolaroidCaptionFormatter.kt`
- Test: `app/src/test/java/com/mckimquyen/util/PolaroidCaptionFormatterTest.kt`

**Interfaces:**
- Produces: `object PolaroidCaptionFormatter { fun opticalLine(profileName: String, distortion: Float, scale: Float, hood: Float, locale: Locale): String }`

- [ ] **Step 0: Start the story.** `git mv doc/task/todo/p3-fish-fish-037-polaroid-optical-caption.md doc/task/inprogress/`, set its `Status`, update the `FISH-037` row in `doc/task/README.md` and add one line to `doc/feature.md` (keep exactly one `## 🟡 In progress` heading if the file uses that heading; check with `rg -n '^## 🟡' doc/feature.md`). Do not stage `.idea/`.
- [ ] **Step 1: Write the failing test**

```kotlin
package com.mckimquyen.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PolaroidCaptionFormatterTest {
    @Test fun formatsProfileAndThreeSettings() {
        assertEquals("Fisheye · d=2.5 · s=1.0 · hood=100%",
            PolaroidCaptionFormatter.opticalLine("Fisheye", 2.5f, 1.0f, 1.0f, Locale.US))
    }

    @Test fun roundsDistortionAndScaleToOneDecimalAndHoodToWholePercent() {
        assertEquals("Pincushion · d=2.7 · s=1.5 · hood=35%",
            PolaroidCaptionFormatter.opticalLine("Pincushion", 2.66f, 1.54f, 0.349f, Locale.US))
    }

    @Test fun usesTheLocalesDecimalSeparator() {
        assertEquals("Mắt cá · d=2,5 · s=1,0 · hood=100%",
            PolaroidCaptionFormatter.opticalLine("Mắt cá", 2.5f, 1.0f, 1.0f, Locale.forLanguageTag("vi-VN")))
    }

    @Test fun nonFiniteNumbersShowADashNotNaN() {
        val line = PolaroidCaptionFormatter.opticalLine("Fisheye", Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Locale.US)
        assertEquals("Fisheye · d=– · s=– · hood=–", line)
    }

    @Test fun blankProfileFallsBackToDashes() {
        assertEquals("– · d=2.5 · s=1.0 · hood=100%",
            PolaroidCaptionFormatter.opticalLine("  ", 2.5f, 1.0f, 1.0f, Locale.US))
    }
}
```

- [ ] **Step 2: Run RED.** `./gradlew testDevDebugUnitTest --tests '*PolaroidCaptionFormatterTest'` → compile failure, unresolved `PolaroidCaptionFormatter`.
- [ ] **Step 3: Implement**

```kotlin
package com.mckimquyen.util

import java.util.Locale

/** FISH-037: the optical-settings line printed under a lens name. Pure, no Context or resources. */
object PolaroidCaptionFormatter {
    private const val DASH = "–"
    private const val SEPARATOR = " · "
    private const val PERCENT_SCALE = 100f

    @JvmStatic
    fun opticalLine(profileName: String, distortion: Float, scale: Float, hood: Float, locale: Locale): String {
        val profile = profileName.trim().ifEmpty { DASH }
        val d = oneDecimal(distortion, locale)
        val s = oneDecimal(scale, locale)
        val h = if (hood.isFinite()) "${Math.round(hood * PERCENT_SCALE)}%" else DASH
        return listOf(profile, "d=$d", "s=$s", "hood=$h").joinToString(SEPARATOR)
    }

    private fun oneDecimal(value: Float, locale: Locale): String =
        if (value.isFinite()) String.format(locale, "%.1f", value) else DASH
}
```

- [ ] **Step 4: Run GREEN**, same command. Expected: 5 tests pass.
- [ ] **Step 5: Mutation check with `--rerun-tasks`.** Mutate one at a time and require a failing test each: drop `locale` (use `Locale.US`), `%.1f` → `%.2f`, `* PERCENT_SCALE` → `* 10f`, `isFinite()` → `true`, separator ` · ` → ` - `. Restore the file after each.
- [ ] **Step 6: Commit** the formatter, its test, and the story move/tracker edits from Step 0.

## Task 2: Contact sheet geometry with size and memory limits (pure)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/PolaroidContactSheetLayout.kt`
- Test: `app/src/test/java/com/mckimquyen/util/PolaroidContactSheetLayoutTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class SheetCell(val index: Int, val left: Int, val top: Int, val width: Int, val height: Int)
  data class SheetLayout(val width: Int, val height: Int, val columns: Int, val rows: Int, val cells: List<SheetCell>)
  object PolaroidContactSheetLayout {
      const val COLUMNS = 2
      const val MAX_LENSES = 6
      const val MAX_SIDE_PX = 4096
      const val MAX_BITMAP_BYTES = 64L * 1024 * 1024
      const val GAP_PX = 24
      const val FOOTER_PX = 96
      fun compute(count: Int, pageWidth: Int, pageHeight: Int): SheetLayout
  }
  ```
  `pageWidth/pageHeight` are the size of one captured lens page; each cell keeps that aspect ratio and includes room for a miniature polaroid frame and caption band (use `PolaroidExportHelper.calculatePolaroidLayout` on the scaled content size).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolaroidContactSheetLayoutTest {
    private fun sheet(count: Int, w: Int = 1080, h: Int = 1944) = PolaroidContactSheetLayout.compute(count, w, h)

    @Test fun twoColumnsAndCeilRows() {
        for ((n, rows) in listOf(1 to 1, 2 to 1, 3 to 2, 4 to 2, 5 to 3, 6 to 3)) {
            val s = sheet(n)
            assertEquals("columns for $n", minOf(n, 2), s.columns)
            assertEquals("rows for $n", rows, s.rows)
            assertEquals("cells for $n", n, s.cells.size)
        }
    }

    @Test fun longestSideNeverExceedsTheCap() {
        for (n in 1..6) {
            val s = sheet(n)
            assertTrue("n=$n ${s.width}x${s.height}", maxOf(s.width, s.height) <= PolaroidContactSheetLayout.MAX_SIDE_PX)
        }
    }

    @Test fun bitmapBytesNeverExceedTheMemoryCap() {
        for (n in 1..6) for ((w, h) in listOf(1080 to 1944, 3000 to 3000, 600 to 4000, 4000 to 600)) {
            val s = sheet(n, w, h)
            assertTrue("n=$n ${w}x$h", s.width.toLong() * s.height * 4 <= PolaroidContactSheetLayout.MAX_BITMAP_BYTES)
        }
    }

    @Test fun cellsStayInsideTheSheetAndDoNotOverlap() {
        val s = sheet(6)
        for (c in s.cells) {
            assertTrue(c.left >= 0 && c.top >= 0)
            assertTrue(c.left + c.width <= s.width)
            assertTrue(c.top + c.height <= s.height - PolaroidContactSheetLayout.FOOTER_PX)
        }
        for (i in s.cells.indices) for (j in i + 1 until s.cells.size) {
            val a = s.cells[i]; val b = s.cells[j]
            val apart = a.left + a.width <= b.left || b.left + b.width <= a.left ||
                a.top + a.height <= b.top || b.top + b.height <= a.top
            assertTrue("cells $i and $j overlap", apart)
        }
    }

    @Test fun cellsKeepThePageAspectRatioWithinRounding() {
        val s = sheet(4, 1080, 1944)
        for (c in s.cells) assertEquals(1944f / 1080f, c.height.toFloat() / c.width, 0.2f)
    }

    @Test fun degenerateInputIsClampedNotCrashing() {
        val s = PolaroidContactSheetLayout.compute(0, 0, 0)
        assertTrue(s.width >= 1 && s.height >= 1)
        assertEquals(0, s.cells.size)
        assertEquals(PolaroidContactSheetLayout.MAX_LENSES, PolaroidContactSheetLayout.compute(99, 1080, 1944).cells.size)
    }
}
```

- [ ] **Step 2: Run RED.** `./gradlew testDevDebugUnitTest --tests '*PolaroidContactSheetLayoutTest'` → unresolved reference.
- [ ] **Step 3: Implement** with this exact algorithm so the tests and the helper agree:

```kotlin
package com.mckimquyen.util

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

data class SheetCell(val index: Int, val left: Int, val top: Int, val width: Int, val height: Int)
data class SheetLayout(val width: Int, val height: Int, val columns: Int, val rows: Int, val cells: List<SheetCell>)

/** FISH-037: pure geometry of the multi-lens contact sheet, including the size and memory limits. */
object PolaroidContactSheetLayout {
    const val COLUMNS = 2
    const val MAX_LENSES = 6
    const val MAX_SIDE_PX = 4096
    const val MAX_BITMAP_BYTES = 64L * 1024 * 1024
    const val GAP_PX = 24
    const val FOOTER_PX = 96
    private const val BYTES_PER_PIXEL = 4
    private const val MIN_CELL_PX = 1

    fun compute(count: Int, pageWidth: Int, pageHeight: Int): SheetLayout {
        val n = count.coerceIn(0, MAX_LENSES)
        val pw = pageWidth.coerceAtLeast(1)
        val ph = pageHeight.coerceAtLeast(1)
        if (n == 0) return SheetLayout(MIN_CELL_PX, MIN_CELL_PX, 1, 0, emptyList())
        val columns = min(n, COLUMNS)
        val rows = ceil(n / COLUMNS.toDouble()).toInt()
        // Natural size of the sheet if every cell kept the full page size.
        val naturalW = columns * pw + (columns + 1) * GAP_PX
        val naturalH = rows * ph + (rows + 1) * GAP_PX + FOOTER_PX
        val bySide = min(1.0, MAX_SIDE_PX.toDouble() / maxOf(naturalW, naturalH))
        val byBytes = min(1.0, sqrt(MAX_BITMAP_BYTES.toDouble() / BYTES_PER_PIXEL / (naturalW.toDouble() * naturalH)))
        val scale = min(bySide, byBytes)
        val cellW = floor(pw * scale).toInt().coerceAtLeast(MIN_CELL_PX)
        val cellH = floor(ph * scale).toInt().coerceAtLeast(MIN_CELL_PX)
        val width = columns * cellW + (columns + 1) * GAP_PX
        val height = rows * cellH + (rows + 1) * GAP_PX + FOOTER_PX
        val cells = (0 until n).map { i ->
            SheetCell(i, GAP_PX + (i % columns) * (cellW + GAP_PX), GAP_PX + (i / columns) * (cellH + GAP_PX), cellW, cellH)
        }
        return SheetLayout(width, height, columns, rows, cells)
    }
}
```

   The fixed gap and footer are not scaled, so after flooring `width`/`height` can exceed the cap by a few pixels for extreme inputs; if a test fails for that reason, subtract the gap and footer from the budget before computing `scale` (`bySide = (MAX_SIDE_PX - fixed) / natural cell extent`) rather than loosening the test.
- [ ] **Step 4: Run GREEN.**
- [ ] **Step 5: Mutation check (`--rerun-tasks`).** `COLUMNS` 2 → 3, `ceil` → `floor`, drop `bySide`, drop `byBytes`, `MAX_LENSES` clamp removed, cell `left` ignoring `GAP_PX`. Each must fail a named test.
- [ ] **Step 6: Commit.**

## Task 3: Caption line in the single-lens export (`PolaroidExportHelper`)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/PolaroidExportHelper.kt`
- Test: `app/src/test/java/com/mckimquyen/util/PolaroidExportHelperTest.kt`

**Interfaces:**
- Consumes: `PolaroidCaptionFormatter.opticalLine`.
- Produces: `data class CaptionLines(val line1: String, val line2: String, val line3: String = "")` (keeps `line1`/`line2` source-compatible); `fun buildCaptionLines(lensName: String, fallbackName: String, optical: String, via: String): CaptionLines`; `exportAsync(lensView, lensName, optical: String, context, onDone)` with the existing 4-argument overload kept and delegating with an empty `optical`.

- [ ] **Step 1: Write the failing tests** in `PolaroidExportHelperTest.kt`:

```kotlin
@Test fun captionPutsOpticalOnLineTwoAndViaOnLineThree() {
    val c = PolaroidExportHelper.buildCaptionLines("Work", "Fisheye Launcher", "Fisheye · d=2.5 · s=1.0 · hood=100%", "via Fisheye Launcher")
    assertEquals("Work", c.line1)
    assertEquals("Fisheye · d=2.5 · s=1.0 · hood=100%", c.line2)
    assertEquals("via Fisheye Launcher", c.line3)
}

@Test fun withoutOpticalTextTheOldTwoLineCaptionIsKept() {
    val c = PolaroidExportHelper.buildCaptionLines("Work", "Fisheye Launcher", "", "via Fisheye Launcher")
    assertEquals("via Fisheye Launcher", c.line2)
    assertEquals("", c.line3)
}

@Test fun theLayoutLeavesRoomForThreeCaptionLines() {
    val three = PolaroidExportHelper.calculatePolaroidLayout(1080, 1944, captionLines = 3)
    val two = PolaroidExportHelper.calculatePolaroidLayout(1080, 1944)
    assertTrue(three.outerHeight > two.outerHeight)
    assertTrue(three.captionLine3BaselineY > three.captionLine2BaselineY)
    assertEquals(two.outerWidth, three.outerWidth)
}
```

- [ ] **Step 2: Run RED** (`--tests '*PolaroidExportHelperTest'`): unresolved `buildCaptionLines`, extra parameter.
- [ ] **Step 3: Implement.** Add `buildCaptionLines` (line1 = existing `buildCaptionLine1`; when `optical` is blank return the old 2-line caption, otherwise line2 = optical, line3 = via). Add `captionLines: Int = 2` to `calculatePolaroidLayout` (default keeps the old height byte-for-byte, so the FISH-011 tests stay green) with a new `CAPTION_LINE3_TEXT_SIZE_PX = 28f` and extra caption height `CAPTION_LINE3_EXTRA_PX` named constants, and add `captionLine3BaselineY` to `PolaroidLayout` (value `0` when two lines). `renderFrame` draws line 3 only when non-empty. `exportAsync(lensView, lensName, optical, context, onDone)` builds the caption with `appContext.getString(R.string.lens_share_caption_via, appName)` as `via`; the old signature calls it with `optical = ""`.
- [ ] **Step 4: Run GREEN**, plus the full `PolaroidExportHelperTest` (the FISH-011 cases must still pass unchanged).
- [ ] **Step 5: Mutation check (`--rerun-tasks`)** on the blank-optical branch, line order, and the `captionLines` height math.
- [ ] **Step 6: Commit.**

## Task 4: `LensDialogs.multiChoice` selection cap

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/dialog/LensDialogs.kt:121-152`
- Test: `app/src/androidTest/java/com/mckimquyen/ui/dialog/LensDialogsMultiChoiceCapWidgetTest.kt`

**Interfaces:**
- Produces: `multiChoice(context, titleRes, adapter, initiallyChecked, positiveRes, onConfirm, onDismiss, maxSelected: Int = Int.MAX_VALUE, requireSelection: Boolean = false, @StringRes limitMessageRes: Int = 0)`. The existing 7-argument callers (`LensMenuCoordinator.java:536,573`, `LensSettingsActivity.kt:224`) must keep compiling and behaving identically; Java callers use `@JvmOverloads` so the added parameters are optional there too. Add `@JvmOverloads` to the function.

- [ ] **Step 1: Write the failing widget test** (hosted in `OrganizationHarnessActivity`, like `LensDialogsWidgetTest` hosts in a real context): with 7 items and `maxSelected = 6`, ticking a 7th row leaves it unchecked and the `Toast`/callback records the limit; with `requireSelection = true` the positive button is disabled when nothing is ticked and enabled after one tick; with defaults the dialog behaves as before (ticking all 7 works, positive enabled with none ticked, `onConfirm` gets exactly the ticked set). Use `ListView` row clicks on the dialog's list and read `dialog.getButton(BUTTON_POSITIVE).isEnabled`.
- [ ] **Step 2: Run RED** on the device: `adb -s <locked TECNO> shell am instrument -w -e class com.mckimquyen.ui.dialog.LensDialogsMultiChoiceCapWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` → compile error on the new parameters.
- [ ] **Step 3: Implement.** In the item click listener, when the row becomes checked and `selected.size >= maxSelected`, call `list.setItemChecked(position, false)`, show `Toast.makeText(context, limitMessageRes, SHORT)` only if `limitMessageRes != 0`, and return; otherwise update `selected` as before. After every click and once in `setOnShowListener`, set `positive.isEnabled = !requireSelection || selected.isNotEmpty()`. `initiallyChecked` larger than `maxSelected` is truncated to the first `maxSelected` indexes so the invariant holds from the start.
- [ ] **Step 4: Run GREEN**, then the existing `LensDialogsWidgetTest` and `ActHomeLensManagementWidgetTest` on the device as regression for the two existing callers.
- [ ] **Step 5: Mutation check on a fresh build** (cap comparison `>=` → `>`, drop `requireSelection`, drop truncation). Each must fail a test.
- [ ] **Step 6: Commit.**

## Task 5: Shared lens-scope filter

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java:900-905` (`scopedApps`) and `app/src/main/java/com/mckimquyen/util/LensAppScope.kt`
- Test: `app/src/test/java/com/mckimquyen/util/LensAppScopeTest.kt` (the file exists; extend it)

**Interfaces:**
- Produces: `LensAppScope.forLens(apps: List<App>, settings: UtilSettings, lensId: String): ArrayList<App>` equal to `filter(apps, settings.getLensAppScope(lensId), settings.getLensAppSelection(lensId), settings.getLensHiddenAppIds(lensId))`. `ActHome.scopedApps` calls it so both places cannot drift.

- [ ] **Step 1: Write the failing test** with a Robolectric `UtilSettings`: scope ALL with a hidden id excludes it; scope SELECTED with a selection keeps only that id minus hidden; unknown lens id behaves like the default (all apps).
- [ ] **Step 2: RED**, **Step 3: implement `forLens` and replace the body of `ActHome.scopedApps` with `return LensAppScope.forLens(listApp, utilSettings, lensId)` keeping the existing null guard**, **Step 4: GREEN** plus `ActHomeMultiLensWidgetTest` on the device, **Step 5: commit.**

## Task 6: Picker strings in 17 locales

**Files:**
- Modify: `app/src/main/res/values/strings.xml` and the 16 `values-<locale>/strings.xml` files
- Test: existing `LensStringTranslationTest`, `AllStringsTranslationTest`

**Interfaces:**
- Produces: `R.string.lens_share_pick_title` (picker title), `R.string.lens_share_limit_message` with a `%1$d` placeholder (maximum number of lenses).

- [ ] **Step 1: Write the failing check.** Extend `LensStringTranslationTest` expectations by adding the two keys to its list of lens strings (follow how `lens_hood_title` was added) so the test fails while any locale lacks them.
- [ ] **Step 2: RED**: `./gradlew testDevDebugUnitTest --tests '*LensStringTranslationTest' --tests '*AllStringsTranslationTest'`.
- [ ] **Step 3: Add real translations** to all 17 files (a script that inserts after `lens_share_caption_via` and refuses to run if a key already exists). `lens_share_limit_message` keeps `%1$d` in every locale; because a format-only string can equal the English text it must be added to the `allowedToMatch` set in `LensStringTranslationTest` only if a locale's correct translation is identical (do not add it speculatively).
- [ ] **Step 4: GREEN.**
- [ ] **Step 5: Commit.**

## Task 7: Contact-sheet export (`PolaroidExportHelper.exportContactSheetAsync`)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/PolaroidExportHelper.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/util/PolaroidContactSheetIntegrationTest.kt`

**Interfaces:**
- Consumes: `PolaroidContactSheetLayout.compute`, `PolaroidCaptionFormatter.opticalLine`, `LensAppScope.forLens`.
- Produces:
  ```kotlin
  data class LensExportSpec(val lensId: String, val lensName: String, val optical: String)
  fun exportContactSheetAsync(
      context: Context, host: View, specs: List<LensExportSpec>, liveView: LensView?, liveLensId: String?,
      appsForLens: (String) -> ArrayList<App>, onDone: (Uri?) -> Unit
  )
  ```
  Runs on the main thread: for each spec in order, obtains a page bitmap (`liveView` for `liveLensId`, otherwise a detached temporary `LensView` built as in the spec), scales it to its `SheetCell`, draws it with its caption into the sheet bitmap, recycles the page and cell bitmaps immediately, then compresses and writes `cache/polaroid/lenses.png` on `Dispatchers.IO`. Any `OutOfMemoryError` or exception recycles everything created so far and calls `onDone(null)`.

- [ ] **Step 1: Write the failing integration tests** (device, hosted in `OrganizationHarnessActivity`): a sheet for 3 specs returns a PNG whose decoded size equals `PolaroidContactSheetLayout.compute(3, w, h)`; the pixel at the top-left gap is white; the pixel in the empty fourth cell area (3 lenses means the right-hand cell of row 2 is empty) is white; each filled cell contains non-white pixels; a spec with an empty app list still produces a filled frame without crashing; an injected failure (a provider that throws) returns `null` and leaves no half-written file; a request for 9 specs yields exactly 6 cells.
- [ ] **Step 2: RED** (unresolved `exportContactSheetAsync`).
- [ ] **Step 3: Implement.** The temporary view is built with `LensView(context)`, then **`lensId` is set before `setApps`** (the `lensId` setter re-runs the Smart Focus arrangement, so setting it after `setApps` would arrange the list twice), `setApps(appsForLens(id))`, `setHasOtherLenses(false)`, `measure/layout` to the live page size (`liveView.width/height`, else the host size), `resetToIdleForExport()`, then `draw(Canvas(bitmap))`; it is never added to a window and is released by letting it go out of scope after draw (call `onDetachedFromWindow()` is NOT needed because it was never attached). Reuse `renderFrame` for each cell by calling it with the scaled content and a 2-line caption. Draw the "via <app>" line once centered inside `FOOTER_PX`. Keep every constant named; no magic numbers.
- [ ] **Step 4: GREEN** on the device.
- [ ] **Step 5: Mutation check on a fresh build**: skip recycle, ignore the 6 cap, draw all cells at origin, return a uri on failure. Each must fail a test.
- [ ] **Step 6: Commit.**

## Task 8: Picker flow in `LensMenuCoordinator`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/home/LensMenuCoordinator.java` (`exportActiveLensImage`, around line 395-447)
- Test: `app/src/androidTest/java/com/mckimquyen/ui/home/LensShareLensPickerWidgetTest.kt`

**Interfaces:**
- Consumes: `LensDialogs.multiChoice(..., maxSelected = PolaroidContactSheetLayout.MAX_LENSES, requireSelection = true, limitMessageRes = R.string.lens_share_limit_message)`, `PolaroidExportHelper.exportAsync(view, name, optical, activity, cb)`, `exportContactSheetAsync`, `host.lenses().currentLenses()`, `host.settings()`, `host.apps()`, `host.activeLensView()`.
- Behaviour: one lens → export directly (caption now includes optical text built from `settings.getLensProfile/getDistortionFactor/getFloat(KEY_SCALE_FACTOR)/getLensHood` and `activity.getString(R.string.lens_profile_<name>)`); two or more → picker with the active lens ticked; confirm with one lens → single export of that lens (use the live view only if it is the viewed lens, otherwise the contact-sheet path with one spec so a non-viewed lens is still rendered); two or more → contact sheet. Cancel and dismiss call the existing terminal-outcome path that clears `pending_auto_export_lens`.

- [ ] **Step 1: Write the failing widget tests** against a real `ActHome` with two lenses (follow `ActHomeMultiLensWidgetTest` for setup): one lens → no dialog and a share chooser is launched (use the existing `ActHome.lensShareLauncher` test seam (`interface LensShareLauncher { void launch(Intent chooserIntent); }`, `ActHome.java:223-232`) to capture the chooser Intent instead of opening a real share sheet); two lenses → a dialog with the viewed lens ticked and the confirm button enabled, the sixth-plus tick is refused, unticking everything disables confirm; confirming two lenses launches a chooser whose stream is `lenses.png`; cancelling clears the pending flag and launches nothing; the failure path shows `error_lens_share_failed` once.
- [ ] **Step 2: RED**, **Step 3: implement** (extract a private `buildOpticalCaption(UtilSettings, String lensId, Activity)` that maps `LensProfile` to its string and calls `PolaroidCaptionFormatter.opticalLine` with `Locale.getDefault()`), **Step 4: GREEN**, run `ActHomeLensManagementWidgetTest`, `ActHomeAutoExportWidgetTest` and `ActHomeAutoExportPersistenceIntegrationTest` (the FISH-013 pending-flag tests) as regression.
- [ ] **Step 5: Mutation check on a fresh build**: always open the picker, never clear the pending flag on cancel, tick none by default, swap single/multi branch. Each must fail a test.
- [ ] **Step 6: Commit.**

## Task 9: Full gate, device smoke, audit, closure

- [ ] **Step 1: Host gate** (Global Constraints), record unit/fail/skip counts and the APK sha256.
- [ ] **Step 2: Lock the device.** Run `adb devices -l`; use KJ7 if present, else BG6; never Pixel or OPPO. Back up the app data (`run-as tar`, drop the `./lib` symlink from the restore archive), the app and test APKs, locale, font and foreground window, and hash every member.
- [ ] **Step 3: Regression on the device**, one class per call with a hard timeout and `-r` output parsed per test: `PolaroidContactSheetIntegrationTest`, `LensShareLensPickerWidgetTest`, `LensDialogsMultiChoiceCapWidgetTest`, `LensDialogsWidgetTest`, `ActHomeLensManagementWidgetTest`, `ActHomeMultiLensWidgetTest`, `ActHomeAutoExportWidgetTest`, `ActHomeAutoExportPersistenceIntegrationTest`, `FrmLensPerLensWidgetTest`, `LensViewProfileIntegrationTest`.
- [ ] **Step 4: Real smoke.** Install the built APK, confirm its sha256 equals the built one, ask the owner via `AskUserQuestion` before changing the default launcher role (needed only if a shortcut-dependent step is used; the share flow itself does not need it). Share one lens and two lenses from the menu; pull the produced PNGs from `cache/polaroid/` with `run-as`, open them, and record size, bytes and elapsed time. Stop immediately and wait for the owner if an ad or consent screen covers a screenshot; never accept consent for the owner.
- [ ] **Step 5: Restore** the device exactly (APKs, data, locale, font, role), compare every member hash, and record the result.
- [ ] **Step 6: Audit** `doc/task/AUDIT_FISH-037_<date>.md` with evidence, mutation results (each a real verdict), the honest score and every limitation; update the story, `doc/task/README.md`, `doc/feature.md`. Move the story to `done/` only if the score is above 9.0 and the smoke evidence exists. Commit locally; do not push.

## Self-Review

1. **Spec coverage:** caption line (Tasks 1, 3, 8), single-lens passthrough (Task 8), picker with default tick, cap 6 and disabled confirm (Tasks 4, 8), 2-column contact sheet with per-cell caption and one footer line (Tasks 2, 7), temporary `LensView` with scope filter (Tasks 5, 7), size and memory limits (Task 2, 7), failure and cancel semantics (Tasks 7, 8), strings in 17 locales (Task 6), mutation/gate/smoke/audit (Task 9).
2. **Placeholder scan:** none; every code step has code or an exact algorithm, every command is concrete.
3. **Type consistency:** `SheetCell/SheetLayout/PolaroidContactSheetLayout.compute`, `LensExportSpec`, `CaptionLines(line1, line2, line3)`, `PolaroidCaptionFormatter.opticalLine`, `LensAppScope.forLens` and the `multiChoice` parameters are named identically in every task that uses them.
