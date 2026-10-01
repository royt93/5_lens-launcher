# Clean the Lens (Minimalist Mode) Design Spec

## 1. Overview
"Clean the Lens" is a minimalist display mode designed to eliminate visual distractions on the home screen. When enabled, the launcher hides extraneous Chrome elements (the search bar, app text labels, notification count badges, and multi-lens page dots), leaving only the pure fisheye lens app grid.

## 2. Architecture & Data Flow

### 2.1 Settings Storage (`UtilSettings`)
- **Key**: `UtilSettings.KEY_CLEAN_LENS_MODE = "clean_lens_mode"`
- **Default value**: `false` (`UtilSettings.DEFAULT_CLEAN_LENS_MODE = false`)
- Type: `Boolean` persisted via `SharedPreferences`.

### 2.2 User Interface (`FrmSettings`)
- Located in `FrmSettings.kt` under general screen options.
- Switch widget: `swCleanLensMode` (MaterialSwitch / SwitchCompat).
- String resources defined in `strings.xml`:
  - `clean_lens_mode_title`: "Chế độ tối giản (Clean the lens)"
  - `clean_lens_mode_summary`: "Ẩn thanh tìm kiếm, tên ứng dụng, huy hiệu thông báo và chỉ báo trang để tập trung vào thấu kính."

### 2.3 Home Screen Presentation (`ActHome` & `LensView`)
- In `ActHome.onResume()` / configuration update:
  - If `cleanLensMode` is true:
    - `searchBar.visibility = View.GONE`
    - Multi-lens `tabLayout` indicator `.visibility = View.GONE`
  - If `cleanLensMode` is false:
    - `searchBar.visibility` follows `UtilSettings.KEY_SHOW_SEARCH_BAR`
    - `tabLayout.visibility` follows multi-lens count rules (> 1 lens shows indicator).
- In `LensView.kt`:
  - In `drawAppLabels()` / touch hover label drawing: skip text drawing when `cleanLensMode` is active.
  - In `drawNotificationBadge()`: skip drawing notification count badges when `cleanLensMode` is active.

## 3. Test Matrix (Rule R5 Compliance)

### 3.1 Unit Tests (JVM / Robolectric)
- `CleanLensModeUnitTest.kt`:
  - Verify default value is `false`.
  - Verify persistence and toggle roundtrip via `UtilSettings`.
  - Verify isolation from other settings keys.

### 3.2 Widget Tests (Instrumentation / Device)
- `CleanLensModeWidgetTest.kt`:
  - Launch settings fragment, assert switch presence and correct toggle behavior.
  - Verify toggling switch persists state into `UtilSettings`.

### 3.3 Integration Tests (Instrumentation / Device)
- `ActHomeCleanLensModeIntegrationTest.kt`:
  - Launch `ActHome` with `cleanLensMode = true`:
    - Assert `searchBar` is `GONE`.
    - Assert `tabLayout` is `GONE`.
  - Launch `ActHome` with `cleanLensMode = false`:
    - Assert `searchBar` restores to user preference.
    - Assert `tabLayout` restores correctly for current lens count.

## 4. Verification and DoD
- 100% JVM unit tests pass.
- Widget and integration tests pass on designated device.
- Full regression suite passes with 0 new failures.
- Android Lint passes with 0 errors.
