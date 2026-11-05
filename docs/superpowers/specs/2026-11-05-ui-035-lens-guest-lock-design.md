# UI-035 — Khóa lens (chế độ khách) — Design

Date: 2026-11-05
Story: `doc/task/todo/p3-ui-ui-035-lens-guest-lock.md`
Device policy: TECNO KJ7 `115333744A005844` only. No Pixel/Samsung/OPPO. No push.

## Goal

Owner can lock individual lenses before handing the phone to another person. A locked lens cannot be reached
from Home navigation, management UI, shortcuts or restored state until the owner unlocks it with Android's
system biometric/device credential prompt.

This is an in-app privacy guard. It is not a security boundary against ADB, root, recents/OS capture, backups or
someone who already controls the device.

## Decisions

- Unlock method uses only system-owned authentication, no secret storage. API 30+: `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`. API 25–29: retain the same choice through separate system biometric and `KeyguardManager.createConfirmDeviceCredentialIntent` paths; do not pass the combined authenticator flags on API 28–29 because AndroidX Biometric does not support that combination there. Check biometric availability with `BiometricManager`; check credential availability with `KeyguardManager.isDeviceSecure()`. If neither is available, locking is refused. On API 25–29 credential confirmation is an OS activity and may not be cancellable once launched; document and test result handling.
- Locked lens selection: do not expose its app grid; show a localized locked placeholder/message. Unlock only through an explicit owner action.
- Scope: every entry that can select/display a lens — pager drag, edge swipe, indicator/tab, lens management selection,
  lens menu, shortcut/Intent `EXTRA_TARGET_LENS_ID`, saved active-lens restore and Home resume.
- A ViewPager callback alone is too late to guarantee no frame of private content: `LensPagerAdapter.onBindViewHolder`/the `LensViewHolder` bind path must not pass app data to a locked page. If a drag temporarily reveals that page, it shows only locked placeholder until the pager returns to the last allowed page. Any restore/startup path resolves an unlocked target before binding app data.
- Unlock persistence: authenticated unlock clears the persistent lock flag. The owner may lock again from the lens menu.
- Active lens locked at startup/resume: switch to the first unlocked lens before rendering the lens grid.
- Last unlocked lens cannot be locked or deleted while any other lens remains locked; both actions are disabled/refused with a short explanation. If deleting a lens would leave only locked lenses, deletion is refused.
- Control location: the existing lens row overflow menu in `LensManagementActivity`; no second settings entry.
- Storage: use a dedicated private preferences file `lens_access_prefs` with boolean key `lens_locked_<lensId>` (including the literal `default` id); do not store it in `PreferenceManager.getDefaultSharedPreferences()` because that entire XML file is backed up. The flag contains no PIN, biometric, secret or token.
- Lock state is device-local: exclude `lens_access_prefs.xml` from Android Auto Backup and device transfer in both `backup_rules.xml` and `data_extraction_rules.xml`, and exclude it from workspace/layout/profile export/import. Importing a layout neither adds nor removes lock flags; deleting a lens removes its flag; duplicating a lens always creates it unlocked.

## Non-goals

- No replacement for the device lock screen, app lock, task-switcher hiding, ADB/root hardening or screenshot blocking.
- No password/PIN storage or custom credential UI.
- No timed relock, grace period, auto-lock, remote lock or per-app locking changes.
- No change to existing `isLensFrozen` meaning (freeze app positions).
- No Ads/IAP/TalkBack scope, no CI gates, no `store-assets` work.

## Architecture

### Pure policy

New `LensAccessPolicy` (Kotlin, JVM-testable) owns every rule:

- `isLocked(lensId)` from a stored locked-id set.
- `canLock(lensId, lenses, lockedIds)`: false for unknown lens, already locked lens, or when locking would leave no unlocked lens.
- `firstUnlocked(lenses, lockedIds)`: first by current lens ordering.
- `resolveTarget(requestedId, lenses, lockedIds)`: allowed, blocked-with-message, or fallback target.
- `resolveActive(savedActiveId, lenses, lockedIds)`: saved active if valid/unlocked, otherwise first unlocked.

No Android context, no UI, no auth in the policy.

### Persistence

Create `LensLockStore` backed by `context.getSharedPreferences("lens_access_prefs", MODE_PRIVATE)`; keep this store separate from general `UtilSettings` so the lock XML can be excluded from OS backup without excluding all launcher preferences.

- `isLocked(lensId)`, `saveLocked(lensId, locked)`, `lockedLensIds(validLensIds)`, and `delete(lensId)`.
- Wrong-type or malformed individual flags are treated as unlocked; stale IDs are ignored by `lockedLensIds`.
- Lens creation/duplication never copies a lock flag. Lens deletion clears it. Store is never consulted by workspace/profile export/import.
- Startup and lock mutations use `LensAccessPolicy` to guarantee an unlocked destination; if state corruption leaves all existing lenses locked, fail closed by showing no app grid and require an owner unlock path rather than silently unlocking a lens.

### Authentication

`LensUnlockAuthenticator` wraps system authentication behind an injectable launcher and returns `SUCCESS`, `CANCELLED`, `UNAVAILABLE`,
`ERROR`. Only `SUCCESS` clears the flag. API 30+ uses one `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`; do not call
`setNegativeButtonText` in this mode. API 25–29 offers the supported strong-biometric prompt (with a negative action to fall back) and,
when needed, `KeyguardManager.createConfirmDeviceCredentialIntent`; the credential screen is system-owned. Check `isDeviceSecure()`
before offering credential auth. The prompt strings are localized and do not claim the in-app lock is device security.

