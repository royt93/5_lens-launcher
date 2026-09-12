# SEARCH-006 — Web search fallback

| Field | Value |
|---|---|
| Type | `new` |
| Status | `todo` |
| Priority | `P3` |
| Evidence | `idea` |
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

## Acceptance criteria

- [ ] The web-search suggestion row appears only when every local result set (apps, shortcuts,
      quick actions) is empty — never alongside real local results.
- [ ] Uses `Intent(Intent.ACTION_WEB_SEARCH)` with the query as `SearchManager.QUERY` extra
      (respects the user's own default browser/search app — no hardcoded search engine or URL).
- [ ] No network call is made by this app itself — the browser handles the request, preserving
      this app's own "no network dependency" property.
- [ ] Update `CLAUDE.md`'s Search section once shipped, since "No network or IME-suggestion
      dependency" will no longer describe this one fallback path precisely.

## Implementation notes

Single new branch in the empty-results path of the search overlay (`ActHome.java`/`FrmLens.kt`
search wiring) — no new engine needed, this is UI + one `Intent`, not a data/ranking change.

## Verification and Definition of Done

- [ ] Unit tests: Not applicable beyond a pure "should show fallback" predicate (empty local
      results) — cover that predicate directly.
- [ ] Widget/UI: fallback row appears only when local results are empty, tapping fires the
      correct `Intent` with the exact typed query.
- [ ] Integration: Not applicable — no persistence/SDK boundary beyond the `Intent`.
- [ ] Smoke on designated device: search a nonsense string, confirm fallback appears and opens
      the device's default browser/search app with the right query.
- [ ] No new lint/build failures.
