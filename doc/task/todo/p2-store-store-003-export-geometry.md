# STORE-003 — Make screenshot export geometry exact

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Store asset quality |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | STORE-002 |

## Context and evidence

`captureSlide()` forces canvas width/height for targets whose source and export aspect ratios differ, risking subtle distortion instead of explicit crop/pad behavior.

## User story

As a store publisher, I need exported artwork to match exact platform dimensions without stretching devices or text.

## Acceptance criteria

- [ ] Define per-target logical canvas, scale, crop/pad and safe-area rules.
- [ ] Never non-uniformly scale composed content.
- [ ] Validate text/device bounds and connected-canvas crop seams before export.
- [ ] Embed deterministic file naming and target metadata.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover transform math and every target dimension.
- [ ] Component tests render representative isolated/connected decks.
- [ ] Integration export test inspects PNG dimensions and pixel snapshots.
- [ ] Manual smoke compares resulting bundle with Play/App Store upload validators.
