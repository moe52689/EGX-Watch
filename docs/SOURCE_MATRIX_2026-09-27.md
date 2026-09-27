# Current-source acceptance matrix · 2026-09-27

The prototype is **NO-GO for operational EGX monitoring** until an authorized source
returns a validated current EGX observation on the device. Historical purchases are
not required. Documentation discovery is not successful live-data validation.

| Candidate / documentation | Coverage relevant here | Authentication / limits | Automation rights and timestamp semantics | Probe / decision |
|---|---|---|---|---|
| [EGID](https://www.egidegypt.com/) / [published Swagger](https://ticker.egidegypt.com/index.html) | Exchange-owned EGX feed service; delayed-feed routes appear in Swagger. Exact ETF and individual fund coverage must be contracted. | Feed access/limits require provider confirmation; no account supplied. Public Swagger does not establish anonymous quote rights. | EGID describes itself as EGX's authorized data provider. No blanket permission to repurpose its website ticker was verified. Field/timestamp semantics need the authorized feed contract. | Documentation reachable; quote probe **blocked by missing entitlement/permission**. No ticker scrape added. |
| [ICE EGX catalog](https://developer.ice.com/fixed-income-data-services/catalog/egyptian-exchange-egx) | Explicit equities/ETF streaming, bonds and indices; mutual-fund NAV coverage not established. | Licensed client feed/API; credentials and limits contract-specific. | Enterprise APIs support integration; catalog describes native exchange timestamps plus UTC system timestamps. Display/analytics rights require subscription terms. | Coverage docs verified; live response **blocked by credentials/entitlement**. Gateway candidate, not a bundled feed. |
| [Twelve Data XCAI](https://twelvedata.com/exchanges/XCAI) / [usage rights](https://support.twelvedata.com/en/articles/5332349-commercial-and-personal-usage) | Egyptian Exchange catalog exists, listing reference/fundamental/analysis/fund endpoints. This page alone does not prove current EGX quote coverage or these six funds. | API key and plan/endpoint-specific credits; XCAI page shows Pro+/Venture+. No key supplied. | Personal/commercial usage depends on plan and exchange rights. Current quote timestamp behavior untested for EGX. | Docs verified; **not accepted** as a working EGX price source. Exchange-directory inclusion is insufficient proof. |
| [EODHD exchange API](https://eodhd.com/financial-apis/exchanges-api-list-of-tickers-and-trading-hours) | Documents discovery across supported exchanges, stock/ETF/fund filters. EGX-specific current coverage unverified. | API token required; subscribed plan governs limits. | A commercial data API, but EGX entitlement and timestamp semantics require verification. No key in this app or its URLs. | **Blocked**: no token; no claim of EGX coverage/current responses. |
| [LSEG Lipper Funds API](https://developers.lseg.com/en/api-catalog/refinitiv-data-platform/lipper-funds-API) / [fund data](https://www.lseg.com/en/data-catalogue/funds) | Documented global fund/ETF data. AZG/BFA/T70/CTQ and Egyptian NAV rights/identifiers not confirmed. | RDP access credentials/entitlements required; contract limits. | Provider API is intended for fund-content integration. Valuation dates must be mapped as NAV, never exchange trade time. Actual fund response not available. | **Blocked by entitlement and exact coverage**. No invented NAV adapter. |
| [Gold-API documentation](https://gold-api.com/docs) / [machine-readable reference](https://gold-api.com/llms.txt) / [terms](https://gold-api.com/terms) | Global XAU/USD spot; separate from Egyptian gram prices and EGX fund NAVs. Endpoint `https://api.gold-api.com/price/XAU/USD`. | No key for current prices. Docs advertise no fixed rate cap; terms forbid request spam. Client caches 30 seconds; normal interval >=15 min. | Terms explicitly allow mobile applications. `updatedAt` is provider snapshot time, **not verified exchange execution time**. `symbol=XAU`, `currency=USD`, decimal price required. | Host probe HTTP 200 at `2026-09-27T07:46:27Z`: `4286.200195 USD/oz`, provider time `2026-09-27T07:46:01Z`. Accepted documented adapter. Device result recorded separately below/in DEVICE_ACCEPTANCE.md. No history route used. |
| [Metals.Dev docs](https://metals.dev/docs) / [pricing](https://metals.dev/pricing) / [terms](https://metals.dev/policy/terms) | Global gold spot, configurable currency/unit, bid/ask and other metals. | Secret API key; free plan 100 requests/month, advertised 60-second updates. This quota cannot sustain 15-minute continuous polling. | Response timestamp is data-collection ISO time; require USD and `toz`. Terms explicitly discuss publishing on websites; this app's redistribution/use needs confirmation. | **No adapter activated**: no server credential, unverified mobile rights, no live response to validate. Credentials must stay behind a gateway. |
| [GoldAPI.io](https://www.goldapi.io/) / [terms page](https://www.goldapi.io/kb/tos) | Gold/silver service candidate; exact current response/units not verified in this review. | Account/key and plan limits need verification. | Documentation/terms rendered as JavaScript-only in the research reader; permission and timestamp contract not established. | **Not accepted**. No guessed decoder or key embedded. |

## Retired sources remain disabled

[TradingView terms](https://www.tradingview.com/policies/) restrict non-display and
machine-driven use; its retired screener integration is not restored. SNDUK
(`https://snduk.com/`), Azimut (`https://app.azimut.eg/`), EGXpilot (`https://egxpilot.com/api/stocks/all`) and the ETF website
(`https://www.egx30etf.com/`) remain disabled because no permission and documented
app API contract for the required automated use was verified. No bypass, token
harvesting or anti-bot workaround was attempted. Saved legacy observations remain
readable, with their original timestamps/classification.

## Calendar evidence

The Twelve Data XCAI page identifies Sunday–Thursday and Africa/Cairo, but includes
both 14:15 continuous-trading and 14:30 broader session descriptions. It is not used
as an authoritative holiday feed. Official EGX TradingHours/Holidays URLs could not
be retrieved by the research tool. Therefore holiday/session authority is **blocked**,
not silently inferred from a generic weekday calendar. Existing configurable Cairo
windows, Sunday trading, local holidays and exception tests remain. UI explicitly
says exchange status UNKNOWN until an authoritative source is connected.

## Activation path

Obtain authorization for current EGX quotes/NAVs and the desired display/analytics
usage; confirm exact tickers/types/units and timestamp semantics. Deploy or supply
an HTTPS gateway implementing PROVIDER_CONTRACT.md. Vendor credentials remain
server-side. Configure primary and up to two ordered fallback gateway URLs in the
app. No historical endpoint is needed. For a second gold source, separately verify
its live response and permission, then configure a gateway and its Gold Watch order.
A failing mocked source plus a succeeding mocked source proves failover mechanics,
not that two independent production sources are currently available.
