# FEAT-013 — Search "new" lists apps installed in the last 7 days

Date: 2026-10-27. Story: `doc/task/todo/p3-feat-feat-013-recent-install-quick-action.md` (P3, 2 SP).

## Problem

A user who just installed an app has no quick way to find it by installation time. `App.installDate` already holds
the first-install time (`UtilApp.getAppShells`, from `PackageInfo.firstInstallTime`) and needs no new permission.

## Evidence check (code read on 2026-10-27)

| Story claim | Current code | Consequence |
|---|---|---|
| `installDate` exists in `App` | Yes, filled for primary-user apps | Reuse |
| "Use the `QuickActionEngine` pipeline" | `QuickActionEngine.resolve` returns one `QuickAction` (one row); it cannot list apps | Put the logic in `AppSearchEngine`, whose result list already renders rows with icon, badge and menu |
| Respect hidden apps | `AppSearchEngine` filters `App::isVisible` | Same filter |
| (not in story) work-profile apps | `ProfileAppSource.toShells` never sets `installDate`, so it is 0 | Work apps would never count as recent; fix by filling it |
| (not in story) existing "NEW" grid tag | `LensView` uses `SHOW_NEW_APP_TAG_DURATION` = 12 hours and only while `openCount == 0` | Different, shorter definition; kept as is |

## Decisions (owner, 2026-10-27)

1. **Presentation:** matching apps go into the normal search result list (not a single quick-action row).
2. **Window:** a separate named constant, 7 days, counting apps that were already opened. The 12-hour/unopened
   rule of the grid tag is unrelated and unchanged. Two definitions of "new" is intentional and recorded here.

## Design

- `AppSearchEngine.RECENT_INSTALL_DAYS = 7` and `RECENT_INSTALL_KEYWORDS = setOf("new", "recent", "moi cai", "vua cai")`.
  Keywords are compared after `normalize`, so accents and case do not matter.
- `AppSearchEngine.recentInstalls(apps, nowMillis, limit)`: visible apps with
  `installDate > 0 && nowMillis - installDate <= RECENT_INSTALL_DAYS * DAY_MILLIS`, newest first, ties broken by
  normalised label then `componentKey`, capped at `limit`. A future `installDate` (clock skew) is treated as recent.
- `AppSearchEngine.search(...)` gains an injectable `nowMillis: Long = System.currentTimeMillis()` parameter
  (`@JvmOverloads`). When the **whole** normalised query equals one keyword, it returns `recentInstalls`; every
  other query takes the existing path untouched. A multi-word query that merely contains "new" is not a trigger.
- T9 mode is unaffected (digits only).
- Work-profile apps: `ProfileAppEntry` gains `installDate: Long = 0L`; `LauncherAppsDirectory` fills it from
  `PackageManager.getPackageInfo(pkg, 0).firstInstallTime` for the profile's package when readable, else 0;
  `ProfileAppSource.toShells` copies it into `App.installDate`.
- No new UI, string, permission or dependency. The keywords live in code and are not shown to the user.

## Trade-off accepted

Typing exactly `new` (or `recent`, `moi cai`, `vua cai`) no longer ranks an app literally named "New ..." by label
for that query; the recent-installs list replaces it. Typing more characters (e.g. `new york`) restores the normal
ranking. Recorded in the audit.

## Out of scope

A settings toggle for the window or keywords, a header row explaining the list, localisation of the keywords
beyond the four above, and changing the grid NEW tag.

## Test plan

Unit, red first (`AppSearchEngineTest`): window boundary exactly 7 days and one millisecond past; hidden apps
excluded; `installDate = 0` excluded; newest first with deterministic ties; capped at the shared limit; each
keyword and accent variant triggers; a longer query containing "new" does not; non-trigger queries unchanged;
future install date counted as recent. `ProfileAppSourceTest`: `installDate` carried into the shell.
Device (`AppSearchIntegrationTest`-style or `ActHomeT9KeypadWidgetTest` sibling): typing `new` in Home search lists a
seeded recent app. Work-profile install date is covered by a unit test with a fake directory; no work profile is
needed on the locked device.
