# FISH-039 — Focus Peaking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An opt-in bold outline (highlight colour, 4 dp) around every icon inside the lens' sharp zone, shown while a finger is down or focus is pinned (FISH-029), never idle or tilt.

**Architecture:** A pure `FocusPeaking` object (beside `DepthOfField` / `LensVignette`) decides *whether* and *how opaque*; `LensView.drawGrid` draws one `drawCircle` per sharp icon right after the icon, using one cached `Paint`. The outline's centre is the frame's visual focus centre (`focusCenter`: live touch, else the pin, else none), because `mTouchX/Y` is a sentinel while pinned. A new global boolean setting (`focus_peaking`, default off) follows the FISH-034 vignette pattern end to end.

**Tech Stack:** Kotlin/Java, Android Canvas, Room-free (SharedPreferences only), JUnit4/Robolectric and AndroidJUnit4. No new dependency.

## Global Constraints (spec: `docs/superpowers/specs/2026-11-06-fish-039-focus-peaking-design.md`)

- Branch `dev` only; no feature branch, no worktree. No push unless the owner asks. Do not stage `.idea/`. Commit trailer: `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
- Outline: every icon with `DepthOfField.focusDistance <= DepthOfField.FOCUS_ZONE` (0.2); one ring per icon, drawn right after the icon; works with depth-of-field on or off.
- Look: `UtilSettings.highlightColorInt()`, stroke **4 dp**, ring radius = icon half-width + `R.dimen.padding_focus_halo` (4 dp).
- When: only live touch (magnified lens) **or** pinned focus. Never idle, never tilt.
- Setting: own key `focus_peaking`, default **off**, in reset-to-defaults. FISH-024 `Show focus halo` and its thin ring are unchanged.
- No new allocation in `onDraw`: one reusable `Paint`, per-cell work is arithmetic plus one `drawCircle`.
- Never change geometry, hit-testing, launch, haptics, `LensGridCache`, DoF or vignette behavior.
- Clean mode does **not** hide it; reduced motion only snaps the touch fade (like DoF/vignette).
- Pinned without a touch: full alpha. Live touch: alpha = `round(255 * DepthOfField.transition(animationMultiplier, reduceMotion))`.
- Device per R3: run `adb devices -l`, lock exactly one device (`AskUserQuestion` if more than one), use `adb -s <serial>` for every command, never a Gradle `connected*` task. Before any install: back up main APK, test APK and `databases shared_prefs` tar, SHA-256 every data member, record font/animator/transition/window scales, app locale and HOME resolver. Restore by pushing the tar to `/data/local/tmp` and extracting with `run-as … tar xf` (never `exec-out … tar xf -`, it hangs), then compare every hash and setting; stop if not exact.
- adb smoke uses `input swipe x y x y 80` or slow `input swipe … 3000` drags; never `input tap` on icons. If `AdActivity` covers the UI: stop and wait for the owner's `done` (R4); never tap "Start (Ads may appear)".
- R5: named constants, no `late`/`!!` without a justified comment, a test for every behavior. Every behavior change: write the test, run it, watch it fail for the expected reason, write the minimum code, rerun.

---

## File Map

- Create `app/src/main/java/com/mckimquyen/views/FocusPeaking.kt` — pure decision math.
- Create `app/src/test/java/com/mckimquyen/views/FocusPeakingTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` — key, default, getter branch.
- Create `app/src/test/java/com/mckimquyen/util/UtilSettingsFocusPeakingTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/ui/settings/ResetEntries.kt` and `app/src/test/java/com/mckimquyen/ui/settings/ResetEntriesTest.kt`.
- Modify `app/src/main/res/layout/frm_settings.xml`, `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt` — switch row.
- Modify `app/src/main/res/values/strings.xml` + 16 `values-<locale>/strings.xml` — two strings.
- Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsFocusPeakingWidgetTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt` — flag, paint, drawing, test probes, sampling.
- Create `app/src/androidTest/java/com/mckimquyen/views/LensViewFocusPeakingWidgetTest.kt` (touch path, software canvas) and `LensViewFocusPeakingIntegrationTest.kt` (pinned/tilt on an attached view, BLUR on real `ActHome`).
- Evidence: `doc/task/AUDIT_FISH-039_2026-11-06.md`, `doc/feature.md`, `doc/task/README.md`; story `todo/` → `inprogress/` (Task 1) → `done/`.

## Interfaces

```kotlin
package com.mckimquyen.views

object FocusPeaking {
    const val STROKE_DP = 4f
    const val OPAQUE_ALPHA = DepthOfField.OPAQUE_ALPHA
    fun shouldApply(enabled: Boolean, hasFocusCentre: Boolean, lensVisible: Boolean): Boolean
    fun isSharp(focusDistance: Float): Boolean                 // finite && <= DepthOfField.FOCUS_ZONE
    fun alpha(pinnedOnly: Boolean, transition: Float): Int     // pinnedOnly -> 255; else round(255 * clamp(transition)), non-finite -> 0
}
```

`UtilSettings`: `const val KEY_FOCUS_PEAKING = "focus_peaking"`, `const val DEFAULT_FOCUS_PEAKING = false`.

`LensView` (all `@VisibleForTesting internal`): `var focusPeakingEnabled: Boolean`, `val focusPeakingRingsForTest: Int` (rings drawn in the last frame), `val focusPeakingAlphaForTest: Int` (alpha used in the last frame, 0 when off), `fun focusPeakingStrokePxForTest(): Float`.

Strings: `setting_focus_peaking`, `setting_focus_peaking_hint`. View ids: `rlSwitchFocusPeakingParent`, `iconFocusPeaking`, `swFocusPeaking`, `tvFocusPeakingHint`.

---

## Task 1 — Pure decision math

**Files:** create `FocusPeaking.kt`, `FocusPeakingTest.kt`; move the story to `inprogress/`.

- [ ] **Step 1: Move the story.** `git branch --show-current` must print `dev`. Then `git mv doc/task/todo/p3-fish-fish-039-focus-peaking-low-vision.md doc/task/inprogress/p3-fish-fish-039-focus-peaking-low-vision.md`, set `| Status | inprogress |`, and point the `FISH-039` row in `doc/task/README.md` at `inprogress/…`.
- [ ] **Step 2: RED.** Create `app/src/test/java/com/mckimquyen/views/FocusPeakingTest.kt`:

```kotlin
package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPeakingTest {
    @Test fun strokeIsFourDp() = assertEquals(4f, FocusPeaking.STROKE_DP, 0f)

    @Test fun anIconAtTheFocusPointAndOnTheZoneEdgeIsSharp() {
        assertTrue(FocusPeaking.isSharp(0f))
        assertTrue(FocusPeaking.isSharp(DepthOfField.FOCUS_ZONE))
    }

    @Test fun anIconJustOutsideTheZoneOrFarAwayIsNotSharp() {
        assertFalse(FocusPeaking.isSharp(DepthOfField.FOCUS_ZONE + 0.001f))
        assertFalse(FocusPeaking.isSharp(1f))
    }

    @Test fun nonFiniteDistanceIsNeverSharp() {
        assertFalse(FocusPeaking.isSharp(Float.NaN))
        assertFalse(FocusPeaking.isSharp(Float.POSITIVE_INFINITY))
        assertFalse(FocusPeaking.isSharp(Float.NEGATIVE_INFINITY))
    }

    @Test fun theOutlineAppliesOnlyWhenEnabledWithAFocusCentreOnTheAppsLens() {
        for (enabled in listOf(false, true)) for (centre in listOf(false, true)) for (visible in listOf(false, true)) {
            val expected = enabled && centre && visible
            assertEquals("enabled=$enabled centre=$centre visible=$visible", expected,
                FocusPeaking.shouldApply(enabled, centre, visible))
        }
    }

    @Test fun aPinnedFocusWithoutATouchIsFullyOpaqueWhateverTheTransition() {
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = true, transition = 0f))
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = true, transition = 0.5f))
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = true, transition = Float.NaN))
    }

    @Test fun aLiveTouchFollowsTheLensTransition() {
        assertEquals(0, FocusPeaking.alpha(pinnedOnly = false, transition = 0f))
        assertEquals(128, FocusPeaking.alpha(pinnedOnly = false, transition = 0.5f))
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = false, transition = 1f))
    }

    @Test fun theTransitionIsClampedAndNonFiniteHidesTheOutline() {
        assertEquals(0, FocusPeaking.alpha(pinnedOnly = false, transition = -2f))
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = false, transition = 9f))
        assertEquals(0, FocusPeaking.alpha(pinnedOnly = false, transition = Float.NaN))
        assertEquals(0, FocusPeaking.alpha(pinnedOnly = false, transition = Float.POSITIVE_INFINITY))
    }

    @Test fun theTouchAlphaNeverFallsAsTheTransitionRises() {
        var previous = -1
        var t = 0f
        while (t <= 1.0001f) {
            val a = FocusPeaking.alpha(pinnedOnly = false, transition = t)
            assertTrue("alpha $a at $t must not drop below $previous", a >= previous)
            previous = a
            t += 0.05f
        }
    }
}
```

