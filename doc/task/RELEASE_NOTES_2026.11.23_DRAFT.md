# Release 2026.11.23 (versionCode 20261123): draft notes and owner checklist

Draft written 2026-11-23 from `dev` bf702b2a. Everything under "Checked" was measured and is in `doc/task/evidence/release-2026.11.23-prep-kj7.txt` and `doc/task/evidence/brainstorm-2026-11-14/SUMMARY_split-screen-keyboard-hook.md`. Everything under "Not checked" was not. This is NOT a sign-off: the audit stays 8.5/10, below the 9.0 gate in the working agreement.

## What changed since 2026.10.07 (514 commits, 124 feat, 70 fix; the user-visible ones, grouped)

- **Search on short windows:** on any window under 480 dp high (phone held sideways, split-screen) the in-app T9 number pad replaces the on-screen keyboard even with the T9 switch off, and a note under the switch says so (17 locales). The window height is now measured from the real window, so split-screen picks it up; the on-screen keyboard is dismissed when the pad takes over and when the window regains focus.
- **Lens effects, all opt-in and off by default:** vignette, focus peaking, chromatic aberration, lens inertia, focus snap, hover magnify, neighbour labels, recent-open dots, boundary haptic, tilt parallax.
- **Lenses:** several lenses, a lock per lens (device unlock), duplicate with automatic aperture, optical profile import/export, focus-ring shortcuts, focus lock by double tap, polaroid share image and contact sheet.
- **Accessibility actions** for focus lock and ring rotation.
- **Fixes:** Vietnamese terms text and the default share message and chooser title no longer mix languages (the default `values` file used to hold Vietnamese for `share_via` and `share_app_message`, shown to every unsupported language); Italian "Focus peaking" title translated.
- Store-listing text is the owner's to write; the lines above are a source, not final copy. Translations are AI, no native-speaker review.

## How the lens opens an app (so a reviewer or a new user does not think it is broken)

Measured on the release build on the KJ7 (see the evidence file, section "OPEN AN APP FROM THE LENS GRID"): touch the grid and slide to magnify; **lift the finger on an icon to open that app**; **hold on an icon for about half a second to get its menu**. A short tap on an icon does NOT open the app. Search (the bar at the top) opens an app with a normal tap on a result. If the store listing or a reviewer tests with plain taps on the grid, they will see nothing open; the listing text should say "touch, slide and lift". The app has no first-run hint for this today (I found no onboarding string or class); adding one is a product change with 17 locales and a test, and is not in this release.

## Checked (host and TECNO KJ7 only)

- Host: unit 1819 tests, 0 failures; lint 0 errors / 16 baseline warnings (forced runs).
- KJ7 (Android 14, 1080x2436, display cutout): whole `com.mckimquyen.ui` package 587/587 on three debug builds of the later HEADs (the last on 05c92e9a), plus `com.mckimquyen.model` 12/12; Room 12->13 migration test 5/5; `productionRelease` (R8, 16 KB aligned, not debuggable) installs and runs; deep smoke by adb on the release build: first run, lens drag, search, real split-screen with the keyboard up, landscape, 0 crash/ANR/Room errors; upgrade from a 2026.10.07 build over its data (same throwaway key) keeps the language and starts without a Room error.
- The A50s and the KJ7 were restored to their baselines after every run.

## Not checked, or not provable from here

- **The Play-signed artifact.** Every release build above used a throwaway key. The upload needs your real keystore through `ANDROID_RELEASE_*` or `keystore.properties`.
- **versionCode.** `20261123` is date-based and above `20261007`; I do not know the last code you uploaded. If it is higher, raise it first.
- **Upgrade from the build actually on Play** (not a build of the 2026.10.07 commit) and from a long-used database. My old data was a few minutes of use.
- Long sessions, launching apps from the lens, ad flows, icon packs, folders, lock/biometric, layout import/backup, VIP on the release build. One swipe, one query, one split, one rotation were tried.
- A person using it: all device checks were adb taps and screenshots I read.
- Translations: all AI. Two Indonesian/Italian-style gaps were reviewed by me only.

## You decided to defer (not touched)

- Release key rotation (SEC-001): the old keystore is in git history (commits a8701ba, ac7e944, 67e7f81).
- Ad consent state machine (ADS-001): the SDK is initialised before consent; matters for EEA/UK.
- Rewarded ad id: the release still uses Google's TEST id `ca-app-pub-3940256099942544/5224354917` (BUG-6).
- Keeping the three sensitive permissions (location for the Wi-Fi name, contacts, delete-packages).

## Only you can do (from `RELEASE_OWNER_CHECKLIST.md`)

1. Build the real bundle/APK from a commit at or after bf702b2a with your key; check the versionCode against the Play Console.
2. Play Console: Data safety form against the merged `productionRelease` manifest; privacy policy URL reachable; permission explanations for `REQUEST_DELETE_PACKAGES`, location, contacts; store text says this is a replacement launcher.
3. Upload to an internal or closed track first and run it on a phone before production.
4. Decide whether to publish with the rewarded test id and without the consent flow.

## Small things I noticed and left for you

- The Terms and Privacy dialog shows again after every versionCode change (the flag is `KEY_READ_POLICY${VERSION_CODE}`). It looks designed; confirm it is wanted.
- Indonesian `organization_folder_label` reads "Folder: %1$s"; the same locale already uses the word "folder" in its neighbours ("Atur folder", "Nama folder"), so I left it.
- Many strings are identical to English in several locales on purpose (brand and loanwords: Github, WiFi, Timer, Feedback, Apps in de).
