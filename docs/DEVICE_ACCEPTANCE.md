# v1.4.1 build 6 acceptance · 2026-09-27

## Decision

**NO-GO for operational live EGX monitoring.** No permitted, entitled EGX gateway is
configured. A compiled adapter, passing mocked tests and saved prices are not live
EGX acceptance. Gold is a separate provider snapshot series, not exchange execution.

| Check | Result |
|---|---|
| EGX stocks / ETF exchange prices | BLOCKED: authorized gateway/entitlement absent |
| Egyptian fund / ETF NAV | BLOCKED: no verified permitted source configured |
| Gold-API host probe | HTTP 200, 4286.200195 USD/troy oz; provider time 2026-09-27T07:46:01Z, fetched 07:46:27Z |
| Gold-API Android emulator manual refresh | PASS: accepted 4286.200195 USD/troy oz at provider timestamp 2026-09-27T07:58:31Z; diagnostics and first forward chart observation verified |
| EGX emulator manual refresh without gateway | PASS: SETUP REQUIRED message, empty new-install watchlist |
| Second independent live gold source | BLOCKED: permission and authenticated response not verified; mocked failover passes |
| Authoritative EGX holidays / exceptions | BLOCKED: no verified calendar feed; configurable Cairo calendar supported, status UNKNOWN |
| Unit tests | 77 passed, 0 failed |
| Android integration / UI / migrations | 24 passed, 0 failed, API 35 emulator |
| Lint | Completed, 0 errors, 23 maintenance warnings (dependency versions, target/API, KAPT/KTX) |
| Physical phone update | PASS: adb install -r, app.egxwatch versionCode 5→6, versionName 1.4.0→1.4.1 |
| Signing compatibility | PASS: installed build 5 and new APK certificate SHA-256 identical |
| Phone migration / preservation | PASS: Room schema 6; all 6 instrument rows, watchlist, settings, engine config and existing collection series exactly unchanged |
| Phone launch | PASS: MainActivity launched, process running; visual/touch checks pending locked screen |
| Phone manual refresh | BLOCKED pending user taps: device rejects ADB input with INJECT_EVENTS SecurityException |

## APK

- Path: `D:\Codex\EGX-Watch\artifacts\EGX-Watch-v1.4.1-build6-2026-09-27-debug.apk`
- Package: `app.egxwatch`; versionName `1.4.1`; versionCode `6`.
- SHA-256: `2716bf4d48e73e1f83fb09b0b78b822a78f0cf813a3f002259649b0dc4792eca`
- Certificate SHA-256: `a26df6b9dfb6a6fbf53a9bc0b56309b2cd17e7bbe1512209936c68ecf82d825f`
- Debuggable development build. Do not uninstall to upgrade. Keep the original local
  signing keystore for future compatible builds; never commit it.

## Reproduce

Set JDK 17, SDK 35 and the original debug signing environment, then run
`gradlew.bat assembleDebug testDebugUnitTest lintDebug`. Set
`ANDROID_SERIAL=emulator-5554` for `connectedDebugAndroidTest`: UI tests clear the
emulator database. Never run the destructive test suite on the user's phone.
Install the resulting APK with `adb -s DEVICE install -r PATH`. Verify its certificate
matches the installed app first. README includes general installation instructions.

Private device database comparison evidence is kept under ignored `.tools/` and is
not committed. Tests use mocks and do not require live APIs. Provider diagnostics
are visible for the current refresh; health/cooldowns survive restart. No background
job can guarantee exact execution under Android Doze, force-stop or battery policy.

## Remaining setup

1. Obtain EGX quote/NAV display and automated-analytics rights. Confirm supported
   identifiers, timestamp semantics, exchange delay, publication schedules and limits.
2. Deploy an HTTPS current-price gateway implementing [the contract](PROVIDER_CONTRACT.md).
   Keep vendor credentials server-side. No historical API/subscription is required.
3. Enter primary URL in Settings and ordered fallback URLs in Analytics settings.
   Supply local verified holidays/session exceptions until reliable remote calendar
   integration exists. Gold URLs and order are separate in Gold → Configure.
4. Refresh on the phone and verify current timestamp, source, identity and freshness
   for each asset class before calling EGX operational. Allow sufficient forward
   observations to accumulate before expecting opportunity assessments.

## Incremental file map

Created: `ProviderDiagnostics.kt`, `ProviderAcceptanceTest.kt`,
`ProviderRepositoryIntegrationTest.kt`, provider acceptance plan, source matrix and
this evidence report. Modified: provider/repository adapters, quote validation/JSON,
freshness/dispute analytics gate, Watch/Gold ViewModels and screens, Room migration
v5→v6/gold order, application migration registration, archive schema version, build
version, existing failover/Room tests, README and gateway contract. Existing manual
DI, WorkManager, forward charts and analytics architecture remain.

Security remains TLS/hostname validation, bounded responses/timeouts, no credential
URLs, no vendor keys in APK and no retired scraper fallback. Local Room storage is
app-private but not separately encrypted; this debug build is for testing. The next
milestone is an entitled current-observation EGX gateway plus reliable calendar and
backend monitoring/push; avoid promising handset delivery guarantees.
