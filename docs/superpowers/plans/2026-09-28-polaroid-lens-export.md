# Polaroid Lens Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user snapshot the currently-active lens's fisheye layout into a polaroid-framed PNG and share it, from two entry points (`ActHome`'s lens long-press menu, and a button in `FrmLens`).

**Architecture:** New `util/PolaroidExportHelper.kt` (Kotlin `object`) split into pure functions (caption formatting, filename sanitizing, frame-layout geometry, share-intent building — unit-tested with Robolectric) and one Android-side-effecting `exportAsync` (captures the live `LensView` on the calling/main thread, then does compositing + PNG compress + file write on `Dispatchers.IO`, delivers a `content://` `Uri` via a first-time-in-this-repo `FileProvider` on `Dispatchers.Main`). `ActHome` wires a new menu item and a one-shot auto-export intent extra that `FrmLens`'s button uses to relay through `ActHome` (which alone holds a real, content-rendering `LensView`).

**Tech Stack:** Kotlin + Java (existing mixed codebase), AndroidX `FileProvider`, Kotlin coroutines (`kotlinx-coroutines-android:1.9.0`, already a dependency), JUnit4 + Robolectric (unit), AndroidX Test + `AndroidJUnit4` + `ActivityScenario` / `launchFragmentInContainer` (androidTest) — all already in this project, no new dependency.

**Design spec:** `docs/superpowers/specs/2026-09-28-polaroid-lens-export-design.md` (read this first for the product decisions this plan implements).

## Global Constraints

- minSdk 25 / compileSdk 37 / targetSdk 37 (unchanged) — every API used below must exist on API 25+ (`FileProvider`, `Bitmap.compress`, `Canvas`, `Typeface.create` all do).
- No new Gradle dependency: `androidx.core.content.FileProvider` is already transitively resolved (via `com.google.android.material:material:1.13.0`, already used elsewhere as `ContextCompat`). If any task's `./gradlew assembleDevDebug` unexpectedly fails on dependency verification, run `./gradlew --write-verification-metadata sha256 assembleDevDebug` (see `CLAUDE.md`) rather than adding a dependency line.
- Device policy (this session, resolved 2026-09-28 after a memory/README conflict — see `feedback_device_target` memory): **TECNO KJ7, serial `115333744A005844`, is the only device to build/install/test on.** If it drops off `adb devices`, the standing fallback is TECNO BG6 (`118743744X002560`). Never use Samsung S24 Ultra or Pixel 7 Pro this session. If a Pixel or other device is also attached, do **not** run `./gradlew connected*AndroidTest` (it fans out to every attached device) — install with `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest`, then run tests with `adb -s 115333744A005844 shell am instrument -w -e class <FullyQualifiedTestClassName> com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`.
- Every new user-visible string must be translated into all 16 shipped locales before this feature is done: `ar, de, es, fr, hi, in, it, ja, km, ko, lo, pt, ru, th, vi, zh` (Task 5 does this in one pass for all 3 new strings).
- Each task's final step runs the **full** unit suite (`./gradlew testDevDebugUnitTest`) and reports the pass count, not just the new test(s) — this repo's regression discipline (see `doc/task/done/p2-lint-lint-009-notifydatasetchanged.md` for the pattern this plan follows).
- `./gradlew lintDevDebug` must stay at 0 errors after every task.
- Any test whose whole purpose is proving a specific fix/behavior gets a mutation check: temporarily break the production code, confirm the test fails with a clear message, restore it, confirm green again — before checking the task off.
- `PolaroidExportHelper.exportAsync`'s bitmap-capture step (`lensView.draw(...)`) **must** be called from the main thread — every call site in this plan already is (menu click callback, `Handler.post`), and every test calls it via `instrumentation.runOnMainSync`.

---

### Task 1: `PolaroidExportHelper` pure functions (caption, filename, layout geometry, share intent)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/PolaroidExportHelper.kt`
- Modify: `app/src/main/res/values/strings.xml` (add `lens_share_caption_via`)
- Test: `app/src/test/java/com/mckimquyen/util/PolaroidExportHelperTest.kt`

**Interfaces:**
- Produces (used by Task 2's `exportAsync` and Task 3's `ActHome` wiring):
  - `PolaroidExportHelper.formatCaption(context: Context, lensName: String): PolaroidExportHelper.CaptionLines` — `CaptionLines(val line1: String, val line2: String)`.
  - `PolaroidExportHelper.sanitizeFileName(lensName: String): String`.
  - `PolaroidExportHelper.calculatePolaroidLayout(contentWidthPx: Int, contentHeightPx: Int): PolaroidExportHelper.PolaroidLayout` — `PolaroidLayout(val outerWidth: Int, val outerHeight: Int, val contentLeft: Int, val contentTop: Int, val captionLine1BaselineY: Int, val captionLine2BaselineY: Int)`.
  - `PolaroidExportHelper.buildShareIntent(context: Context, imageUri: Uri): Intent`.

- [x] **Step 1: Add the new string resource**

In `app/src/main/res/values/strings.xml`, find this exact block (end of the `FISH-008` lens strings comment group, right before `</resources>`):

```xml
    <string name="lens_smart_focus_enable">Turn on Smart Focus</string>
    <string name="lens_smart_focus_disable">Turn off Smart Focus</string>
</resources>
```

Replace it with:

```xml
    <string name="lens_smart_focus_enable">Turn on Smart Focus</string>
    <string name="lens_smart_focus_disable">Turn off Smart Focus</string>

    <!-- FISH-EXPORT (polaroid lens export, 2026-09-28 brainstorm): translated into all 16
         locales in the same pass as lens_share_image / error_lens_share_failed below. -->
    <string name="lens_share_caption_via">via %1$s</string>
</resources>
```

- [x] **Step 2: Write the failing unit test**

Create `app/src/test/java/com/mckimquyen/util/PolaroidExportHelperTest.kt`:

```kotlin
package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PolaroidExportHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `formatCaption uses the lens name and an app-name branding line`() {
        val lines = PolaroidExportHelper.formatCaption(context, "Work")
        assertEquals("Work", lines.line1)
        assertEquals(
            context.getString(R.string.lens_share_caption_via, context.getString(R.string.app_name)),
            lines.line2
        )
    }

    @Test
    fun `formatCaption falls back to the app name for a blank lens name`() {
        val lines = PolaroidExportHelper.formatCaption(context, "   ")
        assertEquals(context.getString(R.string.app_name), lines.line1)
    }

    @Test
    fun `formatCaption truncates a very long lens name with an ellipsis`() {
        val longName = "A".repeat(40)
        val lines = PolaroidExportHelper.formatCaption(context, longName)
        assertEquals(24, lines.line1.length)
        assertTrue(lines.line1.endsWith("…"))
    }

    @Test
    fun `formatCaption keeps Vietnamese diacritics intact`() {
        val lines = PolaroidExportHelper.formatCaption(context, "Công việc")
        assertEquals("Công việc", lines.line1)
    }

    @Test
    fun `sanitizeFileName keeps a simple name unchanged`() {
        assertEquals("Work", PolaroidExportHelper.sanitizeFileName("Work"))
    }

    @Test
    fun `sanitizeFileName replaces unsafe characters with underscores`() {
        assertEquals("Work_Personal", PolaroidExportHelper.sanitizeFileName("Work/Personal"))
    }

    @Test
    fun `sanitizeFileName falls back to a default for a blank name`() {
        assertEquals("lens", PolaroidExportHelper.sanitizeFileName("   "))
    }

    @Test
    fun `sanitizeFileName falls back to a default for an all-emoji name`() {
        assertEquals("lens", PolaroidExportHelper.sanitizeFileName("😀😀"))
    }

    @Test
    fun `calculatePolaroidLayout adds a border and a caption band around the content`() {
        val layout = PolaroidExportHelper.calculatePolaroidLayout(800, 800)
        assertEquals(880, layout.outerWidth)
        assertEquals(1060, layout.outerHeight)
        assertEquals(40, layout.contentLeft)
        assertEquals(40, layout.contentTop)
        assertTrue(layout.captionLine1BaselineY > layout.contentTop + 800)
        assertTrue(layout.captionLine2BaselineY > layout.captionLine1BaselineY)
    }

    @Test
    fun `calculatePolaroidLayout coerces non-positive content size to at least 1px`() {
        val layout = PolaroidExportHelper.calculatePolaroidLayout(0, -5)
        assertEquals(81, layout.outerWidth)
        assertEquals(261, layout.outerHeight)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `buildShareIntent targets an image share with the given uri and read permission`() {
        val uri = "content://com.mckimquyen.lenslauncher.fileprovider/polaroid/Work.png".toUri()
        val intent = PolaroidExportHelper.buildShareIntent(context, uri)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }
}
```

- [x] **Step 3: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests PolaroidExportHelperTest -q`
Expected: FAIL to compile — `PolaroidExportHelper` does not exist yet.

- [x] **Step 4: Implement the pure functions**

Create `app/src/main/java/com/mckimquyen/util/PolaroidExportHelper.kt`:

```kotlin
package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mckimquyen.R

/**
 * Polaroid lens export (brainstormed 2026-09-27, spec:
 * docs/superpowers/specs/2026-09-28-polaroid-lens-export-design.md). Snapshots a lens's
 * currently-rendered LensView into a polaroid-framed PNG for sharing.
 *
 * Pure geometry/text/intent-building functions live here and are unit-tested directly
 * (PolaroidExportHelperTest). The Android-side-effecting `exportAsync` (bitmap capture, frame
 * compositing, file write, FileProvider) is added in a later task and covered by androidTest,
 * since it needs a real Bitmap/Canvas/File - same split this codebase already uses for
 * ApertureRevealHelper (pure) vs LayoutBackupIo (side-effecting).
 */
object PolaroidExportHelper {

