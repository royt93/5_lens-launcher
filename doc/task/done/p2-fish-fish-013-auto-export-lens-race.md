# FISH-013 — Auto-export-lens intent extra can be silently lost

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 3 SP (needs its own design pass before implementation — not a one-line fix) |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Found during the `FISH-012` transient-state audit (2026-09-29). `FrmLens`'s
share button (`ui/FrmLens.kt:141-146`) starts `ActHome` carrying a one-shot
`EXTRA_AUTO_EXPORT_LENS` intent extra. `ActHome.consumeAutoExportExtra`
(`ui/ActHome.java:313-314`) **removes** that extra from the `Intent` the moment
it reads it — before the export it guards has actually run
(`ui/ActHome.java:521-523`, `:575-578`). The story's own comment at `:570-574`
already discloses a related bind-timing race this exact design has to work
around.

If the process dies (or that disclosed race resolves unluckily) in the window
between "extra consumed" and "export actually completes", the share request
is gone with no way to retry it — the `Intent` no longer carries the flag, and
nothing else remembers it was requested. The user taps "Share lens image" and,
on some devices/timings, nothing ever happens.

## User story

As a user tapping "Share lens image" in `FrmLens`, I want the share to either
happen or fail with a visible message, never silently do nothing.

## Acceptance criteria

- [x] The one-shot request survives a process kill between "extra consumed"
      and "export completes" — either by not clearing the request until the
      export truly finishes (success or failure), or by persisting a pending
      flag the same way `FISH-012` persists the pinch adjustment (a
      pending-key + resurrect-on-next-bind pattern — reuse that shape if it
      fits once actually designed).
- [x] On unrecoverable failure (e.g. the requesting lens no longer exists),
      the user sees an explicit message rather than silence.

## Implementation notes

**Design/brainstorm pass completed 2026-09-29** — full design at
`docs/superpowers/specs/2026-09-29-fish-013-auto-export-lens-race-design.md`,
implementation plan at `docs/superpowers/plans/2026-09-29-fish-013-auto-export-lens-race.md`.

Chosen shape: **persisted pending flag** (`UtilSettings.KEY_PENDING_AUTO_EXPORT_LENS`),
mirroring FISH-012's pending-distortion-factor pattern exactly, rather than
keeping the Intent extra alive — Android's Intent-redelivery behavior across a
real process kill is not documented/guaranteed, whereas `SharedPreferences` is.
Global (not per-lens) boolean: exports whichever lens is active at resolve
time, matching pre-existing production semantics (Share always targets the
lens currently open in `FrmLens`/`ActHome`). No TTL/staleness guard by design
(YAGNI) — the request fires whenever the app is next opened, however long that
takes.

`FrmLens.shareLensImage()` and `ActHome.consumeAutoExportExtra()` both write
the flag at request time; `ActHome.exportActiveLensImage()` is the single
place that clears it, and only at a genuine terminal outcome (success,
export-failure Toast, or the two unrecoverable early-returns — no bound
`LensView`, or the active lens no longer exists in `currentLenses`) via a
shared `clearPendingAutoExportLensAndNotifyFailure()` helper. The two
trigger-only sites (`refreshLensList`'s DB-load callback, `bindLensView`)
still only flip the pre-existing in-memory `pendingAutoExportLens` boolean to
prevent a duplicate in-flight post — they never touch the disk key.

Code review (2026-09-29, see Audit section) flagged two Important nits, both
addressed same-day: (1) `SharedPreferences.apply()`'s async-flush ceiling vs.
a raw SIGKILL is now documented inline and in the design spec, matching the
same accepted precedent as FISH-012; (2) the unrecoverable-state test's actual
location (`ActHomeAutoExportPersistenceIntegrationTest.kt`, not
`ActHomeAutoExportWidgetTest.kt` as originally planned) is now noted in the
design spec so a future grep doesn't trip over it. A Minor nit (menu item 5's
"Share" entry intentionally not writing the pending flag, since it's a
synchronous foreground action, not the cross-process gap this story targets)
got an explanatory comment.

## Verification and Definition of Done

- [x] Unit tests cover pure logic once a design exists. —
      `UtilSettingsPendingAutoExportTest.kt` (3 tests: default-false,
      set-reads-true, clear-reverts-false), Robolectric/JUnit4.
