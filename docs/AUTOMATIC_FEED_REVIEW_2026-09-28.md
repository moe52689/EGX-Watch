# Automatic EGX feed review — 28 September 2026

## Result and release scope

**No source passed both data acceptance and permitted automated mobile display.**
The requested install-and-see-prices experience remains blocked. Version 1.4.3 does
not add an automatic feed or a proxy. It corrects misleading unavailable/saved-value
labels, prevents the dashboard claiming active monitoring without a source, and
adds fresh-database/mixed-watchlist/restart coverage. No schema change, credential,
mock production value or restricted scraper was introduced. Custom HTTPS gateways
remain available to advanced users. Public browser links are not quote providers.

## Inspection and plan

Inspected the provider selector, gateway contract, offline directory, Room storage,
launch/reconnection checks, monitoring worker, dashboard and acceptance tests before
editing. Existing custom gateways already validate identity, kind, currency and
original timestamps, retain observations on failure, and apply provider cooldowns.
Launch/reconnection checks and the foreground loop respect monitoring configuration;
WorkManager requires network and has a minimum 15-minute interval. Execution is not
exact. No source means no quote request, no fabricated baseline and no price alert.

Plan: review public documentation and underlying rights; probe documented endpoints
without credentials; enable only an accepted source; otherwise expose coverage gaps;
run JVM/Android checks, build, verify signing and update the phone without data loss.
The pre-existing local change to DEVICE_ACCEPTANCE.md is outside this commit.

## EGID: genuine directory access, quote authentication required

