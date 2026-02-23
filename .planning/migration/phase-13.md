# Phase 13: E2E Integration + Launch Polish

**What:** Full system integration testing, chaos CI gate, performance soak validation, accessibility audit, privacy feature implementation, and app lifecycle features. Everything must work together.

**Depends on:** Phases 9 (chaos infrastructure, multi-pack), 11 (all overlay UI), 12 (benchmark infrastructure)

## Integration Testing

- Full E2E: launch → load layout → bind data → render widgets → edit mode → add/remove/resize → theme switch → settings → widget settings. Semantics verification at each step: `assertWidgetRendered` after add, `assertWidgetNotRendered` after remove, bounds change after resize
- CI chaos gate: deterministic `seed = 42` chaos profile → `assertChaosCorrelation()` passes. Semantics verification: fallback UI rendered for failed providers, no "NaN" text in widget subtrees
- CI diagnostic artifact collection: `adb pull` diagnostic snapshots + `list-diagnostics` + `dump-health` + `dump-semantics` as CI artifacts on failure
- Agentic debug loop validation: inject fault → detect via `list-diagnostics` → investigate via `diagnose-crash` + `query-semantics` → verify via `dump-health` + `assertWidgetRendered` after fix
- Multi-pack validation: essentials + themes + demo all loaded simultaneously — no Hilt binding conflicts, no KSP annotation collisions, no R8 rule conflicts

## Performance soak (NF11, NF37)

- Battery drain measurement: 1-hour screen-on soak with 12 widgets → target < 5% battery/hour (NF11 — exact threshold TBD via baseline from Phase 12 benchmarks)
- Background battery: app backgrounded for 1 hour → near-zero drain. Verify all sensor `callbackFlow`s properly `awaitClose` and unregister (NF37)
- Memory: heap dump after 30-min session — no leaked `Activity`, `ViewModel`, or `CoroutineScope`. LeakCanary in debug builds catches these earlier, but explicit verification here

## Data privacy feature (NF-P5)

- **Export My Data:** JSON export of all user data (layouts, settings, profiles, paired devices). Accessible from Settings → Data & Privacy. Format documented. Implementation: inject all DataStore repositories (`LayoutRepository`, `UserPreferencesRepository`, `ProviderSettingsStore`, `PairedDeviceStore`, `WidgetStyleStore`), serialize each current value to JSON via kotlinx.serialization, write to user-selected file via `ActivityResultContracts.CreateDocument`. No server-side data — everything is local Proto/Preferences DataStore
- **NF-P3 (PDPA compliance) verification:** Analytics consent flow works end-to-end (consent dialog → opt-in → events fire → opt-out → events stop). Privacy policy URL reachable. "Delete All Data" (F14.4, implemented in Phase 10) verified to clear all stores

### NF-P4: Data Export + Firebase Deletion
"Export My Data" produces a ZIP of all Proto DataStore contents + user preferences. Firebase Analytics ID reset via `FirebaseAnalytics.resetAnalyticsData()`. Verified as part of the "Delete All Data" flow (delivered in Phase 10) — Phase 13 validates the end-to-end flow including Firebase cleanup.

## Accessibility audit (NF30, NF32, NF33, NF39, NF40)

- **NF30 (WCAG AA contrast):** Systematic audit of all themes — critical text (speed, time, speed limit) meets 4.5:1 contrast ratio against theme backgrounds. Automated: extract theme colors + measure contrast ratios programmatically for all 24 themes (2 free + 22 premium)
- **NF32 (TalkBack):** Settings and setup flow TalkBack traversal test — all interactive elements have `contentDescription`, focus order is logical, no focus traps. Dashboard rendering explicitly excluded per requirement
- **NF33 (font scale):** Settings UI renders correctly at system font scales 1.0x through 2.0x (Compose `sp` units handle this, but verify no layout overflow or clipping)
- **NF39 (reduced motion):** When `Settings.Global.ANIMATOR_DURATION_SCALE == 0`: disable wiggle animation in edit mode, replace spring transitions with instant, disable pixel-shift (deferred). Glow remains. Verify via `adb shell settings put global animator_duration_scale 0`
- **NF40 (color-blind safety):** Speed limit warnings use color + pulsing border + warning icon (not color alone). Free themes verified for deuteranopia contrast via simulated color filter

## App lifecycle (NF-L2, NF-L3)

- **NF-L2 (In-app updates):** Google Play In-App Updates API via `com.google.android.play:app-update` library. IMMEDIATE flow for critical bugs (version code flagged in Play Console), FLEXIBLE flow for feature updates. Add library to version catalog → implement `AppUpdateManager` check in `:app` `MainActivity.onResume()`
- **NF-L3 (In-app review):** Google Play In-App Review API via `com.google.android.play:review`. Trigger conditions: 3+ sessions AND 1+ layout customization AND no crash in current session. Frequency cap: once per 90 days (tracked in `UserPreferencesRepository`). Non-intrusive. Add library to version catalog → implement `ReviewManager` trigger in `:app`

## Localization validation (NF-I1, NF-I2)

- **NF-I1:** Run Android lint `HardcodedText` check across all modules — zero violations. Grep for string literals in `@Composable` functions as secondary check
- **NF-I2:** Widget data formatting uses locale-aware APIs (`NumberFormat`, `DateTimeFormatter`) — verify decimal separators and unit labels respect `Locale.getDefault()` across essentials pack renderers

### NF-D2: Speed Accuracy Disclaimer (Legal Checklist)
ToS includes speed accuracy disclaimer: GPS-derived speed is approximate, not a certified speedometer. Legal review checklist item — verify wording with counsel before launch.

**Tests:**
- E2E journey test: full user flow from launch through all major interactions with semantics assertions
- Chaos correlation test: `seed=42` deterministic fault injection → diagnostic snapshot correlation
- Multi-pack Hilt binding test: all 3 packs loaded, `Set<WidgetRenderer>` and `Set<DataProvider<*>>` contain expected counts
- Export My Data: round-trip test — export → parse exported JSON → verify all DataStore keys present
- In-app update (NF-L2): mock `AppUpdateInfo` with IMMEDIATE priority → IMMEDIATE flow triggered, FLEXIBLE priority → FLEXIBLE flow triggered, no update available → no flow. Version code threshold logic: critical bug flag (hardcoded version range) → IMMEDIATE, all other updates → FLEXIBLE
- In-app review trigger conditions (NF-L3): 3+ sessions AND 1+ layout customization AND no crash → review prompt shown. Fewer than 3 sessions → not shown. 90-day frequency cap → second trigger within 90 days suppressed. All conditions tracked in `UserPreferencesRepository`
- Accessibility: programmatic contrast ratio: 4.5:1 minimum for normal text (<18sp), 3:1 minimum for large text (≥18sp bold or ≥24sp). Checked for all 24 themes against: speed value, clock time, date text, battery percentage, speed limit value. Failing theme+text combination logged with exact ratio
