# FISH-045 — Focus Trail (dấu vết icon rời vùng nét) — Thiết kế

Ngày: 2026-11-08  
Branch: `dev` duy nhất. Thiết bị khóa: **TECNO BG6 `118743744X002560`** (owner đổi từ KJ7 khi KJ7 rớt USB). Backup trước cài, khôi phục exact sau đó. Không push trừ khi owner yêu cầu; không stage `.idea`.

## Mục tiêu

Hỗ trợ mắt theo dõi hướng quét khi kéo lens: các icon vừa rời khỏi vùng nét (`DepthOfField.FOCUS_ZONE = 0.2`) được giữ độ mờ nhẹ (alpha tối đa 70%) và hạ dần về độ mờ bình thường của DoF/vignette trong 300 ms. Chỉ là hiệu ứng hiển thị, không thêm hình vẽ, không đổi hit-test/chọn icon.

## Quyết định của owner (2026-11-08)

1. **Hiển thị:** chỉ làm mờ chậm (alpha lingering); không vẽ vòng tròn highlight, không vẽ đường nối.
2. **Phụ thuộc:** công tắc riêng `focus_trail` (mặc định tắt, reset-to-defaults, Settings, 17 locale); **chỉ có tác dụng khi DoF hoặc vignette bật** (khi cả hai tắt, icon đã 100% opaque nên không có gì để nâng; chuỗi cài đặt nêu rõ điều này).
3. **Thời gian:** 300 ms, hạ tuyến tính từ mức nâng về alpha nền hiện tại.
4. **Mức nâng:** alpha trần 70% (`OPAQUE_ALPHA * 0.7f ≈ 179`); nếu alpha nền của ô đang cao hơn 70% thì giữ nguyên alpha nền (chỉ nâng, không bao giờ làm mờ hơn).
5. **Giới hạn số icon:** giữ tối đa **3 icon mới nhất** vừa rời vùng nét; khi có icon thứ 4 rời, icon cũ nhất bị đẩy khỏi vệt ngay lập tức.
6. **Thực thi:** inline theo TDD nghiêm ngặt, không lập plan riêng (owner duyệt).

## Bằng chứng probe trước khi làm

Probe hình học host (`doc/task/evidence/fish-045/trail-occupancy-probe.txt`, hình học KJ7 1080×2436 @480dpi, icon 18dp, vuốt chéo 400 bước / 6,4s):
- Lưới 40 app: 11 lần rời vùng nét, trung bình 0,52 icon trong vệt cùng lúc, max 2; không lần nào vượt quá 3.
- Lưới 120 app: 33 lần rời, trung bình 1,54 icon, max 4; 17 khung hình có >3 icon trong vệt.
- Lưới 270 app: 73 lần rời, trung bình 3,38 icon, max 7; 201/400 khung hình có >3 icon.

Kết luận: quy tắc "chỉ giữ 3 icon mới nhất" có hiệu lực cắt đuôi thực sự ở lưới dày (120 và 270 app), và giữ vệt luôn gọn không tràn màn hình.

## Hành vi chi tiết

1. Công tắc riêng `focus_trail`, mặc định tắt, có trong reset-to-defaults, hàng Settings, 17 locale.
2. Cổng kích hoạt:
   - `focusTrailEnabled && (depthOfFieldEnabled || lensVignetteEnabled) && mDrawType == DrawType.APPS && hasActiveTouch && mMoving && gestureState == PANNING && !mReduceMotion`
   - Ghim focus, tilt, idle, pinch, ring rotate, nhả tay: **không có trail**.
3. Phát hiện rời vùng nét:
   - Dùng khoảng cách nét đã tính cho ô (`focusDistance`). Icon được coi là "trong vùng nét" khi `focusDistance <= DepthOfField.FOCUS_ZONE`.
   - Một ô chuyển từ `inside == true` sang `inside == false` được đưa vào hàng đợi vệt với dấu thời gian hiện tại (`nowMs`).
   - Hàng đợi vệt là cấu trúc vòng tròn cố định 3 phần tử (fixed-size array 3 slot, không cấp phát trên frame): mỗi slot giữ `(appIndex, exitTimestampMs)`. Khi đầy, slot cũ nhất bị ghi đè.
