# Market-data gateway contract (v1)

The Android client accepts a public HTTPS base URL in Settings, for example
`https://market.example.org/v1/`. Credentials, tokens, query strings and fragments
are rejected in that URL. Provider vendor keys must remain in server environment
variables or a secret manager. The shipped app has no API key fields or embedded keys.
For a private gateway, add short-lived user authentication as a separate feature;
do not embed a reusable vendor key in this client.

Implement these four read-only routes. The client URL-encodes every path segment.
Return non-2xx for unsupported instruments, missing data, quota errors or provider
failures. Never return a zero or generated value to stand in for unavailable data.
Responses must be JSON and at most 1 MiB. Search is limited to 500 matches.

## GET /instruments?q=search

Return `{"instruments": [instrument, ...]}`. Query may be empty. Support ticker and
full-name search across stocks, ETFs and funds. A result must have all fields below:

```json
{
  "id": "EGX:CCAP",
  "ticker": "CCAP",
  "name": "Qalaa for Financial Investments",
  "type": "STOCK",
  "currency": "EGP",
  "source": "Your identity directory source",
  "verifiedAt": "2026-09-20",
  "validated": true
}
```

`type` is `STOCK`, `ETF` or `FUND`. `id` is your stable canonical ID (up to 160
characters); `ticker` must be nonempty (up to 160, to accommodate provider fund codes), name up to 200, currency ISO-like
three-letter uppercase. `verifiedAt` is an ISO date. Keep separate share classes
and currencies under separate IDs. There is no client-side six-symbol allowlist
for a gateway. To reuse built-in watchlist entries, support these IDs:

| Symbol | ID | Type |
|---|---|---|
| CCAP | EGX:CCAP | STOCK |
| BINV | EGX:BINV | STOCK |
| T70 | EG:FUND:T70 | FUND |
| CTQ | EG:FUND:CTQ | FUND |
| AZG | EG:FUND:AZG | FUND |
| BFA | EG:FUND:BFA | FUND |
| EGX30ETF | EGX:EGX30ETF | ETF |

## GET /instruments/{id}

Return one instrument object. This call runs again before adding a search result.
The ID, ticker, currency and type must match the selected search result; the
provider must affirm `validated: true`. An arbitrary typed string cannot bypass
validation.

## GET /quotes/{id}

Return fields `instrumentId`, `value` (decimal string), `currency`, `kind`,
`timestamp` (ISO-8601 instant), `source`, `timestampBasis`, and optional
`delayMinutes`. Do not use fetch time as a trade timestamp.

| kind | Meaning | timestampBasis |
|---|---|---|
| LIVE | Provider-confirmed live exchange quote | EXCHANGE |
| DELAYED | Exchange quote with known delay; delayMinutes required | EXCHANGE |
| NAV | Published fund unit NAV, never a stock quote | VALUATION_DATE or EXCHANGE |
| INDICATIVE | Price with unverified exchange timing/delay | PROVIDER_SNAPSHOT |

For date-only NAVs, encode the valuation date at 00:00 in Africa/Cairo converted
to UTC and set `timestampBasis: "VALUATION_DATE"`. The UI displays only the date
with “time not published”; the encoded midnight is a storage convention, not a
claimed publication time. NAV instrument type must be FUND. ETF values here are
exchange quotes; do not send an ETF indicative NAV as its traded price.

The client rejects negative/oversized values, wrong identity/currency, missing
source, future timestamps beyond 5 minutes, regressing timestamps, and a different
value under an unchanged exchange/snapshot timestamp. Date-only NAV corrections
can update the same valuation date. Preserve decimals (up to 24 significant digits
and 12 decimal places). Old observations remain visible with their actual date
and an age warning after 24 hours. They are never re-dated to “now”.

Changing the gateway URL or free-feed mode clears baselines. A changed quote
source or kind also establishes a new baseline, preventing cross-source movement
alerts. Source names should be stable, not include request-specific IDs.

## GET /market-status

Return `state` (`OPEN`, `CLOSED`, `HALTED`, `UNKNOWN`), explanatory `detail` and
ISO-8601 `timestamp`. Obtain holiday/exception information from your provider.
The app does not infer an authoritative open status from a weekday clock.

## Implementing another provider

`MarketDataProvider` is the boundary; implement `search`, `resolve`, `quote` and
`marketStatus`, then select it in `WatchRepository.provider`. Neither Compose
screens nor the WorkManager worker knows about HTTP schemas. `GatewayProvider`
supports server-side vendor substitution without an APK update when this contract
remains stable. `FreePublicProvider` demonstrates three source adapters with
fail-closed parsing and timestamp classification. Tests inject a provider into
the repository without using a network or shipping fictional values.
