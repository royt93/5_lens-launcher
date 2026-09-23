# SEARCH-007 — Focus/DND quick toggle

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Search expansion |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | SEARCH-002 (already shipped) |

## Context and evidence

`search/QuickActionEngine.kt` already resolves typed queries (`wifi`, timer,
battery %, unit conversion, Settings deep-links) in priority order via
`SEARCH-002`/`SEARCH-004`. Do Not Disturb has no entry today. Toggling DND
requires `NotificationManager.isNotificationPolicyAccessGranted()` /
`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` (a special-access grant, not a
runtime permission) — same one-time-grant-then-remember shape `SEARCH-004`
already established for CAMERA/location.

## User story

As a user, I want to type something like "dnd" or "focus" in search and
toggle Do Not Disturb without leaving the launcher.

## Acceptance criteria

- [ ] New `QuickActionEngine` entry, triggered by keywords (`dnd`, `focus`,
      `do not disturb`, plus the Vietnamese equivalents already used elsewhere
      in this file, e.g. `lien he`/`goi` style transliteration for `SEARCH-005`).
- [ ] First use requests `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`; a
      decline is remembered and never re-prompted uninvited (same pattern as
      `SEARCH-004`'s CAMERA/location handling) — a visible re-prompt affordance
      still exists if the user wants to grant it later.
- [ ] Toggling reflects real system DND state immediately (query
      `NotificationManager.getCurrentInterruptionFilter()` before rendering the
      row, not a locally cached guess).
- [ ] Denied-access state shows a clear "why" (not just missing/silent).

## Required test matrix

- [ ] Unit: `QuickActionEngine` resolution priority/keyword matching for the
      new entry, denied-vs-granted branch logic.
- [ ] Widget/UI: row renders correct current-state icon/label; tapping it
      when access is missing routes to the settings screen instead of
      silently failing.
- [ ] Integration: real `NotificationManager` policy-access grant/deny flow
      on-device (cannot be faked in Robolectric — matches `SEARCH-004`'s own
      test-layer split).
- [ ] Smoke: on the designated device, grant flow, toggle both directions,
      decline-then-remember flow, all confirmed live.

## Verification and Definition of Done

- [ ] Live-verified grant + toggle + decline-remembered on the designated
      device.
- [ ] Play Console Data Safety form reviewed for whether this new special
      access needs disclosure (mirrors `SEARCH-004`'s own still-open Data
      Safety item — don't assume no, check).