4. Tính alpha của ô trong vệt:
   - Nếu ô nằm trong 3 slot và `elapsedMs = nowMs - exitTimestampMs < 300L`:
     - Hệ số còn lại: `t = 1f - elapsedMs / 300f` (từ 1 về 0).
     - Alpha trần: `trailFloor = (DepthOfField.OPAQUE_ALPHA * 0.70f).roundToInt() = 179`.
     - `iconAlpha = max(baseAlpha, (baseAlpha + (trailFloor - baseAlpha) * t).roundToInt())` nếu `baseAlpha < trailFloor`, ngược lại giữ `baseAlpha`.
   - Nếu ô không nằm trong vệt hoặc đã quá 300 ms: giữ nguyên `baseAlpha` của DoF/vignette.
5. Vẽ lại sau khi dừng ngón:
   - Nếu ngón dừng và vệt còn ít nhất một icon chưa hết 300 ms: gọi `postInvalidateOnAnimation()` để vệt mờ dần mượt mà, dừng khi hết 300 ms.
6. Dọn dẹp:
   - `ACTION_UP`, `ACTION_CANCEL`, `resetTouchState()`: xóa toàn bộ 3 slot và mảng trạng thái `inside`. Cử chỉ mới bắt đầu từ baseline sạch.

## Kiến trúc

- Pure `FocusTrail` (`views/FocusTrail.kt`):
  - Hằng số: `WINDOW_MS = 300L`, `MAX_SLOTS = 3`, `MAX_ALPHA_FRACTION = 0.70f`, `MAX_ALPHA = 179`.
  - Hàm thuần `applyAlpha(baseAlpha, elapsedMs): Int` và `shouldApply(...)`.
  - Lớp `Tracker(cellCount: Int)` với mảng `inside: BooleanArray`, 3 slot `Slot(index, exitMs)`, `onFrame(index, inside, nowMs)`, `trailAlpha(index, baseAlpha, nowMs): Int`, `needsRedraw(nowMs): Boolean`, `reset()`, `resize(cellCount)`. Không cấp phát trên frame.
- Nối vào `LensView`:
  - Khai báo `focusTrailEnabled` đọc từ prefs.
  - Tracker được cập nhật trong vòng lặp ô của `drawGrid` sau khi tính `focusDistance`.
  - Biến `iconAlpha` nhận kết quả từ `mTrailTracker.trailAlpha(...)` trước khi truyền vào `drawAppIcon` và `mChromaAlphas`.
  - Hàng Settings mới `swFocusTrail` nằm sau `swFocusSnap`.

## Kiểm thử

- Unit thuần (`FocusTrailTest`):
  - Alpha nâng lên 70% tại thời điểm 0 ms, giảm về baseAlpha tại 300 ms, quá 300 ms không nâng.
  - Ô có baseAlpha > 70% không bị kéo xuống.
  - Cổng `shouldApply` đòi hỏi cả trail on VÀ (DoF on HOẶC vignette on) VÀ pan thật VÀ không reduced motion.
  - Hàng đợi 3 slot: icon thứ 4 đẩy icon cũ nhất; hai ô rời cùng lúc; ô quay lại vùng nét rồi rời lại; giá trị không hữu hạn bỏ qua.
  - `reset()` xóa toàn bộ vệt và clock; `resize()` giữ trạng thái ô còn tồn tại.
- Static source guard (`LensViewFocusTrailSourceTest`):
  - UP/CANCEL reset tracker.
  - Cổng dùng đúng biến cài đặt và reduced motion.
  - Khối toán và frame loop không cấp phát bộ nhớ.
- Widget / Reachability trên BG6:
  - Vuốt thật qua lưới có DoF/vignette: ít nhất một ô ngoài vùng nét nhận alpha > baseAlpha trong lúc vuốt.
  - Tắt DoF và vignette: không nâng alpha.
  - Tắt công tắc trail: không nâng alpha.
  - Quá 300 ms sau dừng: alpha về đúng baseAlpha.
  - Hit-test và chọn icon không đổi.
- Mutation:
  - Bỏ cổng DoF/vignette, bỏ cổng pan, bỏ cổng reduced motion, đổi trần 70%, đổi 300 ms, bỏ cap 3 slot, không dọn ở UP/CANCEL.
- Frame budget trên BG6:
  - Đo A/B off/on với 10 cặp xen kẽ, kiểm tra delta p50/p90.

## Giới hạn cần ghi nhận trong audit

- Hiệu ứng chỉ thấy được khi DoF hoặc vignette bật; người dùng bật riêng trail sẽ không thấy gì (đã ghi chú trong chuỗi cài đặt).
- Vệt 3 slot bị tràn ở lưới dày (270 app), chỉ giữ 3 icon mới nhất.
- Hằng số 70% và 300 ms là phỏng đoán, chưa qua người dùng thật thử tay.
- Máy đo chính là TECNO BG6 (đổi từ KJ7).
