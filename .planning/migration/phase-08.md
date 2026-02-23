# Phase 8: Essentials Pack (Architecture Validation Gate)

**What:** First pack migration. Proves the entire SDK→Pack contract works end-to-end.

## `:pack:essentials`

**Convention plugin validation** — `dqxn.pack` auto-wires deps, no manual `:sdk:*` imports.

**Snapshot types** — partitioned between `:pack:essentials:snapshots` (cross-boundary, available for future packs) and `:pack:essentials` (pack-local). Each annotated with `@DashboardSnapshot`, validated by KSP. This is the first real test of the non-sealed `DataSnapshot` + KSP validation approach.

`:pack:essentials:snapshots` sub-module (using `dqxn.snapshot` plugin from Phase 1):
- `SpeedSnapshot`, `AccelerationSnapshot`, `BatterySnapshot`, `TimeSnapshot`, `OrientationSnapshot`, `AmbientLightSnapshot`

`:pack:essentials` pack-local:
- `SolarSnapshot` (only consumed by Essentials pack's Solar widget)
- `SpeedLimitSnapshot` (only consumed by Essentials pack's Speed Limit widgets)

**13 widgets** migrated to new contracts:

| Widget | Snapshot Type | Notes |
|---|---|---|
| `SpeedometerRenderer` | `SpeedSnapshot` | `derivedStateOf` + `drawWithCache` for high-frequency data |
| `ClockDigitalRenderer` | `TimeSnapshot` | |
| `ClockAnalogRenderer` | `TimeSnapshot` | |
| `DateSimpleRenderer` | `TimeSnapshot` | Formats date from `TimeSnapshot` — no separate `DateSnapshot` |
| `DateStackRenderer` | `TimeSnapshot` | Formats date from `TimeSnapshot` |
| `DateGridRenderer` | `TimeSnapshot` | Formats date from `TimeSnapshot` |
| `CompassRenderer` | `OrientationSnapshot` | `drawWithCache` for needle rotation |
| `SpeedLimitCircleRenderer` | `SpeedLimitSnapshot` | SPEED_LIMIT is a data provider type (exists in old codebase) |
| `SpeedLimitRectRenderer` | `SpeedLimitSnapshot` | SPEED_LIMIT is a data provider type (exists in old codebase) |
| `SolarRenderer` | `SolarSnapshot` | |
| `AmbientLightRenderer` | `AmbientLightSnapshot` | |
| `BatteryRenderer` | `BatterySnapshot` | Displays battery level, charging state, time-to-full/empty |
| `ShortcutsRenderer` | (none — tap actions) | `AppPickerSetting` |

All widgets: `ImmutableMap` settings, `LocalWidgetData.current` data access, `accessibilityDescription()` implemented.

**9 data providers + 1 action handler** migrated to typed emission:

Typed `DataProvider<T>` implementations:

| Provider | Emits | Port status | Notes |
|---|---|---|---|
| `TimeDataProvider` | `TimeSnapshot` | Port from old | |
| `OrientationDataProvider` | `OrientationSnapshot` | Port from old | callbackFlow + awaitClose preserved |
| `SolarTimezoneDataProvider` | `SolarSnapshot` | Port from old | |
| `SolarLocationDataProvider` | `SolarSnapshot` | Port from old | |
| `AmbientLightDataProvider` | `AmbientLightSnapshot` | Port from old | callbackFlow |
| `GpsSpeedProvider` | `SpeedSnapshot` | **Greenfield** | Requires `ACCESS_FINE_LOCATION`. No old equivalent — old used OBD/EXTOL for speed. Implement `callbackFlow` + `LocationManager.requestLocationUpdates()` with `TRANSPORT_GPS` provider. Emit speed in m/s (widget handles unit conversion via `RegionDetector`) |
| `BatteryProvider` | `BatterySnapshot` | **Greenfield** | `BroadcastReceiver` for `ACTION_BATTERY_CHANGED` → `callbackFlow`. Extract level, status, plugged, temperature fields |
| `AccelerometerProvider` | `AccelerationSnapshot` | **Greenfield** | `SensorManager.getDefaultSensor(TYPE_ACCELEROMETER)` → `callbackFlow`. Low-pass filter for gravity removal configurable via settings |
| `SpeedLimitProvider` | `SpeedLimitSnapshot` | **Greenfield** | User-configured static value. No standalone provider exists in old codebase — old speed limits came only from OBU SDK and demo simulator. Implement as `MutableStateFlow` reading from `ProviderSettingsStore` |

Action handler (not a typed `DataProvider<T>` — excluded from `DataProviderContractTest` count):

| Provider | Notes |
|---|---|
| `CallActionProvider` | Tap action routing for Shortcuts widget (no snapshot emission, implements `ActionableProvider` not `DataProvider<T>`) |

**Multi-snapshot widget wiring:** `SpeedometerRenderer` declares `compatibleSnapshots = setOf(SpeedSnapshot::class, AccelerationSnapshot::class, SpeedLimitSnapshot::class)`. `WidgetDataBinder` binds all three providers → `WidgetData` accumulates via `merge()+scan()`. Speedometer reads speed from slot 0, acceleration from slot 1, speed limit from slot 2 (for overspeed indicator). This is the first real test of multi-slot `WidgetData` delivery — if `combine()` sneaks in, the speedometer stalls until all three providers emit.

**2 free themes** — `slate.theme.json`, `minimalist.theme.json` (port verbatim).

**Ported from old:** All widget implementations except `BatteryRenderer` exist in old codebase — import wholesale and adapt to new architecture (signature changes, `LocalWidgetData`, `ImmutableMap`). `BatteryRenderer` is greenfield (no old equivalent). 4 providers are greenfield: `GpsSpeedProvider` (old used OBD/EXTOL, not GPS), `BatteryProvider` (no old equivalent), `AccelerometerProvider` (old `OrientationProvider` uses accelerometer internally but no standalone provider), `SpeedLimitProvider` (old had OBU-specific and demo-only — no standalone user-configured provider). Remaining 5 providers (`Time`, `Orientation`, `SolarTimezone`, `SolarLocation`, `AmbientLight`) port with callbackFlow patterns already correct. Theme JSON files port verbatim. Every widget's `Render()` signature changes (no `widgetData` param, add `ImmutableMap`, read from `LocalWidgetData`). Also port: old `WidgetRenderer.onTap(context, widgetId, settings)` drops `Context` param — but `CallActionProvider.execute()` still needs `Context` for intent launching (it gets `Context` via Hilt injection, not method param).

**Port inventory:**

| Old artifact | Target | Notes |
|---|---|---|
| `SolarCalculator` — Meeus/NOAA solar algorithm, pure Kotlin, ±1min accuracy | `:pack:essentials` alongside providers | Port verbatim; no Android deps. Regeneration task (`updateIanaTimezones`) needs equivalent. **Add `SolarCalculatorTest`** — pure algorithm with known NOAA reference data; bugs produce plausible-but-wrong sunrise times affecting theme auto-switch app-wide |
| `IanaTimezoneCoordinates` — 312-entry IANA zone → lat/lon lookup table (updated 2026-01-25) | `:pack:essentials` alongside providers | Port; **has one `android.icu.util.TimeZone.getCanonicalID()` import** for alias resolution (e.g., `"Asia/Saigon"` → `"Asia/Ho_Chi_Minh"`). This makes it Android-only — acceptable for `:pack:essentials` but note the dependency. Alternatively replace ICU call with a static alias map. Spot-check 3–5 known cities to verify table integrity after port |
| `RegionDetector` — timezone-first MPH country detection + `MPH_COUNTRIES` set | `:pack:essentials` | Port; only speed widgets use it — keep in pack unless second consumer appears. **Add `RegionDetectorTest`** — verify 3-step fallback chain (timezone→locale→"US") and `MPH_COUNTRIES` correctness (wrong entry = wrong speed unit for entire country) |
| `TimezoneCountryMap` — IANA timezone → country code + city name | `:pack:essentials` | Port; co-located with `RegionDetector` |
| `InfoCardLayout` — deterministic weighted normalization for STACK/COMPACT/GRID modes (536 lines) | `:sdk:ui` (Phase 3, not deferred) | Port; 5+ widgets depend on layout modes. Includes `getTightTextStyle` (font padding elimination via `PlatformTextStyle(includeFontPadding = false)` + `LineHeightStyle.Trim.Both`). **Add tests**: `SizeOption.toMultiplier()` mapping and normalization calc per layout mode — wrong weights cause text clipping |
| `WidgetPreviewData` — `PREVIEW_WIDGET_DATA` static data for picker previews | Each pack provides its own preview data | Port + adapt to typed snapshots. **Old code places this in `feature/dashboard` — packs can't import that.** Each pack should define its own `object PreviewData` with typed snapshot instances for its widgets. `WidgetPicker` (Phase 10) reads preview data via `WidgetRenderer.getPreviewData(): WidgetData` method or similar pack-local factory |
| `CardSize` enum — `SMALL(8dp)`, `MEDIUM(12dp)`, `LARGE(16dp)` corner radii | `:core:design` | Port verbatim. No `CornerRadius` enum or shared `styleSettingsSchema` exists in old code — style settings (glow, border, background, opacity, cornerRadius, rim) are per-widget via `WidgetStyle` properties, not a shared schema |

**Tests:** Every widget extends `WidgetRendererContractTest`. Every provider extends `DataProviderContractTest`. Widget-specific rendering tests — each widget type has at least one test beyond the contract base: `SpeedometerRenderer` arc angle calculation at known speed values (0, 60, 120), `CompassRenderer` needle rotation at cardinal bearings (0°, 90°, 180°, 270°), `SolarRenderer` arc position at known times (sunrise, solar noon, sunset), `ClockAnalogRenderer` hand positions at known times (12:00, 3:15, 6:30). Remaining widgets: composition smoke test with realistic `WidgetData` (not just `Empty`/`Unavailable` from contract). On-device semantics verification via `assertWidgetRendered` + `assertWidgetText` for each widget type. `SolarCalculatorTest` — known reference data: summer/winter solstice at known cities (e.g., London 51.5°N), equatorial location, `minutesToLocalTime` edge cases (0, 1439, fractional), `toJulianDay` against NOAA reference. `RegionDetectorTest` — 3-step fallback chain + `MPH_COUNTRIES` set correctness. Greenfield provider unit tests (beyond contract): `GpsSpeedProvider` — `LocationManager` mock emitting location with speed field → `SpeedSnapshot` in m/s, permission denied → `connectionState` false. `BatteryProvider` — `ACTION_BATTERY_CHANGED` broadcast mock → `BatterySnapshot` with level/status/plugged/temperature. `AccelerometerProvider` — sensor event mock → `AccelerationSnapshot` with gravity-removed values (low-pass filter verified). `SpeedLimitProvider` — `ProviderSettingsStore` key format (`essentials:speed-limit:value`) read/write round-trip, default value when key missing. Multi-slot `WidgetDataBinder` integration test for Speedometer: bind 3 providers (`SpeedSnapshot`, `AccelerationSnapshot`, `SpeedLimitSnapshot`), delay `AccelerationSnapshot` by 2s — verify Speedometer renders with speed data immediately (slot 1 present), then accumulates acceleration when it arrives (slot 2 present). Explicitly verifies `merge()+scan()` does NOT wait for all slots (would fail with `combine()`).

**Phase 8 gate — all four criteria must pass before Phase 9 starts:**

1. **Contract tests green.** All 13 widgets pass `WidgetRendererContractTest`, all 9 data providers pass `DataProviderContractTest`.
2. **End-to-end wiring.** On-device: `add-widget` + `dump-health` for each of the 13 widget types shows ACTIVE status (provider bound, data flowing). `query-semantics` confirms each widget's semantics node is visible with non-empty `contentDescription`.
3. **Stability soak.** 60-second soak with all 13 widgets placed — safe mode not triggered (no 4-crash/60s event). `dump-semantics` at end confirms all 13 widget nodes visible with correct bounds.
4. **Regression gate.** All Phase 2-7 tests pass with `:pack:essentials` in the `:app` dependency graph. Adding a pack must not cause Hilt binding conflicts, KSP annotation processing errors, or R8 rule collisions.

First real E2E test class (`AgenticTestClient`) starts here — wrapping `adb shell content call` with assertion helpers including semantics helpers (`querySemanticsOne`, `assertWidgetRendered`, `assertWidgetText`, `awaitSemanticsNode`). Module: `:app:src/androidTest/kotlin/` — colocated with `HiltAndroidRule`-based instrumented tests. Test: `add-widget` for each type → `dump-health` → assert all ACTIVE → `assertWidgetRendered` for each → `get-metrics` → assert draw times within budget. This test grows in Phase 9 (chaos correlation + semantics verification of fallback UI) and Phase 10 (full user journey).

**`WidgetScopeBypass` lint rule** (referenced in Phase 1, delivered here with first widget renderer):

| Aspect | Spec |
|---|---|
| **Detects** | Coroutine launches inside `@Composable` `Render()` functions that do NOT use `LocalWidgetScope.current` — catches `rememberCoroutineScope()`, `LaunchedEffect` with captured non-widget scope, direct `CoroutineScope()` construction, and `GlobalScope` usage in any file under `pack/*/widgets/` |
| **Exempts** | `remember { }` blocks (no coroutine), `derivedStateOf` (synchronous), test source sets |
| **Why load-bearing** | After fd-count leak detection was dropped from `DataProviderContractTest` (#4), this rule + LeakCanary are the only remaining resource leak guards. A widget launching on `rememberCoroutineScope()` bypasses `SupervisorJob` isolation — one widget crash cancels siblings |
| **Tests** | Positive: `rememberCoroutineScope().launch {}` in Render → fires. `GlobalScope.launch {}` in Render → fires. Negative: `LocalWidgetScope.current.launch {}` → does not fire. `derivedStateOf {}` → does not fire. Test files → does not fire |

If contracts feel wrong, fix them in Phase 2 before proceeding.
