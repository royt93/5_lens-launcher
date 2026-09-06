# FEAT-002 — Add folders, favorites and pinned zones

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | idea |
| Epic | App organization |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | CORE-001, DB-001 |

## Selection decision

- Picked by the owner on 2026-09-05 as the next feature loop after FEAT-001.
- Implemented in the same audited wave as the CORE-001 and DB-001 foundations.

## User story and value

As a user, I want stable personal zones and optional local categories without losing the Fisheye spatial model.

## Acceptance criteria

- [x] Favorite, pin/unpin, folder and order edits persist against stable component IDs.
- [x] Organization is entirely explicit; no automatic suggestion path can rearrange committed placement.
- [x] Rows are retained while an app is absent, so the same component ID recovers its organization metadata after reinstall.
- [x] Long-press/overflow actions expose move-earlier/later equivalents to drag ordering for keyboard and accessibility users.

## Required test matrix

- [x] Unit tests cover folder normalization, zone/favorite ordering, batch conflicts, migration and reinstall recovery.
- [x] Widget/UI test verifies rendered organization summary and the accessible long-press entry point.
- [x] Integration coverage includes Room migration/recovery, StrictMode, app-event refresh and installed launcher/settings flows.
- [x] Exact candidate smoke passed on the designated TECNO KJ7; evidence is in `AUDIT_FEAT_002_2026-09-06.md`.

## Verification and Definition of Done

- [x] Ordering/conflict/migration and rendered organization-state tests pass.
- [x] Stable-ID restore and organization-aware sorting tests pass; package/app-state integration remains green.
- [x] TECNO suite covers the long-press entry point, lifecycle recreation, cold process startup and launcher/settings responsiveness.
