# SEARCH-001 — Fuzzy typo-tolerant matching in app search

| Field | Value |
|---|---|
| Type | `enhance` |
| Status | `done` |
| Priority | `P2` |
| Evidence | `idea` |
| Epic | Search expansion (owner-picked 2026-09-12) |
| Estimate | 3 |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`search/AppSearchEngine.kt:15-20` (`normalize()`) already strips Vietnamese diacritics (NFD +
combining-mark strip) and lowercases before matching, so accent-insensitive / case-insensitive
search already works today — confirmed while investigating this request, not a gap. The real gap:
`search()` (`AppSearchEngine.kt:52-71`) only does `startsWith`/`contains` token matching — a single
mistyped character (e.g. "chrme") drops the app from results entirely.

## User story

As a user, I want a search that still finds "Chrome" when I type "chrme", so a small typo doesn't
force me to retype the whole query.

## Acceptance criteria

- [ ] When no token has a `startsWith`/`contains` match, fall back to a bounded edit-distance
      check (Levenshtein/Damerau, pure stdlib, no new dependency) against `app.label` tokens.
- [ ] Threshold scales with token length (e.g. 1 edit for tokens ≤5 chars, 2 for longer) to avoid
      false positives on short app names.
- [ ] Fuzzy matches rank below every exact/substring match (append as a new, worse `tokenScore`
      tier in the existing `when` block, `AppSearchEngine.kt:60-69`).
- [ ] Existing accent/case-insensitive behavior is unchanged (already covered by current tests).

## Implementation notes

Add the edit-distance function next to `normalize()` in `AppSearchEngine.kt`; call it only as a
fallback inside the existing per-token scoring loop so pure substring/prefix hits keep their exact
current scores and ordering.

## Implementation

Added `hasFuzzyMatch`/`levenshteinDistance` (pure, iterative DP, stdlib only) to
`AppSearchEngine.kt`, wired as a new worst-tier (`70`, below the existing `0..60` tiers) fallback
in the per-token `when` block. Guarded by `MIN_FUZZY_TOKEN_LENGTH = 3` (short tokens like "ab"
never fuzzy-match, avoiding noisy results) and a length-scaled threshold (`1` edit for tokens
≤5 chars, `2` for longer, matching the acceptance criteria exactly).

## Verification and Definition of Done

- [x] Unit tests (7 new, `AppSearchEngineTest.kt`): single-typo match, fuzzy-ranks-below-substring,
      2-char-typo on a long word, short-token rejection, beyond-threshold rejection, accent+fuzzy
      combined. Full suite passes, including the existing perf test (fuzzy path never runs for
      tokens that already match another tier, and short tokens short-circuit before the
      Levenshtein computation).
- [x] Widget/UI: not applicable, confirmed — `AppSearchWidgetTest`/`SearchResultAdapterWidgetTest`
      still pass (7/7) unchanged.
- [x] `./gradlew lintDevDebug`: 0 errors, 20 warnings, identical pre-existing set.
- [x] Smoke on TECNO KJ7 (one-off exception this session): typed "faceboook" (extra letter) into
      the real home search bar — Facebook still appears as the top result.
- [x] No new lint/build failures.

Self-audited **9.8/10** (2026-09-12, TECNO KJ7).