- [ ] **Step 3: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.views.FocusPeakingTest' > /tmp/f039-red1.txt 2>&1; grep -E "^e: " /tmp/f039-red1.txt | head`. Expected: compile errors only, `Unresolved reference 'FocusPeaking'`.
- [ ] **Step 4: GREEN.** Create `app/src/main/java/com/mckimquyen/views/FocusPeaking.kt`:

```kotlin
package com.mckimquyen.views

import kotlin.math.roundToInt

/**
 * FISH-039: focus peaking for low vision. Pure decision math only. The outline is one circle per icon drawn right after
 * the icon, so it never touches geometry or hit-testing. "Sharp" reuses [DepthOfField.FOCUS_ZONE] so the outlined cluster
 * is exactly the cluster depth-of-field keeps sharp, with depth-of-field on or off.
 */
object FocusPeaking {
    /** Thicker than the FISH-024 halo (2.5 dp) so it reads for low vision. */
    const val STROKE_DP = 4f
    const val OPAQUE_ALPHA = DepthOfField.OPAQUE_ALPHA

    /** Needs the setting, a visual focus centre (live touch or pin; never idle or tilt) and the apps lens. */
    fun shouldApply(enabled: Boolean, hasFocusCentre: Boolean, lensVisible: Boolean): Boolean =
        enabled && hasFocusCentre && lensVisible

    /** True inside the focus zone; non-finite distances fail closed (no outline). */
    fun isSharp(focusDistance: Float): Boolean =
        focusDistance.isFinite() && focusDistance <= DepthOfField.FOCUS_ZONE

    /**
     * A pinned focus has no lens animation, so it is fully opaque. With a live touch the outline fades with the same
     * transition as the lens (which snaps under reduced motion). Non-finite transition hides it.
     */
    fun alpha(pinnedOnly: Boolean, transition: Float): Int {
        if (pinnedOnly) return OPAQUE_ALPHA
        if (!transition.isFinite()) return 0
        return (OPAQUE_ALPHA * transition.coerceIn(0f, 1f)).roundToInt()
    }
}
```

- [ ] **Step 5: Verify GREEN.** Rerun the class; all 9 tests pass (read `app/build/test-results/testDevDebugUnitTest/TEST-com.mckimquyen.views.FocusPeakingTest.xml` for `tests="9" failures="0"`).
- [ ] **Step 6: Commit.** `git branch --show-current` (must be `dev`), `git add app/src/main/java/com/mckimquyen/views/FocusPeaking.kt app/src/test/java/com/mckimquyen/views/FocusPeakingTest.kt doc/task/inprogress doc/task/todo doc/task/README.md && git commit -m "feat(fish-039): add focus peaking decision math"` with the trailer.

---

## Task 2 — Setting, reset entry and Settings switch

**Files:** modify `UtilSettings.kt`, `ResetEntries.kt`, `ResetEntriesTest.kt`, `frm_settings.xml`, `FrmSettings.kt`, `values/strings.xml` + 16 locale strings; create `UtilSettingsFocusPeakingTest.kt`, `FrmSettingsFocusPeakingWidgetTest.kt`.

**Interfaces:** consumes `FocusPeaking` only at render time (Task 3); produces `UtilSettings.KEY_FOCUS_PEAKING`, `DEFAULT_FOCUS_PEAKING`, resource IDs `rlSwitchFocusPeakingParent`, `iconFocusPeaking`, `swFocusPeaking`, `tvFocusPeakingHint`, string IDs `setting_focus_peaking`, `setting_focus_peaking_hint`.

- [ ] **Step 1: RED (settings).** Create `app/src/test/java/com/mckimquyen/util/UtilSettingsFocusPeakingTest.kt` with the exact FISH-034 setup pattern:

```kotlin
package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsFocusPeakingTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun focusPeakingIsOffByDefault() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_FOCUS_PEAKING))
        assertFalse(UtilSettings.DEFAULT_FOCUS_PEAKING)
    }

    @Test fun enablingAndDisablingPersists() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_FOCUS_PEAKING, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_FOCUS_PEAKING))
        settings.save(UtilSettings.KEY_FOCUS_PEAKING, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_FOCUS_PEAKING))
    }

    @Test fun theKeyIsStable() = assertEquals("focus_peaking", UtilSettings.KEY_FOCUS_PEAKING)
}
```

- [ ] **Step 2: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsFocusPeakingTest'`. Expected compile failure only: `Unresolved reference KEY_FOCUS_PEAKING / DEFAULT_FOCUS_PEAKING`.
- [ ] **Step 3: GREEN (settings and reset).** In `UtilSettings.kt`, beside `DEFAULT_LENS_VIGNETTE` / `KEY_LENS_VIGNETTE`, add `const val DEFAULT_FOCUS_PEAKING = false` and `const val KEY_FOCUS_PEAKING = "focus_peaking"`; add `KEY_FOCUS_PEAKING -> prefs.getBoolean(name, DEFAULT_FOCUS_PEAKING)` to the same boolean `when` branch. In `ResetEntries.kt`, add `flag(UtilSettings.KEY_FOCUS_PEAKING, R.string.setting_focus_peaking, UtilSettings.DEFAULT_FOCUS_PEAKING)` beside the FISH-034 flag. In `ResetEntriesTest.kt`, add `"focus_peaking"` to the settings key set.
- [ ] **Step 4: Verify GREEN.** Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsFocusPeakingTest' --tests 'com.mckimquyen.ui.settings.ResetEntriesTest'` (3 + all existing ResetEntries tests pass).
- [ ] **Step 5: RED (Settings widget).** Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsFocusPeakingWidgetTest.kt`, mirroring `FrmSettingsLensVignetteWidgetTest` exactly with identifiers switched to FISH-039. Four tests: default switch unchecked; saved true is checked; toggle persists; `onDefaultsReset()` unchecks and persists false. Use `DeviceStateGuard`, `PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()` in `@Before/@After`, and `launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar)`.
- [ ] **Step 6: Verify RED.** `./gradlew compileDevDebugAndroidTestKotlin`. Expected resource errors only: `R.id.swFocusPeaking` unresolved.
- [ ] **Step 7: GREEN (layout and fragment).**
  - `frm_settings.xml`: the vignette row is followed by one `MaterialDivider` and then the FISH-006 Smart Focus row (`<!-- FISH-006: local open-count Smart Focus, opt-in -->`). Insert the block below **between that divider and the FISH-006 comment**, and then add one more `MaterialDivider` (same attributes) after it, so the order is: vignette, divider, peaking, divider, Smart Focus.

```xml
                <!-- FISH-039: focus peaking, opt-in -->
                <RelativeLayout
                    android:id="@+id/rlSwitchFocusPeakingParent"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:paddingVertical="8dp">

                    <ImageView
                        android:id="@+id/iconFocusPeaking"
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:layout_alignParentStart="true"
                        android:layout_centerVertical="true"
                        android:contentDescription="@string/app_name"
                        android:padding="8dp"
                        android:src="@drawable/ic_center_focus_24dp" />

                    <com.google.android.material.materialswitch.MaterialSwitch
                        android:id="@+id/swFocusPeaking"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:layout_toEndOf="@id/iconFocusPeaking"
                        android:paddingTop="12dp"
                        android:text="@string/setting_focus_peaking"
                        android:textColor="?android:attr/textColorSecondary"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:thumb="@drawable/sw_thumb_ios"
                        app:switchPadding="16dp"
                        app:track="@drawable/sw_track_ios" />

                    <TextView
                        android:id="@+id/tvFocusPeakingHint"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_below="@id/swFocusPeaking"
                        android:layout_marginStart="12dp"
                        android:layout_marginEnd="16dp"
                        android:layout_toEndOf="@id/iconFocusPeaking"
                        android:paddingBottom="8dp"
                        android:text="@string/setting_focus_peaking_hint"
                        android:textColor="?android:attr/textColorTertiary"
                        android:textSize="12sp" />

                </RelativeLayout>

                <com.google.android.material.divider.MaterialDivider
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="52dp"
                    app:dividerColor="?attr/colorOutlineVariant" />
