# STORE-001 — Harden store-assets write and upload APIs

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | conditional |
| Epic | Store tooling security |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | SEC-002 |

## Context and evidence

`POST /api/project` accepts arbitrary JSON and overwrites the project file. `POST /api/upload` trusts a declared data-URL MIME before writing to `public`. Neither route has authentication, origin control, rate limits or a deployment boundary.

## User story

As an editor operator, I need local files protected from malformed or remote requests.

## Acceptance criteria

- [ ] Validate project payload against a versioned runtime schema with request-size limits.
- [ ] Verify decoded image magic bytes, dimensions, pixel count and supported format.
- [ ] Use temp-file plus atomic rename; return stable error codes without filesystem detail leakage.
- [ ] Default to localhost and reject cross-origin requests; require auth/TLS if a network deployment is supported.
- [ ] Apply upload count/storage quotas and safe cleanup rules.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Route tests cover invalid JSON/schema, oversized/compressed-bomb images, spoofed MIME, concurrent writes and unauthorized origins.
- [ ] Normal upload and project save remain compatible.
