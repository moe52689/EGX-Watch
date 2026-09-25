# Verification · EGX Watch 1.3.0 / build 4

Verified 25 September 2026 with JDK 17, Gradle 8.13, Android SDK 35 and an
Android 15/API 35 x86_64 emulator. Each development stage compiled and passed its
unit-test gate before the next stage. The final suite has **55 JVM tests** and
**15 emulator/instrumentation tests**, using mocks rather than live market APIs.

Coverage includes:

- Session windows/weekends, Cairo timezone, holidays, exception windows and next session.
- Missing, stale, malformed, mismatched and discontinuous data; NAV timestamp semantics.
- Ordered failover, offline errors, timeout, HTTP 429 Retry-After and provider cooldown
  retained across repository recreation; sanitized provider health messages.
- Snapshot fingerprint normalization, rich quote/analysis/history serialization,
  SMA/EMA/RSI/MACD/ATR/drawdown reference values and reproducible scoring.
- Opportunity score/confidence thresholds, material change, state re-entry, duplicate
  suppression, cooldown, quiet hours and daily limit decisions.
- Room v1 → v2 → v3 migration retaining watchlist, value, timestamp and settings.
- Room-backed analysis/event/alert-state persistence after database close/reopen,
  repeated snapshot suppression and cached historical data; no off-session polling.
- Existing price/NAV baselines, provider changes, late in-flight results, concurrent
  settings changes, per-security thresholds and saved-value retention.
- Compose fresh-start watchlist, identity search/addition, disabled duplicate addition,
  History, Settings, analytics/calendar navigation and Status navigation.
- Production provider selection cannot re-enable undocumented public feeds.

Debug assembly, JVM tests, instrumentation and Android lint are the build gate.
Lint has zero errors; dependency/target-version, KAPT and KTX style warnings remain.
No dependency upgrade was bundled into this feature change. Final APK signature is
verified and its certificate matches the prior local debug build:
`a26df6b9dfb6a6fbf53a9bc0b56309b2cd17e7bbe1512209936c68ecf82d825f`.

APK package `app.egxwatch`, versionName `1.3.0`, versionCode `4`, minimum API 26.
The APK installs and launches on the emulator; the empty initial dark-theme UI was
visually inspected. The checksum is in `artifacts/SHA256SUMS.txt`.

No real authorized EGX gateway was supplied or tested. There is no claim of live
exchange connectivity, full market coverage, predictive accuracy or financial returns.
No physical-device, extended Doze/battery, production authentication, penetration or
load test has been performed. Android notification permission/channel checks are
implemented; notification delivery timing still depends on the OS.

Reproduce using the README build/test commands. Test reports are local under
`app/build/reports/tests/`, `app/build/reports/androidTests/` and the lint report.
The instrumentation UI fixture clears the test application's database; run it on a
dedicated emulator, not a personal watchlist installation.
