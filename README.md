# EGX Watch 1.3.0 · build 4

Kotlin / Compose Material 3 / Room / Coroutines and Flow / WorkManager.
Android 8.0+ (API 26). This extends the existing application, including the pending
1.2 reliability and interface improvements; existing watchlists and observations migrate.
New installations have an empty watchlist.

## Install

Use `artifacts/EGX-Watch-v1.3.0-build4-2026-09-25-debug.apk`, or the identical
`artifacts/EGX-Watch-debug.apk`. Copy to the phone, open it, allow installation
from that file manager if prompted, and install. Install over the previous locally
signed APK to retain data. This is a development-signed, debuggable build.

```sh
adb install -r artifacts/EGX-Watch-v1.3.0-build4-2026-09-25-debug.apk
adb shell am start -n app.egxwatch/.MainActivity
```

A CI/machine using a different signing key cannot upgrade this installation.
Do not uninstall to resolve that without first considering that local data will be lost.
APKs are ignored by Git; Actions also builds downloadable debug artifacts.

## Data connection is required

**No authorized live EGX feed or historical API has been supplied.** The app now
uses an explicitly configured HTTPS gateway, with up to two fallback gateways.
It never substitutes mock prices. Existing saved values keep their original timestamps.
The existing offline identity catalogue has 296 stocks, 167 funds and one ETF;
it is not a complete, permanently current register of every Egyptian financial product.
Gateway search can validate and add other instruments without changing application code.

TradingView prohibits automated collection and non-display usage. Its undocumented
screener is therefore disabled. Other undocumented website feeds are not assumed to
have analytics/redistribution permission. The old free-feed preference is migrated off
and cannot activate these sources. Historical parser fixtures remain test-only.
See [source policy](docs/FREE_FEEDS.md).

To activate data, provide an **authorized HTTPS gateway base URL** implementing
[the provider contract](docs/PROVIDER_CONTRACT.md). Vendor credentials stay on that
server. Required routes: instrument search/validation, quotes, daily history and
market status. No vendor key should be pasted into the Android app or committed.
There is no external LLM or paid data integration configured.

## Using the app

1. Discover: choose stocks, ETFs or funds, search by ticker/name and validate/add.
   Offline validation is against saved identities, not an assertion of current listing.
2. Settings: enter the primary authorized gateway URL and test the connection.
3. Settings → Analytics, fallbacks & calendar: configure fallback URLs, local holidays
   and exceptional session windows. Holidays override the weekly window.
4. Choose monitoring days and Cairo times, enable monitoring and save. Defaults on
   new installs are Sun–Thu, 10:00–14:30, every 15 minutes; confirm them for the session
   you intend to monitor. Existing user windows remain unchanged.
5. Enable Android notifications and, separately, opportunity notifications. Defaults:
   score 75, coverage confidence 0.70, 60-minute per-security cooldown, 10-point
   material change, 10 alerts/day, quiet 22:00–08:00 Cairo.
6. Watchlist → instrument → Explore analysis & risks shows reproducible indicators,
   score components, drivers, risks, provider and timestamps. Status shows provider
   health/cooldowns, monitoring activity, network and Android restrictions.
7. History retains price alerts and opportunity events, including delivery status.

## What the engine does

- Ordered provider failover on timeout, invalid response, rate limit or stale data;
  persistent exponential backoff with jitter and Retry-After handling. TLS certificate
  and hostname checks remain enabled. Responses and request durations are bounded.
- Latest saved values remain visible after errors/restarts. LIVE, DELAYED, STALE and
  UNAVAILABLE quality states accompany the original quote/NAV classification.
  NAV valuations and indicative retrieval timestamps are never exchange live quotes.
- Snapshot fingerprints include timestamp, normalized decimals, provider and market
  fields. Unchanged snapshots skip historical fetches, analytics and opportunity alerts.
- Deterministic SMA/EMA, RSI, MACD, Bollinger bands, ATR when OHLC exists, volatility,
  momentum, support/resistance, volume confirmation, drawdown and range reward/risk.
  Volume comparisons use completed daily observations, never partial intraday volume
  against a full day's average. At least 60 comparable completed observations required.
