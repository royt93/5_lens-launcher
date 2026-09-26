# BUILD-001 — Make dependencies and release packaging reproducible

| Field | Value |
|---|---|
| Type | enhance |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Supply chain |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | SEC-002, REL-002 |

## Context and evidence

The ad wrapper comes from JitPack, broad keep rules reduce R8 effectiveness, and `pickFirsts += ['**/*.so']` hides native collisions. Build output also warns about SDK/tooling and KAPT compatibility.

## User story

As a release engineer, I need identical reviewed inputs to produce a traceable artifact.

## Acceptance criteria

- [x] Enable dependency verification/checksums via `gradle/verification-metadata.xml` (sha256 for all build/compile/test dependencies).
- [x] Pin and review advertising/security-sensitive SDK provenance (AdmobApplovinWrapper pinned at 1.1.5, Google Play Review 2.0.2).
- [x] Resolve native library packaging; removed blind wildcard `pickFirsts += ['**/*.so']` without collisions.
- [x] Narrow ProGuard keep rules (removed dead SugarORM `-keep class com.orm.**`, removed broad `-keep class kotlin.**`, `-keep class kotlinx.coroutines.**`, and redundant `-keep class androidx.lifecycle.**`), verified with `minifyDevBenchmarkReleaseWithR8`.

## Required test matrix

- [x] Unit: 531/531 unit tests pass (`./gradlew testDevDebugUnitTest`).
- [x] Widget/UI: Not applicable; no user-facing UI changed in build/packaging rules.
- [x] Integration: 3/3 real-device integration tests pass on TECNO BG6 (`118743744X002560`).
- [x] Smoke: Exact debug candidate built with verification metadata and narrowed packaging installed on TECNO BG6 (`118743744X002560`); `ActHome` launched, fully drawn in logcat (`+7s820ms`), zero native crashes or R8/packaging errors.

## Verification and Definition of Done

- [x] Clean builds twice with dependency verification enabled; deterministic artifacts.
- [x] Full unit/integration test suites pass with R8 compilation verified (`minifyDevBenchmarkReleaseWithR8`).
- [x] Tecno device smoke confirms zero reflection/R8/native loading failure.

