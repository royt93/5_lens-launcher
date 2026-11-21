# Plan: why the soft keyboard stays up in a real split-screen (2026-11-15)

Status: NOT STARTED as a log probe. UPDATE 2026-11-21: the owner locked the A50s `R58MA6WYRPE` only; the Pixel is not to be touched (another Claude session uses it). Run the probe on the A50s instead; its One UI split behaviour (keyboard returns on focus, see `search-keypad-split-screen-a50s.txt`) leans toward cause 1 but is unproven. The Pixel rules below stay as history; for the A50s use the same single-block gate (launcher `com.sec.android.app.launcher` in front, no `*.test` process) and restore from `~/lens-launcher-device-backups/a50s-20261114-183017`.

## What is known

- Real split on the Pixel 7 Pro (Recents > Chia đôi màn hình), search already open, soft keyboard up, then split: the top pane reports `h441dp`, the T9 keypad shows, **the soft keyboard stays** (`mInputShown=true`). One run.
- `ActHomeSearchKeypadWindowHeightWidgetTest` (window shortened with `Window.setLayout`) passes with the keyboard dismissed. So the two environments differ on this point and the test does not prove the real case.
- Opening the search after the window is already short gives keypad and no keyboard in the real split (one earlier run).
- Code under suspicion: `HomeSearchController.applyKeypadChoice()` calls `InputMethodManager.hideSoftInputFromWindow(appSearch.getWindowToken(), 0)` when the keypad takes over and the search is showing.

## What is NOT known

Why the hide did not hold. Candidate causes, none tested:
1. The IME is requested again after the resize (focus change or a restart of the input connection when the configuration/window changes), after our hide call.
2. The hide ran before the window had a usable token or focus in split.
3. `applyKeypadChoice()` was not reached for this resize at all (the layout listener may not fire, or fires before `decorView.getHeight()` changes).

## Probe design (one run, one command block)

Temporary log only; it must never dereference anything that can be null (a previous log of mine crashed the app on `ViewCompat.getRootWindowInsets(...)` returning null in `onResume`). Log, with `String.valueOf(...)` or explicit null checks:
- on each `applyKeypadChoice()`: `useKeypad`, `searchView.isShowing()`, `decorView.getHeight()`, `appSearch.hasFocus()`, `appSearch.getWindowToken() != null`, `appSearch.isAttachedToWindow()`;
- right after `hideSoftInputFromWindow`: its boolean result and `imm.isAcceptingText()`;
- 1500 ms later: `imm.isAcceptingText()` and `appSearch.hasFocus()` (posted with `postDelayed`, guarded for a detached view).
Read the `dumpsys input_method` fields `mInputShown` and `mImeInputTarget` once after the log.

Decision table:
- log shows `applyKeypadChoice` never ran with `useKeypad=true` after the split: cause 3, fix = call it from the resize path (e.g. `onConfigurationChanged` / `onMultiWindowModeChanged`).
- ran, `hide=true`, `isAcceptingText` true again 1500 ms later: cause 1, fix = hide again after the layout settles, or clear focus from the box when the keypad takes over.
- ran with `token=false` or `attached=false`: cause 2, fix = defer the hide until the view is attached.

## Rules for the Pixel (the shared device)

- Do not send any command, not even a read-only one, until the ReadYou session writes "xong".
- Then, in the SAME shell block as the first device command: check `mCurrentFocus` is `nexuslauncher`, no `*.test` process, no `com.mckimquyen.reader.test`, `accelerometer_rotation=1`. Abort the block if any check fails. Do not rely on an earlier gate (that was the 21:43 mistake).
- Before any install: back up nothing new, the durable baseline is `~/lens-launcher-device-backups/pixel-20261115-003038`; restore from it afterwards and compare hashes (`DATA-IDENTICAL`, APKs `134ba042` / `a48a5efd`, 198 tmp entries, display 1440x3120, `accel=1 rot=0`).
- An ad (`com.google.android.gms.ads.AdActivity`) covers the top half after each split; bring ActHome to front with `am start`, send `KEYCODE_BACK` only while the ad window has focus (max 3), never tap ad content.
- Message the other session "xong" with `accelerometer_rotation=1` when finished.

## TDD afterwards

If the probe points at a fixable cause: write the failing test first. The test must reproduce the REAL path, so `Window.setLayout` alone is not enough for this case; if no in-process reproduction can be made, record that and keep the fix covered by the real-split run only, saying so in the evidence file.

## Out of scope / limits

One device, adb taps, no person; foldables untested; the other IME apps were not tried (only the Pixel's stock keyboard).

## Result of the A50s probe (2026-11-21)

Run with null-safe logs on the A50s (see `doc/task/evidence/brainstorm-2026-11-14/search-keypad-split-screen-a50s.txt`): cause 3 is false (the decision runs after the resize with use=true and decorH 1201), cause 2 is false (token and focus present), and the keyboard returns when the window regains focus (`onWindowFocusChanged(true)`), where nothing of ours runs. Not shown: that the Pixel behaves the same. Candidate fix, NOT written: re-apply `applyKeypadChoice()` (and hide the IME while the keypad is in use) from `ActHome.onWindowFocusChanged(true)`. Per the TDD rule the test must reproduce the real path; a `Window.setLayout` test cannot change focus the way One UI does, so any in-process test would only cover the call, not the real focus return. Say so in the evidence if a fix is written.
