# LINT-004 — Replace platform APIs with their KTX equivalents (UseKtx)

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Code health |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Owner-picked as one of three parallel follow-ups once the real backlog was confirmed still exhausted. `UseKtx` (40 warnings) is Kotlin-only (Java files like `ActSettings.java` use the same underlying `Color.parseColor` calls but aren't flagged — KTX extensions are Kotlin-specific, confirmed by checking which files lint listed). Every suggestion is a 1:1 wrapper around the exact same platform call — `androidx.core`'s KTX extensions are thin, Google-maintained wrappers, not reimplementations — so this batch is mechanical, not a design change.

## Changes

- **`String.toUri()`** (17 occurrences: `ActVipManagement.kt`, `ext/Activity.kt` ×8, `ext/Context.kt` ×3 distinct call sites reported twice each by lint, `util/WebViewSecurity.kt`) replacing `Uri.parse(x)`.
- **`String.toColorInt()`** (11 occurrences, all in `ActVipManagement.kt`) replacing `android.graphics.Color.parseColor(x)`.
- **`SharedPreferences.edit { }`** (10 occurrences: `ext/Activity.kt`, `util/LocaleHelper.kt` ×2, `search/SearchHistoryStore.kt` ×2, `feature/vip/VipPrefs.kt` ×5) replacing `.edit().put...().apply()`/`.commit()` chains. The one `.commit()` call (`Activity.kt`'s `rateAppInApp`, already `@SuppressLint("ApplySharedPref")`'d in `LINT-001` as intentional/synchronous) now uses `edit(commit = true) { }` — same KTX helper, same synchronous behavior, verified the `androidx.core:core-ktx:1.16.0` resolved by this project has that overload (added in 1.10).
- **`Bitmap.scale()`** (1, `util/BitmapCache.kt`) replacing `Bitmap.createScaledBitmap(bitmap, w, h, true)` — matches the KTX default (`filter = true`).
- **`createBitmap()`** (1, `ext/View.kt`) replacing `Bitmap.createBitmap(w, h, config)`.

Every now-unused `android.net.Uri`/`android.graphics.Bitmap`-adjacent import was removed from the touched files.

## Required test matrix

- [x] Unit tests: `app/src/test/java/com/mckimquyen/ext/ActivityKtUriTest.kt` (new — these `Activity`/`Context` extension functions had zero prior coverage of any kind) — 10 Robolectric tests capturing the real `Intent` each function fires (via `ShadowActivity.nextStartedActivity`) and asserting its exact `action`/`data`, covering `rateApp` (success path + blank-package no-op), `moreApp`, `uninstallApp`, `playYoutube`/`playYoutubeWithId` (+ null-url no-op), `likeFacebookFanpage`, `searchIconPack`, and `launchSystemSetting`. Along the way, found that `launchSystemSetting` (and separately `Context.sendEmail`, `Drawable.toBitmap()`) are unreferenced anywhere in the app — genuinely dead code, out of scope to remove here (that's a different cleanup category), so the `launchSystemSetting` test uses an Activity `Context` (not the bare Application context) to test the function correctly rather than exercising with an unrealistic caller. The already-touched `BitmapCache`/`WebViewSecurity`/`VipPrefs`/`LocaleHelper`/`SearchHistoryStore` functions all have existing dedicated test coverage (`BitmapCacheTest` + 3 more, `WebViewSecurityTest`, `VipPrefsTest`, `LocaleHelperTest`, `SearchHistoryStoreTest`) that already re-ran clean against this diff — no new tests needed there.
- [x] Widget/UI tests: Not applicable as new tests — `FVipManagementWidgetTest` (existing) directly exercises `ActVipManagement.bindUi()`'s color logic (the 11 `toColorInt()` sites) and re-ran clean.
- [x] Integration tests: Not applicable — no persistence/SDK boundary changed; every call site produces byte-identical `Intent`/`Bitmap`/`SharedPreferences` behavior to before.
- [x] Smoke test the exact candidate on a real device and record model, Android version, build SHA, network state, timestamp and log evidence. — See Test evidence below.

## Test evidence

- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures (`ActivityKtUriTest`'s 10 new cases included).
- **Lint** — `./gradlew :app:lintDevDebug`: 87 → 47 warnings (0 errors). `UseKtx` fully gone (40 → 0); no new warnings introduced.
- **Full instrumentation regression**: see the combined regression run recorded in `LINT-006` (this round's smoke covered `LINT-004`, `LINT-005`, and `LINT-006` together in one on-device pass — see that story for the device/result details, since the S24 Ultra dropped off mid-round and the owner authorized a one-off fallback device for all three).

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | Every substitution verified to be a byte-identical wrapper (checked the KTX source signatures, e.g. `scale`'s default `filter=true`, `edit`'s `commit` parameter) before applying. |
| Unit-test quality and coverage | 1.5 | 1.5 | Closes a real zero-prior-coverage gap for 8 `Activity`/`Context` extension functions by asserting the exact fired `Intent`, not just "doesn't crash"; a dead-code finding (`launchSystemSetting`/`sendEmail`/`toBitmap()`) is disclosed rather than silently worked around. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Reused `FVipManagementWidgetTest`, which already covers the highest-risk touched code (11 color-parsing sites), instead of writing a near-duplicate. |
| Integration-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable — no boundary changed. |
| Tecno + general smoke | 1.0 | 0.9 | See `LINT-006` for the shared on-device evidence; docked slightly because the S24 Ultra dropped off mid-round and a one-off fallback device was used instead (owner-approved). |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Zero behavior change; full regression confirms no unrelated breakage. |
| Maintainability and documentation truth | 1.0 | 1.0 | Documents the dead-code finding and the shared-smoke-evidence pointer so neither is rediscovered or misattributed later. |
| **Total** | **10.0** | **9.9** | **Exceeds the > 9.0 push gate.** |
