# FISH-048 — Chấm "mở gần đây"

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-11-13, audit 8,7/10 (dưới ngưỡng 9,0, owner đóng với giới hạn, owner thử tay 2026-11-14: ổn) |
| Priority | P3 |
| Evidence | probe (real warp formula, KJ7 numbers) |
| Epic | Fisheye lens |
| Estimate | 3 SP |
| Risk | Low (opt-in, off by default) |
| Dependencies | FEAT-009 (recent apps store) |

Spec: `docs/superpowers/specs/2026-11-13-fish-048-recent-dots-design.md`. Evidence: `doc/task/evidence/fish-048/recent-dot-size-probe.txt`.

Acceptance: công tắc `recent_dots` mặc định tắt, có trong reset và 17 locale; khi bật và đang kéo, tối đa 3 app mở trong 24 giờ qua có chấm ở góc trên-trái, bán kính co theo icon; không đọc store trên đường vẽ; hit-test không đổi; vuốt thật trên KJ7 chứng minh số chấm đã vẽ (đối chứng dương); frame budget bật/tắt được đo.

## Kết quả

Xem `doc/task/AUDIT_FISH-048_2026-11-13.md`. Owner chọn đóng và push khi chưa thử tay, rồi thử tay 2026-11-14 trên Samsung A50s: "ổn cả hai", giữ nguyên hằng số. Giới hạn chính: độ thấy được trên icon nhỏ nhất không có bằng chứng, hằng số là giá trị đoán, một thiết bị.
