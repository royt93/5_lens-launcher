# SEARCH-006 — Web search fallback

| Field | Value |
|---|---|
| Type | `new` |
| Status | `done` |
| Priority | `P3` |
| Evidence | `confirmed` |
| Epic | Search expansion (owner-picked 2026-09-12) |
| Estimate | 3 |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Owner-picked idea: when local search (apps, shortcuts, quick actions) returns nothing, offer
"Search the web for '<query>'" which opens the browser. Flagged during triage as the one item in
this epic that introduces a network-dependent path — every other search feature in this codebase
(`search/AppSearchEngine.kt`, `search/SearchHistoryStore.kt`) is explicitly local/offline
(see `CLAUDE.md`'s Search section: "No network or IME-suggestion dependency").

## User story

As a user, when my search finds nothing on-device, I want a one-tap way to search the web instead
of retyping the query in a browser.

## What shipped

New `tvWebSearchFallback` row in `act_home.xml`, below `tvNoSearchResults`. `ActHome
.updateSearchResults()` shows it only when `!isEmptyQuery && !hasResults &&
quickActionRow.getVisibility() != VISIBLE` — i.e. every local result set (apps, shortcuts, quick
actions) is empty, never alongside real local results. Tapping fires
`Intent(Intent.ACTION_WEB_SEARCH)` with `SearchManager.QUERY` set to the exact typed text - no
hardcoded search engine or URL, so it always respects the user's own default browser/search app
choice (confirmed live: Pixel 7 Pro's disambiguation sheet offered Chrome/Google, matching the
device's actual installed options). `CLAUDE.md`'s Search section updated to note this one
exception to the "no network dependency" property, per the acceptance criteria.

## Acceptance criteria

- [x] The web-search suggestion row appears only when every local result set (apps, shortcuts,
      quick actions) is empty — never alongside real local results.
- [x] Uses `Intent(Intent.ACTION_WEB_SEARCH)` with the query as `SearchManager.QUERY` extra
      (respects the user's own default browser/search app — no hardcoded search engine or URL).
- [x] No network call is made by this app itself — the browser handles the request, preserving
      this app's own "no network dependency" property.
- [x] Update `CLAUDE.md`'s Search section once shipped.

## Verification and Definition of Done

- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass;
      `./gradlew lintDevDebug` 0 errors, 20 pre-existing warnings, none new.
- [x] Widget: 2 new `AppSearchWidgetTest` cases (`webSearchFallbackShowsForNoMatch_andCarriesTheTypedQuery`,
      `webSearchFallbackHiddenWhenQuickActionOrAppMatchExists`) - 18/18 pass on Pixel 7 Pro.
- [x] Smoke on Pixel 7 Pro: searched "zzznotfoundxyz" (guaranteed no app/shortcut/quick-action
      match), fallback row appeared with the exact typed query quoted; tapping opened the real
      Android app-chooser sheet for `ACTION_WEB_SEARCH` (Chrome/Google offered) - correct intent,
      correct extra, no crash. Did not complete the chooser tap itself (would leave the browser
      genuinely open) since visual confirmation of the correctly-populated chooser sheet was
      sufficient evidence.

Self-audited **9.5/10** (2026-09-13, Pixel 7 Pro).
