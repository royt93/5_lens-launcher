# Task 1 report — Schema-v2 model and strict parser

## Implementation
- Added `LayoutBackupLens` and v2 serialization/parsing while retaining v1 parsing behavior.
- V2 parser rejects missing or wrong-type `selectedAppIds`/`entries`, invalid scope, bad nested entries, duplicate IDs/order, missing default lens, and invalid pinned zones. It preserves selected IDs for apps not installed.
- V1 remains lenient for malformed entries and keeps old root `entries` format.

## Tests and review
- Focused `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LayoutBackupTest` passed after all cases; initial run of the new missing-field tests failed as expected before parser fix. Wrong-type regression tests passed after addition.
- Full `./gradlew testDevDebugUnitTest`: 788/788 passed before the parser review additions.
- Independent final review: spec compliance ✅, code quality ✅. Review confirmed both missing and wrong-type array fields reject; v1 behavior unchanged.
- `git diff --check` passed.

## Commits
- `19b6a6f feat(backup): model multi-lens schema v2`
- `ec03aff fix(backup): reject missing v2 lens fields`
- `ff54c98 test(backup): reject wrong-type v2 lens arrays`
- `f00b40b docs(backup): record parser review follow-up`

No device, release, ads, signing, or unrelated changes.