    private const val BORDER_PX = 40
    private const val CAPTION_HEIGHT_PX = 180
    private const val CAPTION_LINE1_TEXT_SIZE_PX = 56f
    private const val CAPTION_LINE2_TEXT_SIZE_PX = 34f
    private const val CAPTION_LINE_GAP_PX = 16
    private const val MAX_LENS_NAME_LENGTH = 24
    private val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9_-]+")

    data class CaptionLines(val line1: String, val line2: String)

    data class PolaroidLayout(
        val outerWidth: Int,
        val outerHeight: Int,
        val contentLeft: Int,
        val contentTop: Int,
        val captionLine1BaselineY: Int,
        val captionLine2BaselineY: Int
    )

    /** line1 = the lens name (truncated if very long, falls back to the app name if blank);
     *  line2 = a fixed "via <app name>" branding line. */
    @JvmStatic
    fun formatCaption(context: Context, lensName: String): CaptionLines {
        val trimmed = lensName.trim()
        val base = trimmed.ifEmpty { context.getString(R.string.app_name) }
        val line1 = if (base.length > MAX_LENS_NAME_LENGTH) {
            base.take(MAX_LENS_NAME_LENGTH - 1) + "…"
        } else {
            base
        }
        val line2 = context.getString(R.string.lens_share_caption_via, context.getString(R.string.app_name))
        return CaptionLines(line1, line2)
    }

    /** Strips characters unsafe for a filesystem path component; never returns an empty string. */
    @JvmStatic
    fun sanitizeFileName(lensName: String): String {
        val cleaned = UNSAFE_FILENAME_CHARS.replace(lensName.trim(), "_").trim('_')
        return cleaned.ifEmpty { "lens" }
    }

    /** Pure geometry: a white polaroid frame around [contentWidthPx]x[contentHeightPx], with a
     *  caption band below it. No Android Context/view dependency. */
    @JvmStatic
    fun calculatePolaroidLayout(contentWidthPx: Int, contentHeightPx: Int): PolaroidLayout {
        val w = contentWidthPx.coerceAtLeast(1)
        val h = contentHeightPx.coerceAtLeast(1)
        val outerWidth = w + BORDER_PX * 2
        val outerHeight = h + BORDER_PX * 2 + CAPTION_HEIGHT_PX
        val captionTop = BORDER_PX + h + BORDER_PX
        val line1Baseline = captionTop + CAPTION_LINE1_TEXT_SIZE_PX.toInt()
        val line2Baseline = line1Baseline + CAPTION_LINE2_TEXT_SIZE_PX.toInt() + CAPTION_LINE_GAP_PX
        return PolaroidLayout(
            outerWidth = outerWidth,
            outerHeight = outerHeight,
            contentLeft = BORDER_PX,
            contentTop = BORDER_PX,
            captionLine1BaselineY = line1Baseline,
            captionLine2BaselineY = line2Baseline
        )
    }

    /** ACTION_SEND for [imageUri], matching ext/Activity.kt's shareApp() pattern (the caller
     *  wraps this in Intent.createChooser(...) with R.string.share_via). */
    @JvmStatic
    fun buildShareIntent(context: Context, imageUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
```

- [x] **Step 5: Run the test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests PolaroidExportHelperTest -q`
Expected: PASS (11 tests, 0 failures).

- [x] **Step 6: Run the full unit suite and lint**

Run: `./gradlew testDevDebugUnitTest -q && ./gradlew lintDevDebug -q`
Expected: full suite passes (591 tests: 580 pre-existing + 11 new), 0 lint errors.

- [x] **Step 7: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/PolaroidExportHelper.kt app/src/test/java/com/mckimquyen/util/PolaroidExportHelperTest.kt app/src/main/res/values/strings.xml
git commit -m "feat(lens-export): add PolaroidExportHelper pure functions (caption, filename, layout, share intent)"
```

**Deviation found, tried, and reverted during execution (not in the original plan) - recorded so the same dead end isn't retried:** the unit test's `context.getString(R.string.xxx)` calls in the original `formatCaption(context, lensName)` design threw `Resources$NotFoundException` - no prior Robolectric test in this repo had ever called `getString`. First fix attempt: set `testOptions.unitTests.includeAndroidResources = true` in `app/build.gradle` (the standard AGP fix) plus `app/src/test/resources/robolectric.properties` (`sdk=34`, working around Robolectric 4.11.1's API-34 ceiling against this app's targetSdk 37). This *worked* for this test but had a much bigger side effect than expected: it made Robolectric actually instantiate the real `RApplication` (per the manifest) for every other unit test that didn't opt out with `@Config(manifest = Config.NONE)`, running its real `onCreate()` - which seeds a default `LensWorkspace` row and installs `StrictMode` policies as side effects. That broke `LensWorkspaceDaoTest` (an unrelated test asserting an exact, now-polluted row list) and `DebugStrictModeAndThemedIconTest` (StrictMode leaking across tests in the same JVM fork). Patching `DebugStrictModeAndThemedIconTest` with a defensive `@Before` reset fixed that one, but discovering a second, unrelated collateral regression from the same one-line Gradle flag was the signal to stop patching symptoms and fix the actual design instead.

**Actual fix, kept:** `PolaroidExportHelper` never needed a `Context` in its pure functions at all - `formatCaption(context, lensName): CaptionLines` was replaced with `buildCaptionLine1(lensName, fallbackName): String`, a fully pure function with no resource dependency. The two real string-resource lookups (`R.string.app_name`, `R.string.lens_share_caption_via`) moved into `exportAsync` itself, which only ever runs against a real Android `Context` (production, or androidTest - never a JVM unit test). `PolaroidExportHelperTest` still needs `@RunWith(RobolectricTestRunner::class)` (its `buildShareIntent` test touches the real `Intent` framework class, which needs Robolectric's shadow to work in a JVM test at all) but now also declares `@Config(manifest = Config.NONE)` - the same annotation `DebugStrictModeAndThemedIconTest` already used for exactly this reason - so it never touches the real manifest, `RApplication`, or resources, and carries zero risk to any other test. `app/build.gradle`'s `testOptions` block and `robolectric.properties` were both removed again; `DebugStrictModeAndThemedIconTest` was reverted to its original form since the condition that motivated the defensive `@Before` no longer exists. `buildShareIntent(context, imageUri)` also lost its always-unused `context` parameter in the same pass (`ActHome`'s call site updated to match).

Verified clean after the redesign: `591` unit tests (589 pass, the same 2 expected-pending until Task 5's i18n - nothing else), `20/20` androidTest across `PolaroidExportHelperWidgetTest`/`PolaroidExportHelperIntegrationTest`/`ActHomeLensManagementWidgetTest`/`ActHomeLensShareIntegrationTest`, lint back to exactly the expected 3 `MissingTranslation` errors (0 new warnings - a stray `UseKtx` on the two `Bitmap.createBitmap(...)` calls introduced by this task was also fixed to match this codebase's already-established `androidx.core.graphics.createBitmap(...)` KTX convention).

No separate "test-infra" commit exists in the final history - the redesign is folded into Task 1 and Task 3's own commits instead.

---

### Task 2: FileProvider infra + `LensView.resetToIdleForExport()` + `exportAsync`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (add `<provider>`)
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt` (add `resetToIdleForExport()`)
- Modify: `app/src/main/java/com/mckimquyen/util/PolaroidExportHelper.kt` (add `exportAsync`)
- Test: Create `app/src/androidTest/java/com/mckimquyen/util/PolaroidExportHelperWidgetTest.kt`
- Test: Create `app/src/androidTest/java/com/mckimquyen/util/PolaroidExportHelperIntegrationTest.kt`

**Interfaces:**
- Consumes: `PolaroidExportHelper.formatCaption`, `.sanitizeFileName`, `.calculatePolaroidLayout` (Task 1). `LensView` (existing: `width`/`height` from `View`, `mTouchX`/`mTouchY`/`gestureState` fields already in the class).
- Produces (used by Task 3's `ActHome` wiring):
  - `LensView.resetToIdleForExport()` (internal, no args, `Unit`).
  - `PolaroidExportHelper.exportAsync(lensView: LensView, lensName: String, context: Context, onDone: (Uri?) -> Unit)` — must be called from the main thread; delivers `onDone` on the main thread.

- [x] **Step 1: Add the FileProvider to the manifest**

In `app/src/main/AndroidManifest.xml`, find this exact block:

```xml
        <!-- ====================================================================
             BROADCAST RECEIVERS - Lắng nghe sự kiện hệ thống và internal events
             ==================================================================== -->
```

Insert immediately before it:

```xml
        <!-- ====================================================================
             FILE PROVIDER - Chia sẻ ảnh polaroid lens qua FileProvider (cache dir)
             ==================================================================== -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

        <!-- ====================================================================
             BROADCAST RECEIVERS - Lắng nghe sự kiện hệ thống và internal events
             ==================================================================== -->
```

- [x] **Step 2: Add the file-paths declaration**

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Scoped narrowly to the one subdirectory PolaroidExportHelper writes to - not the whole
         cache dir - so the FileProvider can never expose anything else this app ever caches. -->
    <cache-path name="polaroid" path="polaroid/" />
</paths>
```

- [x] **Step 3: Verify the manifest merges cleanly**

Run: `./gradlew :app:processDevDebugManifest -q`
Expected: task succeeds. Then: `grep -A2 'androidx.core.content.FileProvider' app/build/intermediates/merged_manifest/devDebug/processDevDebugManifest/AndroidManifest.xml`
Expected: the `<provider>` block appears with `android:authorities="com.mckimquyen.lenslauncher.dev.fileprovider"` (or the equivalent resolved `applicationId` for whichever flavor merged).

- [x] **Step 4: Add `resetToIdleForExport()` to `LensView`**

In `app/src/main/java/com/mckimquyen/views/LensView.kt`, find this exact block:

```kotlin
    /** Puts the lens in a given drag state without driving the (unattached-view-unfriendly) Animation. */
    @androidx.annotation.VisibleForTesting
    internal fun setLensStateForTest(touchX: Float, touchY: Float, animationMultiplier: Float, reduceMotion: Boolean) {
        mTouchX = touchX
        mTouchY = touchY
        mAnimationMultiplier = animationMultiplier
        mReduceMotion = reduceMotion
    }
```

Replace it with:

```kotlin
    /** Puts the lens in a given drag state without driving the (unattached-view-unfriendly) Animation. */
    @androidx.annotation.VisibleForTesting
    internal fun setLensStateForTest(touchX: Float, touchY: Float, animationMultiplier: Float, reduceMotion: Boolean) {
        mTouchX = touchX
        mTouchY = touchY
        mAnimationMultiplier = animationMultiplier
        mReduceMotion = reduceMotion
    }

    /** FISH-EXPORT: called by PolaroidExportHelper.exportAsync right before it draws this view
     *  into an offscreen bitmap, so a live touch/pinch never bakes distortion or the pinch HUD
     *  (drawPinchHud, gated on gestureState) into the exported snapshot. Deliberately narrower
     *  than setLensStateForTest above (that one also drives animation/reduceMotion state for a
     *  different testing purpose this export path has no reason to touch). */
    internal fun resetToIdleForExport() {
        mTouchX = -Float.MAX_VALUE
        mTouchY = -Float.MAX_VALUE
        gestureState = LensGestureState.IDLE
    }
```

- [x] **Step 5: Write the failing widget test (capture + reset-before-draw)**

Create `app/src/androidTest/java/com/mckimquyen/util/PolaroidExportHelperWidgetTest.kt`:

```kotlin
package com.mckimquyen.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.views.LensGestureState
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PolaroidExportHelperWidgetTest {

    private companion object {
        const val SIZE_PX = 400
        const val APP_COUNT = 12
        const val ICON_PX = 48
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val iconBitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(Color.RED) }
    private lateinit var lensView: LensView

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.polaroid.app$i", name = "App$i", label = "App $i", iconCacheKey = "polaroid#$i")
        })
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, iconBitmap) }
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, SIZE_PX, SIZE_PX)
                setApps(apps)
            }
        }
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.clearAllData()
        File(context.cacheDir, "polaroid").listFiles()?.forEach { it.delete() }
    }

    private fun exportAndAwait(lensName: String): android.net.Uri? {
        val latch = CountDownLatch(1)
        var result: android.net.Uri? = null
        instrumentation.runOnMainSync {
            PolaroidExportHelper.exportAsync(lensView, lensName, context) { uri ->
                result = uri
                latch.countDown()
            }
        }
        assertTrue("export must complete within 5s", latch.await(5, TimeUnit.SECONDS))
        return result
    }

    @Test
    fun exportAsync_producesAFramedPngLargerThanTheRawContent() {
        val uri = exportAndAwait("Work")
        assertNotNull("export must succeed for a laid-out view", uri)

        val file = File(File(context.cacheDir, "polaroid"), "Work.png")
        assertTrue("exported PNG must exist on disk at the expected path", file.exists())

        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull(decoded)
        assertTrue("framed image must be wider than raw content (border added)", decoded!!.width > SIZE_PX)
        assertTrue("framed image must be taller than raw content (border + caption)", decoded.height > SIZE_PX)
    }

    @Test
    fun exportAsync_resetsLiveTouchAndGestureStateBeforeCapture() {
        instrumentation.runOnMainSync {
            lensView.setLensStateForTest(50f, 50f, 1f, reduceMotion = false)
            lensView.gestureState = LensGestureState.PINCHING
        }

        exportAndAwait("Pinching")

        instrumentation.runOnMainSync {
            assertEquals(
                "resetToIdleForExport() must run before draw() so a live pinch never leaks into the snapshot",
                LensGestureState.IDLE,
                lensView.gestureState
            )
        }
    }
}
```

- [x] **Step 6: Run it to verify it fails**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.util.PolaroidExportHelperWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAIL to compile/run — `exportAsync` does not exist yet.

- [x] **Step 7: Implement `exportAsync` in `PolaroidExportHelper.kt`**

In `app/src/main/java/com/mckimquyen/util/PolaroidExportHelper.kt`, replace the import block:

```kotlin
package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mckimquyen.R
```

with:

```kotlin
package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.mckimquyen.R
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.views.LensView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
```

Then, right before the final closing `}` of the `object PolaroidExportHelper` body (i.e. immediately after the `buildShareIntent` function), add:

```kotlin

    private fun renderFrame(content: Bitmap, layout: PolaroidLayout, caption: CaptionLines): Bitmap {
        val framed = Bitmap.createBitmap(layout.outerWidth, layout.outerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(framed)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(content, layout.contentLeft.toFloat(), layout.contentTop.toFloat(), null)

        val line1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = CAPTION_LINE1_TEXT_SIZE_PX
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        val line2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = CAPTION_LINE2_TEXT_SIZE_PX
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        val centerX = layout.outerWidth / 2f
        canvas.drawText(caption.line1, centerX, layout.captionLine1BaselineY.toFloat(), line1Paint)
        canvas.drawText(caption.line2, centerX, layout.captionLine2BaselineY.toFloat(), line2Paint)
        return framed
    }

    private fun writeToCache(context: Context, bitmap: Bitmap, fileName: String): File {
        val dir = File(context.cacheDir, "polaroid").apply { mkdirs() }
        val file = File(dir, "$fileName.png")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return file
    }

    /**
     * Must be called from the main thread - [lensView].draw() requires it. Captures the bitmap
     * synchronously on the calling thread, then moves to [Dispatchers.IO] only for
     * compositing/compress/file-write, then delivers [onDone] on [Dispatchers.Main]. Delivers
     * `null` on any failure: an unlaid-out view (width/height <= 0), an OutOfMemoryError, or an
     * I/O/FileProvider failure.
     */
    @JvmStatic
    fun exportAsync(lensView: LensView, lensName: String, context: Context, onDone: (Uri?) -> Unit) {
        val width = lensView.width
        val height = lensView.height
        if (width <= 0 || height <= 0) {
            onDone(null)
            return
        }
        lensView.resetToIdleForExport()
        val contentBitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                lensView.draw(Canvas(bmp))
            }
        } catch (error: OutOfMemoryError) {
            Logger.e("PolaroidExportHelper: bitmap alloc failed", error)
            onDone(null)
            return
        }

        val appContext = context.applicationContext
        val caption = formatCaption(appContext, lensName)
        val fileName = sanitizeFileName(lensName)
        ApplicationScope.scope.launch(Dispatchers.IO) {
            val uri = try {
                val layout = calculatePolaroidLayout(contentBitmap.width, contentBitmap.height)
                val framed = renderFrame(contentBitmap, layout, caption)
                val file = writeToCache(appContext, framed, fileName)
                framed.recycle()
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            } catch (error: Exception) {
                Logger.e("PolaroidExportHelper: export failed", error)
                null
            } finally {
                contentBitmap.recycle()
            }
            withContext(Dispatchers.Main) { onDone(uri) }
        }
    }