- [x] Widget/UI tests cover visible behavior (success and failure messaging).
      — pre-existing `ActHomeAutoExportWidgetTest.kt`
      (`autoExportExtra_firesExactlyOnce_andNeverAgainAfterRecreate`) re-run,
      still passes; the "unrecoverable state shows failure" branch is instead
      covered by `ActHomeAutoExportPersistenceIntegrationTest.kt`'s
      `exportWithNoBoundLensView_clearsPendingFlagInsteadOfResurrectingForever`
      (functionally equivalent coverage, relocated per code review — see
      design spec §4.2 note).
- [x] Integration tests cover the process-boundary/timing scenario this story
      exists to fix. — `ActHomeAutoExportPersistenceIntegrationTest.kt`, 2
      tests: `pendingFlagOnDisk_resurrectsExportOnColdLaunch_andClearsFlag`
      (writes the pending flag directly to disk with no Intent extra at all,
      launches a fresh `ActHome`, asserts the chooser Intent still fires and
      the flag clears) and
      `exportWithNoBoundLensView_clearsPendingFlagInsteadOfResurrectingForever`.
- [x] Smoke tests pass — **device policy deviation, disclosed**: the
      designated TECNO KJ7 (`115333744A005844`) and its approved fallback
      TECNO BG6 were both absent from `adb devices` this session (only Pixel
      7 Pro and an emulator were attached). Per standing policy this blocks
      device work; the user was asked explicitly via `AskUserQuestion` and
      granted a one-off, session-only exception to use Pixel 7 Pro
      (`2B051FDH3006MU`, Android 17) instead — consistent with prior
      documented exceptions for this project. Not a standing policy change;
      re-ask next session if TECNO is still unavailable.
      - Build: devDebug, commit `71c5a9a`, 2026-09-29 ~20:00-20:05 local.
      - Ran on-device: `ActHomeAutoExportPersistenceIntegrationTest` (2/2
        pass), `ActHomeAutoExportWidgetTest` (1/1 pass),
        `ActHomeLensShareIntegrationTest` (1/1 pass) via
        `adb shell am instrument`.
      - Manual flow: Settings → tab Lens → "Chia sẻ ảnh lens" → real Android
        share sheet ("Chia sẻ hình ảnh") appeared with the exported image.
      - Manual regression scenario (the actual bug being fixed): tapped
        Share, `am force-stop`'d the app ~150ms later (mid-export, before
        the coroutine could finish) → confirmed `pending_auto_export_lens=true`
        persisted to `shared_prefs` after the kill → relaunched `ActHome` →
        share sheet appeared automatically with **no user interaction** →
        confirmed the disk flag was cleared afterward → `logcat` showed no
        `FATAL`/uncaught exceptions throughout.
- [x] No new lint/build failures. — `./gradlew testDevDebugUnitTest
      lintDevDebug` both green, no new findings.
- [x] A post-change audit record scores the round `> 9.0/10` before push. —
      see Audit section below.
- [x] Evidence and status are updated before moving this file to `done`.

## Audit (2026-09-29)

Code review dispatched per `superpowers:requesting-code-review` against
`git diff 2d0c240..6819a94` (base = last commit before this story's work,
head = pre-review-fix state). Findings: **0 Critical**, 2 Important (both
documentation/traceability, not functional defects — fixed same-day in
commit `71c5a9a`), 1 Minor (fixed same-day). Reviewer traced all five
clear/trigger call sites by hand and confirmed no double-clear or
missed-clear path, confirmed the async-callback threading is actually main-
thread-safe (contrary to the review brief's own stated concern), and
confirmed `utilSettings` init-order is safe on cold launch. Reviewer's own
verdict: **"Ready to merge: Yes."**

**Score: 9.6/10.** Full test matrix passes (unit + integration + pre-existing
regression suite), zero lint/build regressions, on-device verification
reproduced the exact original bug scenario (force-stop mid-export) and
confirmed the fix under real OS process-death conditions, not just
`ActivityScenario.recreate()`. The 0.4 deduction is for the disclosed device-
policy deviation (Pixel 7 Pro one-off exception, TECNO unavailable this
session) — code-controlled checks are otherwise clean.

Commits: `2057ef5`, `a1fe6aa`, `2259b02`, `6819a94`, `71c5a9a` (plus doc
commits `3dc2643`, `452f7f8`).
