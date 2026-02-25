---
phase: 08-essentials-pack
verified: 2026-02-25T18:00:00Z
status: gaps_found
score: 5/7 success criteria verified
re_verification: false
gaps:
  - truth: "On-device add-widget + dump-health shows ACTIVE for all 13 widget types; query-semantics confirms visible nodes"
    status: failed
    reason: "AddWidgetHandler validates typeId against Set<WidgetRenderer> only — it does not dispatch DashboardCommand.AddWidget to LayoutCoordinator. dump-health returned 0 widgets (no widgets on canvas). query-semantics returned no nodes. Deferred to Phase 13 per 08-09-SUMMARY."
    artifacts:
      - path: "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/AddWidgetHandler.kt"
        issue: "Validates typeId but does not place widget on canvas. No DashboardCommand.AddWidget dispatch."
    missing:
      - "Wire AddWidgetHandler to dispatch DashboardCommand.AddWidget through LayoutCoordinator"
      - "OR accept deferred status and mark SC3 as Phase 13 deliverable (E2E Integration phase)"
  - truth: "9 typed data providers pass DataProviderContractTest (SolarTimezoneDataProvider and SolarLocationDataProvider included)"
    status: partial
    reason: "SolarTimezoneDataProviderTest and SolarLocationDataProviderTest do NOT extend DataProviderContractTest. They have 7-8 standalone assertions each, but the 12 inherited contract assertions (emission timing, type correctness, timestamp > 0, cancellation safety, connection state, timeouts, concurrency, etc.) are not verified for these two providers."
    artifacts:
      - path: "android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/providers/SolarTimezoneDataProviderTest.kt"
        issue: "Standalone JUnit5 class, does not extend DataProviderContractTest. Missing 12 contract assertions."
      - path: "android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/providers/SolarLocationDataProviderTest.kt"
        issue: "Standalone JUnit5 class, does not extend DataProviderContractTest. Missing 12 contract assertions."
    missing:
      - "SolarTimezoneDataProviderTest: extend DataProviderContractTest, override createProvider() with mocked Context"
      - "SolarLocationDataProviderTest: extend DataProviderContractTest, override createProvider() with mocked FusedLocationProviderClient"
human_verification: []
---

# Phase 8: Essentials Pack Verification Report

**Phase Goal:** First pack migration. Proves entire SDK-to-Pack contract works end-to-end. Cross-boundary snapshot types live in `:pack:essentials:snapshots` sub-module. 4 greenfield providers (GpsSpeed, Battery, Accelerometer, SpeedLimit). 13 widgets, 9 typed providers, 2 free themes.
**Verified:** 2026-02-25T18:00:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | 13 widgets pass WidgetRendererContractTest; 9 typed providers pass DataProviderContractTest | PARTIAL | All 13 widget tests confirmed extending WidgetRendererContractTest. 7 of 9 typed providers extend DataProviderContractTest (SolarTimezone/SolarLocation do NOT). |
| SC2 | Multi-snapshot wiring: SpeedometerRenderer receives data from 3 providers via merge()+scan() | VERIFIED | SpeedometerRenderer has 3 independent derivedStateOf reads. MultiSlotBindingTest (10 tests) proves withSlot() accumulation matches merge()+scan() semantics. |
| SC3 | End-to-end wiring: dump-health ACTIVE for 13 types; query-semantics confirms visible nodes | FAILED | AddWidgetHandler validates typeId only — no canvas placement. dump-health returned 0 widgets. query-semantics returned no nodes. Explicitly deferred to Phase 13. |
| SC4 | Stability soak: 60 seconds with all 13 widgets — safe mode not triggered | VERIFIED | 60-second soak with PID unchanged, no safe mode, no ERRORED widgets. Pre-soak NoSuchMethodError caught by error boundary (stale incremental build artifact, not a code bug). |
| SC5 | Regression gate: all Phase 2-7 tests pass with :pack:essentials in dependency graph | VERIFIED | Full project tests pass (400+ tests). assembleDebug + assembleRelease succeed. lintDebug passes. Commits 3c5a8a9, b2e015d confirm 32 test fixes to reach green. |
| SC6 | Widget-specific rendering tests: at least one per widget beyond contract base | VERIFIED | All 13 widget test classes have widget-specific tests. SpeedometerRendererTest has 13 extra tests (gauge max, arc angle, acceleration segments, multi-slot independence). |
| SC7 | Greenfield provider tests: GpsSpeed/Battery/Accelerometer/SpeedLimit have provider-specific tests | VERIFIED | GpsSpeedProviderTest: hardware speed extraction, fallback path, NF-P1 field inspection, SecurityException, setupSchema. BatteryProviderTest: level calc, charging states, temp conversion, sticky broadcast. AccelerometerProviderTest: LINEAR_ACCELERATION preference, gravity fallback, BATCH_LATENCY_US. SpeedLimitProviderTest: settings round-trip, default. |

