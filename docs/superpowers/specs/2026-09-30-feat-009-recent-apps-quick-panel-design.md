# FEAT-009 — Recently Used Apps Quick Panel Design

- **Date:** 2026-09-30
- **Status:** Approved
- **Story:** `p2-feat-feat-009-recent-apps-quick-panel` (owner-approved 4-story delivery loop, `doc/task/README.md`)
- **Target branch:** `dev`

## 1. Problem

`SearchHistoryStore` already tracks the 8 most-recently-launched apps (MRU, global across lenses,
no network) and shows them as `recentHeader` inside the full-screen search overlay — but only when
the user opens search with a blank query. There is no one-tap path from the home screen straight to
"apps I just used", and the story's own name ("quick panel") implies something reachable without
going through search first.

## 2. Goals

- A `BottomSheetDialogFragment` ("the panel") lists up to 16 recently-launched apps, tap to launch.
- Two entry points: (a) a new menu icon on `SearchBar` (always visible at the top of home,
  `act_home.xml:81-89`), toggleable off in Settings; (b) a new item in the existing
  lens-management long-press menu (`ActHome.java:609`), always present, no toggle.
- Long-press a panel item opens a 2-item context menu: "App info" (reuses the existing
  `ACTION_APPLICATION_DETAILS_SETTINGS` helper already in `UtilApp.kt`/`ext/Context.kt`) and
  "Remove from recent" (new single-entry removal, distinct from the existing full `clear()`).
- Recent list stays global across all lenses (`FISH-008`) — same "by design" scope `App.openCount`
  already uses; no per-lens key suffix.
- `MAX_RECENT` raised from 8 to 16, shared by both the panel and the existing `recentHeader` (one
  source of truth, not two diverging lists).
- Smallest change that satisfies the P2/5 SP estimate: no new persistence layer, no adapter
  rewrite, no change to `LensView`/`LensGridCache` render path.

## 3. Architecture

### Data — `SearchHistoryStore.kt`

```kotlin
private companion object {
    const val MAX_RECENT = 16 // was 8 — shared by recentHeader and the new panel
}

fun removeKey(componentKey: String) {
    val updated = recentKeys().filterNot { it == componentKey }
    preferences.edit { putString(KEY_RECENT, updated.joinToString(SEPARATOR)) }
}
```

- `removeKey` is idempotent: absent key → `filterNot` is a no-op, same string gets rewritten.
- No schema change. No migration needed — raising a `take(MAX_RECENT)` ceiling never invalidates
  existing shorter lists.

### Settings — `UtilSettings.kt` + `FrmSettings`

```kotlin
const val KEY_RECENT_APPS_QUICK_PANEL_ENABLED = "recent_apps_quick_panel_enabled" // default true
```

- New getter/setter pair, same shape as the existing `KEY_QUICK_ACTION_*_enabled` block.
- One `SwitchMaterial` row added to `FrmSettings`, same pattern as the existing quick-action
  toggles. Controls only the `SearchBar` icon's visibility — the lens-management menu entry is
  unaffected (it doesn't occupy persistent screen space, so it needs no off switch).

### UI — panel

- New `RecentAppsPanelFragment` (Kotlin, `BottomSheetDialogFragment`) + `bottom_sheet_recent_apps.xml`
  (a `RecyclerView` + empty-state `TextView`, Material3 tokens matching the rest of the app —
  `colorOnSurface`/`colorOutlineVariant`, no hardcoded colors).
- **No new adapter.** The panel's `RecyclerView` reuses `SearchResultAdapter` as-is — it already
  owns icon loading, shortcuts, `DiffUtil`-based `submitList`, tap-to-launch, and a long-press
  `PopupMenu` (`R.menu.menu_search_result`: App info / Uninstall / Pin / Unpin) with zero
  duplication. Only change to that shared class: one new optional constructor parameter,
  `onRemoveFromRecent: ((App) -> Unit)? = null`. `showActionMenu` gains one more conditionally
  visible item (`menuItemRemoveFromRecent`, new id in `menu_search_result.xml`, `isVisible =
  onRemoveFromRecent != null` — same pattern the existing `menuItemUnpin` visibility check
  already uses), wired to call the callback then `startActionIntent`-style dismiss. Search
  overlay usage passes `onRemoveFromRecent = null` (unchanged behavior, item stays hidden there).
- Row list resolution is a pure, unit-testable function (same shape as `LensLabelResolver`):

```kotlin
object RecentAppsPanelResolver {
    fun resolve(recentKeys: List<String>, snapshot: List<App>): List<App>
    // maps componentKey -> App via snapshot, in recentKeys order, dropping keys no longer
    // present in snapshot (app uninstalled / package changed) — never shows a "ghost" row.
}
```
- `SearchBar` menu icon: `SearchBar.inflateMenu(...)` (Material `SearchBar` extends `Toolbar`),
  new drawable, visible iff `KEY_RECENT_APPS_QUICK_PANEL_ENABLED`. `onClick` →
  `RecentAppsPanelFragment().show(supportFragmentManager, TAG)`.
- Lens-management menu: one new `MenuItem`/list entry in the existing `showLensManagementMenu`
  popup (`ActHome.java`), same `show(...)` call.
- Remove flow: `onRemoveFromRecent` callback calls `SearchHistoryStore.removeKey(componentKey)`,
  then `adapter.submitList(resolver.resolve(store.recentKeys(), snapshot))` — the adapter's own
  existing `DiffUtil` handles the remove animation, no manual `notifyItemRemoved` needed.
- Tap row → `SearchResultAdapter`'s existing click callback launches the app, panel then dismisses.

## 4. Behavior

- **Empty state:** 0 recent apps (fresh install, or history just cleared) → empty-state text,
  reusing an existing `@string/no_...` pattern if one fits, otherwise one new string.
- **Stale entries:** an app uninstalled or updated to a version with a different component key
  silently disappears from the panel next time it's opened (`RecentAppsPanelResolver` filters
  against the live `RAppsSingleton` snapshot) — never a crash, never a dead row.
