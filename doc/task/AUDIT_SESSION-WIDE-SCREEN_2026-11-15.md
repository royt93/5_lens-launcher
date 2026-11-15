# Audit: effect cost, wide-screen coverage and the T9 landscape fix (2026-11-14 / 15)

Scope: commits `7c147016` .. `db3c734c` on `dev`. One product-code change (`BoundedHeightNestedScrollView`), everything else tests, scripts and docs.
Device: Pixel 7 Pro `2B051FDH3006MU` for every measurement; display profiles made with `wm size` / `wm density` / `user_rotation`, never physically different screens.
Raw numbers: `doc/task/evidence/brainstorm-2026-11-14/`. Narrative per item: `doc/feature.md` (Ideas, "Brainstorm lens 2026-11-14").

## What was delivered

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Cost of the 10 opt-in effects on the A50s (still finger) | 6.73 -> 8.11 ms/draw (x1.20); 4 of 10 stages did not change the pixels and are not claimed | `cumulative-effects-a50s.txt` |
| 2 | Cost of the 4 motion effects (Snap, Inertia, Magnify, haptic) with a real pan, Pixel | all four together x1.05-1.07 (pan), x1.03-1.07 (hold); 4 runs; every effect has its own positive control | `motion-effects-pixel.txt`, `LensViewMotionEffectsDrawCostTest` (opt-in) |
| 3 | Idle lens layout, 4 display profiles | 102/102 apps visible in all four | `wide-screen-pixel.txt` |
| 4 | Warp under a real finger, 4 profiles | 20/20 selections correct and inside the drawn rect, 0 overlapping pairs, peak 3.10-3.27x | `wide-screen-warp-pixel.txt`, `LensViewWideScreenWarpTest` |
| 5 | **T9 keypad overflow in phone landscape** | found (11 px under the navigation bar), fixed test-first, 9/9 mutations of the fix caught | `t9-keypad-display-sizes-pixel.txt`, `BoundedHeightNestedScrollView.kt` (+34 lines), 15 unit tests |
| 6 | T9 behaviour suite (filter, every key reachable, recreate + Activity collected, stop/start, typing jank, screenshot) | 6/6 on phone portrait, tablet-like portrait and landscape, and phone landscape (the last only when run one test per `am instrument`) | `t9-behavior-tests-pixel.txt`, `run-t9-per-test.sh` |
| 7 | Lens manager, 4 profiles | 8/8; last row clears the system bar | `lens-manager-display-sizes-pixel.txt` |
| 8 | Settings tab, 4 profiles | 8/8; last element 46 dp above the frame bottom everywhere; no ad overlay or banner in 8 runs | `settings-display-sizes-pixel.txt` |
| 9 | Two small coverage gaps closed | `BoundedHeightNestedScrollView` (5 -> 15 tests), `stableKey` and `performLensManagerHaptic` (6 tests, 6/6 mutations) | unit tests |

## The one product change

`BoundedHeightNestedScrollView` capped itself at 40% of the height its parent offered. In a vertical `LinearLayout` that contains a weighted child, the parent offers every child the whole column and does not subtract the rows above, so on a phone in landscape with an empty query: 596 px of rows above + a 389 px keypad in a 974 px column = 985 px, 11 px too much. The view now subtracts the visible fixed rows above it (margins included, GONE rows and the weighted filler excluded) when the parent is a vertical `LinearLayout` with a weighted child at or above it.
Red first: 3 of the 12 first tests failed on the old code; 15 tests now, 9 of 9 mutations of the fix are caught (3 survived the first 12 tests and led to 3 more tests).
After the fix on the Pixel: keypad viewport 389 -> 378 px, keypad bottom 1367 -> 1356 = visible frame bottom, last key reachable.

## Gate

- Forced host runs (`--rerun-tasks`, counted from the result files, not from the BUILD line): unit **1783/1783**, 233 classes, 31 tasks executed; lint 0 errors, 16 warnings (the baseline), none in any file added or changed here. A first lint run had a 17th (UseKtx `isGone`) in the new code and was fixed.
- Pixel, whole `com.mckimquyen.ui` package with the fix: **568/568** (14 min 48 s).
- Pixel restored to the same baseline after every run that installed anything: data identical (151 files), APKs `134ba042...` / `a48a5efd...`, 421 packages, 198 `/data/local/tmp` entries.

## What is not proven