```

  - `FrmSettings.kt` has five edits, each mirroring the vignette line next to it:
    1. field `private var swFocusPeaking: SwitchCompat? = null` beside `swLensVignette` (line ~67);
    2. bind `swFocusPeaking = view.findViewById(R.id.swFocusPeaking)` beside line ~151;
    3. clear the row click: `view.findViewById<View>(R.id.rlSwitchFocusPeakingParent).setOnClickListener(null)` beside line ~223;
    4. listener `swFocusPeaking?.setOnCheckedChangeListener { _, isChecked -> utilSettings?.save(UtilSettings.KEY_FOCUS_PEAKING, isChecked) }` beside line ~289, and in `assignValues()` `swFocusPeaking?.isChecked = us.getBoolean(UtilSettings.KEY_FOCUS_PEAKING)` beside line ~436;
    5. `onDestroyView`: `swFocusPeaking = null` beside line ~639.
  - The switch is independent of `KEY_SHOW_FOCUS_HALO` and Clean mode; do not touch their dependency logic.
- [ ] **Step 8: Add 17 locale strings.** Add `setting_focus_peaking` and `setting_focus_peaking_hint` in base + `ar de es fr hi in it ja km ko lo pt ru th vi zh`. Use the strings below (base entries are English; translations are AI-written, audit will say so):

| locale | `setting_focus_peaking` | `setting_focus_peaking_hint` |
|---|---|---|
| base | Focus peaking | Outlines icons in the sharp focus area while touching or focus is pinned. Uses the highlight colour. Off by default. |
| ar | تحديد الحواف البؤرية | يحدد الأيقونات في منطقة التركيز الحادة أثناء اللمس أو تثبيت التركيز. يستخدم لون التمييز. معطل افتراضياً. |
| de | Fokus-Peaking | Umrandet Symbole im scharfen Fokusbereich bei Berührung oder fixiertem Fokus. Verwendet die Hervorhebungsfarbe. Standardmäßig aus. |
| es | Resalte de enfoque | Delinea los iconos del área nítida al tocar o fijar el enfoque. Usa el color de resaltado. Desactivado de forma predeterminada. |
| fr | Accentuation de mise au point | Entoure les icônes de la zone nette pendant le toucher ou lorsque la mise au point est verrouillée. Utilise la couleur de surbrillance. Désactivé par défaut. |
| hi | फ़ोकस पीकिंग | स्पर्श या फ़ोकस लॉक होने पर तीखे फ़ोकस क्षेत्र के आइकन की रूपरेखा दिखाता है। हाइलाइट रंग का उपयोग करता है। डिफ़ॉल्ट रूप से बंद। |
| in | Penajaman fokus | Menggarisbawahi ikon di area fokus tajam saat disentuh atau fokus dikunci. Menggunakan warna sorotan. Nonaktif secara default. |
| it | Focus peaking | Delinea le icone nell'area nitida quando tocchi o blocchi il fuoco. Usa il colore evidenziato. Disattivato per impostazione predefinita. |
| ja | フォーカスピーキング | タッチ中またはフォーカス固定中に、ピントの合った領域のアイコンを囲みます。ハイライト色を使用します。初期設定ではオフです。 |
| km | ការបន្លិចតំបន់ច្បាស់ | គូសស៊ុមរូបតំណាងក្នុងតំបន់ច្បាស់ ពេលប៉ះ ឬចាក់សោការផ្ដោត។ ប្រើពណ៌រំលេច។ បិទតាមលំនាំដើម។ |
| ko | 포커스 피킹 | 터치하거나 초점을 고정할 때 선명한 초점 영역의 아이콘을 둘러쌉니다. 강조 색상을 사용합니다. 기본값은 꺼짐입니다. |
| lo | ການເນັ້ນຈຸດໂຟກັດ | ຂີດຂອບໄອຄອນໃນບໍລິເວນຊັດເມື່ອແຕະ ຫຼື ລັອກໂຟກັດ. ໃຊ້ສີເນັ້ນ. ປິດໄວ້ໂດຍຄ່າເລີ່ມຕົ້ນ. |
| pt | Realce de foco | Contorna ícones na área nítida ao tocar ou fixar o foco. Usa a cor de destaque. Desativado por padrão. |
| ru | Выделение резкой области | Обводит значки в резкой области при касании или фиксации фокуса. Использует цвет выделения. По умолчанию выключено. |
| th | โฟกัสพีกกิง | แสดงเส้นขอบไอคอนในบริเวณที่คมชัดเมื่อแตะหรือล็อกโฟกัส ใช้สีไฮไลต์ ปิดไว้โดยค่าเริ่มต้น |
| vi | Viền vùng nét | Viền biểu tượng trong vùng nét khi chạm hoặc ghim tiêu điểm. Dùng màu nhấn. Mặc định tắt. |
| zh | 峰值对焦 | 触摸或锁定焦点时，为清晰对焦区域中的图标添加轮廓。使用高亮颜色。默认关闭。 |

- [ ] **Step 9: Verify GREEN.** Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsFocusPeakingTest' --tests 'com.mckimquyen.ui.settings.ResetEntriesTest' --tests 'com.mckimquyen.a11y.AllStringsTranslationTest' --tests 'com.mckimquyen.a11y.LensStringTranslationTest'`; assemble `assembleDevDebugAndroidTest`. After Task 4's backup, run `FrmSettingsFocusPeakingWidgetTest` on the locked device; expect `OK (4 tests)`.
- [ ] **Step 10: Commit.** Stage only settings, reset, layout, fragment, strings and these tests; commit `feat(fish-039): add focus peaking setting` with trailer.

---

## Task 3 — Draw the outline in `LensView`

**Files:** modify `LensView.kt`; create `LensViewFocusPeakingWidgetTest.kt` (touch path on a bare view, software canvas), `LensViewFocusPeakingIntegrationTest.kt` (pinned / tilt / flag sampling on an attached view, plus the hardware BLUR path on the real `ActHome`), and `app/src/test/java/com/mckimquyen/views/FocusPeakingNoAllocationTest.kt`.

**Interfaces:** consumes `FocusPeaking.{shouldApply, isSharp, alpha, STROKE_DP, OPAQUE_ALPHA}` (Task 1) and `UtilSettings.KEY_FOCUS_PEAKING` (Task 2); produces `LensView.focusPeakingEnabled`, `focusPeakingRingsForTest` (Int), `focusPeakingAlphaForTest` (Int), `focusPeakingRingedForTest` (BooleanArray, one slot per cell, true when that cell was outlined in the last frame), `focusPeakingStrokePxForTest()`.

**Design (read before coding).**
- One decision per frame, before the cell loop: `peakingOn = FocusPeaking.shouldApply(focusPeakingEnabled, focusCenter != null, mDrawType == DrawType.APPS)`; `peakingAlpha = FocusPeaking.alpha(pinnedOnly = !hasActiveTouch, transition = dofTransition)`. Nothing is drawn when `peakingAlpha == 0` (touch just landed, transition still 0).
- `focusCenter` already means "live touch wins, else the pin, else null" and is null for idle and tilt, so tilt never outlines.
- Per cell, right after `drawAppIcon(iconCanvas, rect, currentIndex)`: `DepthOfField.focusDistance(focusCenter.x, focusCenter.y, baseRect.centerX(), baseRect.centerY(), width, height)`; if `FocusPeaking.isSharp(...)` draw one circle centred on the magnified `rect`, radius `rect.width()/2 + R.dimen.padding_focus_halo`. A sharp icon is band 0, so drawing on `iconCanvas` keeps the ring sharp on the hardware BLUR path too.
- One extra `Paint` (`mPaintFocusPeaking`), STROKE, width `FocusPeaking.STROKE_DP * density`, created in `setupPaints()`. Per frame only its colour/alpha change. The colour is the one `onDraw` already resolved for `mPaintNewAppTag` this frame (`mPaintNewAppTag.color`), so no second preference parse and no extra string work in the hot path.
- Flag sampling: read in `LensAnimation.init` (live touch) and again in `toggleFocusLockAt` when a pin is made (a pin has no animation). The field is a plain `Boolean`, never read per frame from SharedPreferences.
- No effect on `mTouchX/Y`, `mSelectIndex`, rects, haptics, `LensGridCache`, DoF or the vignette.

