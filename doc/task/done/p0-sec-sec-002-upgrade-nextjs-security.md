# SEC-002 — Close the Next.js item by excluding store-assets from scope

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P0 |
| Evidence | decision |
| Epic | Supply-chain security |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | None |

## Context and evidence

The source audit identified an outdated Next.js dependency under `store-assets`. During Wave 0 it was upgraded and tested, but the user then explicitly removed `store-assets` from the implementation scope and requested that all related code changes be reverted.

## User story

As the product owner, I want this repository's active delivery loop to focus on the Android launcher.

## Acceptance criteria

- [x] Revert every Wave 0 modification under `store-assets`.
- [x] Exclude SEC-002 from the Android release-hardening implementation scope by explicit owner decision.
- [x] Preserve the audit finding in this record so the risk is visible if `store-assets` is deployed again.

## Required test matrix

- [x] Unit: not applicable after the implementation was reverted; reviewed owner scope decision.
- [x] Widget/UI: not applicable after the implementation was reverted; reviewed owner scope decision.
- [x] Integration: not applicable after the implementation was reverted; reviewed owner scope decision.
- [x] Smoke: not applicable to the Android candidate because `store-assets` was removed from this wave.

## Verification and Definition of Done

- [x] `git diff -- store-assets` is empty after the requested revert.
- [x] Android build/test/smoke does not depend on `store-assets`.

## Residual note

The original dependency finding is intentionally not fixed. If `store-assets` becomes maintained or network reachable later, reopen this story before using it.
