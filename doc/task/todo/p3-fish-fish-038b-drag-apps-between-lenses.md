# FISH-038b — Kéo thả app giữa các lens (phần kéo thả thật)

| Field | Value |
|---|---|
| Type | idea |
| Status | todo |
| Priority | P3 |
| Evidence | idea |
| Epic | Multi-lens |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | ARCH-002, FISH-038 |

## Context and evidence

Tách từ `FISH-038` (2026-10-27). Phần "Chuyển sang lens…" bằng menu, hoàn tác và action TalkBack đã xong trong FISH-038 (`util/LensMovePlan.kt`, `util/LensMoveApplier.kt`). Phần còn lại là kéo thả thật trên lưới: xung đột gesture với pan/pinch/edge-swipe và `ViewPager2` (`views/LensView.kt` `onTouchEvent`), và `ActHome` đã 2.400+ dòng nên cần `ARCH-002` trước.

## User story

Là người nhiều lens, tôi muốn kéo một app sang lens khác ngay trên lưới.

## Acceptance criteria

- [ ] Kéo thả app giữa lens có phản hồi rõ (bóng kéo, vùng thả, lens đích nổi bật).
- [ ] Không phá pan/pinch/edge-swipe và không làm `ViewPager2` lật trang ngoài ý muốn.
- [ ] Dùng lại `LensMoveApplier` (cùng quy tắc nguồn/đích và hoàn tác), không thêm đường ghi thứ hai.
- [ ] Test gesture trên thiết bị đã khóa + smoke.

## Implementation notes

Đường thay thế a11y đã có từ FISH-038 (menu + TalkBack action `menuItemMoveToLens`), nên story này chỉ là lớp cử chỉ. Làm sau ARCH-002.

## Verification and Definition of Done

- [ ] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [ ] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [ ] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.
