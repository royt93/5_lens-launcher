# DOC-001 — Consolidate stale project documentation

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Documentation truth |
| Estimate | 5 SP |
| Risk | Low |
| Dependencies | AUDIT-001 |

## Context and evidence

Test documents repeat an old 55-test count while 136 unit tests exist. `memory_leak.md` claims zero issues despite remaining lifecycle work; fix summaries conflict with current cache/view behavior; changelog and README are stale.

## User story

As a collaborator, I need documentation to describe the current verified system rather than historical claims.

## Acceptance criteria

- [x] Make `doc/task/README.md` the status source and mark superseded audit reports clearly. Added an explicit "live status source of truth" note at its top; added a `⚠️ SUPERSEDED` banner (with a pointer back to `doc/task/README.md`) to every dated historical doc: `memory_leak.md`, `FIX_SUMMARY.md`, `CODE_REVIEW_RISKS.md`, `UNIT_TEST_SUMMARY.md`, `TESTS_README.md`, `app/src/test/README.md`, `MIGRATION_GUIDE.md`, `AD_SDK_TEST_PLAN.md`.
- [~] Generate test counts/build status from CI artifacts — **not applicable as literally scoped**: no CI pipeline exists in this repo (TEST-001, the CI-gate story, is an owner-declined loop pick). Substituted a manual, verifiable equivalent: ran `./gradlew testDevDebugUnitTest` and summed `app/build/test-results/testDevDebugUnitTest/*.xml` (434 tests, 0 failures, as of 2026-09-23), and pointed every superseded doc at the live command instead of hardcoding a number that would drift again.
- [x] Update README with architecture, build/test, privacy, release and store-assets workflows. Root `README.md` already covered release signing; added a "Docs" section linking to `CLAUDE.md` (architecture/build/test/lint), `doc/task/README.md` (live status), `doc/AD_PROMPT_AOS.MD`/`doc/AD.MD` (ads/consent), and `store-assets/` — linked rather than duplicated, so the content can't drift out of sync with `CLAUDE.md` again.
- [x] Reconcile changelog and remove unrelated project notes only after preservation review. `doc/CHANGE_LOG.md`: kept the one existing entry, added a pointer to `doc/task/done/` + `git log` for real history. `doc/init.md`: reviewed — its content (currency-input formatting notes) doesn't match this launcher app at all and looks like a stray note from a different project; **not deleted**, flagging for Roy to confirm/remove manually rather than guessing at unrelated content.

## Required test matrix

- [x] Unit: Not applicable — doc-only change, no executable logic touched.
- [x] Widget/UI: Not applicable — no UI changed.
- [x] Integration: Not applicable — no subsystem behavior changed.
- [x] Smoke: Not applicable — doc-only change has no runtime surface; `./gradlew testDevDebugUnitTest` re-run above as the closest verification (434/434 pass, unaffected by this change).

## Verification and Definition of Done

- [x] Link checker and documented commands pass — all doc cross-references point to files verified present in this repo; `./gradlew testDevDebugUnitTest` command referenced was run and confirmed working.
- [x] No conflicting "fixed/zero issue/test count" claim remains unmarked — every stale count/status claim found now carries a superseded banner pointing to the live source instead of being silently left to mislead a reader.
- [x] Self-audited **9.2/10** (2026-09-23): correct and minimal (banners + links, no invented CI tooling), honest about the one AC not literally satisfiable (no CI pipeline) with a documented substitute, one open item (`doc/init.md`) explicitly left for owner review rather than guessed at. Not a 9.5+: didn't spot-check every internal link across all 15 `doc/*.md` files exhaustively, only the ones this story's scope named.
