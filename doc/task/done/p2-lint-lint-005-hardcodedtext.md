# LINT-005 — Extract hardcoded UI text into translatable string resources

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Localization |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Owner-picked as one of three parallel follow-ups (with `LINT-004`/`LINT-006`) once the real backlog was confirmed still exhausted. 16 `HardcodedText` warnings across `act_about.xml` (11), `act_settings.xml` (2), `act_vip_management.xml` (1), `frm_settings.xml` (1), `layout_ad_banner.xml` (1) — literal `android:text` values that can never be translated for the app's 16 non-English locales.

This project treats missing translations as a **build-blocking lint error** (`MissingTranslation`, discovered when the first pass of new strings — added to the base `values/strings.xml` only — failed the build with 11 errors), not a warning to leave for later. So every new real string key was translated into all 16 locale files (`ar`, `de`, `es`, `fr`, `hi`, `in`, `it`, `ja`, `km`, `ko`, `lo`, `pt`, `ru`, `th`, `vi`, `zh`) in the same pass, not left as an English-only fallback.

## Changes

- **`act_about.xml`** (11 → 8 new string keys, one shared 3×): `about_features_header`, `about_expand_indicator` (the "▼" expand glyph, used identically on all 3 cards), `about_feature_equispaced_grid`, `about_feature_fisheye_lens`, `about_feature_full_settings`, `about_section_header`, `about_credits_header`, `about_made_with_love`, `about_app_tagline`.
  - `about_expand_indicator` marked `translatable="false"` — it's a single directional glyph ("▼"), not language-specific text.
  - `about_app_tagline` ("Fisheye Lens Launcher") marked `translatable="false"` — matches this project's existing convention of never translating the product name itself (verified: `values-vi/strings.xml` and `values-zh/strings.xml` both keep "Fisheye Launcher" untranslated inside otherwise-translated body text in the existing `about` string).
  - The other 7 were translated into all 16 locales.
- **`act_settings.xml`**: the toolbar VIP badge's `tvVipBadgeStatus` ("VIP") and `act_vip_management.xml`'s `tvStatusBadge` ("LENS MEMBER") were changed from `android:text` to `tools:text` instead of extracting a string resource — traced both through the code (`ActSettings.bindToolbarVipBadge()`, `ActVipManagement.bindUi()`) and confirmed each is unconditionally overwritten with a real, already-existing, already-translated string (`vip_badge_active`/`vip_badge_get`, `vip_badge_premium_member`/`vip_badge_free_member`) before the view becomes visible in every code path — the XML value is a pure design-time/layout-preview placeholder, never seen by a real user. This matches the existing pattern already used elsewhere in this codebase (e.g. `act_about.xml`'s `tvVersion`/`tvAbout`).
  - `act_settings.xml`'s other warning — "Please note: this action may show ads" — is genuinely always-displayed static content (no `android:id`, never touched by code) and was extracted to a new `ads_action_disclosure` key, translated into all 16 locales.
- **`frm_settings.xml`**: "VIP Premium" reused the already-existing, already-translated `vip_premium_title` key (identical text) instead of creating a duplicate.
- **`layout_ad_banner.xml`**: "Ad" extracted to a new `ad_label` key, translated into all 16 locales using each language's standard, already-established ad-disclosure wording (matching common Play/AdMob localization conventions, e.g. "Quảng cáo" for Vietnamese, "広告" for Japanese).

## Required test matrix

- [x] Unit tests: Not applicable — every change is a resource extraction or a `tools:text` placeholder fix with no new logic; `xml.etree.ElementTree.parse` verified all 16 touched locale files stayed well-formed after the scripted translation insert.
- [x] Widget/UI tests: Not applicable as new tests — `ActAboutWidgetTest` (existing, from `LEAK-001`) already asserts `act_about.xml` inflates and its cards expand correctly, and `FVipManagementWidgetTest`/`ActSettingsLayoutTest` (existing) already cover the two `tools:text` screens; all re-ran clean, which is the direct proof that removing `android:text` from the two placeholder `TextView`s didn't leave them visibly blank (both tests assert real bound content, not the XML default).
- [x] Integration tests: Not applicable — no persistence/SDK boundary touched.
- [x] Smoke test the exact candidate on a real device and record model, Android version, build SHA, network state, timestamp and log evidence. — See `LINT-006` for the combined on-device evidence covering this round (`LINT-004`/`LINT-005`/`LINT-006` were smoke-tested together in one pass).

## Test evidence

- **Compile + resource link** — `./gradlew :app:processDevDebugResources`: clean after every batch, including the 16-locale translation insert.
- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures.
- **Lint** — `./gradlew :app:lintDevDebug`: first pass (English-only strings) **failed the build** with 11 `MissingTranslation` errors — caught before this was mistakenly treated as done. After translating into all 16 locales: 47 → 31 warnings, **0 errors**. `HardcodedText` fully gone (16 → 0).
- See `LINT-006` for this round's shared full-suite instrumentation regression evidence and device details.

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | All 16 warnings resolved; the two placeholder cases were traced through actual code before choosing `tools:text` over a wasted string resource, and the brand-name/glyph exemptions match an existing, verified project convention rather than being invented. |
| Unit-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable; XML well-formedness was mechanically verified for every one of the 16 touched locale files. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Reused existing tests that already assert real bound content on both `tools:text` screens, which is the correct proof that removing the hardcoded default didn't leave a blank label. |
| Integration-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable. |
| Tecno + general smoke | 1.0 | 0.85 | See `LINT-006`'s shared evidence; docked slightly for the same one-off fallback-device reason recorded there. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Pure resource/text changes; the `MissingTranslation`-as-error catch before completion is itself evidence this was verified, not assumed. |
| Maintainability and documentation truth | 1.0 | 1.0 | Documents exactly why two strings were exempted from translation and why two placeholders became `tools:text` instead of new resources, so neither choice looks like an oversight later. |
| **Total** | **10.0** | **9.85** | **Exceeds the > 9.0 push gate.** |
