# FISH-013 — Auto-Export-Lens Intent Extra Process-Kill Recovery Design

- **Date:** 2026-09-29
- **Author:** Claude Code
- **Status:** Approved
- **Story:** `p2-fish-fish-013-auto-export-lens-race` (3 SP)
- **Target branch:** `dev`

---

## 1. Context & Problem Statement

In `FrmLens` (tab Lens settings), tapping "Share lens image" launches `ActHome` with a one-shot `Intent` extra `EXTRA_AUTO_EXPORT_LENS = "com.mckimquyen.lenslauncher.EXTRA_AUTO_EXPORT_LENS"`.

In `ActHome`:
- `consumeAutoExportExtra(Intent)` reads the extra, calls `intent.removeExtra(...)`, and sets `pendingAutoExportLens = true` in memory.
- In two places (`refreshLensList`'s `loadAll` callback line 527 and `bindLensView` line 588), `pendingAutoExportLens` is checked and immediately flipped back to `false` before `exportActiveLensImage()` (and its background `PolaroidExportHelper.exportAsync`) finishes.
- If the process dies between "extra consumed" and "export completes" (or during bind latency), the intent extra is lost forever, the in-memory flag is lost, and the user's action silently disappears.
- Furthermore, if `exportActiveLensImage()` hits an unrecoverable state (e.g. `lensViews == null` or the active lens workspace row was deleted), it returns early silently with zero user feedback.

---

## 2. Goals & Non-Goals

### Goals
- **Survive normal app-stop/process death:** The one-shot request persists across process recreation via `SharedPreferences` (matching the proven `FISH-012` pending-key pattern). The shared `prefs.edit { }` helper uses asynchronous `apply()`: Android flushes it on a normal app-stop/process-death sequence, but a raw SIGKILL arriving in the narrow pre-flush window remains outside this guarantee (same accepted ceiling as FISH-012).
- **Clear on resolve only:** The persistent flag is only cleared when export finishes successfully, fails visibly, or hits an unrecoverable state.
- **Explicit failure UX:** If the active lens cannot be resolved or export fails, show an explicit error Toast (`R.string.error_lens_share_failed`) rather than failing silently.
- **Prevent duplicate exports:** In-memory guards prevent multiple simultaneous exports in the same Activity session.
- **Traceable tests:** Unit (API round-trip), Widget (unrecoverable error branch), and Integration (cold-launch resurrection from disk flag without Intent extra).

### Non-Goals
- Per-lens pending targeting (YAGNI): Bam Share always targets the currently active lens; global boolean matches current production semantics.
- Time-to-live expiration (YAGNI): Request does not expire automatically; fires on next launch.

---

## 3. Architecture & Detailed Changes

### 3.1. Persistence Layer (`UtilSettings.kt`)
Add API to manage pending auto-export state in SharedPreferences:
```kotlin
const val KEY_PENDING_AUTO_EXPORT_LENS = "pending_auto_export_lens"

fun hasPendingAutoExportLens(): Boolean {
    return prefs.getBoolean(KEY_PENDING_AUTO_EXPORT_LENS, false)
}

fun setPendingAutoExportLens(value: Boolean) {
    save(KEY_PENDING_AUTO_EXPORT_LENS, value)
}

fun clearPendingAutoExportLens() {
    prefs.edit { remove(KEY_PENDING_AUTO_EXPORT_LENS) }
}
```

### 3.2. Write Sites
- **`FrmLens.kt`**: Inside `shareLensImage()`, call `utilSettings?.setPendingAutoExportLens(true)` before `lensExportLauncher(intent)`.
- **`ActHome.java`**: Inside `consumeAutoExportExtra(Intent)`, if `getBooleanExtra(EXTRA_AUTO_EXPORT_LENS, false)` is true, also call `utilSettings.setPendingAutoExportLens(true)` to guarantee disk durability even if launched by an external/test intent.

### 3.3. Read & Trigger Pipeline in `ActHome.java`
- At `onCreate()` and `onNewIntent()`:
  - If `utilSettings.hasPendingAutoExportLens()` is true, set `pendingAutoExportLens = true`.
- At trigger points (line 526 in `refreshLensList` and line 587 in `bindLensView`):
  - Check `pendingAutoExportLens`.
  - Flip in-memory `pendingAutoExportLens = false` (prevents double-posting while in flight).
  - Post `exportActiveLensImage()`.
  - **Do NOT clear SharedPreferences flag here.**

### 3.4. Resolve Sites in `ActHome.exportActiveLensImage()`
Clear `utilSettings.clearPendingAutoExportLens()` only at actual terminal outcomes:
1. **Unrecoverable - view or matched lens missing**:
   ```java
   if (view == null || matched == null) {
       if (utilSettings != null) {
           utilSettings.clearPendingAutoExportLens();
       }
       Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
       return;
   }
   ```
2. **Success callback (`uri != null`)**:
   ```java
   if (utilSettings != null) {
       utilSettings.clearPendingAutoExportLens();
   }
   lensShareLauncher.launch(...);
   ```
3. **Failure callback (`uri == null` or exception)**:
   ```java
   if (utilSettings != null) {
       utilSettings.clearPendingAutoExportLens();
   }
   Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
   ```

---

## 4. Test Matrix

1. **Unit Test (`UtilSettingsTest.kt`)**:
   - `pendingAutoExportLens_defaultsToFalse()`
   - `pendingAutoExportLens_roundTripAndClear()`
2. **Widget Test (`ActHomeAutoExportWidgetTest.kt`)**:
   - Preserve existing `autoExportExtra_firesExactlyOnce_andNeverAgainAfterRecreate()`.
3. **Integration Test (`ActHomeAutoExportPersistenceIntegrationTest.kt`)**:
   - Write `utilSettings.setPendingAutoExportLens(true)` directly to simulate surviving a process kill.
   - Launch `ActHome` without any intent extra.
   - Assert `lensShareLauncher` receives chooser intent.
   - Assert `utilSettings.hasPendingAutoExportLens()` is now `false`.
   - Recreate Activity and verify no second export occurs.
   - **Implementation note (post-review):** the unrecoverable-state case originally slated
     for `ActHomeAutoExportWidgetTest.kt` as `unrecoverableState_clearsPendingFlagAndShowsToast()`
     landed instead in this same file as
     `exportWithNoBoundLensView_clearsPendingFlagInsteadOfResurrectingForever()` - it needed
     `exportActiveLensImage()` made package-visible (`@VisibleForTesting`) to call it directly
     with `lensViews == null` deterministically, rather than racing real `ViewPager2` bind timing
     the way the widget test's other cases do. Functionally equivalent coverage, different file;
     confirmed via code review (2026-09-29).
4. **Physical Device Smoke (Tecno KJ7 `115333744A005844`)**:
   - Launch app -> Settings -> Tab Lens -> Share lens image -> Verify share sheet appears.
   - Record SHA, device metadata, timestamp.

---

## 5. Definition of Done
- All test layers pass (Unit, Widget, Integration).
- Zero new lint errors.
- Audit score > 9.0/10.
- Backlog file moved `todo` -> `inprogress` -> `done`.
