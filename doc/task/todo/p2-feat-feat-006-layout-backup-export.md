# FEAT-006 — Export/import launcher layout

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Home-screen usability |
| Estimate | 5 SP |
| Risk | Low |
| Dependencies | DB-001 (already shipped) |

## Context and evidence

`model/AppOrganizationRules`, `model/PinnedZone`, and `AppPersistent`/
`AppPersistentDao` (Room) already hold everything that defines a user's
layout: favorites, folders, pinned zones, custom order, visibility, lock,
sort type. None of it survives an uninstall/reinstall or moves to a new
device — a real, commonly-reported launcher pain point, and one this app has
no story for today. No `ADS-001`/`INSIGHT-001`/`VIP-001` dependency: this is
pure data-layer serialization of state the app already owns.

## User story

As a user, I want to export my current app organization (favorites, folders,
pinned zones, order, hidden/locked state) to a file and restore it later —
after a reinstall, on a new device, or just as a backup before experimenting.

## Acceptance criteria

- [ ] Export: serialize the current `AppOrganizationRules`/`PinnedZone`/
      per-app `AppPersistent` state to a versioned JSON file (schema version
      field from day one — this data model will keep changing).
- [ ] Export target uses the Storage Access Framework (`ACTION_CREATE_DOCUMENT`)
      — no raw filesystem path, works under scoped storage on every supported
      API level (minSdk 25).
- [ ] Import: read the file back via `ACTION_OPEN_DOCUMENT`, match entries by
      the app's existing identity key (component name, not display name —
      matches `iconCacheKey`'s existing identity convention), skip entries for
      apps not currently installed instead of erroring the whole import.
- [ ] Import is additive/overwrite-with-confirmation, never silently
      destructive — show a preview of what will change before committing.
- [ ] Malformed/future-schema-version file: reject with a clear message,
      never partially apply and never crash.

## Required test matrix

- [ ] Unit: JSON (de)serialization round-trip for every field, schema-version
      mismatch handling, unmatched-app skip logic.
- [ ] Widget/UI: export/import entry points in `FrmSettings`, preview screen
      before import commit, error state for malformed file.
- [ ] Integration: real `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT` round
      trip (write a file, read it back) and a real Room round trip applying
      imported state.
- [ ] Smoke: export on the designated device, uninstall + reinstall the app,
      import, confirm layout matches (favorites/folders/zones/order/hidden/
      locked all restored).

## Verification and Definition of Done

- [ ] Full export→reinstall→import cycle verified live on the designated
      device with a non-trivial layout (multiple folders/pinned zones/hidden
      apps), not just a default/empty state.
- [ ] Exported file contains no data beyond what this app already stores
      locally (no PII beyond package names/user-chosen labels already visible
      in the UI).
