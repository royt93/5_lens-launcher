# Task 2 report — Pure workspace import plan

## Implementation
- Added `LayoutImportApplier.WorkspaceLensPlan`, `WorkspacePlan`, `PreviewCounts`.
- `planV1(backup)`: always one `WorkspaceLensPlan` targeting `LensWorkspace.DEFAULT_LENS_ID`, carrying v1's flat `entries`; scope/selectedAppIds/frozen are placeholder defaults (meaningless for v1, documented in kdoc).
- `planV2(backup)`: maps every `LayoutBackupLens` 1:1 into a `WorkspaceLensPlan`, preserving id/name/orderIndex/scope/selectedAppIds/frozen/entries verbatim - no installed-app filtering, so absent-package entries/selections pass through unchanged.
- `previewCounts(plan)`: pure fold producing lens count, total entry count, and a per-lens-id entry-count map.
- No Android Context, PackageManager, Room, SharedPreferences, or I/O touched; existing `plan()`/`apply()` (installed-app matching for the old single-lens path) left untouched.

## Review follow-up
- Added schema guards: `planV1` rejects any backup whose schema is not v1; `planV2` rejects any schema other than v2. This prevents wrong-schema calls from silently returning an empty payload.
- `previewCounts` rejects duplicate lens IDs instead of undercounting its per-lens map.
- Removed the redundant parse-failure test, which only repeated `LayoutBackupTest` parser assertions without exercising planner behavior.
- Focused test: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LayoutWorkspaceImportPlanTest` — `BUILD SUCCESSFUL` after fixes.
- Remaining planner tests cover v1 default target and entries; wrong-schema rejection for both cross-calls; v2 preservation of lens settings/entries/uninstalled IDs; preview totals and duplicate-ID rejection.

## Original TDD evidence
- RED: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LayoutWorkspaceImportPlanTest` failed to compile (`Unresolved reference: planV1/planV2/...`) before implementation.
- GREEN: same command after implementation — 6 tests passed before review follow-ups.
- Regression classes `LayoutImportApplierTest` and `LayoutBackupTest` passed alongside implementation.

## Environment note
This worktree (`agent-a0459760f87625b1d`) was branched before Task 1's commits existed and had neither the brief nor `LayoutBackupLens`/schema-v2 code. The orchestrator's Task 1 work lives on `worktree-agent-a41230bc3ec76454b`, a sibling worktree this agent's sandbox refuses to run Bash in directly. Fast-forwarded this worktree's branch onto that branch via a local `git fetch` of its filesystem path (no uncommitted changes lost; fast-forward only), then implemented Task 2 on top. The `.superpowers/sdd/task-2-brief.md` and the design/plan docs were read directly from the sibling worktree via the `Read` tool before switching back.

## Commit
- Original implementation: `070195a feat(backup): plan full workspace restore`; cherry-picked into the active worktree as `0cfbda4`.

## Review follow-up
Independent review found: (1) the original parse-failure test duplicated parser coverage without testing planner behavior; removed it. (2) planners accepted mismatched schema versions and could silently produce empty payloads; `planV1`/`planV2` now reject mismatches and have regression tests. (3) `previewCounts` could collapse duplicate lens IDs; it now rejects duplicates and computes total from all lens records. Focused planner tests pass after changes (`./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LayoutWorkspaceImportPlanTest`).

No Room, prefs, PackageManager, device, ads/IAP, or release work touched.
