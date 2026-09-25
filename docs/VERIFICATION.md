# Verification · EGX Watch 1.4.0 / build 5

Executed locally on 2026-09-25 with JDK 17, Gradle 8.13, SDK/build-tools 35.

- Every planned implementation stage reached a successful debug build and unit-test gate.
- Final `assembleDebug testDebugUnitTest lintDebug`: successful.
- **69 JVM unit tests, zero failures/errors.**
- **20 Android instrumentation tests, zero failures**, on isolated EGXWatchApi35
  emulator, Android 15/API 35. No live API dependency in the test suite.
- Lint: **0 errors, 23 warnings**. Remaining warnings concern newer toolchain/library
  versions, target API, KAPT versus KSP, and two optional KTX-style suggestions.
- `git diff --check`: no whitespace errors.
- APK signer and manifest verified: package `app.egxwatch`, min API 26, target 35,
  versionName `1.4.0`, versionCode `5`.
- The actual packaged APK successfully upgraded the locally signed 1.3.0 APK on the
  emulator using `adb install -r`. The connected physical phone was not modified.

## Covered behavior

Existing tests cover provider timeout/rate-limit/failover, source identity/validation,
market-calendar weekends/holidays/exception windows, malformed/stale data, persisted
health, cached values, notifications and opportunity thresholds/dedup/cooldown.

New JVM suites cover independent maturity gates, missing history, chart-range access,
adaptive calculations/no premature long indicators, bad discontinuities, independent
gold scheduling, gold JSON/timestamps, threshold crossing/baseline/cooldown/dedup,
lookback insufficiency, and archive authenticated round-trip/wrong password/tampering.

Room integration covers forward-only collection, duplicate and out-of-order rejection,
NAV revision without sample inflation, sampled OHLC/slot aggregation, stale exclusion,
compaction retaining aggregates, reset, gold cooldown/dedup/read/dismiss after reopening
the database, and bounded chart queries over 10,000 observations. The migration test
validates v1→v5 schema while preserving user selections/latest values. Existing engine
restart test now asserts that the historical API is never called.

Compose smoke test exercises navigation, instrument search/validation/addition, alerts,
settings and monitoring status. Fresh-install onboarding and gold layout were inspected
visually on the emulator. A separate manual network smoke check retrieved an actual
Gold-API XAU/USD observation with provider/receipt timestamps; it was not a test fixture
or an automated-test dependency. No authorized live EGX connection is available to
verify production EGX coverage. Screenshots remain in ignored `artifacts/screenshots`.

## Signed artifact

`artifacts/EGX-Watch-v1.4.0-build5-2026-09-25-debug.apk`

SHA-256:
`38441381200f786edf84ebbd559d79f77515d67b244806f734ea319a3cb49d27`

Signing certificate SHA-256 (same as the prior delivered APK):
`a26df6b9dfb6a6fbf53a9bc0b56309b2cd17e7bbe1512209936c68ecf82d825f`

This is a debug/development artifact. Preserve the existing private debug keystore for
local upgrades. Locally it is under ignored `.tools/android-user/debug.keystore` and
builds must set `ANDROID_USER_HOME` to that directory. Other machines/CI use different
debug keys unless deliberately configured. Never commit signing material.

## Remaining acceptance work

Real-device Doze/force-stop/overnight tests, full Arabic translation/RTL visual audit,
TalkBack and large-font review, authenticated gateway pilot, authoritative calendar,
long-running storage/battery profiling and archive restore remain next milestones.
No profitable-outcome claim or calibrated prediction validation is made.
