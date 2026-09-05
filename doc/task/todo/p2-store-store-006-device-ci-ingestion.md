# STORE-006 — Ingest screenshots and build metadata from device or CI

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Store automation |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | STORE-001, STORE-004 |

## User story and value

As a publisher, I want screenshots tied to an exact build/device/locale so outdated imagery cannot enter a release deck.

## Acceptance criteria

- [ ] Import an explicit manifest containing build SHA/version, device, resolution, locale and capture timestamp.
- [ ] Verify image hashes/dimensions and flag mixed build versions.
- [ ] Support a local ADB/CI-produced bundle without allowing arbitrary command execution from the web UI.
- [ ] Preserve provenance through final ZIP filenames/manifest.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests validate manifests; integration tests import valid/tampered/mixed bundles.
- [ ] Tecno capture-to-editor-to-export smoke succeeds for the release candidate.