```

- [x] **Step 8: Run the widget test to verify it passes**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.util.PolaroidExportHelperWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: `OK (2 tests)`.

- [x] **Step 9: Mutation check the reset-before-draw behavior**

Temporarily comment out the `lensView.resetToIdleForExport()` line inside `exportAsync`. Reinstall (`ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`) and rerun just `exportAsync_resetsLiveTouchAndGestureStateBeforeCapture`:

Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.util.PolaroidExportHelperWidgetTest#exportAsync_resetsLiveTouchAndGestureStateBeforeCapture com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAILS with the assertion message about `resetToIdleForExport()`.

Restore the `lensView.resetToIdleForExport()` line, reinstall, rerun the same command.
Expected: `OK (1 test)`.

- [x] **Step 10: Write and run the integration test (real FileProvider + contentResolver boundary)**

Create `app/src/androidTest/java/com/mckimquyen/util/PolaroidExportHelperIntegrationTest.kt`:

```kotlin
package com.mckimquyen.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PolaroidExportHelperIntegrationTest {

    private companion object {
        const val SIZE_PX = 300
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val iconBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
    private lateinit var lensView: LensView

    @Before
    fun setUp() {
        val apps = ArrayList(
            listOf(App(id = 1, packageName = "com.polaroid.a", name = "A", label = "A", iconCacheKey = "polaroid-int#a"))
        )
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, iconBitmap) }
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, SIZE_PX, SIZE_PX)
                setApps(apps)
            }
        }
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.clearAllData()
        File(context.cacheDir, "polaroid").listFiles()?.forEach { it.delete() }
    }

    @Test
    fun exportAsync_producesAContentUriReadableAcrossTheFileProviderBoundary() {
        val latch = CountDownLatch(1)
        var uri: Uri? = null
        instrumentation.runOnMainSync {
            PolaroidExportHelper.exportAsync(lensView, "Integration", context) { result ->
                uri = result
                latch.countDown()
            }
        }
        assertTrue("export must complete within 5s", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("FileProvider must resolve a real content:// uri", uri)
        assertEquals("content", uri!!.scheme)

        context.contentResolver.openInputStream(uri!!)?.use { stream ->
            assertTrue("the shared file must contain real PNG bytes", stream.readBytes().isNotEmpty())
        } ?: throw AssertionError("contentResolver could not open the FileProvider uri - check file_paths.xml")
    }
}
```

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.util.PolaroidExportHelperIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: `OK (1 test)`.

- [x] **Step 11: Run the full unit suite and lint**

Run: `./gradlew testDevDebugUnitTest -q && ./gradlew lintDevDebug -q`
Expected: full suite passes (591 tests, unchanged from Task 1 - no new unit tests this task), 0 lint errors.

- [x] **Step 12: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml \
  app/src/main/java/com/mckimquyen/views/LensView.kt \
  app/src/main/java/com/mckimquyen/util/PolaroidExportHelper.kt \
  app/src/androidTest/java/com/mckimquyen/util/PolaroidExportHelperWidgetTest.kt \
  app/src/androidTest/java/com/mckimquyen/util/PolaroidExportHelperIntegrationTest.kt
git commit -m "feat(lens-export): add FileProvider + LensView.resetToIdleForExport() + PolaroidExportHelper.exportAsync"
```

