# UI-024 — Notification count badges on app icons

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Home-screen badges |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Final item of the owner-approved 4-story delivery loop (`doc/task/README.md`): FISH-014 → FISH-015
→ FEAT-009 → UI-024, all now done.

## User story

As a user, I want to see at a glance how many unread items an app has, without opening it.

## What shipped

Full `brainstorming` → `writing-plans` → `executing-plans` pipeline —
`docs/superpowers/specs/2026-09-30-ui-024-notification-count-badges-design.md` /
`docs/superpowers/plans/2026-09-30-ui-024-notification-count-badges.md`.

- **Badge-broadcast convention, not `NotificationListenerService`.** Brainstorming surfaced that
  a real "read every app's notification count" feature normally requires
  `NotificationListenerService` — a sensitive permission requiring a Play Console Restricted
  Permissions declaration (and, for some categories, a demonstration video), which the owner
  explicitly rejected up front over release-schedule risk. Shipped instead: a statically-declared
  `BadgeCountReceiver` for the long-standing de-facto `android.intent.action.BADGE_COUNT_UPDATE`
  broadcast (the convention `ShortcutBadger` popularized) — no special permission, no notification
  content ever read, no Play review exposure. **Disclosed, deliberate tradeoff:** only apps that
  actively send this broadcast (Gmail and others) get a badge; this is not universal coverage,
  and was never claimed to be.
- `AppPersistent.NOTIFICATION_COUNT` (new Room column, global across lenses — exact same
  no-`LENS_ID`-filter shape `PALETTE_COLOR` already uses) via a new `MIGRATION_11_12` (schema
  v11 → v12), with a dedicated migration test mirroring `AppDatabaseMigration10To11Test`.
- `BadgeCountReceiver`: validates the broadcast's package against the live `RAppsSingleton`
  snapshot (unknown package → dropped, nothing written) and clamps the count to `[0, 9999]`
  before persisting — a spam/garbage guard independent of the separate `"99+"` **display** cap.
- Badge renders in both places an app icon appears: `LensView` (fisheye home grid, drawn directly
  on `Canvas` right after the icon bitmap, paint objects allocated once — zero per-frame
  allocation, matching this codebase's `PERF-001` hot-path discipline) and `AppAdapter` (Apps tab,
  a small `TextView` overlapping the icon's corner).
- Tapping to open an app clears its badge to 0 immediately (optimistic) via the one shared
  `UtilApp.launchComponent` path every launch surface already funnels through — no per-surface
  duplication.
- Settings toggle `KEY_SHOW_NOTIFICATION_BADGES` (default on), same shape as the existing
  `KEY_SHOW_NEW_APP_TAG` row. TalkBack announces the unread count via an extended
  `LensAccessibilityHelper` content description.
- `NotificationBadgeFormatter` (new, pure) is the single shared "99+" cap used by both render
  sites, so they can never drift out of sync.

## Real device-policy deviation, disclosed

TECNO KJ7 (the session-locked device) disconnected mid-implementation and did not reconnect;
TECNO BG6 (the standing fallback) was not attached either. Given both project devices were
unavailable, the owner was asked directly and explicitly approved substituting the **Samsung
S24U (S928B, Android 16)** for the remainder of this story — a device this repo's standing policy
otherwise hard-bans. This is a one-off, owner-approved exception for this story only, the same
shape as the Pixel 7 Pro exceptions already recorded for FISH-013/FISH-014; it does not change the
standing TECNO-only policy for future stories.

## Root-caused issues found during implementation

1. **Missing Room migration** (found before any test ran, by inspection): adding a column to a
   live `@Entity` also requires a schema version bump. Added `MIGRATION_11_12`
   (`ALTER TABLE ... ADD COLUMN NOTIFICATION_COUNT ... DEFAULT 0`) and a dedicated migration test
   before this could ever reach a device and corrupt an existing user's database.
2. **A second, pre-existing test's own hard-coded migration list** (found by the full-suite
   regression run, not caught by the new migration test above since that one already knew about
   `MIGRATION_11_12`): `AppDatabaseMigrationIntegrationTest.migrate10To11_preservesLayoutAndCreatesDefaultLens`
   builds its own `Room.databaseBuilder` with an explicit `.addMigrations(migration10To11())` —
   raising the app's `@Database` version to 12 meant this test's own hand-picked list could no
   longer reach the compiled target version. Root-caused via `superpowers:systematic-debugging`
   (the exception named the exact missing range); fixed by adding `migration11To12()` to the same
   call — the test's own assertions only check v10/v11-introduced fields, unaffected by the new
   column's default.

