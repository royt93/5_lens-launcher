# FEAT-006 — Export/import launcher layout

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Home-screen usability |
| Estimate | 5 SP |
| Risk | Low |
| Dependencies | DB-001 (already shipped) |

## Context and evidence

`model/AppPersistent`/`AppPersistentDao` (Room) holds everything that defines
a user's layout: favorites, folders, pinned zones, custom order, visibility,
lock. (Correction from this story's original framing:
`model/AppOrganizationRules` is only a folder-name validation helper, not a
separate data holder — `AppPersistent` alone is the real source.) None of it
survives an uninstall/reinstall or moves to a new device. No
`ADS-001`/`INSIGHT-001`/`VIP-001` dependency: pure data-layer serialization.

## User story

As a user, I want to export my current app organization (favorites, folders,
pinned zones, order, hidden/locked state) to a file and restore it later —
after a reinstall, on a new device, or just as a backup before experimenting.

## Acceptance criteria

- [x] Export: `model/LayoutBackup.kt` serializes `AppPersistent` rows to a
      versioned JSON file (`schemaVersion` field, `CURRENT_SCHEMA_VERSION = 1`
      from day one). Deliberately scoped to exactly what the story names —
      favorites/folders/pinned zones/order/hidden/locked — excluding
      `openCount` (usage stat) and `paletteColor` (per-device theming), which
      a "restore my layout" feature shouldn't silently also touch.
- [x] Export target uses SAF (`ActivityResultContracts.CreateDocument`,
      `application/json`) — no raw filesystem path. Live-verified.
- [x] Import: `ActivityResultContracts.OpenDocument`, matched by
      `AppPersistent.generateIdentifier` (packageName + component name, the
      same identity `iconCacheKey` already uses) via `LayoutImportApplier.plan`
      — apps not currently installed are skipped, not erroring the import.
- [x] Import shows a preview (matched/skipped counts) via
      `MaterialAlertDialogBuilder` before committing — a full dedicated
      preview screen was judged unnecessary (YAGNI) for what's fundamentally
      two numbers and a confirm/cancel choice; upgrade to a itemized list
      screen if real usage shows the count alone isn't enough context.
- [x] Malformed/future-schema file: `LayoutBackup.parse` returns a typed
      `LayoutBackupParseResult` (`Malformed` / `UnsupportedSchemaVersion` /
      `Success`) — never throws, never partially applies. One malformed entry
      inside an otherwise valid file is skipped individually, not fatal to
      the whole import.

## Implementation notes

- `LayoutImportApplier.apply()` reuses `AppPersistent`'s existing single-app
  persistence statics (`setAppVisibility`/`setAppOpened`/`setOrganization`/
  `setAppOrderBatch`) in a loop — the exact codepath a user flipping each
  switch by hand would trigger, including the `RAppsSingleton` sync +
  `AppEventManager` notify each already does. No new bulk-write query, no
  duplicated side-effect wiring.
- `util/LayoutBackupIo.kt` exists specifically because `ActSettings` is Java
  with no ergonomic way to call Kotlin `suspend` functions — it exposes
  plain `@JvmStatic` callback-based entry points that self-manage their own
  coroutine threading, matching the pattern `AppPersistent`'s own companion
  functions already established for Java call sites.
- `org.json` (already part of the Android SDK) used for serialization — no
  new dependency (Gson/Moshi/kotlinx.serialization) added for this.
- Fixed a `PluralsCandidate` lint regression this story's own new string
  introduced (`%1$d apps` phrasing) by rephrasing to put the noun before the
  number in all 17 locale strings, rather than building real `<plurals>`
  resources for a two-number confirmation message.

## Required test matrix

- [x] Unit (`LayoutBackupTest.kt`, 9 tests): full round-trip (entry and
      whole-backup), null-folder handling, `fromPersistent` skipping
      blank-identity rows, malformed JSON, future schema version rejection,
      one-bad-entry-skipped-not-fatal, missing top-level keys, missing
      optional fields defaulting safely. (`LayoutImportApplierTest.kt`, 5
      tests): identity matching, skip-not-installed, same-package-different-
      component not conflated, empty backup, empty installed list. All pass.
- [x] Widget (`FrmSettingsMaterialYouWidgetTest` +1, `FrmSettingsLayoutBackupWidgetTest`,
      new file): rows exist in the raw layout; click listeners actually wired
      on a real attached Fragment (`hasOnClickListeners()`). The launched SAF
      Intent itself isn't asserted (no espresso-intents dependency in this
      project, not worth adding for one test) — proven live instead, below.
- [x] Integration (`LayoutBackupIoIntegrationTest.kt`, 2 tests, real device):
      real `file://` Uri + real `ContentResolver` write/read round trip
      preserving every field; `LayoutImportApplier.apply()` actually
      persisting through Room (polled via the real DAO, not asserted from
      memory).
- [x] Smoke: full real-device UI flow, see below.

## Verification and Definition of Done