---

### Task 3: Wire the export into `ActHome` (long-press menu item + auto-export intent extra)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Modify: `app/src/main/res/values/strings.xml` (add `lens_share_image`, `error_lens_share_failed`)
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt` (add one test)
- Test: Create `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensShareIntegrationTest.kt`

**Interfaces:**
- Consumes: `PolaroidExportHelper.exportAsync`, `PolaroidExportHelper.buildShareIntent` (Task 2).
- Produces (used by Task 4's `FrmLens` button):
  - `public static final String ActHome.EXTRA_AUTO_EXPORT_LENS` (Intent extra key).
  - `ActHome.LensShareLauncher` (`@VisibleForTesting` functional interface: `void launch(Intent chooserIntent)`) and the field `ActHome.lensShareLauncher` (`@VisibleForTesting`, default `this::startActivity`) - the same test seam pattern this file already uses for `lensManagementMenu`/`lensDialog`.

- [x] **Step 1: Add the two new strings**

In `app/src/main/res/values/strings.xml`, find:

```xml
    <string name="lens_share_caption_via">via %1$s</string>
</resources>
```

Replace with:

```xml
    <string name="lens_share_caption_via">via %1$s</string>
    <string name="lens_share_image">Share lens image</string>
    <string name="error_lens_share_failed">Couldn\'t share lens image</string>
</resources>
```

- [x] **Step 2: Write the failing widget test (menu item 5 end-to-end) first**

In `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt`, find:

```kotlin
    // Menu item ids as built by ActHome.showLensManagementMenu.
    private val itemAdd = 1
    private val itemRename = 2
    private val itemDelete = 3
    private val itemSmartFocus = 4
```

Replace with:

```kotlin
    // Menu item ids as built by ActHome.showLensManagementMenu.
    private val itemAdd = 1
    private val itemRename = 2
    private val itemDelete = 3
    private val itemSmartFocus = 4
    private val itemShare = 5
```

Then add, right before the final closing `}` of the `ActHomeLensManagementWidgetTest` class:

```kotlin

    @Suppress("DEPRECATION")
    @Test
    fun shareMenuItem_exportsAndLaunchesAChooserForTheActiveLens() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        var captured: android.content.Intent? = null
        val latch = java.util.concurrent.CountDownLatch(1)

        scenario.onActivity { activity ->
            activity.lensShareLauncher = ActHome.LensShareLauncher { intent ->
                captured = intent
                latch.countDown()
            }
            activity.onLensMenuItemSelected(itemShare, 0)
        }

        assertTrue("share export must complete", latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertNotNull("selecting Share must build a chooser Intent", captured)

        val inner = captured!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertNotNull("the chooser must wrap a real ACTION_SEND intent", inner)
        assertEquals(android.content.Intent.ACTION_SEND, inner!!.action)
        assertEquals("image/png", inner.type)
        assertNotNull(
            "the send intent must carry the exported image's uri",
            inner.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        )

        scenario.close()
    }
```

- [x] **Step 3: Run it to verify it fails**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Expected: build FAILS to compile - `itemShare`/`ActHome.LensShareLauncher`/`activity.lensShareLauncher`/`onLensMenuItemSelected(itemShare, 0)` reference API that does not exist yet (item 5 isn't handled, `lensShareLauncher` isn't a field).

- [x] **Step 4: Add imports**

In `app/src/main/java/com/mckimquyen/ui/ActHome.java`, find:

```java
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
```

Replace with:

```java
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
```

Find:

```java
import com.mckimquyen.util.ApertureRevealHelper;
import com.mckimquyen.util.Logger;
import com.mckimquyen.util.UIUtils;
```

Replace with:

```java
import com.mckimquyen.util.ApertureRevealHelper;
import com.mckimquyen.util.Logger;
import com.mckimquyen.util.PolaroidExportHelper;
import com.mckimquyen.util.UIUtils;
```

- [x] **Step 5: Add the new fields (constant, pending-export flag, test seam)**

Find:

```java
    private List<LensWorkspace> currentLenses = new ArrayList<>();
```

Replace with:

```java
    private List<LensWorkspace> currentLenses = new ArrayList<>();

    /** FISH-EXPORT: set by FrmLens's share button on the Intent that (re)launches this singleTask
     *  activity, so onLensMenuItemSelected's item 5 path runs automatically once currentLenses is
     *  loaded, instead of FrmLens needing its own copy of the export logic. */
    public static final String EXTRA_AUTO_EXPORT_LENS =
            "com.mckimquyen.lenslauncher.EXTRA_AUTO_EXPORT_LENS";
    private boolean pendingAutoExportLens = false;

    /** Test seam so PolaroidExportHelperWidgetTest-style tests can capture the built chooser
     *  Intent instead of actually popping a real system share sheet - same pattern as
     *  lensManagementMenu/lensDialog below. */
    @androidx.annotation.VisibleForTesting
    interface LensShareLauncher {
        void launch(Intent chooserIntent);
    }

    @androidx.annotation.VisibleForTesting
    LensShareLauncher lensShareLauncher = this::startActivity;
```

- [x] **Step 6: Consume the auto-export extra in `onCreate`, add `onNewIntent`**

Find:

```java
        setupSearch();
        // updateColor();
        refreshLensList();
```

Replace with:

```java
        setupSearch();
        // updateColor();
        consumeAutoExportExtra(getIntent());
        refreshLensList();
```

Find:

```java
        rateAppInApp(this, BuildConfig.DEBUG);
    }
```

Replace with:

```java
        rateAppInApp(this, BuildConfig.DEBUG);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeAutoExportExtra(intent);
        refreshLensList();
    }

    /** Consumes (clears) the one-shot auto-export flag so a later rotation/recreate never
     *  re-triggers it - the same one-shot-extra pattern this codebase already needs because
     *  ActHome is launchMode="singleTask" (relaunching it calls onNewIntent, not onCreate). */
    private void consumeAutoExportExtra(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_AUTO_EXPORT_LENS, false)) {
            intent.removeExtra(EXTRA_AUTO_EXPORT_LENS);
            pendingAutoExportLens = true;
        }
    }
```

- [x] **Step 7: Trigger the pending auto-export once lenses are loaded**

Find:

```java
                        break;
                    }
                }
            }
            return Unit.INSTANCE;
        });
    }
