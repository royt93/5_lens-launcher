# PERF-005 — Lens rendering allocation and gesture sampling

**Date:** 2026-10-21

**Story:** `doc/task/done/p2-perf-perf-005-lens-draw-alloc-and-binder-per-event.md`

**Status:** Owner approved; implemented, see audit

## Goal and scope

Reduce avoidable allocation and repeated system-policy reads during lens rendering and touch gestures, without changing grid geometry, icon output, or gesture semantics. Implement only the three confirmed items in PERF-005; do not expand into unrelated rendering optimization.

## Design

- Replace the per-icon source `Rect` allocation in `LensView.drawAppIcon` with one `Rect` scratch field on the view, updating its bounds to the current bitmap dimensions immediately before `drawBitmap`. Keep destination `RectF` and bitmap selection unchanged.
- Remove the unused `mWorkspaceBackgroundDrawable` field and `drawWorkspaceBackground` method, plus their dead draw and detach references. Repository evidence shows no assignment to the drawable; current draw path is a no-op.
- Sample `LensPhysicsPolicy.shouldReduceLensMotion(context)` once when an app gesture receives `ACTION_DOWN`; store the value for that gesture and use it for `ScaleGestureDetector`, pointer-down/up, move and vibration decisions. Clear gesture sample on `ACTION_UP` and `ACTION_CANCEL`; non-app draw mode keeps existing superclass handling. Animation construction may keep its own policy read because animation can be started outside touch dispatch.
- Add focused tests for policy sampling count and held value through DOWN/MOVE/UP and DOWN/CANCEL. Add or extend a `LensView` widget test to verify drawing/layout and reduced-motion behavior remain unchanged; use existing seams and real code, avoiding production-only test hooks unless no existing seam can observe behavior.

## Verification

- Test-first: demonstrate repeated policy reads per MotionEvent before change; then verify one sample per gesture and clean reset after UP/CANCEL.
- Unit tests cover gesture sampling state transitions. Widget/integration tests cover visible LensView behavior and real dispatch where existing infrastructure allows. Document any layer that is not applicable with rationale.
- Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`.
- On owner-locked Pixel 7 Pro `2B051FDH3006MU`, capture baseline and post-change `gfxinfo` for the same controlled launcher interaction; record OS/build, commands, frame stats, and limitations. Do not run on another device. If an ad covers the UI during screenshot-based testing, stop and wait for owner confirmation.
- Preserve device state, inspect git diff, run `git diff --check` and `scripts/check-secrets.sh`; audit honestly before any commit/push.

## Acceptance boundaries

Pass only if icon drawing and gesture behavior remain unchanged, the source-Rect and dead drawable allocations/code are gone, reduced-motion policy is sampled once per gesture and reset on both terminal events, automated verification passes, and device profiling evidence is recorded. If `gfxinfo` is noisy or cannot isolate the gesture, report that limitation instead of claiming a measured improvement.