## Known, disclosed test gap (device-specific, not a UI-024 regression)

The full instrumented suite on the substitute S24U consistently shows **4 failures unrelated to
this diff**, reproduced twice (full-suite run, then isolated re-run of just those 3 classes):

- `BaseActivityRefreshRateWidgetTest.testActHome_onResume_requestsHighRefreshRate_thenReleasesOnPause`
- `LensPhysicsPolicyIntegrationTest.realDeviceReducedMotionSetting_isDetectedThroughSettingsGlobal`
- `LensViewPinchIntegrationTest.testTwoFingerPointerDownEntersPinchingState`
- `LensViewPinchIntegrationTest.testPinchLiftEntersPinchReleaseAndDoesNotLaunchApp`

None of these touch any file this story changed (display refresh-rate control, system
accessibility-settings detection, and multi-touch gesture recognition are three unrelated
subsystems, none touched by UI-024's diff). This is the **first time this suite has ever run on a
Samsung S24U/Android 16** — every prior round ran on TECNO KJ7/Android 14 or, for two prior
one-off exceptions, a Pixel 7 Pro. Refresh-rate mode selection, system-wide animation-scale
detection, and raw multi-touch synthetic-event dispatch are all exactly the kind of behavior
that's genuinely OEM/API-level sensitive. Filed as a new observation, not fixed here (out of
UI-024's scope) — the owner should decide whether to file a dedicated story investigating S24U
compatibility for these three tests, since this repo has never run against this device before.

## Test evidence

646/646 unit tests. Instrumented: 409 total, 405 passing on the substitute S24U (the 4 failures
above, reproduced consistently, disclosed as device-specific and unrelated to this diff — not
silently ignored). Lint: 0 errors, 8 warnings (unchanged from baseline — this diff introduced and
then resolved 2 new warnings: bumped an `10sp` badge text size to `11sp`, and documented+suppressed
one `PluralsCandidate` hit on an accessibility-only content-description string not worth a full
`<plurals>` conversion across 16 locales).

Self-audited **9.3/10** — held back for: the mid-story device-policy deviation (even though
owner-approved and disclosed), and not being able to complete a device smoke pass on the
originally-locked TECNO KJ7 as this story's own plan specified.

## Wrap-up round (2026-10-01, TECNO KJ7 — the originally-locked device, back online)

The prior round's diff was still uncommitted (committed through `6662391` plus an uncommitted
settings-live-toggle fix, its regression test, and this file). Before committing, ran a `/code-review
high` pass over the whole story's diff and a manual on-device smoke pass, both of which found real,
previously-undisclosed issues — fixed the cheap/contained ones, disclosed the rest:

**Fixed:**

