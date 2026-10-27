# ARCH-002 — Split ActHome into focused collaborators (design)

Date: 2026-10-27. Story: `doc/task/todo/p3-arch-arch-002-split-act-home-god-class.md`. Device: TECNO KJ7 `115333744A005844` only (screen awake and unlocked). Goal after this story: `FISH-038b` (real drag-and-drop between lenses).

## Evidence check (code, 2026-10-27)

- `ActHome.java` is **2.454 lines** now (story says 2.358; FISH-038 and earlier work added lines). 55 fields, 34 references to `lensViews`, 30 `@VisibleForTesting` members.
- Dependencies `CORE-004` and `UI-028` are **done** (README).
- Safety net: **145 `@Test` in 35 `ActHome*` androidTest source files**, 75 androidTest classes touch `ActHome`, 5 unit tests. Most-used members from tests: `findViewById`, `onLensMenuItemSelected` (20), `quickActionsMenuForTest`, `lensGroupDialogForTest`, `toggleEditModeForTest`, `selectLensForGroupEdit`, `searchResultAdapterForTest`, `setPermissionGrantedForTest`.
- Cluster sizes (approx lines): lens menu + dialogs 597, search + T9 + results 386, quick action + contacts + permissions 250, lens bind/pager 338, scope/`assignApps` 135. Existing collaborator pattern from ARCH-001: `SettingsDialogCoordinator`, `SettingsMenuDispatcher` + `SettingsMenuHost` interface, `SettingsAdVipDelegate` in `ui/settings/`.

## Decisions (owner, 2026-10-27)

1. Scope: **all five clusters**, one small commit each.
2. Safety: run the **whole `ActHome*` androidTest set on KJ7 after each cluster**.

## Design

Pure refactor: **no behavior change, no new feature, no string or layout change.** Public/package-private members that tests use keep their names and signatures; they delegate to the new collaborators.

- Each collaborator is a plain Kotlin or Java class in `com.mckimquyen.ui.home` that receives what it needs through a small host interface implemented by `ActHome` (same shape as `SettingsMenuHost`). It never holds the Activity beyond its lifetime and is released in `onDestroy`.
- Cluster order, chosen so the highest-risk shared state is done once and the rest shrink around it:
  1. **`LensViewHolder`** (single owner of `lensViews`, the active page, bind/rebind, pending pinch, resurrect snackbar). Everything else asks it for the active `LensView`. Done first because the other clusters read `lensViews`.
  2. **`HomeQuickActionDelegate`** (quick-action row, flashlight, Wi-Fi SSID, DND, contacts row, permission results, `REQUEST_CODE_*`).
  3. **`HomeSearchController`** (search setup, chips, transition listener, results rendering, web fallback, launch of a result, system-bar harmonizing) with **`T9KeypadBinder`** extracted from it.
  4. **`LensMenuCoordinator`** (lens management menu, anchors, rename/create/delete/apps/hidden dialogs, export, freeze, Smart Focus).
  5. **Insets/chrome** (`applyHomeColumnInsets`, predictive back preview, status/nav bar, `updateModeVisibility`, `setBackground`) and the **`assignApps`/scope** block into a `HomeAppListController`.
- Pure logic found inside the clusters is extracted and unit tested without Android types: e.g. the search-results state decision (`isEmptyQuery`, `showAllAppsFallback`, `hasResults` and which headers show), `isT9NumericQuery`, `sortedByLabel`, `isSameAppList`, `containsLens`, the lens-id-for-position lookup.
- `ActHome` ends below 1.500 lines and keeps only lifecycle, view wiring and delegation.

## Behaviour-preservation method (this is what makes it safe)

1. **Characterization first.** Before moving a cluster, add unit or widget tests that pin the behavior of any pure decision that is not already covered, run them green on the old code, then move the code and keep them green.
2. **One cluster per commit**, each commit builds and passes `testDevDebugUnitTest` + `lintDevDebug`.
3. **After each cluster, run all 35 `ActHome*` androidTest classes** (one class at a time with `timeout`, never a Gradle `connected*` task) on KJ7, plus the non-prefixed classes that touch the moved code. Zero regression allowed; a failure stops the work and is fixed before the next cluster.
4. Back up and restore app data on KJ7 byte for byte, as in earlier rounds; the screen must be awake and unlocked.
5. Mutation-check the extracted pure logic only; do not mutate view wiring.

## Out of scope

Any visible change, Room/prefs schema, string changes, `LensView` internals, `ActSettings`, and FISH-038b itself (next story).

## Risks and mitigations

- Hidden coupling through shared fields (`listApp`, `utilSettings`, `currentLenses`): pass through the host interface, never copy.
- Lifecycle leaks: every collaborator exposes `onDestroy()`, called from `ActHome.onDestroy`; a test per collaborator asserts it drops its references.
- Tests that reach private/package members by name: keep those members as thin delegates; do not rename.
- Long-running device matrix: run the full `ActHome*` set after each cluster and record counts; stop on the first red.

## Limitations to record

The story's "one PR per cluster" is delivered as one commit per cluster on `dev`. Whether `ActHome` reaches the 1.500-line target is measured and reported, not assumed.
