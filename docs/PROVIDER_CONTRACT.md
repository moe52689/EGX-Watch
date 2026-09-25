# Current release: 1.4 forward collection

The Android analytics pipeline requires current quotes only. It never calls the history route below. That route describes a legacy optional adapter, retained for compatibility/testing; no historical subscription or pre-installation history is needed.

Gold gateways use instrument id `GLOBAL:XAUUSD`, ticker `XAU/USD`, type `COMMODITY`, currency `USD` and quote kind `SPOT` with `PROVIDER_SNAPSHOT` timestamp basis. Values are USD per troy ounce. Provide no API keys in URLs.

# Authorized market-data gateway contract · 1.3

No live API entitlement, vendor key or gateway is bundled. To activate this app you
must supply an HTTPS base URL implementing the routes below and obtain upstream rights
covering EGX display, automated analytics and any redistribution. The Android client
sends no vendor credential. Put upstream credentials in server environment secrets or a
secret manager. Do not use a secret-bearing URL. Short-lived user authentication and
backend deployment are future work; use only data your gateway is authorized to expose.

Settings accepts the primary URL; Analytics settings accepts two ordered fallback URLs.
Every gateway must use the same canonical instrument IDs/currencies. Keep provider
source labels stable. APIs may be changed behind the gateway without rewriting the app.

TLS certificate/hostname validation required. No redirects, URL user-info, query tokens
or fragments. Response JSON maximum 1 MiB; 20-second call timeout. Return HTTP 429 with
Retry-After (seconds or HTTP date) on quota exhaustion. Return non-2xx for missing data,
never fictional values or zero as a substitute. Search results are limited to 500 per
query; refine queries for larger universes. Routes are relative to your base URL.

## GET instruments?q=search and GET instruments/{id}

Search returns `{"instruments":[instrument,...]}`; resolution returns one object.
The app revalidates the selected ID/ticker/type/currency on addition. Example schema
(identity only; these examples are documentation, not runtime responses):

```json
{
  "id":"EGX:CCAP", "ticker":"CCAP", "name":"Qalaa for Financial Investments",
  "type":"STOCK", "currency":"EGP", "source":"Your authorized directory",
  "verifiedAt":"2026-09-25", "validated":true
}
```

Types STOCK/ETF/FUND/COMMODITY; id and ticker max 160, name max 200, three uppercase currency
letters, verifiedAt ISO date. Distinct share classes/currencies require distinct IDs.
Existing built-in IDs: EGX:CCAP, EGX:BINV, EGX:EGX30ETF, EG:FUND:T70,
EG:FUND:CTQ, EG:FUND:AZG, EG:FUND:BFA; other equities use EGX:ticker.
Existing fund IDs include SNDUK:slug and AZIMUT:issuer-id. Resolve these to your vendor
identifiers server-side; do not guess a ticker from a similar company name.

## GET quotes/{id}

Required fields:

| Field | Type / meaning |
|---|---|
| instrumentId | Same canonical identity |
| value | Verified decimal string; max 24 significant digits / 12 decimal places |
| currency | Must match the instrument |
| kind | LIVE, DELAYED, NAV or INDICATIVE |
| timestamp | Actual data ISO-8601 instant, never relabel a saved response as now |
| source | Stable upstream provider/series label |
| timestampBasis | EXCHANGE, VALUATION_DATE, PROVIDER_SNAPSHOT or RETRIEVAL_TIME |
| delayMinutes | Required nonnegative integer for DELAYED |
| open, previousClose, high, low, bid, ask | Optional decimal strings, omit if unavailable |
| volume | Optional nonnegative integer, omit for unsupported NAV/quote feeds |

LIVE/DELAYED require EXCHANGE timestamps. INDICATIVE requires snapshot/retrieval basis
and cannot generate opportunity assessments. Funds require NAV; ETF NAV is distinct
from its traded exchange price. Date-only NAV: convert Africa/Cairo midnight on the
valuation date to UTC and use VALUATION_DATE. Midnight is a storage convention, not an
invented publication time. Never mix ETF iNAV with official NAV or exchange price.

The snapshot envelope stores ticker/name from the validated instrument, all supplied
fields and provider/data timestamp. Age/freshness and daily percentage change are derived
on-device; daily change is unavailable without nonzero previousClose. Quote freshness is
20 minutes plus declared exchange delay; NAV age allowance is four calendar days. This
is a conservative local heuristic, not a verified fund publication schedule. Invalid or
unverified timing yields UNAVAILABLE; old or cached/error-marked data yields STALE.

## GET history/{id}

Returns a complete daily series on the **same price/NAV scale, currency, source and kind**
as the selected quote. Use 60–1,000 completed observations. No polling snapshots may be
silently relabelled as historical daily candles. Corporate-action adjustments must be
comparable to the current price; `comparable:false` prevents analysis.

Object fields:

- instrumentId, currency, source, kind: match the quote.
- comparable: boolean asserting the common adjustment/valuation basis.
- asOf: timestamp of provider verification of this series, at most 24 hours old.
- expectedDates: ordered ISO date array of **all expected completed observation dates**
  in the returned span, using the provider's reliable exchange/publication calendar.
- candles: ordered array containing date, close (number), optional open/high/low
  (numbers) and volume (integer). Every expected date must have exactly one candle.
  Only completed observations; never include an unfinished daily candle.

Do not fill holidays, suspensions or missing days with invented bars. Do not claim a
series is complete by removing a missing date from expectedDates. NAV observations may
have a different publication calendar than equities. The client verifies exact coverage,
ordering, identity, values and timestamp bounds. Latest bar must be no more than seven
calendar days old; longer legitimate suspensions currently yield insufficient data.
Discontinuities over 50% are rejected pending adjusted data. This is deliberately
conservative, not an automatic corporate-action detector.

History is reused for up to one hour per series; snapshot fingerprints suppress repeated
analytics. Successful source/series changes invalidate history reuse. History failure
never erases a successfully retrieved quote and cannot produce an opportunity alert.

## GET market-status

Return state OPEN/CLOSED/HALTED/UNKNOWN, detail and timestamp. This is an observed status,
not a full future calendar. The currently implemented monitoring calendar is **locally
editable**: weekly days/windows, holiday dates, exceptional date windows. A remote
calendar endpoint is not silently assumed to exist. Backend calendar synchronization
is a recommended next step.

## Backend monitoring boundary

A future service can use the same quote/history/directory contract independently of
handset execution, maintain entitlement-aware polling, and deliver push events with
immutable data timestamps. It should own upstream rate limits, secrets, official
calendar ingestion and adjusted-history quality. The APK does not currently deploy a
backend, use FCM, call an LLM or obtain data licenses. StructuredExplanationEngine
currently explains QuantitativeEngine's reproducible features locally.
