# SEARCH-017 — T9 ranks and limits like text search

Date: 2026-10-27. Story: `doc/task/todo/p3-search-search-017-t9-parity-with-text-search.md` (P3, 2 SP).

## Problem

T9 search (`T9SearchEngine.search`) returns every match, so a short digit query on a device with hundreds of
apps (e.g. `2` on 346 apps) pushes hundreds of rows into the result list. It also ignores the recent-launch
history that text search (`AppSearchEngine.search`) uses to order results.

## Evidence check (code read on 2026-10-27)

| Story claim | Current code | Action |
|---|---|---|
| T9 has no result limit | `T9SearchEngine.search` has no `limit`; text search has `DEFAULT_LIMIT = 24` | Fix |
| T9 does not take recent keys | `ActHome.java` passes `searchHistoryStore.recentKeys()` only to text search | Fix |
| T9 does not match package aliases | Already done by SEARCH-012 (`matchTier` tiers 4 and 5) | Record as done, no code |

## Decisions (owner, 2026-10-27)

1. **Limit:** T9 shares the text-search limit of 24. One shared named constant, no second number.
2. **Rank order:** identical to text search. Match tier, then favorite, then recent position, then open count,
   then label, then key. Recent history does not outrank match quality.

## Design

- `AppSearchEngine`: the private `DEFAULT_LIMIT` becomes a shared constant `SEARCH_RESULT_LIMIT` (24) that both
  engines use.
- `T9SearchEngine.search(candidates, query, recentKeys = emptyList(), limit = SEARCH_RESULT_LIMIT)`:
  `@JvmOverloads`, so the existing two-argument callers and tests keep compiling.
  - A non-positive `limit` returns an empty list, like text search.
  - Recent position is `recentKeys.indexOf(componentKey)`, first occurrence wins, absent means last.
  - Keys come from `AppSearchEngine.componentKey` so a work-profile app (`@u<id>`, FEAT-012) is not confused
    with the same app of the primary user.
  - Sort order inserts "recent position" between "favorite" and "open count". Everything else is unchanged,
    so with an empty `recentKeys` the order is exactly today's order.
  - A blank or non-digit query still returns an empty list.
- `ActHome.java`: pass `searchHistoryStore.recentKeys()` to the T9 call.

## Not in scope

- A "more results" row or any new UI or string (would need 17 locales).
- Passing `favoriteKeys` to T9. Favorites keep using `App.isFavorite`, as today.
- Matching the component class name in T9 (text search does; T9 never did).

## Risks

Low. No Room, no layout, no strings. The only behaviour change for existing users is that T9 results are
capped at 24 and recently launched apps come before less recently launched ones within the same match tier and
favorite status.

## Test plan

Unit (`T9SearchEngineTest`), red first:
- result count is capped at the shared limit and the cap keeps the best-ranked entries;
- a non-positive limit returns an empty list;
- within the same tier, a recently launched app precedes one that is not, and an earlier recent position beats a
  later one;
- favorite still outranks recent;
- a better match tier still outranks recent;
- empty `recentKeys` gives the same order as before;
- a work-profile app is not treated as recent because its primary-user twin is;
- blank and non-digit queries stay empty.

Contract: both engines expose the same limit constant.

Device: `ActHomeT9KeypadWidgetTest` and `AppSearchIntegrationTest` still pass on the locked device.
