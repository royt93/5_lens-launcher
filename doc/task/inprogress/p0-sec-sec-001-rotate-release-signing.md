# SEC-001 — Respond to exposed release signing credentials

| Field | Value |
|---|---|
| Type | fix |
| Status | inprogress |
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
- [x] Load signing material from local untracked properties or CI secrets; fresh clones contain no secret.
- [ ] Add secret scanning and document recovery/key ownership.

## Implementation notes

Treat this as incident response. Back up the valid key securely before any history rewrite and coordinate force-pushes with every clone owner.

## Required test matrix

- [x] Unit: four signing contract tests verify tracked artifacts, Gradle properties, external inputs and ignore rules.
- [x] Widget/UI: reviewed as not applicable because signing configuration has no UI behavior.
- [x] Integration: debug configuration/build works without signing data; release tasks fail early when signing inputs are incomplete.
- [x] Smoke: installed the combined dev candidate on the user-approved Pixel 7 Pro substitute and cold-launched `ActSettings` in 658 ms with no fatal app log.

## Verification and Definition of Done

- [ ] Secret scanner finds no current or historical signing material. HEAD is clean after this change; history rewrite remains owner-coordinated work.
- [ ] A signed release artifact installs/upgrades and is accepted by Play Console.
- [ ] Old credential can no longer publish where revocation is supported.

## Local remediation completed

- Removed the tracked keystore and backup configuration from HEAD without deleting the ignored local key copy.
- Removed signing values from tracked Gradle properties and documented environment/local configuration.
- Added an early, explicit release-build failure when required signing input is incomplete.
- The publisher must still identify and rotate the exposed key in Play Console, update CI, revoke old access, validate a non-production upload, and coordinate any history rewrite.
