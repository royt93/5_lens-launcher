Lens Launcher is a unique, efficient way to browse and launch your apps.

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
