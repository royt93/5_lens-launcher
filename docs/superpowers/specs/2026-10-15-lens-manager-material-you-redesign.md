# Lens Manager Material You Redesign

**Date:** 2026-10-15
**Status:** Owner-approved direction; implementation pending
**Parent feature:** FISH-022

## Context

The first FISH-022 manager implementation passed behavior tests but did not establish a convincing Material You visual hierarchy. A TECNO KJ7 screenshot showed low-contrast status-bar icons on the pale surface, oversized title/action region, duplicated “Edit group” title and edit icon, a dominant wrapped Assign button next to an equally prominent text Remove action, and boxy group-count/active labels. The owner approved a screenshot-grounded redesign; changes remain on existing `dev` diff, with a later local commit and no push.

## Goals

- Make browse mode feel like a focused Material 3 destination: compact app bar, legible system bars/insets, calm grouped list, active lens state, and group counts as secondary metadata.
- Give edit mode a distinct selection state and predictable action placement.
- Use existing app theme roles and Material 3 components; avoid custom colors, extra dependencies, search, filter chips, and new data behavior.
- Preserve Room grouping behavior, canonical order, collapse persistence, selected stable IDs, and current-lens quick action.

## Approved interaction and hierarchy

### Browse

- Compact `MaterialToolbar` owns one stable “Manage lenses” title, navigation/back, and one trailing Edit action.
- Group headers read as section labels, not cards. Chevron and count are quiet secondary metadata; keep 48dp interaction targets and a TalkBack description with group/count/action.
- Lens rows use restrained Material state layers and typography. Active lens gets a subtle primary-container/tonal indicator and accessible “active lens” label; avoid a large square badge.
- Status and navigation bars use explicit, contrasting, theme-aware tint/background treatment. Set both status-bar and navigation-bar icon appearance deliberately for light and dark modes; verify icon tint against the actual surface on TECNO, including gesture/navigation-bar area and edge-to-edge insets. Do not rely on transparent bars inheriting contrast automatically.

### Edit

- Toolbar title shows selected count (“N selected”), with Done action in a clear, reachable position. Do not duplicate “Edit group” as title and control.
- Group list remains primary and scrollable.
- A bottom Material 3 action bar is pinned above navigation insets. Assign is one tonal primary action; Remove from group is secondary and visually quieter. Both meet touch-target and long-localization requirements; disable actions until selection exists and announce selection changes accessibly.
- Current-lens popup entry opens manager with that lens selected and edit mode active. It reuses the stable-ID bulk flow; do not add a separate one-off persistence path.

## Material 3 visual rules

- Use `Theme.Material3.DayNight`, existing `colorSurface*`, `colorOnSurface*`, `colorPrimary*`, `colorSecondaryContainer`, and `colorOutline*` roles; no hard-coded palette.
- Use existing Material components and app typography. Apply shape to meaningful surfaces only; do not turn every row/header/count into a rounded card.
- No custom gradient, large hero card, search, or filter chips.
- Support dynamic/day-night colors and system-bar contrast. Verify on TECNO screenshot, not only resource inspection.

## Files and boundaries

- `app/src/main/java/com/mckimquyen/ui/LensManagementActivity.kt`: mode transitions, contextual toolbar title/actions, bottom action visibility/insets, quick-entry selected lens.
- `app/src/main/java/com/mckimquyen/ui/LensGroupAdapter.kt`: active/group count binding, stable selection, accessibility state.
- `app/src/main/res/layout/act_lens_management.xml`, `item_lens_group_header.xml`, `item_lens_group_lens.xml`, `app/src/main/res/menu/menu_lens_group_edit.xml`: compact toolbar/list/edit bar layout.
- `app/src/main/java/com/mckimquyen/ui/ActHome.java`: pass active lens ID from current-lens group action; preserve manager entry and existing menu IDs.
- `app/src/main/res/values*/strings.xml`: only new concise labels needed; keep full locale parity, plural correctness, RTL.
- `app/src/androidTest/java/com/mckimquyen/ui/LensManagementActivityWidgetTest.kt`, `ActHomeLensManagementWidgetTest.kt`: assert browse/edit hierarchy, selected-lens launch, action enabling, counts, and stable-ID mutation behavior.
- `doc/feature.md`, `doc/task/inprogress/p2-fish-fish-022-lens-groups.md`: keep in-progress until final gates.

Reuse `LensGroupPolicy`, `LensWorkspaceDao.assignGroup`, `UtilSettings` collapsed-state persistence, and the existing `ActHome` intent target-lens flow.

## Verification

1. Add/update behavior assertions before production edits; verify meaningful RED for each changed behavior.
2. Build `assembleDevDebug` and AndroidTest APKs. Run focused manager/Home widgets, backup integration, migration integration, full JVM suite, lint, secret scan, `git diff --check`.
3. Use only locked TECNO KJ7 serial `115333744A005844`; direct `adb -s` commands, never Gradle `connected*` tasks.
4. Before every screenshot, inspect the displayed UI for ad overlays; stop immediately and wait for owner if one is present. Capture browse, edit-selected, group picker/create, and bottom-inset states. Confirm no status-bar contrast failure, truncation/wrapping, overflow, or occluded row/action.
5. Manually create a temporary group via current-lens and bulk paths, verify assignment/ungroup/collapse/switch, then restore test data safely. Commit locally only after audit and tests; do not push.