- **Snapshot not ready yet** (cold start, background scan in flight): panel shows the same
  loading/empty treatment the Apps tab already uses rather than assuming a populated snapshot.
- **Removed while panel open:** live in-place removal, no dismiss/reopen.
- **Toggle flipped while panel is already open:** no effect on the current instance; only changes
  whether the icon appears next time `SearchBar` is drawn/refreshed.
- **Cross-lens:** launching an app from lens A updates the same global list a panel opened from
  lens B reads — verified explicitly in integration tests (see below), since this is the one place
  a wrong assumption (per-lens scoping) would silently ship wrong behavior.

## 5. Tests

### Unit

1. `SearchHistoryStoreTest` — `MAX_RECENT` trims at 16, preserves MRU order.
2. `SearchHistoryStoreTest` — `removeKey` removes exactly one entry, preserves order of the rest,
   no-op when the key isn't present.
3. `RecentAppsPanelResolverTest` — maps keys to `App` in order; drops keys missing from snapshot;
   empty input → empty output.
4. `UtilSettingsTest` — `KEY_RECENT_APPS_QUICK_PANEL_ENABLED` default `true`; round-trips `false`.

### Widget

1. `SearchResultAdapterWidgetTest` (extend, existing file) — `menuItemRemoveFromRecent` is hidden
   when `onRemoveFromRecent` is null (search-overlay usage, regression guard) and visible + wired
   when non-null; tapping it invokes the callback and does not affect `menuItemUnpin` visibility.
2. `RecentAppsPanelWidgetTest` (new) — panel shows the resolved list; empty snapshot shows
   empty-state; tapping a row launches the app and dismisses the sheet; "Remove from recent"
   removes that row in place (via the adapter's own `DiffUtil`) without closing the sheet.
3. `FrmSettingsWidgetTest` (new case) — toggling the new switch persists the key and flips
   `SearchBar` icon visibility.
4. `ActHomeLensManagementWidgetTest` (new case) — the new lens-menu entry opens the panel.

### Integration

1. `RecentAppsPanelIntegrationTest` — real `RAppsSingleton` snapshot + real
   `SearchHistoryStore`: launching an app updates the list a freshly-opened panel reads.
2. Multi-lens case (extends `ActHomeMultiLensWidgetTest` or a new integration test) — record a
   launch while lens A is active, open the panel from lens B, confirm the same entry appears
   (proves the "global, not per-lens" decision actually holds at runtime, not just by key-naming
   convention).
3. `LensStringTranslationTest` (extend) — every new string localized into all 16 locales.

### Smoke (session-locked device)

1. Tap the new `SearchBar` icon — panel opens, shows real recent apps.
2. Tap a row — real app launches, panel dismisses.
3. Long-press a row — menu appears; "Remove from recent" removes it live; reopen panel confirms
   it's gone.
4. Toggle the new setting off in `FrmSettings` — icon disappears from `SearchBar`; lens-menu entry
   still opens the panel.
5. Switch lens, launch an app there, reopen panel from the other lens — same entry shows up.

## 6. Non-Goals

- No per-lens recent-apps scoping (stays global, matching `openCount`'s existing "by design"
  scope).
- No new persistence layer/table — `SearchHistoryStore`'s existing `SharedPreferences`-backed MRU
  list is the only source of truth for both surfaces.
- No pin/favorite/reorder functionality inside the panel — MRU order only.
- No change to `LensView`/`LensGridCache` hot render path.
- No settings toggle for the lens-management menu entry (only the `SearchBar` icon is toggleable).
