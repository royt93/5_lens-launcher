# Play Console checklist for 2026.11.23 (generated 2026-11-23 from the merged release manifest)

What this is: the permissions, queries and ad facts of the PRODUCTION RELEASE build, taken from the manifest merger report of `productionRelease` (`app/build/outputs/logs/manifest-merger-production-release-report.txt`, built from `dev` at `06636b8a`, app code identical to `69657aa6`). It is a list for YOU to compare with the forms in the Play Console. I cannot see your console and I did not fill in anything. What an advertising SDK collects is NOT in the manifest: take that from AdMob's and AppLovin's own documentation for the SDK versions below (play-services-ads-api 24.5.0, applovin-sdk 13.1.0, wrapper AdmobApplovinWrapper 1.1.5); I did not read those documents.

## 1. Permissions in the merged release manifest, and who adds each

| Permission | Added by | Used by app code? | Needs a line in Data safety / permission explanation |
|---|---|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | the app | yes: the current Wi-Fi name for a quick action, requested at use | yes: location, "not sent, not stored" per the earlier checklist (I checked code: no network call from app code) |
| `READ_CONTACTS` | the app | yes: search for "call/message/contact X", read on demand, not cached | yes: contacts, not sent off the device |
| `CAMERA` | the app | yes: flashlight only; `uses-feature` camera and flash are `required=false` | permission explanation (torch) |
| `REQUEST_DELETE_PACKAGES` | the app | yes: uninstall from the app menu | permission explanation (Play restricts this permission; be ready to justify it) |
| `VIBRATE` | the app | yes: haptics | no |
| `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`, `INTERNET` | the app and the ad SDKs | network state check and the ads | the ad SDKs' network use; app code itself makes no network call |
| `com.google.android.gms.permission.AD_ID` | the app and ad libraries | by the ad SDKs | yes: advertising ID, used for advertising (AdMob, AppLovin) |
| `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS` | `play-services-ads-api 24.5.0` | by the ad SDK | yes: Android Privacy Sandbox ad services, advertising |
| `RECEIVE_BOOT_COMPLETED` | `play-services-ads-api 24.5.0` | by the ad SDK | probably, if the form asks about boot receivers; I did not check what it does |
| `WAKE_LOCK` | `androidx.work 2.7.0`, `play-services-measurement-sdk-api 20.1.2` | by libraries | normally no line needed |
| `FOREGROUND_SERVICE` | `androidx.work 2.7.0` | by WorkManager only; the app's own manifest declares 0 services. The merged manifest has 6 services, all from libraries (AdService, SystemAlarmService, SystemJobService, SystemForegroundService, MultiInstanceInvalidationService, FullscreenAdService) and NONE carries `foregroundServiceType` | Play asks for a foreground service type only if a foreground service is declared with one; none is, so I would expect no question, but I cannot see your form |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` | `androidx.biometric 1.2.0-alpha05` | yes: unlocking a locked lens | permission explanation (device unlock of a lens) |
| `com.applovin.array.apphub.permission.BIND_APPHUB_SERVICE` | `applovin-sdk 13.1.0` | by the ad SDK | listed in the manifest; no form line expected |
| `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` | `installreferrer 2.2` | by the ad SDK | listed in the manifest; no form line expected |
| `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core 1.16.0` | internal | no |
| **`POST_NOTIFICATIONS`** | **the app itself** (`AndroidManifest.xml:27`, comment "to show notifications") | **no: app code never posts a notification** (it reads Do Not Disturb state through `NotificationManager`, which needs no permission) | see section 2 |

## 2. One permission that does nothing: POST_NOTIFICATIONS

The app declares `POST_NOTIFICATIONS`, but no code creates a channel or posts a notification and the permission is never requested at runtime. It is harmless on its own, yet it shows on the listing and invites a reviewer's question. Removing it is a one-line change in `app/src/main/AndroidManifest.xml`. I did NOT remove it: it changes app code under `app/src/main`, so the whole device test run (`ui`, `views`, `model`, about 35 minutes) would have to be repeated, and the build you upload must come from the commit that carries the change. Your call: leave it (and be ready to say "declared, not used") or remove it and rebuild.

