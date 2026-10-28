# FISH-022 — Lens groups and management

**Status:** Done (28/10/2026; nhóm lens đã chạy lại trên S24U, dialog chung audit riêng 8.7/10; không push)
**Started:** 2026-10-14
**Branch:** `dev`
**Design:** `docs/superpowers/specs/2026-10-14-lens-groups-design.md`
**Plan:** `docs/superpowers/plans/2026-10-14-fish-022-lens-groups.md`

## Scope

Add nullable Room-backed lens group labels; derive canonical group sections, collapse state, multi-select assignment/create/ungroup; add grouped Lens Management Activity, current-lens group menu action, and additive schema-v2 backup support for group and collapsed state. Preserve pager ordering and app layout semantics. Management UI follows compact Material You direction: browse shows group counts and active lens; edit controls and selection count appear only in edit mode. No search/filter chips.

## Implementation record

- Room v13 metadata, canonical policy, transactional group DAO, backup parse/export/import rollback, grouped manager browse, bulk edit, and current-lens manager menu entry implemented in existing `dev` diff.
- Activity extends `ActBase`, uses Material 3 toolbar/buttons and theme roles; manager includes accessible active/group-count/edit-selection labels.
- Added explicit translations for FISH-022 group error and UI state strings across supported locales; Arabic/Russian plural forms included.
- Regression fix: pass a copied selection set when syncing adapter state; avoiding alias-clear of shared mutable IDs.

## Verification

Historical verification below was on TECNO BG6 `118743744X002560`; do not treat it as a current-session device. Re-verification on 2026-10-28 used S24U `R5CX613VZBR` (SM-S928B) after owner changed active device.

- Instrumentation (direct `adb -s`): manager widget **19/19**, Home lens-management **26/26**, quick lens switch **12/12**, multi-lens **4/4**, backup+import integration **10/10**, DB migration **4/4**.
- JVM: full `testDevDebugUnitTest` **856/856**, `lintDevDebug` 0 errors / 33 warnings, `scripts/check-secrets.sh` exit 0, `git diff --check` clean.
- Audit fixes (test-first, each watched RED): unknown/blank `EXTRA_MANAGE_LENS_ID` no longer enters edit mode; edit mode + selection survive recreate (ID-validated against live Room rows); `LensWorkspace.createLens` now copies the source lens group (spec: duplicate copies group); widget-test bounds assertion waits for layout.
- New coverage: policy (Turkish default locale, createdAt tie-break, empty group disappears), DAO (empty/blank assign rejected, rename keeps group, deleting last member drops group, createLens copies group), backup (export JSON -> parse -> import round trip; v2 file without group fields), manager (night-mode bar contrast, case-insensitive reuse of group spelling, blank name rejected, collapse keeps selection).
- Manual smoke on device (no ad seen in any dump/screenshot): popup -> Manage group, edit/selection UI, create group, collapse/expand, ungroup, add 2nd lens, both lenses grouped, tap lens switches Home (active_lens_id verified), delete temp lens. Test data restored (single ungrouped Lens 1).
- Not smoked by hand: backup export/import via system file picker (consent dialog reappeared after reinstall; covered by 10 integration tests incl. real JSON round trip) and dark mode visually (covered by night-mode widget test).

## Re-verification 2026-10-28

- Fresh gate: `testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks` green, 1,307 unit; lint green; diff/secrets clean.
- S24U regression: 17 classes / 157 tests green, including manager 32, Room migration 5, backup import 21, accessibility 4, state restoration 3.
- S24U had no pre-existing package. After test, app archive restored exactly 15/15 files; locale `[]`, font `1.0`, Contacts denied.
- Manual smoke did not run: app displayed consent then language onboarding. Consent was cancelled, not accepted on owner's behalf. Audit: `doc/task/AUDIT_LENS_DIALOGS_2026-10-28.md` (8.7/10).

## Push decision

FISH-022's code is already on origin/dev from `db391c11`; this close is documentation-only. Do not push anything in this round because dialog audit score is below the required >9.0. Device smoke still requires owner to resolve onboarding consent.
