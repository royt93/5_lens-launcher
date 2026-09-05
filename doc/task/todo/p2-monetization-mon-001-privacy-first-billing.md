# MON-001 — Add privacy-first ad-free monetization

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Monetization trust |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | VIP-001, ADS-001, REL-002 |

## User story and value

As a user, I want a transparent ad-free purchase with reliable restore instead of reusable keys or manipulative ad flows.

## Acceptance criteria

- [ ] Define one-time/subscription products, benefits, pricing surface and regional behavior without dark patterns.
- [ ] Verify purchases and restore entitlement under the VIP-001 trust model.
- [ ] Ads stop promptly after purchase and do not initialize unnecessarily for entitled users.
- [ ] Refund, pending purchase, family/payment failure and offline grace states have clear UI.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Billing unit/fake-client tests, purchase UI tests and sandbox integration tests pass.
- [ ] Tecno smoke covers purchase, restart, offline, restore and revoke using licensed test accounts.
- [ ] Play listing, privacy and support documentation match behavior.
