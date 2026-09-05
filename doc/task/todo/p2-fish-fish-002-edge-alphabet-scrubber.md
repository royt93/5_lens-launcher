# FISH-002 — Add an edge alphabet Fisheye scrubber

| Field | Value |
|---|---|
| Type | exclusive |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | PERF-001, A11Y-001 |

## User story and value

As a user with hundreds of apps, I want to scrub an alphabet edge index and snap the lens focus to the matching group.

## Acceptance criteria

- [ ] Build locale-aware sections including digits/symbols and scripts without Latin A–Z.
- [ ] Edge side follows handedness/RTL settings and does not conflict with system back gestures.
- [ ] Focus animates predictably and haptic feedback fires once per section transition.
- [ ] Keyboard/TalkBack users receive an equivalent section-navigation action.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover collation/section mapping; widget tests cover gestures and semantics.
- [ ] Integration tests cover sorting, locale and package changes.
- [ ] Tecno smoke validates gesture navigation, touch accuracy and 300+ app performance.
