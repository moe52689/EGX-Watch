# EGX API acceptance review — 27 September 2026

## Decision

**No provider accepted for production integration.** No authenticated current EGX
quote or Egyptian NAV was obtained from Twelve Data, Alpha Vantage or Finnhub.
No adapter, key, invented price, symbol substitution or website scraper was added.
This is a verified documentation review with explicit access gaps, not proof that
these vendors can never supply Egyptian data under a negotiated agreement.

## Existing application and test scope

Inspected the gateway contract, source policy, quote/freshness validation, repository,
WorkManager scheduler and the connected phone's app database. At initial inspection the watchlist
contained **CCAP / EGX:CCAP / Qalaa for Financial Investments / STOCK / EGP**.
The final pre-install snapshot also contains **T70 / Thndr EGX70 Fund / FUND / EGP**;
these are the two selections preserved during deployment. BINV, EGX30ETF and
CTQ/AZG/BFA remain earlier requested examples. T70 coverage and publication timing
are unverified for all three providers, just as for the other Egyptian NAV examples.
The saved schedule is enabled, 15 minutes, Sunday–Thursday, 10:00–14:30 Africa/Cairo.
No custom gateway is configured. Android schedules approximately; manual refreshes
and retries can consume additional quota. The app deduplicates securities across lists.

No provider credentials were found in process or persistent User/Machine environment
variables with provider names, project-root environment files, or the inspected usual
`.config`/`.secrets` locations. This was a bounded check, not an exhaustive search of
personal files or password vaults. No credentials were displayed or committed. No new
accounts, subscriptions or demo credentials were created/used; authenticated API
resolution and quote tests are blocked. Official documentation and public catalog
pages were reviewed, not used as an application price source.

## Twelve Data — priority candidate, not approved

