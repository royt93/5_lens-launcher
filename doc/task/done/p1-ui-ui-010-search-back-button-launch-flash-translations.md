# UI-010 — Back button doesn't close search; result-tap flash; 16 untranslated strings

| Field | Value |
|---|---|
| Type | `fix` |
| Status | `done` |
| Priority | `P1` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 2 |
| Risk | Low |
| Dependencies | UI-009 |

## Context

Owner reported 9 issues after UI-009 (2026-09-13). Three were confirmed as real bugs with a
single objectively correct fix (no design trade-off), fixed directly rather than asked about via
`AskUserQuestion`; the remaining 6 are design/scope decisions handled separately.

## Bugs found and fixed

1. **Back button did not close the search overlay.** `ActHome.java`'s `OnBackPressedCallback` was
   an unconditional no-op, relying entirely on `com.google.android.material.search.SearchView`'s
   own internal `MaterialBackOrchestrator` to intercept back first. Confirmed via decompiled
   `material-1.13.0.aar` bytecode that `SearchView` does dynamically register/unregister its own
   back callback while showing - but live-tested on TECNO KJ7 (two back presses: one closes the
   IME as normal Android behavior, a second should collapse the panel) and the panel stayed open
   both times. Fixed by explicitly checking `searchView.isShowing()` in the callback and calling
   `hideSearch()` - correct regardless of whatever SearchView's own internal handling was or
   wasn't doing, with no downside if it doubles up.
2. **Tapping a search result flashed the result list to empty before the app launched.**
   `launchSearchResult()` called `hideSearch()` (which calls `searchView.clearText()`, firing the
   `TextWatcher` synchronously and wiping the result list) *before* `UtilApp.launchComponent(...)`
   started the target app - so the user saw their tapped result vanish for a frame before the
   app's own launch animation covered the screen. Fixed by reordering: launch the component first,
   `hideSearch()` after, so whatever the user sees next is the app's reveal animation, not this
   app's own list clearing itself.
3. **16 user-facing strings were untranslated in all 15 non-English/Vietnamese locales**, each
   explicitly marked `tools:ignore="MissingTranslation"` in `values/strings.xml` (a deliberate,
   pre-existing deferral from `FEAT-002`/`UI-001`, not a new regression) - covers the search-result
   context menu (pin start/pin end/unpin, directly part of `SEARCH-003`), the folder/favorite
   organization menu, and the "Show Search Bar" settings toggle. Owner flagged this now, so
   translated all 16 keys into all 15 locales (`de/hi/km/lo/ru/ko/pt/in/it/fr/es/zh/ar/th/ja`) and
   removed every `tools:ignore="MissingTranslation"` now that it's no longer needed. Caught and
   fixed an apostrophe-escaping build break (`l'épingle`, `nell'ultima`) introduced by the French
   and Italian translations during this same fix.

## Verification

- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass;
      `./gradlew lintDevDebug` 0 errors, 20 pre-existing warnings, none new.
- [x] `am instrument` full run of `AppSearchWidgetTest` on TECNO KJ7: 14/14 pass (no regression
      from the back-press and launch-order changes).
- [x] Live smoke on TECNO KJ7: back-press-twice screenshot sequence confirmed broken before the
      fix (panel still open after 2 presses) and fixed after (collapses to the pill, home grid
      visible).

Self-audited **9.5/10** (2026-09-13, TECNO KJ7). The result-tap flash fix (#2) is a reasoning-based
correction to statement order, verified by re-running the full widget suite (no new race
introduced) but not captured as its own before/after screenshot pair since the flash is a
single-frame visual artifact hard to reliably capture via `adb screencap` timing.
