# SEARCH-004 — Permission-gated quick toggles/info (flashlight, wifi name)

| Field | Value |
|---|---|
| Type | `new` |
| Status | `todo` |
| Priority | `P3` |
| Evidence | `idea` |
| Epic | Search expansion (owner-picked 2026-09-12) |
| Estimate | 5 |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Two owner-picked ideas both require a **new runtime permission** the app does not currently
declare (grep of `AndroidManifest.xml` during investigation found no `CAMERA` or
`ACCESS_WIFI_STATE` entries) — kept as its own story, lower priority, so the zero-permission
SEARCH-002/003 stories aren't blocked on a permission/Play-Console review cycle:

1. **Flashlight toggle from search** — typing "flashlight"/"đèn pin" toggles the torch via
   `CameraManager.setTorchMode`, which needs `android.permission.CAMERA` on the API levels this
   app targets.
2. **Wifi name quick info** — typing "wifi" shows the connected SSID, which needs
   `android.permission.ACCESS_WIFI_STATE`.

## User story

As a user, I want to toggle the flashlight or peek my wifi network name straight from search,
accepting a one-time permission prompt for each.

## Acceptance criteria

- [ ] Both permissions requested at first use of the respective quick action (not at app launch),
      with a clear rationale string, per Play policy for runtime permissions.
- [ ] Denial degrades gracefully — the quick action silently doesn't appear (falls through to
      normal search / the settings-shortcut deep link from SEARCH-002 as fallback), never crashes.
- [ ] `AndroidManifest.xml` declares both permissions with an accurate Play Console data-safety
      form update (flagged to the owner — publisher-side action, same pattern as `SEC-001`).

## Implementation notes

Torch toggle and wifi-name lookup are independent one-shot calls; implement as two more entries in
the `QuickActionEngine` from SEARCH-002 rather than a new engine, gated by a runtime permission
check before each is offered.

## Verification and Definition of Done

- [ ] Unit tests: permission-granted vs denied path for each action (mockable via existing test
      patterns for permission checks in this codebase).
- [ ] Widget/UI: permission prompt appears once, action works after grant, action absent after
      deny.
- [ ] Integration: real `CameraManager`/`WifiManager` call on a connected test device.
- [ ] Smoke on designated device: toggle flashlight, read real SSID.
- [ ] No new lint/build failures; owner sign-off on the Play Console permission declaration before
      this moves past `inprogress`.
