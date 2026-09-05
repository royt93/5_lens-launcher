# SEC-001 — Respond to exposed release signing credentials

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P0 |
| Evidence | confirmed |
| Epic | Release security |
| Estimate | 8 SP |
| Risk | Critical |
| Dependencies | None |
| External prerequisites | Play Console owner access, CI secret store |

## Context and evidence

`app/keystore.jks` is tracked by Git. `gradle.properties` contains `KS_ALIAS` and `KS_PW` in plain text, and `app/build.gradle` uses them for release signing. The credential also exists in repository history.

## User story

As a publisher, I need only authorized builds to be accepted as updates so users can trust installed releases.

## Acceptance criteria

- [ ] Determine whether the exposed key is an upload key or app-signing key and follow the correct Play App Signing rotation path.
- [ ] Rotate or revoke the compromised credential; validate an upload on a non-production track.
- [ ] Remove keystore/passwords from HEAD and purge sensitive history using a coordinated repository rewrite.
- [ ] Load signing material from local untracked properties or CI secrets; fresh clones contain no secret.
- [ ] Add secret scanning and document recovery/key ownership.

## Implementation notes

Treat this as incident response. Back up the valid key securely before any history rewrite and coordinate force-pushes with every clone owner.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Secret scanner finds no current or historical signing material.
- [ ] A signed release artifact installs/upgrades and is accepted by Play Console.
- [ ] Old credential can no longer publish where revocation is supported.
