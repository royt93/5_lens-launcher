# UI-035 — Lens Guest Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Let the owner lock individual lenses behind Android system authentication and prevent in-app navigation or rendering of locked lens app grids until unlocked.

**Architecture:** Persist device-local lock flags in a dedicated `LensLockStore` (`lens_access_prefs`), keep lock/fallback rules in pure `LensAccessPolicy`, and authenticate through an injectable `LensUnlockAuthenticator`. `ActHome`/`LensViewHolder` enforce one guard on binding, pager selection and intent paths. The lens row menu owns lock/unlock controls. Locked ViewPager pages must never receive app data because adjacent pages bind before `onPageSelected` can reject navigation.

**Tech Stack:** Kotlin/Java, AndroidX Biometric 1.2.0-alpha05, Activity Result API, Room-backed `LensWorkspace`, SharedPreferences, JUnit4/Robolectric, Android instrumentation, Gradle.

## Global Constraints

- `minSdkVersion 25`; API 25–29 uses separate system biometric and device-credential flows. Do not pass `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` on API 28–29.
- API 30+ uses one `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` and no negative button text.
- Lock state is device-local: only `lens_access_prefs.xml`, excluded from Auto Backup/device transfer and from workspace/layout/profile export/import. Never use default preferences for it.
- Never store PIN, biometric data, secrets, tokens or authentication results.
- Always keep one reachable unlocked lens. Refuse locking/deleting the last unlocked lens; fail closed if corrupt state leaves all lenses locked.
- A locked `LensView` receives no app list and shows only a locked placeholder.
- This is an in-app privacy guard only; document Recents, ADB/root, backups, other launchers and device-owner access as limits.
- Device test: TECNO KJ7 `115333744A005844` only. Backup APK/data/per-file hashes before test; restore and verify afterwards. No implicit MCP device calls. If an ad/consent covers UI, stop and wait for owner `done`.
- No Ads/IAP/TalkBack scope, no new dependency, no push. Do not stage `.idea/`.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/util/LensAccessPolicy.kt`: pure rules for lock refusal, deletion refusal, target resolution, fallback.
- Create `app/src/main/java/com/mckimquyen/util/LensLockStore.kt`: dedicated lock preferences and cleanup.
- Create `app/src/main/java/com/mckimquyen/util/LensUnlockAuthenticator.kt`: API-split system auth with injectable test seam.
- Modify `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/test/java/com/mckimquyen/app/BackupRulesTest.kt`: exclude `lens_access_prefs.xml` in legacy backup, cloud backup and device transfer.
- Modify `app/src/main/java/com/mckimquyen/ui/LensManagementActivity.kt`, `app/src/main/res/menu/menu_lens_row.xml`: lock/unlock row action, deletion guard and refusal feedback.
- Modify `app/src/main/java/com/mckimquyen/ui/ActHome.java`, `app/src/main/java/com/mckimquyen/ui/home/LensViewHolder.java`, `app/src/main/java/com/mckimquyen/ui/home/LensViewHolderHost.java`, `app/src/main/java/com/mckimquyen/adt/LensPagerAdapter.kt`, `app/src/main/res/layout/item_lens_page.xml`, `app/src/main/java/com/mckimquyen/util/LensShortcuts.kt`: no-flash guard, content-free locked placeholder, page fallback, shortcut/intent/restore paths.
- Modify `app/src/main/res/values/strings.xml` and 16 locale `values-*/strings.xml`: localized lock copy and limitations.
- Tests: `LensAccessPolicyTest.kt`, `LensLockStoreTest.kt`, `LensUnlockAuthenticatorTest.kt`; extend `LensManagementActivityWidgetTest.kt` and `ActHomeMultiLensWidgetTest.kt`; add `ActHomeLensLockIntegrationTest.kt`; extend locale tests if string coverage requires.
- Docs: `doc/task/AUDIT_UI-035_2026-11-05.md`, `doc/feature.md`, `doc/task/README.md`, move story only after final verified closure.

---

### Task 1: Pure Lens Access Policy

**Files:** create `app/src/main/java/com/mckimquyen/util/LensAccessPolicy.kt` and `app/src/test/java/com/mckimquyen/util/LensAccessPolicyTest.kt`.

**Produces:**

```kotlin
enum class LensTargetDecision { ALLOWED, FALLBACK, BLOCKED }
data class LensTargetResolution(val decision: LensTargetDecision, val lensId: String?)

