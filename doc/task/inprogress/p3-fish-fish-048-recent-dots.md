# FISH-048 — Chấm "mở gần đây"

| Field | Value |
|---|---|
| Type | enhance |
| Status | in progress (TDD, 2026-11-13) |
| Priority | P3 |
| Evidence | probe (real warp formula, KJ7 numbers) |
| Epic | Fisheye lens |
| Estimate | 3 SP |
| Risk | Low (opt-in, off by default) |
| Dependencies | FEAT-009 (recent apps store) |

Spec: `docs/superpowers/specs/2026-11-13-fish-048-recent-dots-design.md`. Evidence: `doc/task/evidence/fish-048/recent-dot-size-probe.txt`.

Acceptance: công tắc `recent_dots` mặc định tắt, có trong reset và 17 locale; khi bật và đang kéo, tối đa 3 app mở trong 24 giờ qua có chấm ở góc trên-trái, bán kính co theo icon; không đọc store trên đường vẽ; hit-test không đổi; vuốt thật trên KJ7 chứng minh số chấm đã vẽ (đối chứng dương); frame budget bật/tắt được đo.

## Status 2026-11-13 (not closed)

- Code, setting, reset entry, Settings row and 17 locales done. Host: unit 1755 green, lint and the androidTest build clean.
- Device policy changed to the Pixel 7 Pro mid-story (KJ7 dropped off adb). On the Pixel: `LensViewRecentDotsReachabilityTest` 10/10 (3 runs 0 failures after fixing a real bug, below), `views` 321/321, `ui` 561/561.
- **Bug found only on the device:** the cells were computed on ACTION_DOWN from a flag that LensAnimation samples later, so on the first gesture the flag was still false and no dot was ever drawn (every positive-control test failed 0 of 3). Fixed by reading the switch from settings in `refreshRecentDotCells`; a source guard now pins it.
- Device mutation checks: window 24 h to 48 h, max dots 3 to 4, no clean-mode term, refresh never called, gate ignoring the drag, and cells not cleared on a new touch are all caught (the last two only after adding two multi-gesture tests: they survived the first set). **Gate without the switch survives by design** (the cells are already cleared when the switch is off), documented in `RecentDots.shouldApply`.
- Frame budget measured (`doc/task/evidence/fish-048/frame-budget-pixel7pro.txt`): 3 dots drawn when on, per-frame cost not higher than off.
- Visibility: the dot shows in a screenshot taken while it is being drawn, about 22 px across on the big icons near the finger; on the smallest icons it is 1-2 px and not separately measured.
- Not hand-tested by the owner.
