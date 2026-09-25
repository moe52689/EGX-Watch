# Security and operations · 1.3.0

No vendor keys, account tokens or LLM credentials are embedded in the APK. The app
stores only public HTTPS gateway URLs, market observations and user monitoring choices.
Provider secrets must stay in a backend secret manager. A private backend requiring
user authentication needs a separate short-lived token exchange; that flow is not
implemented. Android Keystore is not used to pretend a distributed vendor key is safe.

TLS uses normal platform certificate and hostname validation, cleartext is disabled,
redirects are disabled, URL user-info/query tokens/fragments are rejected. Quote URLs
encode identifiers as individual path segments. Gateway bodies are limited to 1 MiB;
connect/read/call limits are 10/15/20 seconds, plus a 22-second repository timeout.
Cancellation cancels the underlying OkHttp request. No trust-all certificates, custom
DNS bypass, remote JavaScript, sensitive logs, analytics SDK or personal-data access.

Each configured provider serializes requests. A timeout, transport/schema failure or
rate limit produces a persisted cooldown: exponential 60 seconds to 1 hour plus 0–25%
jitter, respecting Retry-After up to seven days. Missing instrument/history routes do
not disable the entire provider. Cooldowns survive restart and network changes. Ordered
fallback is attempted for stale responses too. Saved values never become newly timestamped.
No provider means an offline identity directory and clearly unavailable fresh prices.

Price writes reject identity/currency errors, future/regressing timestamps and conflicting
exchange timestamps. Analytics also rejects missing/duplicate candles, nonfinite/nonpositive
prices, inconsistent OHLC, incomparable series, stale histories and >50% discontinuities.
The discontinuity rule intentionally rejects some legitimate unadjusted corporate actions;
a licensed backend should supply comparable adjusted history, never bypass with invented bars.
History completeness depends on the provider's expected-session list. No fabricated holiday
list or source timestamp is supplied. Market-status payloads are not themselves a calendar.

Room transactions reserve alert events and persist dedup state before delivery. Delivery
is at-most-once: process death between reservation and Android delivery can leave an event
unconfirmed, rather than replaying an alert on every restart. Notification intents are
immutable and carry only a security/event identity; they grant no access to credentials.
Android permission/channel blocks remain visible in history. Alerts are heuristic signals,
not trade execution; the application has no order-placement capability.

Data is in application-private storage, with Android backup/device transfer excluded.
5,000 snapshot limit, 1,000 history bars/security, 500 opportunity and 500 price events.
Per-security opportunity suppression is independent of the bounded event list. Removed
instruments' large histories/analyses are cleaned at checks. No unrelated ephemeral data.

WorkManager remains inexact, >=15 minutes, network-constrained and session-gated. No
foreground-service or exact-alarm circumvention. Foreground polling observes lifecycle and
shares the minimum interval guard. Provider cooldowns apply even to manual checks. Status
reports device restriction flags without claiming to know why Android delayed a particular
job. Next time is an estimate. A force-stopped application cannot monitor until reopened.

The debug APK is debuggable and development signed. This is not a penetration-test
certification. Production needs release signing, authenticated backend threat modelling,
monitoring/quotas and physical-device/Doze testing. Source rights must explicitly cover
this application and automated analytics.
