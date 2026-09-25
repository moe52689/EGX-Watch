# Forward-history release · 1.4.0 / build 5

## Architecture and schema

Existing modules were extended, not rebuilt. Manual dependency injection remains in
WatchApplication. ViewModels expose bounded Room Flows; HTTP, database work and chart
preparation use coroutines. Analytics do not call the legacy historical API adapter.

Room schema 3 → 4 adds `market_observations`, `observed_sessions`, `collection_series`.
Schema 4 → 5 adds `gold_config`, `gold_status`, `forward_preferences`, `gold_rules`,
`gold_rule_state`, `alert_center`. Migrations preserve watchlist selections/latest
values and existing events. Earlier externally sourced history/assessments are
cleared rather than represented as device-collected samples. The v1→v5 integration
upgrade test validates the complete migration chain against Room's schema.

Observation identities are SHA-256 fingerprints of normalized market fields; series
identity includes instrument/currency/provider/data kind/timestamp basis. Provider
and acquisition timestamps remain separate. A unique series+provider-time index
prevents duplicate growth. Same-date NAV revisions replace their sample. Normal
out-of-order/conflicting timestamps cannot expand history.

Valid observations increment session OHLC, occupied/expected slots and valid counts.
OHLC is explicitly *sampled*, not official market candles. The session can contain
unknown activity between observations. Raw data is retained for 90 days before
aggregation-backed compaction; unusable old rows keep only the latest per series.
Long charts retain sampled summaries, with a 4,000-session query ceiling. Archive or
future hierarchical aggregation is needed for histories exceeding that window.

## Analytics

IndicatorRequirement gates minimum samples, observed sessions and missing ratio
independently. Maturity is INITIALIZING → INTRADAY (3 samples/1 session) → SHORT_TERM
(5/5) → DEVELOPING (20/20) → ESTABLISHED (50/50), with progressively stricter
continuity requirements. Elapsed calendar age alone never satisfies a gate.
Completeness describes the span between collected observations and expected sessions,
not guaranteed coverage of an entire exchange trading day. No observations before
collection began are synthesized to fill gaps.

AdaptiveQuantitativeEngine computes early intraday momentum then unlocks sampled
session averages, RSI, MACD, Bollinger upper band, volatility, ATR (non-NAV), support/
resistance and drawdown. Missing news/fundamentals/official volumes are disclosed.
GoldAnalyticsEngine shares deterministic calculations and maturity but has independent
input collection, schedule and alert delivery. Heuristic component weighting remains
explainable. Coverage confidence is capped and is not a calibrated return probability.

Bad/nonpositive samples, large discontinuities, stale sources and missing continuity
suppress opportunity conclusions. Ordinary indicator warm-up is shown as building
history. A split/corporate action can intentionally trigger the discontinuity gate;
no corporate-action adjustment service is configured.

## Screens and charts

Home has monitoring counts/status, recent qualified events, global-gold summary and
compact watchlist cards with source/time/freshness, daily movement when supplied,
local sparkline, score/confidence and maturity. Markets preserves search/validation.
Instrument details add collected-history charts and per-indicator availability.
Gold, Alerts and Storage are new screens; existing settings/status/analysis remain.

Vico Compose 2.1.3 is Apache 2.0, pinned to the application's Kotlin/Compose generation.
Charts offer TODAY/5D/1M/3M/6M/1Y/ALL, disabled unavailable long ranges, marker inspection,
pan/zoom, sampled candles, supplied volume and latest-level overlays. Long-range
prices are actual last observations in a bucket. Alert markers map to the nearest
rendered sample after downsampling; the event dialog retains its exact timestamp.
A nearby marker is not a claim that its time equals the bucket's displayed time.

Material 3 light/dark semantic colors distinguish gains/losses, gold and analytics.
Labels accompany colors; chart semantics summarize values. Android/Compose RTL layout
is supported, with English copy. Full Arabic localization is a later milestone.

## Gold and alert behavior

Gold has its own configurable provider order, persisted backoff/cooldowns, separate
WorkManager job and local session manager. Default source: documented Gold-API
keyless current XAU/USD. Cached snapshots are labelled saved; failed updates preserve
the last observation and report stale/unavailable rather than claiming a live stream.
No remote historical gold endpoint is used.

GoldAlertManager handles PRICE_ABOVE, PRICE_BELOW, PERCENT_MOVEMENT, RAPID_MOVEMENT,
NEW_HIGH and NEW_LOW. First qualifying observations establish a baseline. Crossings,
per-rule cooldown and fingerprints persist atomically with events. Percent/high/low
rules require an observed baseline near the lookback start. Rapid-movement rules need
20 comparable sampled intervals and movement beyond three sample standard deviations
and the configured minimum. They cannot invent an earlier price.

AlertCenter combines prior price/opportunity events, gold events and source-failure
system transitions. Import is idempotent; read/dismiss state survives restart.
At most 500 undismissed events are loaded, 2,000 retained. No replay notification is
sent merely because old events are imported or the app restarts.

## Export and reset

Exports are a consistent logical database snapshot streamed under a Room transaction.
No plaintext temporary file is created. Format:

- 8 ASCII bytes `EGXENC01`.
- 4-byte big-endian PBKDF2 iteration count (210000).
- 16-byte random salt, then 12-byte random GCM nonce.
- AES-256-GCM ciphertext including the 16-byte authentication tag. The entire 40-byte
  header is additional authenticated data.
- Key derivation: PBKDF2-HMAC-SHA256, password, salt, 256-bit key.
- Plaintext: UTF-8 JSON Lines. First line describes format/version/time; remaining
  lines contain `table` and `row` objects with SQLite values/nulls.