[EGID](https://www.egidegypt.com/) identifies itself as an EGX subsidiary and authorized
data provider. Its [published Swagger](https://ticker.egidegypt.com/index.html)
documents the delayed-feed routes and Bearer authentication. A credential-free
`getAllMarketWatchNames` request returned HTTP 200 and these exact Reuters mappings:

| App ticker | EGID security code | Documented getMarketWatchForSymbol result |
|---|---|---|
| COMI | EGS60121C018 | HTTP 401 |
| ETEL | EGS48031C016 | HTTP 401 |
| CCAP | EGS73541C012 | HTTP 401 |
| EGX30ETF | EGS69491M015 | HTTP 401 |
| AZG | No exact directory mapping | No NAV route established |
| T70 | No exact directory mapping | No NAV route established |

Quote probes ran at 20:06:52–53 UTC on 28 September. No quote or source timestamp
was returned. Missing fund mappings do not prove that negotiated EGID products
cannot cover them. The schema does not establish a free display grant, numeric delay,
refresh SLA or unauthenticated quote allowance. No token was requested or borrowed.
An entitled feed and explicit mobile display/analytics/retention rights are needed.

## Leviathan: responses obtained, acceptance failed

[Published API documentation](https://github.com/TheAhmedRmdan/leviathan-docs)
lists Egypt with a 15-minute delay and an anonymous Render service. Its
[OpenAPI](https://leviathan-uchb.onrender.com/openapi.json) documents price and quote
routes. Test responses from those routes disagreed:

| Symbol | `/price` response | `/quote` response (EGP) | Quote `timeLastTraded` |
|---|---:|---:|---|
| COMI | 138.00 | 128.50 | 2026-09-28T12:15:00Z |
| ETEL | 95.01 | 136.00 | 2026-09-28T12:15:00Z |
| CCAP | 4.39 | 6.69 | 2026-09-28T12:15:00Z |
| EGX30ETF | HTTP 400: symbol length 2–6 | Not retried with an invented alias | — |
| AZG | HTTP 400: unrecognized | No documented fund NAV route | — |
| T70 | HTTP 400: unrecognized | No documented fund NAV route | — |

These are **observed API numbers, not independently verified market prices**.
`/price` omitted currency and trade time. Its COMI envelope `date` changed from
`2026-09-28T20:07:08.986914` to `2026-09-28T20:07:26.786018` with request time;
it must not replace exchange time. `/quote` supplied EGP, XCAI, name and trade time;
COMI `timeLastUpdated` was `2026-09-28T13:00:03.5265408Z`. The trade observation
was hours old at retrieval. Reviewed docs supplied no redistribution license,
upstream entitlement, rate limit, guaranteed cadence or SLA. Isolated successes
do not establish reliability. No adapter enabled; no upstream private route queried.

## Open-source and other candidates

| Candidate / primary reference | Blocking finding |
|---|---|
| [Borsa](https://github.com/7ashraf/borsa) | Self-hosted normalizer needing Alpha Vantage/Finnhub keys; unofficial Yahoo disabled by default. Explicitly grants software, not market-data rights. Hosting it cannot cure missing entitlements. |
| [egx-stock-api](https://github.com/rgf2004/egx-stock-api) | Flask wrapper around yfinance; no independent feed or redistribution grant. |
| [yfinance](https://github.com/ranaroussi/yfinance) | Project warns that Yahoo data rights are separate and API use is personal. Not a documented licensed mobile-distribution feed. Not executed. |
| [African Market Data](https://github.com/sawawallet/african-market-data) | EGX calendar entry has no data source. Its bundled free source covers Ghana, not Egypt. |
| [EGX-AI-API](https://github.com/zeyadahmed10/EGX-AI-API) | Architecture includes a scraping service; repository supplies no verified licensed public feed. Not deployed. |
| [EGXPY repository](https://github.com/egxlytics/egxpy) | GitHub and raw README returned 404. Current source/rights could not be verified. |
| [TradeGlob](https://github.com/ibrasonic/TradeGlob/blob/main/PROJECT_STRUCTURE.md) | Depends on tvDatafeed; [TradingView terms](https://www.tradingview.com/policies/) restrict the automated/non-display use needed here. Not executed. |
| [EGXAPI](https://egxapi.com/docs/) | Requires an account key; [legal center](https://egxapi.com/legal/) explicitly contains draft placeholder terms. Marketing claims are not a verified operating feed or display grant. |
| [EODHD](https://eodhd.com/financial-apis/quick-start-with-our-financial-data-apis) / [FMP](https://site.financialmodelingprep.com/developer/docs/quickstart) | Documented access requires operator keys. No entitled hosted gateway or verified Egyptian coverage available for this app. |

Prior [Twelve Data/Alpha Vantage/Finnhub findings](EGX_PROVIDER_REVIEW_2026-09-27.md)
remain unresolved. No documented, permitted keyless NAV API was established for AZG
or T70. Issuer publication pages are not API permission; the retired Azimut/SNDUK
parsers stay disabled. No NAV date or publication time was invented.

## Coverage, scheduling and activation requirements

COMI, ETEL, CCAP, EGX30ETF, AZG and T70 can be added from the offline identity
directory without credentials. **None has an accepted automatic value source.**
The app explicitly shows unavailable; existing values retain their original source
and timestamps as saved observations. NAVs use valuation dates, not exchange-trade
freshness labels. The current four-day age bound is not an issuer publication calendar.

A 10:00–14:30 half-open session contains 18 nominal 15-minute slots. Six one-request
instruments require at least 108 requests/day before retries/manual checks. Neither
tested service established a usable anonymous quote budget. Do not infer unlimited
permission from an HTTP 200. A future licensed gateway must cache across devices,
honor rate limits and NAV publication calendars, and expose original provider time.

Activation requires an actual provider agreement permitting this app's automated
display/analytics and local retention, usable credentials where required, stable
symbol mappings, timestamp/delay semantics, and an operated HTTPS backend if keys
are needed. No existing entitled backend was available to deploy as a default.

## Verification

`assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` passed.
After adding an explicit offline-recovery/cooldown test, `testDebugUnitTest` passed
again: **82 JVM tests, zero failures**. Lint: zero errors, 23 existing maintenance
warnings. A fresh install on the API 35 emulator followed by
`adb -s emulator-5554 shell am instrument -w app.egxwatch.test/androidx.test.runner.AndroidJUnitRunner`
passed **27 Android tests**. These cover empty initial storage, all six requested
identities and mixed types, persistence/restart, migration preserving saved values,
source failures, and navigation. JVM coverage includes rate limiting/recovery,
offline failover and reconnection after cooldown, malformed/stale/future timestamps,
delayed quotes, NAV valuation language and expiry. Tests use mocks, not live EGX feeds.
Emulator UI inspection confirmed DATA UNAVAILABLE and the explicit no-source notice.

After stopping the emulator, ADB showed exactly one authorized phone. Installed
v1.4.2 (7) and the new APK passed certificate verification with matching SHA-256:
`a26df6b9dfb6a6fbf53a9bc0b56309b2cd17e7bbe1512209936c68ecf82d825f`.
`adb install -r` succeeded; v1.4.3 (8) launched and its process ran. No uninstall or
data clear. Before/after database comparisons found no changes in any compared
watchlist, instrument, settings, history, analysis, collection or alert table.
The phone's latest watchlist contains **BFA, CCAP and T70**. Runtime health/status
tables were excluded from equality comparison. Phone UI dump contained no text,
so visual UI verification is from the emulator; phone preservation is verified from
the database, not inferred from a screenshot.

APK: `artifacts/EGX-Watch-v1.4.3-build8-2026-09-28-debug.apk` (also copied to
`artifacts/EGX-Watch-debug.apk`). SHA-256:
`d75e21c2e04d3481f6a7abe51f5a32295e39e72ce18cc138dae01c9b90f859f1`.
No source was enabled and **zero instruments received accepted real market data
inside the app**. Probe payloads and private phone snapshots are ignored local
evidence under `.tools/default-feed-review/`, never APK assets or committed data.
