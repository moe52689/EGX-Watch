# Implementation file map

Changes include the pending 1.2 reliability improvements and the 1.3 monitoring/analytics engine.

## Created

- `app/src/androidTest/java/app/egxwatch/EngineRepositoryTest.kt`
- `app/src/main/java/app/egxwatch/data/AnalyticsRepository.kt`
- `app/src/main/java/app/egxwatch/data/EngineDatabase.kt`
- `app/src/main/java/app/egxwatch/data/EngineJson.kt`
- `app/src/main/java/app/egxwatch/data/FeedParsers.kt`
- `app/src/main/java/app/egxwatch/data/FundDirectory.kt`
- `app/src/main/java/app/egxwatch/data/MarketDataRepository.kt`
- `app/src/main/java/app/egxwatch/domain/Analytics.kt`
- `app/src/main/java/app/egxwatch/domain/EngineMarket.kt`
- `app/src/main/java/app/egxwatch/monitor/Connectivity.kt`
- `app/src/main/java/app/egxwatch/monitor/OpportunityNotificationManager.kt`
- `app/src/main/java/app/egxwatch/ui/EngineScreens.kt`
- `app/src/main/java/app/egxwatch/ui/FeedPresentation.kt`
- `app/src/test/java/app/egxwatch/AnalyticsTest.kt`
- `app/src/test/java/app/egxwatch/EngineContractTest.kt`
- `app/src/test/java/app/egxwatch/FailoverTest.kt`
- `app/src/test/java/app/egxwatch/ReliabilityTest.kt`
- `app/src/test/resources/feeds/etf.html`
- `app/src/test/resources/feeds/tradingview.json`
- `docs/ENGINE_FILES.md`
- `docs/ENGINE_PLAN.md`
- `docs/SECURITY_OPERATIONS.md`

## Modified

- `README.md`
- `app/build.gradle.kts`
- `app/src/androidTest/java/app/egxwatch/LiveFeedTest.kt`
- `app/src/androidTest/java/app/egxwatch/MigrationTest.kt`
- `app/src/androidTest/java/app/egxwatch/RepositoryTest.kt`
- `app/src/androidTest/java/app/egxwatch/UiSmokeTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/app/egxwatch/MainActivity.kt`
- `app/src/main/java/app/egxwatch/WatchApplication.kt`
- `app/src/main/java/app/egxwatch/data/Database.kt`
- `app/src/main/java/app/egxwatch/data/FreePublicProvider.kt`
- `app/src/main/java/app/egxwatch/data/InstrumentCatalog.kt`
- `app/src/main/java/app/egxwatch/data/Providers.kt`
- `app/src/main/java/app/egxwatch/data/PublicFeedClient.kt`
- `app/src/main/java/app/egxwatch/data/WatchRepository.kt`
- `app/src/main/java/app/egxwatch/domain/Market.kt`
- `app/src/main/java/app/egxwatch/monitor/Monitoring.kt`
- `app/src/main/java/app/egxwatch/ui/WatchViewModel.kt`
- `app/src/main/resources/instrument-catalog.json`
- `app/src/test/java/app/egxwatch/FeedCacheTest.kt`
- `docs/FREE_FEEDS.md`
- `docs/PROVIDER_CONTRACT.md`
- `docs/VERIFICATION.md`
- `scripts/update-catalog.ps1`
