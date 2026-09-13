# UI-011 — Empty-state revamp (all-apps + quick actions) + flat results list (no card)

| Field | Value |
|---|---|
| Type | `feature` + `fix` |
| Status | `done` |
| Priority | `P2` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 3 |
| Risk | Medium |
| Dependencies | UI-010 |

## Context

Owner review after UI-010, picked via `AskUserQuestion`:
- "No data" empty state: **combine full app list + a permanent quick-actions row** (over a
  prettier-but-still-empty state, or a quick-actions-only landing screen).
- Results list "card feels pointless" (a `MaterialCardView` nested inside a panel that already has
  its own full-bleed scrim): **remove the card entirely, flat list per Material3's own
  full-screen-search spec** (over a full-bleed card or just reducing elevation).

## Changes

1. **All-apps fallback.** `AppSearchEngine.search()` returns nothing for a blank query with no
   recent/favorite apps - `ActHome.updateSearchResults` now falls back to the full visible app
   list, sorted alphabetically (`sortedByLabel`), whenever that happens and the device has at
   least one app. A new `allAppsHeader` ("Tất cả ứng dụng" / "All apps", `ic_apps_24dp`) labels
   this state so it's never confused with `recentHeader`. `tvNoSearchResults` is now reachable
   only for a typed query with zero matches, or the near-impossible zero-apps-installed case.
2. **Permanent quick-actions row on blank query.** New `llEmptyQuickActions` - 5 tiles (Calculator,
   Convert units, Timer, Battery, WiFi; new `ic_calculate_24dp`/`ic_swap_horiz_24dp`/
   `ic_battery_24dp`/`ic_wifi_24dp` vectors, `ic_timer_24dp` reused) shown whenever the query is
   blank. Each tile prefills a working example (`"12*7"`, `"10 km to mi"`, `"hẹn giờ 5 phút"`,
   `"pin"`, `"wifi"`) into the search box via `prefillOnTap` rather than executing directly - the
   existing contextual `quickActionRow` (SEARCH-002) then handles it exactly as if the user had
   typed it, so there's only one execution path to maintain.
3. **Results list is now flat, no `MaterialCardView`.** Content (quick-action row, empty-actions
   grid, headers, result list) sits directly on `SearchView`'s own scrim
   (`search_view_scrim_background`, already near-opaque per UI-009) instead of a nested rounded
   card - matches the official Material3 full-screen-search pattern instead of a "frame inside a
   frame."

## Tests

- `AppSearchWidgetTest`: updated `searchScrimIsDimmedNotOpaqueOrTransparent` (dropped the
  now-obsolete "must sit inside a MaterialCardView" assertion), rewrote
  `blankQueryWithNoHistoryShowsOnlyEmptyState` for the new all-apps-fallback behavior, added
  `emptyQuickActionsGridShowsOnBlankQuery_hidesWhileTyping` and
  `tappingWifiTilePrefillsExampleQuery`. 16/16 pass on real hardware.

## Verification and Definition of Done

- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass;
      `./gradlew lintDevDebug` 0 errors, 20 pre-existing warnings, none new (the 5 quick-action
      tiles were initially icon+TextView pairs, flagged by `UseCompoundDrawables` - converted to
      single `TextView` + `app:drawableTopCompat` each, matching the pattern already used for
      `resultsSectionHeader`/`tvNoSearchResults`).
- [x] `am instrument` full run of `AppSearchWidgetTest` on TECNO KJ7: 16/16 pass.
- [x] Live smoke on TECNO KJ7: blank query shows the quick-actions row + "Tất cả ứng dụng" header
      + full alphabetical app list, no card visible anywhere; tapping the Calculator tile prefills
      "12*7" and the contextual row shows "= 84" live.

Self-audited **9.2/10** (2026-09-13, TECNO KJ7). Docked slightly: the quick-action tiles reuse the
existing contextual-row execution path by design (prefill, not direct-execute) - this is a
deliberate simplicity trade-off (one execution path, not five), not yet re-confirmed against the
owner beyond their original "combine app list + quick actions" pick.
