# UI-025 — Unified Popup Menus Design

- **Date:** 2026-10-02
- **Status:** Draft, awaiting owner review
- **Story:** `p2-ui-ui-025-unified-popup-menus` (est. 3–5 SP)
- **Target branch:** `dev`

## 1. Problem

Two inconsistencies in the Home popup menus, both reported or measured on TECNO KJ7:

1. **Square lens menu.** `ActHome.showLensManagementMenu()` builds `new PopupMenu(this, anchor)`, bypassing `R.style.PopupMenuTheme`. Every other popup (`AppAdapter`, `SearchResultAdapter`, `LensView`) wraps its context in `PopupMenuTheme`, which applies `RoundedPopupMenuStyle` → `bg_popup_menu.xml` (16 dp corners, `colorSurfaceContainer`). The lens menu therefore falls back to the default square background. This is a wiring defect, not a missing Material You rollout; the theme already exists and is correct.
2. **Icon menu opens in the screen centre.** `LensView.showQuickActionsMenu()` anchors the popup to the whole `LensView` with `Gravity.CENTER`, so the menu appears in the same place regardless of which icon was pressed. The existing `ponytail:` note in that method records this as a known, deliberate simplification with the upgrade path "transient anchor View at the icon's rect".

Dialogs use `ShapeAppearance.Material3.Corner.ExtraLarge`; popups use 16 dp. That difference is intentional (different component class) and out of scope. The requirement is "no popup is square".

## 2. Goals

- Lens-management menu uses the same rounded theme as every other popup.
- Icon quick-actions menu opens next to the pressed icon.
- A test makes a future square popup fail the build, so this does not regress silently.

## 3. Non-Goals

- No change to dialog shape, `bg_popup_menu.xml` radius, or Material theme tokens.
- No replacement of `PopupMenu` with a dialog or bottom sheet.
- No menu content changes (no state indicators, no custom-hint label); those are review notes from FISH-016, tracked separately.
- No TalkBack custom action for pull-down search (separate follow-up).

## 4. Design

### 4.1 Lens-management menu (`ActHome.java`)

Wrap the context exactly as the other call sites do:

```java
Context themed = new ContextThemeWrapper(this, R.style.PopupMenuTheme);
PopupMenu menu = new PopupMenu(themed, anchor);
```

Menu content, ids, and click handling are unchanged. `lensManagementMenu` keeps its test-inspection role.

### 4.2 Icon menu anchored to the icon (`LensView.kt`)

`LensView` is a leaf `View`; it draws every icon on one canvas, so there is no child view to anchor to. Use a transient anchor:

- Add `private var mMenuAnchor: View?`.
- `showQuickActionsMenu(app)` resolves the pressed icon's rect from `getAppBounds(index, Rect)` (the cached **base** rect, i.e. the icon's resting cell) — not `mRectToSelect`, which is the magnified, mid-gesture rect and moves with the finger.
- Create a 1×1 px invisible `View` in the parent `ViewGroup` (`FrameLayout` from `item_lens_page.xml`), positioned at the icon's base rect bottom-centre (`translationX/Y`, `LayoutParams` from the rect). If the parent is not a `ViewGroup` or the rect is unavailable, fall back to the current `this` + `Gravity.CENTER` behaviour. Never crash.
- Show the `PopupMenu` anchored to that view with `Gravity.START | Gravity.TOP`-style default; Android's own popup placement flips above/below to stay on screen.
- Remove the anchor in `setOnDismissListener` **and** in `onDetachedFromWindow`, so no orphan view or leaked reference survives a detach, rotation, or page change.
- At most one anchor at a time; a new long-press removes any existing one first.

Using the **base** rect is deliberate: it is stable, requires no per-frame geometry, and the icon returns to this cell when the lens animation ends. The menu appears beside where the icon rests, which is where the user's finger was before magnification. Using the live magnified rect would make the menu depend on animation state.

### 4.3 Guard against future square popups

A unit/instrumented test enumerates every `PopupMenu(` call site in `app/src/main` and fails if one is constructed from a context that is not wrapped in `PopupMenuTheme`. Implemented as a source scan in the JVM test suite (cheap, no device), plus a widget assertion that the lens menu's popup background is the rounded drawable.

## 5. Error and Edge Handling

- No window token / detached view: existing outer `try/catch` returns `false` and no anchor remains.
- Rotation, fold, or page swipe while the menu is open: `onDetachedFromWindow` removes the anchor; popup dismiss callback is idempotent.
- Icon at a screen edge: platform popup placement repositions; no custom clamping.
- Index out of range / empty apps: unchanged guards.
- Reduced motion: unaffected (menu opens immediately today).

## 6. Test Matrix

### Unit (JVM)
- Source scan: every `PopupMenu(` in `app/src/main` is constructed with a `PopupMenuTheme`-wrapped context.

### Widget (device)
- Lens menu: the built `PopupMenu` is created from a context whose `popupMenuStyle` resolves to `RoundedPopupMenuStyle`.
- Icon menu: anchor is added to the parent at the icon's base-rect position, and is removed on dismiss and on detach (no orphan child).
- Fallback: a view whose parent is not a `ViewGroup` still opens via the old path and does not throw.

### Integration (real `ActHome`)
- Long-pressing an icon opens a menu whose anchor lies inside the pressed icon's cell, for two different icons (proves position tracks the icon, not the screen centre).
- Rotating with the menu open leaves no extra child in the page layout.

### Smoke (TECNO KJ7 `115333744A005844`)
1. Long-press empty space: menu has rounded corners.
2. Long-press an icon in the top row, middle, and bottom row: menu appears beside that icon each time.
3. Rotate while open; no crash, no stray view.
4. Confirm pinch, pan, pull-down search, and horizontal paging still work.

## 7. Definition of Done

- All layers above pass; full JVM, lint (0 errors, no new warnings), and full direct-device instrumented suite green on KJ7.
- Smoke recorded with device state restored.
- Independent `/code-review` run; confirmed findings fixed test-first.
- Audit strictly greater than 9.0/10 before any push.
