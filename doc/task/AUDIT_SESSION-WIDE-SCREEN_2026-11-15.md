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
9. **Settings: only the Settings tab was measured**, not the Lens and Apps tabs.
10. **A50s is left half-restored:** its main APK is the original, but the test APK and the app data were not reinstalled after it dropped off adb mid-restore. Exact backup: `/Users/loitran/lens-launcher-device-backups/a50s-20261114-183017` (APKs `410212b7...` / `acce3c20...`, `data.tar`, hashes, packages, state).

## Mistakes made during this work (kept because they changed what the evidence means)

- Several "phone landscape" runs were really portrait (identical 1440x3120 / 243081-byte screenshots) and were discarded; the cause is item 3 above.
- A Pixel backup taken in the middle of a sequence recorded my dev build instead of the baseline; I restored from the true baseline (`/tmp/f225-...`) once I noticed, and every later script restores from it and checks the APK hashes first.
- A first geometry test compared the expanded app bar's list bottom with the visible frame; that assertion was wrong (not a layout bug) and was fixed. An explanation I gave for the 128 dp difference was wrong at first and was replaced by the measured figure.
- A summary of mine said the 6 behaviour tests passed in phone landscape when only the single geometry test had; the docs state the true split.
- Twice the tool returned an unrelated line ("clean - nothing to commit") instead of command output; I did not use it and re-read the files.

## Score

**8.5 / 10 - below the push gate of 9.0.** Strong: red-first fix with mutation evidence, forced real host runs, every Pixel run restored and hash-checked, limits written down. Weak: one device, no hand test, the ad guard unproven, phone-landscape behaviour coverage needs a script rather than running by itself. Pushing was done by explicit owner decision with these limits recorded, not because the gate was met.
