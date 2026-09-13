# UI-008 — Whole-app theme-attr-override audit; fixed LightAlertDialogCustom's shadowed dynamic color

| Field | Value |
|---|---|
| Type | `fix` |
| Status | `done` |
| Priority | `P1` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 2 |
| Risk | Low |
| Dependencies | UI-005 |

## Context

Owner asked (2026-09-13) for a full re-audit of theme-attribute overrides across the app for the
same class of bug UI-005 found with `colorAccent` (an app-level static override silently shadowing
a value `DynamicColors` would otherwise patch), before starting new feature work.

**Also corrects a wrong claim made earlier in this session's investigation**: the
"Điều khoản và Chính sách bảo mật" consent dialog was misidentified as Google's User Messaging
Platform (a search of `values/strings.xml` alone found no match). It is in fact this app's own
`showDialog2`/`LightAlertDialogCustom` (`ext/Context.kt:96`, called from `ActSettings.java:214`) -
the string lives in `values-vi/strings.xml`, which that earlier search missed.

## Audit method

Reviewed every `style` declaration in `values/styles.xml` for (a) explicit `colorPrimary`/
`colorAccent`/other M3-role overrides that could shadow a value DynamicColors patches through a
different attribute name (the `colorAccent`→`colorSecondary` class of bug), and (b) any style used
as a full replacement `Theme.*` for a Dialog/BottomSheet/Popup (as opposed to a `ThemeOverlay.*`
applied on top of an already-patched Activity context) - the second class of bug UI-005 also found
(`MaterialYouDialogTheme`, `TransBottomSheetDialog`).

`AppTheme`/`HomeTheme` (the two real app themes): only `colorPrimary`, `colorPrimaryDark` (a
legacy AppCompat attr `DynamicColors` never touches regardless - intentionally static, not a bug),
and `colorSecondary` (fixed under UI-005) are set. No further shadow bugs found here.

## Finding: `LightAlertDialogCustom` (one remaining instance of the theme-class bug)

Confirmed live on Pixel 7 Pro: the dialog's title rendered in a near-invisible pale color against
its white background. Root cause: `LightAlertDialogCustom`'s parent was
`Theme.AppCompat.Light.Dialog.Alert` - a full standalone `Theme`, not a `ThemeOverlay`. That
ancestry chain (`Theme.AppCompat.Light`) redeclares its own generic static
`colorPrimary`/`colorAccent`/`textColorPrimary` defaults, which silently override whatever this
dialog should have inherited from the wrapped (dynamic-color-patched) Activity context via
`ContextThemeWrapper` - the exact same shadowing mechanism as `UI-005`'s `colorAccent` bug, just in
a third dialog. This one was explicitly flagged and deliberately left unfixed in `UI-005`'s
write-up ("flag for a follow-up if the owner wants full dynamic color there too") - this is that
follow-up, now backed by live visual proof rather than a theoretical concern.

## Fix

Switched `LightAlertDialogCustom`'s parent to `ThemeOverlay.AppCompat.Dialog.Alert` - confirmed
(by inspecting the decompiled appcompat AAR) to be a thin overlay that only sets window
min-width dimens, not a full theme redeclaring color roles - so it now properly inherits
`colorPrimary` from the Activity instead of being shadowed. The dialog's own deliberate static
choices (`android:background="@color/colorWhite"`, `android:textColorPrimary="@color/colorBlack"`)
were kept unchanged - always-light dialog styling was intentional here, not a dynamic-color
concern.

## Verification and Definition of Done

- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass;
      `./gradlew lintDevDebug` 0 errors, 20 pre-existing warnings, none new.
- [x] Full instrumented regression on Pixel 7 Pro: 133/134 pass; the 1 failure is the same
      pre-existing device-specific font-scale rounding difference already disclosed in `UI-007`.
- [x] Live smoke on Pixel 7 Pro (`pm clear` + fresh launch to reliably reproduce the
      first-run consent dialog): title now renders in the dynamic-color-derived tone, matching
      the rest of the screen, clearly legible against the white panel.
- [x] Full grep audit confirmed zero regressions from this session's other stories (SEARCH-002,
      SEARCH-003, UI-006, UI-007): no new static `@color/colorPrimary`/`@color/colorAccent`
      references crept into any layout/drawable added or touched since UI-005.

Self-audited **9.6/10** (2026-09-13, Pixel 7 Pro). Note: this also serves as a correction to an
earlier wrong claim in this same session's investigation (misidentifying this dialog as a
Google SDK component) - recorded here for the record, not to relitigate.
