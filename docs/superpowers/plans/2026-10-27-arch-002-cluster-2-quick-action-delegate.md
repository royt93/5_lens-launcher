# ARCH-002 cluster 2 — `HomeQuickActionDelegate` (detailed plan)

Parent: `docs/superpowers/plans/2026-10-27-arch-002-split-acthome.md`. Written from `ActHome.java` at `68aac523` (2.185 lines). **Device: Pixel 7 Pro `2B051FDH3006MU`** (owner request "dùng pixel thay thế đi"); never a Gradle `connected*` task; screen awake and unlocked. Pixel baseline before cluster 1: 35/35 `ActHome*` classes; after cluster 1: 39/39 classes (188 tests) incl. 4 extra.

**Goal:** Move the quick-action row, flashlight, Wi-Fi SSID, DND, contact row and permission-result handling out of `ActHome` into `ui/home/HomeQuickActionDelegate.java` (about 230 lines), with no behavior change.

**Language:** Java, same reason as cluster 1 (verbatim copy, no hand translation of lifecycle-sensitive code).

## What was checked in the code and tests (2026-10-27)

- Tests read **no** quick-action or contact field by reflection (reflection into `ActHome` is limited to `hasReportedFullyDrawn`, `lensTabMediator`, `searchBackCallback`, `searchView`), so the fields below can move freely.
- Tests reach the rows through `findViewById(R.id.quickActionRow / tvQuickActionValue / contactActionRow ...)`.
- `ActHomePermissionResultFlagsIntegrationTest` calls the **public override** `ActHome.onRequestPermissionsResult(code, permissions, results)` with hard-coded codes `1001/1002/1003`. The override and the three numeric values must not change; the override stays on `ActHome` and delegates.
- Other test seams (`setPermissionGrantedForTest`, `setContactsForTest`) live in `ContactSearchEngine`/`ContactSearchController`, not in `ActHome`, so they are unaffected.

## What moves (verbatim bodies; only Activity references redirected)

| `ActHome` member | To the delegate |
|---|---|
| `REQUEST_CODE_CAMERA/WIFI_SSID/CONTACTS` (1001/1002/1003) | `public static final` with the same values |
| fields `quickActionRow`, `quickActionDivider`, `tvQuickActionLabel`, `tvQuickActionValue`, `contactActionRow`, `contactActionDivider`, `tvContactResultName`, `tvContactResultPhone`, `btContactCall`, `btContactMessage`, `contactSearchController`, `flashlightOn` | private fields |
| `setupViews` lines for those views and `ContactSearchController.create(...)` | `attach()` |
| `updateQuickAction(CharSequence)` | `updateQuickAction` (public) |
| `toggleFlashlight`, `performFlashlightToggle`, `requestWifiSsidPermission`, `openDndAccessSettings`, `performDndToggle` | private |
| `updateContactAction`, `renderContactSearchState`, `requestContactsPermission`, `startContactIntent` | `updateContactAction` public, the rest private |
| body of `onRequestPermissionsResult` after `super.` | `onPermissionResult(int, String[], int[])` |
| `contactSearchController` close in `onDestroy` | `onDestroy()` |

## What stays on `ActHome`

- `public void onRequestPermissionsResult(...)`: `super.onRequestPermissionsResult(...)` then `quickActions.onPermissionResult(...)`.
- The three call sites that ask for a refresh of the quick-action row: `updateSearchResults` (`updateQuickAction(query)` and `updateContactAction(query)`) and `onResume` (`updateQuickAction(appSearch.getText())`) become `quickActions.updateQuickAction(...)` / `quickActions.updateContactAction(...)`.
- `updateWebSearchFallback()` and `hideSearch()` stay (cluster 3 owns them). The delegate reaches them only through the host.
- `updateWebSearchFallback(query, isEmptyQuery, hasResults)` reads `quickActionRow.getVisibility()` and `contactActionRow.getVisibility()`. These two reads must keep working: expose `quickActionRowVisible()` and `contactActionRowVisible()` on the delegate and call those from `ActHome`. Do **not** pass the views out.

## Host interface `HomeQuickActionHost`

```java
public interface HomeQuickActionHost {
    android.app.Activity activity();
    CharSequence searchText();          // appSearch.getText(); never null here, may be empty
    void hideSearch();                  // ActHome.hideSearch() (cluster 3 later)
    void refreshWebSearchFallback();    // ActHome.updateWebSearchFallback()
}
```

