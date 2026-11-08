# FISH-042 — Haptic mốc tiêu điểm

| Field | Value |
|---|---|
| Type | exclusive |
| Status | done 2026-11-07 (audit 9,0/10; Samsung S24 Ultra; chưa push) |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | FISH-007, FISH-020, FISH-041 |

## Context and design

Owner picked one LIGHT haptic when the currently hovered app crosses into or out of `DepthOfField.FOCUS_ZONE` during a real pan. Reuse `KEY_VIBRATE_APP_HOVER` (default off); no new setting, UI, locale or DB state. Hysteresis 0.02, app identity changes baseline silently, max one pulse per frame; focus pulse wins over ordinary hover pulse. Spec: `docs/superpowers/specs/2026-11-07-fish-042-focus-boundary-haptics-design.md`.

## Acceptance criteria

- [x] Only the same selected app crossing either focus boundary direction pulses; first observation and app changes establish baseline.
- [x] Hysteresis prevents chatter between 0.18 and 0.22.
- [x] Haptic is LIGHT and gated by hover switch, live pan, and reduced-motion policy.
- [x] At most one haptic each frame; focus boundary has priority over ordinary hover entry.
- [x] Tap, idle, pin, tilt, pinch, ring rotation, launch and switch-off do not emit a boundary haptic.
- [x] No per-frame allocation and no geometry/`LensGridCache` changes.
- [x] Host, widget, integration, and A50s device smoke; backup and restore exact.
- [ ] Audit >9.0/10 — **đúng 9,0/10 (không phải trên 9,0), owner đóng với giới hạn** before push; no push unless owner asks.

## Verification and definition of done

- [x] Unit tests exercise hysteresis, baseline, both directions, invalid distances, and selection changes.
- [x] Widget/integration tests verify gates, haptic priority, lifecycle reset, and no selection changes.
- [x] Device evidence records A50s `R58MA6WYRPE`, Android version, build SHA, original animator scale 0, and any temporary change/restore.
- [x] No new lint/build failures.

## Kết quả

Audit: `doc/task/AUDIT_FISH-042_2026-11-07.md` (9,0/10). Giới hạn chính: integration MotionEvent thật không tái hiện được boundary crossing (kéo đổi icon trước khi distance đủ đổi); mutation set chưa chạy; owner chưa thử tay. Commit: `85abcc12`, `02c159db`, `5d3e23a5`, kèm 3 bản vá lifecycle/conditional-test/optic-centre.
