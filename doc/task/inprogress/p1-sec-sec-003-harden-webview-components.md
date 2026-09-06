# SEC-003 — Harden WebView and exported Android components

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Android attack surface |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | None |
| External prerequisites | Approved privacy/support domains |

## Context and evidence

`SuperWebViewActivity` is exported, accepts `KEY_URL` from an external intent, enables JavaScript, and uses substring matching for navigation. Splash, About, VIP, and Fake Launcher activities also expose more surface than their internal roles require.

## User story

As a user, I need external apps and untrusted URLs to be unable to turn the launcher into a phishing or unsafe content host.

## Acceptance criteria

- [ ] Mark internal-only components `exported=false`; retain only entry points required by launcher behavior.
- [ ] Parse URI and require exact HTTPS scheme/host/port allowlist before loading.
- [ ] Open foreign links in a verified external browser; reject `javascript:`, `file:`, `content:`, malformed and credential-bearing URLs.
- [ ] Disable JavaScript, file/content access, mixed content and unnecessary WebView features unless a documented domain requires them.
- [ ] Remove WebView from its parent before destroy and preserve safe browsing.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Malicious-intent instrumentation tests cover schemes, subdomain tricks, user-info and encoded hosts.
- [ ] Approved privacy-policy navigation works.
- [ ] Manifest attack-surface review is documented.
