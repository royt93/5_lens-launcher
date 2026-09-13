# UI-015 — Fix icons hidden/unclickable under search pill + results list not full height

| Field | Value |
|---|---|
| Type | `fix` |
| Status | `done` |
| Priority | `P1` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 2 |
| Risk | Low |
| Dependencies | UI-014 |

## Context

Owner found two bugs live right after UI-014 shipped:

1. The search pill overlapped some grid icons, and those icons became unclickable.
2. The search screen's result list "isn't full height" - most matching apps used a hand-computed
   pixel cap and left the rest of the screen as dead scrim space.

## Bug 1 root cause and fix

UI-014's fix applied `UIUtils.setupEdgeToEdge2` (which calls `View.setPadding()`) directly to
`lensViews` to restore its status-bar clearance after `rootLayout` stopped absorbing insets.
**This did nothing** - `LensView` is a leaf custom `View` that computes its fisheye grid geometry
and touch hit-testing purely from `getWidth()`/`getHeight()` (confirmed by grep: no
`getPaddingTop()`/`getPaddingLeft()`/etc. call anywhere in `LensView.kt`). Padding only changes
where a `ViewGroup` positions its *children* - a leaf view drawing itself must explicitly consult
its own padding, which this one never did (it never needed to before, since the clearance used to
come from the *parent* `rootLayout` being padded, which genuinely shrinks a `match_parent` child's
measured bounds).

Fixed by reusing the margin-based `applyStatusBarInsetAsTopMargin` helper (already added in
UI-014 for `searchBar`) on `lensViews` too - a margin, unlike self-padding, does shrink/shift a
`match_parent` child's actual laid-out bounds regardless of whether the child ever reads its own
padding, so the fisheye grid's own geometry math (still oblivious to padding) now receives
correctly-reduced bounds and draws/hit-tests exactly where it used to.

## Bug 2 root cause and fix

`ActHome.updateSearchResults()` set `rvSearchResults`' height to `min(results.size() * 64dp,
384dp)` - a leftover from before UI-011's all-apps fallback existed, when a handful of matches was
the realistic case. Once a blank query could show the device's entire app list (dozens of apps on
a real phone), this cap left the list stuck at 6 rows tall with the remaining screen empty.

Fixed structurally instead of just raising the cap: the content `LinearLayout` inside `SearchView`
changed from `wrap_content` to `match_parent` height, and `rvSearchResults` changed from
`wrap_content` to `layout_height="0dp"` + `layout_weight="1"` - standard "one weighted child fills
remaining space" pattern. The list now always uses all available height (with its own native
scrolling for overflow), and the manual `resultHeightDp`/`getLayoutParams().height`/
`requestLayout()` code in `ActHome` was deleted entirely - simpler than what it replaced, not just
different.

## Verification and Definition of Done

- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass;
      `./gradlew lintDevDebug` 0 errors, 20 pre-existing warnings, none new.
- [x] Full instrumented regression on Pixel 7 Pro: 141/143 pass; the 2 failures are the same
      already-disclosed pre-existing flakes (font-scale rounding; the order-dependent IME-action
      test) - confirmed one *new*-looking failure
      (`wifiSsidQuickActionOffersPermissionRequestWhenNeverAsked`) was test-environment pollution
      (the owner's own live SEARCH-004 permission grant on this exact device, from this same
      session, not reset between runs) rather than a real regression - passed clean after
      `adb pm revoke`.
- [x] Live smoke on Pixel 7 Pro: tapped an icon in the row directly under the search pill -
      launched correctly (previously dead/unclickable there); opened search with a blank query -
      the all-apps list now visibly extends to the true bottom of the screen instead of stopping
      after 6 rows.

Self-audited **9.4/10** (2026-09-13, Pixel 7 Pro).
