# Verification

The implementation was built locally with JDK 17, Gradle 8.13, Android SDK 35
and Android Gradle Plugin 8.11.1.

| Check | Result |
|---|---|
| Debug APK assembly | Passed |
| JVM unit tests | 28 passed |
| Android emulator tests (API 35, x86_64) | 8 passed |
| Android lint | No errors; dependency/target-version and tooling warnings remain |
| APK signature verification | Passed, APK Signature Scheme v2 |
| APK installation and launch | Passed on Android 15 emulator |
| Public feeds | Retrieved actual fund NAVs and indicative stock snapshots in the installed app |

Emulator tests exercise Room transactions and persistence, first-value baselines,
changed/unchanged values, error retention, timestamp regressions, duplicate
additions, duplicate instruments across lists, provider switching, per-instrument
thresholds and Compose screen navigation. They exposed a name-resolution deadlock
in the add-instrument path, which was corrected and covered by a regression test.

The public-feed parsers are tested against captured real excerpts, separate from
production data. Live checks were made against the public sources documented in
[FREE_FEEDS.md](FREE_FEEDS.md); their future availability is not guaranteed.

No physical device or prolonged battery/Doze scenario was tested. WorkManager
execution timing depends on Android. Follow the README’s background-monitoring
acceptance checklist on the intended phone. Live exchange entitlements and a
custom production gateway are not supplied; the built-in stock feed is explicitly
indicative and the free market status remains Unknown.

The delivered APK checksum is in `artifacts/SHA256SUMS.txt`. Local test reports
are under `app/build/reports/`. GitHub Actions rebuilds source and publishes its
own debug APK artifact after a successful pushed run; that APK uses a different
debug signing key from the locally delivered build.
