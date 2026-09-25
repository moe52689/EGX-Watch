# EGX monitoring and analytics development plan

Repository inspection completed 2026-09-25: Kotlin domain, Room v2, repository,
HTTP adapters/cache/parsers/catalogue, WorkManager/notifications, Compose/ViewModel,
all test suites/fixtures, manifest/resources, build/CI scripts and documentation.
Preserve pending v1.2 fixes, watchlists, stored observations and baseline alert rules.

## Stages (compile + deterministic unit tests gate each stage)
0. Establish a passing baseline. No destructive database recreation.
1. Add provider-independent snapshots/history, configurable session calendar,
   quality gates, deterministic indicators/scoring/explanation, alert decision rules.
   Test formulas, missing data, calendar boundaries and duplicate/cooldown rules.
2. Add Room v3 migration and bounded snapshots/history/analysis/events/provider health.
   Extend HTTPS gateway with history; ordered failover with persisted backoff/jitter,
   Retry-After, cancellation/timeouts. Integrate fresh-only analytics and session gates.
   Keep legacy price notifications separate from opportunity notifications.
3. Add opportunity details, watchlist summaries, engine settings/status and alert links.
   Test migration/restart and mocked end-to-end flows, lint, emulator UI, build APK.
4. Update provider contract, operations/security/testing docs; stamp 1.3.0/build 4,
   verify signature, commit implementation and deliver APK.

## Decisions and boundaries
- No synthetic production prices, history or AI facts. Mock providers exist only in tests.
- TradingView explicitly prohibits automated collection/non-display use; disable that
  runtime source and its refresh script. Other undocumented public endpoints are not
  assumed licensed for analytics. Production uses an explicitly configured HTTPS gateway;
  bundled identities and last-known observations remain available offline.
- Current gateway has no credentials, historical bars or exchange calendar configured.
  Document exact extended routes. Indicators require complete, comparable daily bars;
  insufficient/stale/unverified histories cannot generate opportunities.
- Local calendar settings include holiday dates and exceptional date windows. Clock
  schedule is labelled configured, not authoritative exchange state. No invented holidays.
- WorkManager remains >=15 minutes and inexact, checks calendar before network access.
  No foreground-service workaround. Backend is the future continuous-monitoring boundary.
- Manual DI remains: it is already simple and functioning. No unrelated rewrite.
- Explainable deterministic scores are heuristic, not calibrated probabilities or advice.
  Missing fundamental/news/context features remain absent. No external LLM or personal data.
- Existing database and UI are extended incrementally; retention and alert state survive restart.

## Execution result
Baseline and stages 1–3 compiled and passed their unit-test gates. Final verification: 55 JVM tests, 15 emulator tests, zero lint errors. Room migration/restart and UI navigation passed. Debug APK 1.3.0/build 4 signed, installed and launched. Live activation remains dependent on an authorized gateway; no mock data enters production.
