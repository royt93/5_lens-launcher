# Workspace Backup Selected Scope Validation

## Problem

Schema-v2 workspace backup parser accepts a lens with `scope = SELECTED` and `selectedAppIds = emptySet()`. Import restores those preferences successfully, while `LensAppScope.filter` returns no apps. This creates a blank lens despite `LensAppScopeEditor` maintaining the invariant that a selected-scope lens has at least one selected app.

## Decision

Treat `SELECTED` with an empty `selectedAppIds` list as malformed input at the strict v2 parser boundary. Return `LayoutBackupParseResult.Malformed` for the whole backup. Do not normalize to `ALL`, select an app automatically, partially import, or add a warning/confirmation UI.

## Scope

- Validate the cross-field constraint in `LayoutBackupLens.fromJson`.
- Add parser unit coverage for empty selected IDs under `SELECTED`; retain acceptance of `ALL` with empty selected IDs and `SELECTED` with non-empty IDs.
- Add an instrumentation regression proving malformed v2 input is rejected before mutation and leaves existing Room workspace and preferences unchanged.
- No schema version change, migration, dependency, UI change, or unrelated cleanup.
- Work only on the existing `dev` branch.

## Acceptance Criteria

1. A v2 lens with `scope = SELECTED` and an empty `selectedAppIds` array makes `LayoutBackup.parse` return `Malformed`.
2. Existing valid combinations continue to parse: `ALL` plus empty selected IDs, and `SELECTED` plus non-empty IDs.
3. Applying an invalid v2 backup through `WorkspaceBackupImportCoordinator` returns `Malformed` and does not mutate Room or SharedPreferences.
4. Focused unit and TECNO instrumentation tests pass; `git diff --check` passes; no branch, worktree, unrelated docs, or feature changes.
