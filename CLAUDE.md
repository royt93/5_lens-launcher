# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android home-screen launcher ("Fisheye Launcher" / "Lens Launcher"). Package `com.mckimquyen`, applicationId `com.mckimquyen.lenslauncher`. Mixed Kotlin + Java codebase (newer code is Kotlin; several core classes — `RApplication`, `ActHome`, `ActSettings`, `ActAbout`, `AppAdapter`, `UtilIconPackManager`, `UtilAppSorter`, `UtilCalculator`, `Logger` — are still Java). minSdk 25, target/compileSdk 37.

Two product flavors (dimension `type`): `dev` and `production` (app name differs: "Fisheye Launcher DEV" vs "Fisheye Launcher"). Build types: `debug` (AdMob test ad unit IDs, unminified) and `release` (real AdMob IDs, minified/shrunk, requires signing). This gives variants like `devDebug`, `productionRelease`, etc.

## Build / test / lint

```bash
./gradlew assembleDevDebug              # build a debug APK, no signing needed
./gradlew assembleProductionRelease     # release build — fails fast if signing isn't configured (see below)

./gradlew testDevDebugUnitTest          # JVM unit tests (Robolectric + JUnit4 + Mockito)
./gradlew test --tests BitmapCacheTest  # single test class
./run_tests.sh                          # convenience wrapper around `./gradlew test`, tees to test_output.log

./gradlew connectedDevDebugAndroidTest  # instrumentation tests, needs a connected device/emulator
./gradlew lintDevDebug                  # Android Lint
```

Release signing (`assemble*Release`, `bundle*Release`) reads `ANDROID_RELEASE_STORE_FILE` / `_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD` from the environment first, falling back to an untracked `keystore.properties` (copy `keystore.properties.example`). A release-artifact task throws a `GradleException` at configuration time if none of this is present; debug builds never need it.

## Architecture

**Single source of truth for app data** is `app/RAppsSingleton` (Kotlin `object`-style singleton, lazily created, all mutation synchronized). It holds the current `List<App>` snapshot; app icons live separately in `util/BitmapCache` (an `LruCache`), keyed by `App.iconCacheKey` — a composite of component name + package version + icon-pack identity, not the bare package name, so icon-pack switches and app updates invalidate correctly (see `doc/task/done/p1-core-core-002-icon-cache-invalidation.md`).

**Refresh pipeline**: `services/TaskUpdateApps` queries `PackageManager` off the main thread (coroutines, not AsyncTask), builds a full apps+icons generation, and commits it atomically via `RAppsSingleton.replaceSnapshot(apps, icons)` — this is generation-guarded so a stale in-flight scan can't clobber a newer one. `services/TaskSortApps` re-sorts the current snapshot when the user changes sort settings. Both are triggered from `app/RApplication` (the `Application` subclass), which also dynamically registers the `services/BroadcastReceivers` inner receivers (apps edited / visibility changed / lock changed / loaded / background changed / night mode changed) — these are *not* declared as implicit manifest broadcasts because Android 8+ ignores those; `RApplication` registers them programmatically instead.

**Eventing**: `services/AppEventManager` exposes these as LiveData. The various `services/*Observable.kt` classes (`EditedObservable`, `LoadedObservable`, `LockChangedObservable`, `UpdatedObservable`, `VisibilityChangedObservable`, `BackgroundChangedObservable`, `NightModeObservable`) are thin backward-compatible wrappers around that LiveData, kept so older call sites don't need to change; new code should observe `AppEventManager` directly.

**Persistence**: Room (`model/AppDatabase`, `model/AppPersistent`, `model/AppPersistentDao`) stores per-app user state that must survive process death and isn't re-derivable from `PackageManager` — visibility, lock, favorite/folder/pinned-zone organization (`model/AppOrganizationRules`, `model/PinnedZone`), open count, custom order. All DB access is async; `model/App` itself is an immutable data class with `copyWith*` methods used to apply DB/organization updates onto the in-memory snapshot without mutating shared references.

**UI shell**: `ui/ActSettings` (Java) is the `LAUNCHER`-category entry point; it hosts three tabs — `ui/FrmLens`, `ui/FrmApps`, `ui/FrmSettings` — via `adt/FragmentPagerAdapter` behind a `ViewPager2` + `TabLayoutMediator`. Each fragment casts its `context`/`activity` back to `ActSettings` to reach interfaces defined in `itf/` (`LensInterface`, `AppsInterface`, `SettingsInterface`) rather than holding a direct dependency. `ui/ActHome` is the separate `HOME`-category activity (what the OS launches when this app is set as the default launcher); `ui/ActFakeLauncher` is a disabled-by-default decoy activity toggled on/off at runtime purely to force the system's "choose default launcher" dialog. `ui/BaseActivity`/`ui/ActBase` centralize locale override (`util/LocaleHelper`, applied in `attachBaseContext`) and adaptive refresh-rate selection (API 30+).

**Rendering**: `views/LensView` draws the fisheye-style app grid; `views/LensGridCache` precomputes and caches per-cell grid geometry (base `RectF`s) so the hot `onDraw` path (continuous while dragging) reuses geometry and a single scratch `RectF` instead of reallocating every frame (`doc/task/done/p2-perf-perf-001-lens-render-hot-path.md`).

**Search**: `search/AppSearchEngine` does local/offline ranked matching over the current app snapshot; `search/SearchHistoryStore` persists recent queries; `search/SearchResultAdapter` renders results. No network or IME-suggestion dependency.

**VIP/ads**: `feature/vip/` (`ActVipManagement`, `VipPrefs`, `VipKeys`, `AdKeys`) gates ad-related behavior; AdMob + AppLovin ad unit IDs are wired through `BuildConfig` fields set per build type in `app/build.gradle`, not hardcoded in source.

**WebView**: `views/SuperWebViewActivity` / `util/WebViewSecurity` enforce an exact HTTPS host allowlist for both initial load and in-page navigation, reject dangerous URI schemes, and disable file/content access and mixed content — don't loosen this without checking `doc/task/done/p1-sec-sec-003-harden-webview-components.md`.

## Working with the backlog

`doc/task/README.md` is the source of truth for product/engineering backlog and owner decisions (scope, device policy, deferred/declined work). Stories move as whole files `doc/task/todo/` → `inprogress/` → `done/`, named `{priority}-{domain}-{id}-{slug}.md`. Read that file before assuming a feature is missing or a decision is still open — several things (CI gates, `store-assets` hardening) have been explicitly declined or deferred by the owner and should not be re-proposed without being asked.

## store-assets/

A separate Next.js + Bun tool (`store-assets/`) for composing Play Store screenshots; it is not part of the shipped Android app and is out of scope for the launcher's product loop by owner decision. Build with `bun run build` inside that directory if you need to touch it.
