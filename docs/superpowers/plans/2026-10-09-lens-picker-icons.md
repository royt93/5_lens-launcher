# Lens picker icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mỗi dòng chọn ứng dụng hiện icon launcher, tên app và checkbox.

**Architecture:** Giữ ListView multi-choice native và Set lựa chọn hiện có. ArrayAdapter dùng CheckedTextView native, icon compound drawable phía start 40dp lấy từ RAppsSingleton bằng iconCacheKey; fallback app.icon rồi icon launcher của ứng dụng. Mỗi lần bind đặt lại drawable để không rò icon giữa dòng tái sử dụng. Không thêm thư viện, timer hay coroutine.

**Tech Stack:** Kotlin, Java, AppCompat/Material, JUnit4, Robolectric, AndroidX Test.

## Global Constraints

- User duyệt “Icon + tên + checkbox”; không thêm package hay đổi phạm vi lens.
- Pixel 7 Pro `2B051FDH3006MU` là thiết bị khóa do user chọn; không gửi lệnh tới TECNO/OPPO.
- Không xóa dữ liệu app và không đổi launcher mặc định trong smoke này.
- Icon không là node TalkBack riêng: CheckedTextView đọc tên và trạng thái chọn.
- Kiểm chứng selection dài, cuộn, Hủy và icon fallback trước khi push.

### Task 1: Adapter icon + tên + checkbox

**Files:** Create `app/src/main/java/com/mckimquyen/adt/LensAppChoiceAdapter.kt`; create `app/src/test/java/com/mckimquyen/adt/LensAppChoiceAdapterTest.kt`; modify `app/src/main/java/com/mckimquyen/ui/ActHome.java`; extend `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensAppsDialogWidgetTest.kt` và `ActHomeLensAppsDialogLongListTest.kt`.

**Interfaces:** `LensAppChoiceAdapter(context: Context, apps: List<App>) : ArrayAdapter<CharSequence>`; getItem giữ nhãn app, getView dùng CheckedTextView native. Icon cache qua `RAppsSingleton.instance.getAppIcon(app.iconCacheKey)`; bounds 40dp, khoảng cách 12dp dùng constant.

- [ ] Test RED: bind cache bitmap đúng iconCacheKey, bind bitmap app khi cache trống, bind lại cùng view sang app không có icon phải xóa bitmap cũ và hiện fallback; mỗi dòng vẫn CheckedTextView.
- [ ] Chạy `./gradlew testDevDebugUnitTest --tests com.mckimquyen.adt.LensAppChoiceAdapterTest`; kỳ vọng unresolved adapter trước implementation.
- [ ] Implementation: inflate native `android.R.layout.simple_list_item_multiple_choice`, bind text theo ArrayAdapter, bind compoundDrawableRelativeStart từ BitmapDrawable hoặc `R.mipmap.ic_launcher`, đặt bounds và padding constants; không giữ lifecycle resource.
- [ ] Dialog: builder.setAdapter(adapter, null), sau show đặt choiceMode=CHOICE_MODE_MULTIPLE, setItemChecked cho mọi vị trí bằng Set, onItemClick cập nhật Set bằng list.isItemChecked; OK lưu Set, Hủy bỏ draft.
- [ ] Chạy unit adapter; widget kiểm icon và checkbox; integration long list giữ lựa chọn khi cuộn/Hủy, dùng performItemClick thật.
- [ ] Build và install bằng `adb -s 2B051FDH3006MU install -r -t <apk>`; không connected*.
- [ ] Smoke nhìn screenshot dialog có icon, cuộn và đóng Hủy không đổi selection.
- [ ] Unit toàn bộ + lint + instrumentation liên quan, ghi evidence; commit riêng file tính năng, không stage `.idea`.
