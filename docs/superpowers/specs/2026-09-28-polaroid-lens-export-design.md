# Polaroid lens export — design spec

Status: approved for planning · 2026-09-28

## Context

One of three ideas picked in the 2026-09-27 brainstorming round alongside `FISH-010`
(shipped: `doc/task/done/p2-fish-fish-010-aperture-reveal-dialogs.md`). Original pitch:
"export a 'Polaroid'-style image of a lens layout, for sharing." Never scoped into a
story file until now. This spec narrows it to a concrete, buildable shape the same way
`FISH-010` was narrowed during its own feasibility check.

No screenshot/export/share-image code exists anywhere in this repo today — this
introduces the first `Bitmap.compress()`, the first `FileProvider`, and the first
`Intent.ACTION_SEND` with `EXTRA_STREAM`. Recon backing every claim below: the
codebase-recon subagent transcript run during this brainstorm (paths cited inline).

## Decisions made during brainstorming

1. **Image content**: a snapshot of the currently-rendered `LensView` for one lens
   (real app icons in that lens's current fisheye layout, live distortion applied),
   composited into a white polaroid-style frame, with a caption below the photo area:
   line 1 = the lens's name, line 2 = a small "via Fisheye Launcher" line. Not a full
   screen (`no` search bar/status bar), not a from-scratch re-rendered app list.
2. **Capture technique**: `lensView.draw(Canvas(bitmap))` (software canvas). Already
   proven safe in this codebase by two existing widget tests doing the exact same
   thing (`LensViewDepthOfFieldWidgetTest`, `LensViewIconReloadWidgetTest`). Rejected
   alternatives: `MediaStore`/gallery persistence (this is a share feature, not a save
   feature — no existing `MediaStore` usage to build on, avoid the new surface) and
   `PixelCopy` from the real `Window` (no precedent in this repo, and harder to crop to
   just the `LensView`'s screen rect).
3. **Known, accepted limitation**: because the capture canvas is software-only,
   `LensView`'s Depth-of-Field `RenderEffect` path (`LensView.kt:903`, API 31+,
   hardware-canvas-gated) does not render into the exported image — it silently
   degrades to no blur. Depth of Field is an opt-in secondary feature; this is an
   accepted trade-off, not a defect to fix here.
4. **Entry points (both)**:
   - `ActHome`'s existing lens-management long-press menu
     (`ActHome.showLensManagementMenu` / `onLensMenuItemSelected`, already
     `@VisibleForTesting` and already directly unit-tested via that seam because
     Espresso cannot drive a `PopupMenu` on API 37 — see `ActHomeLensManagementWidgetTest`).
     New menu entry: "Share lens image".
   - A share button in `FrmLens` (the Settings tab that already owns
     `activeLensId`-scoped controls like the distortion slider). `FrmLens` does not
     hold a live rendered `LensView` — it's a settings/controls surface, not the grid.
     Its button therefore does **not** render its own offscreen `LensView`; it
     navigates to `ActHome` (already showing the same active lens, since
     `UtilSettings.KEY_ACTIVE_LENS_ID` is shared state) and triggers the exact same
     export path there. Zero duplicate rendering logic.
5. **Caption content**: lens name + a fixed "via Fisheye Launcher" branding line.
   No app count, no date — keeps the caption formatter a pure function of one input
   (the lens name) with no counting/formatting-of-numbers edge cases to get wrong.
6. **Scope explicitly excluded** (YAGNI): no gallery/`MediaStore` persistence, no
   VIP/ads gating (this idea round was explicitly scoped to avoid the
   `ADS-001`/`VIP-001` chain — see `doc/task/README.md`'s 2026-09-22 decision), no
   per-row share button for every lens in a list (there is no such list in `FrmLens`;
   only the currently active lens is ever exported from there), no user-editable
   caption text, no choice of frame color/style.

## Architecture

New file: `util/PolaroidExportHelper.kt`, following the shape already established by
two sibling helpers in this codebase:

- **Pure half** (Kotlin `object`, `@JvmStatic`, unit-testable with Robolectric — same
  shape as `util/ApertureRevealHelper.kt`):
  - `formatCaption(lensName: String): CaptionLines` — data class with `line1`, `line2`.
    Handles empty name (falls back to a default string resource) and very long names
    (truncates with an ellipsis at a fixed max character count — exact number decided
    in the implementation plan, not user-facing product decision).
  - `sanitizeFileName(lensName: String): String` — strips characters unsafe for a
    filesystem path component (slashes, control characters, emoji outside the basic
    filename-safe set), never empty (falls back to a constant like `"lens"`).
  - `calculatePolaroidLayout(contentWidthPx: Int, contentHeightPx: Int): PolaroidLayout`
    — pure geometry: outer canvas size, the photo content's offset inside the frame,
    caption baseline Y. No Android `Context`/view dependency, just ints in/out.
  - `buildShareIntent(context: Context, imageUri: Uri): Intent` — constructs the
    `ACTION_SEND` intent (`type = "image/png"`, `EXTRA_STREAM = imageUri`,
    `FLAG_GRANT_READ_URI_PERMISSION`), wrapped by the caller in
    `Intent.createChooser(...)` matching `ext/Activity.kt:shareApp()`'s existing
    pattern. Testable by inspecting the returned `Intent`'s fields directly — no
    `espresso-intents` dependency needed (confirmed absent from this repo;
    intentionally not adding it).

- **Side-effecting half** (async, off main thread — same shape as
  `util/LayoutBackupIo.kt`):
  - `exportAsync(lensView: LensView, lensName: String, context: Context, onDone: (Uri?) -> Unit)`
    running on `Dispatchers.IO`:
    1. Reset the view to its idle state before drawing (no live touch/pinch state
       baked into the snapshot) — needs a small production-safe reset entry point;
       `LensView.kt:322`'s existing `internal fun setLensStateForTest(...)` is
       test-only today, so this either gets a narrow non-test sibling method or its
       `internal`/`ForTest` naming gets revisited — a plan-level decision, not a
       product decision.
    2. `lensView.draw(Canvas(bitmap))` into a freshly allocated ARGB_8888 bitmap
       matching the view's current width/height.
    3. Composite onto the polaroid frame using `calculatePolaroidLayout`'s numbers.
    4. `Bitmap.compress(PNG, ...)` to a file under
       `context.cacheDir/polaroid/<sanitizeFileName(lensName)>.png` (cache dir — no
       storage permission needed, matches the fact this app writes no files of its
       own today and holds no storage permissions).
    5. `FileProvider.getUriForFile(context, "<applicationId>.fileprovider", file)`.
    6. Callback on `Dispatchers.Main` with the resulting `Uri` (or `null` on any
       failure — bitmap alloc failure, view not laid out yet, I/O error).

- **New manifest/resource surface** (first use in this repo):
  - `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">` with a `<meta-data>` pointing at `res/xml/file_paths.xml`.
  - `res/xml/file_paths.xml` declaring a `<cache-path name="polaroid" path="polaroid/" />` — scoped narrowly to just this one subdirectory, not the whole cache dir.

## Data flow

`exportAsync` needs a live `LensView`, which only exists inside `ActHome` — `FrmLens`
never calls it directly. Its button instead starts/brings-forward `ActHome` carrying a
one-shot intent extra (e.g. `EXTRA_AUTO_EXPORT_LENS = true`); `ActHome` consumes that
extra once (in `onCreate`/`onNewIntent`, then clears it so a later recreate/rotation
doesn't re-trigger it) and calls the *same* private export path its own long-press menu
item calls — no duplicated logic, just two triggers into one function:

```
ActHome long-press menu item ──────────────┐
                                            ├─→ ActHome's private exportActiveLens()
FrmLens share button                       │        │
  → start ActHome(EXTRA_AUTO_EXPORT_LENS)──┘        │
      (consumed once in onCreate/onNewIntent)        ▼
                              PolaroidExportHelper.exportAsync(lensView, lensName, ctx) {onDone}
                                            │ (Dispatchers.IO: reset→draw→frame→compress→FileProvider)
                                            ▼
                              onDone(uri) on Dispatchers.Main
                                            │
                        uri != null ────────┼──── uri == null
                        ▼                             ▼
          startActivity(createChooser(          Toast: export failed
          buildShareIntent(ctx, uri)))
```

`FrmLens`'s button does no lens-switching itself — `KEY_ACTIVE_LENS_ID` is already
shared state, so `ActHome` opens showing the same lens `FrmLens` was scoped to.

## Error handling

- View not yet laid out (`width/height == 0`) → `onDone(null)`, no crash. Caller shows
  a Toast (new string resource, localized to all 16 locales like every other
  user-facing string in this app).
- Bitmap allocation failure (`OutOfMemoryError` on an unexpectedly huge view) → caught,
  `onDone(null)`, same Toast path. No retry logic — YAGNI.
- File write / `FileProvider` resolution failure → caught, `onDone(null)`, same Toast.
- No installed app can handle `ACTION_SEND image/png` (theoretically possible on a
  stripped-down device) → `Intent.createChooser` itself handles the "no app found"
  case at the OS level; wrapped in the same try/catch pattern
  `ext/Activity.kt:shareApp()` already uses around `startActivity`.

## Testing plan

- **Unit** (`app/src/test/java/com/mckimquyen/util/PolaroidExportHelperTest.kt`,
  Robolectric, mirroring `ApertureRevealHelperTest`'s structure):
  - `formatCaption`: normal name, empty name (falls back to default string), very long
    name (truncated), a Vietnamese-diacritic name (no mangling).
  - `sanitizeFileName`: normal name, name with `/`/control characters, name that is
    only emoji/unsafe characters (falls back to the constant default), empty name.
  - `calculatePolaroidLayout`: at least two different content sizes, asserting the
    exact frame/caption geometry contract (not just "doesn't crash").
  - `buildShareIntent`: asserts `action`, `type`, the `EXTRA_STREAM` URI round-trips,
    and `FLAG_GRANT_READ_URI_PERMISSION` is set.

- **Widget** (androidTest, alongside the relevant existing files rather than a new
  dedicated file — matching this repo's convention of colocating widget coverage with
  its call site, e.g. `ApertureRevealHelperTest`'s widget-layer siblings live in
  `ActHomeLensManagementWidgetTest`/`AppAdapterFolderDialogWidgetTest`, not a dedicated
  "aperture widget" file):
  - Capturing a real, laid-out `LensView` produces a non-null bitmap whose dimensions
    match the view's measured bounds.
  - Simulating an active pinch/touch state (`setLensStateForTest` or its production
    successor) then exporting produces pixel-identical output to exporting from idle —
    proves the reset-before-draw step actually works, not just that it compiles.
  - The new "Share lens image" menu item exists in the long-press popup and, invoked
    directly via `onLensMenuItemSelected` (this repo's established seam for testing
    that specific `PopupMenu`, since Espresso can't drive it), does not crash.
  - The `FrmLens` share button correctly resolves/launches `ActHome` showing the
    active lens.

- **Integration** (androidTest):
  - A real end-to-end `exportAsync` call produces a real PNG file under
    `cacheDir/polaroid/`, and `FileProvider.getUriForFile` resolves it without
    throwing (this is the test that would actually catch a `file_paths.xml`
    path-declaration mismatch — a common real-world bug class for this exact setup).
  - The resulting `content://` URI is readable via
    `context.contentResolver.openInputStream(uri)` — proves the granted URI permission
    actually crosses the process boundary, not just that the `Uri` object was
    constructed.
  - Exporting after switching the active lens (real `ViewPager2` page swipe) captures
    the newly-active lens, not a stale one — integrates `ActHome` + `LensWorkspace` +
    the pager's active-page tracking.

- **Mutation check** (same discipline as `LINT-009`): temporarily remove the
  reset-before-draw step, confirm the relevant widget test fails with a clear
  assertion message; restore, confirm green again. Documented in the story file's
  test evidence, not left as an unverified claim.

- **Manual smoke** (designated device): trigger from both entry points; confirm the
  share sheet opens with the correct image and caption; confirm no ad is visible
  during the flow (this repo's standing screenshot-test rule); confirm a mid-pinch
  export still shows the idle (non-distorted, no HUD) layout.

## Out of scope for this story (explicitly deferred, not silently dropped)

- Gallery/`MediaStore` persistence of the exported image.
- Capturing Depth-of-Field blur in the export (would need `PixelCopy` or a
  hardware-backed off-screen render target — a separate, larger story if ever
  requested).
- Any VIP/ads interaction.
- User-customizable caption text or frame styling.
