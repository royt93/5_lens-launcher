# SEARCH-003 — App shortcuts + inline row quick actions in search results

| Field | Value |
|---|---|
| Type | `new` |
| Status | `done` |
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

## Implementation

- `util/UtilApp.kt`: extracted `appInfoIntent`/`uninstallIntent` (shared, no duplication) - both
  `AppAdapter.java`'s existing popup menu and the new `SearchResultAdapter` row menu call these
  same two functions now.
- `search/AppShortcutsProvider.kt`: queries each matched app's shortcuts via `LauncherApps`
  (works from minSdk 25, no version branching needed) - catches `SecurityException` (not the
  default launcher) and any other failure, always returning an empty list rather than crashing.
- `view_search_result.xml`: restructured to an outer vertical container so an optional
  shortcuts row (`llSearchResultShortcuts`, up to 3 chips via `item_search_shortcut_chip.xml`)
  can render below the main row within the same list item - avoided a second `RecyclerView` item
  type. Click/long-click moved to the inner `llSearchResultMainRow` only.
- `menu_search_result.xml`: a deliberately smaller menu than `menu_app.xml` - only info/pin
  start/pin end/unpin/uninstall (no favorite/folder/move, which need a stable grid position
  search results don't have). `SearchResultAdapter`'s pin action calls the exact same
  `AppPersistent.setOrganization` AppAdapter already uses - no duplicated persistence logic.

## Verification and Definition of Done

- [x] Unit tests (2, `UtilAppActionIntentsTest.kt`): the shared intent builders.
- [x] Widget/UI (3 new, `SearchResultAdapterWidgetTest.kt`): shortcuts row stays hidden with no
      queryable shortcuts, long-press shows the menu without crashing, menu resource has exactly
      the expected 5 actions (no favorite/folder/move). Existing test updated for the
      main-row-not-itemView click/contentDescription change.
- [x] Integration: not fully exercisable - our test devices (Pixel 7 Pro, TECNO KJ7) are not set
      as the default launcher, so `LauncherApps.getShortcuts` always hits its
      `SecurityException` fallback path in practice. That fallback itself is proven safe
      (no crash, empty list) by the widget test above.
- [x] Smoke on Pixel 7 Pro (one-off exception - TECNO KJ7 in active personal use this round):
      long-press on a real "Facebook" result shows Info/Pin start/Pin end/Uninstall (Unpin
      correctly hidden - not currently pinned); tapping "Ghim vào vùng đầu" completes with no
      crash/exception in logcat. Screenshot test then hit a real AdMob test interstitial
      (Flood-It) on a separate `ActSettings` launch - flagged and paused per policy, owner
      confirmed done, no further smoke needed this round.
- [x] `./gradlew lintDevDebug`: 0 errors, 20 pre-existing warnings (caught and fixed a
      transient `UseKtx` regression - `Uri.parse` → `.toUri()` - during this round).

Self-audited **9.2/10** (2026-09-13, Pixel 7 Pro). Deduction: the app-shortcuts half of this
story couldn't be end-to-end verified with real shortcuts on either available device, since
neither is set as the default launcher - only the safe-fallback path was proven live.
