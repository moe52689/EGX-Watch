# Data-source permissions and coverage · 1.3.0

The prior prototype used publicly accessible website endpoints. Public accessibility
is not authorization for automated extraction, analytics or redistribution.

TradingView's [terms](https://www.tradingview.com/policies/) and
[automated collection guidance](https://www.tradingview.com/support/solutions/43000674726-why-is-my-account-banned-due-to-suspicious-activity/)
prohibit the automated collection/non-display use needed here. The production selector
cannot activate the former public adapter; its HTTP client specifically rejects the
TradingView screener. The catalogue-refresh script now requires an authorized gateway.

SNDUK, Azimut and ETF website parsers remain for regression coverage of previously
stored data semantics, but are not selected by the production repository. No permission
for their use in automated opportunity analytics is presumed. No website source is
silently substituted when the gateway is absent or fails. EGXpilot's retired source
also remains inactive. Captured test fixtures are not APK assets.

The bundled, dated identity metadata remains available offline: 296 stocks, 167 funds
and one ETF in the current union. Saved identities are not a real-time listing register,
and investment funds, exchange-listed securities and broker product menus are distinct
universes. Only a suitably licensed provider can establish broader current coverage.
Existing prices/NAVs remain displayed with original provenance/date; no new quotes are
fetched from restricted sources. Update the directory via an authorized gateway and
review the identity-only diff before committing it.

Source policy review: 25 September 2026. Live data activation requires the gateway
contract and appropriate upstream entitlements described in PROVIDER_CONTRACT.md.
