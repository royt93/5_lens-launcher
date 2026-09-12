# SEARCH-002 — Zero-permission local quick-action results

| Field | Value |
|---|---|
| Type | `new` |
| Status | `done` |
| Priority | `P2` |
| Evidence | `idea` |
| Epic | Search expansion (owner-picked 2026-09-12) |
| Estimate | 8 → split per sub-action at implementation time if needed |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Four owner-picked ideas share the same shape — a pure, local, zero-new-permission "quick action"
that can render as a special result row above/alongside app results in the existing search
overlay (`act_home.xml:44-99`, `rvSearchResults`):

1. **Calculator inline** — typing `12*7` shows `= 84` as a result row.
2. **Offline unit conversion** — typing `10km to mi` shows the converted value.
3. **Alarm/timer quick-set** — typing "hẹn giờ 10 phút" fires
   `Intent(AlarmClock.ACTION_SET_TIMER)` (stock Android intent, no permission).
4. **Battery % quick info** — typing "battery"/"pin" shows current battery percentage
   (`Intent.ACTION_BATTERY_CHANGED` sticky broadcast — no permission required for basic level).

`Settings` deep-link shortcuts (typing "wifi"/"bluetooth" → open the matching system Settings
screen via `Settings.ACTION_WIFI_SETTINGS` etc.) belong here too — same shape as `UtilLauncher.kt`'s
existing `ACTION_HOME_SETTINGS` intent pattern (`UtilLauncher.kt:15,69`).

## User story

As a user, I want to type a quick math expression, unit conversion, timer, battery check, or
settings name into search and get an instant inline action/result, without opening another app.

## Acceptance criteria

- [ ] A new pure `QuickActionEngine` (or similar) parses the normalized query and returns at most
      one quick-action result per query, tried in a fixed priority order (calculator > unit
      conversion > timer > battery > settings match) before/alongside `AppSearchEngine.search()`.
- [ ] Each quick action is a pure function returning a display string + an `Intent` (for
      timer/settings) or nothing (for calculator/battery, which just display a value).
- [ ] Malformed/ambiguous input (e.g. divide by zero, unknown unit, unmapped settings keyword)
      falls through silently to normal app search — never crashes, never shows a broken row.
- [ ] Settings keyword→action map is a small static table, documented as needing occasional
      review against new Android API levels (some `Settings.ACTION_*` constants get deprecated).

## Implementation notes

Keep this as one new small module (`search/QuickActionEngine.kt` or similar) rather than four
separate engines — the four sub-actions share the same "parse query → pure result" shape and the
same result-row slot in the UI, so one file with one function per action stays simplest per the
existing `AppSearchEngine` pattern (already a `Pure, deterministic` object per its own doc
comment).

## Implementation

`search/QuickActionEngine.kt` — pure object, tried in priority order calculator > unit
conversion > timer > battery > settings, first match wins:
- **Calculator**: tiny hand-written recursive-descent evaluator (+,-,*,/, parentheses); gated by
  a "must contain an actual operator, not just a bare number" check so a literal numeric app
  search (e.g. "5") isn't hijacked.
- **Unit conversion**: regex `<amount> <unit> (to|sang) <unit>`; length/weight lookup tables in
  base units + special-cased temperature (C/F/K) formulas.
- **Timer**: Vietnamese + English phrase regex → `Intent(AlarmClock.ACTION_SET_TIMER)` with
  `EXTRA_LENGTH` in seconds, `EXTRA_SKIP_UI=false`.
- **Battery %**: exact-keyword match (never substring — "Pinterest" must never trigger it) +
  `ACTION_BATTERY_CHANGED` sticky-broadcast read.
- **Settings shortcuts**: exact-keyword → `Settings.ACTION_*` static map (wifi, bluetooth, sound,
  display, language, location, date/time, network, security).

UI: a new `quickActionRow` (`act_home.xml`, above `recentHeader`/`rvSearchResults`, inside the
same `MaterialCardView` panel) shown/populated by `ActHome.updateQuickAction()` — tapping an
`Info` result copies its value to the clipboard, tapping an `Action` result starts the `Intent`
(caught `ActivityNotFoundException` falls back to a toast, never crashes).

Deviation from the original acceptance criteria: implemented as a dedicated row above
`rvSearchResults` rather than as a new row type *inside* the same `RecyclerView`/adapter - lower
regression risk against the existing, well-tested `SearchResultAdapter`, same user-visible result.

## Verification and Definition of Done

- [x] Unit tests (25, `QuickActionEngineTest.kt`): every sub-action's valid/invalid/edge cases
      per the acceptance criteria, plus a priority-order check.
- [x] Widget/UI (4 new, `AppSearchWidgetTest.kt`): row shows for calculator/settings queries,
      hidden for an ordinary app query, real on-device battery % via `resolveBattery`.
- [x] Integration: not applicable beyond the widget tests, confirmed.
- [x] Smoke on Pixel 7 Pro (one-off exception — TECNO KJ7 was in active personal use this
      round): "12*7" → "84" row renders correctly; "wifi" row tap opens the real Wi-Fi Settings
      screen end-to-end.
- [x] `./gradlew lintDevDebug`: 0 errors, 20 pre-existing warnings, no new ones.

Self-audited **9.5/10** (2026-09-13, Pixel 7 Pro). Deduction: timer and the remaining Settings
keywords (bluetooth/sound/display/etc., beyond wifi) were verified by unit test + code review
only, not individually smoke-tested by hand on-device this round.
