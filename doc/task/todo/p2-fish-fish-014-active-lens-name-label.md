# FISH-014 — Active lens name label on home screen

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
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

- [ ] A compact lens name label appears directly above the dot indicator when
      at least two lenses exist and Fisheye mode is active.
- [ ] Single-lens installs show no label (`GONE`), matching pre-existing UI.
- [ ] The label updates immediately on swipe, rename, create, delete, list
      reload, and Activity recreation.
- [ ] Long-pressing the label opens the existing lens-management menu,
      matching the dots indicator.
- [ ] TalkBack announces the active lens name and exposes the long-click
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

- [ ] Unit tests cover pure resolution logic (matching active id, fallback,
      null/empty inputs).
- [ ] Widget/UI tests cover visible behavior (single-lens hide, multi-lens
      show, swipe update, rename update, delete-to-one hide, long-press menu).
- [ ] Integration tests cover real Room + SharedPreferences + Activity
      recreation + ViewPager2 boundary (seed multi-lens, launch, verify
      restored active lens label, delete, verify fallback and label hide).
- [ ] Smoke test on the session-locked device; record model, Android version,
      build, and timestamp.

## Verification and Definition of Done

- [ ] Required test layers pass (unit, widget, integration).
- [ ] Smoke checklist signed off on real hardware.
- [ ] Zero new lint or build warnings/failures.
- [ ] Post-change audit scores `> 9.0/10` before push.