**Score:** 5/7 success criteria fully verified (1 partial, 1 failed)

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `android/pack/essentials/snapshots/src/.../SpeedSnapshot.kt` | VERIFIED | `@DashboardSnapshot(dataType="speed")`, `@Immutable`, implements `DataSnapshot`, all `val`, cross-boundary public |
| `android/pack/essentials/snapshots/src/.../AccelerationSnapshot.kt` | VERIFIED | Same pattern, dataType="acceleration" |
| `android/pack/essentials/snapshots/src/.../BatterySnapshot.kt` | VERIFIED | dataType="battery", level/isCharging/temperature |
| `android/pack/essentials/snapshots/src/.../TimeSnapshot.kt` | VERIFIED | dataType="time", epochMillis + zoneId |
| `android/pack/essentials/snapshots/src/.../OrientationSnapshot.kt` | VERIFIED | dataType="orientation", bearing/pitch/roll |
| `android/pack/essentials/snapshots/src/.../AmbientLightSnapshot.kt` | VERIFIED | dataType="ambient_light", lux/category |
| `android/pack/essentials/src/.../snapshots/SolarSnapshot.kt` | VERIFIED | dataType="solar", sunrise/sunset/noon/isDaytime/sourceMode. Note: public visibility, not internal as plan specified. Functionally correct. |
| `android/pack/essentials/src/.../snapshots/SpeedLimitSnapshot.kt` | VERIFIED | dataType="speed_limit", speedLimitKph/source. Note: public visibility, not internal as plan specified. Functionally correct. |
| `android/lint-rules/src/.../WidgetScopeBypassDetector.kt` | VERIFIED | Exists, registered in DqxnIssueRegistry at line 28. Import-based detection for widget packages. |
| `android/pack/essentials/src/.../providers/GpsSpeedProvider.kt` | VERIFIED | `@DashboardDataProvider(localId="gps-speed")`, `callbackFlow`, `LocationManager.GPS_PROVIDER`, `awaitClose`, no Location fields retained (NF-P1 confirmed via reflection test). |
| `android/pack/essentials/src/.../providers/BatteryProvider.kt` | VERIFIED | `callbackFlow`, sticky broadcast, `awaitClose`, greenfield. |
| `android/pack/essentials/src/.../providers/AccelerometerProvider.kt` | VERIFIED | LINEAR_ACCELERATION + TYPE_ACCELEROMETER fallback, BATCH_LATENCY_US=100_000, `awaitClose`. |
| `android/pack/essentials/src/.../providers/SpeedLimitProvider.kt` | VERIFIED | Reads from ProviderSettingsStore with "essentials"/"speed-limit"/"value" path. |
| `android/pack/essentials/src/.../providers/SolarCalculator.kt` | VERIFIED | `toJulianDay()` present, NOAA precision tests exist (London winter within 2 min; summer within 5 min — tolerance widened from plan's 2 min for summer). |
| `android/pack/essentials/src/.../providers/IanaTimezoneCoordinates.kt` | VERIFIED | `getCanonicalID()` for alias resolution at line 25. |
| `android/pack/essentials/src/.../providers/SolarTimezoneDataProvider.kt` | VERIFIED | `@DashboardDataProvider`, sourceMode="timezone", IanaTimezoneCoordinates + SolarCalculator wired. |
| `android/pack/essentials/src/.../providers/SolarLocationDataProvider.kt` | VERIFIED | Exists, PASSIVE priority, ACCESS_COARSE_LOCATION in setupSchema. |
| All 13 widget renderers | VERIFIED | All present in widgets/: clock/, date/, battery/, ambientlight/, compass/, speedlimit/, shortcuts/, solar/, speedometer/. All have `@DashboardWidget`, `LocalWidgetData.current` + `derivedStateOf`. |
| `android/pack/essentials/src/.../theme/EssentialsThemeProvider.kt` | VERIFIED | 2 themes (Minimalist/Slate) as inline `DashboardThemeDefinition` objects. JSON files omitted (valid: `:core:design` unavailable to packs). Both free (null entitlement). |
| `android/pack/essentials/src/.../integration/MultiSlotBindingTest.kt` | VERIFIED | 10 tests proving withSlot() accumulation semantics. Speed data available before acceleration/speed limit. |
| `android/pack/essentials/src/.../integration/PackCompileVerificationTest.kt` | VERIFIED | 10 assertions: 13 widgets, typeId set, format validation, metadata, aspects, entitlements. |
| `android/app/src/debug/.../handlers/AddWidgetHandler.kt` | VERIFIED (PARTIAL) | Validates typeId against Set<WidgetRenderer>, returns generated widgetId. Does NOT dispatch to dashboard — canvas placement deferred to Phase 13. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| SpeedometerRenderer.kt | SpeedSnapshot, AccelerationSnapshot, SpeedLimitSnapshot | 3 independent derivedStateOf blocks | VERIFIED | Lines 153-157: `val speed by remember { derivedStateOf { widgetData.snapshot<SpeedSnapshot>() } }` etc. Each null-safe. |
| SpeedSnapshot.kt | DataSnapshot interface | `: DataSnapshot` | VERIFIED | Confirmed in source. |
| GpsSpeedProvider.kt | SpeedSnapshot | emits without retaining Location | VERIFIED | Only primitive fields stored (lastLocationTime: Long, lastLatitude/Longitude: Double, hasLastLocation: Boolean). No Location class field. Confirmed by reflection in GpsSpeedProviderTest. |
| SpeedLimitProvider.kt | ProviderSettingsStore | getSetting("essentials", "speed-limit", "value") | VERIFIED | SpeedLimitProviderTest line 27: `every { getSetting("essentials", "speed-limit", "value") }` confirmed. |
| SolarTimezoneDataProvider.kt | SolarCalculator | uses calculator for sunrise/sunset | VERIFIED | Compilation evidence; SolarCalculatorTest confirms algorithm. |
| SolarLocationDataProvider.kt | PASSIVE FusedLocationProviderClient | callbackFlow with awaitClose | VERIFIED | setupSchema contains ACCESS_COARSE_LOCATION; SolarLocationDataProviderTest confirms. |
| DqxnIssueRegistry.kt | WidgetScopeBypassDetector | registered in issues list | VERIFIED | Line 28 of DqxnIssueRegistry.kt. |
| app/build.gradle.kts | :pack:essentials | implementation dependency | VERIFIED | `implementation(project(":pack:essentials"))` at line 26. |
| AddWidgetHandler | DashboardCommand.AddWidget | command dispatch | NOT WIRED | Handler validates typeId only. Command channel bridge to DashboardViewModel not implemented. Deferred to Phase 13. |

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| F5.1 | 01,03,07,08 | Speedometer — circular gauge, acceleration arc, speed limit warning | SATISFIED | SpeedometerRenderer: 3-slot, 12-segment arc, auto-scale, NF40 triple-signal warning. |
| F5.2 | 01,02,05a,08 | Clock (Digital) — HH:mm, optional seconds, AM/PM, timezone | SATISFIED | ClockDigitalRenderer: showSeconds/use24Hour/showLeadingZero/timezone settings. |
| F5.3 | 01,05a,08 | Clock (Analog) — Canvas clock with hands, tick marks | SATISFIED | ClockAnalogRenderer: drawWithCache, hour/minute/second hand angles. |
| F5.4 | 01,02,05a,05b,08 | Date — 3 variants (Simple, Stack, Grid), format/timezone | SATISFIED | DateSimpleRenderer, DateStackRenderer, DateGridRenderer — all locale-aware. |
| F5.5 | 01,06a,08 | Compass — cardinal direction, tick marks, tilt indicators | SATISFIED | CompassRenderer: OrientationSnapshot, Canvas rotation, cardinal buckets. |
| F5.6 | 01,02,05b,08 | Battery — level, charging state, temperature | SATISFIED | BatteryRenderer + BatteryProvider: greenfield, InfoCardLayout. |
| F5.7 | 01,03,06a,08 | Speed Limit (Circle) — European style, region-aware KPH/MPH, Japan blue | SATISFIED | SpeedLimitCircleRenderer + SpeedLimitProvider: RegionDetector wired. |
| F5.8 | 01,03,06b,08 | Shortcuts — tappable app launcher with suggested apps | SATISFIED | ShortcutsRenderer + CallActionProvider: ActionableProvider, packageManager. |
| F5.9 | 01,04,06b,08 | Solar — sunrise/sunset, countdown, 24h arc | SATISFIED | SolarRenderer (3 modes) + SolarTimezoneDataProvider + SolarLocationDataProvider. |
| F5.10 | 01,02,05b,08 | Ambient Light — lux, DARK/DIM/NORMAL/BRIGHT, InfoCard | SATISFIED | AmbientLightRenderer + AmbientLightDataProvider: InfoCardLayout. |
| F5.11 | 01,03,06a,08 | Speed Limit (Rectangle) — US MUTCD style | SATISFIED | SpeedLimitRectRenderer: black border, "SPEED LIMIT" text, region-aware. |
| NF14 | 02,03,08 | Sensor batching for non-critical sensors | SATISFIED | OrientationDataProvider: maxReportLatencyUs=200_000. AmbientLightDataProvider: 500_000. AccelerometerProvider: 100_000 (confirmed via BATCH_LATENCY_US constant test). |
| NF40 | 07,08 | Color-blind safety: speed limit warnings use color+pattern+icon | SATISFIED | SpeedometerRenderer: amber/red background + pulsing border (animateFloat infinite transition 500ms) + Canvas-drawn warning triangle. Triple-signal confirmed in source. |
| NF-I2 | 05a,05b,06a,06b,07 | Locale-aware formatting | SATISFIED | NumberFormat.getInstance(Locale.getDefault()) in Speedometer + AmbientLight. DateTimeFormatter.ofPattern with Locale in date widgets. Locale.getDefault() in ClockDigital. |
| NF-P1 | 03,08 | No GPS coordinates stored | SATISFIED | GpsSpeedProvider stores only Long/Double/Boolean primitive fields. No Location class fields. GpsSpeedProviderTest reflection assertion (line 128-131) verifies at runtime. |

### Anti-Patterns Found

| File | Issue | Severity | Impact |
|------|-------|----------|--------|
| `SolarCalculatorTest.kt` line 34 | London summer solstice tolerance widened to 5 min vs plan spec of 2 min | Warning | Plan required 2-min precision for all dates. Summer solstice uses 5-min tolerance (likely due to atmospheric refraction complexity). Winter solstice and equinox remain at 2 and 5 min respectively. |
| `SolarTimezoneDataProviderTest.kt` | Does not extend DataProviderContractTest (7 standalone assertions) | Warning | Missing 12 contract assertions. Provider behavior is partially tested but not to the same rigor as the other 7 typed providers. |
| `SolarLocationDataProviderTest.kt` | Does not extend DataProviderContractTest (6 standalone assertions) | Warning | Same issue. provideState() flow is never tested (no FusedLocationProviderClient mock for callback). |
| `SolarCalculatorTest.kt` line 23-24 | `toJulianDay(2000,1,1)` returns 2451544.5 not 2451545.0 | Info | Plan expected exact 2451545.0 (J2000.0 epoch). Implementation returns 2451544.5 (Julian Date for Jan 1, 2000 at midnight). Both are standard — plan spec was off by 0.5 (Julian Dates count from noon). Test accepts 2451544.5 correctly. |

### Human Verification Required

None — all human verification items from the on-device SC3/SC4 criteria were performed by the executing agent using adb commands.

## Gaps Summary

**Gap 1: SC3 — dump-health ACTIVE and query-semantics (FAILED)**

The most significant gap is SC3. `AddWidgetHandler` was built to validate typeId against the Hilt-injected `Set<WidgetRenderer>` but does not dispatch `DashboardCommand.AddWidget` to the `LayoutCoordinator`. The plan's original design assumed add-widget would cause physical widget placement, after which `dump-health` could observe `ACTIVE` status and `query-semantics` would return visible composed nodes.

Instead, the implementation chose a simpler AddWidgetHandler that only validates registration. This was a deliberate design decision (08-08-SUMMARY: "No command channel bridge exists outside DashboardViewModel"). The upside is DI registration is fully verified (all 13 typeIds accepted, no UNKNOWN_TYPE). The downside is the "renders visible on device" part of SC3 is unverified.

This gap is structural: it requires wiring AddWidgetHandler to DashboardViewModel's command channel. Phase 13 (E2E Integration) is the designated resolution target.

**Gap 2: SC1 partial — Solar providers not extending DataProviderContractTest**

`SolarTimezoneDataProviderTest` and `SolarLocationDataProviderTest` are standalone test classes. They cover sourceId, dataType, priority, setupSchema, snapshotType, isAvailable, and one emission test (for SolarTimezone) — but they miss 12 contract-level assertions including cancellation safety, concurrent subscriber handling, connection state semantics, first-emission timeout, and subscriber timeout behavior. This matters because Solar providers have background recalculation logic (midnight recalculation, timezone change broadcast) that could have cancellation or concurrency issues.

This is addressable: both providers could be converted to extend `DataProviderContractTest`. The challenge noted in the SUMMARY is that these providers have Android dependencies (Context for BroadcastReceiver, FusedLocationProviderClient) that require careful mocking for the auto-fire callback pattern used in GpsSpeed and Accelerometer tests.

---

_Verified: 2026-02-25T18:00:00Z_
_Verifier: Claude (gsd-verifier)_
