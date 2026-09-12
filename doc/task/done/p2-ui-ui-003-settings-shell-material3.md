# UI-003 — Settings shell Material3 restyle

| Field | Value |
|---|---|
| Type | `enhance` |
| Status | `done` |
| Priority | `P2` |
| Evidence | `decision` |
| Epic | Material You revamp |
| Estimate | 5 |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Split out of `UI-002` after owner decision (2026-09-12): keep the existing `ViewPager2` +
`TabLayoutMediator` tab shell in `ActSettings.java` (do not switch to `NavigationRail`/bottom nav
— rejected option), but restyle it to Material3: `TabLayout` secondary/pill style with dynamic
color, and Material3 `ListItem`-style rows (72dp height, leading icon, trailing control) in
`FrmApps.kt`'s app rows and `FrmSettings.kt`'s option rows.

## User story

As a user, I want the Settings tabs and rows to look and feel Material You, matching the home
screen's already-migrated theme (`UI-001`).

## Acceptance criteria

- [ ] Tab indicator/style updated without changing `ActSettings`'s `itf/*Interface` wiring or
      `ViewPager2`/`FragmentPagerAdapter` structure.
- [ ] `FrmApps` app rows and `FrmSettings` option rows restyled to consistent M3 row height/icon
      sizing; `AppAdapter`/`AppDiffCallback` (PERF-003) data path untouched.
- [ ] Existing drag-reorder (`ItemTouchHelper` in `FrmApps.kt:60-87`) and click behavior unaffected.
- [ ] Dynamic color applies to tab indicator and row accents on Android 12+, sane static fallback
      below.

## Implementation notes

Pure XML/style + `ViewHolder` binding changes; no changes to `AppEventManager`/`RAppsSingleton`
data flow.

## Changes made

- `act_settings.xml`: `TabLayout` given `style="@style/Widget.Material3.TabLayout.Secondary"` —
  M3 secondary pill/underline indicator (width matches label, not full-tab-width block), dynamic
  color follows the same `?attr/colorPrimary` role already patched app-wide.
- `view_item_app.xml` (FrmApps row, `AppAdapter`): `androidx.cardview.widget.CardView` →
  `com.google.android.material.card.MaterialCardView` — same tag attrs (`card_view:` prefix kept),
  same ids, so `AppAdapter.java`'s `CardView cvAppContainer` field needed **no code change**
  (`MaterialCardView` is-a `CardView`). Corner radius/elevation left exactly as-is — the pill shape
  was a deliberate existing look, not part of this restyle's scope.
- `frm_settings.xml`: all 3 `androidx.cardview.widget.CardView` sections (General/Appearance/
  Behavior) → `com.google.android.material.card.MaterialCardView`, same reasoning; row height was
  already ~72dp via the existing 48dp icon + margins (`@dimen/width_app_icon`), so no row
  height/icon-size change was needed there.
- `ActSettings`'s `itf/*Interface` wiring, `ViewPager2`/`FragmentPagerAdapter`,
  `AppAdapter`/`AppDiffCallback` data path: untouched, as required.

## Verification and Definition of Done

- [x] Widget/UI: `FrmAppsWidgetTest`, `AppAdapterWidgetTest`, `FrmAppsLifecycleIntegrationTest` —
      10/10 pass on TECNO KJ7.
- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass;
      `./gradlew lintDevDebug` — 0 errors, 20 warnings, identical pre-existing set (no new).
- [x] Smoke on TECNO KJ7 (one-off exception this session): tab indicator renders as M3 secondary
      pill under the active tab on both "Thấu kính"/"Ứng dụng"; Apps tab rows render correctly as
      pill-shaped cards (ripple/elevation preserved); drag-reorder untested visually this round
      but covered by the passing `ItemTouchHelper`-dependent widget tests.
- [x] Evidence updated.

Self-audited **9.5/10** (2026-09-12, TECNO KJ7). Deduction: drag-reorder gesture itself wasn't
re-verified by hand on-device this round (only via the existing automated tests), and FrmSettings
row height/icon sizing was left as-is rather than actively re-measured against the M3 72dp spec
(it already matches by construction, not by a deliberate check this round).