Use the password only in trusted local tools. The unit test verifies decryption,
wrong-password rejection, tamper rejection and clearing the password char buffer.
In-app restore is not implemented. The app-private SQLite file uses Android's storage
protection, not independent database encryption. OS backup remains disabled.

Confirmed reset removes observations/session summaries/series metadata/analyses and
alert baseline state. Watchlists, last saved prices and notification history remain.
Reset cannot conjure historical samples: subsequent observations rebuild maturity.

## Provider setup and operational limits

EGX needs authorized current quote/search/validation/status endpoints; history is
optional and unused by this release. Gold works with the documented free current feed
or configured gold gateways. Provider access can change; no free feed has an uptime,
accuracy or universal EGX coverage guarantee. No provider secrets are embedded.

Android's >=15-minute WorkManager schedule is approximate. Gold is independent from
EGX; local holidays/windows are editable. There is no authoritative synchronized
calendar or backend push service. Stock opportunity rules retain quiet hours/daily
limits; gold rules currently use per-rule crossing/cooldown. No unrelated private
account, contact, message or location data is sent for analytics.

Recommended milestone: authorized current-price EGX backend, maintained calendar,
independent monitoring/push, encrypted archive restore, and validation of locally
accumulated indicators against subsequent outcomes. History subscriptions remain
optional. See README and VERIFICATION for build/install and executed checks.

## Implementation files

The following inventory is generated from this release's Git changes.


### Created

- `app/src/androidTest/java/app/egxwatch/ObservationIntegrationTest.kt`
- `app/src/main/java/app/egxwatch/data/AlertCenter.kt`
- `app/src/main/java/app/egxwatch/data/ForwardDatabase.kt`
- `app/src/main/java/app/egxwatch/data/ForwardMigrations.kt`
- `app/src/main/java/app/egxwatch/data/GoldMarketProvider.kt`
- `app/src/main/java/app/egxwatch/data/GoldMarketRepository.kt`
- `app/src/main/java/app/egxwatch/data/MarketArchive.kt`
- `app/src/main/java/app/egxwatch/data/ObservationDatabase.kt`
- `app/src/main/java/app/egxwatch/data/ObservationRepository.kt`
- `app/src/main/java/app/egxwatch/data/StorageRepository.kt`
- `app/src/main/java/app/egxwatch/domain/AdaptiveAnalytics.kt`
- `app/src/main/java/app/egxwatch/domain/AnalyticsMaturity.kt`
- `app/src/main/java/app/egxwatch/domain/ChartRange.kt`
- `app/src/main/java/app/egxwatch/domain/GoldAnalytics.kt`
- `app/src/main/java/app/egxwatch/domain/MarketSessions.kt`
- `app/src/main/java/app/egxwatch/monitor/GoldAlertManager.kt`
- `app/src/main/java/app/egxwatch/monitor/GoldMonitoring.kt`
- `app/src/main/java/app/egxwatch/ui/AlertsScreen.kt`
- `app/src/main/java/app/egxwatch/ui/Dashboard.kt`
- `app/src/main/java/app/egxwatch/ui/GoldScreen.kt`
- `app/src/main/java/app/egxwatch/ui/InstrumentDashboard.kt`
- `app/src/main/java/app/egxwatch/ui/MarketCharts.kt`
- `app/src/main/java/app/egxwatch/ui/StorageScreen.kt`
- `app/src/test/java/app/egxwatch/AdaptiveAnalyticsTest.kt`
- `app/src/test/java/app/egxwatch/ArchiveTest.kt`
- `app/src/test/java/app/egxwatch/ChartRangeTest.kt`
- `app/src/test/java/app/egxwatch/GoldAlertsTest.kt`
- `app/src/test/java/app/egxwatch/GoldSourceTest.kt`
- `app/src/test/java/app/egxwatch/MaturityTest.kt`
- `docs/FORWARD_HISTORY_PLAN.md`
- `docs/FORWARD_RELEASE.md`
- `docs/THIRD_PARTY_NOTICES.md`

### Modified

- `README.md`
- `app/build.gradle.kts`
- `app/src/androidTest/java/app/egxwatch/EngineRepositoryTest.kt`
- `app/src/androidTest/java/app/egxwatch/MigrationTest.kt`
- `app/src/androidTest/java/app/egxwatch/UiSmokeTest.kt`
- `app/src/main/java/app/egxwatch/MainActivity.kt`
- `app/src/main/java/app/egxwatch/WatchApplication.kt`
- `app/src/main/java/app/egxwatch/data/AnalyticsRepository.kt`
- `app/src/main/java/app/egxwatch/data/Database.kt`
- `app/src/main/java/app/egxwatch/data/EngineDatabase.kt`
- `app/src/main/java/app/egxwatch/data/EngineJson.kt`
- `app/src/main/java/app/egxwatch/data/WatchRepository.kt`
- `app/src/main/java/app/egxwatch/domain/Analytics.kt`
- `app/src/main/java/app/egxwatch/domain/EngineMarket.kt`
- `app/src/main/java/app/egxwatch/domain/Market.kt`
- `app/src/main/java/app/egxwatch/monitor/Monitoring.kt`
- `app/src/main/java/app/egxwatch/ui/EngineScreens.kt`
- `app/src/main/java/app/egxwatch/ui/WatchViewModel.kt`
- `docs/FREE_FEEDS.md`
- `docs/PROVIDER_CONTRACT.md`
- `docs/SECURITY_OPERATIONS.md`
- `docs/VERIFICATION.md`