object LensAccessPolicy {
    fun canLock(lensId: String, lenses: List<LensWorkspace>, locked: Set<String>): Boolean
    fun canDelete(lensId: String, lenses: List<LensWorkspace>, locked: Set<String>): Boolean
    fun firstUnlocked(lenses: List<LensWorkspace>, locked: Set<String>): String?
    fun resolveTarget(requestedLensId: String, lenses: List<LensWorkspace>, locked: Set<String>): LensTargetResolution
    fun resolveActive(savedActiveLensId: String?, lenses: List<LensWorkspace>, locked: Set<String>): LensTargetResolution
}
```

- [ ] Write RED tests first. Cover empty workspace; unknown requested lens; locked target; allowed target; locking unknown/already locked lens; refuse locking the last unlocked lens; allow locking one of two unlocked lenses; refuse deleting the last unlocked lens while other lenses are locked; allow deleting a locked lens when another unlocked remains; first-unlocked preserves `LensWorkspace` order; saved active locked falls back; all-locked fails closed with `BLOCKED`/null; input sets/lists are not mutated.
- [ ] Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.LensAccessPolicyTest'`. Expected: FAIL because `LensAccessPolicy` is missing.
- [ ] Implement only the pure policy above. It must not read Android/context/prefs and must treat stale locked IDs as ignored.
- [ ] Re-run the focused test. Expected: PASS.
- [ ] Commit `feat(lens-lock): add access policy`.

### Task 2: Device-Local Lock Store and Backup Exclusion

**Files:** create `LensLockStore.kt` and `LensLockStoreTest.kt`; modify `backup_rules.xml`, `data_extraction_rules.xml`, `BackupRulesTest.kt`; likely modify `UtilSettings.kt` only for deletion cleanup hook if that matches existing delete callers.

**Produces:**

```kotlin
class LensLockStore(context: Context) {
    fun isLocked(lensId: String): Boolean
    fun saveLocked(lensId: String, locked: Boolean)
    fun lockedLensIds(validLensIds: Set<String>): Set<String>
    fun delete(lensId: String)
}
```

Use a dedicated private preferences file named `lens_access_prefs`; key shape `lens_locked_<lensId>` including `default`. `lockedLensIds` filters to valid IDs and treats wrong-typed values as unlocked. Never touch default preferences. `UtilSettings.duplicateLensSettings` and workspace/profile import/export must not copy, import or remove lock flags.

- [ ] Write RED tests: default unlocked; per-lens isolation including default lens; valid-ID filtering; wrong-type value unlocked for that key only; delete removes only that lens; duplicate settings leaves new lens unlocked; workspace snapshot/replace/import leaves lock store unchanged; backup rule XML excludes `lens_access_prefs.xml` in `full-backup-content`, `cloud-backup`, `device-transfer` while existing exclusions stay.
- [ ] Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.LensLockStoreTest' --tests 'com.mckimquyen.app.BackupRulesTest'`. Expected: FAIL for missing store/rules.
- [ ] Implement `LensLockStore` and XML exclusions. Wire deletion cleanup at the existing lens-delete boundary(ies) in `LensManagementActivity` and `LensMenuCoordinator`; keep this store out of `snapshotWorkspacePreferences()`.
- [ ] Re-run focused tests plus `UtilSettingsPerLensTest` and workspace-backup tests. Expected: PASS.
- [ ] Commit `feat(lens-lock): add device-local lock store`.

### Task 3: System Unlock Authenticator

**Files:** create `LensUnlockAuthenticator.kt` and `LensUnlockAuthenticatorTest.kt`; inspect existing `BiometricWidgetTest.kt` and `AppAdapterAuthenticationTest.kt` patterns.

**Produces:**

```kotlin
enum class LensUnlockResult { SUCCESS, CANCELLED, UNAVAILABLE, ERROR }

interface LensUnlockAuthenticator {
    fun isAvailable(context: Context): Boolean
    fun authenticate(activity: AppCompatActivity, lensName: String, callback: (LensUnlockResult) -> Unit)
}
```

