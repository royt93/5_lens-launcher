# Release 2026.11.23, one page (written 2026-11-23)

Short version of `doc/task/evidence/release-2026.11.23-prep-kj7.txt` (168 lines, 17 sections; kept as is, it is the source) and of `RELEASE_NOTES_2026.11.23_DRAFT.md`. If a number here differs from those, they win. Everything below was measured on ONE phone, a TECNO KJ7 (Android 14, 1080x2436, display cutout), by `adb` and by device tests, not by a person.

## The commit to build from

`dev` at `69657aa6` or later: later commits only add documents (the last code change is the one-line home list fix in that commit). `versionCode 20261123`, `versionName 2026.11.23` (was 20261007 / 2026.10.07; 514 commits and 187 files in `app/src/main` behind).

## What I checked, in one table

| Check | Result |
|---|---|
| Unit tests plus lint on the host, forced | 1827 tests, 0 failures; lint 0 errors, 16 baseline warnings |
| Device tests on the KJ7, debug build of `69657aa6` | `ui` 591 OK, `views` 327 OK, `model` 12 OK |
| `productionRelease` (R8 and shrink on, 16 KB aligned, not debuggable) | builds, installs, starts in 0.2-1.3 s; logs by our own pid: 0 crash, 0 ANR, 0 R8 signatures, 0 Room errors |
| Room 12 to 13 | 5 migration tests; a dense database (12 lenses, 3000 rows) kept field by field; a mutant that deletes rows is caught |
| Upgrade 2026.10.07 to 2026.11.23 over data | kept: pinned apps, a favourite in a folder, a hidden app, sort type, background, vibration, language; Room ran 12 to 13 |
| Release smoke by adb | first run, lens drag, search, split-screen with the keyboard up, landscape, opening an app, 3 lenses with paging, lens menu, pull-down search |

## Defects found and fixed after the work started (each with a test and a device run)

1. A lens page that came back from the pager cache with 3 lenses lost pull-down search, edge swipe, the lens menu and the drop strip (introduced 2026-10-22, AFTER the shipped 2026.10.07).
2. Choosing a language in the first-run sheet right after pressing Home could throw `IllegalStateException`.
3. The Settings "reset" preview printed a whole sentence for an empty value and raw enum names or English-only words for sort type, background and vibration.
4. The default `values` strings for the share title and message were Vietnamese; the Vietnamese terms text kept an English clause; the three screenshot tests failed on this phone because of its display cutout (a test bug).
5. Search on a short window or split-screen: the keyboard came back after a focus change; fixed earlier, now tested.
6. The home list kept its adapter after the Activity was destroyed, so a row's Set-folder dialog was never dismissed by the adapter's own cleanup; `ActHome.onDestroy` now drops the adapter (one line, device test red before and green after). I could not make the system destroy the Activity with the dialog open, so the test checks the adapter, not the leaked window.

## Open, read but not reproduced or not measured (none of these changed my advice)

- Rotating the phone while the unlock credential screen is open can lose the result on Android 7-10 only; the KJ7 is Android 14.
- Icon pack scan on the main thread: 0.7-2 ms with NO icon pack installed; unmeasured with real packs.
- ICU pinyin for apps with Chinese names; Smart Focus order recorded on "freeze" uses the settings screen's size.

## Not covered anywhere

A build signed with your real key; an upgrade from the build actually on Play; a person using it; Android 7-10; foldables and tablets; long sessions. All translations are AI text, none reviewed by a native speaker. Audit score 8.5/10, below the 9.0 gate in the working agreement; pushing below it is your decision.

## Only you can do

1. Build the real bundle with your key (`ANDROID_RELEASE_*` or `keystore.properties`) from the commit above. Every release build I made used a throwaway key that is deleted; none is uploadable.
2. Check `versionCode 20261123` against the last code in the Play Console; I do not know it. Raise it if the console has a higher one.
3. Play Console items: Data safety against the merged manifest, privacy policy URL, permission explanations (location, contacts, delete-packages), store text saying this is a replacement launcher.
4. Upload to the INTERNAL track first and use it on your phone for a day.
5. Decide, as you did before: the rewarded ad still uses Google's TEST id, the consent flow (ADS-001) is not done, the release key (SEC-001) is not rotated.

## Things worth knowing so you are not surprised

- The Terms and Privacy dialog appears again after every `versionCode` change (the flag includes the version code); it looks designed, I did not ask.
- A tap on a lens-grid icon opens the app (a touch held 10 ms or more); only a touch shorter than one frame does nothing, which `adb input tap` produces and a finger does not. I did not test with a finger.
- Night-mode names are English in all other locales and always were; translating them needs the selection code changed first.
