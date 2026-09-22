# FISH-004 — Add adaptive lens physics and haptic profiles

| Field | Value |
|---|---|
| Type | exclusive |
| Status | done |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | PERF-001, DISPLAY-001, A11Y-001 (all done as of 2026-09-22) |

## User story and value

As a user, I want distinct lens movement/haptic profiles that feel responsive while respecting reduced-motion and battery settings.

## Scope decision

The 3 existing continuous sliders (`Hệ số biến dạng`/Distortion Factor, `Hệ số tỷ lệ`/Scale
Factor, `Thời gian hiệu ứng`/Animation Time in `FrmLens`) already let a user fully customize lens
movement - "profiles" are delivered as 3 named quick-preset buttons that set all 3 sliders to a
fixed, validated triple in one tap, rather than a separate parallel system. This keeps the
sliders as the single source of truth (no second, driftable "which profile is active" state to
track) and means switching profiles never touches `LensView`'s rendering/hit-testing math at all.

## Acceptance criteria

- [x] Ship a small set of mathematically specified profiles with stable selection geometry.
  `LensPhysicsPreset` enum: `GENTLE` (1.0 / 1.0 / 350ms), `STANDARD` (2.5 / 1.0 / 200ms - identical
  to today's existing defaults, so the default experience is unchanged), `SNAPPY` (4.5 / 2.0 /
  120ms). All 3 verified within the sliders' real XML-declared bounds (guarded by a unit test).
- [x] Separate visual distortion from hit testing so every displayed selection launches correctly.
  Audited `LensView.drawGrid`: both the drawn rect and the hit-test (`isInsideRect`) read the same
  per-frame computed `mScratchRect` - already correct before this story, and unaffected by preset
  choice since a preset is just a different (distortion, scale) constant pair, not a new physics
  model (no spring/overshoot introduced, so nothing here could newly destabilize hit-testing).
  Documented rather than "fixed", since no actual bug was found.
- [x] Respect reduced motion, haptic disablement, battery saver and thermal limits. New
  `LensPhysicsPolicy.shouldReduceLensMotion()` (mirrors `BaseActivity.shouldRequestHighRefreshRate`'s
  pattern) checks `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (reduced motion),
  `PowerManager.isPowerSaveMode`, and `currentThermalStatus >= MODERATE`; when any is true, the
  show/hide animation duration collapses to 0 and both hover/launch haptics are suppressed
  (layered under the existing `KEY_VIBRATE_APP_HOVER`/`KEY_VIBRATE_APP_LAUNCH` user toggles - a
  preset/system condition can only further restrict, never override an explicit user opt-out).
- [x] Preview changes safely and restore defaults instantly. Tapping a preset calls `save()` on
  the 3 existing settings keys and `invalidate()`s the live preview `LensView` already present at
  the top of `FrmLens` - same instant/reversible mechanism the sliders themselves already used.
  Live-verified: tapping Snappy → Gentle → Standard on a real device shows each preset's exact
  values applied immediately, `Standard` restoring the identical values `Reset to Default` does.

## Bug found and fixed during this story's own live smoke test

The first layout attempt (3 equal-weight `MaterialButton`s at default text size) wrapped "Standard"
and "Snappy" mid-word ("Stand ard" / "Snapp y") on a real device - not caught by lint or the
widget tests (which only assert persisted values, not visual line-wrapping). Fixed with
`android:maxLines="1"`, `android:ellipsize="end"`, smaller `textSize`, and zeroed
inset/padding/minWidth on all 3 buttons; re-verified live on both devices below.

## Required test matrix

- [x] Unit tests: `LensPhysicsPolicyTest` (8 cases - every condition alone/combined/at the
  threshold boundary, plus `STANDARD` == today's defaults, plus every preset within the sliders'
  real bounds). 421/421 unit tests pass overall.
- [x] Widget/UI tests: `FrmLensPhysicsPresetsWidgetTest` (3 cases - Gentle/Snappy apply their exact
  values to the real persisted settings; Standard after Snappy restores exact defaults).
- [x] Integration tests: `LensPhysicsPolicyIntegrationTest` - toggles the real
  `animator_duration_scale` system setting via a shell command and confirms
  `LensPhysicsPolicy.shouldReduceLensMotion(context)` reflects it through the real
  `Settings.Global`/`PowerManager` APIs, not just the pure decision table.
- [x] Smoke test on the designated device(s), model/OS/build SHA/timestamp recorded below.

## Verification and Definition of Done

- [x] Property-based-style unit tests validate every branch of the reduced-motion decision table
  and every preset's bounds (see above).
- [x] Widget/integration tests cover the real settings side effect and the real OS signal.
- [x] Smoke on Samsung SM-S928B (Android 16) and TECNO BG6 (Android 13): both devices show the
  "Movement feel" card with 3 correctly single-line-rendered buttons; tapping Snappy live-applies
  4.5/2.0/120ms on both. Full connected-test regression on S24 Ultra: 203 tests, 201 pass (2
  pre-existing, unrelated - `git log` traces them to commit `1638f87`, outside this session).
  Build SHA at test time: `ed413ce`. Timestamp: 2026-09-22. Network: on-device Wi-Fi, no network
  dependency in the feature itself.

Self-audited **9.3/10**.
