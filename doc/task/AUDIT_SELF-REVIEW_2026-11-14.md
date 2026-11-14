# Self-review of the main source (2026-11-14)

Requested by the owner ("bạn tự audit và tìm các vấn đề cho tôi"; no usage data was available). Static, read-only scan of `app/src/main` (156 Kotlin and 17 Java files) plus the manifest and the backup rules. Nothing was run on a device and no code was changed.

## What was checked and what came out

| Check | Result |
|---|---|
| Kotlin force-unwrap `!!` | 0 occurrences |
| `lateinit var` | 13 in 3 files; each is first assigned inside `onCreate` |
| `postDelayed` without a matching `removeCallbacks` in the file | 4 files; all four act on a view or are guarded by `isFinishing`/`isDestroyed`, no leak found |
| `registerReceiver` in `RApplication` | `localeReceiver`, `packageReceiver`, `profileReceiver`: each has its `unregisterReceiver` (matched by name) |
| Other register without unregister (substring count) | 5 flagged by count only; read each: the page-change callbacks and the tilt listener are paired in other lifecycle methods, and `QuickActionEngine` calls `registerReceiver(null, ...)` for the sticky battery value, which registers nothing |
| Exported components | `ActHome`, `ActSettings`, `ActFakeLauncher` (launcher entry points) and `BadgeCountReceiver` |
| Backup | `vip_screen_prefs`, `app_search_history`, `app_preferences`, `lens_access_prefs` excluded in both rule files, as documented |

## Findings

1. **R5 rule not followed for `lateinit` (confirmed, low).** The owner's rule requires a comment explaining why a `lateinit` is certainly non-null. 11 of 13 have none: `LensManagementActivity` 6, `SuperWebViewActivity` 4, `ActVipManagement` 1. All 13 are assigned in `onCreate`, so the risk is a missing comment, not a crash.
2. **`LanguageBottomSheetDialogFragment` runs `requireContext()` 200 ms after a tap (suspected, NOT verified).** The click handler posts `onItemClick` with `postDelayed(..., 200)`, and `onItemClick` calls `requireContext()` and `dismiss()`. If the fragment is detached inside that window (for example a rotation right after the tap) `requireContext()` could throw. Whether the posted runnable still runs after detach was not tested, and it only affects the language picker.
3. **`BadgeCountReceiver` accepts a badge count from any installed app (accepted tradeoff, not a bug).** It is `exported=true` without a permission. The source documents this: the broadcast follows the ShortcutBadger convention, carries no sender authentication, and the impact is a cosmetic count for an installed package, clamped to `MAX_STORED_NOTIFICATION_COUNT`.

## Not found / not checked

No `!!`, no unpaired `registerReceiver` in `RApplication`, no cleartext traffic (`usesCleartextTraffic=false`), no `debuggable` flag in the manifest. Not checked: runtime behaviour, threading, database migrations, ProGuard/R8 rules, dependency vulnerabilities, accessibility (declined by the owner), and anything that needs real usage data.
