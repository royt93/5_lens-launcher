# Repository Audit Design — 2026-10-10

## Goal

Run a risk-ordered repository audit across correctness/lifecycle, security/privacy, test gaps, and performance. Fix only findings supported by a reproducible failure, a focused test, or a demonstrable unsafe code path.

## Approach

Audit sequentially in this order:

1. Correctness and lifecycle: state transitions, concurrency, persistence, Android resource cleanup.
2. Security and privacy: trust boundaries, permissions, sensitive data handling, WebView and network boundaries. Existing owner-declined or externally blocked backlog stays out of scope unless the audit finds a distinct, actionable issue.
3. Test gaps: identify important uncovered behavior; add tests only when they prove a specific risk or regression.
4. Performance: inspect likely hot paths first; make changes only when impact can be measured or directly demonstrated.

For each candidate finding, trace the complete code path and inspect existing coverage. Verify with the narrowest appropriate test or deterministic reproduction before changing implementation. Keep each fix and its regression test minimal and within the finding's scope. If verification requires Android build, install, or device interaction, stop and follow the repository's device-target policy before proceeding.

## Constraints

- Preserve unrelated working-tree state; initial working tree was clean.
- Do not reopen backlog categories previously declined or blocked without a distinct finding.
- Do not push or commit as part of this audit unless separately requested.
- Do not claim a defect from speculation alone; report confirmed findings separately from areas with no confirmed issue.

## Completion criteria

- All four audit areas receive an explicit result: confirmed findings, or no confirmed findings.
- Every applied fix has a regression test that demonstrates the behavior.
- Run relevant available verification and report exact outcomes, including anything not run.
- Summarize remaining risks and any work deferred because device authorization or external access is needed.
