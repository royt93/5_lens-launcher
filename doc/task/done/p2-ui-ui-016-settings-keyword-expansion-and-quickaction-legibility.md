# UI-016 — Settings keyword table expansion + quick-action row legibility fix

| Field | Value |
|---|---|
| Type | `fix` + `feature` |
| Status | `done` |
| Priority | `P2` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 2 |
| Risk | Low |
| Dependencies | SEARCH-002 |

## Context

Owner reported "search settings/contacts/spotlight-style search-all doesn't work". Investigated
and clarified with the owner via `AskUserQuestion`:

- **Contacts search**: already known-blocked (`SEARCH-005`), needs owner + Play Console sign-off -
  not a bug, not started, unchanged.
- **"Search setting doesn't work"**: the owner had typed a word outside the 16-keyword table
  (confirmed via `AskUserQuestion` - not a bug in the mapped keywords, which a live unit test
  confirmed still worked).
- **"True Spotlight-style search everything"**: **not achievable** for a third-party app - Android
  has no public API to enumerate/search Settings' own preference screens (that mechanism,
  `SettingsSearchIndexablesProvider`, is system-signature-only). Every third-party launcher (Nova,
  Lawnchair included) ships a hardcoded keyword table for exactly this reason. Owner accepted
  expanding the existing table substantially instead of the infeasible "true index".

## What shipped

1. **`SETTINGS_KEYWORDS` expanded from 16 to ~45 entries** - added brightness, storage, battery
   saver, notifications, accessibility, airplane mode, NFC, VPN, app manager, developer options,
   sync/accounts, print, keyboard/input method, device info, system update (Vietnamese + English
   each). `@SuppressLint("InlinedApi")` added since some of these `Settings.ACTION_*` constants
   were added in later API levels than minSdk 25 - safe, since they're plain string constants and
   an unresolvable one on an old device just fails gracefully via the existing
   `ActivityNotFoundException` catch, same as every other keyword.
2. **Real bug found and fixed while investigating: the quick-action row's label text was
   genuinely hard to see.** Extensive debugging (added temporary logging, confirmed via
   `getLocationOnScreen`/`getCurrentTextColor` that the view was correctly positioned, sized,
   colored, and visible - then a *zoomed screenshot crop* revealed the text WAS rendering, just
   small, unbold, and easy to miss against a busy dynamic-color scrim) - not a logic bug, a
   legibility bug. `tvQuickActionLabel` had no explicit `textColor`/size/weight at all (every other
   text element on this screen does). Fixed: explicit `?attr/colorOnSurface`, 16sp, bold. Also
   added a trailing "›" chevron to `QuickAction.Action` rows (Settings deep-links, timer), which
   previously left the row's right side completely blank - compounding the "did this even do
   anything?" perception.

## Verification and Definition of Done

- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass (expanded
      unit test coverage for all new keywords); `./gradlew lintDevDebug` 0 errors, 20 pre-existing
      warnings, none new.
- [x] Full instrumented regression on Pixel 7 Pro: 142/143 pass (the 1 failure is the same
      already-disclosed pre-existing font-scale rounding flake, unrelated file/feature).
- [x] Live smoke on Pixel 7 Pro: "wifi" now renders as bold, clearly legible text with a trailing
      chevron (screenshot at normal scale, no zoom needed this time); tapping still opens the real
      WiFi settings screen as before.

Self-audited **9.2/10** (2026-09-13, Pixel 7 Pro). Docked slightly for the amount of debugging
time this took relative to the eventual fix's size - a lesson for next time: crop/zoom screenshots
before concluding something visual is "missing" rather than "hard to see".