- [x] **Full export→reinstall→import cycle verified live** on the designated
      device with a non-trivial, real 67-app inventory (not a synthetic
      empty state) — see Smoke.
- [x] Exported file contains no data beyond what this app already stores
      locally: `packageName`, `name` (component identity, already visible in
      the UI), `orderNumber`, `appVisible`, `appOpened`, `isFavorite`,
      `folderName`, `pinnedZone`. No PII, no device identifiers, no usage
      history.

## Smoke (TECNO KJ7, serial `115333744A005844`, Android 14, 2026-09-24 09:45-09:54 local)

Real UI, real SAF picker (`com.google.android.documentsui`), real file on
disk — not simulated:

1. Opened Settings → Cài đặt tab, scrolled to the new "Xuất bố cục"/"Nhập bố
   cục" card, confirmed correct icons (`ic_share_24dp`/`ic_restore_24dp`) and
   Vietnamese labels.
2. Tapped "Xuất bố cục" → real Android file picker opened with the suggested
   filename `lens_launcher_layout.json` pre-filled → tapped "LƯU" (Save) →
   real toast "🎭 Đã xuất bố cục" confirmed success.
3. Tapped "Nhập bố cục" → real file picker opened, the just-saved file
   visible (18.74 kB) → selected it → **real preview dialog** rendered:
   "Nhập bố cục? Ứng dụng được cập nhật: 67. Ứng dụng bị bỏ qua (chưa cài
   đặt): 0." (this device's real installed-app count) → tapped OK → toast
   "🎭 Đã nhập bố cục" confirmed.
4. **Uninstalled the app entirely** (`adb uninstall`), reinstalled fresh
   (`installDevDebug`) — a genuinely empty Room database.
5. Repeated the language-picker/consent first-run flow, navigated back to
   Settings → Cài đặt → "Nhập bố cục" → the file from step 2 was still
   present in Downloads (SAF-exported files live outside app storage,
   confirmed by design) → selected it → **preview dialog again showed
   "Ứng dụng được cập nhật: 67. Ứng dụng bị bỏ qua: 0"** against the fresh
   post-reinstall app list → OK → "Đã nhập bố cục" toast, no crash
   (`logcat` checked clean of `FATAL`/`AndroidRuntime` for this app both
   times).
6. One coordinate-mapping mistake was made and caught mid-session (tapped
   using un-scaled screenshot-preview coordinates instead of real device
   pixels), which toggled an unrelated switch ("Hiện lối tắt cài đặt") off —
   caught via the next screenshot, toggled back on immediately, and the
   scale factor was corrected for the rest of the session using
   `uiautomator dump`'s real reported bounds instead of screenshot-preview
   coordinates going forward.

## Loop end condition / self-audit

| Dimension | Weight | Notes |
|---|---:|---|
| Correctness and acceptance criteria | 2.0 | All AC met and live-verified through the actual hard case (uninstall/reinstall), not just a happy-path click-through. |
| Unit-test quality and coverage | 1.5 | 14 tests across two files, every parse/plan branch covered including adversarial cases (malformed entry inside a valid file, future schema version). |
| Widget/UI-test quality and coverage | 1.0 | Click-wiring proven on a real attached Fragment; the one thing not asserted (launched Intent shape) has a documented, deliberate reason (no espresso-intents dependency) and is covered by the real-device Smoke instead. |
| Integration-test quality and coverage | 1.5 | Real file + real ContentResolver + real Room round trip, not mocks — the two boundaries this story's own AC named as needing more than unit tests. |
| Smoke results | 1.0 | The single most convincing evidence layer this story could produce: a genuine uninstall/reinstall against a real 67-app device inventory, both preview-dialog counts matching exactly, no crash. One real mid-session mistake disclosed and corrected rather than hidden. |
| Security/privacy/Play readiness | 1.0 | Confirmed the exported file's field set contains nothing beyond what's already locally stored and user-visible. |
| Performance/lifecycle/regression risk | 1.0 | No hot path touched; reuses existing single-app persistence codepaths rather than new bulk queries; full 451/451 unit regression, lint held at the existing 9-warning baseline (caught and fixed a self-introduced `PluralsCandidate` regression in the same round). |
| Maintainability and documentation truth | 1.0 | Corrected this story's own initial context claim about `AppOrganizationRules` once the real code was read, rather than leaving a wrong architectural claim in the record; every YAGNI/reuse decision (no preview screen, no new JSON library, Java-callback bridge pattern) is justified at the point it's made. |

**Self-audited 9.6/10.** Not higher: the widget-test layer's one gap (SAF
Intent shape not directly asserted in an automated test) is a considered,
disclosed trade-off rather than a defect, but it's still a gap relative to
a hypothetical espresso-intents-equipped suite — real-device Smoke closes it
in practice, not in the automated suite. Zero failed code-controlled checks,
zero regressions, zero secrets touched, no PII in the export format.
**Push qualifies** (`> 9.0/10`).