```

Replace with:

```java
                        break;
                    }
                }
            }
            if (pendingAutoExportLens) {
                pendingAutoExportLens = false;
                lensPager.post(this::exportActiveLensImage);
            }
            return Unit.INSTANCE;
        });
    }
```

- [x] **Step 8: Add the menu item, its handler, and `exportActiveLensImage()`**

Find:

```java
        menu.getMenu().add(0, 4, 0, lensSmartFocusMenuLabelRes(position));
        menu.setOnMenuItemClickListener(item -> onLensMenuItemSelected(item.getItemId(), position));
        menu.show();
```

Replace with:

```java
        menu.getMenu().add(0, 4, 0, lensSmartFocusMenuLabelRes(position));
        menu.getMenu().add(0, 5, 0, R.string.lens_share_image);
        menu.setOnMenuItemClickListener(item -> onLensMenuItemSelected(item.getItemId(), position));
        menu.show();
```

Find:

```java
        } else if (itemId == 4) {
            toggleSmartFocusForLens(current);
            return true;
        }
        return false;
    }
```

Replace with:

```java
        } else if (itemId == 4) {
            toggleSmartFocusForLens(current);
            return true;
        } else if (itemId == 5) {
            exportActiveLensImage();
            return true;
        }
        return false;
    }
```

Find:

```java
    private void toggleSmartFocusForLens(LensWorkspace lens) {
        if (utilSettings == null) return;
        boolean enabled = !utilSettings.isSmartFocusBias(lens.getId());
        utilSettings.saveSmartFocusBias(lens.getId(), enabled);
        if (lensViews != null) {
            lensViews.refreshSmartFocus();
        }
    }
```

Replace with:

```java
    private void toggleSmartFocusForLens(LensWorkspace lens) {
        if (utilSettings == null) return;
        boolean enabled = !utilSettings.isSmartFocusBias(lens.getId());
        utilSettings.saveSmartFocusBias(lens.getId(), enabled);
        if (lensViews != null) {
            lensViews.refreshSmartFocus();
        }
    }

    /** FISH-EXPORT: snapshots the currently visible lens - via {@link #lensViews}, this file's
     *  existing "which LensView is actually on screen" pointer (see its own field comment), kept
     *  correct across page-selection and rebind-after-rotation - into a polaroid-framed PNG and
     *  hands it to the share sheet via {@link #lensShareLauncher}. */
    private void exportActiveLensImage() {
        LensView view = lensViews;
        if (view == null) return;
        String activeLensId = view.getLensId();
        LensWorkspace matched = null;
        for (LensWorkspace lens : currentLenses) {
            if (lens.getId().equals(activeLensId)) {
                matched = lens;
                break;
            }
        }
        if (matched == null) return;
        String lensName = matched.getName();
        PolaroidExportHelper.exportAsync(view, lensName, this, uri -> {
            if (uri != null) {
                try {
                    lensShareLauncher.launch(Intent.createChooser(
                            PolaroidExportHelper.buildShareIntent(this, uri),
                            getString(R.string.share_via)));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
            }
            return Unit.INSTANCE;
        });
    }
```

- [x] **Step 9: Run the test to verify it passes**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensManagementWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: `OK (16 tests)` (15 pre-existing + 1 new).

- [x] **Step 10: Mutation-check**

Temporarily delete the `} else if (itemId == 5) { exportActiveLensImage(); return true; }` branch from `onLensMenuItemSelected`. Reinstall and rerun just the new test:
Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensManagementWidgetTest#shareMenuItem_exportsAndLaunchesAChooserForTheActiveLens com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAILS (latch times out - `onLensMenuItemSelected` returns `false` and never calls the launcher).

Restore the branch, reinstall, rerun the same command.
Expected: `OK (1 test)`.

- [x] **Step 11: Write and run the cross-lens integration test**

Create `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensShareIntegrationTest.kt`:

```kotlin
package com.mckimquyen.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** FISH-EXPORT: sharing must export the ACTIVE lens after paging, not the first/default one. */
@RunWith(AndroidJUnit4::class)
class ActHomeLensShareIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() = cleanDb()

    @After
    fun tearDown() {
        cleanDb()
        File(context.cacheDir, "polaroid").listFiles()?.forEach { it.delete() }
    }

    private fun cleanDb(): Unit = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        dao.insertOrUpdate(LensWorkspace(id = "second-lens", name = "Second Lens", orderIndex = 1))
        Unit
    }

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
    }

    @Test
    fun sharingAfterSwipingToTheSecondLens_exportsTheSecondLensNotTheFirst() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        idle()

        scenario.onActivity { activity ->
            activity.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(1, false)
        }
        idle()

        var captured: Intent? = null
        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.lensShareLauncher = ActHome.LensShareLauncher { intent ->
                captured = intent
                latch.countDown()
            }
            activity.onLensMenuItemSelected(5, 1)
        }
        assertTrue("share export must complete", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("sharing the second lens must still launch a chooser", captured)

        assertTrue(
            "export must target the active (second) lens's file, not the default lens's",
            File(File(context.cacheDir, "polaroid"), "Second_Lens.png").exists()
        )

        scenario.close()
    }
}
```

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensShareIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: `OK (1 test)`.

- [x] **Step 12: Run the full unit suite and lint**

Run: `./gradlew testDevDebugUnitTest -q && ./gradlew lintDevDebug -q`
Expected: full suite passes (591 tests, unchanged - all new tests this task are androidTest), 0 lint errors.

- [x] **Step 13: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/main/res/values/strings.xml \
  app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt \
  app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensShareIntegrationTest.kt
git commit -m "feat(lens-export): wire Share lens image into ActHome's long-press menu + auto-export intent"
```

---

### Task 4: `FrmLens` share button (relays through `ActHome`)