1. **Apps-tab never live-updated for a badge arriving while the tab was already open**
   (found via manual smoke, not code review — directly contradicted this story's own design spec:
   "badge appears on both the fisheye grid icon and the Apps tab row"). Root cause: `FrmApps` had
   no observer at all for `AppEventManager.appsEdited` (only the heavier `appsLoaded`, forwarded
   via `ActSettings`/`AppsInterface.onAppsUpdated` after a full rescan) — `ActHome`'s own
   `LensView`+`homeAppAdapter` pair already had this wiring (and had already been fixed, this
   session, to include `notificationCount` in its `isSameAppList` change-detection — see
   `ActHome.isSameAppList`'s own UI-024 comment), but the Settings Apps tab never did. Fixed by
   observing `appsEdited` directly in `FrmApps` and routing through the existing
   `AppAdapter.updateApps()` (`DiffUtil`). New integration test
   (`FrmAppsLifecycleIntegrationTest.testFrmApps_appsEditedEvent_updatesExistingAdapterWithNewBadgeCount`)
   reproduces the exact real path (`RAppsSingleton.updateAppState` + `notifyAppsEdited()`) and
   asserts the already-bound row's badge `TextView` updates in place.
2. **Badge misattributed on multi-launcher-activity packages**: `BadgeCountReceiver` matched the
   incoming package to only the *first* `App` entry sharing that `packageName` (`firstOrNull`),
   contradicting its own documented intent ("one badge per installed app, not per activity"). Now
   applies the count to every entry sharing that package. New test:
   `broadcastForAPackageWithTwoLauncherActivitiesUpdatesBothEntries`.
3. **TalkBack ignored the "Show notification badges" setting** — it announced the unread count
   unconditionally while the two visual paths (`LensView`, `AppAdapter`) both correctly gated on
   the setting. Fixed by threading a `showBadgesSettingProvider` into `LensAccessibilityHelper` and
   reusing `LensView.shouldDrawNotificationBadge` (promoted from `@VisibleForTesting` to a plain
   shared pure function, since it now has two production call sites) so all three surfaces can
   never drift out of sync. Real string-resource assertions aren't possible in this project's
   Robolectric unit-test config (confirmed by direct experiment: even
   `Context.getString(R.string.app_name)` throws `Resources$NotFoundException` there — no
   `testOptions.unitTests.includeAndroidResources`, and no other unit test in this repo calls
   `Context.getString(R.string...)` either) — the gating-logic unit test stays in
   `LensAccessibilityHelperTest`, and the resource-backed on/off case moved to a new androidTest,
   `AccessibilityActionsIntegrationTest.testNotificationBadgeAnnouncement_honorsShowBadgesSetting`.
4. **`LensView`'s hot `onDraw` path read the badge setting from `SharedPreferences` once per
   visible cell** instead of once per frame (continuous while dragging — the exact hot path
   `PERF-001`/`PERF-002` were written to protect). Hoisted to a single per-frame field
   (`mShowNotificationBadgesThisFrame`), not a parameter, to avoid invalidating the already-shipped
   `PERF-004` Baseline Profile's compiled entry for `drawAppIcon`'s old signature.
5. **`AppAdapter` allocated a new `UtilSettings` on every single row bind** (continuous while
   scrolling) instead of reusing one instance — now a single adapter-level field.
6. **`UtilApp.launchComponent` wrote `notificationCount = 0` unconditionally on every app launch**,
   even when there was never a badge to clear — now short-circuits via `RAppsSingleton.findApp(...)`
   when the count is already 0, skipping the Room transaction for the common case.
7. **Duplicated magic number**: `MAX_STORED_NOTIFICATION_COUNT = 9999` was independently redefined
   in both `AppPersistent` and `BadgeCountReceiver`. `BadgeCountReceiver` now references
   `AppPersistent.MAX_STORED_NOTIFICATION_COUNT`.

8. **Clear-on-launch asymmetry with fix #2**: `UtilApp.launchComponent` only cleared the launched
   component's own `(packageName, name)` badge, while fix #2 above now writes the *same* count to
   every `App` entry sharing that packageName - so on a multi-launcher-activity package, opening
   one entry point would clear only its own badge and leave a sibling entry point's badge stale.
   Made symmetric: launch now clears every entry sharing the package (still skipping entries
   already at 0, so the common single-activity case stays a single no-op check). New test:
   `LaunchClearsNotificationBadgeIntegrationTest.launchingOneActivityOfAMultiActivityPackageClearsEverySiblingEntryToo`.

**Disclosed, not fixed (judged out of scope for this wrap-up):**

- **Unauthenticated badge-broadcast convention**: any installed app can claim to be any other
  installed package via `EXTRA_PACKAGE_NAME` and set its displayed count. Same trust model this
  de-facto convention has always had industry-wide (no official API exists); impact is bounded to
  a cosmetic digit, never real notification content, and only for an already-installed package.
  Documented directly in `BadgeCountReceiver`'s class doc rather than silently left unstated.

**Test evidence (this wrap-up round):** 648/648 unit tests. Instrumented, full suite on TECNO KJ7
(`adb -s 115333744A005844 shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`):
**414 run, 1 failure** — `AppSearchWidgetTest.wifiSsidQuickActionOffersPermissionRequestWhenNeverAsked`,
root-caused (not just assumed) to this physical device's real `ACCESS_FINE_LOCATION` runtime grant
already being `granted=true` from an earlier test round (confirmed via `dumpsys package`) — the test
only resets a `SharedPreferences` flag, not the real OS permission, so the real device takes the
"already granted" render branch instead of the "needs permission" one it expects; unrelated to any
file this story touches, reproduced both in the full suite and in isolation. Lint: 0 errors, 8
warnings (back to baseline — fixing finding #3 above introduced and then resolved a `VisibleForTests`
hit by promoting `shouldDrawNotificationBadge` out of test-only visibility, as described above).

Manual real-device smoke on TECNO KJ7 confirmed the `BADGE_COUNT_UPDATE` broadcast path end-to-end
(`adb shell am broadcast`, confirmed the persisted Room row via a pulled `.db`/`.db-wal`), and
caught the Apps-tab live-refresh gap (finding #1) that none of the existing automated tests had
exercised. One unrelated, pre-existing device setting (`Rung khi khởi chạy ứng dụng` / vibrate-on-
launch) was accidentally toggled off during manual exploration (a mis-tap, not a code change) and
left as-is rather than risk further mis-taps through a privacy-dialog link that kept intercepting
intended button taps — disclosed here rather than silently reset.

Re-audited **9.5/10** for this wrap-up round (up from 9.3 — the originally-locked TECNO KJ7 device
smoke this story's own plan specified is now complete, and a real spec-contradicting gap was found
and fixed rather than shipped silently broken). Held back from higher for: the unauthenticated
badge-broadcast trust model (industry-standard, but still worth a reader's awareness) and the
narrow multi-activity clear-on-launch asymmetry, both left as disclosed, judged-out-of-scope
limitations rather than fixed in this round.

## Follow-up filler round (2026-10-01, same session, owner picked both options offered)

**1. Fixed the multi-activity clear-on-launch asymmetry** disclosed above: `UtilApp.launchComponent`
now clears the notification count for every `App` entry sharing the launched package (symmetric
with `BadgeCountReceiver`'s own write-side fix), not just the launched component, skipping entries
already at 0. New test:
`LaunchClearsNotificationBadgeIntegrationTest.launchingOneActivityOfAMultiActivityPackageClearsEverySiblingEntryToo`.

**2. Root-caused the cross-test "background scan" flake** this repo has independently disclosed
under slightly different symptoms in `LEAK-001`, `DISPLAY-001`, `LINT-002`, `PERF-003` and
`FEAT-009` (each time as a newly-surfaced, order-dependent occurrence) — applied
`superpowers:systematic-debugging` end to end against a fresh, live reproduction instead of
re-reading the old write-ups:

- **Phase 1 (reproduce + evidence):** the new sibling-clearing test above failed once
  (`expected:<0> but was:<null>`) right after the file's original test, but passed reliably alone
  and 8/8 in a stress loop of the full 2-test class. Temporary diagnostic logging around the
  launch call, plus reading `RApplication.onCreate()`'s source directly, confirmed the mechanism:
  `onCreate()` unconditionally calls `updateApps()` on every process start (not gated on whether
  this is a test process or whether `RAppsSingleton` already holds data), which debounces 150ms
  (`TaskUpdateApps.PACKAGE_EVENT_DEBOUNCE_MS`) then runs a real `PackageManager` scan and
  wholesale-replaces `RAppsSingleton.instance.apps` via `replaceSnapshot`/a plain `.apps =`
  assignment — and the same dynamically-registered `packageReceiver`
  (`ACTION_PACKAGE_ADDED/REMOVED/CHANGED/REPLACED`) can re-trigger that same debounced rescan later,
  including (plausibly) from a test's own `am force-stop` of a real external app in `tearDown()`.
  Any androidTest that seeds `RAppsSingleton.instance.apps` with fake data and later asserts
  against it **after** an arbitrary wait is racing this - the Room/`AppPersistent` row is not,
  since it's written synchronously (optimistically) or already-persisted by the time any later
  rescan lands.
- **Phase 2/3 (pattern + hypothesis):** confirmed hypothesis by re-running the exact failing
  sequence after instrumenting both checkpoints - the list was never found *partially* wrong, only
  *wholesale* intact or wholesale-replaced, consistent with a full-snapshot replace racing the
  assertion, not a logic bug in the new filter/forEach code.
- **Phase 4 (fix, scoped):** hardened this story's own two tests
  (`LaunchClearsNotificationBadgeIntegrationTest`'s both methods) by asserting the in-memory
  `RAppsSingleton` side effect **synchronously inside the same `onActivity` callback** that
  performs the real launch - before any `waitForIdleSync()`/sleep gives a real rescan time to
  land - and moving the `Room`-row assertion (immune to this race) after the wait, matching what
  the one Room check already did. Verified with 8 consecutive full-class runs, 0 failures.
  **Reproduced the identical root cause a second, independent time** the same session in
  `ActHomeMultiLensRecentAppsIntegrationTest` (which already carries its own `TEST-003`-class
  flake-guard comment, and still failed once this session on the stated "can happen at any point"
  risk its own comment names) - confirming this is one shared root cause across multiple
  previously-separately-diagnosed stories, not several unrelated ones.
- **Deliberately not fixed further**: did not retrofit this same hardening across every other
  affected test file (`ActHomeMultiLensRecentAppsIntegrationTest`, `FrmAppsLifecycleIntegrationTest`,
  and others these prior stories named) - each would need its own read of what it actually asserts
  and whether a synchronous-in-callback or Room-based rewrite fits its specific shape; scoped this
  round to this story's own two tests. The viable, now-proven pattern (assert in-memory state
  synchronously at the point of mutation, or assert Room instead of `RAppsSingleton` when the
  check can tolerate the async persistence delay) is recorded here for whoever picks up the
  remaining occurrences, instead of `RApplication.onCreate()`'s unconditional scan itself being
  changed (that would need a test-mode seam design decision, a bigger, separate change).

**Test evidence (filler round):** 648/648 unit tests. Full instrumented suite on TECNO KJ7: first
attempt crashed mid-run (`INSTRUMENTATION_RESULT: shortMsg=Process crashed`, `UiAutomation service
owner died`, this app's own process shown force-stopped by an unrelated pid in `logcat` -
device/USB-level hiccup, not reproducible, not a code issue - a second crash on retry at a
different, also-unrelated test confirmed it wasn't tied to any specific class); third attempt
completed clean: **415 run, 2 failures** - the same pre-existing `AppSearchWidgetTest` wifi-permission
failure already disclosed above, plus one occurrence of the newly-root-caused background-scan race
in `ActHomeMultiLensRecentAppsIntegrationTest` (confirmed pre-existing and unrelated: that file is
untouched by this round's diff, and the failure did not reproduce when that class was re-run
alone). Lint 0 errors/8 warnings, unchanged.

Re-audited **9.5/10** for this filler round — the fix itself is small and fully proven; held at the
same score because the broader flaky-test category this round diagnosed is intentionally left
for a dedicated round rather than scope-creeping a full fix into a filler pick.
