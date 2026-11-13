# FISH-048 — Chấm "mở gần đây" — Thiết kế

Ngày: 2026-11-13. Branch `dev` duy nhất. Thiết bị khóa: **TECNO KJ7 `115333744A005844`** (owner 2026-11-13); backup APK + dữ liệu + hash trước cài, khôi phục exact sau. Không push trừ khi owner yêu cầu; không stage `.idea`. Quảng cáo chỉ đóng bằng `KEYCODE_BACK`.

## Mục tiêu

Khi kéo ngón trên lens, đánh dấu bằng một chấm nhỏ những app vừa mở gần đây để tìm lại nhanh.

## Quyết định của owner (2026-11-13)

1. Hướng: chấm "mở gần đây" (đã bỏ rung theo cụm vì FISH-042 rev2 đã có, và iris khi mở app vì `makeClipRevealAnimation` đã có).
2. Khi nào hiện: **chỉ khi đang kéo ngón** trên lens (cùng kiểu cổng với halo/peaking), không hiện lúc lens đứng yên.
3. Kiểu: **chấm tròn nhỏ ở góc trên-trái** icon, màu highlight của theme. Không dùng vòng bao (đã có halo 2,5 dp và peaking 4 dp). Góc trên-phải là badge thông báo, giữa-dưới là thẻ "app mới", nên không đụng hai chỗ đó.
4. Tiêu chí: **tối đa 3 app mở gần nhất, và phải mở trong 24 giờ qua**.
5. Kích thước: **co theo icon đã warp**: bán kính `clamp(12% × bề rộng icon, 1,5 dp, 4 dp)`.
6. Công tắc riêng `recent_dots`: mặc định tắt, có trong reset-to-defaults, hàng switch Settings và 17 locale. Tắt ở Clean the Lens.
7. Thực thi inline TDD, không lập plan riêng.

## Bằng chứng giá trị tác động (bài học FISH-042 rev1, FISH-045, FISH-047)

`doc/task/evidence/fish-048/recent-dot-size-probe.txt`, công thức warp thật với số KJ7: khi lens phóng, đa số icon chỉ rộng 17-27 px (6-9 dp); một chấm cố định 4 dp (12 px) chiếm 44-69% bề rộng, nên **không dùng kích thước cố định** (quyết định 5). Với bán kính co theo icon: từ ~38 px trở lên chấm luôn chiếm 24% bề rộng; dưới đó sàn 1,5 dp làm chấm to tương đối hơn (tới 53% ở icon 17 px). Chưa chứng minh: chấm 2-3 px trên icon nhỏ nhất có nhìn thấy được trên máy thật. Việc chứng minh gồm bộ đếm chấm đã vẽ trong lúc vuốt thật (đối chứng dương) và owner thử tay; nếu không nhìn thấy, ghi rõ giới hạn như FISH-047.

## Kiến trúc

- `views/RecentDots.kt` thuần, không cấp phát: `shouldApply(enabled, cleanMode, dragging, lensVisible)`; `radiusPx(iconWidthPx, density)` = `clamp(0.12 × iconWidth, 1.5 × density, 4 × density)`, không hữu hạn hoặc âm thì 0; hằng số `MAX_DOTS = 3`, `WINDOW_MS = 24 h`, `FRACTION = 0.12f`, `MIN_DP = 1.5f`, `MAX_DP = 4f`.
- `search/SearchHistoryStore`: thêm `recentKeysSince(windowMs, limit)` trả khóa mở trong cửa sổ, mới nhất trước, tối đa `limit`; dùng `clock` đã có nên test được bằng đồng hồ giả. Không đổi định dạng lưu; các hàm hiện có giữ nguyên.
- `LensView`: không đọc store trên đường vẽ. Khi `ACTION_DOWN` (và khi danh sách app đổi) tính sẵn `BooleanArray` theo chỉ số ô (`mRecentDotCells`) từ `componentKey` của từng app; đường vẽ chỉ tra mảng và gọi `drawCircle` với một `Paint` dựng sẵn, vẽ sau icon, cùng rect đã warp. Không cấp phát. Hit-test, chọn icon và nhãn không đổi.
- Reset: `ACTION_UP/CANCEL` và `resetTouchState()` xoá mảng.

## Kiểm thử

- Unit thuần (`RecentDotsTest`): hằng số; cổng; bán kính tại các biên (nhỏ hơn sàn, đúng sàn, giữa, đúng trần, vượt trần, NaN/âm); không cấp phát.
- Store (`SearchHistoryStoreRecentSinceTest`): cửa sổ 24 giờ ở biên đúng bằng; giới hạn 3; thứ tự mới nhất trước; khóa hết hạn bị bỏ; không đổi hành vi các hàm cũ.
- Static guard: đường vẽ không đọc store, không tạo đối tượng; chấm vẽ sau icon; hit-test không đọc trạng thái chấm; mảng reset ở UP/CANCEL.
- Reachability trên KJ7 bằng DOWN/MOVE thật, không hook: lưới 100 app với 3 app đã ghi vào store trong 24 giờ, đúng số chấm đã vẽ; app ngoài 24 giờ không có chấm; công tắc tắt thì 0; clean mode thì 0; không pan thì 0.
- Mutation cho cổng, biên 24 giờ, giới hạn 3, bán kính, reset.
- Đo frame bằng test kéo chéo (như FISH-047), bật/tắt, kèm bộ đếm chấm làm đối chứng dương.

## Giới hạn dự kiến

- Chấm trên icon nhỏ nhất (≈17-21 px) chỉ ~2-3 px: có thể khó thấy; sẽ ghi nếu không chứng minh được.
- Người ít dùng máy có thể không thấy chấm nào (cửa sổ 24 giờ).
- Recents dùng chung mọi lens, nên một app mở ở lens khác vẫn có chấm.
- `ALPHA`/kích thước/màu là giá trị đoán, owner chưa thử tay.
