# Publishing 2026.11.23: a careful rollout plan (written 2026-11-23)

I cannot see your Play Console, so everything about the console below is general practice, not a reading of your settings. Everything about the app comes from `RELEASE_2026.11.23_ONE_PAGE.md` and the evidence file. This is advice for you to adopt or ignore; I did not publish anything and I do not have the signing key.

## Why a staged rollout and not a straight release

This build is 514 commits and 187 `app/src/main` files after the version that is on Play, and it was checked on ONE phone (Android 14, display cutout). Nothing has been checked on Android 7 to 10, on a foldable or tablet, with your real signing key, or by a person. That is the case for letting a small share of real devices find what I could not.

## Before you build

1. Open the Play Console and read the highest `versionCode` ever uploaded. This build uses `20261123`. If the console has a number equal or higher, raise `versionCode` in `app/build.gradle` first (a higher number only; `versionName` is free text).
2. Build from commit `d31c6842` or later on `dev` (it carries the fixes found after `69657aa6`: the Apps-tab row text overlap, the cancelled-gesture launch, the timer quick-action crash and the same guard on the contact and Do Not Disturb rows, and from the 2026-11-24 audit the language sheet after recreate, locked lenses in the share picker, hostile extras on exported entry points, SecurityException on app launch, the four Settings menu web items, the import snapshot refresh, the lens rename, installed apps staying visible on a v2 import, the bounded layout import read, the review error cast, the permission-asked flags after a restore and the feedback message + its 3 unit tests; the release build of `d31c6842` was smoke-tested by adb on A50s, TECNO KJ7 and Samsung S24U, and its debug suite passed on KJ7 and S24U with 0 new failures; S2 and R5 were covered by unit + debug device tests, not exercised on the release build; later commits should add documents only). Sign with YOUR keystore through `ANDROID_RELEASE_*` or an untracked `keystore.properties`. The build fails on purpose if neither is present; do not work around that.
3. Prefer an Android App Bundle (`bundleProductionRelease`) if your Play app uses Play App Signing; the 4 native ABIs and 16 KB alignment were checked on an APK.

## Rollout, in this order

1. **Internal testing track** with your own account on your own phone. Use it for a day: open it as the default launcher, make a second and a third lens, swipe between them, use search with the keyboard and with the number pad, rotate, put it in split screen.
2. **Closed or open track** if you have testers, at least a few days, ideally with one phone on Android 7 to 10 (the one case I could not test, see below).
3. **Production at a small percentage** (for example 5 to 10 %), then raise it in steps (for example 20, 50, 100 %) only after the checks below stay clean for at least a day at each step.

## What to watch, and when to stop

In the Play Console under Android vitals and the pre-launch report:

- Crash rate and ANR rate for the new version against the old one. Stop and halt the rollout if the new version is clearly worse, or if one stack trace dominates.
- Look first at: `IllegalStateException`, `ClassNotFoundException` / `NoSuchMethodError` (an R8 sign), `SQLiteException` or `Migration didn't properly handle` (the Room 12 to 13 step), `WindowLeaked`, and anything in `LensView`, `LensPagerAdapter`, `LanguageBottomSheetDialogFragment`.
- The pre-launch report runs your build on real devices; read the screenshots for the first run (the Terms dialog and the language sheet) and for a clipped or blank home screen.
- Reviews and support mail in the first two days: a launcher that fails to show apps or to open them is reported fast.

Stop rules, as a suggestion: halt the rollout on any report of "no apps shown", "cannot open apps", a crash loop on start, or lost settings, then investigate before resuming.

## If it goes wrong

- Halting the rollout stops new installs of the new version. Users who already have it keep it until you ship another version.
- Play does not let you re-upload an older `versionCode`. To go back, you publish the old code as a NEW build with a HIGHER `versionCode` (a hotfix), signed with your key. Keep the 2026.10.07 source (`git show e0c30979`) and a way to build it.
- A user who has the new build cannot be moved back to the old one by the app: Room goes from 12 to 13 with a migration but there is no downgrade path. Do not plan on downgrading data.

## Known, accepted or open (so a report does not surprise you)

- Rotating the phone while the device-credential screen is open when unlocking a lens can drop that unlock attempt on Android 7 to 10 only; the user taps unlock again. Accepted, not fixed (see the one-page file).
- The Terms and Privacy dialog shows again after every `versionCode` change. It looks designed; confirm it is wanted.
- Real ad ids are used; the rewarded id is Google's test id and there is no consent flow (your earlier decisions).
- All translations are AI text. A native speaker of any language may find phrasing to fix.
- Night-mode names are English in every locale (they always were).
- A lens-grid icon opens its app when the finger lifts after about 10 ms or more; an `adb` tap can be too short to open it. A finger should not hit that.

## What I did not and cannot do

I did not read or change anything in the Play Console, did not use your signing key, did not test a Play-signed artifact, did not test an upgrade from the build actually on Play (only from a rebuild of the 2026.10.07 commit), and did not test on Android 7 to 10. The audit score stays 8.5/10, below the 9.0 gate in the working agreement; releasing below it is your decision.