**Files:**
- Modify: `app/src/main/res/layout/frm_lens.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmLens.kt`
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/FrmLensPerLensWidgetTest.kt` (add one test)

**Interfaces:**
- Consumes: `ActHome.EXTRA_AUTO_EXPORT_LENS` (Task 3).
- Produces: `FrmLens.lensExportLauncher: (Intent) -> Unit` (`@VisibleForTesting internal`, default `{ intent -> startActivity(intent) }`) - same test-seam shape as `ActHome.lensShareLauncher`.

- [ ] **Step 1: Write the failing widget test first**

In `app/src/androidTest/java/com/mckimquyen/ui/FrmLensPerLensWidgetTest.kt`, add these imports:

```kotlin
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
```

(they are already present in the file - just confirm, do not duplicate). Then add, right before the final closing `}` of `FrmLensPerLensWidgetTest`:

```kotlin

    @Test
    fun shareButton_startsActHomeWithTheAutoExportExtra() {
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        var captured: android.content.Intent? = null

        scenario.onFragment { fragment ->
            fragment.lensExportLauncher = { intent -> captured = intent }
            fragment.requireView().findViewById<MaterialButton>(R.id.btnShareLens).performClick()
        }

        assertNotNull("the share button must launch an intent", captured)
        assertEquals(ActHome::class.java.name, captured!!.component?.className)
        org.junit.Assert.assertTrue(
            "the intent must carry the one-shot auto-export flag",
            captured!!.getBooleanExtra(ActHome.EXTRA_AUTO_EXPORT_LENS, false)
        )
        scenario.close()
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Expected: build FAILS to compile - `R.id.btnShareLens` and `fragment.lensExportLauncher` don't exist yet.

- [ ] **Step 3: Add the button to the layout**

In `app/src/main/res/layout/frm_lens.xml`, find:

```xml
            <com.mckimquyen.views.LensView
                android:id="@+id/lensViewsSettings"
                android:layout_width="match_parent"
                android:layout_height="@dimen/height_lens_preview"
                android:background="@drawable/bg_lens_preview_card" />

        </com.google.android.material.card.MaterialCardView>

        <!-- Min Icon Size Card -->
```

Replace with:

```xml
            <com.mckimquyen.views.LensView
                android:id="@+id/lensViewsSettings"
                android:layout_width="match_parent"
                android:layout_height="@dimen/height_lens_preview"
                android:background="@drawable/bg_lens_preview_card" />

        </com.google.android.material.card.MaterialCardView>

        <!-- FISH-EXPORT: relays to ActHome, which alone holds a real content-rendering LensView -
             this preview card above draws DrawType.CIRCLES placeholders, not real app icons. -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnShareLens"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:icon="@drawable/ic_share_24dp"
            android:text="@string/lens_share_image" />

        <!-- Min Icon Size Card -->
```

- [ ] **Step 4: Wire the button in `FrmLens.kt`**

Find:

```kotlin
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
```

Replace with:

```kotlin
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
```

Find:

```kotlin
    private var btnPresetGentle: MaterialButton? = null
    private var btnPresetStandard: MaterialButton? = null
    private var btnPresetSnappy: MaterialButton? = null
    private var utilSettings: UtilSettings? = null
    private var activeLensId: String = LensWorkspace.DEFAULT_LENS_ID
```

Replace with:

```kotlin
    private var btnPresetGentle: MaterialButton? = null
    private var btnPresetStandard: MaterialButton? = null
    private var btnPresetSnappy: MaterialButton? = null
    private var btnShareLens: MaterialButton? = null
    private var utilSettings: UtilSettings? = null
    private var activeLensId: String = LensWorkspace.DEFAULT_LENS_ID

    /** Test seam: swap this to capture the launched Intent instead of actually starting ActHome -
     *  same pattern as ActHome.lensShareLauncher, which this button ultimately triggers. */
    @androidx.annotation.VisibleForTesting
    internal var lensExportLauncher: (Intent) -> Unit = { intent -> startActivity(intent) }
```

Find:

```kotlin
        btnPresetGentle?.setOnClickListener { applyPreset(LensPhysicsPreset.GENTLE) }
        btnPresetStandard?.setOnClickListener { applyPreset(LensPhysicsPreset.STANDARD) }
        btnPresetSnappy?.setOnClickListener { applyPreset(LensPhysicsPreset.SNAPPY) }
    }
```

Replace with:

```kotlin
        btnPresetGentle?.setOnClickListener { applyPreset(LensPhysicsPreset.GENTLE) }
        btnPresetStandard?.setOnClickListener { applyPreset(LensPhysicsPreset.STANDARD) }
        btnPresetSnappy?.setOnClickListener { applyPreset(LensPhysicsPreset.SNAPPY) }
        btnShareLens = view.findViewById(R.id.btnShareLens)
        btnShareLens?.setOnClickListener { shareLensImage() }
    }

    private fun shareLensImage() {
        val intent = Intent(requireContext(), ActHome::class.java).apply {
            putExtra(ActHome.EXTRA_AUTO_EXPORT_LENS, true)
        }
        lensExportLauncher(intent)
    }
```

Find:

```kotlin
    override fun onDestroyView() {
        lensViewsSettings = null
        sbMinIconSize = null
```

Replace with:

```kotlin
    override fun onDestroyView() {
        lensViewsSettings = null
        btnShareLens = null
        sbMinIconSize = null
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.FrmLensPerLensWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: `OK (7 tests)` (6 pre-existing + 1 new).

- [ ] **Step 6: Mutation-check**

Temporarily change `btnShareLens?.setOnClickListener { shareLensImage() }` to `btnShareLens?.setOnClickListener { }`. Reinstall and rerun just the new test:
Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.FrmLensPerLensWidgetTest#shareButton_startsActHomeWithTheAutoExportExtra com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAILS (`captured` stays null).

Restore the real click listener, reinstall, rerun the same command.
Expected: `OK (1 test)`.

- [ ] **Step 7: Run the full unit suite and lint**

Run: `./gradlew testDevDebugUnitTest -q && ./gradlew lintDevDebug -q`
Expected: full suite passes (591 tests, unchanged), 0 lint errors.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/layout/frm_lens.xml app/src/main/java/com/mckimquyen/ui/FrmLens.kt \
  app/src/androidTest/java/com/mckimquyen/ui/FrmLensPerLensWidgetTest.kt
git commit -m "feat(lens-export): add FrmLens share button, relaying through ActHome"
```

---

### Task 5: Translate the 3 new strings into all 16 locales

**Files:**
- Modify: `app/src/main/res/values-{ar,de,es,fr,hi,in,it,ja,km,ko,lo,pt,ru,th,vi,zh}/strings.xml` (16 files)

**Interfaces:** None (resource-only; no code depends on translated text, only on the resource keys already added in Tasks 1 and 3).

- [ ] **Step 1: Insert the 3 translated strings into each locale file**

For every locale file below, find:

```xml
    <string name="lens_smart_focus_disable">...</string>
```

(the existing translated line - text varies per locale, already present) and insert immediately after it, before the file's closing `</resources>` (matching the exact insertion point used in the base `values/strings.xml` in Tasks 1 and 3):

**`values-vi/strings.xml`:**
```xml
    <string name="lens_share_caption_via">qua %1$s</string>
    <string name="lens_share_image">Chia sẻ ảnh lens</string>
    <string name="error_lens_share_failed">Không thể chia sẻ ảnh lens</string>
```

**`values-ja/strings.xml`:**
```xml
    <string name="lens_share_caption_via">%1$s より</string>
    <string name="lens_share_image">レンズ画像を共有</string>
    <string name="error_lens_share_failed">レンズ画像を共有できませんでした</string>
```

**`values-ko/strings.xml`:**
```xml
    <string name="lens_share_caption_via">%1$s 제공</string>
    <string name="lens_share_image">렌즈 이미지 공유</string>
    <string name="error_lens_share_failed">렌즈 이미지를 공유할 수 없습니다</string>
```

**`values-zh/strings.xml`:**
```xml
    <string name="lens_share_caption_via">来自 %1$s</string>
    <string name="lens_share_image">分享镜头图片</string>
    <string name="error_lens_share_failed">无法分享镜头图片</string>
```

**`values-de/strings.xml`:**
```xml
    <string name="lens_share_caption_via">über %1$s</string>
    <string name="lens_share_image">Linsenbild teilen</string>
    <string name="error_lens_share_failed">Linsenbild konnte nicht geteilt werden</string>
```

**`values-fr/strings.xml`:**
```xml
    <string name="lens_share_caption_via">via %1$s</string>
    <string name="lens_share_image">Partager l\'image de la lentille</string>
    <string name="error_lens_share_failed">Impossible de partager l\'image de la lentille</string>
```

**`values-es/strings.xml`:**
```xml
    <string name="lens_share_caption_via">vía %1$s</string>
    <string name="lens_share_image">Compartir imagen de la lente</string>
    <string name="error_lens_share_failed">No se pudo compartir la imagen de la lente</string>
```

**`values-it/strings.xml`:**
```xml
    <string name="lens_share_caption_via">tramite %1$s</string>
    <string name="lens_share_image">Condividi immagine della lente</string>
    <string name="error_lens_share_failed">Impossibile condividere l\'immagine della lente</string>
```

**`values-pt/strings.xml`:**
```xml
    <string name="lens_share_caption_via">via %1$s</string>
    <string name="lens_share_image">Compartilhar imagem da lente</string>
    <string name="error_lens_share_failed">Não foi possível compartilhar a imagem da lente</string>
```

**`values-ru/strings.xml`:**
```xml
    <string name="lens_share_caption_via">через %1$s</string>
    <string name="lens_share_image">Поделиться изображением объектива</string>
    <string name="error_lens_share_failed">Не удалось поделиться изображением объектива</string>
```

**`values-ar/strings.xml`:**
```xml
    <string name="lens_share_caption_via">عبر %1$s</string>
    <string name="lens_share_image">مشاركة صورة العدسة</string>
    <string name="error_lens_share_failed">تعذرت مشاركة صورة العدسة</string>
```

**`values-hi/strings.xml`:**
```xml
    <string name="lens_share_caption_via">%1$s के माध्यम से</string>
    <string name="lens_share_image">लेंस छवि साझा करें</string>
    <string name="error_lens_share_failed">लेंस छवि साझा नहीं की जा सकी</string>
```

**`values-th/strings.xml`:**
```xml
    <string name="lens_share_caption_via">ผ่าน %1$s</string>
    <string name="lens_share_image">แชร์ภาพเลนส์</string>
    <string name="error_lens_share_failed">ไม่สามารถแชร์ภาพเลนส์ได้</string>
```

**`values-in/strings.xml`:**
```xml
    <string name="lens_share_caption_via">melalui %1$s</string>
    <string name="lens_share_image">Bagikan gambar lensa</string>
    <string name="error_lens_share_failed">Gagal membagikan gambar lensa</string>
```

**`values-km/strings.xml`:**
```xml
    <string name="lens_share_caption_via">តាមរយៈ %1$s</string>
    <string name="lens_share_image">ចែករំលែករូបភាពកែវពង្រីក</string>
    <string name="error_lens_share_failed">មិនអាចចែករំលែករូបភាពកែវពង្រីកបានទេ</string>
```

**`values-lo/strings.xml`:**
```xml
    <string name="lens_share_caption_via">ຜ່ານ %1$s</string>
    <string name="lens_share_image">ແບ່ງປັນຮູບພາບເລນ</string>
    <string name="error_lens_share_failed">ບໍ່ສາມາດແບ່ງປັນຮູບພາບເລນໄດ້</string>
```

- [ ] **Step 2: Verify no missing translation and no lint regression**

Run: `./gradlew lintDevDebug -q`
Expected: 0 errors, and no new `MissingTranslation` warning for `lens_share_caption_via`, `lens_share_image` or `error_lens_share_failed` (this repo's lint config fails/warns on that - see the "Translated into all 16 locales" comment convention in `strings.xml`).

- [ ] **Step 3: Run the full unit suite**

Run: `./gradlew testDevDebugUnitTest -q`
Expected: full suite passes (591 tests, unchanged - resource-only change).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values-ar/strings.xml app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-hi/strings.xml app/src/main/res/values-in/strings.xml \
  app/src/main/res/values-it/strings.xml app/src/main/res/values-ja/strings.xml \
  app/src/main/res/values-km/strings.xml app/src/main/res/values-ko/strings.xml \
  app/src/main/res/values-lo/strings.xml app/src/main/res/values-pt/strings.xml \
  app/src/main/res/values-ru/strings.xml app/src/main/res/values-th/strings.xml \
  app/src/main/res/values-vi/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "i18n(lens-export): translate the 3 new polaroid-export strings to all 16 locales"
```

---

### Task 6: Full regression + manual smoke on the designated device

**Files:** None (verification only).

- [ ] **Step 1: Full unit + full instrumented regression**

Run: `./gradlew testDevDebugUnitTest -q`
Expected: full suite passes, report the exact count (should be 591: 580 pre-`LINT-009` baseline + 11 new unit tests from Task 1).

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest -q`
Then: `adb -s 115333744A005844 shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` (no `-e class` filter runs every instrumented test in the APK).
Expected: 0 failures beyond any already-known pre-existing flake - cross-check any red against the most recent `doc/task/done/*.md` entry's disclosed flakes (e.g. `p2-lint-lint-009-notifydatasetchanged.md`) before treating it as a new regression.

Run: `./gradlew lintDevDebug -q`
Expected: 0 errors.

- [ ] **Step 2: Manual smoke - `ActHome` long-press menu entry point**

On TECNO KJ7 (`115333744A005844`):
1. `adb -s 115333744A005844 shell am start -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActHome`
2. Long-press the lens page indicator (or empty grid space on a single-lens install) to open the management menu.
3. Confirm **"Share lens image"** appears as the last item.
4. Tap it. Confirm the system share sheet opens with a PNG preview and no crash.
5. **Stop and check for an ad per this repo's standing screenshot-test rule (R4)**: if any ad overlay is visible at any point in this flow, stop, note the ad type/position, and do not continue until it's dismissed.
6. Take a screenshot of the share sheet for the story file's evidence.

- [ ] **Step 3: Manual smoke - `FrmLens` button entry point + mid-pinch behavior**

1. Open Settings (`ActSettings`) → Lens tab.
2. Tap **"Share lens image"**. Confirm it navigates to `ActHome` and the share sheet opens automatically for the currently-active lens (no extra tap needed).
3. Back in `ActHome`, start a two-finger pinch on the lens grid and, **while still mid-pinch**, trigger the share menu another way is not possible mid-gesture by design - instead: pinch, release, then immediately long-press and share; confirm the exported image shows the idle (non-distorted, no HUD pill) layout, not a mid-gesture artifact.
4. Confirm the caption reads `<lens name>` / `via <app name>`.

- [ ] **Step 4: Write the story file**

Create `doc/task/done/p2-fish-fish-011-polaroid-lens-export.md` following the exact structure of `doc/task/done/p2-lint-lint-009-notifydatasetchanged.md` (fields table, Context, Investigation and changes, Required test matrix, Test evidence with real counts/output, Device policy note, Audit score table scored honestly against the README rubric). Update `doc/feature.md`'s Implemented section and `doc/task/README.md`'s Implemented list with a matching entry, and remove the now-shipped idea from `doc/feature.md`'s Ideas section (matching how FISH-010's entry was moved in the `p2-lint-lint-009` docs-sync fix earlier this session).

- [ ] **Step 5: Self-audit and push gate**

Score the round against `doc/task/README.md`'s rubric (Correctness 2.0 / Unit 1.5 / Widget 1.0 / Integration 1.5 / Smoke 1.0 / Security 1.0 / Performance-lifecycle 1.0 / Maintainability 1.0). Push only if the total is strictly greater than 9.0, matching this session's established push gate (see `feedback_step_done_gate` memory).

```bash
git add doc/task/done/p2-fish-fish-011-polaroid-lens-export.md doc/feature.md doc/task/README.md
git commit -m "docs(fish-011): close polaroid lens export story"
git push origin dev
```