## 3. `<queries>`: what the OS is told the app may see

Merged (app plus libraries): intents `MAIN`, `VIEW` (https), `DIAL` (tel), `INSERT` (calendar events, mime type `vnd.android.cursor.dir/event`), `sms` scheme, `CustomTabsService`, `com.applovin.am.intent.action.APPHUB_SERVICE`, `com.google.zxing.client.android.SCAN`. There is NO `QUERY_ALL_PACKAGES`. The launcher lists apps through `queries` with `MAIN`/`LAUNCHER`, which is the allowed route for a launcher. The `sms`, `tel`, calendar and `CustomTabs` entries come from the ad SDK libraries, not from code you wrote; check the Data safety form's "app activity > installed apps" question with that in mind.

## 3b. Exported components in the merged release manifest (read from the file, not from memory)

4 are `exported="true"`: `com.mckimquyen.services.BadgeCountReceiver` (the app's own, deliberately open: it clamps the number and only writes a cosmetic count for an already installed package), `androidx.work.impl.background.systemjob.SystemJobService` (guarded by `BIND_JOB_SERVICE`), and `androidx.work.impl.diagnostics.DiagnosticsReceiver` and `androidx.profileinstaller.ProfileInstallReceiver` (both guarded by the `DUMP` permission). Everything else is `exported="false"`: 6 receivers of the app, FileProvider, the ad SDK init providers, `AdService`, `FullscreenAdService`, Room's service. Not counted here: the activities (`ActHome`, `ActSettings`, `ActFakeLauncher` is disabled by default); their export flags were read earlier in `AndroidManifest.xml`.

## 4. Things the console asks that no earlier document covered

- **"Contains ads" declaration (App content > Ads): yes.** The app shows AdMob and AppLovin ads. Not ticking it for an ad-supported app is a policy violation.
- **Content rating questionnaire:** answer it for an ad-supported app; the app has no user-generated content and no chat.
- **Privacy policy URL:** the app ships TWO different URLs. `app/build.gradle` `PRIVACY_POLICY_URL` = `https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f` (VIP screen); `util/C.kt` = `https://loitp.notion.site/loitp/Privacy-Policy-319b1cd8783942fa8923d2a3c9bce60f/` (Settings menu and the Terms dialog). Open BOTH in a browser that is NOT logged in to Notion. Put in the console the one that is public and that names advertising ID, location (Wi-Fi name), contacts and installed apps. If one is not public, change the code so both use the good one (that is an app change: rebuild and retest).
- **Target audience:** a launcher with ads is not a children's app; choose an adult or general audience that matches your ad SDK settings.
- **Default launcher / HOME:** the store text says it is a replacement home screen (already in the store listing draft).
- **App access:** nothing is behind a login.

## 5. The ad test-device question (not a form item, but check it before you publish)

`RApplication.setupAdmob` registers developer test devices on EVERY build type, and also adds the current device's own hashed `ANDROID_ID` and advertising ID to the AdMob test-device list. The same code is in the 2026.10.07 you already shipped. I could NOT establish whether AdMob therefore serves test ads to every installer. The check that settles it is cheap: open AdMob and look at impressions and revenue of the live 2026.10.07. If revenue is normal, this is not a problem. If it has been zero all along, it was zero before this release too, and the cause is this code.

## 6. Order I suggest

1. AdMob: impressions and revenue of the live version (section 5).
2. Browser, logged out: both policy URLs (section 4); decide which goes in the console.
3. Data safety form with the table in section 1 next to it; do not fill it from `app/src/main/AndroidManifest.xml` alone (libraries add 10 permissions).
4. Ads declaration, content rating, target audience.
5. Decide on POST_NOTIFICATIONS (section 2). If you remove it, tell me and I will redo the device runs.
6. Then build from `d31c6842` or later with your key and use the internal track first (`PUBLISH_PLAN_2026.11.23.md`).
