# FISH-026 — Đàn hồi thấu kính

- **Status:** done — 2026-10-18, owner cho phép commit/push sau review.
- **Phạm vi:** `LensOverscrollPhysics` tính offset hãm, tối đa 28dp ở bốn mép. Toggle toàn cục `lens_elastic_overscroll`, mặc định bật, trong Settings → Lens; reset về bật. Không đổi Room hay workspace backup.
- **Reduced motion:** `LensPhysicsPolicy` vô hiệu đàn hồi khi tắt animation, battery saver hoặc thermal moderate+. Gate `isEnabled` kiểm cả bốn tổ hợp boolean.
- **Release:** dùng animation thu nhỏ lens hiện có; cường độ biến dạng về 0 rồi xóa touch. Không thêm spring/nảy hoặc animator riêng.
- **Bản địa hóa:** hai chuỗi mới ở đủ 17 locale.

## Review và sửa

- Tọa độ âm ở mép trái/trên phải khác sentinel nghỉ `-Float.MAX_VALUE`; sửa gate ở draw và `UtilCalculator.shiftPoint/scalePoint`.
- Regression geometry đỏ trước sửa: tọa độ âm làm `scalePoint` trả kích thước thay vì tọa độ, phình icon. Sau sửa giữ phép biến dạng bình thường và zero multiplier trả đúng base geometry.
- Test Settings reset snapshot/restore cả bốn key slider bị reset, không xóa toàn prefs hay Room.

## Verification bản cuối

- Full JVM: **921/921**, không fail/error/skip.
- Lint: **0 errors / 38 warnings** (không claim toàn bộ là warning cũ).
- Build `assembleDevDebug assembleDevDebugAndroidTest`: pass.
- OPPO CPH1989 `FUJZIFIR7DQCNRWW`: **6 FISH-026 tests + 32 regressions = 38 pass**. Gồm pinch, DOF, halo, preset, custom preset, T9 và chip.
- Smoke thật: switch/mô tả hiện đúng; kéo giữ góc dưới-phải, thả về grid nghỉ; bản cuối kéo về góc trên-trái hoạt động, không crash. Tất cả screenshot đã xem không có quảng cáo. Không đổi default launcher, locale hay hệ thống animation scale.
- Giới hạn: `adb input swipe` chỉ xác nhận drag/render/release, không chứng minh overscroll âm vượt vùng màn hình hay cảm giác easing. Tọa độ vượt biên dùng MotionEvent widget test; reduced-motion gate dùng unit test, không đổi cài đặt hệ thống OPPO để test.
- `git diff --check`: sạch. Secret scan chạy trên staged diff trước commit; file `.idea` không được stage.
- Audit: **9.3/10**, xem `doc/task/AUDIT_FISH-026_2026-10-18.md`.

Spec: `docs/superpowers/specs/2026-10-18-lens-elastic-overscroll-design.md`.
Plan: `docs/superpowers/plans/2026-10-18-lens-elastic-overscroll.md` (bản kế hoạch ban đầu; evidence và điều chỉnh thực tế ở story này).
