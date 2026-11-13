# FISH-047 — Nhãn icon liền kề

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-11-13, audit 8,7/10 (dưới ngưỡng 9,0, owner đóng với giới hạn) |
| Priority | P3 |
| Evidence | probe (real warp formula, KJ7 numbers) |
| Epic | Fisheye lens |
| Estimate | 3 SP |
| Risk | Low (opt-in, off by default) |
| Dependencies | None |

Spec: `docs/superpowers/specs/2026-11-13-fish-047-neighbour-labels-design.md`. Evidence: `doc/task/evidence/fish-047/neighbour-gap-probe.txt`.

Acceptance: công tắc `neighbour_labels` mặc định tắt, có trong reset và 17 locale; khi bật và đang kéo, tối đa 2 nhãn mờ liền kề cùng hàng được vẽ chỉ khi đủ chỗ; nhãn chính và hit-test không đổi; một vuốt thật trên KJ7 chứng minh ít nhất một nhãn liền kề được vẽ; frame budget bật/tắt được đo.

## Kết quả

Xem `doc/task/AUDIT_FISH-047_2026-11-13.md`. Owner thử tay trên KJ7 2026-11-13: nhãn hiện ổn. Giới hạn chính: độ thấy được không có bằng chứng khách quan, hằng số `ALPHA`/`GAP_DP` là giá trị đoán, một thiết bị.
