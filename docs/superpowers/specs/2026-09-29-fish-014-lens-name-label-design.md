# FISH-014 — Active Lens Name Label Design

- **Date:** 2026-09-29
- **Status:** Approved
- **Story:** `p2-fish-fish-014-active-lens-name-label`
- **Target branch:** `dev`

## 1. Problem

FISH-008's multi-lens home screen shows only an unlabeled dot indicator. With names such as “Work”, “Personal”, and “Travel”, users cannot tell which lens is active without remembering dot positions. Single-lens users do not need more chrome.

## 2. Goals

- Show active lens name only when at least two lenses exist and Fisheye mode is visible.
- Keep single-lens UI byte-for-byte uncluttered: label is `GONE`, not invisible.
- Update immediately after page selection, rename, create, delete, list reload, and Activity recreation.
- Long-press label opens existing lens-management menu, matching dots indicator.
- Use native `TextView` semantics so TalkBack reads visible lens name and exposes long-click action.

## 3. Architecture

### Layout

Add `TextView` `tvLensName` to `app/src/main/res/layout/act_home.xml`, directly above `lensPageIndicator`, centered at bottom. Default `visibility="gone"`. No string resource: content is `LensWorkspace.name`.

### State resolution

Add pure resolver (package chosen during planning based on current patterns):

```kotlin
fun resolveActiveLensName(
    lenses: List<LensWorkspace>,
    activeLensId: String?
): String?
```

Rules:
1. Empty list → `null`.
2. Matching `activeLensId` → that lens name.
3. Missing/null active id → first lens name.

### ActHome wiring

- Bind `tvLensName` in `setupViews()`.
- Give it same long-press behavior as `lensPageIndicator`.
- Centralize visibility/text synchronization in one helper rather than duplicating writes across all lifecycle sites:

```java
private void updateLensNavigationChrome() {
    boolean visible = isFisheyeModeVisible()
            && currentLenses.size() > 1
            && !currentLenses.isEmpty();
    lensPageIndicator.setVisibility(visible ? View.VISIBLE : View.GONE);
    tvLensName.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (visible) {
        int position = lensPager.getCurrentItem();
        if (position >= 0 && position < currentLenses.size()) {
            tvLensName.setText(currentLenses.get(position).getName());
        } else {
            String activeId = utilSettings != null
                    ? utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                    : null;
            tvLensName.setText(LensLabelResolver.resolveActiveLensName(currentLenses, activeId));
        }
    }
}
```

Every existing direct `lensPageIndicator.setVisibility(...)` call affected by current mode/data changes is replaced by this helper. `lensPageChangeCallback.onPageSelected()` invokes it after active-lens persistence. Rename/create/delete callbacks invoke it after `currentLenses` and pager state settle. List mode, loading, and no-app states hide both label and dots.

## 4. Behavior

- **1 lens:** both label and dots `GONE`.
- **2+ lenses:** label and dots visible; label matches currently selected page.
- **Swipe:** label updates in page-selection callback.
- **Rename:** active lens label updates after reload without restart.
- **Create second lens:** label appears as lens count changes 1→2.
- **Delete to one lens:** label disappears as count changes 2→1.
- **List mode/loading/no apps:** label hidden with dots; restored when Fisheye mode resumes.
- **Recreation:** active lens id restores pager and label.
- **Long-press:** opens existing management menu, no separate click action.

## 5. Tests

### Unit — `LensLabelResolverTest`

Cases:
1. Matching active id resolves its name.
2. Missing active id falls back to first lens.
3. Null active id falls back to first lens.
4. Empty list returns null.

### Widget — `ActHomeLensLabelWidgetTest`

Cases:
1. Single lens hides label.
2. Multiple lenses show active lens name.
3. Swipe updates label.
4. Rename active lens updates label live.
5. Delete to one lens hides label.
6. Long-press label opens lens-management menu.

### Integration — `ActHomeLensLabelIntegrationTest`

Real Room + SharedPreferences + Activity recreation + ViewPager2 boundary:
1. Seed default and Work lenses in Room.
2. Persist `KEY_ACTIVE_LENS_ID = work`.
3. Launch fresh `ActHome` and verify pager restores Work plus label reads “Work”.
4. Recreate Activity and verify Work label survives.
5. Delete Work, refresh lens data, and verify fallback to default plus label hidden because one lens remains.

### Smoke

On session-locked device:
1. Create second lens named Work.
2. Verify label appears above dots.
3. Swipe pages, rename, rotate/recreate, delete back to one lens.
4. Verify label tracks every state and disappears at one lens.

## 6. Non-Goals

- Label in single-lens mode.
- Styling/name truncation customization beyond native one-line ellipsis.
- New persistence or schema.
- Replacing dot indicator.
- Making label clickable; only long-click is supported.