Production implementation: API 30+ `PromptInfo.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` with no negative text. API 25–29: use strong biometric if `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` succeeds, with a localized negative action only when `KeyguardManager.isDeviceSecure()`; if biometric is unavailable but credential is secure, launch credential immediately. Credential uses `createConfirmDeviceCredentialIntent()` with an `ActivityResultLauncher<Intent>` registered in the owning Activity field/initializer before `onStart()` (not at unlock time). `LensManagementActivity` supplies/registers the launcher and passes it into the authenticator seam. Report unavailable if neither path exists. OS credential activities may not be cancellable once launched; only returned results are trusted.

- [ ] Write RED tests with injected fake launchers/prompts: API 30+ combined flags and no negative text; API 28–29 requests only `BIOMETRIC_STRONG` plus a “Use device PIN” negative action; biometric success; cancel; negative action launches device-credential fallback; unavailable biometric + secure credential launches credential directly; neither authenticator returns `UNAVAILABLE`; prompt errors return `ERROR`; credential `RESULT_OK` succeeds and canceled result does not. Assert only `SUCCESS` permits caller-side unlock.
- [ ] Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.LensUnlockAuthenticatorTest'`. Expected: FAIL for missing authenticator.
- [ ] Implement the interface and production class. Keep Android calls behind small adapters so Robolectric tests do not need hardware.
- [ ] Re-run focused tests plus `AppAdapterAuthenticationTest`. Expected: PASS.
- [ ] Commit `feat(lens-lock): add system unlock authenticator`.

### Task 4: Lens Row Lock/Unlock UX and Localized Copy

**Files:** modify `LensManagementActivity.kt`, `menu_lens_row.xml`, 17 string resources, `LensManagementActivityWidgetTest.kt`; update/extend translation tests if they enumerate keys.

New resources: `lens_lock`, `lens_unlock`, `lens_locked_message`, `lens_lock_last_unlocked`, `lens_lock_no_auth`, `lens_lock_privacy_limit`, `lens_delete_last_unlocked`. Copy must state this is an in-app privacy guard and does not replace the device lock or protect against ADB/root/Recents/other launchers.

- [ ] Write RED widget tests before UI changes: unlocked row shows Lock and locked row shows Unlock; lock disabled for the last unlocked lens; delete disabled for the last unlocked lens while others are locked; no-credential state refuses lock/unlock with explanation; lock writes only that lens in `LensLockStore`; unlock success clears only target; cancel/error keeps lock; locked row tap does not launch `ActHome`; menu icon tint/accessibility description follows current state; deleting a lens clears its lock flag.
- [ ] Run focused widget methods in `LensManagementActivityWidgetTest`; confirm RED failures are missing menu behavior, not fixture errors.
- [ ] Add one `menuLensLockToggle` item. `showLensRowMenu` computes `LensAccessPolicy.canLock/canDelete`, authenticator availability and persisted state. Lock is immediate; unlock calls injected authenticator and clears only on `SUCCESS`. Refuse unavailable/last-lens actions with a Toast/Snackbar using resources above.
- [ ] Add all 17 locale resources. Run `AllStringsTranslationTest` and `LensStringTranslationTest` with updated key lists.
- [ ] Re-run focused widget methods, translation tests and existing delete/rename row tests. Expected: PASS.
- [ ] Commit `feat(lens-lock): add lens lock menu`.

### Task 5: Central Navigation Guard and No-Flash Locked Pages

**Files:** modify `ActHome.java`, `LensViewHolder.java`, `LensViewHolderHost.java`, `LensPagerAdapter.kt`, `LensManagementActivity.kt`, `LensShortcuts.kt`; create `ActHomeLensLockIntegrationTest.kt`; extend `ActHomeMultiLensWidgetTest.kt`.

Use these exact collaborator names across implementation tasks:

```java
boolean isLensLocked(String lensId);
LensTargetResolution resolveLensTarget(String requestedLensId);
void onLensBlocked(String lensId);
void onLensAccessChanged();
```

`ActHome` owns the current locked-ID set and `lastAllowedLensId`. Replace all paths that save/select active lens with `resolveLensTarget` or `resolveActive`; do not persist locked IDs as `KEY_ACTIVE_LENS_ID`.

