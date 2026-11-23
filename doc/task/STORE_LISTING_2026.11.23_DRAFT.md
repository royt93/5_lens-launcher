# Store listing text, draft for 2026.11.23 (English and Vietnamese)

Draft only. The listing is the owner's to write and publish; every line below is something I measured or read in the code, but the wording is mine, and the Vietnamese is an AI translation with no native-speaker review. Do not paste it without reading it. Facts come from `doc/task/evidence/release-2026.11.23-prep-kj7.txt` and the app's own strings.

## What is measured and safe to say

- The launcher shows ALL apps on one grid (an "equispaced grid"); touching and sliding a finger across it magnifies the icons near the finger like a fisheye lens; lifting the finger on an icon opens that app (measured: a touch held 10 ms or more on an icon opens it; a touch shorter than that did nothing, which a hand does not produce).
- Pressing and holding on an icon (longer than the phone's long-press time) opens that app's menu.
- Search at the top finds apps by name; on a short screen (phone held sideways, split-screen) a number pad replaces the on-screen keyboard.
- Several lenses (separate layouts), per-lens lock, opt-in lens effects, 17 languages (AI translation).
- It is a home-screen replacement: Android asks the user to choose it as the default launcher.

## What I did NOT verify, so it should not be promised

- Anything about speed, battery or "no ads". The release build uses real ad ids (the rewarded ad id is still Google's test id; owner deferred consent and ads).
- Behaviour on Android 7 to 9, on foldables or tablets: only one Android 14 phone with a display cutout was tested.
- Use by a person: every check was by adb and screenshots.

## English (draft)

**Title (30 characters max):** Fisheye Launcher

**Short description (80 characters max):** See every app on one grid and slide a lens over it to find and open apps fast.

**Full description:**
Fisheye Launcher puts all of your apps on a single grid. Touch the screen and slide: a lens magnifies the icons near your finger, so you can pick one without scrolling through pages or lists. Lift your finger on an icon to open it, or hold on an icon for its menu.

- Equispaced grid: every app is visible, whatever the screen size or app count.
- Fisheye lens: zoom and pan with touch, and tune the distortion, icon size and animation.
- Search: find apps by name from the bar at the top. On a short screen, such as a phone held sideways or in split-screen, a number pad replaces the on-screen keyboard so the results stay visible.
- Several lenses: keep separate layouts, rename them, and lock one with your device unlock.
- Optional lens effects, all off by default.
- 17 languages.

Fisheye Launcher is a replacement home screen. Android will ask you to choose it as the default launcher; you can switch back any time in Settings.

## Tiếng Việt (bản nháp, dịch bằng AI)

**Tiêu đề:** Fisheye Launcher

**Mô tả ngắn:** Xem mọi ứng dụng trên một lưới và trượt thấu kính để tìm, mở app thật nhanh.

**Mô tả đầy đủ:**
Fisheye Launcher đặt toàn bộ ứng dụng của bạn lên một lưới duy nhất. Chạm vào màn hình và trượt ngón tay: thấu kính phóng to các biểu tượng gần ngón tay, nên bạn chọn được app mà không phải cuộn qua nhiều trang hay danh sách. Nhấc ngón tay lên trên một biểu tượng để mở app, hoặc nhấn giữ biểu tượng để mở menu của nó.

- Lưới đều: mọi app đều hiện, bất kể kích thước màn hình hay số app.
- Thấu kính fisheye: phóng to và kéo bằng cảm ứng, chỉnh độ cong, cỡ biểu tượng và hiệu ứng.
- Tìm kiếm: tìm app theo tên từ thanh phía trên. Trên màn hình thấp, như điện thoại cầm ngang hoặc chia đôi màn hình, bàn phím số thay cho bàn phím thường để kết quả không bị che.
- Nhiều thấu kính: giữ các bố cục riêng, đổi tên, và khoá một thấu kính bằng cách mở khoá thiết bị.
- Các hiệu ứng thấu kính tuỳ chọn, mặc định đều tắt.
- 17 ngôn ngữ.

Fisheye Launcher là màn hình chính thay thế. Android sẽ hỏi bạn chọn nó làm launcher mặc định; bạn có thể đổi lại bất cứ lúc nào trong Cài đặt.

## Checks I could not do from here

- The 30 and 80 character limits are the Play Console's; I counted the English ones (title 16, short description 78) but did not check them in the console.
- Category, content rating, Data safety, screenshots and the privacy policy page are the owner's (see `RELEASE_OWNER_CHECKLIST.md`).
