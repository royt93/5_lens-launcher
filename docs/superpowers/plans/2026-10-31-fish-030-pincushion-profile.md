# FISH-030 Pincushion Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-lens Pincushion profile that reverses Fisheye's displacement while preserving positive distortion storage and all existing profile behavior.

**Architecture:** Extend `LensProfile` with a direction-aware mapping while keeping Fisheye on the exact current `UtilCalculator` path. For Pincushion, reflect the computed shifted/scaled coordinate around the lens point (`L - (f - L)`); apply this before the same `LensHood` falloff and use the resulting rect for both drawing and hit-test. Reuse existing per-lens profile preference and physics-backup field; no new key/schema/negative distortion.

**Tech Stack:** Kotlin enum/policy, Java `UtilCalculator`, Android Material `MaterialButtonToggleGroup`, Room-independent SharedPreferences, `LensPhysicsBackup`, JUnit4/Robolectric, AndroidJUnitRunner.

## Global Constraints

- Device: TECNO KJ7 `115333744A005844` only; never target Pixel/S24U.
- No Ads/IAP/monetization, no new TalkBack actions, and no change to unrelated accessibility scope.
- Pincushion is a fourth stored `LensProfile`; existing missing/unknown profiles stay Fisheye.
- Keep `KEY_DISTORTION_FACTOR` positive in its current range; no negative values, no new strength key, and no additional physics preset.
- No Room migration/schema change and no new dependency.
- Preserve existing Fisheye, Anamorphic, Cylindrical, hood, reduced-motion, draw and hit-test results.
- Run device instrumentation only with `adb -s 115333744A005844`; never use a Gradle `connected*` task.
- Never stage `.idea/`. Stop immediately if an ad covers a smoke screenshot; do not dismiss/consent for owner.
- Gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`; backup/restore TECNO, compare all data-file hashes and locale/font/permissions. Do not push without explicit authorization; audit must be >9.0 to qualify.

## File Map

- Modify `app/src/main/java/com/mckimquyen/util/LensProfile.kt`: add `PINCUSHION`; implement a small shared reflection transform for coordinates and pass it only in that profile's warped X/Y paths.
- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt` only if the profile API needs the reflection helper; retain one per-frame settings read and same `mScratchRect` for render/hit-test.
- Modify `app/src/main/java/com/mckimquyen/ui/LensSettingsActivity.kt` and `app/src/main/res/layout/act_lens_settings.xml`: add fourth profile button and bind its existing enum value; slider and hood stay unchanged.
- Modify all 17 translated `app/src/main/res/values*/strings.xml` files: add one short Pincushion label and one concise description; follow `LensStringTranslationTest` and `AllStringsTranslationTest`.
- Modify `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt` only if its current allow-list rejects `PINCUSHION`; preserve current `lensProfile` JSON shape and reject unknown names.
- Modify `app/src/test/java/com/mckimquyen/util/LensProfileTest.kt`, `app/src/test/java/com/mckimquyen/model/LayoutBackupPhysicsTest.kt`, and `app/src/androidTest/java/com/mckimquyen/views/LensViewProfileIntegrationTest.kt`, `app/src/androidTest/java/com/mckimquyen/ui/LensSettingsActivityWidgetTest.kt`.
- Move `doc/task/todo/p3-fish-fish-030-negative-distortion-pincushion.md` to `inprogress/` at start; update story, README, feature tracker, and add `doc/task/AUDIT_FISH-030_2026-10-31.md` after verification.

## Task 1: Pure profile mapping (RED → GREEN)

**Interface:** `LensProfile` retains existing `shiftX/Y` and `scaleX/Y` behavior for Fisheye/Anamorphic/Cylindrical. Add a pure helper (exact name may follow the existing enum style) that reflects a warped coordinate about lens center: `lens - (warped - lens)`. Only `PINCUSHION` calls this helper; `LensProfile.sizeFor` keeps the same profile axis policy (Pincushion warps both axes).

- [ ] **Step 1: Write tests first** in `LensProfileTest.kt`: for item points on both sides of lens center, assert Pincushion output equals `lens - (FisheyeOutput - lens)` for both shift and scale; assert lens-center maps to itself; idle sentinel returns original position; `FULL`/partial hood remain symmetric; width/height stay positive; Fisheye outputs equal existing legacy methods exactly; Anamorphic/Cylindrical pin their current axes; unknown/missing stored name resolves to Fisheye.
- [ ] **Step 2: Run RED.** `./gradlew testDevDebugUnitTest --tests '*LensProfileTest'`; expected compile/test failure because `PINCUSHION` and reflected operations do not exist.
- [ ] **Step 3: Implement minimally** in `LensProfile.kt`: extend enum axis configuration with explicit `reverseDirection` (default false for old enum entries); add private pure `reflect(lens, warped)`; in each warped shift/scale method, call existing `UtilCalculator` first, then reflect only for Pincushion. Do not pass negative distortion.
- [ ] **Step 4: Run GREEN.** Same test command. Confirm old expected values stay bit-identical; specifically keep Fisheye dispatch as a direct return from the legacy helper.
- [ ] **Step 5: Mutation check.** Mutate the reflection sign and disable reflection in shift and scale; named tests must fail. Restore production source before proceeding.

## Task 2: Backup compatibility

**Interface:** Existing `LensPhysicsBackup.lensProfile: String?` stores enum names; no JSON key/schema change.

