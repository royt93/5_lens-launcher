# Feature tracker

Source of truth for implementation choices in this session. Detailed acceptance criteria and evidence remain in `doc/task/`.

## ✅ Implemented

- **FEAT-005 follow-up — Keep Screen On on every activity**: `BaseActivity` remains the shared owner of `FLAG_KEEP_SCREEN_ON`; `ActVipManagement` now extends it too. Unit and instrumentation coverage verify every manifest activity inherits the base and the VIP screen applies/clears the flag.
- **PERF-004 — Baseline Profile for cold start**: `:baselineprofile` module + shipped profile, `reportFullyDrawn()` when icons first appear, benchmark-only signing/ads carve-out. Details: `doc/task/done/p2-perf-perf-004-baseline-profile-cold-start.md`.
- **Themed icon + debug StrictMode**: `<monochrome>` layer on both adaptive icons (Android 13+ themed icons); log-only StrictMode installed in debug builds only via `util/DebugStrictMode`.

## 🟡 In progress

- None.

## 📋 Picked

1. **FISH-007 — Depth-of-field blur by focus distance** — render hot path, needs frame-timing evidence.
2. **FISH-009 — Live pinch-to-adjust lens curvature** — last, with gesture-conflict coverage.

## ⏸️ Deferred

- FISH-006 Smart Focus lite and FISH-008 Multi-lens workspaces were not selected in this batch.

## ❌ Skipped

- None.

## 💭 Ideas

- See `doc/task/todo/` for the remaining product backlog.
