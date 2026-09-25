# Forward history, charts and gold — implementation plan (1.4)

Inspected the clean 1.3 repository at 402303a: domain calculations and quality gates,
Room v3 and migrations, quote/history gateway, failover/health, WatchRepository,
AnalyticsRepository, WorkManager and notification managers, Compose/ViewModel screens,
unit/instrumented tests, catalogues/fixtures, build/CI, manifest and operating docs.

Reuse provider validation, failover, WorkManager, watchlists, exact-decimal snapshots,
indicator primitives and opportunity suppression. Refactor AnalyticsRepository to consume
only locally acquired observations: no /history request is necessary or automatic.
Existing externally based analyses must not become local-history claims on upgrade.

Implementation order, with assembleDebug + testDebugUnitTest after every phase:
1. Indexed forward observation storage, monotonic/dedup validation, local session aggregates.
2. Independent indicator requirements and maturity based on samples/sessions/coverage.
3. Adaptive local quantitative analysis and pipeline integration; remove historical dependency.
4. Compose chart infrastructure, bounded window queries and interactive inspection.
5. Semantic financial dashboard, compact watchlist cards and sparklines.
6. Detailed instrument chart/maturity/available-versus-building dashboards.
7. Separate gold adapter/repository/session manager and background collection.
8. Global Gold tab/chart, explicit USD per troy ounce and provider provenance.
9. Persisted gold rules, crossings/movement/extrema/cooldown/dedup and notifications.
10. Unified categorized alert center, read/dismiss state and chart event details.
11. SQL downsampling, retention preserving session aggregates, deletion/reset and encrypted export.
12. First-run explanation, accessible semantic palette, RTL layout verification, UI polish.

Final verification: mocked unit and Room/instrumentation tests, migration/restart,
large-series bounded queries, Android lint, emulator UI and signed version-stamped APK.
Commit/push source and update README/provider/security/schema/test/file documentation.

Data semantics:
- Local observation history begins with monitoring, never fabricated before install.
- Provider session OHLC is not a per-poll candlestick. Sample-derived session candles
  are explicitly labelled observed OHLC, never official exchange daily candles.
- Sampling gaps are visible; observations are not assumed equidistant for daily indicators.
  Session-based MA/RSI/MACD use locally observed session closes with independent gates.
- Source/kind/currency/timestamp-basis changes create separate comparable series.
- Old detailed points compact to session aggregates transactionally; current analytics
  uses those same aggregates so compaction does not silently erase required inputs.
- Gold data/schedule/alerts stay independent of EGX. Gold-API documented free quote API
  is a candidate (terms allow apps); no paid historical endpoint will be required.
- Check a maintained Apache-2.0 Compose chart library version compatible with this app.
- Encrypted export is a user-selected file, password-derived authenticated encryption;
  never place a plaintext database in public storage. Destructive history resets require UI confirmation.
- No chart/indicator/example prices in production. No purchase or new service account.

## Completed checkpoints

All twelve stages passed debug compilation and JVM tests before the following stage. Final validation: 69 JVM tests, 20 API-35 emulator tests, lint with zero errors. See VERIFICATION.md for checksums and limitations.
