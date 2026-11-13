# FISH-047 — Nhãn icon liền kề

| Field | Value |
|---|---|
| Type | enhance |
| Status | in progress (TDD, 2026-11-13) |
| Priority | P3 |
| Evidence | probe (real warp formula, KJ7 numbers) |
| Epic | Fisheye lens |
| Estimate | 3 SP |
| Risk | Low (opt-in, off by default) |
| Dependencies | None |

Spec: `docs/superpowers/specs/2026-11-13-fish-047-neighbour-labels-design.md`. Evidence: `doc/task/evidence/fish-047/neighbour-gap-probe.txt`.

Acceptance: công tắc `neighbour_labels` mặc định tắt, có trong reset và 17 locale; khi bật và đang kéo, tối đa 2 nhãn mờ liền kề cùng hàng được vẽ chỉ khi đủ chỗ; nhãn chính và hit-test không đổi; một vuốt thật trên KJ7 chứng minh ít nhất một nhãn liền kề được vẽ; frame budget bật/tắt được đo.

## Status 2026-11-13 (not closed)

- Code, setting, reset entry, Settings row and 17 locales done. Host: unit tests, lint and `assembleDevDebugAndroidTest` green.
- KJ7: `LensViewNeighbourLabelsReachabilityTest` 5/5 (15 runs 0 failures after fixing the test's own label length; first run exposed that 7-character labels sit at the fit boundary on a 100-app lens); `views` 311/311, `ui` 560/560.
- Device mutation checks: fit always/never true and gate without the switch are all caught; **gate without clean mode survives** because the outer label gate (`shouldDrawAppNameLabel`) already covers clean mode, so that branch is redundant.
- **Frame budget NOT measured**: see `doc/task/evidence/fish-047/perfetto-kj7-inconclusive.txt`.
- Not hand-tested by the owner; `NeighbourLabels.ALPHA` (140) and `GAP_DP` (8) are guesses.
