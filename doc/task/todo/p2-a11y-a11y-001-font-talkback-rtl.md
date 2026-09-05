# A11Y-001 — Support system font scale, TalkBack and RTL

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Inclusive UX |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | TEST-002 |

## Context and evidence

`BaseActivity.attachBaseContext()` forces `fontScale=1.0`. `LensView` is an interactive custom canvas without complete accessibility semantics; lint also reports localization/plural issues.

## User story

As a user with large text, TalkBack or RTL language, I need to find and launch apps independently.

## Acceptance criteria

- [ ] Respect system font scale through 200% and reflow settings/VIP screens without clipping.
- [ ] Expose app nodes, focused app, actions and state to TalkBack, or route accessibility users to an equivalent list mode.
- [ ] Provide meaningful content descriptions, click semantics and keyboard/D-pad focus.
- [ ] Correct plurals, missing translations, hardcoded strings and RTL mirroring.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover locale/plural mapping.
- [ ] Widget tests cover 200% font, RTL layout and semantics tree.
- [ ] Integration tests launch/lock/hide an app through accessibility actions.
- [ ] Tecno TalkBack smoke passes with screen off/on and Activity recreation.
