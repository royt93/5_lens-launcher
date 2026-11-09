# FISH-046 — Hover Magnify — Thiết kế

Ngày: 2026-11-09. Branch `dev` duy nhất. Thiết bị khóa: **TECNO BG6 `118743744X002560`**; backup trước cài, khôi phục exact sau. Không push trừ khi owner yêu cầu; không stage `.idea`.

## Mục tiêu

Khi ngón dừng yên trên một icon trong lúc lens đang pan, icon đó phóng thêm 20% để dễ nhìn và dễ xác nhận trước khi nhả tay.

## Quyết định của owner (2026-11-08)

1. **Kích hoạt:** ngón giữ yên trên cùng một icon ≥ 200 ms.
2. **Định nghĩa "yên":** tốc độ ngón < 15 dp/s (dùng `FocusSnap.Tracker`) và cùng `selectIndex` liên tục 200 ms; đổi icon hoặc ngón nhanh hơn thì đếm lại từ đầu.
3. **Nội suy:** sau 200 ms chờ, phóng mượt 150 ms từ 1.0 lên 1.2; khi rời icon (hoặc ngón di chuyển) trở về 1.0 ngay, không animation thu nhỏ.
4. **Hit-test và nhãn:** chỉ phóng hình vẽ. Vùng nhận chạm và chọn icon giữ nguyên; nhãn tên và focus halo dùng rect đã phóng nên nhãn dịch lên theo đỉnh icon.
5. Công tắc riêng `focus_magnify`: mặc định tắt, có trong reset-to-defaults, Settings và 17 locale. Chỉ khi pan thật và không reduced-motion.
6. Thực thi inline TDD, không lập plan riêng (owner duyệt).

## Bằng chứng giá trị tác động (bài học FISH-042 rev1 và FISH-045)

Probe với công thức warp thật (`doc/task/evidence/fish-046/warped-size-probe.txt`): với distortion 2.5 và scaleFactor 1.0 mặc định, icon dưới ngón đã warp lên ~58 dp (174 px @480dpi) ở lưới 40/120/270 app. +20% thêm 11,6 dp (35 px); khác biệt nhìn thấy được. Trước khi viết code còn phải chứng minh bằng một vuốt thật rồi giữ yên trên BG6 rằng rect vẽ của icon được chọn đổi ~20% (counter hoặc ảnh pixel), không chỉ bằng test ép trạng thái.

## Kiến trúc

- `views/HoverMagnify.kt` thuần: hằng số `DWELL_MS=200`, `STILL_SPEED_DP_PER_S=15f`, `SCALE=1.2f`, `RAMP_MS=150`; `shouldApply(enabled, panning, reduceMotion)`; `Tracker` với `onFrame(selectedIndex, speedDpPerS, nowMs)`, `scaleFor(index, nowMs)`, `needsRedraw(nowMs)`, `reset()`. Không cấp phát trên frame.
- `LensView`: sau hit-test của ô, nếu ô là icon đang được chọn và `scaleFor > 1`, phóng `rect` quanh tâm của chính nó trước khi vẽ; `mRectToSelect` nhận rect đã phóng. Tốc độ lấy từ `mSnapTracker.speedDpPerS(now)` (tracker này đã luôn được nạp ở `ACTION_MOVE`). Trong lúc đếm hoặc phóng, gọi `postInvalidateOnAnimation()` vì ngón yên không sinh sự kiện.
- Reset ở `ACTION_DOWN/UP/CANCEL` và `resetTouchState()`.

## Kiểm thử

- Unit thuần (`HoverMagnifyTest`): hằng số; cổng; scale = 1 trước 200 ms, nội suy tuyến tính 150 ms, đạt 1.2 sau 350 ms; đổi icon đếm lại; tốc độ ≥ 15 dp/s hoặc không hữu hạn đếm lại; không chọn icon thì không phóng; `needsRedraw` đúng khoảng; `reset`.
- Static guard: UP/CANCEL reset; cổng đủ ba điều kiện; hit-test dùng rect chưa phóng; không cấp phát.
- Widget/reachability trên BG6 bằng DOWN/MOVE thật rồi giữ yên: rect vẽ của icon được chọn lớn ~1.2×, rect nhận chạm và `selectedIndex` không đổi; ngón di chuyển thì về 1.0; công tắc tắt thì không phóng.
- Mutation cho cổng, ngưỡng 200 ms, ngưỡng tốc độ, hệ số 1.2, nội suy, reset, thứ tự hit-test.
- Đo frame (Perfetto FrameTimeline trên BG6) bật/tắt.

## Giới hạn dự kiến

- 200 ms, 15 dp/s, 1.2 và 150 ms là giá trị đoán, owner chưa thử tay.
- Với lưới dày icon phóng 1.2× có thể chạm icon bên cạnh (chỉ hình vẽ, không ảnh hưởng chọn).
- Chồng với Focus Halo và nhãn tên; cần kiểm bằng ảnh thật.