### Part A — RED: tests

- [ ] **Step 1: JVM allocation guard.** Create `app/src/test/java/com/mckimquyen/views/FocusPeakingNoAllocationTest.kt`. It reads the production source and checks that the two new hot-path functions contain no allocation tokens (the existing `drawGrid` already allocates a `RectF` when an icon is selected, so only the new functions are inspected):

```kotlin
package com.mckimquyen.views

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** FISH-039: the focus-peaking hot path must not allocate; checked on the source of the two new functions. */
class FocusPeakingNoAllocationTest {
    private val source: String by lazy {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        val file = sequenceOf(
            File(dir, "src/main/java/com/mckimquyen/views/LensView.kt"),
            File(dir, "app/src/main/java/com/mckimquyen/views/LensView.kt")
        ).first { it.isFile }
        file.readText()
    }

    private fun bodyOf(signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature not found in LensView.kt", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        var i = open
        while (i < source.length) {
            if (source[i] == '{') depth++
            if (source[i] == '}') { depth--; if (depth == 0) return source.substring(open, i + 1) }
            i++
        }
        error("unbalanced braces after $signature")
    }

    private val forbidden = listOf("Paint(", "RectF(", "Rect(", "Path(", "ArrayList", "listOf(", "mutableListOf", "String.format", "Array(")

    @Test fun theRingDrawingFunctionDoesNotAllocate() {
        val body = bodyOf("private fun drawFocusPeakingRing(")
        forbidden.forEach { assertTrue("drawFocusPeakingRing must not contain '$it'", !body.contains(it)) }
        assertTrue("it draws exactly one circle per call", body.split("drawCircle(").size == 2)
    }

    @Test fun thePaintPreparationFunctionDoesNotAllocate() {
        val body = bodyOf("private fun prepareFocusPeakingPaint(")
        forbidden.forEach { assertTrue("prepareFocusPeakingPaint must not contain '$it'", !body.contains(it)) }
    }
}
```

- [ ] **Step 2: Widget test, touch path.** Create `app/src/androidTest/java/com/mckimquyen/views/LensViewFocusPeakingWidgetTest.kt`. It reuses the FISH-034 bare-view harness (software `Bitmap` canvas, `setLensStateForTest`):

```kotlin
package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-039 touch-path proof on a bare LensView drawn into a software canvas (the DoF ALPHA fallback path). */
@RunWith(AndroidJUnit4::class)
class LensViewFocusPeakingWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 800
        const val APP_COUNT = 25
        const val ICON_PX = 48
        const val PARTIAL_ANIMATION = 0.3f
        const val FULL_ANIMATION = 1f
        const val OPAQUE = 255
        const val IDLE = -Float.MAX_VALUE
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val iconBitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private lateinit var lensView: LensView // assigned in setUp before every test

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish039.app$i", name = "App$i", label = "App $i", iconCacheKey = "fish039#$i")
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
        com.mckimquyen.util.BitmapCache.clear()
    }

    private fun render(): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { lensView.draw(Canvas(bitmap)) }
        return bitmap
    }

    private fun highlightPixels(bitmap: Bitmap): Int {
        val highlight = UtilSettings(context).highlightColorInt() or (OPAQUE shl 24)
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        bitmap.recycle()
        return pixels.count { it == highlight }
    }

    private fun touchAt(x: Float, y: Float, animation: Float = FULL_ANIMATION, reduceMotion: Boolean = false) =
        lensView.setLensStateForTest(x, y, animation, reduceMotion)

    /** Centre of a real cell (never a gap between icons); the first render computes the grid cache. */
    private fun cellCentre(index: Int): Pair<Float, Float> {
        render().recycle()
        val bounds = android.graphics.Rect()
        var found = false
        instrumentation.runOnMainSync { found = lensView.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    private fun touchAtCell(index: Int = APP_COUNT / 2, animation: Float = FULL_ANIMATION, reduceMotion: Boolean = false) {
        val (x, y) = cellCentre(index)
        touchAt(x, y, animation, reduceMotion)
    }

    @Test fun defaultIsOffSoNoRingIsDrawn() {
        touchAtCell()
        render().recycle()
        assertEquals(0, lensView.focusPeakingRingsForTest)
        assertEquals(false, lensView.focusPeakingEnabled)
    }

    @Test fun withTheSettingOnOnlyTheSharpClusterUnderTheFingerIsOutlined() {
        lensView.focusPeakingEnabled = true
        touchAtCell()
        render().recycle()
        val rings = lensView.focusPeakingRingsForTest
        assertTrue("at least the icon under the finger is outlined, was $rings", rings >= 1)
        assertTrue("far icons are not outlined ($rings of $APP_COUNT)", rings < APP_COUNT)
    }

    @Test fun aCornerCellTouchOutlinesOnlyTheIconsNearThatCorner() {
        lensView.focusPeakingEnabled = true
        touchAtCell(0)
        render().recycle()
        val corner = lensView.focusPeakingRingsForTest
        assertTrue("corner cluster is small but not empty, was $corner", corner in 1 until APP_COUNT / 2)
    }

    @Test fun ringsAreActuallyPaintedInTheHighlightColour() {
        touchAtCell()
        val off = highlightPixels(render())
        lensView.focusPeakingEnabled = true
        val on = highlightPixels(render())
        assertTrue("the 4dp outline adds highlight-coloured pixels ($on vs $off)", on > off)
    }

    @Test fun theOutlineIsThickerThanTheThinFocusHalo() {
        val density = context.resources.displayMetrics.density
        assertEquals(FocusPeaking.STROKE_DP * density, lensView.focusPeakingStrokePxForTest(), 0.01f)
        assertTrue(lensView.focusPeakingStrokePxForTest() > context.resources.getDimension(R.dimen.stroke_width_focus_halo))
    }

    @Test fun anIdleLensDrawsNoRingEvenWhenEnabled() {
        lensView.focusPeakingEnabled = true
        touchAt(IDLE, IDLE, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.focusPeakingRingsForTest)
    }

    @Test fun aTouchWhoseTransitionIsStillZeroDrawsNothingYet() {
        lensView.focusPeakingEnabled = true
        touchAtCell(animation = 0f)
        render().recycle()
        assertEquals(0, lensView.focusPeakingAlphaForTest)
        assertEquals(0, lensView.focusPeakingRingsForTest)
    }

    @Test fun theOutlineFadesWithTheLensAndSnapsUnderReducedMotion() {
        lensView.focusPeakingEnabled = true
        touchAtCell(animation = PARTIAL_ANIMATION)
        render().recycle()
        assertEquals(FocusPeaking.alpha(pinnedOnly = false, transition = PARTIAL_ANIMATION), lensView.focusPeakingAlphaForTest)
        touchAtCell(animation = PARTIAL_ANIMATION, reduceMotion = true)
        render().recycle()
        assertEquals("reduced motion snaps to full", OPAQUE, lensView.focusPeakingAlphaForTest)
    }

    @Test fun cleanLensModeDoesNotHideTheAccessibilityAid() {
        UtilSettings(context).save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        lensView.focusPeakingEnabled = true
        touchAtCell()
        render().recycle()
        assertTrue(lensView.focusPeakingRingsForTest >= 1)
    }

    @Test fun depthOfFieldOnOrOffTheSameClusterIsOutlined() {
        lensView.focusPeakingEnabled = true
        touchAtCell()
        lensView.depthOfFieldEnabled = false
        render().recycle()
        val without = lensView.focusPeakingRingsForTest
        lensView.depthOfFieldEnabled = true
        render().recycle()
        assertEquals(without, lensView.focusPeakingRingsForTest)
        assertTrue(without >= 1)
    }

    @Test fun geometryAndHitTestAreIdenticalWithAndWithoutTheOutline() {
        val step = SIZE_PX / 8
        for (x in step until SIZE_PX step step) for (y in step until SIZE_PX step step) {
            touchAt(x.toFloat(), y.toFloat())
            lensView.focusPeakingEnabled = false
            render().recycle()
            val rectsOff = lensView.drawnRectsForTest()
            val indexOff = lensView.selectedIndexForTest
            val touchOff = lensView.touchCoordinatesForTest()
            lensView.focusPeakingEnabled = true
            render().recycle()
            assertEquals(rectsOff, lensView.drawnRectsForTest())
            assertEquals(indexOff, lensView.selectedIndexForTest)
            assertEquals(touchOff, lensView.touchCoordinatesForTest())
        }
    }

    @Test fun theGridCacheIsNotRecomputedByOutlineFrames() {
        val field = LensView::class.java.getDeclaredField("mGridCache").apply { isAccessible = true }
        val cache = field.get(lensView) as LensGridCache
        touchAtCell()
        lensView.focusPeakingEnabled = false
        render().recycle()
        val recomputes = cache.recomputeCount
        lensView.focusPeakingEnabled = true
        repeat(5) { render().recycle() }
        assertEquals(recomputes, cache.recomputeCount)
    }

    @Test fun afterATouchedFrameAnIdleFrameDrawsNoRing() {
        lensView.focusPeakingEnabled = true
        touchAtCell()
        render().recycle()
        assertTrue(lensView.focusPeakingRingsForTest >= 1)
        touchAt(IDLE, IDLE, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.focusPeakingRingsForTest)
    }
}
```