- [ ] Write RED tests first for: saved locked active resolves to first unlocked before binding any grid; `EXTRA_TARGET_LENS_ID` locked does not write active ID/report usage; pager user selection of locked page returns to last allowed; edge-swipe to locked target blocks; indicator/tab selection blocks; management selection blocks; locked page bind calls no non-empty `setApps`; locking the visible lens switches immediately to unlocked lens; lock status survives recreate; lens shortcuts omit locked entries; lock change republishes shortcuts; list mode cannot expose locked lens. Include a prefetch test proving an adjacent locked page never receives apps before selection.
- [ ] Run focused tests before production edits: `./gradlew connectedDevDebugAndroidTest` is forbidden by device policy. Instead compile/assemble androidTest, then for TECNO-only use `adb -s 115333744A005844 shell am instrument -w -e class <class>` after backup/hash. Confirm RED assertions are the locked-navigation failures.
- [ ] Implement the guard minimally: refresh locked IDs before every bind/restore; call `LensPagerAdapter.setLockedLensIds(ids)` before `submitLenses`/any layout/bind pass; locked page `onBindViewHolder` must not invoke `onBindPage` and must set an empty app list plus placeholder, never call `bindLensView`'s app assignment. Update locked IDs and rebind affected pages when lock state changes. Resolve/fallback in `refreshLensList`, `applyTargetLensExtra`, `handleTargetLensIntent`, `onResume`, `onPageSelected`, edge-swipe and management selection. Keep guards idempotent and avoid reentrant `setCurrentItem` loops.
- [ ] Refresh dynamic shortcuts after lock/unlock/delete; filter locked lenses from `LensShortcuts.pick` input.
- [ ] Re-run affected host unit tests and TECNO instrumentation classes: `LensManagementActivityWidgetTest`, `ActHomeMultiLensWidgetTest`, `ActHomeLensManagementWidgetTest`, `ActHomeLensLockIntegrationTest`. Expected: PASS.
- [ ] Commit `feat(lens-lock): guard lens navigation`.

### Task 6: Verification, TECNO Smoke, Audit and Closure

**Files:** `doc/task/AUDIT_UI-035_2026-11-05.md`, `doc/feature.md`, `doc/task/README.md`, `doc/task/todo/p3-ui-ui-035-lens-guest-lock.md` → `inprogress/`/`done/` only per state.

- [ ] Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, `git diff --check`, and `scripts/check-secrets.sh`; record exact test/lint/warning counts.
- [ ] Verify `adb devices -l`; lock TECNO KJ7 `115333744A005844`; backup main/test APKs, app data, per-file hashes, locale/font/animator/HOME before install or tests. If backup/restore cannot be proven, stop before destructive actions.
- [ ] Run named instrumentation classes on TECNO with `adb -s 115333744A005844 shell am instrument -w -e class ...`; do not use Gradle `connected*`.
- [ ] Manual smoke with owner: lock a non-active lens; swipe, edge-swipe, indicator, management row selection, shortcut/Intent and relaunch cannot display it; lock active lens and confirm immediate fallback with no private icon flash; try locking/deleting last unlocked lens; unlock via system biometric/device credential; cancel and unavailable credential cases keep lock. If an ad/consent covers UI, stop and wait for owner `done`.
- [ ] Restore APK/data/system state; compare every file hash and device settings. Separate OS-generated cache differences from app state; never claim exact restore without evidence.
- [ ] Write audit: score, command output, device/Android/APK SHA/time, test classes, bypass limits (Recents, ADB/root/backup, other launchers, device owner), and anything not run. Score must exceed 9.0/10 before marking story done.
- [ ] Update feature/backlog docs and story location; commit local docs/code. Do not stage `.idea/`. Do not push unless the owner separately requests it.

## Self-Review

- Spec coverage: policy/lock invariants Tasks 1–2; auth/API compatibility Task 3; menu/resources Task 4; all entries/no-flash/shortcuts Task 5; device smoke/audit/backup Task 6.
- No placeholders: every task names files, produces concrete signatures, red tests, commands, and expected outcomes.
- Type consistency: `LensTargetResolution`, `LensUnlockResult`, `LensLockStore`, `LensAccessPolicy.canDelete` names are used consistently.
