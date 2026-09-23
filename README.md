Lens Launcher is a unique, efficient way to browse and launch your apps.

## Docs

- Architecture, build/test/lint commands, working agreements: `CLAUDE.md`.
- Live backlog and status (current test counts, done/in-progress/todo stories): `doc/task/README.md`.
- Ad/consent flow: `doc/AD_PROMPT_AOS.MD`, `doc/AD.MD`.
- Store screenshot tooling (separate Next.js tool, out of app scope): `store-assets/`.

Older docs under `doc/` (`FIX_SUMMARY.md`, `memory_leak.md`, `UNIT_TEST_SUMMARY.md`,
`TESTS_README.md`, `CODE_REVIEW_RISKS.md`, `MIGRATION_GUIDE.md`,
`app/src/test/README.md`) are dated historical snapshots, each marked superseded
at the top — `doc/task/README.md` is the current source of truth.

## Release signing

Release signing material must stay outside version control. Configure it with
the following environment variables in CI or on a release workstation:

- `ANDROID_RELEASE_STORE_FILE`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

For local development, copy `keystore.properties.example` to
`keystore.properties` and fill it with local values. The local file and common
keystore formats are ignored by Git. A release Gradle invocation fails early
when any required value is missing; debug builds do not require signing values.
