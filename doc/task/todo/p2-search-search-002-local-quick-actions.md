# SEARCH-002 — Zero-permission local quick-action results

| Field | Value |
|---|---|
| Type | `new` |
| Status | `todo` |
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

## Verification and Definition of Done

- [ ] Unit tests per sub-action: valid expression, invalid expression, divide-by-zero, valid unit
      pair, unknown unit, valid/invalid timer phrase, each settings keyword mapped, unmapped
      keyword.
- [ ] Widget/UI: new result-row type renders correctly in `rvSearchResults`, tapping a
      timer/settings result fires the right `Intent`.
- [ ] Integration: Not applicable beyond the widget test — no persistence/SDK boundary.
- [ ] Smoke on designated device: try one real query per sub-action, confirm the row and the
      resulting action (timer created, Settings screen opened, etc.).
- [ ] No new lint/build failures.
