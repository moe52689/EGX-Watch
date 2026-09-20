# Free public feed coverage

Checked on 20 September 2026. These are public website feeds with no API keys,
not a guaranteed or exchange-licensed real-time service. Each source is credited
in the app. Respect upstream terms and rate limits; no authentication bypass,
cookie harvesting or rate-limit evasion is used.

| Instruments | Source | Classification and limitation |
|---|---|---|
| CCAP, BINV | [EGXpilot public API](https://egxpilot.com/developers.html) | `LastPrice`; INDICATIVE. `CreatedAt` is displayed as provider snapshot time, never exchange time. Actual delay is unknown. |
| AZG | [Azimut issuer fund list](https://azimut.eg/funds) | Published NAV from fund ID 16, slug az-gold-2. Date-only valuation. |
| T70, CTQ, BFA | [SNDUK public fund price table](https://snduk.com/eg/page/mutual-funds-prices-today?lang=en) | Published third-party NAV with valuation date. Exact fund-page link selects the row. |
| EGX30ETF | [Issuer identity directory](https://www.egx30etf.com/) | Identity supported; free traded-price mapping is not verified, so value remains unavailable. |
| Other instruments | Configurable HTTPS gateway | Search, validate and monitor any supported stock, ETF or fund. No app rewrite needed. |

The initial directory is deliberately small and sourced. It is not the application’s
supported universe: gateway search and validation are dynamic. Free-feed stock
coverage is restricted to verified ticker/name/currency mappings; the public
stock response includes instruments from other markets and does not supply enough
metadata to classify every entry safely.

Identity references: [CCAP](https://www.arabfinance.com/en/Home/CompanyProfile/CCAP),
[BINV](https://www.arabfinance.com/en/Home/CompanyProfile/BINV), and
[Thndr fund directory](https://support.thndr.app/en/articles/651226-mutual-fund-cutoff-times).

The Azimut website requests
`https://app.azimut.eg/api/fund/list?size=100&web=true` publicly. SNDUK uses an HTML
table: selectors, currency, numeric value and date must all pass validation.
Changes to either source fail as unavailable instead of yielding fabricated values.
Yahoo's public chart endpoints returned HTTP 429 during evaluation and are not
included. No subscription/account creation is necessary for the included feeds.

Checks reuse public-feed responses for at most 60 seconds within a process,
avoid repeated requests for the same instrument across watchlists, and use
15-minute-or-longer background intervals. HTTP failures are shown on each
instrument and retried at the next check, not in a tight loop. Free market status
is explicitly Unknown; the feeds do not establish exchange hours/holidays.

Test fixtures are small real response excerpts captured on the above date and
live only under `src/test/resources`. They are not packaged into the app and never
used as runtime prices. Numerical values in tests are assertions against those
historical excerpts, not current-price claims.