- [ ] **Step 3: Integration test, pinned focus, tilt and flag sampling.** Create `app/src/androidTest/java/com/mckimquyen/views/LensViewFocusPeakingIntegrationTest.kt`. The harness is the FISH-029 `LensViewFocusLockIntegrationTest` one (an attached view in `OrganizationHarnessActivity`, 1000×1000, `draw(Canvas())`), with 25 apps so a cluster exists:

```kotlin
package com.mckimquyen.views

import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewFocusPeakingIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 1_000
        const val APP_COUNT = 25
        const val OPAQUE = 255
        const val DRAG_STEPS = 12
        const val STEP_DELAY_MS = 16L
        const val SETTLE_TIMEOUT_MS = 3_000L
        const val POLL_MS = 50L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun withView(block: (LensView) -> Unit) {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(ArrayList((0 until APP_COUNT).map { i ->
                        App(id = i, packageName = "com.fish039.int$i", name = "Int$i", label = "Int $i")
                    }))
                    layoutParams = FrameLayout.LayoutParams(SIZE_PX, SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, SIZE_PX, SIZE_PX)
                    draw(Canvas())
                }
            }
            instrumentation.waitForIdleSync()
            block(requireNotNull(view) { "assigned in onActivity above" })
        }
    }

    private fun draw(view: LensView) = instrumentation.runOnMainSync { view.draw(Canvas()) }
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    /** Centre of a real cell (never a gap); withView already drew once, so the grid cache exists. */
    private fun cellCentre(view: LensView, index: Int = APP_COUNT / 2): Pair<Float, Float> {
        val bounds = android.graphics.Rect()
        var found = false
        onMain { found = view.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    /** Pins (or, when already pinned, releases) focus on the middle icon: pinning is a toggle. */
    private fun toggleFocus(view: LensView) {
        val (x, y) = cellCentre(view)
        onMain { assertTrue(view.toggleFocusLockAtForTest(x, y)) }
    }

    /**
     * Pinning re-samples the stored setting (a pin has no lens animation), so a test that pins must enable the setting
     * in the store as well as on the field; DeviceStateGuard restores the stored value afterwards.
     */
    private fun enablePeaking(view: LensView) {
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_PEAKING, true)
        onMain { view.focusPeakingEnabled = true }
    }

    @Test fun aPinnedFocusWithoutATouchOutlinesTheClusterAtFullOpacityThenReleasing() = withView { view ->
        enablePeaking(view)
        toggleFocus(view)
        draw(view)
        assertTrue("pinned cluster is outlined", view.focusPeakingRingsForTest >= 1)
        assertTrue("but not the whole grid", view.focusPeakingRingsForTest < APP_COUNT)
        assertEquals(OPAQUE, view.focusPeakingAlphaForTest)
        toggleFocus(view)   // second toggle releases
        draw(view)
        assertEquals("released: no ring", 0, view.focusPeakingRingsForTest)
    }

    @Test fun aPinnedFocusWithTheSettingOffDrawsNothing() = withView { view ->
        onMain { view.focusPeakingEnabled = false }
        toggleFocus(view)
        draw(view)
        assertEquals(0, view.focusPeakingRingsForTest)
    }

    @Test fun theSettingIsSampledWhenFocusIsPinnedSoNoLensAnimationIsNeeded() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_PEAKING, true)
        assertFalse("the view has not seen the setting yet", view.focusPeakingEnabled)
        toggleFocus(view)
        assertTrue("pinning samples the stored setting", view.focusPeakingEnabled)
        draw(view)
        assertTrue(view.focusPeakingRingsForTest >= 1)
    }

    @Test fun tiltNeverOutlinesAnything() = withView { view ->
        enablePeaking(view)
        onMain { view.setTiltOffset(120f, 80f) }
        draw(view)
        assertEquals("tilt has no real focus centre", 0, view.focusPeakingRingsForTest)
    }

    @Test fun aLiveTouchWinsOverThePinAndTheRingFollowsTheFinger() = withView { view ->
        enablePeaking(view)
        toggleFocus(view)
        draw(view)
        val pinned = view.focusPeakingRingedForTest.copyOf()
        assertTrue("pinned cluster is outlined", pinned.any { it })

        // Put the lens in a live-touch state on the first (corner) cell. The lens animation only advances when the
        // window draws, so the test sets the multiplier directly, as the FISH-034 widget tests do.
        val (tx, ty) = cellCentre(view, 0)
        onMain { view.setLensStateForTest(tx, ty, 1f, reduceMotion = false) }
        draw(view)
        val touching = view.focusPeakingRingedForTest.copyOf()
        assertTrue("a ring is drawn while touching", touching.any { it })
        assertTrue("the corner cell is ringed under the finger", touching[0])
        assertFalse("the ringed icons moved with the finger, away from the pinned cluster", touching.contentEquals(pinned))
    }

    @Test fun detachAndPauseClearThePinSoTheRingGoesAway() = withView { view ->
        enablePeaking(view)
        toggleFocus(view)
        draw(view)
        assertTrue(view.focusPeakingRingsForTest >= 1)
        onMain { view.clearFocusLockForPause() }
        draw(view)
        assertEquals(0, view.focusPeakingRingsForTest)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test fun onTheRealHomeWithTheHardwareBlurPathTheSharpClusterKeepsItsRings() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        RAppsSingleton.instance.apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish039.home$i", name = "Home$i", label = "Home $i")
        })
        UtilSettings(context).save(UtilSettings.KEY_DEPTH_OF_FIELD, true)
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_PEAKING, true)
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var found: LensView? = null
                scenario.onActivity { found = it.findViewById(R.id.lensViews) }
                val view = requireNotNull(found) { "assigned in onActivity above" }
                instrumentation.waitForIdleSync()
                var w = 0f
                var h = 0f
                onMain { w = view.width.toFloat(); h = view.height.toFloat() }
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, x: Float, y: Float) = onMain {
                    val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    view.dispatchTouchEvent(e)
                    e.recycle()
                }
                send(MotionEvent.ACTION_DOWN, w / 4, h / 4)
                for (i in 1..DRAG_STEPS) {
                    send(MotionEvent.ACTION_MOVE, w / 4 + w / 2 * i / DRAG_STEPS, h / 4 + h / 2 * i / DRAG_STEPS)
                    SystemClock.sleep(STEP_DELAY_MS)
                }
                val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
                var ok = false
                while (SystemClock.uptimeMillis() < deadline && !ok) {
                    onMain { ok = view.dofLastMode == DepthOfField.Mode.BLUR && view.focusPeakingRingsForTest >= 1 }
                    if (!ok) SystemClock.sleep(POLL_MS)
                }
                assertTrue("BLUR path active and the band-0 cluster is still outlined", ok)
                send(MotionEvent.ACTION_CANCEL, w * 3 / 4, h * 3 / 4)
            }
        } finally {
            prefs.edit().clear().commit()
            RAppsSingleton.instance.clearAllData()
        }
    }
}
```