Four methods only. `searchText()` returns `appSearch.getText()`; the delegate never touches the EditText.

## Characterization tests (before moving)

Pure decisions inside this block that are not unit-tested today:

1. **Permission-result routing** in `onRequestPermissionsResult`: which flag key and permission string is recorded for each request code, and which refresh follows (`CAMERA` granted -> perform toggle; `CAMERA` denied -> refresh row; `WIFI_SSID` -> refresh row; `CONTACTS` -> refresh contact). Extract `static Action routePermissionResult(int requestCode, boolean granted)` returning an enum `{ TOGGLE_FLASHLIGHT, REFRESH_QUICK_ACTION, REFRESH_CONTACT, NONE }`, plus `static String flagKeyFor(int requestCode)` and `static String permissionFor(int requestCode)` (return `null` for unknown). Unit test every request code, granted/denied, an unknown code, and the empty `grantResults` case (`granted` is false).
2. **Torch trigger rule:** `performFlashlightToggle` flips `flashlightOn` and returns the new state; extract the flip to `static boolean nextTorchState(boolean current)` (trivial, but pins the single-toggle contract).
3. **Quick-action value label:** `flashlightOn ? on : off` and `dndToggle.isOn() ? on : off` use the same two string ids. Extract `static int onOffLabelRes(boolean on)` returning `R.string.quick_action_flashlight_on/off` and test it (Robolectric not needed; `R` ids are ints).

Write `HomeQuickActionLogicTest` (JVM). Switch `ActHome` to call these functions **before** the move and run `ActHomePermissionResultFlagsIntegrationTest`, `AppSearchWidgetTest`, `DndQuickActionIntegrationTest`, `ScanQuickActionIntegrationTest` on the Pixel, then move.

## Steps

- [ ] 1. `HomeQuickActionLogicTest` (red: unresolved reference); add the three static functions in a new `HomeQuickActionDelegate` shell; green.
- [ ] 2. Make `ActHome.onRequestPermissionsResult`, `performFlashlightToggle` and the two label ternaries call the pure functions; build; run the four device classes above on the Pixel.
- [ ] 3. Create `HomeQuickActionHost`; copy the table's blocks into the delegate with references redirected (`getString` -> `host.activity().getString`, `startActivity` -> `host.activity().startActivity`, `getSystemService` -> `host.activity().getSystemService`, `appSearch.getText()` -> `host.searchText()`, `hideSearch()` -> `host.hideSearch()`, `updateWebSearchFallback()` -> `host.refreshWebSearchFallback()`); compile.
- [ ] 4. In `ActHome`: implement the host, delete the moved members, keep the public override, add `private final HomeQuickActionDelegate quickActions`, call `quickActions.attach()` where the views were found in `setupViews`, call `quickActions.onDestroy()` in `onDestroy`, replace the three refresh call sites and the two visibility reads.
- [ ] 5. `./gradlew testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest` then `lintDevDebug` run separately (running them together once hit a kapt-stub race); green.
- [ ] 6. Mutation on the three pure functions (each mutation must compile; restore; `--rerun-tasks` before installing).
- [ ] 7. Back up Pixel data, install, run all 35 `ActHome*` classes plus `MoveAppToLensWidgetTest`, `LensManagementActivityWidgetTest`, `FrmLensWidgetTest`, `LauncherIntegrationTest` and the cluster extras: `AppSearchWidgetTest`, `DndQuickActionIntegrationTest`, `ScanQuickActionIntegrationTest`, `ContactSearchIntegrationTest` (note the last three live in `com.mckimquyen.search`), using `/tmp/run_acthome_pixel.py` extended with these classes. Restore app data and verify byte for byte.
- [ ] 8. Check `wc -l ActHome.java`, `git diff --check`, `bash scripts/check-secrets.sh`; commit `refactor(home): move quick actions and contact row into HomeQuickActionDelegate (ARCH-002 cluster 2)` with before/after line counts.

## Stop conditions

Any red class stops the cluster. If a block cannot be moved verbatim (for example it reads a field that is not on the host), stop and record why. Do not change a request code, a string id, a permission constant or a SharedPreferences key.
