# SEARCH-003 — App shortcuts + inline row quick actions in search results

| Field | Value |
|---|---|
| Type | `new` |
| Status | `todo` |
| Priority | `P2` |
| Evidence | `idea` |
| Epic | Search expansion (owner-picked 2026-09-12) |
| Estimate | 8 |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Two owner-picked ideas both enrich the existing app search row (`view_search_result.xml`,
`SearchResultAdapter.kt`) rather than adding a new content type:

1. **App shortcuts** — mix each installed app's own published shortcuts (e.g. "New message" in a
   chat app) into results, via `ShortcutManager.getShortcuts()`. This launcher already has the
   default-launcher role toggled via `ActFakeLauncher`/`UtilLauncher.kt`, which is the same
   precondition `ShortcutManager` query access needs — no new permission required.
2. **Inline row quick actions** ("gỡ / thông tin app / ghim" on long-press/swipe) — reuses the
   exact intent already wired for the same purpose elsewhere: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`
   (`AppAdapter.java:637`, `ext/Context.kt:41`) for "app info", and the existing pinned-zone
   mutation path (`model/PinnedZone`, `model/AppOrganizationRules`) for "pin".

## User story

As a user, I want search results to also surface an app's own quick shortcuts, and let me
uninstall/pin/see info on an app straight from the search row, without opening the Apps tab.

## Acceptance criteria

- [ ] Shortcuts (when present for a matched app) render as sub-rows or an expandable affordance
      under the app's search result, ranked below the app itself.
- [ ] Long-press/swipe on a search result row reveals info/pin/uninstall actions using the exact
      existing intents/mutations (no duplicate logic).
- [ ] Uninstall action goes through the standard system uninstall confirmation — never a silent
      `PackageManager` uninstall call.
- [ ] Apps that publish no shortcuts render exactly as today (no empty affordance shown).

## Implementation notes

`SearchResultAdapter` gets a new optional shortcuts sub-list per row and an action-menu affordance
reusing `AppAdapter`'s existing info/uninstall/pin intent code instead of re-implementing it —
check `AppAdapter.java` for the exact existing call sites before writing new ones.

## Verification and Definition of Done

- [ ] Unit tests: shortcut-to-result mapping, ranking order with/without shortcuts.
- [ ] Widget/UI: row renders shortcuts correctly, action menu triggers correct intents, uninstall
      shows system confirmation.
- [ ] Integration: `ShortcutManager` query against a real installed app with published shortcuts.
- [ ] Smoke on designated device: pin/uninstall/info from a real search result row.
- [ ] No new lint/build failures; re-check `TooDeepLayout` if the row layout grows nesting.