The [XCAI exchange page](https://twelvedata.com/exchanges/XCAI) labels Pro+/Venture+,
leaves delay unspecified, and lists reference/fundamental/analysis/fund endpoints,
without listing current quote/time-series endpoints. The [stocks product page](https://twelvedata.com/stocks)
includes Egypt under Pro/Venture, but supplies no Egyptian delay. These signals do
not establish per-symbol price access. The provider's [July update](https://twelvedata.com/news/july-2026-update)
explicitly says its exchange directory includes venues it does not yet carry.

Official interactive lookup for `CCAP` returned unrelated Crescent Capital/CORESTATE
securities. Searching `Qalaa` found [Qalaa Holdings S.A.E., EGS73541C012, EGX/XCAI](https://twelvedata.com/markets/531875/stock/egx/egs73541c012).
This is an issuer metadata candidate, not an authenticated canonical-ID mapping or
price. The issuer page displayed no current quote in this review. Never request bare
CCAP and accept the first result; confirm Egyptian share class, identifier and EGP.

[Individual pricing](https://twelvedata.com/pricing): Basic is 8 credits/minute and
800/day; Pro starts at 610 credits/minute with no daily cap and advertises mutual-fund
NAV. Neither that feature nor a catalog entry proves Egyptian fund coverage. Exact
access must be checked with the subscribed account; no purchase is recommended on
these labels alone. [Business plans](https://twelvedata.com/pricing-business) provide
separate display tiers. [Usage guidance](https://support.twelvedata.com/en/articles/5332349-commercial-and-personal-usage)
requires additional approval for non-US commercial price use and a separate agreement
for redistribution. [Terms](https://twelvedata.com/terms) separately govern display,
non-display analytics, derived data and attribution. Private testing permission does
not grant public mobile-app distribution rights.

[Current endpoint docs](https://twelvedata.com/docs): `/quote` costs one credit per
symbol. `timestamp` is interval opening time; `last_quote_at` is last minute-candle
time, not necessarily last execution time. `/price` example contains only price,
insufficient for this app's timestamp contract. `/stocks` supports MIC and plan filters.
Fund directory updates daily; a daily directory update is not a NAV publication.
Fund-summary examples include NAV without a valuation timestamp; they cannot alone
satisfy the contract. Egyptian update cadence and NAV valuation dates remain unknown.

## Alpha Vantage — not approved

[Documentation](https://www.alphavantage.co/documentation/) provides SYMBOL_SEARCH
and GLOBAL_QUOTE but does not establish this watchlist's EGX symbol or Egyptian ETF/
fund NAV coverage. Quote updates default to end-of-day; documented realtime and
15-minute delayed entitlements concern US stocks. A latest-trading-day date must not
be relabeled as an intraday trade timestamp. No exact EGX API identifier was verified.

[Support](https://www.alphavantage.co/support/) states 25 free requests/day; verified
open-source/educational exceptions require verification, which this project does not
have. [Premium](https://www.alphavantage.co/premium/) removes the daily cap under paid
plans, but does not establish EGX access. [Terms](https://www.alphavantage.co/terms_of_service/)
allow personal non-commercial use on owned devices; broader/commercial use requires
written agreement. A premium API key is not automatically redistribution permission.

## Finnhub — not approved

[Quote documentation](https://finnhub.io/docs/api/quote) describes realtime US quotes;
international realtime requires Enterprise partner-feed arrangements. Symbol lookup
and stock-symbol metadata require authentication. No CCAP/XCAI identifier, Egyptian
ETF or dated Egyptian fund NAV response was verified. Quote examples include `t`,
but Egyptian timestamp semantics and delay remain unverified. Global fund profiles/
holdings are not evidence of current NAV service.

[Pricing](https://finnhub.io/pricing), verified in the rendered official page: free
personal use has 60 calls/minute. All-in-One lists 900 market-data and 300 fundamental
calls/minute, still personal use. Its named international market-data coverage is
TSX/LSE/Euronext/Deutsche Boerse; LSE is 15-minute delayed, others EOD. This does not
prove EGX coverage. [Enterprise](https://api.finnhub.io/pricing-startups-and-enterprise)
advertises commercial redistribution subject to an actual agreement and feed scope.
[Terms](https://finnhub.io/terms-of-service) require written approval to share data or
derived results and require deletion after the corresponding data subscription ends.
That retention requirement needs explicit design before using this app's persistent
history. All plans have an additional 30-calls/second ceiling.

## Instrument acceptance matrix

| App instrument | Twelve Data | Alpha Vantage | Finnhub | Current observation |
|---|---|---|---|---|
| CCAP, currently saved | Qalaa issuer metadata candidate EGS73541C012; API mapping/price/rights unverified | EGX resolution/access unverified | EGX resolution/Enterprise access unverified | None |
| BINV, earlier example | Exact API mapping/quote unverified | Unverified | Unverified | None |
| EGX30ETF traded price or NAV | Separate price/NAV coverage unverified | Unverified | Unverified | None |
| T70 (saved before deployment), CTQ, AZG, BFA NAV | Exact share classes, NAV dates and publication schedules unverified | No verified Egyptian NAV route | Global fund metadata is insufficient | None |

No ambiguous ticker match, foreign listing, issuer-page number or generic demo quote
counts as a successful EGX response. Unverified is not a confirmed unsupported result.

## Fifteen-minute capacity calculation

The configured half-open 10:00–14:30 window has 18 ideal polling slots: 10:00 through
14:15. For N securities and C credits per quote, budget at least 18×N×C per trading
day, plus directory/status calls, retries and manual checks. Corroboration against
multiple sources consumes each source's allowance. Central gateway caching should
share observations across handsets and enforce the account-wide budget.

| Plan | One stock / 18 quote calls | Six instruments / 108 quote calls | Qualification |
|---|---|---|---|
| Twelve Basic, one-credit quote assumption | Numerically within 8/min, 800/day | Six-call burst within 8/min; leave overhead headroom | EGX entitlement/display not verified |
| Alpha free, 25/day | 18 leaves only 7 for overhead | Exceeds daily allowance | EOD/default and unverified EGX coverage |
| Finnhub free, 60/min | Numerically fits | Numerically fits | Free realtime quote scope is US, not verified EGX |

These are quota calculations, not feed acceptance. Continuous all-day 15-minute
polling would be 96×N calls/day and does not match this phone's configured session.
Polling both final saved entries every slot would require 36 quote calls/day, already
above Alpha Vantage's free allowance before overhead. A publication-aware NAV cache
could reduce calls once the fund's actual publication schedule is verified.
NAVs should be fetched according to verified issuer publication days/cutoffs, cached
between releases, and labeled by valuation date. None of the reviewed sources
established those schedules for the four Egyptian funds. The app's current four-day
NAV age ceiling is a conservative heuristic, not an issuer calendar; no new NAV
integration can claim schedule-aware freshness without those inputs.

## Requirements before an adapter can be enabled

Obtain a securely stored server key plus account plan/entitlement evidence and written
confirmation of exact Egyptian securities, mobile display, alerts/analytics, retention
and redistribution scope. Confirm native identifier/share class, currency, quote vs
NAV kind, timestamp meaning, delay, update cadence and per-endpoint limits. Capture
an authenticated successful current response during the relevant session/publication
window. Keep upstream credentials on a private backend; the app receives only the
[existing HTTPS gateway contract](PROVIDER_CONTRACT.md). A vendor base URL does not
implement that contract. Missing data returns unavailable, not a substituted price.

## Build and delivery

`assembleDebug testDebugUnitTest lintDebug` completed successfully on the unchanged
application; Gradle reused up-to-date compilation/test results (77 unit tests, zero
failures; lint zero errors). No production code or schema changed. Existing custom
gateway and public browser shortcuts remain. APK is v1.4.2 build 7, package
`app.egxwatch`, at `artifacts/EGX-Watch-v1.4.2-build7-2026-09-27-debug.apk`.

The research-only documentation is committed separately from any integration.
Phone verification: exactly one authorized phone `dd607155`; matching installed/new
certificate SHA-256 `a26df6b9dfb6a6fbf53a9bc0b56309b2cd17e7bbe1512209936c68ecf82d825f`.
`adb install -r` succeeded without uninstalling or clearing data; MainActivity launched.
Both CCAP and T70, full saved instrument rows, watchlists, settings and collection
metadata compare unchanged before/after. Installed version remains 1.4.2 build 7;
this review added no application functionality and therefore no version increment.
APK SHA-256: `b50a576fdbfbbe99fa48a7e00469b7263b80e3169d805f106730106751b21a39`.
No successful EGX quote/NAV is claimed for any of these providers.
