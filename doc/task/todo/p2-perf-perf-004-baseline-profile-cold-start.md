# PERF-004 — Add a Baseline Profile for cold-start

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Startup performance |
| Estimate | 5 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

No `baselineProfile` Gradle module exists (`androidx.benchmark.macro.junit4` /
`androidx.profileinstaller` are not in `app/build.gradle`'s dependencies), and
no `.pro`/`baseline-prof.txt` profile is shipped. `PERF-001`/`PERF-002`/`PERF-003`
already removed the hot-path *allocation* waste (render loop, icon cache,
`DiffUtil`); a Baseline Profile is a different, complementary lever — it ships
AOT-compilation hints for the launcher's actual cold-start path (the very
first frames after tap-to-open, which for a launcher is effectively always,
since it's the home screen), letting ART skip JIT warm-up for that code on
first run instead of only after repeated use.

## User story

As a user, I want the launcher to render its first frame as fast as possible
every time it starts, not just after ART has JIT-warmed it from repeated use.

## Acceptance criteria

- [ ] Add a `:baselineprofile` Gradle module using the Macrobenchmark library,
      targeting the real cold-start path: process start → `ActHome` → first
      `LensView` frame with icons visible.
- [ ] Generate `src/main/baselineProfiles/baseline-prof.txt` via
      `./gradlew :baselineprofile:generateBaselineProfile` on a real device (not emulator — profile generation needs a rooted/userdebug image or a real device in a supported state).
- [ ] Wire the generated profile into `app/build.gradle` (`profileinstaller` dependency + AGP baseline profile source set) so it ships in the release APK/AAB.
- [ ] Add a Macrobenchmark `StartupTimingMetric` test that fails the build (or is at least run in the audit round) if cold-start regresses beyond a documented threshold.

## Required test matrix

- [ ] Unit: N/A — this is a build/profiling concern, not app logic; reviewed as not applicable.
- [ ] Widget/UI: N/A — no UI behavior changes.
- [ ] Integration: the Macrobenchmark `StartupTimingMetric` test itself is the integration-layer proof (real process start measurement).
- [ ] Smoke: before/after cold-start timing comparison on the designated device, `adb shell am start -W` or the Macrobenchmark report, with the profile installed vs. not.

## Verification and Definition of Done

- [ ] Documented before/after cold-start numbers on the designated device (Macrobenchmark report or `am start -W` `TotalTime`).
- [ ] Baseline profile ships in the release build; `profileinstaller` confirmed via `adb shell dumpsys package <applicationId>` showing profile status.
- [ ] No regression to existing `PERF-001`/`PERF-002`/`PERF-003` behavior.