1. **No real hand test.** Every number comes from injected or scripted input on one Pixel 7 Pro.
2. **"Wide screen" is `wm size` on a phone**, not a tablet, not a foldable, not a different panel. Foldable posture, multi-window and a large-screen window manager were not exercised.
3. **Phone landscape behaviour tests only pass when run one test per `am instrument`.** Measured on this Pixel: when an instrumented test finishes, the Pixel launcher takes the foreground and the display returns to portrait, so only the first test after a rotation saw landscape (5 of 5 repeats). Re-applying the rotation before each test gives 6/6, repeated 4 times. This is a property of this device and launcher, not established for others. Running the whole class in one go in landscape still fails 5 of 6 at the orientation check, by design.
4. **The ad guard is untested.** It never fired. A deliberate single interstitial request (`ActSettingsAdGuardProbeTest`, `openAbout()`) showed no ad window. That shows neither that the guard works nor that BACK closes `AdActivity` nor that no interstitial can ever appear. AppLovin uses its real ids even in debug, so by the owner's choice only AdMob was in scope and no AppLovin ad was run on purpose.
5. **Cost numbers are from the Pixel only.** The A50s (animator scale 0 = reduced motion) drifted x1.99 on an identical configuration, so its motion-effect cost numbers were discarded; only its controls were kept.
6. **Resolved afterwards (same day, see below):** the search results list was 0 px high in phone landscape with an empty query (596 px of rows above, a 378 px keypad, 974 px of column). The owner chose to hide the quick-actions row when the column is low; it is done and measured (list 274 px), with its own limits listed in the section "Follow-up: search list height" of `doc/feature.md`.
7. **Golden/screenshot tests do not compare against a stored baseline.** They write images and check size and non-blankness. There is no golden framework in the repo.
8. **Not applicable, with reasons:** network conditioning (the touched code and the measured screens make no network call), IAP and ad mediation (the owner excluded Ads/IAP/monetisation on 2026-10-29), a separate leak profiler (only an Activity-collected-after-recreate check was written; no LeakCanary-style tooling is in the project).
9. **Resolved 2026-11-15: the Lens and Apps tabs were measured too** (8/8 on four profiles; Lens last element 56 dp above the frame, Apps last row 108 dp). Limits: Apps only with 60 fake apps, not the real list or the empty/loading state; the first Apps run failed 4/4 because of a bug in my test (`waitForIdleSync` on the main thread), fixed and rerun.
11. **Soft keyboard over the search list (T9 off), found 2026-11-15:** the list was drawn under the keyboard (883 px hidden in phone landscape, 1264 px in phone portrait). Fixed for portrait (`ActHome`: bottom padding = max(system bars, keyboard); 0 px hidden, 1022 px visible). **Landscape then handled by the owner-chosen design (same day):** `SearchKeypadPolicy` shows the in-app T9 keypad instead of the soft keyboard on windows under 480 dp, even when the user turned T9 off (red first, 7/7 mutations, unit 1800, ui 582/582 on the Pixel; list 274 px visible in phone landscape). Trade-off: the user setting is overridden on short windows. **Mitigation added later the same day:** a note under the T9 switch in all 17 locales (AI translations, not native-reviewed); the widget reaches it on all four profiles (wraps to at most 3 lines on phone portrait); evidence `t9-short-screen-note-pixel.txt`. The paragraph below describes the state BEFORE that change: **was NOT fixed in phone landscape:** the search bar (382 px) plus the 967 px keyboard leave 91 px (26 dp) even with the chips and every header hidden (probe, removed), so the list stays unusable there; the test logs `NOROOM` and does not assert a minimum list height for that case. Keyboard measured on this Pixel only, no hand test.
12. **`ResetDefaultsWidgetTest` flaky on both builds:** second 20-round interleaved A/B: base 3/20, changed 3/20 (5/30 and 3/30 over both rounds), two different test methods. Present without these changes; cause not found.
10. **Resolved 2026-11-15: A50s restored from its backup** (one-time owner exception, then back to Pixel-only). The earlier "half-restored" note was partly wrong: the test APK `acce3c20...` was already installed; only the app data was missing (data dir held `cache`/`code_cache` only). Data re-extracted from `data.tar` and verified 14/14 hashes identical, main APK `410212b7...`, font 1.3, animator scales 0.0, Samsung launcher in focus, 52 `/data/local/tmp` entries. One difference stays: package `com.mckimquyen.reader.test` (another app's test APK) is in the backup list but absent now (460 vs 461 packages); not mine, left alone. Backup: `/Users/loitran/lens-launcher-device-backups/a50s-20261114-183017`.

## Mistakes made during this work (kept because they changed what the evidence means)

- Several "phone landscape" runs were really portrait (identical 1440x3120 / 243081-byte screenshots) and were discarded; the cause is item 3 above.
- A Pixel backup taken in the middle of a sequence recorded my dev build instead of the baseline; I restored from the true baseline (`/tmp/f225-...`) once I noticed, and every later script restores from it and checks the APK hashes first.
- A first geometry test compared the expanded app bar's list bottom with the visible frame; that assertion was wrong (not a layout bug) and was fixed. An explanation I gave for the 128 dp difference was wrong at first and was replaced by the measured figure.
- A summary of mine said the 6 behaviour tests passed in phone landscape when only the single geometry test had; the docs state the true split.
- Twice the tool returned an unrelated line ("clean - nothing to commit") instead of command output; I did not use it and re-read the files.

## Score

**8.5 / 10 - below the push gate of 9.0.** Strong: red-first fix with mutation evidence, forced real host runs, every Pixel run restored and hash-checked, limits written down. Weak: one device, no hand test, the ad guard unproven, phone-landscape behaviour coverage needs a script rather than running by itself. Pushing was done by explicit owner decision with these limits recorded, not because the gate was met.


## Follow-up: explain the short-window T9 override (2026-11-15)

Added `setting_enable_t9_search_keypad_short_screen_note` below the T9 switch in all 17 locales. The translations are AI-generated (owner asked for every locale in the same change) and have had no native-speaker review. `T9SearchStringsTest` checks that all 17 keys are present, non-blank, and not an English copy; 4/4 mutations are caught (a first mutation pass was invalid because Gradle returned the test up to date, and was discarded). `ActSettingsDisplaySizesTest#theT9ShortScreenNoteIsShownAndReachable` passed 4/4 on the Pixel profiles (note wraps to 1, 1, 2 and 3 lines on landscape phone, landscape tablet, portrait tablet, portrait phone); its first run failed 4/4 because of a scroll-offset bug in my test, fixed. Host: unit 1800/1800, lint 0 errors and 16 baseline warnings. Pixel `ui` package: 583 tests, 1 failure, the known flaky `ResetDefaultsWidgetTest`. The Pixel gained an unrelated package `com.freeapp.quick_invoice` (Play Store install at 16:52, not mine); it is left alone, so the package check now reports 422 against the 421 baseline.

Split-screen under 480 dp was **not measured**: adb `--windowingMode 3/4` did not produce a half-height window on the Pixel (see `search-keypad-split-screen-pixel.txt`). By reasoning only, a half-height portrait split (about 445 dp) would use the keypad even with T9 off; this needs a hand test in the real split UI.

Translation pass (same day, still AI): after a read-through of the 16 non-English notes, 13 were reworded for more natural phrasing (ar, de, es, fr, hi, in, it, lo, pt, ru, th, vi, zh); ja and ko were kept and km could not be improved with confidence. This is still not native-speaker review. `T9SearchStringsTest` green after the rewording. The unrelated package `com.freeapp.quick_invoice` on the Pixel: Play Store install (installer `com.android.vending`, 2026-11-15 16:52:32), single user 0. Its owner was not established beyond that; nothing was changed. The Pixel true baseline was copied from /tmp to `~/lens-launcher-device-backups/pixel-20261115-003038` (checksums identical), so it no longer lives only in /tmp.

Another session is using the Pixel at the same time (found 2026-11-15 ~18:00): `com.mckimquyen.reader.test` was installed by adb at 17:56:08 (installer null, not by me) and its instrumentation process was running. My runs install and restore only `com.mckimquyen.lenslauncher` and `.test`, so their data is untouched, but display changes (`wm size`, rotation) from my scripts would also hit whoever else is running a test on that phone, and theirs would hit mine. Any Pixel measurement taken while that other process was running should be read with that in mind; the 582/583-test `ui` runs and the 4-profile runs passed regardless. The package baseline copy now has `packages-after-20261115.txt` (423 packages) next to the original 421: the two extra are `com.freeapp.quick_invoice` (Play Store, 16:52:32) and `com.mckimquyen.reader.test` (adb, 17:56:08).

CORRECTION to the split-screen note above: it was later measured for real through the system UI (Recents > Split screen). The top half reports `h441dp` with T9 off, yet the soft keyboard opened and the T9 keypad did not show, so `SearchKeypadPolicy` did not take effect in that run. Cause unknown, not fixed, no test yet; one run, adb taps. See `search-keypad-split-screen-pixel.txt`. The earlier "reasoning only" prediction was wrong for this case.
