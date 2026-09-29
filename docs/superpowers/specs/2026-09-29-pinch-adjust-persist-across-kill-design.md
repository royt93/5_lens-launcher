# Pinch-adjust curvature: survive process kill — design spec

Status: approved for planning · 2026-09-29

## Context

Triggered by a real repro on the designated device (TECNO KJ7, `115333744A005844`),
done with explicit owner sign-off to temporarily set this app as default launcher: an
`adb shell am kill` while `FISH-009`'s pinch-adjust Snackbar was showing, followed by
`KEYCODE_HOME`, showed the process resurrects cleanly (no crash, `RApplication`'s
dynamically-registered receivers re-register fine — that half of the original
hypothesis was disproven) but the in-flight curvature adjustment is gone with zero
trace, and a ~2.7s cold-restart lag to `Fully Drawn` is real and measured (logcat:
`ActivityTaskManager: Fully drawn ... +2s693ms`).

A follow-up codebase-wide audit for the same class of bug (session-only state a user
could reasonably believe is already saved) found several more candidates. Per owner
decision this story fixes only the pinch-adjust case; the others become separate
backlog entries (see `doc/task/README.md` for where they land), not fixed here.

## The bug, precisely

- `LensView.liveDistortionFactor` (`views/LensView.kt:230`, `internal set`) holds the
  live value during and after a pinch.
- On gesture end (`onScaleEnd` at `:587-593`, and the `ACTION_UP` path at `:805-818` —
  both guarded by the same `mPinchReported` flag so exactly one fires), the listener
  calls into `ActHome.showPinchCurvatureSnackbar()` (`ui/ActHome.java:1397-1427`),
  which shows a `Snackbar.LENGTH_LONG` (~2.75s) offering "Save as default".
- Tapping the action calls `LensView.commitLiveDistortionFactor()` (`:239-244`), which
  writes `UtilSettings.saveDistortionFactor(lensId, factor)`. Any other dismissal calls
  `resetLiveDistortionFactor()` (`:246-251`), which just clears the in-memory value.
- Nothing is written to disk between gesture-end and one of those two outcomes. A
  process kill in that window (OS memory reclaim, OEM aggressive background
  management, `am kill`) loses the adjustment. Unlike the intentional "dismiss/timeout
  reverts" behavior — which the user sees happen in front of them — a kill is silent:
  the user never gets the choice they were mid-way through.

## Fix

Persist the value to a **separate, pending** key the instant the gesture ends (same
moment the Snackbar would show today), and resurrect the exact same choice the next
time this lens's page is bound — whether that's later the same session (killed while
backgrounded, user returns) or after a full relaunch.

### `UtilSettings` additions (`util/UtilSettings.kt`)

Follows the existing `lensKey`/`contains`-gated read pattern used by
`getDistortionFactor`/`saveDistortionFactor` (`:326-341`) exactly:

```kotlin
const val KEY_PENDING_DISTORTION_FACTOR = "pending_distortion_factor"

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
```

`deleteLensSettings()` (`:365-370`) gets a third `remove(...)` line for the pending key,
matching how it already clears the distortion/Smart-Focus keys for a deleted lens.

### `LensView` changes (`views/LensView.kt`)

- New small private helper (also removes the existing two-site duplication at
  `:587-593` / `:805-818`):
  ```kotlin
  private fun reportPinchFinished(finalDistortion: Float) {
      if (mPinchReported) return
      mPinchReported = true
      mUtilSettings?.savePendingDistortionFactor(lensId, finalDistortion)
      onCurvatureAdjustedListener?.onCurvatureAdjusted(finalDistortion, true)
  }
  ```
  Both call sites become `liveDistortionFactor?.let(::reportPinchFinished)`.
- `commitLiveDistortionFactor()` and `resetLiveDistortionFactor()` both additionally
  call `mUtilSettings?.clearPendingDistortionFactor(lensId)` — every path that resolves
  the choice cleans up the pending key, so it never lingers past a normal session.
- New public method, same shape as the existing commit/reset pair:
  ```kotlin
  fun restoreLiveDistortionFactor(value: Float) {
      liveDistortionFactor = value
      invalidate()
  }
  ```

### `ActHome` changes (`ui/ActHome.java`)

Inside `bindLensView(view, lens)` (`:534-567`), in the branch that already identifies
"this page is the one currently active" (`lensViews == null || lensViews == view ||
isActiveLensPage`, `:559`): after apps are set, check
`utilSettings.getPendingDistortionFactor(lens.getId())`. If non-null:
1. `view.restoreLiveDistortionFactor(pendingValue)`.
2. `showPinchCurvatureSnackbar(pendingValue)` — the exact existing method, unchanged.

For a page bound but *not* currently active (a prefetched neighbour), still call
`view.restoreLiveDistortionFactor(pendingValue)` if that lens has a pending value, but
skip the Snackbar — so the pill/preview is ready the instant the user swipes there,
without popping a confirmation for a lens they aren't looking at. (Mirrors the existing
per-page-carries-its-own-curvature model already established by `LensView.lensId`'s
setter recomputing Smart Focus, `:216-224`.)

