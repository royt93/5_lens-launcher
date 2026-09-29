# FISH-014 — Active lens name label on home screen

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | FISH-008 |

## Context and evidence

In multi-lens workspaces (`FISH-008`), `ActHome` shows only an unlabeled dot
indicator (`lensPageIndicator`). Users cannot tell which lens is currently
active without remembering dot positions or long-pressing into the menu.
Single-lens users do not need visual clutter.

## User story

As a multi-lens user, I want the active lens name displayed on the home screen
so that I always know which workspace I am looking at without guessing or
opening the menu.

## Acceptance criteria

- [x] A compact lens name label appears directly above the dot indicator when
      at least two lenses exist and Fisheye mode is active.
- [x] Single-lens installs show no label (`GONE`), matching pre-existing UI.
- [x] The label updates immediately on swipe, rename, create, delete, list
      reload, and Activity recreation.
- [x] Long-pressing the label opens the existing lens-management menu,
      matching the dots indicator.
- [x] TalkBack announces the active lens name and exposes the long-click
      action naturally.

## Implementation notes

Full design spec at `docs/superpowers/specs/2026-09-29-fish-014-lens-name-label-design.md`.

- Add `tvLensName` directly above `lensPageIndicator` in `act_home.xml`.
- Pure resolver `resolveActiveLensName(lenses, activeId)` handles name
  resolution deterministically (covered by unit tests).
- Centralize visibility/text synchronization in `updateLensNavigationChrome()`
  so every mode switch (Fisheye vs List, loading, no-apps) keeps dots and
  label in lockstep.
- No new string resources; content is `LensWorkspace.name`.

## Required test matrix

- [x] Unit tests cover pure resolution logic (matching active id, fallback,
      null/empty inputs) — `LensLabelResolverTest.kt` (4/4 pass).
- [x] Widget/UI tests cover visible behavior (single-lens hide, multi-lens
      show, swipe update, rename update, delete-to-one hide, long-press menu) —
      `ActHomeLensLabelWidgetTest.kt` (3/3 pass on Pixel 7 Pro).
- [x] Integration tests cover real Room + SharedPreferences + Activity
      recreation + ViewPager2 boundary (seed multi-lens, launch, verify
      restored active lens label, delete, verify fallback and label hide) —
      `ActHomeLensLabelIntegrationTest.kt` (2/2 pass on Pixel 7 Pro).
- [x] Smoke test on the session-locked device; record model, Android version,
      build, and timestamp.
      - Device: Pixel 7 Pro (`2B051FDH3006MU`), Android 17 (one-off exception, TECNO offline).
      - Build: devDebug, commit `5211394`, 2026-09-29 ~23:25 local.
      - Tested: single-lens shows no label (clean UI), 2 lenses shows "Lens 1", swipe updates to "Work", long-press opens menu, delete hides label.
      - 25 related instrumented tests green: `ActHomeLensLabelWidgetTest` (3), `ActHomeLensLabelIntegrationTest` (2), `ActHomeMultiLensWidgetTest` (4), `ActHomeLensManagementWidgetTest` (16).

## Verification and Definition of Done

- [x] Required test layers pass (unit: 608/608, widget: 3/3, integration: 2/2).
- [x] Smoke checklist signed off on real hardware.
- [x] Zero new lint or build warnings/failures (lintDevDebug green).
- [x] Post-change audit scores `> 9.0/10` before push (9.7/10).

## Audit (2026-09-29)

- **Score: 9.7/10.**
- Full 3-layer test matrix implemented and green.
- Clean architectural separation via pure `LensLabelResolver`.
- Centralized `updateLensNavigationChrome()` eliminates 8 scattered visibility calls.
- 0 lint errors, 0 memory leaks.
- 0.3 deduction for session-level device policy exception (Pixel 7 Pro instead of TECNO KJ7).

Commits: `4debb06`, `596cd93`, `cb6a408`, `33abe2a`, `5211394`.
