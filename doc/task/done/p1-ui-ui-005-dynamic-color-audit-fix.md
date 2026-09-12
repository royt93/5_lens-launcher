# UI-005 — Dynamic color audit fix: static @color/colorPrimary → ?attr, non-M3 dialog themes

| Field | Value |
|---|---|
| Type | `fix` |
| Status | `done` |
| Priority | `P1` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 5 |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Owner flagged (2026-09-12) that dynamic color wasn't actually reaching most of the UI despite
`UI-001`'s `DynamicColors.applyToActivitiesIfAvailable()` wiring (`RApplication.java:111`) being
correct. Root cause confirmed by decompiling behavior + live device testing on TECNO KJ7: dozens
of resources referenced the **static** `@color/colorPrimary`/`@color/colorAccent` (`#b51939`
maroon, `values/colors.xml:3-6`) instead of the **theme attribute** `?attr/colorPrimary`/
`?attr/colorAccent` that `DynamicColors` actually patches. A named color resource never changes;
only a theme attribute does.

Two additional structural findings during the same audit:
1. `ic_language_24dp.xml` had a hardcoded `#FF000000` fill with no `app:tint` override at its only
   usage site (`frm_settings.xml`), and the Settings row background is pure black in night mode
   (`values-night/colors.xml`) — a real bug, not just a dynamic-color gap: the icon was invisible
   in dark mode, independent of this fix.
2. `DynamicColors.applyToActivitiesIfAvailable()` only patches **Activities**. Two custom dialog
   themes were built on **non-Material3 parents** (`TransBottomSheetDialog` on
   `Theme.Design.BottomSheetDialog`, `MaterialYouDialogTheme` on
   `Theme.MaterialComponents.DayNight.Dialog.Alert`), so no amount of `?attr` fixing inside their
   content layouts would have picked up dynamic color - the dialog's own theme chain never went
   through the patched Activity theme's roles. Fixed by switching both to their Material3
   `ThemeOverlay.*` equivalents (`ThemeOverlay.Material3.BottomSheetDialog`,
   `ThemeOverlay.Material3.MaterialAlertDialog`), which correctly inherit the wrapped Activity
   context's already-dynamic-patched attributes instead of redeclaring them.

## Changes made (this round)

- 13 vector drawables (`ic_scale/ic_label/ic_lens_distortion/ic_icon_size/ic_home/ic_color_lens
  (5 paths)/ic_dark_mode/ic_new_releases/ic_touch/ic_vibrate/ic_palette/ic_timer/ic_language_24dp`)
  and 6 shape/gradient drawables (`custom_thumb`, `bg_button_retry`, `bg_button_lock`,
  `bg_button_gradient_ripple`, `bg_button_icon_pack`, `sw_track_ios`): `@color/colorPrimary`/
  `@color/colorAccent` → `?attr/colorPrimary`/`?attr/colorAccent`.
- Layout text/tint colors: `frm_settings.xml` (7), `frm_lens.xml` (4), `dialog_language_picker.xml`,
  `item_language.xml`, `act_about.xml` (2), `act_settings.xml` (`flAdOpenApp` background).
- Code: `ActBase.kt` (task-description color), `ext/Context.kt` (confirm-dialog button color),
  `AppAdapter.java` (4 call sites, consolidated into one `resolveDynamicPrimaryColor(Context)`
  helper) — all switched from `ContextCompat.getColor(colorRes)` to
  `MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, fallback)`.
- `styles.xml`: `MaterialYouDialogTheme` and `TransBottomSheetDialog` parents switched to their
  Material3 `ThemeOverlay.*` equivalents (see above); explicit static `colorPrimary`/`colorAccent`
  overrides removed from `MaterialYouDialogTheme` so it inherits instead of re-hardcoding.
- Intentionally left static (owner-confirmed acceptable): VIP gold branding (`bg_vip_*`,
  `#FFD60A` etc.) - deliberate fixed "premium" accent, not a dynamic-color bug.

## Verification done this round

- `./gradlew assembleDevDebug` clean, `./gradlew testDevDebugUnitTest` full pass (no regressions -
  this is a resource/attr-reference change, no logic touched).
- Live device (TECNO KJ7, one-off exception this session): confirmed on-screen before/after -
  Settings icons, icon-size slider thumb, "BẮT ĐẦU" gradient button, and the language
  bottom-sheet title/checkmark all now track the wallpaper-derived dynamic palette instead of
  static maroon; bottom sheet and VIP dialog still render correctly (rounded corners, no window/
  floating regressions) after the `ThemeOverlay` parent swap.

## Follow-up verification (same round)

- `./gradlew lintDevDebug`: 0 errors, 20 warnings - identical pre-existing set from `BUILD-002`
  (`ContentDescription`/`Icon*`/`SetTextI18n`); none of the files touched by this fix appear in
  the report. No new lint issues introduced.
- Full instrumented regression on TECNO KJ7 via direct `adb shell am instrument` (not Gradle's
  `connectedDevDebugAndroidTest`, which fans out to every attached device - see the `LEAK-001`
  device-policy incident): **119 tests, 2 failures found and resolved, then 0/119 on the
  confirming re-run**:
  1. `LanguageBottomSheetWidgetTest.languagePickerIconsUseConfiguredCompatTints` - a real bug this
     fix surfaced in the *test itself*: it inflated `dialog_language_picker`/`item_language`
     against the raw `InstrumentationRegistry` target context (no `AppTheme` applied, unlike the
     other three tests in the same file), so the new `?attr/colorPrimary` reference had no theme
     to resolve against and crashed with `InflateException`. Fixed by wrapping the inflate context
     in `ContextThemeWrapper(context, R.style.AppTheme)` and resolving the expected color the same
     way production code now does (`MaterialColors.getColor(..., androidx.appcompat.R.attr.colorPrimary, ...)`
     instead of the stale static `@color/colorPrimary` resource).
  2. `SuperWebViewActivityWidgetTest.toolbarShowsRequestedTitle_forAllowlistedUrl` - unrelated
     `ActivityScenario` state flake (`expected:<RESUMED> but was:<CREATED>`), pre-existing and
     unrelated to any file this story touched; passed cleanly on the immediate re-run with no code
     change, confirming it as the same class of flake disclosed in prior rounds (e.g. `LINT-007`'s
     `AppSearchIntegrationTest`).
- `LightAlertDialogCustom` (used by `ext/Context.kt`'s generic confirm-dialog helper) only had its
  two `colorPrimary`-referencing text attrs switched to `?attr`; its parent
  (`Theme.AppCompat.Light.Dialog.Alert`, pre-Material3) and fixed white background were left alone
  deliberately - flagged for a follow-up story if the owner wants full dynamic color there too.

Self-audited **9.6/10** (2026-09-12, TECNO KJ7 — one-off exception this session, S24 Ultra not
connected). Deduction: `LightAlertDialogCustom`'s dialog chrome (not just its two text attrs) is
knowingly left on a pre-Material3 parent rather than fully resolved in this round.