- [ ] **Step 4: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.views.FocusPeakingNoAllocationTest' > /tmp/f039-red3.txt 2>&1` fails because the two function signatures do not exist yet (`not found in LensView.kt`). `./gradlew compileDevDebugAndroidTestKotlin` fails with unresolved `focusPeakingEnabled`, `focusPeakingRingsForTest`, `focusPeakingAlphaForTest`, `focusPeakingStrokePxForTest`, `focusPeakingRingedForTest`. Compile and assertion errors for those names only; fix typos in the tests, never by weakening an assertion.

### Part B — GREEN: production code in `LensView.kt` (anchors verified against the current file)

- [ ] **Step 5: State, probes and flag.** Next to `internal var lensVignetteEnabled` (line ~830) add:

```kotlin
    /** FISH-039: sampled in LensAnimation.init (live touch) and in toggleFocusLockAt (a pin has no animation). */
    @androidx.annotation.VisibleForTesting
    internal var focusPeakingEnabled = UtilSettings.DEFAULT_FOCUS_PEAKING
    private var mPaintFocusPeaking: Paint? = null
    private var mFocusPeakingRings = 0
    private var mFocusPeakingAlpha = 0
    private var mFocusPeakingRinged = BooleanArray(0)

    /** Rings drawn in the last frame. */
    @androidx.annotation.VisibleForTesting
    internal val focusPeakingRingsForTest: Int get() = mFocusPeakingRings

    /** Alpha the rings used in the last frame; 0 when none were drawn. */
    @androidx.annotation.VisibleForTesting
    internal val focusPeakingAlphaForTest: Int get() = mFocusPeakingAlpha

    /** One slot per cell: true when that cell was outlined in the last frame. */
    @androidx.annotation.VisibleForTesting
    internal val focusPeakingRingedForTest: BooleanArray get() = mFocusPeakingRinged

    @androidx.annotation.VisibleForTesting
    internal fun focusPeakingStrokePxForTest(): Float = mPaintFocusPeaking?.strokeWidth ?: 0f
```

- [ ] **Step 6: Paint.** In `setupPaints()`, directly after the `mPaintFocusHalo = Paint().apply { … }` block, add:

```kotlin
        mPaintFocusPeaking = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = FocusPeaking.STROKE_DP * resources.displayMetrics.density
            mUtilSettings?.let { color = it.highlightColorInt() }
        }
```

- [ ] **Step 7: Two allocation-free helpers.** Beside `drawFocusHalo` add exactly these two functions (their names and shape are what `FocusPeakingNoAllocationTest` inspects: no allocation tokens, and exactly one `drawCircle` in the ring function):

```kotlin
    /** FISH-039: one frame's colour and alpha; the colour is the highlight onDraw already resolved this frame. */
    private fun prepareFocusPeakingPaint(alpha: Int) {
        val paint = mPaintFocusPeaking ?: return
        paint.color = mPaintNewAppTag?.color ?: paint.color
        paint.alpha = alpha
    }

    /** FISH-039: bold outline around one sharp icon; reuses one Paint, no per-frame allocation. */
    private fun drawFocusPeakingRing(canvas: Canvas, rect: RectF) {
        val paint = mPaintFocusPeaking ?: return
        canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f + mFocusPeakingPaddingPx, paint)
    }
```

  `mFocusPeakingPaddingPx` is `private var mFocusPeakingPaddingPx = 0f`, set once in `setupPaints()` to `resources.getDimension(R.dimen.padding_focus_halo)` (the same 4 dp the FISH-024 halo uses), so the per-cell code does no resource lookup.

- [ ] **Step 8: `drawGrid` — one decision per frame.** Directly after the `vignetteOn` block (the one ending `lensVignetteEnabled, mDrawType == DrawType.APPS && hasActiveTouch && dofTransition > 0f`) add:

```kotlin
        // FISH-039: needs a visual focus centre (live touch or pin), so idle and tilt never outline; a pin has no
        // transition, so it is opaque, while a live touch follows the lens transition (snapping under reduced motion).
        val peakingOn = FocusPeaking.shouldApply(focusPeakingEnabled, focusCenter != null, mDrawType == DrawType.APPS)
        val peakingAlpha = if (peakingOn) FocusPeaking.alpha(pinnedOnly = !hasActiveTouch, transition = dofTransition) else 0
        val peakingDraw = peakingOn && peakingAlpha > 0
        if (peakingDraw) prepareFocusPeakingPaint(peakingAlpha)
        mFocusPeakingRings = 0
        mFocusPeakingAlpha = if (peakingDraw) peakingAlpha else 0
        if (mFocusPeakingRinged.size != baseRects.size) mFocusPeakingRinged = BooleanArray(baseRects.size)
        mFocusPeakingRinged.fill(false)
```

- [ ] **Step 9: `drawGrid` — per cell.** Right after `drawAppIcon(iconCanvas, rect, currentIndex)` (line ~2158, inside `if (mDrawType == DrawType.APPS)`), add:

```kotlin
                if (peakingDraw && focusCenter != null) {
                    val peakDistance = DepthOfField.focusDistance(
                        focusCenter.x, focusCenter.y, baseRect.centerX(), baseRect.centerY(),
                        width.toFloat(), height.toFloat()
                    )
                    if (FocusPeaking.isSharp(peakDistance)) {
                        drawFocusPeakingRing(iconCanvas, rect)
                        mFocusPeakingRings++
                        if (currentIndex < mFocusPeakingRinged.size) mFocusPeakingRinged[currentIndex] = true
                    }
                }
```

  `focusCenter` is the existing `VisualFocusCenter?` local; Kotlin smart-casts it inside the `!= null` check, and it is read, not allocated, per cell. Drawing on `iconCanvas` keeps a band-0 ring sharp on the hardware BLUR path.

- [ ] **Step 10: Sampling.** (a) In `LensAnimation.init`, after the vignette read:

```kotlin
            focusPeakingEnabled = mUtilSettings?.getBoolean(UtilSettings.KEY_FOCUS_PEAKING)
                ?: UtilSettings.DEFAULT_FOCUS_PEAKING
```

  (b) In `toggleFocusLockAt`, inside the `else` branch that creates the pin (after `mFocusLockY = y`), add `focusPeakingEnabled = mUtilSettings?.getBoolean(UtilSettings.KEY_FOCUS_PEAKING) ?: UtilSettings.DEFAULT_FOCUS_PEAKING` before `invalidate()`. (c) In `onDetachedFromWindow`, beside the other state resets, set `mFocusPeakingRings = 0` and `mFocusPeakingAlpha = 0`. Do not null `mPaintFocusPeaking`: paints are built once in `init`, and a re-attached ViewPager page must still draw. A re-attached view re-reads the flag on its next lens animation or pin.

