# EGX Watch

A native Kotlin Android market monitor for Egyptian stocks, ETFs and investment
funds. Material 3, Jetpack Compose, ViewModel/StateFlow, Room and WorkManager.
Android 8.0 (API 26) or newer. Dark, light and system themes.

## Install the debug APK

The local deliverable is `artifacts/EGX-Watch-debug.apk`. On GitHub, open the
**Actions → Android build and tests** successful run and download **EGX-Watch-debug**,
then unzip the artifact. Build artifacts are intentionally not committed to Git.

1. Copy the APK to your Android phone and open it.
2. Allow “Install unknown apps” for the file manager/browser you use, if prompted.
3. Install **EGX Watch**. This is a development-signed debug build.
4. Open the app and tap **Check now** (refresh icon). Free public feeds are enabled
   by default; background monitoring starts paused.
5. In **Settings**, allow notifications, choose monitoring days/times and interval,
   enable background monitoring, then tap **Save settings**.

With USB debugging enabled, installation can also be done with:

```sh
adb install -r artifacts/EGX-Watch-debug.apk
adb shell am start -n app.egxwatch/.MainActivity
```

Updating an existing app requires the same debug signing key. A build from a
different machine/CI may need the old app uninstalled first, which removes local
watchlists and history. No provider credentials are needed for the free feeds.

## What is included

- Multiple named watchlists with validated additions and removal; stocks, ETFs and
  funds may be mixed in a single list. Each shows ticker, full name and type.
- Initial watchlist: **CCAP, BINV, T70, CTQ, AZG, BFA**. Discover also includes
  EGX30ETF. A custom gateway can return any validated instruments, not just these.
- Public free feeds: indicative CCAP/BINV snapshots; dated AZG/T70/CTQ/BFA fund NAVs.
  [Coverage, sources and limitations](docs/FREE_FEEDS.md) are explicit. No random,
  demo or hard-coded prices are used at runtime.
- Separate **LIVE**, **DELAYED**, **INDICATIVE**, and **NAV** labels. Unknown
  exchange timing is never advertised as live. Date-only NAVs do not claim a time.
  Stale values keep their source timestamp and an older-observation label.
- WorkManager intervals: 15 min, 30 min, 1 hour, 2 hours, or custom 15–525600 min.
  Monitoring days/windows use Africa/Cairo, including DST. Equal start/end means
  all day; overnight windows belong to their starting day. Times are HH:mm.
- Change notifications show ticker, full name, current price/NAV, previous value,
  signed absolute and percentage changes, source and data timestamp/date.
- Global and per-instrument absolute/percentage thresholds; either threshold
  triggers. Blank per-instrument rules inherit global rules. Comparison is against
  the **previous successful check**, not a daily close or last alerted value.
- Optional notification on every successful check, including unchanged values;
  this overrides thresholds. Initial values otherwise establish a quiet baseline.
  Percentage is N/A when the previous value is zero or unavailable.
- Latest 500 alert records persist locally, including blocked-notification status.
  Offline/provider errors retain the last real value and never generate a fake
  change. Duplicate instruments across lists produce one notification per check.
- Market status from a connected gateway; Unknown for free feeds because they do
  not verify the exchange calendar. No fabricated holiday/open-status claims.

Android may delay periodic work due to Doze, battery restrictions or no network;
15 minutes is a minimum interval, **not an exact alarm**. Force-stopping the app
prevents background execution until it is opened again. Manual checks ignore
monitoring windows and can generate alerts using the same rules.

## Connect a different provider

Enter a public HTTPS gateway base URL under Settings, test it, and save. A gateway
takes priority over free feeds. To restore free feeds, clear the URL and enable
the free-feeds switch. Switching sources resets baselines to avoid false changes.

See the [provider contract](docs/PROVIDER_CONTRACT.md). All vendor credentials stay
on your gateway server. The app rejects URLs containing embedded credentials or
query tokens; it contains no API keys. Android cleartext traffic is disabled.
Only the selected provider receives search queries and requested instrument IDs.
No analytics SDK is included. Local data and history are not cloud-synced.

## Build and test

Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0. Gradle 8.13
is pinned in the wrapper. Set `ANDROID_HOME` or an ignored `local.properties`
with `sdk.dir`. Open the root in Android Studio, or run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
# Windows: .\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

Run Room/repository and Compose UI tests on a connected API 26+ device/emulator:

```sh
./gradlew connectedDebugAndroidTest
```

Unit tests cover monetary precision, inclusive thresholds, zero baselines,
overnight/day boundaries, Cairo timezone, provider identity and timestamp
validation, actual public-feed response parsing and schema/currency failures.
Instrumented tests cover atomic baselines/history, duplicate watchlists, errors,
old observations, provider changes, threshold overrides and navigation.

Manual acceptance checklist:

1. Refresh the initial six instruments on a network; verify source/date labels.
2. Search CCAP and try adding it twice to one list; duplicate addition is rejected.
3. Create a second watchlist and add an ETF/fund alongside a stock. Unknown symbols
   must show no matches; configure a gateway to validate symbols outside the free directory.
4. Open details, set a positive threshold, save it, then return to the watchlist.
5. Try custom interval 14, empty days or invalid time: saving must be rejected.
6. Allow notifications and enable every-check mode. Refresh twice; history should
   record successful checks even when a NAV has not changed. Revoke notification
   permission and repeat: history records the blocked delivery.
7. Enable monitoring with an active Cairo window, close the app normally, and allow
   at least one chosen interval plus possible Android scheduling delay.
8. Turn network access off and refresh. Existing values retain their timestamps;
   errors appear without zeroing values or producing movement alerts.
9. Restart the app and switch light/dark themes; watchlists, rules and history persist.

## Structure

`domain/` owns provider-independent types, validation, schedule and alert rules.
`data/` owns Room persistence, public/gateway adapters and serialized monitoring.
`monitor/` owns WorkManager scheduling and Android notifications. The Compose UI
observes database flows through `WatchViewModel`. Synthetic tests are isolated
from production providers. The existing GPL-3.0 repository license is retained.
