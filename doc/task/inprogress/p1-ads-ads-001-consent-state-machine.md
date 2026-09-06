# ADS-001 — Make advertising consent-driven

| Field | Value |
|---|---|
| Type | fix |
| Status | inprogress — external (Ad SDK team) |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Ads and privacy |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | None |
| External prerequisites | AdMob/UMP console, wrapper SDK contract |

## Ownership note (2026-09-06)

Handed to the Ad SDK team for implementation; excluded from this repo's code-loop until they deliver. Remains `inprogress` per the push-gate rule for named external ownership. VIP-001 stays blocked on this until it lands.

## Context and evidence

`RApplication.setupAdmob()` initializes advertising before an Activity resolves consent. `ActSettings.checkShowAd()` receives `canRequestAds` but loads ads regardless; its offline branch marks consent as resolved. This conflicts with the intended flow documented in `doc/AD_PROMPT_AOS.MD`.

## User story

As a user, I want my consent choice respected before advertising SDKs request ads or identifiers.

## Acceptance criteria

- [ ] A single state machine represents unknown, requesting, denied, allowed, unavailable, and error/timeout states.
- [ ] No load/show call occurs while `canRequestAds=false` or consent is unknown.
- [ ] Offline startup never equates network failure with consent approval and never blocks the UI.
- [ ] Privacy-options entry point appears when required and consent revocation takes effect without reinstall.
- [ ] Banner/interstitial/app-open initialization remains idempotent across lifecycle events.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Automated tests cover allow, deny, required form, no form, timeout, offline→online, and revoke.
- [ ] Manual EEA/UK debug-geography matrix passes using test ad units.
- [ ] Data Safety and privacy documentation match actual SDK behavior.
