# Verification

Version 1.1.0 was verified on 21 September 2026 with JDK 17, Gradle 8.13, Android SDK 35
and Android Gradle Plugin 8.11.1.

| Check | Result |
|---|---|
| Debug APK assembly | Passed |
| JVM unit tests | 34 passed |
| Android emulator tests (API 35, x86_64) | 12 passed, including opt-in live-source checks |
| Android lint | No errors; dependency/target-version and tooling warnings remain |
| APK signature verification | Passed, APK Signature Scheme v2 |
| APK installation and launch | Passed on Android 15 emulator |
| Public feeds | Retrieved actual fund NAVs and indicative stock snapshots in the installed app |
| Version 1 → 2 Room migration | Existing watchlist, saved price, source date and settings retained |
| Fresh-install watchlist and picker | Empty watchlist, browse/search, validated addition and disabled duplicate button passed |
| Public response cache | Survives client restart; malformed/failed refresh retains original content and timestamp |

Emulator tests exercise Room transactions and persistence, first-value baselines,
changed/unchanged values, error retention, timestamp regressions, duplicate
additions, duplicate instruments across lists, provider switching, per-instrument
thresholds, date-only NAV corrections, cached-feed alert suppression and Compose
screen navigation. No preset instruments are inserted on new installations.

The live Android check retrieved valid observations for CCAP, BINV, COMI, EGBE
(USD), T70, CTQ, AZG, BFA and an additional fund. All three sources passed actual
quote/NAV parsing. Directory refresh returned 296 stocks, 153 funds and one ETF
(including the issuer reference entry). The separate stock response contained
246 verified stock matches; remaining directory entries are not promised quotes.
Testing detected Azimut's AZG slug revision from `az-gold-2` to `az-gold-1`; the
adapter now checks stable fund ID 16, the gold slug family, NAV fund ID and currency.
This regression is covered by a unit test.

The public-feed parsers are tested against captured real excerpts, separate from
production data. Live checks were made against the public sources documented in
[FREE_FEEDS.md](FREE_FEEDS.md); their future availability is not guaranteed.

No physical device or prolonged battery/Doze scenario was tested. WorkManager
execution timing depends on Android. Follow the README’s background-monitoring
acceptance checklist on the intended phone. Live exchange entitlements and a
custom production gateway are not supplied; the built-in stock feed is explicitly
indicative and the free market status remains Unknown.

The delivered APK checksum is in `artifacts/SHA256SUMS.txt`. Local test reports
are under `app/build/reports/`. GitHub Actions rebuilds source and publishes its
own debug APK artifact after a successful pushed run; that APK uses a different
debug signing key from the locally delivered build.