- [ ] **Step 11: Verify GREEN (JVM).** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.views.FocusPeakingNoAllocationTest' --tests 'com.mckimquyen.views.FocusPeakingTest'` — 11 tests pass. Then `./gradlew assembleDevDebug assembleDevDebugAndroidTest` must compile every new probe.
- [ ] **Step 12: Verify GREEN (device, after Task 4's backup).** Run `LensViewFocusPeakingWidgetTest` (13 tests), `LensViewFocusPeakingIntegrationTest` (7 tests; the `@SdkSuppress` BLUR test runs on API 31+), plus the regression set: `LensViewVignetteWidgetTest`, `LensViewDepthOfFieldWidgetTest`, `LensViewDepthOfFieldIntegrationTest`, `LensViewFocusLockIntegrationTest`, `LensViewTiltIntegrationTest`, `LensViewReattachIntegrationTest`, `LensViewWidgetTest`, `LensViewFocusRingIntegrationTest`, `LensViewPinchIntegrationTest`, `LensViewPagerGestureIntegrationTest`, `LensViewRingRotateIntegrationTest`, `ActHomeRingRotateGestureSmokeTest`. Every class must print `OK (N)` with N > 0.
- [ ] **Step 13: Mutation checks (each reverted afterwards, then rebuilt).** (1) Make `FocusPeaking.isSharp` return `true` always: `withTheSettingOnOnlyTheSharpClusterUnderTheFingerIsOutlined` and the corner test must fail. (2) Remove the `focusCenter != null` term from `peakingOn`: `tiltNeverOutlinesAnything` and `anIdleLensDrawsNoRingEvenWhenEnabled` must fail. (3) Drop the sampling line in `toggleFocusLockAt`: `theSettingIsSampledWhenFocusIsPinnedSoNoLensAnimationIsNeeded` must fail. Record which tests failed in the audit.
- [ ] **Step 14: Commit.** `git branch --show-current` must be `dev`; stage `LensView.kt` and the three new test files; commit `feat(fish-039): outline the sharp focus cluster` with the trailer.

---

## Task 4 — Device procedure: lock, backup, regression, frame budget, smoke, restore

Per R3 the device is **not assumed**. Every command below uses `$S`, the serial locked in Step 1. Never use bare `adb`, a Gradle `connected*` task or `installDevDebug`. Each command is run on its own and its output file is read afterwards (a truncated terminal display does not mean the command stopped).

- [ ] **Step 1: Lock one device.** `adb devices -l`. If exactly one device is listed, announce "Đang dùng device <model> <serial>". If more than one, ask with `AskUserQuestion` once, then lock that serial for the whole session; never address another device, including for cleanup.

```bash
S=<locked-serial>
D=/tmp/f039-run-$(date +%Y%m%d-%H%M%S); mkdir -p "$D"; echo "$D" > /tmp/f039-dir; echo "$S" > /tmp/f039-serial
adb -s $S shell getprop ro.product.model; adb -s $S shell getprop ro.build.version.release
```

- [ ] **Step 2: Backup before the first install.** A fresh backup every run; never reuse an older directory as the baseline.

```bash
D=$(cat /tmp/f039-dir); S=$(cat /tmp/f039-serial)
MAIN=$(adb -s $S shell pm path com.mckimquyen.lenslauncher | sed 's/package://' | tr -d '\r')
TEST=$(adb -s $S shell pm path com.mckimquyen.lenslauncher.test | sed 's/package://' | tr -d '\r')
adb -s $S pull "$MAIN" "$D/before.apk" > "$D/pull1.txt" 2>&1
adb -s $S pull "$TEST" "$D/before-test.apk" > "$D/pull2.txt" 2>&1
adb -s $S exec-out run-as com.mckimquyen.lenslauncher tar cf - databases shared_prefs > "$D/before-data.tar"
{ adb -s $S shell settings get system font_scale; adb -s $S shell settings get global animator_duration_scale; \
  adb -s $S shell settings get global transition_animation_scale; adb -s $S shell settings get global window_animation_scale; \
  adb -s $S shell cmd locale get-app-locales com.mckimquyen.lenslauncher; \
  adb -s $S shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME; } > "$D/device-state.txt"
mkdir -p "$D/x" && tar -xf "$D/before-data.tar" -C "$D/x"
( cd "$D/x" && find . -type f | sort | xargs shasum -a 256 ) > "$D/before-hashes.txt"
shasum -a 256 "$D/before.apk" "$D/before-test.apk" > "$D/apk-hashes.txt"
wc -l < "$D/before-hashes.txt"; cat "$D/apk-hashes.txt" "$D/device-state.txt"
```

  Record the file count, both APK hashes and the six system values in the audit. Reading the tar with `exec-out … tar cf -` is safe; only **writing** with `exec-out … tar xf -` hangs.
- [ ] **Step 3: Host gate (no device).** `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, then `bash scripts/check-secrets.sh` and `git diff --check`. Record the unit count (baseline 1575 + the 9 + 3 + 2 new JVM tests), lint errors/warnings (baseline 0/15), exit codes and `git rev-parse --short HEAD`.
- [ ] **Step 4: Install and run the classes.**

```bash
D=$(cat /tmp/f039-dir); S=$(cat /tmp/f039-serial)
adb -s $S install -r -d app/build/outputs/apk/dev/debug/*.apk > "$D/inst1.txt" 2>&1
adb -s $S install -r -d app/build/outputs/apk/androidTest/dev/debug/*.apk > "$D/inst2.txt" 2>&1
adb -s $S shell pm clear com.mckimquyen.lenslauncher.test
for c in views.LensViewFocusPeakingWidgetTest views.LensViewFocusPeakingIntegrationTest ui.FrmSettingsFocusPeakingWidgetTest \
         views.LensViewVignetteWidgetTest ui.FrmSettingsLensVignetteWidgetTest views.LensViewDepthOfFieldWidgetTest views.LensViewDepthOfFieldIntegrationTest \
         views.LensViewFocusLockIntegrationTest views.LensViewTiltIntegrationTest views.LensViewReattachIntegrationTest views.LensViewWidgetTest \
         views.LensViewFocusRingIntegrationTest views.LensViewPinchIntegrationTest views.LensViewPagerGestureIntegrationTest \
         views.LensViewRingRotateIntegrationTest ui.ActHomeRingRotateGestureSmokeTest; do
  adb -s $S shell input keyevent KEYCODE_HOME
  adb -s $S shell am instrument -w -e class com.mckimquyen.$c com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner > "$D/run-$c.txt" 2>&1
  echo "$c: $(grep -E '^OK|FAILURES|Tests run' "$D/run-$c.txt" | tr '\n' ' ')" >> "$D/summary.txt"
done; cat "$D/summary.txt"
```

  `OK (0 tests)` means a stale test APK and is a failure: rebuild `assembleDevDebugAndroidTest` and reinstall. If a class fails with `the device rejected MotionEvent` or `pointers already down`, an earlier crashed run left the injector's virtual finger down: rerun once (the smoke class releases it in `@Before`). If another app's dialog covers Home (seen on the Pixel with an unrelated app's 16 KB warning), `am force-stop` that app only after confirming it is not the project, and rerun.
- [ ] **Step 5: Frame budget (acceptance criterion).** Same method as FISH-034. Enabling the setting is one switch tap in Settings, done with `input swipe x y x y 80` (never `input tap`); get `x y` from `uiautomator dump` bounds of `swFocusPeaking`, scaled to device pixels.

```bash
D=$(cat /tmp/f039-dir); S=$(cat /tmp/f039-serial)
adb -s $S shell am start -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActHome; sleep 3
adb -s $S shell dumpsys gfxinfo com.mckimquyen.lenslauncher reset
for i in 1 2 3 4 5; do adb -s $S shell "input swipe 200 700 900 1500 1200; input swipe 900 1500 200 700 1200"; done
adb -s $S shell dumpsys gfxinfo com.mckimquyen.lenslauncher > "$D/gfx-off.txt"
```

  Enable **Focus peaking** in Settings, return to Home and repeat the identical loop into `gfx-on.txt`. Extract `Total frames rendered`, `Janky frames`, and the 50th/90th/95th/99th percentile lines from both. A run is valid only with more than 1600 frames each (FISH-034 got 1689/1688); otherwise repeat it and say so. Report honestly: one run per state, device on USB power, no claim for other devices or API levels. If gfxinfo cannot give valid frames, record that instead of a pass.
- [ ] **Step 6: Visual smoke (adb) with a 3 s drag.** With the setting off take `v0-idle.png`; slow diagonal drag `adb -s $S shell "input swipe 300 1200 1100 2000 3000"` in the background and `screencap` at 1.6 s into `v1-pan-off.png`. Enable the setting; repeat into `v2-pan-on.png`; release and capture `v3-released-on.png`. Compare with Pillow over the grid area only (crop the status/navigation bars): `v2` differs from `v1` in a ring of highlight-coloured pixels around the cluster under the finger and **not** at far icons; `v3` returns to `v0` (0 changed pixels). Zoom a crop of `v2` to check the ring is around the sharp icons only. Stop immediately if `AdActivity` appears (R4) and wait for the owner's `done`; never tap "Start (Ads may appear)". The **pinned** state is proven by `LensViewFocusPeakingIntegrationTest`, not by adb: `input swipe` cannot make a reliable double-tap pin (measured on FISH-035), so do not claim a pinned screenshot unless the FISH-035 `UiAutomation` approach was added.
- [ ] **Step 7: Restore and prove it exact.**

```bash
D=$(cat /tmp/f039-dir); S=$(cat /tmp/f039-serial)
adb -s $S shell am force-stop com.mckimquyen.lenslauncher
adb -s $S push "$D/before-data.tar" /data/local/tmp/f039-before-data.tar > /dev/null
adb -s $S shell chmod 644 /data/local/tmp/f039-before-data.tar
adb -s $S shell "run-as com.mckimquyen.lenslauncher sh -c 'rm -rf databases shared_prefs && tar xf /data/local/tmp/f039-before-data.tar && echo EXTRACT_OK'"
adb -s $S shell rm -f /data/local/tmp/f039-before-data.tar
adb -s $S install -r -d "$D/before.apk"; adb -s $S install -r -d "$D/before-test.apk"
adb -s $S shell am force-stop com.mckimquyen.lenslauncher
adb -s $S exec-out run-as com.mckimquyen.lenslauncher tar cf - databases shared_prefs > "$D/restored-data.tar"
mkdir -p "$D/y" && tar -xf "$D/restored-data.tar" -C "$D/y"
( cd "$D/y" && find . -type f | sort | xargs shasum -a 256 ) > "$D/restored-hashes.txt"
diff "$D/before-hashes.txt" "$D/restored-hashes.txt" && echo DATA_HASHES_IDENTICAL
adb -s $S pull "$(adb -s $S shell pm path com.mckimquyen.lenslauncher | sed 's/package://' | tr -d '\r')" "$D/after.apk" > /dev/null
adb -s $S pull "$(adb -s $S shell pm path com.mckimquyen.lenslauncher.test | sed 's/package://' | tr -d '\r')" "$D/after-test.apk" > /dev/null
shasum -a 256 "$D/after.apk" "$D/after-test.apk" | awk '{print $1}' | diff - <(awk '{print $1}' "$D/apk-hashes.txt") && echo APK_HASHES_IDENTICAL
{ adb -s $S shell settings get system font_scale; adb -s $S shell settings get global animator_duration_scale; \
  adb -s $S shell settings get global transition_animation_scale; adb -s $S shell settings get global window_animation_scale; \
  adb -s $S shell cmd locale get-app-locales com.mckimquyen.lenslauncher; \
  adb -s $S shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME; } > "$D/restored-state.txt"
diff "$D/device-state.txt" "$D/restored-state.txt" && echo STATE_IDENTICAL
```

  Stop and disclose if any hash, setting or the HOME resolver differs. Do not open the app again after the final restore (launching Home rewrites some SDK prefs files and breaks the comparison).

---

## Task 5 — Audit, trackers, closure

**Files:** create `doc/task/AUDIT_FISH-039_2026-11-06.md`; modify `doc/feature.md`, `doc/task/README.md`, the story file.

- [ ] **Step 1: Audit document.** Sections: scope; evidence table (JVM total and new tests per class, lint/build/secrets/diff, every device class with `OK (N)`, mutation results from Task 3 Step 13, frame budget numbers or the explicit "no valid frames" statement, restore hashes); defects found by RED tests; smoke result; limitations. Limitations to state unless disproved: ring colour is the user highlight colour so it can be low-contrast on a matching wallpaper (4 dp, no halo behind it); one gfxinfo run per state; the pinned state is covered by instrumented tests, not an adb screenshot; AI-written translations; owner has not used it by hand; magnified neighbours drawn later can overlap an earlier ring; no TalkBack change by design.
- [ ] **Step 2: Score honestly** out of 10. The story moves `inprogress/` → `done/` only if the score is above 9.0, every device class showed `OK (N>0)` and the restore was proven exact; otherwise it stays in `inprogress/` with the gap listed. The owner decides closure; ask with `AskUserQuestion`.
- [ ] **Step 3: Trackers (R2).** `doc/feature.md`: replace the "📋 Picked" FISH-039 bullet with the final status, counts, audit link and limitations. `doc/task/README.md`: update the `FISH-039` row. Tick the acceptance criteria in the story file with the evidence (opt-in outline, no new allocation in `onDraw` via `FocusPeakingNoAllocationTest`, tests + smoke images).
- [ ] **Step 4: Commit** the audit and tracker changes as their own commit on `dev`. Never stage `.idea/`. Do not push; pushing and a PR to `master` happen only if the owner asks.

---

## Self-review

**Spec coverage** (`docs/superpowers/specs/2026-11-06-fish-039-focus-peaking-design.md`):
- Outline for every icon in the sharp zone, DoF on or off → Task 1 `isSharp`, Task 3 Step 9, widget test `depthOfFieldOnOrOffTheSameClusterIsOutlined`.
- Highlight colour, 4 dp, radius = half-width + halo padding → Task 3 Steps 6–7, tests `ringsAreActuallyPaintedInTheHighlightColour`, `theOutlineIsThickerThanTheThinFocusHalo`.
- Only while touching or pinned; never idle or tilt → Task 3 Step 8 (`focusCenter != null`), tests `anIdleLens…`, `tiltNeverOutlinesAnything`, `aPinnedFocus…`, `aLiveTouchWins…`.
- Pinned fully opaque; touch follows transition; reduced motion snaps; Clean mode does not hide → Task 1 `alpha`, Task 3 tests `theOutlineFadesWithTheLensAndSnapsUnderReducedMotion`, `cleanLensModeDoesNotHideTheAccessibilityAid`, `aTouchWhoseTransitionIsStillZero…`.
- Own switch, default off, in reset, FISH-024 unchanged → Task 2 (settings, reset entry, Settings row, 17 locales, widget test).
- Flag sampled on lens animation and on pin, not per frame → Task 3 Step 10, test `theSettingIsSampledWhenFocusIsPinned…`.
- No allocation in `onDraw` → Task 3 Steps 6–9 (one Paint, no per-cell objects) and `FocusPeakingNoAllocationTest`.
- Geometry, hit-test, `LensGridCache`, DoF, vignette untouched → tests `geometryAndHitTestAreIdentical…`, `theGridCacheIsNotRecomputed…`, vignette/DoF regression classes in Task 4.
- Hardware BLUR path keeps rings on band 0 → integration test `onTheRealHomeWithTheHardwareBlurPath…` (API 31+).
- Frame budget, smoke, backup and restore → Task 4. Audit and trackers → Task 5.

**Placeholder scan:** no `TBD`/`TODO`/comment-only test bodies; every test has assertions. The Task 2 Settings widget test is specified by "mirror the FISH-034 test with identifiers switched", whose four test bodies are named and sourced from `FrmSettingsLensVignetteWidgetTest.kt`, an existing file the executor copies.

**Type consistency:** `FocusPeaking.{STROKE_DP, OPAQUE_ALPHA, shouldApply, isSharp, alpha}`; `UtilSettings.{KEY_FOCUS_PEAKING, DEFAULT_FOCUS_PEAKING}`; `LensView.{focusPeakingEnabled, focusPeakingRingsForTest, focusPeakingAlphaForTest, focusPeakingRingedForTest, focusPeakingStrokePxForTest}` and private `prepareFocusPeakingPaint`, `drawFocusPeakingRing`, `mFocusPeakingPaddingPx`; ids `rlSwitchFocusPeakingParent`, `iconFocusPeaking`, `swFocusPeaking`, `tvFocusPeakingHint`; strings `setting_focus_peaking`, `setting_focus_peaking_hint`. Counts: `FocusPeakingTest` 9, `UtilSettingsFocusPeakingTest` 3, `FocusPeakingNoAllocationTest` 2, widget 13, integration 7, Settings widget 4.

**Known risks the executor must watch:** (1) the ring colour is the highlight colour, so contrast against the wallpaper is not guaranteed — recorded as a limitation, not solved here; (2) the touch-path widget tests rely on `getAppBounds` after one render, so keep `cellCentre()` rendering first; (3) the BLUR integration test needs API 31+ and a hardware canvas, so it is skipped elsewhere and must not be reported as run there; (4) `LensView` is already large: do not extract the ring code in this story, only note it if it grows further.

**Execution handoff** (after the plan commit): ask the owner with `AskUserQuestion` — subagent-driven vs inline (FISH-029/034/035 were inline) — and do not start Task 1 before they answer.
