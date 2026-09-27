# Public ticker picker · v1.4.2 build 7

The requested commit `2bd4eb0` was not present after fetching origin; fetching that
revision returned `could not find remote ref`. Implemented directly on `f531b22`.

## Change

One Settings → Data connection picker offers:

- EGX: https://www.egx.com.eg/en/prices.aspx
- Mubasher: https://english.mubasher.info/markets/EGX/
- TradingView: https://www.tradingview.com/markets/stocks-egypt/
- Custom authorized gateway: explains the existing HTTPS field immediately below.

Public choices dispatch an external browser URL only. The picker states that pages
do not supply in-app quotes or alerts and do not change the gateway or refresh saved
prices. It makes no data requests, scrapes nothing and does not write preferences or
market observations. Selecting Custom does not launch a browser. Missing browser
errors are displayed without crashing. Selection survives UI recreation, not a new
installation/session; it is a shortcut, not a persisted data-provider setting.

No Room migration or change to pricing, notifications, provider validation or cache
freshness. Updated versionName 1.4.2, versionCode 7 (installed version was 6).

## Verification

- `assembleDebug testDebugUnitTest lintDebug`: successful; 77 unit tests, zero lint errors.
- Focused `TickerWebsitePickerTest`: two Android emulator tests passed. Verified all
  three exact URLs, Custom with no browser action, clear browser-only copy and missing
  browser handling. No tests that clear data were run on the physical phone.
- Emulator closed before deployment. `adb devices`: exactly one authorized phone,
  `dd607155`; explicit device selector used for installation.
- APK package `app.egxwatch`, versionName `1.4.2`, versionCode `7`.
- Installed APK and new APK verified with apksigner. Matching certificate SHA-256:
  `a26df6b9dfb6a6fbf53a9bc0b56309b2cd17e7bbe1512209936c68ecf82d825f`.
- `adb install -r`: Success. MainActivity launched; installed version checked.
- Pre/post database comparison: existing one-instrument CCAP watchlist, full instrument
  row, settings, gold settings and collection metadata unchanged. No uninstall/reset.
- Physical Settings UI hierarchy contains the picker, explanatory text and gateway field.
  Browser launch by tapping is pending user verification: device policy rejects ADB
  touch injection with `INJECT_EVENTS` SecurityException. Emulator routing tests passed.
- No configured/verified authorized EGX feed; live EGX remains unavailable.

## Artifact

`D:\Codex\EGX-Watch\artifacts\EGX-Watch-v1.4.2-build7-2026-09-27-debug.apk`

SHA-256: `b50a576fdbfbbe99fa48a7e00469b7263b80e3169d805f106730106751b21a39`

Debug signing only; keep the existing local keystore for future in-place updates.
Build/install instructions remain in README. The prior uncommitted device acceptance
note was preserved separately from this change.
