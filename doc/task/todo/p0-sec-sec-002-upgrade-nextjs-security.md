# SEC-002 — Upgrade the vulnerable Next.js runtime

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P0 |
| Evidence | confirmed |
| Epic | Supply-chain security |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | None |

## Context and evidence

`store-assets/package.json` and `bun.lock` pin Next.js `15.0.3`, an affected App Router line in the official Next.js RSC security advisories. Severity is P0 when reachable over a network and P1 only when technically restricted to localhost.

## User story

As an editor operator, I need the local screenshot tool to resist known remote-code attacks.

## Acceptance criteria

- [ ] Upgrade Next.js/React to a currently supported, fully patched compatible release and regenerate `bun.lock`.
- [ ] Default start commands bind to localhost; document that network deployment requires authentication and TLS.
- [ ] Project load/save, upload, undo/redo, connected/isolated canvas, and ZIP export still work.
- [ ] Dependency audit has no known critical/high issue accepted without an owner and expiry.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] `bun run build` passes.
- [ ] Editor smoke test and representative PNG export pass.
- [ ] Patched version is verified against the official advisory rather than only a package-manager suggestion.
