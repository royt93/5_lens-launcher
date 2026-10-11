# Task 1 report — Define schema-v2 model and strict parser

**Status: done.** Model + parser implemented per brief, TDD RED→GREEN, committed locally on branch `worktree-agent-a41230bc3ec76454b` (fast-forwarded to `dev` tip `964a619` before starting — see "Environment note" below). Not pushed.

## Environment note (read first)

This worktree's branch started 48 commits behind `dev`, from before FISH-021 (`LensAppScope`, per-lens app scope/selection/frozen in `UtilSettings`) landed. The brief's interface (`scope: LensAppScope`) assumes FISH-021 already exists, which it does — just not in this stale worktree checkout. Fast-forwarded (`git merge --ff-only dev`, clean, no divergent commits lost) to pick up `LensAppScope.kt`, `LensAppScopeEditor.kt`, `UtilSettings` keys (`lens_app_scope_<lensId>`, `lens_app_selection_<lensId>`, `lens_frozen_<lensId>`), confirmed via `doc/task/done/p2-fish-fish-021-per-lens-app-scope.md`. No other ambiguity required asking — proceeded.

## Files changed

- `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt`
- `app/src/test/java/com/mckimquyen/model/LayoutBackupTest.kt`

## What changed

- `LayoutBackup.CURRENT_SCHEMA_VERSION = 2`; added `SCHEMA_VERSION_V1 = 1` constant (replaces the old single `CURRENT_SCHEMA_VERSION` use in `fromPersistent` and in tests, so v1 intent is explicit, not implicit via "whatever current is").
- `LayoutBackup` gained `lenses: List<LayoutBackupLens> = emptyList()` alongside the existing `entries` field (default `emptyList()`), so every v1-era call site (`fromPersistent`, existing tests) keeps compiling unchanged. Exactly one of `entries`/`lenses` is populated per instance, selected by `schemaVersion`.
- New `LayoutBackupLens(id, name, orderIndex, scope: LensAppScope, selectedAppIds: Set<String>, frozen: Boolean, entries: List<LayoutBackupEntry>)` with `toJson()` / `fromJson()` (returns `null` = malformed lens, never throws).
- `toJson()`: v1 (`schemaVersion < 2`) emits `{schemaVersion, exportedAt, entries}` as before; v2 (`>= 2`) emits `{schemaVersion, exportedAt, lenses}` — root `entries` key is omitted entirely for v2, per design ("no v1 root entries").
- `parse()`: reads `schemaVersion` first (same malformed/range/unsupported-future-version checks as before), then dispatches to `parseV1` (unchanged behavior: one bad entry is skipped, not fatal) or `parseV2` (strict).
- `parseV2` validation, each failure → `Malformed`: every lens object valid (`LayoutBackupLens.fromJson` non-null), lens IDs nonblank+unique, exactly one `id == LensWorkspace.DEFAULT_LENS_ID`, order indices unique and nonnegative. `LayoutBackupLens.fromJson` itself validates: nonblank id/name, `scope` is exactly `"ALL"` or `"SELECTED"`, `selectedAppIds` entries nonblank and unique, each entry object parses via a new `LayoutBackupEntry.fromJsonStrict` (component identity nonblank — same as existing lenient `fromJson` — plus `pinnedZone` must match a real `PinnedZone` enum name; unlike v1's lenient default-to-`NONE`, an unrecognized value is rejected). One bad entry/lens fails the whole v2 file — never a partial/best-effort import.
- Unknown additive JSON fields (root or lens level) are silently ignored (no explicit code needed — `org.json` doesn't care about extra keys), covered by a test.
- `selectedAppIds` for an app with no matching `entries` row round-trips intact (no filtering happens at parse time) — covered by a test, satisfying "preserve selection for uninstalled apps."
- Added a `ponytail:` comment on `fromPersistent`: it still returns a flat v1-shaped `entries` list tagged `SCHEMA_VERSION_V1` — real per-lens-grouped v2 export (reading the workspace's lenses + FISH-021 state) is explicitly out of scope here and lands in a later task (export orchestration). Nothing in this task's diff touches `LayoutBackupIo.kt` or `LayoutImportApplier.kt`.

## Tests

`./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LayoutBackupTest`: RED confirmed first (compile failure — `LayoutBackupLens`/`SCHEMA_VERSION_V1`/`lenses` unresolved), then **23/23 GREEN** after implementation.

New/changed cases: v1 round-trip made explicit (`SCHEMA_VERSION_V1` instead of generic `CURRENT_SCHEMA_VERSION`), known-v1-JSON parses with `lenses` empty, v2 round-trip (two lenses, mixed scope/selection/frozen/entries), v2 missing `lenses` key, missing default lens, duplicate lens IDs, duplicate order indices, negative order index, invalid `scope` string, duplicate selected IDs, blank selected ID, malformed entry inside a lens (rejects whole file, doesn't skip), unrecognized `pinnedZone` enum value (v2-only strictness), unknown additive fields at root/lens level tolerated, selected ID preserved for an app with no entry. All prior v1 tests (malformed JSON, future schema 999, one-bad-entry-skipped, missing top-level keys, default-field fallbacks) kept and pass unchanged.

Full regression: `./gradlew testDevDebugUnitTest` → **788/788 passing**, no other module touched.

## Concerns / things the next task owner should know

1. **`fromPersistent`/`toJson` combo is not yet valid v2 wire format.** `fromPersistent` still returns `schemaVersion=1`-tagged data (see ponytail comment) specifically so this gap doesn't surface silently — if a future change makes it emit `schemaVersion=2` without also populating `lenses`, `toJson()` would silently drop all entries (v2 branch ignores `entries`). Flagging so Task 2/5 (pure plan + export orchestration) replaces `fromPersistent`'s caller rather than just bumping its version number.
2. Did not touch `LayoutBackupIo.kt` or `LayoutImportApplier.kt` — confirmed these are Task 2/5 scope per the plan's file map, and per the "ads/IAP/consent/signing/release — out of scope" instruction (not applicable here, just noting adjacency boundary respected).
3. No device build/install/run performed, per explicit instruction — Task 1 has no device step in the plan either (first device step is Task 6/7).
4. Commit made locally only (`19b6a6f`, message `feat(backup): model multi-lens schema v2`, exact two files). Not pushed — the repo's step-done-gate memory policy requires an audit score >9 before push, which hasn't been run for a single-task slice; pushing wasn't requested.
5. Worktree branch was fast-forwarded onto `dev` (see Environment note) — this worktree is now even with `dev` plus the one new commit. Flagging in case the orchestrating process expected the worktree to stay pinned at its original stale commit.

## Reviewer fix — strict required nested fields (2026-10-11)

Reviewer found that a v2 lens missing `selectedAppIds` or `entries` parsed as a valid empty value. Added two regression tests; both failed before the parser fix with assertions at `LayoutBackupTest.kt:191` and `:201`. Changed `LayoutBackupLens.fromJson` to reject missing/wrong-type arrays. Re-ran `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LayoutBackupTest`; passed (25 tests, 0 failures). `git diff --check` passed. Fix commit: `ec03aff fix(backup): reject missing v2 lens fields`. Independent re-review pending.

Full report: `.superpowers/sdd/task-1-report.md` (this file).