No `onPause`/`onStop` override is added anywhere — the write already happens
synchronously at gesture-end, before the Snackbar shows, so there is no dependency on
any lifecycle callback firing before the OS reclaims the process.

## Data flow

```
Pinch ends (onScaleEnd / ACTION_UP, mPinchReported guard)
        │
        ▼
reportPinchFinished(value)
        │  mUtilSettings.savePendingDistortionFactor(lensId, value)   ← new, durable
        │  onCurvatureAdjustedListener.onCurvatureAdjusted(value, true)
        ▼
ActHome.showPinchCurvatureSnackbar(value)         (unchanged)
        │
   ┌────┴─────────────┐
   ▼ tap "Save"        ▼ dismiss / timeout
commitLiveDistortionFactor()   resetLiveDistortionFactor()
   │  saveDistortionFactor()      │  (in-memory clear only)
   │  clearPendingDistortionFactor() ← new, both branches
   ▼                              ▼
       pending key gone either way

--- if the process dies before either branch runs ---

Next bindLensView(view, lens) for this lensId (later this session, or a fresh launch)
        │
        ▼
getPendingDistortionFactor(lensId) != null
        │
        ▼
restoreLiveDistortionFactor(value)  [+ showPinchCurvatureSnackbar(value) if this
                                       page is the active one]
```

## Edge cases

- **Multi-lens**: pending key is per-lens (`lensKey` suffixing, same as every other
  FISH-008 Phase 3 setting) — a pending adjustment on lens A never leaks into lens B,
  and is checked on *every* page's own bind, not just the active page at launch, since
  the pinch could have happened on a lens that wasn't active when the kill occurred.
- **Lens deleted while a pending value exists**: cleaned up by the existing
  `deleteLensSettings()` call path, extended to remove the pending key too.
- **Repeated kills before the user ever resolves it**: idempotent — each new pinch
  release just overwrites the same pending key with the newest value.
- **Default lens**: unsuffixed key, consistent with how `getDistortionFactor`/
  `saveDistortionFactor` already treat it — no special-case code needed.
- **User backgrounds without pinching at all**: no pending key ever gets written, so
  nothing changes for the overwhelming majority of sessions.

## Testing plan

- **Unit** (`app/src/test/java/com/mckimquyen/util/UtilSettingsTest.kt` or a sibling,
  matching existing per-lens setting test coverage style):
  - `getPendingDistortionFactor` returns `null` when never set, returns the saved value
    per-lens, is independent between two lens ids, and is cleared by
    `clearPendingDistortionFactor` and by `deleteLensSettings`.
- **Widget** (androidTest, colocated with `LensViewPinchIntegrationTest`/
  `ActHomePinchWidgetTest` per this repo's convention of coverage living next to its
  call site):
  - Seed a pending key via `UtilSettings` directly, construct/bind a real `ActHome`
    (via `ActivityScenario`), assert the Snackbar reappears showing the pending value
    and that `LensView.liveDistortionFactor` reflects it before any tap.
  - Tapping "Save as default" on a resurrected Snackbar commits and clears pending,
    same as a normal (non-resurrected) flow.
  - Dismissing a resurrected Snackbar reverts the live value and clears pending.
  - A non-active (prefetched neighbour) page with its own pending value gets the live
    value restored silently, with no Snackbar shown until the user swipes to it.
- **Integration** (androidTest): a full seed-pending-key → fresh `ActHome` launch (not
  a recreate, a genuine new `ActivityScenario.launch`) → Snackbar resurrect →
  save-branch persists to the real `UtilSettings`/`SharedPreferences` → confirmed by
  reading it back directly, not just observing UI state.
- **Tecno smoke** (TECNO KJ7, `115333744A005844`, the currently locked device):
  repeat the exact manual repro already done — pinch to adjust, confirm Snackbar
  showing, `adb shell am kill`, `KEYCODE_HOME` — confirm the Snackbar resurrects with
  the correct value, and both the Save and dismiss branches behave correctly from that
  resurrected state. Screenshot/logcat evidence recorded in the story file, matching
  every other story's evidence convention.

## Out of scope for this story (deferred to their own backlog entries)

- `AppAdapter.java:613-641` / `FrmSettings.kt:212-234` — folder-name and search-hint
  dialog `EditText`s created without `setId()`, losing typed text across a rotation
  (identical root cause and fix already applied for the lens-rename dialog at
  `ActHome.java:765-767`). Separate story, same root cause bundled into one file.
- `ActHome.java:175/313-314/521-523/575-578` — `pendingAutoExportLens`'s intent extra
  is consumed (removed) before the export it guards actually runs; a kill or the
  already-disclosed bind race (`:570-574`) loses the share request non-retryably.
  Needs its own design, not a one-line fix. Separate story.
- Flashlight toggle display can go stale after recreate (`ActHome.java:1132/1159-1161`,
  cosmetic, no real hardware-state getter exists) and the drag-reorder async Room-write
  gap (`FrmApps.kt`/`AppAdapter.persistOrder`, narrow — long-press-drag is already
  disabled in favor of the menu-action path) — recorded as ideas in
  `doc/task/README.md`, not yet scoped as stories.
