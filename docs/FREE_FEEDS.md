# Free public feed coverage

Rechecked on 21 September 2026. These public website feeds need no API keys.
They are not a guaranteed or exchange-licensed real-time service. No authentication
bypass, cookie harvesting or rate-limit evasion is used. Sources can change or fail.

| Coverage | Source | Treatment |
|---|---|---|
| 296 EGX stock identities | [TradingView Egypt stock directory](https://www.tradingview.com/markets/stocks-egypt/market-movers-all-stocks/) | Names, tickers, exchange and currencies; identity metadata only. The public Egypt screener supplies the complete returned catalogue. |
| 246 matching stocks in the checked response | [EGXpilot public API](https://egxpilot.com/developers.html) | `LastPrice`, explicitly INDICATIVE. `CreatedAt` is provider snapshot time, never exchange trade time. Delay unknown. |
| 153 fund identities and published NAV rows | [SNDUK public fund table](https://snduk.com/eg/page/mutual-funds-prices-today?lang=en) | Match by exact fund-page slug; validate currency, numeric NAV and valuation date. Dates differ between funds. |
| AZG issuer NAV | [Azimut Egypt](https://azimut.eg/funds) | Public fund list, stable ID 16 and the `az-gold` slug family. Compare available Azimut/SNDUK observations and select the latest valuation date; prefer issuer on equal dates. |
| EGX30ETF identity | [Issuer](https://www.egx30etf.com/) | Selectable; price unavailable unless returned by the current feed or a configured gateway. |
| Additional coverage | Configurable HTTPS gateway | Dynamic instrument validation and quotes/NAVs through the documented provider interface. |

The checked EGXpilot response contains 272 rows, including indices, non-EGX symbols
and names not matched to the verified catalogue. Only matching EGX identities are
used. Currency comes from the verified identity, including USD shares such as
EGBE, VLMR and FAITA; the stock response itself does not publish currency. This
does not verify exchange entitlements or real-time timing. An absent/invalid price
is unavailable, never zero-filled or fabricated. Coverage counts describe the
checked responses, not a promise that every listed instrument always has a quote.

The bundled directory contains metadata only and works offline. Discover can
refresh it using `https://scanner.tradingview.com/egypt/scan` (EGX identity columns)
and the SNDUK table. `scripts/update-catalog.ps1` reproduces the bundled metadata.
For funds without a confirmed trading ticker, the app explicitly labels the
upstream page slug as a **fund provider code** instead of inventing a ticker.
Known aliases T70, CTQ, AZG and BFA retain their existing identities.

The Azimut website uses `https://app.azimut.eg/api/fund/list?size=100&web=true`.
EGXpilot quotes use `https://egxpilot.com/api/stocks/all`. Public responses are
validated before caching, reused for 60 seconds, and saved in private app storage.
On refresh failure, the last valid response may be reused with a visible warning
and its original data date. Failed requests are suppressed for 30 seconds; manual
connection diagnostics bypass that suppression. A failed or malformed response
cannot overwrite the cached valid response. Android may remove all app data on
uninstall; persistence here means across ordinary restarts and upgrades.

Room also retains each selected instrument's last successful observation. Changing
providers invalidates comparison baselines but preserves display values. Older
observations cannot replace newer ones. Date-only NAV corrections may replace a
value for the same valuation date, since the source publishes no intraday time.
Exchange/snapshot observations with conflicting values at the same timestamp are
rejected. Cached responses with network warnings do not create price alerts.

Settings → Test connection downloads and parses each actual source, showing
Connected, saved-feed fallback or failure separately. New additions fetch a value
immediately; opening the app refreshes selected instruments. Background checks
remain opt-in with intervals of at least 15 minutes. Free market status is Unknown.

Historical response excerpts live only under `src/test/resources` and are never
used as runtime prices. Live instrumentation checks are explicitly opt-in, so
ordinary builds do not depend on upstream uptime. Source identity references also
include [CCAP](https://www.arabfinance.com/en/Home/CompanyProfile/CCAP),
[BINV](https://www.arabfinance.com/en/Home/CompanyProfile/BINV), and the
[Thndr fund directory](https://support.thndr.app/en/articles/651226-mutual-fund-cutoff-times).
