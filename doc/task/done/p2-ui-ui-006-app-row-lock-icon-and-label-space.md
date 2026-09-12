# UI-006 — App-row lock control: icon instead of text label, freeing space for the app name

| Field | Value |
|---|---|
| Type | `fix` |
| Status | `done` |
| Priority | `P2` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 2 |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Owner-reported (2026-09-12) while reviewing the Apps tab: the lock/unlock control in each app row
(`view_item_app.xml`, `R.id.btAppLock`) was an `AppCompatButton` showing the text "Lock"/"Unlock",
wide enough to crowd `tvAppLabel` (the app name, `layout_toStartOf="@id/btAppLock"`) into a
narrower column, truncating longer app names.

## Fix

- `view_item_app.xml`: `btAppLock` changed from `AppCompatButton` (text label) to
  `AppCompatImageView` sized identically to its sibling icon controls (`@dimen/width_app_icon` x
  `@dimen/height_app_action_button`, `?attr/selectableItemBackgroundBorderless`) — same pattern as
  `ivAppHide`/`ivAppMenu` in the same row. `tvAppLabel` automatically gets the freed horizontal
  space since it's laid out `toStartOf="@id/btAppLock"` in a `RelativeLayout` (no separate width
  change needed).
- Two new vector icons, `ic_lock_24dp`/`ic_lock_open_24dp` (standard Material glyphs, matching the
  existing single-path 24dp icon convention used elsewhere in this codebase).
- `AppAdapter.java`: `btAppLock` field retyped `Button` → `ImageView`; the 4 call sites that used
  to set button text + `ViewCompat.setBackgroundTintList` now call `setImageResource` +
  `setColorFilter` (tint semantics preserved exactly: grey = unlocked/tap-to-lock, dynamic
  `colorPrimary` = locked/tap-to-unlock) + `setContentDescription` (the old visible text is now the
  accessible name instead, so screen-reader behavior is unchanged).
- Deleted `bg_button_lock.xml` (now unreferenced anywhere) after confirming via grep — same
  verify-before-delete discipline as `LINT-003`.

## Verification

- `./gradlew assembleDevDebug` clean, `./gradlew testDevDebugUnitTest` full pass.
- `./gradlew lintDevDebug`: 0 errors, 20 warnings — identical pre-existing set, no new
  `ContentDescription` warning despite the new `ImageView` (no static `android:src` in XML for
  lint to flag; the real accessible name is still set programmatically at runtime).
- `AppAdapterWidgetTest`/`FrmAppsWidgetTest`/`FrmAppsLifecycleIntegrationTest`: 10/10 pass on
  TECNO KJ7.
- Live device screenshot confirms: lock icon renders compact and correctly tinted per state, app
  names now render across up to 3 visible lines with substantially more room (e.g. "Be My Tester
  (14 Testers For Closed ...", "Bộ khuếch đại âm thanh" both now legible instead of cut off
  immediately).

Self-audited **9.7/10** (2026-09-12, TECNO KJ7).
