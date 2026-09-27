# Provider acceptance plan · v1.4.1

Inspected v1.4.0 provider/repository/UI code and schema v5 before editing. Use one additive Room v5→v6 column for gold provider order; additive quote JSON fields require no destructive migration. Existing gold/EGX provider URLs and watchlists remain.

1. Research source permissions, current docs, coverage, timestamps and access; record matrix and distinguish blocked entitlement from working data.
2. Move freshness and saved-timestamp rejection into each ordered provider attempt. Validate gateway response identity, isolate category cooldowns, expose per-instrument diagnostics and persist provider health and compare eligible sources for material disagreement. Keep first validated result; never accept stale substitutes.
3. Explicit unconfigured setup state, single-flight manual refresh and diagnostics; Gold Watch independent provider ordering and status. Maintain configurable Cairo calendar without inventing holiday authority.
4. Focused JVM/Room tests, emulator suite, lint and signed v1.4.1 build6 APK.
5. Inspect installed phone package/certificate, preserve baseline database evidence, update via adb install -r, confirm watchlist/settings and exercise both refresh flows. No uninstall or test database resets on the phone. Report EGX operational NO-GO until a permitted current EGX quote actually validates on device.
