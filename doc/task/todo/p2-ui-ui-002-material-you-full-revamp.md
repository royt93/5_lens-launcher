# UI-002 — Material You full revamp (all screens)

| Field | Value |
|---|---|
| Type | `idea` |
| Status | `todo` |
| Priority | `P2` |
| Evidence | `idea` |
| Epic | Material You revamp |
| Estimate | 13 → split per screen before implementation |
| Risk | Medium |
| Dependencies | None (UI-001 base Material3 migration already `done`) |

## Context and evidence

UI-001 (`done/`) migrated the base theme to `Theme.Material3.DayNight`, enabled real dynamic color
(`DynamicColors.applyToActivitiesIfAvailable`), and rebuilt the home search bar on
`com.google.android.material.search.SearchBar`/`SearchView`
(`app/src/main/res/layout/act_home.xml:27-100`, `ui/ActHome.java:129-131`). That covers theme +
home search shell only. Owner asked (2026-09-12) for a full UI/UX/animation revamp pass across
every screen in the Material You direction, plus a concrete follow-up: the home search
overlay + result list look bad and should get a 50%-opacity scrim.

Screens confirmed in code (see `doc/task/README.md` implemented list for what's already done):

- `ui/ActHome.java` + `ui/FrmLens.kt` + `views/LensView.kt` — fisheye grid, search overlay
  (`act_home.xml`), search result rows (`res/layout/view_search_result.xml`).
- `ui/ActSettings.java` (tab shell) + `ui/FrmApps.kt` + `ui/FrmSettings.kt`.
- `feature/vip/ActVipManagement.kt` (already de-nested in LINT-008, Material3 theme applies).
- `ui/ActAbout.java`, `ui/SplashAct.kt`.

Each sub-section below is one independently deliverable option set; split into its own
`UI-00x` story at pick time instead of implementing this file directly.

## Owner decisions (2026-09-12)

- **A (home search scrim)**: option 1 picked — **implemented this round** (`act_home.xml`
  MaterialCardView panel + `ActHome.java` `SEARCH_SCRIM_ALPHA_FRACTION = 0.5f` scrim).
- **B (search result row)**: option 1 picked — **implemented this round** (`DividerItemDecoration`
  in `ActHome.setupSearch()`, icon bumped 44dp→48dp in `view_search_result.xml`).
- **C (settings shell)**: option 1 picked (keep tabs, restyle Material3) — split out as `UI-003`,
  not yet implemented.
- **D (supporting screens)**: option 2 picked (full redesign incl. `ActVipManagement`) — split out
  as `UI-004`, not yet implemented.

Pending before either A/B is marked `done`: real-device smoke on the currently-locked device (not
run this session — no device was selected/locked yet) and the instrumented widget tests already
covering this file (`SearchResultAdapterWidgetTest`, `AppSearchWidgetTest`) re-run for regressions.

## Options per screen (recommended first, with trade-offs)

### A. Home search overlay + scrim (ties to the owner's opacity-0.5 request)

1. **(Recommended) Scrim at 50% alpha on the `SearchView` background, results list held in a
   separate `MaterialCardView` panel for guaranteed contrast.**
   Pros: matches the requested opacity exactly; text stays readable regardless of what's behind
   (lens icons/wallpaper) since the panel itself stays opaque; `MaterialCardView` is already a
   transitive dependency (used in `act_vip_management.xml`), no new lib.
   Cons: one extra `MaterialCardView` wrapper node in `view_search_result`'s parent — must be
   checked against the `TooDeepLayout` lint category just closed in LINT-008.
2. Set the whole `SearchView` background to 50% alpha, rows drawn directly on the dimmed grid
   (no separate opaque panel).
   Pros: smallest diff — one `setBackgroundColor`/theme-attr change.
   Cons: readability now depends on whatever is behind (lens icons, live wallpaper); needs
   per-wallpaper contrast testing, real a11y risk.
3. Blur-behind (`RenderEffect`, Android 12+) instead of a flat scrim.
   Pros: closest to Pixel/iOS "frosted glass" look.
   Cons: minSdk is 25 — needs a flat-scrim fallback path for 25-31 anyway, doubles the code path
   and test matrix for a purely cosmetic gain.

### B. Search result row (`view_search_result.xml`)

1. **(Recommended) Keep rows flat (no per-row card), wrap the whole `rvSearchResults` list in one
   `MaterialCardView`/elevated surface with a thin `Divider` between rows; bump icon 44dp→48dp.**
   Pros: one elevated surface (not N per-row cards) keeps nesting shallow — safe against
   `TooDeepLayout` recurring; matches how Pixel Launcher's own search list looks.
   Cons: loses independent per-row elevation/shadow.
2. Per-row `MaterialCardView` (rounded 16dp corners, small elevation) for every result item.
   Pros: each row visually "pops" individually, nice press/ripple isolation.
   Cons: reintroduces a nesting level per row right after LINT-008 flattened this exact file's
   sibling layout — needs a lint re-check every row.

### C. Settings shell (`ActSettings` tabs / `FrmApps` / `FrmSettings`)

1. **(Recommended) Restyle `TabLayoutMediator` tabs to Material3 `TabLayout` secondary style
   (pill indicator, dynamic color), convert `FrmApps`'s app rows and `FrmSettings`'s option rows
   to Material3 `ListItem`-style rows (consistent 72dp height, leading icon, trailing control).**
   Pros: reuses the same `AppAdapter`/`AppDiffCallback` data path (PERF-003) untouched — pure XML
   + `ViewHolder` binding changes, no data-flow risk.
   Cons: touches 2 fragments + their adapters — needs the existing `FrmAppsWidgetTest`/
   `AppAdapterWidgetTest` re-run to prove no regression (LEAK-001/PERF-003 coverage already exists).
2. Replace `ViewPager2` tabs with a Material3 `NavigationRail`/bottom `NavigationBar` (drop tabs
   entirely).
   Pros: currently trendier M3 pattern for 2-3 destinations.
   Cons: bigger structural change to `ActSettings`'s `itf/*Interface` wiring than the ask calls
   for; higher regression risk for a cosmetic goal.

### D. Supporting screens (`ActVipManagement`, `ActAbout`, `SplashAct`)

1. **(Recommended) Leave `ActVipManagement` structure as-is (already Material3-themed +
   de-nested by LINT-008); only re-skin `ActAbout` rows and `SplashAct`'s branding to match the
   dynamic-color palette (no logic change, pure style/attrs).**
   Pros: smallest surface area, avoids re-touching a screen (`ActVipManagement`) that just passed
   its own hardening/lint rounds.
   Cons: `ActAbout`/`SplashAct` get less "wow" polish than a full redesign would.
2. Full redesign of all three screens (new layouts, entrance animations, motion on
   `ActVipManagement`'s feature rows).
   Pros: most visual impact.
   Cons: `ActVipManagement` was just hardened (SEC-003) and de-nested (LINT-008) — re-touching its
   layout risks reopening both without new product value.

## Acceptance criteria (per split-out story, once picked)

- [ ] Owner-picked option implemented exactly as chosen (or documented deviation).
- [ ] No `TooDeepLayout`/`Overdraw`/other lint regression introduced.
- [ ] Existing widget/integration tests for the touched screen re-run green.
- [ ] Real-device smoke on the S24 Ultra (current device policy).

## Implementation notes

Split at pick time into per-screen stories (`UI-003` home scrim/search, `UI-004` settings shell,
`UI-005` supporting screens) so each stays independently deliverable per the backlog working
agreement, rather than one 13-point story.