### Navigation guard

`ActHome` owns the guard and passes it to `LensViewHolder`/menu collaborators:

- Keep `lastAllowedLensId` in Activity memory; never persist a locked lens as `KEY_ACTIVE_LENS_ID`.
- Before applying a target Intent or restored active id, validate/fallback to an unlocked workspace.
- `LensViewHolder.bindLensView` must know the lock set before setting apps. A locked page receives no app list (empty list/placeholder state); this prevents ViewPager2's adjacent-page pre-bind from exposing icons before `onPageSelected` can reject navigation.
- User swipes can begin before `onPageSelected`; if ViewPager2 temporarily displays a locked page, it remains content-free and is returned to `lastAllowedLensId`. Disable/fence a locked indicator tab where possible; `onPageSelected` remains final defense.
- Edge-swipe, indicator/tab, menu switch, management selection and shortcut/Intent call the same `resolveTarget` policy before changing active lens.
- `applyTargetLensExtra`/`handleTargetLensIntent` resolve an Intent/shortcut target through the same policy. A locked target does not
  write `KEY_ACTIVE_LENS_ID` and never binds apps for that lens.
- `refreshLensList`, startup and `onResume` use `resolveActive` before the saved page is applied.
- `LensManagementActivity.selectLens` refuses a locked row; owner unlocks it from the row menu. Management list can still show lens names and lock state; it never displays the locked grid content.

### Menu

`menu_lens_row.xml` gains one entry. Label depends on lens state:

- Unlocked: **Lock lens**. Writes the flag immediately, no authentication required, then Home falls back if that lens is active.
- Locked: **Unlock lens**. Opens system auth, then clears the flag on success.
- Disabled when the lens is the last unlocked lens, with a message: “Keep at least one lens unlocked.”
- Disabled/refused when no system biometric/device credential is configured.

Rows show a lock state in the content description/subtitle and may use the existing lock icon tinted from theme tokens.

### Strings and docs

New strings are added for every supported locale (`values` + 16 locale folders). Explanations must say it is an in-app privacy guard
and does not replace the device lock, ADB/root protection or screen security.

## Data flow

1. Owner chooses Lock on lens row → check `LensAccessPolicy.canLock` and credential availability → write flag → notify list/Home.
2. Home guard receives the changed lock set → if active lens is locked, select first unlocked lens and update `KEY_ACTIVE_LENS_ID`/pipeline.
3. Guest taps/swipes/uses shortcut to a locked lens → policy returns blocked → page/target unchanged → message.
4. Owner chooses Unlock on a locked row → system prompt → success clears flag; cancel/error leaves lock.

## Error handling and failure modes

- Missing or deleted lens: ignore flag; stale locked ids cannot block unlocked lenses.
- Process death: flag is persistent; startup applies fallback before binding/restoring the locked page.
- Recreate/rotate: active selection comes from `resolveActive`; no locked page should briefly bind as active.
- Prompt cancel/error: no change, no data loss.
- Race between lock and navigation: final guard check occurs at page-selected/set-active boundary.
- Multiple lenses with all but one locked: last unlocked is protected from locking and deleting.
- Backup/import cannot alter device-local lock flags; deletion/duplication cannot create an all-locked reachable workspace.

## Security notes to ship in the audit and UI copy

Known bypass/limitation areas: Recents screenshot/thumbnail, ADB/root/backups, notifications or apps launched elsewhere,
another launcher, device owner/user with unlock credentials. The feature only prevents in-app lens navigation and Home display
for locked lenses.

## Testing

### Unit (RED before code)

- `LensAccessPolicyTest`: lock refusal for last unlocked, unknown/stale ids, all resolve cases, first-unlocked order, fallback active, no mutation.
- `UtilSettingsLensLockTest`: default/legacy key, per-lens suffix, wrong-type read, delete cleanup, duplicate does not copy.
- Authenticator unit seam test: success/cancel/unavailable/error outcomes and only success clears flag.
- Backup/import regression: locked flags never create all-locked state and do not touch auth.

### Widget / UI

- Lens row menu shows Lock/Unlock correctly, disabled for last unlocked and no-credential state.
- Locked row cannot select/open; unlock prompt seam success/cancel/error.
- Home pager/edge-swipe/menu/indicator cannot switch to locked lens.
- Active lens locked at resume/recreate falls back before binding grid.
- Shortcut/Intent to locked lens does not write active id and shows blocked message.
- Light/dark/RTL/font-scale smoke for the changed row/menu state where practical.

### Integration

- Real Room + prefs with multiple lenses: lock/unlock persists across recreate/process-like reload.
- Delete locked lens, duplicate lens and import/export preserve invariants.
- Dedicated bypass test list: pager, edge-swipe, selection from management, shortcut extra, saved active id, recreation.

### Device smoke (TECNO only)

Backup/hash before testing. Verify lock one lens, attempt every in-app entry, active lens fallback, unlock with owner-provided
system prompt, relaunch/process kill, restore backup. If an ad covers UI, stop and wait for owner `done`.

## Delivery gate

Audit file `doc/task/AUDIT_UI-035_<date>.md` must score `> 9.0/10`; list real bypass limits; run
`./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, `git diff --check`,
`scripts/check-secrets.sh`; update `doc/feature.md`, `doc/task/README.md`, story status after each decision. Push only if the
current owner request authorizes it and score > 9.
