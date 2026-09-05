# VIP-001 — Replace reusable VIP secrets with trusted entitlement

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Monetization trust |
| Estimate | 13 SP |
| Risk | High |
| Dependencies | ADS-001 |
| External prerequisites | Play Billing or signed-token backend |

## Context and evidence

`VipKeys.kt` and `AdKeys.kt` embed reusable secrets in the APK; Base64 is reversible. `ActVipManagement.setupListeners()` validates locally and temporarily replaces the SDK secret, so a modified APK can grant entitlement.

## User story

As a paying or rewarded user, I need VIP status to be durable, restorable, fair, and resistant to trivial forgery.

## Acceptance criteria

- [ ] Split into purchase/token verification, local cache/offline policy, legacy migration, and UI restoration stories.
- [ ] No reusable grant secret ships in the APK.
- [ ] Entitlement has a verifiable issuer, product/grant type, expiry, nonce/order ID, and replay protection.
- [ ] Restore, refund/revoke, expiry, clock change, reinstall, offline grace, and device migration are specified and tested.
- [ ] Existing legitimate users receive a documented migration path.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Decompiled APK cannot independently mint VIP.
- [ ] Billing/token test matrix passes with deterministic clocks.
- [ ] Privacy, support, refund, and recovery documentation are updated.
