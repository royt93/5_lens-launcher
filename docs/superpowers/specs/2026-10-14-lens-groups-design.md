# FISH-022 — Lens Groups Design

**Date:** 2026-10-14
**Status:** Owner-approved design; implementation not started
**Branch policy:** Work only on the existing `dev` branch.

## Goal

Help users organize and switch between many lenses without changing current lens paging order or app-layout semantics.

## Data model and persistence

- Add nullable `groupName: String?` to `LensWorkspace` as `GROUP_NAME`.
- Bump Room from schema 12 to 13 with a migration adding nullable `GROUP_NAME`; existing lenses migrate as ungrouped. Export the version-13 schema snapshot.
- A lens belongs to at most one group. There is no separate Group table: group sections are derived from non-null `groupName` values on lenses. A group with no lenses disappears automatically.
- Normalize names by trimming surrounding whitespace. Compare names case-insensitively for grouping and duplicate detection. When a user assigns a selected lens set to a name matching an existing group, reuse the existing group's display spelling. Reject blank names rather than creating a blank group.
- Renaming, duplicating, deleting, and reordering lenses preserve or intentionally remove `groupName`: rename/reorder preserve; duplicate copies the source lens's group; delete removes the lens and therefore only removes the group from the derived view when it was the last member.

## User experience

- Replace the current single-choice lens switch dialog opened by tapping the lens name with a dedicated lens-management Activity.
- Default browsing mode shows a `No group` section plus one collapsible section per group. Lens order inside each section follows existing global lens order (`ORDER_INDEX`, then `CREATED_AT`); paging order remains unchanged.
- Tapping a lens switches `ViewPager2` to that lens and closes the management Activity, returning to Home.
- Edit mode supports selecting multiple lenses and three bulk operations: assign to an existing group, create a group from the selection, or remove group assignment. Selection across collapsed sections remains intact until operation completion or explicit cancel.
- Existing lens popup retains quick actions to assign/change/remove the current lens's group and to open the management Activity. Group metadata changes update the popup/menu and Activity promptly.
- Section collapsed/expanded state is UI preference state, not lens identity. It is saved locally and also included in workspace backup so a restore reproduces the management view.

## Backup compatibility and import

- Extend schema-v2 lens objects additively with optional `groupName`; absence means `null`, so existing v2 backups continue to parse. New exports always write the field, using JSON null for ungrouped lenses.
- Extend the v2 root additively with optional `collapsedGroupNames: string[]`; absence means no collapsed sections. New exports always write the array.
- Strict validation rejects non-string/non-null group names and blank names after trimming. Repeated names across lenses are expected group membership. `LensGroupPolicy.canonicalKey(name)` is `name.trim().lowercase(Locale.ROOT)`. Canonicalization is shared by parsed backup lenses and Room/UI lenses; for backup, process lenses by `orderIndex` then JSON array order, and for Room/UI use `ORDER_INDEX` then `CREATED_AT`. The first trimmed spelling per key becomes display spelling; normalize member lenses and `collapsedGroupNames` to it. Case/whitespace variants in a backup are valid and collapse into one group.
- `LensGroupPolicy` exposes pure overloads for `LayoutBackupLens` and `LensWorkspace` so strict parser and UI/DAO paths share the same key and canonical-label implementation without Android/Room dependencies.
- Import replaces all existing lenses and preferences, so collapsed-group state is also replaced wholesale. It participates in the existing preference snapshot/restore compensation and rollback path; no partially applied group state is allowed.
- No schema-version bump: v2 already has additive-field compatibility and older v2 files remain readable.

## Non-goals

- No nested groups, groups containing groups, independent group ordering, group colors/icons, or group-level app scope.
- No change to active lens, pager order, shortcuts, `LensAppScope`, global app visibility, search ranking, ads/VIP, or `store-assets`.
- No retention of empty groups; no independent group entity or Room table.

## Error handling and lifecycle

- Reject empty/whitespace-only group names at every UI and import boundary.
- Group assignment and lens selection use stable lens IDs, never visible list positions, so paging/order changes cannot redirect writes.
- DAO updates are transactional for bulk lens assignment. If import preferences cannot commit, the existing coordinator restores Room and SharedPreferences snapshots, including collapsed groups.
- Management Activity observes the existing lens/workspace update event; clear adapters, callbacks, and observers on destroy. Switching back from the manager must select the requested lens only if it still exists; otherwise retain/reconcile the current active lens.

## Test strategy

- **Unit:** normalization/case-fold grouping; whitespace/duplicate validation; derived groups/order; bulk operation plan; v2 parser compatibility for old files and new optional fields.
- **Room/integration:** migration 12→13 preserves existing rows and assigns null group; CRUD/duplicate/delete/bulk group operations; deterministic section ordering.
- **Backup integration:** export/import group labels and collapsed state; old v2 file with absent fields defaults safely; invalid group names return malformed; preference-commit failure restores Room and UI preferences.
- **Widget/UI:** manager renders grouped and ungrouped lenses, collapse/expand, select lens and return Home, bulk select across sections, assign/create/ungroup, current-lens popup quick action, TalkBack labels/actions, recreation/destroy cleanup.
- **Device smoke:** TECNO KJ7 preferred; TECNO BG6 only when KJ7 absent. Use explicit serial with direct `adb` commands; never run Gradle `connected*` tasks.

## Acceptance criteria

1. Existing schema-v12 users open with all lenses ungrouped and no lost app layouts/preferences.
2. Users can create/reuse a group and assign/unassign one or many lenses; blank names and case-insensitive duplicates do not create duplicate groups.
3. Group sections collapse/expand and lens selection remains correct across collapse state.
4. Tapping a lens in the Activity switches to exactly that lens without changing global lens order.
5. Existing lens popup supports current-lens group management and opens the Activity.
6. New v2 backups round-trip `groupName` and collapsed sections; older v2 backups import with all lenses ungrouped and sections expanded.
7. Failed import preferences commit restores both Room lens metadata and collapsed preferences.
8. Required tests, lint, secret scan, diff check, and TECNO smoke pass before audit score/push decision.

## Alternatives rejected

- **SharedPreferences for `groupName`:** rejected because group labels are workspace identity metadata, must duplicate/delete/import atomically, and otherwise can drift from Room.
- **Separate Group table:** rejected because groups are only labels attached to lenses and empty groups should disappear; independent ordering/metadata is explicitly out of scope.
- **Leave collapsed state local-only:** rejected by owner choice; collapsed sections must travel with the workspace backup.
- **Keep the existing dialog and add grouped headers:** rejected when the owner selected an Activity replacing the dialog, due to bulk-management needs.
- **Reject repeated group labels across lenses:** rejected because repeated labels are the normal representation of group membership; only the canonical display spelling must be unique.
- **Preserve raw case/whitespace variants through import:** rejected because Room/UI/backup would not share one deterministic grouping key.