- [ ] **Step 1: Add RED tests** in `LayoutBackupPhysicsTest.kt`: `PINCUSHION` round-trips; old backup with no `lensProfile` parses to `null`; unknown profile name and wrong JSON type reject the physics block; existing three profiles still parse.
- [ ] **Step 2: Run RED.** `./gradlew testDevDebugUnitTest --tests '*LayoutBackupPhysicsTest'`; expected failure on parser allow-list if it currently names the three old values.
- [ ] **Step 3: Implement** the smallest parser change: accept `LensProfile.entries` (or the explicit four stored names), retaining rejection of all unknown values. Do not alter positive distortion bounds.
- [ ] **Step 4: Run GREEN** plus `./gradlew testDevDebugUnitTest --tests '*WorkspaceBackupPhysicsRoundTripTest' --tests '*LensProfileSettingsTest'`.
- [ ] **Step 5: Mutation check** remove Pincushion from accepted names; backup round-trip must fail. Restore source.

## Task 3: Lens Settings UI and translations

**Interface:** Add a fourth button `btLensProfilePincushion`; selection uses existing `KEY_LENS_PROFILE` and updates only the target lens.

- [ ] **Step 1: Add a widget test first** in `LensSettingsActivityWidgetTest.kt`: tap Pincushion; wait until target preference is `PINCUSHION`; assert default lens profile is unchanged; recreate Activity; assert Pincushion remains checked and recreate caused no extra write. Keep existing hoodie slider state unchanged.
- [ ] **Step 2: Run RED.** `./gradlew compileDevDebugAndroidTestKotlin`; expected missing id/resource failure.
- [ ] **Step 3: Add** one button in the existing vertical `MaterialButtonToggleGroup` in `act_lens_settings.xml`; add `buttonForProfile`/`profileForButton` mapping in `LensSettingsActivity.kt`. Do not add a new preference key.
- [ ] **Step 4: Add exactly two new `lens_profile_*` strings** to base and all 16 locale files, concise label “Pincushion” and description that it reverses displacement in both axes. Translation must not be left English verbatim where localized text differs; keep any percent/format placeholders identical.
- [ ] **Step 5: Run GREEN:** widget test, `LensStringTranslationTest`, `AllStringsTranslationTest`.
- [ ] **Step 6: Mutation check:** change Pincushion button to save Fisheye or write the default lens key; the widget test must fail. Restore source.

## Task 4: LensView render and hit-test integration

**Contract:** Pincushion must reflect each Fisheye warped center across lens position on both axes. The final rectangle must remain the same object used by both `drawAppIcon` and selection/hit-test. Hood falloff occurs after directional reflection; Full hood must exactly match the reflected Fisheye result.

- [ ] **Step 1: Add integration tests first** in `LensViewProfileIntegrationTest.kt`: Pincushion on both sides of the touch center; shifted and scaled centers follow reflection; FULL and partial hood; same rendered rect is hit-test rect; idle sentinel and multiplier 0 stay at base; all icons retain positive finite sizes. Also assert existing profiles remain unchanged.
- [ ] **Step 2: Run RED.** `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest`, then `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewProfileIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`; expected named Pincushion assertions fail before integration.
- [ ] **Step 3: Wire** only through `LensProfile` from Task 1; do not fork a new drawing/hit-test path or alter grid geometry/cache.
- [ ] **Step 4: Run GREEN** on TECNO; verify the existing Fisheye/Anamorphic/Cylindrical tests also pass.
- [ ] **Step 5: Mutation check** disable reflection in `LensView` dispatch and swap reflection direction; integration tests must fail. Restore source.

## Task 5: Gate, smoke, audit, close

- [ ] **Step 1: Full gate** with exact command in Global Constraints. Record unit counts, lint warning count and APK SHA/size.
- [ ] **Step 2: Backup TECNO KJ7** (`115333744A005844`) app/test APK, all app-data members and hashes, locale/font/permissions/foreground. Do not touch Pixel.
- [ ] **Step 3: Install and run targeted regression** one class at a time on TECNO: `LensViewProfileIntegrationTest`, `LensSettingsActivityWidgetTest`, `LensManagementActivityWidgetTest`, `LensViewPinchIntegrationTest`, `LensViewSmartFocusIntegrationTest`, `LensHoodTest` host, `LensHoodSettingsTest` host, `LayoutBackupPhysicsTest`, `WorkspaceBackupPhysicsRoundTripTest`, plus relevant Settings/restore tests.
- [ ] **Step 4: Real smoke on TECNO:** use Lens Settings on default lens; select Fisheye, then Pincushion; keep distortion positive (e.g. 2.5) and hood Full, drag on the grid from both sides of the center; capture screenshots showing center expansion/perimeter recession and confirm no inversion at zero offset. Switch back Fisheye, compare against pre-change baseline; verify default lens stays unchanged on another lens. **If any screenshot shows an ad, stop immediately and wait for owner `done`.**
- [ ] **Step 5: Restore** APK/data, compare every file SHA, locale/font/permissions and foreground; remove orphan test prefs. No screenshot itself is evidence of restoration.
- [ ] **Step 6: Audit** `AUDIT_FISH-030_2026-10-31.md`, score honestly; close story and update README/feature. Commit only after final gate and diff review; never stage `.idea/`. Do not push without an explicit current request.

## Self-review

- Spec user behavior: Task 1 adds separate profile; Task 4 reflects both shifted/scaled centers and retains same rect for draw/hit-test; Task 3 exposes it in Lens Settings; Task 2 covers backup compatibility.
- Compatibility: no negative distortion, new strength key, Room change, or preset; old profile names/default and physics import bounds are tested.
- Scope: no Ads/IAP/monetization, TalkBack actions, or unrelated accessibility work.
- Device: all physical device commands use TECNO KJ7 `115333744A005844` only; no Gradle `connected*` commands.
