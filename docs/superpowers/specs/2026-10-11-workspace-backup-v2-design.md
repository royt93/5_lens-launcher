# FEAT-006 follow-up — Multi-lens workspace backup v2

## Goal

Extend the existing layout backup so users can move or restore a complete multi-lens launcher workspace, including each lens's app layout and FISH-021 scope/frozen state. Preserve JSON + Android Storage Access Framework (SAF), and keep schema-v1 files importable.

## Current behavior and gap

`model/LayoutBackup` schema v1 serializes app organization rows without a lens ID. `LayoutBackupIo.exportAsync` reads `APP_PERSISTENT` rows, while `LayoutImportApplier` applies matched rows using the installed app's current `lensId`. FISH-021 stores per-lens app scope, selected component IDs, and frozen state in `SharedPreferences`; these are not included. Multi-lens rows cannot be reliably restored as a workspace.

## Scope

- Newly exported files use schema version 2.
- Export every lens: stable ID, name, order index, and per-lens app layout rows.
- Export each lens's scope (`ALL`/`SELECTED`), selected stable component identifiers, and frozen flag.
- Import v2 by replacing the complete workspace and backed-up layout after explicit preview and confirmation.
- Preserve `openCount`, `paletteColor`, notification counts, and other user data outside layout.
- Keep v1 import supported: apply app rows to default lens, leave lens list and FISH-021 settings unchanged.
- Retain backup entries and selected IDs for apps not installed; they remain invisible until installed again.
- Reject malformed/unsupported input before writes; never partially apply a backup.
- No ads, IAP, consent, signing, release, or unrelated feature work.

## Backup format

Schema v2 root contains `schemaVersion`, `exportedAt`, and a `lenses` array; it has no v1 root-level `entries`. Each lens contains `id`, `name`, `orderIndex`, `scope`, `selectedAppIds`, `frozen`, and its app-layout `entries`. Entry fields retain v1 names: package/component identity, order, visibility, lock, favorite, folder, and pinned zone. Stable component IDs use `AppPersistent.generateIdentifier(packageName, componentName)`. The default lens ID remains `default`; IDs restore unchanged, intentionally replacing any local lens with the same ID.

Parser validates root shape/schema, unique nonblank lens IDs, exactly one `default`, nonblank names, unique nonnegative order indices, valid scope, unique nonblank selected IDs, and valid app entries. Any invalid v2 input returns typed parse failure and leaves stores untouched. Unknown fields may be ignored; higher schema versions are unsupported.

## Export flow

1. Read all lenses in persisted order.
2. Read all `APP_PERSISTENT` entries grouped by lens, plus each lens's scope, selection, frozen state.
3. Serialize one snapshot as schema v2 and write via SAF.
4. Any required read or stream-write failure returns failure, never success.

## Import flow

### Schema v2

1. Parse and validate whole file before writes.
2. Preview lens count/app-entry counts to be replaced and warn clearly that current workspace/layout will be replaced. Cancel means no mutation.
3. Snapshot current workspace/layout rows and exact per-lens scope/selection/frozen and active-lens preference values.
4. Replace lens rows and per-lens app-layout rows in one Room transaction; restore IDs unchanged including `default`.
5. Preserve `openCount`, `paletteColor`, and other fields outside backup schema. Remove stale old app-layout rows for replaced lenses when absent from backup, without changing those preserved fields. Keep entries for uninstalled apps in Room and selection IDs.
6. Since Room and SharedPreferences cannot share a platform transaction, commit DB first, then synchronously commit preference replacement on IO. If prefs fail, transactionally restore prior DB snapshot and restore prior prefs; report failure. If rollback fails, report distinct recovery-required error with diagnostics. A process death between DB and prefs commits remains an atomicity ceiling; startup reconciles an active lens missing from DB to `default`.
7. On success refresh active lens UI and dynamic shortcuts.

### Schema v1

Keep existing behavior: apply rows only to `default`, leave lens list and all FISH-021 preferences unchanged, preserve `openCount`/`paletteColor`, and retain installed-app matching/skip behavior. Do not target whichever lens happens to be active.

## Identity and ordering

- v2 lens IDs are authoritative and restored unchanged.
- `orderIndex` is unique and nonnegative; reject duplicates/negative values.
- App identity uses stable component IDs, never row positions or labels.
- Retain selection IDs for uninstalled apps.
- Restore `active_lens_id` if present; otherwise use `default`.
- Refresh dynamic shortcuts after successful import using restored names/order and existing count limit.

## Failure handling and lifecycle

- Unsupported schema, malformed data, invalid lens state, read/DB/prefs/SAF failure never shows success.
- DB replacement is one Room transaction. Apply prefs only after DB success; compensate by restoring both prior snapshots if prefs commit fails.
- Report a separate recovery-required error if compensation fails.
- Keep disk/DB work off main, return callbacks on main, dismiss dialogs/release listeners on Activity destruction.

## Tests

- **Unit:** v1 compatibility; v2 round trip; malformed/future schema; duplicate lens/default/order/selection; missing or wrong-type required arrays; invalid scope/entries; unknown fields; uninstalled app entries/selection retained.
- **Widget:** v2 preview and replacement warning; cancel leaves workspace unchanged; confirm refreshes; v1 import leaves lenses and scope/frozen state unchanged.
- **Integration:** Room transaction replaces multi-lens rows; preserves usage/palette fields; restores scope/selection/frozen/active lens; refreshes shortcuts; prefs failure triggers DB rollback; rollback failure reports recovery-required; v1 applies to default only.
- **Device smoke:** export → mutate multi-lens workspace → import → verify names/order/scope/selection/freeze/layout and cancel path. Record locked device/model/build SHA.

## Acceptance criteria

- New exports include every lens's layout and FISH-021 state.
- v2 import restores complete workspace with explicit destructive confirmation and DB transaction.
- v1 remains backward compatible and preserves current workspace and per-lens FISH-021 state.
- `openCount`, `paletteColor`, and other non-layout user data survive v2 replacement.
- Uninstalled app identities and selection IDs are retained.
- Unit, widget, integration, smoke and applicable regression checks pass; lifecycle resources are cleaned up.

## Non-goals

Cloud sync, encryption/password protection, automatic backups, downgrade support, selective lens import, merge mode, ID remapping, or new UI beyond safe preview/confirmation.
