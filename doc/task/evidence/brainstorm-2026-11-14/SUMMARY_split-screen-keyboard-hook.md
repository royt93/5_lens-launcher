# Summary: T9 keypad on short windows and the soft-keyboard focus hook (2026-11-14 to 2026-11-23)

Short version of the scattered sections in `search-keypad-split-screen-a50s.txt`, the audit `../../AUDIT_SESSION-WIDE-SCREEN_2026-11-15.md` and the plan `../../PLAN_IME-SPLIT-INVESTIGATION_2026-11-15.md`. The originals stay; if a number here differs from them, the original wins.

## What shipped

1. On any window under 480 dp high the in-app T9 keypad replaces the soft keyboard, even with the T9 switch off (`SearchKeypadPolicy`). A note under the switch says so, in 17 locales (AI translation, no native review).
2. The window height is measured from the decor view, not from `Configuration.screenHeightDp`: after being resized into split-screen the Configuration kept 891 dp for a 440 dp window, so the keypad never showed.
3. Focus hook: `ActHome.onWindowFocusChanged(true)` calls `HomeSearchController.onWindowFocusGained()`, which hides the soft keyboard again only while the search is showing and the keypad is in use (`SearchKeypadPolicy.shouldHideSoftKeyboard`). A keyboard the user wants at full height is never touched.
4. Refactor `64411408`: the decision moved into the pure policy function and the controller measures the height in one place.

## Evidence (all on the TECNO KJ7 unless noted)

| Check | Result |
|---|---|
| Unit tests, forced run | 1809/1809, lint 0 errors / 16 baseline warnings |
| Mutation of the policy on the host | 10/10 mutants killed |
| 20-round real split (Recents > Split Screen), T9 off, keyboard up before the split | base: keyboard still up 10/10; hook and refactor: 0/10 (20/20 rounds valid each time) |
| Full-height regression (keypad not in use), real focus events | keyboard the user wanted survived focus loss in 5 valid rounds |
| Whole `com.mckimquyen.ui` package, 587 tests | 3 failures in every clean run on base, hook and refactor builds: the screenshot-height tests (PixelCopy 2328 vs display 2436, a property of this device) |
| A50s (earlier, before the owner had only the TECNO) | base keyboard returned 3/3 on focus return; hook 0/3 |

## Unexplained, kept open

- Two hook-build full runs had extra failures (one paging test once, one batch of five once). Neither reproduced in later full runs under the corrected gate (hook 2, base 2, refactor 2, each only the 3 screenshot failures). Cause unknown.
- `ResetDefaultsWidgetTest` is flaky on base and changed builds alike.
- Why a keyboard cannot be brought up with the search open and the keypad in use (`showSoftInput` and the insets controller both fail), so no automated test can start from that state.

## Mistakes that changed what the evidence means

- Early claims retracted: "T9 behaviour tests passed in phone landscape" (only the geometry test did); a timeline claim about the other session (only its install time was verified, its process was later read as idle).
- 2026-11-15 21:43: commands sent to the Pixel while another session was running on it.
- A first refactor `ui` run installed the old test APK from the backup (350 failures); discarded and rerun with the matching APK.
- 21 session commits carry a Claude trailer; the owner chose to keep pushed history (R6 applies from 2026-11-21).

## Limits

One phone, adb taps, no person; foldables untested; the Pixel was never re-tested with the hook; the A50s may still hold the hook build and cannot be restored unless it reappears in `adb devices`; the focus hook itself has no automated test (the decision is unit tested, the focus event is not); only Gboard was tried: the KJ7 has no second keyboard installed (checked 2026-11-23 with `ime list -s`: Gboard, voice input, autofill).

## Score

8.5/10, below the 9.0 gate. Pushed below the gate on the owner's decision with the limits above written down.