- Explainable weighted trend/momentum/risk/volume score. Missing valuation, news and
  market context are disclosed, not invented. Confidence is **data coverage**, not a
  calibrated likelihood of profit. These heuristic signals are not individualized advice.
- Opportunity notifications require an eligible session, quality history, thresholds,
  a meaningful increase in state or material score change, cooldown, quiet-hours and
  daily-limit checks. State persists independently of the bounded event history.
- Existing price/NAV change thresholds and explicit every-check alerts remain separate.
  Those optional every-check alerts are not opportunity notifications.

## Background limits

WorkManager runs at intervals of 15 minutes or longer (15/30/60/120/custom). It is
inexact and affected by Doze, network availability, battery restrictions and force-stop.
The visible app also checks at the configured interval; a shared timestamp prevents
normal foreground/background polling more often than the interval. Manual refresh is
explicit and may run outside the session, but cannot emit opportunity alerts there.
Off-session scheduled work performs no normal quote polling; it resumes on an eligible
subsequent Android execution. No foreground service or exact-alarm workaround is used.

The locally editable calendar supports holidays and date-specific windows. No reliable
remote holiday source is configured; the UI distinguishes a configured window from
verified exchange status. Next-check time is an eligibility estimate, not an alarm.
A backend monitor is the next step for dependable independent intraday monitoring and
push delivery. No shorter-interval provider contract is configured, so sub-15-minute
polling is deliberately unavailable.

## Build and test

Requirements: JDK 17, Android SDK 35/build-tools 35.0.0; Gradle 8.13 wrapper.
Set ANDROID_HOME or ignored local.properties with sdk.dir.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
# Windows: .\gradlew.bat with the same arguments
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Instrumentation requires an API 26+
emulator/device and uses isolated test databases; the UI smoke test resets the test
app's local database. **Do not run the instrumentation suite against personal app data.**
Tests use mocked providers and captured parser fixtures; no live market API is needed.
See [verification](docs/VERIFICATION.md) for the actual executed checks.

Manual acceptance: install over the prior local APK; confirm retained selections and
values, source timestamps and empty new-install watchlist. Configure an authorized
provider and check healthy/failing/stale primary and fallback responses. Verify history
is insufficient until valid daily bars arrive; repeated identical snapshots must not
create opportunity events. Exercise quiet hours, thresholds, holidays, offline recovery,
light/dark themes, notification taps and overnight/background behavior on the target phone.

## Architecture and next milestone

The original WatchRepository owns watchlists and baseline price alerts. MarketDataRepository
wraps replaceable providers with health/failover; GatewayProvider implements the HTTPS
contract. AnalyticsRepository orchestrates quality gates, QuantitativeEngine and atomic
Room persistence; StructuredExplanationEngine implements AIInterpretationEngine locally.
OpportunityNotificationManager only delivers already-qualified events. ViewModels expose
Room Flows to Compose. Manual dependency injection remains in WatchApplication.

Room v3 migration preserves existing tables and adds snapshots, historical series,
analyses, events, alert state, provider health and engine configuration/status. Keep at
most 5,000 snapshots, 1,000 bars/security and 500 events of each notification type;
removed instruments' history/analysis are pruned during checks. No unrelated messaging
or personal information enters the market database. Backup remains disabled.

Remaining limits: no authorized live feed/backend deployment, no provider authentication
flow in the handset, no remote holiday synchronizer, no external LLM, no fundamentals/news,
no calibrated/backtested return model, no guarantee of complete directory coverage or
phone scheduling. The next milestone is a licensed backend pilot with validated EGX
corporate-action-adjusted history, official calendar, monitoring and push delivery,
followed by out-of-sample evaluation and device battery testing.

[Development plan](docs/ENGINE_PLAN.md) · [Security/operations](docs/SECURITY_OPERATIONS.md)
· [Implementation file map](docs/ENGINE_FILES.md). Repository GPL-3.0 license retained.
