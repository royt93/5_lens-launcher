# FISH-047 — Nhãn icon liền kề — Thiết kế

Ngày: 2026-11-13. Branch `dev` duy nhất. Thiết bị khóa: **TECNO KJ7 `115333744A005844`** (owner 2026-11-13 "dùng tecno cho mọi phiên"); backup APK + dữ liệu + hash trước cài, khôi phục exact sau. Không push trừ khi owner yêu cầu; không stage `.idea`. Quảng cáo chỉ đóng bằng `KEYCODE_BACK`.

## Mục tiêu

Khi kéo ngón qua lưới, ngoài nhãn tên của icon đang chọn, hiện thêm nhãn mờ cho tối đa hai icon ngay trái và phải cùng hàng, để biết ngón sắp trượt tới app nào.

## Quyết định của owner (2026-11-13)

1. Hướng: nhãn tên chỉ hiện cho icon gần tiêu điểm (đã bỏ hướng iris khi mở app vì `UtilApp.getLauncherOptionsBundle` đã dùng `makeClipRevealAnimation`).
2. Phạm vi: **tối đa 2 icon liền kề** icon đang chọn (trái và phải cùng hàng lưới, `index-1` và `index+1` khi cùng hàng).
3. Chống đè chữ: chỉ vẽ nhãn liền kề khi **đủ chỗ**, không đủ thì bỏ qua (không cắt chữ, không `…`).
4. Công tắc riêng `neighbour_labels`: mặc định tắt, có trong reset-to-defaults, hàng switch Settings và 17 locale.
5. Thực thi inline TDD, không lập plan riêng.

## Bằng chứng giá trị tác động (bài học FISH-042 rev1 và FISH-045)

`doc/task/evidence/fish-047/neighbour-gap-probe.txt`, công thức warp thật với số của KJ7: icon liền kề cùng hàng cách icon chọn **194-521 px** (tâm tới tâm) ở lưới 30/60/100/150 app, nhãn 8 ký tự rộng ~213 px. Vì vậy hiệu ứng nhìn thấy được ở lưới 100 và 150 app (cả hai bên đủ chỗ) và một phần ở 30-60 app (bên 194 px bị bỏ qua, bên 412-521 px được vẽ). Một luật "luôn vẽ" sẽ in chữ đè nhau, nên luật đủ chỗ là bắt buộc, không phải tuỳ chọn. Còn thiếu: chứng minh bằng một vuốt thật trên KJ7 rằng ít nhất một nhãn liền kề thực sự được vẽ (counter) trước khi coi là xong.

## Kiến trúc

- `views/NeighbourLabels.kt` thuần, không cấp phát: `shouldApply(enabled, labelsOn, clean, moving)` (cùng cổng nhãn chính cộng công tắc riêng), `neighbours(selected, cols, count, out)` trả tối đa hai chỉ số cùng hàng, và `fits(selectedCx, neighbourCx, selectedLabelW, neighbourLabelW, gap)` với `fits = |dx| >= (wSel + wNb) / 2 + gap`. Hằng số: `ALPHA = 140` (≈55%), `GAP_DP = 8`.
- `LensView.drawAppName`: sau nhãn chính, nếu `NeighbourLabels.shouldApply(...)`, lấy hai chỉ số liền kề, lấy rect đã vẽ từ `mDrawnRectsForTest` (cùng warp với icon), đo chữ bằng `Paint.measureText`, vẽ khi `fits`. Dùng `mPaintText` với alpha tạm rồi khôi phục. Nhãn chính và hit-test không đổi.
- Không tạo thêm vòng lặp nặng: chỉ ≤2 lần `drawText` mỗi frame, chỉ khi đang kéo.

## Kiểm thử

- Unit thuần (`NeighbourLabelsTest`): cổng đủ điều kiện; `neighbours` ở mép trái/phải, hàng cuối chưa đầy, `cols=1`, `count` nhỏ; `fits` ở biên đúng bằng; hằng số.
- Static guard: nhãn chính không đổi, hit-test không đọc nhãn, không cấp phát trong đường vẽ.
- Widget/reachability trên KJ7 bằng DOWN/MOVE thật: lưới 100 app vẽ đúng 2 nhãn liền kề; lưới đủ gần vẽ ít hơn; công tắc tắt thì 0; clean mode thì 0; reduced-motion không ảnh hưởng nhãn (nhãn không phải chuyển động).
- Mutation cho cổng, biên `fits`, chỉ số liền kề, alpha khôi phục.
- Đo frame (Perfetto FrameTimeline trên KJ7) bật/tắt.

## Giới hạn dự kiến

- `ALPHA = 140` và `GAP_DP = 8` là giá trị đoán, owner chưa thử tay.
- Ở lưới thưa (30-60 app) nhiều lúc chỉ một bên có nhãn, hoặc không có nhãn nào; đây là hệ quả của luật đủ chỗ, không phải lỗi.
- Chỉ liền kề theo hàng, không theo cột: nhãn nằm phía trên icon nên nhãn theo cột sẽ đè lên icon hàng trên.
- Chồng với nhãn chính và halo ở lưới rất dày; kiểm bằng ảnh thật.
