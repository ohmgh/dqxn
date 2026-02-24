# Phase 8: Essentials Pack (Architecture Validation Gate) - Research

**Researched:** 2026-02-24
**Domain:** Android pack implementation — widget renderers, data providers, snapshot types, Hilt DI wiring, sensor callbackFlow, Canvas-based Compose rendering
**Confidence:** HIGH

## Summary

Phase 8 is the first real pack implementation and the architecture validation gate. It proves the entire SDK-to-Pack contract chain works end-to-end: snapshot types in `:pack:essentials:snapshots`, KSP-generated Hilt modules via `@DashboardWidget`/`@DashboardDataProvider`/`@DashboardSnapshot`, 13 widget renderers extending `WidgetRendererContractTest`, 9 typed data providers extending `DataProviderContractTest`, and multi-slot `WidgetData` wiring via `merge()+scan()`.

The module stubs exist (`pack/essentials/build.gradle.kts` with `dqxn.pack` plugin, `pack/essentials/snapshots/build.gradle.kts` with `dqxn.snapshot` plugin) and `:pack:essentials` is already in `:app`'s dependency graph. All SDK contracts (`WidgetRenderer`, `DataProvider<T>`, `DataSnapshot`, `WidgetData`, `LocalWidgetData`, `LocalWidgetScope`), test base classes (`WidgetRendererContractTest` with 14 assertions, `DataProviderContractTest` with 12 assertions), convention plugins, and the data layer (`ProviderSettingsStore`, `LayoutRepository`) are implemented. The infrastructure is ready — this phase fills the pack module with actual implementations.

Four providers are greenfield (GpsSpeed, Battery, Accelerometer, SpeedLimit) — none have old codebase equivalents. The remaining 5 providers and all 12 non-Battery widgets port from the old codebase with signature changes (`LocalWidgetData.current`, `ImmutableMap`, typed snapshots). BatteryRenderer is also greenfield. The ported SolarCalculator and IanaTimezoneCoordinates are the riskiest ports (algorithmic precision matters for theme auto-switch, and ICU dependency needs handling). The multi-slot Speedometer wiring (Speed + Acceleration + SpeedLimit via `merge()+scan()`) is the key architectural validation — it proves slots update independently and don't block on missing providers.

**Primary recommendation:** Structure implementation as snapshot types first (unblocks everything), then providers and widgets in parallel waves grouped by complexity, with the Speedometer multi-slot wiring as a dedicated integration verification. The `WidgetScopeBypass` lint rule should be delivered alongside the first widget renderer.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F5.1 | **Speedometer** — circular arc gauge, 12-segment acceleration arc, auto-scaling max, speed limit warning, configurable alert types | Port from old codebase. Canvas-based `drawWithCache`. Multi-slot binding: SpeedSnapshot + AccelerationSnapshot + SpeedLimitSnapshot. `RegionDetector` for AUTO unit. `computeGaugeMax()` stepped thresholds. Settings schema: 9 settings including `showSpeedArc`, `showAccelerationArc`, `arcStrokeDp`, `showTickMarks`, `speedUnit`, `speedLimitOffsetKph/Mph`, `showWarningBackground`, `alertType` |
| F5.2 | **Clock (Digital)** — large HH:mm, optional seconds, AM/PM, timezone label, configurable timezone | Port from old. `derivedStateOf` for time extraction from `TimeSnapshot`. Settings: `showSeconds`, `use24HourFormat`, `showLeadingZero`, `timezoneId` (TimezoneSetting), size/position enums. `SettingsAwareSizer` |
| F5.3 | **Clock (Analog)** — Canvas circular clock with hour/minute/second hands, tick marks | Port from old. `drawWithCache` for static tick marks. Hand position math. Settings: `showTickMarks`, `timezoneId` |
| F5.4 | **Date** — 3 variants (Simple, Stack, Grid), configurable format and timezone | Port from old. 3 renderers sharing `DateSettings`. `DateFormatSetting` with locale-aware options. Reads `TimeSnapshot` and extracts date portions |
| F5.5 | **Compass** — cardinal direction, tick marks, tilt indicators (pitch/roll) | Port from old. Canvas rotation with `drawWithCache`. `OrientationSnapshot` for bearing/pitch/roll. Settings: `cardinalLabelStyle`, `showTiltIndicators` |
| F5.6 | **Battery** — battery level percentage, charging state, optional temperature | **Greenfield** renderer + provider. `BatterySnapshot` via `BroadcastReceiver` + `callbackFlow` for `ACTION_BATTERY_CHANGED`. Settings: `showPercentage`, `showTemperature`, `chargingIndicator` |
| F5.7 | **Speed Limit (Circle)** — European-style circular sign, region-aware | Port from old. `SpeedLimitSnapshot` from greenfield `SpeedLimitProvider`. `RegionDetector` for auto KPH/MPH. Settings: `borderSizePercent`, `speedUnit`, `digitColor` |
| F5.8 | **Shortcuts** — tappable widget launching a chosen app | Port from old. No snapshot type — tap action via `CallActionProvider`. `InfoCardLayout`. Settings: `packageName` (AppPickerSetting), `displayName`. `onTap()` launches app |
| F5.9 | **Solar** — sunrise/sunset times or next event countdown, optional 24h circular arc | Port from old (1217-line widget). Complex Canvas: dawn/day/dusk/night bands, celestial markers. `SolarCalculator` + `IanaTimezoneCoordinates` required. Settings: `displayMode`, `nextEventFormat`, `arcSize`, `timezoneId` |
| F5.10 | **Ambient Light** — lux level, category, InfoCard layout | Port from old. `InfoCardLayout` with `LightBulbPainter`. `AmbientLightSnapshot` category display. No settings beyond info card schema |
| F5.11 | **Speed Limit (Rectangle)** — US-style rectangular sign | Port from old. Same `SpeedLimitSnapshot` as Circle. Settings: `speedUnit`. MUTCD-style rendering |
| NF14 | Sensor batching for non-critical sensors (compass, ambient light) to reduce SoC wakeups | `SensorManager.registerListener()` with `maxReportLatencyUs` parameter for batching. Compass (OrientationDataProvider): 200ms batch. Ambient light: 500ms batch. GPS: handled by FusedLocationProvider. Battery: event-driven (no batching needed) |
| NF40 | Color-blind safety: speed limit warnings use color + pattern/icon, not color alone | Speedometer warning system: amber/red backgrounds must add pulsing border + warning icon alongside color. Speed limit widgets: region-aware digit color must remain discriminable for deuteranopia |
| NF-I2 | Widget data uses locale-aware formatting (decimal separators, unit labels) | All numeric display: use `NumberFormat.getInstance(Locale.getDefault())`. Speed: "65 km/h" vs "40 mph". Temperature: locale decimal. Unit labels from Android string resources |
| NF-P1 | Location retention: no GPS track stored. Trip accumulator stores only derived distance/duration | GpsSpeedProvider: no location history. Emit speed+acceleration only. No coordinate storage. SpeedLimitProvider: user-configured static value, no location dependency |
</phase_requirements>

## Standard Stack

### Core (Already in Project)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Jetpack Compose + M3 | BOM managed | Widget `Render()` composables, Canvas, `drawWithCache` | In `:sdk:ui`, auto-wired by `dqxn.pack` |
| Hilt + KSP | Auto-wired | `@Module @InstallIn(SingletonComponent)`, `@Binds @IntoSet` | `dqxn.pack` applies `dqxn.android.hilt` + KSP |
| kotlinx-collections-immutable | Auto-wired | `ImmutableMap<String, Any>` for settings, `ImmutableList` for schemas | Required for Compose stability |
| kotlinx.serialization | Auto-wired | JSON theme files, manifest generation | `dqxn.pack` applies serialization plugin |
| Turbine | Test dep | Flow testing in provider unit tests | Via `dqxn.android.test` |
| MockK | Test dep | Mocking SensorManager, LocationManager, BroadcastReceiver | Via `dqxn.android.test` |
| Truth | Test dep | Assertions in contract tests | Via `dqxn.android.test` |

### Supporting (Need Adding to Pack)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| play-services-location | Latest stable | `FusedLocationProviderClient` for `SolarLocationDataProvider` (PASSIVE priority) | GPS-based solar calculation |
| No additional deps needed | — | Battery, Accelerometer, Orientation all use Android SDK sensors directly | `SensorManager`, `BatteryManager` are framework APIs |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Direct `SensorManager` | `SensorManagerCompat` (AndroidX) | No benefit — minSdk 31 has all needed APIs. No compat wrapper exists for sensors anyway |
| `FusedLocationProviderClient` for speed | Direct `LocationManager` GPS provider | FusedLocation is more battery-efficient. But phase-08.md specifies `LocationManager.requestLocationUpdates()` with `TRANSPORT_GPS` for GpsSpeedProvider. Use direct `LocationManager` for speed (raw GPS), `FusedLocation` for solar (passive) |
| Manual JSON parsing for themes | kotlinx.serialization | kotlinx.serialization already in pack deps — use it |

**Dependencies needed in `pack/essentials/build.gradle.kts`:**
```kotlin
implementation(libs.play.services.location) // For SolarLocationDataProvider only
```

## Architecture Patterns

### Recommended Project Structure
```
pack/essentials/
├── snapshots/                              # dqxn.snapshot plugin, :sdk:contracts only
│   └── src/main/kotlin/.../snapshots/
│       ├── SpeedSnapshot.kt                # @DashboardSnapshot(dataType = "speed")
│       ├── AccelerationSnapshot.kt
│       ├── BatterySnapshot.kt
│       ├── TimeSnapshot.kt
│       ├── OrientationSnapshot.kt
│       └── AmbientLightSnapshot.kt
├── src/main/kotlin/.../pack/essentials/
│   ├── providers/
│   │   ├── TimeDataProvider.kt             # Port from old
│   │   ├── OrientationDataProvider.kt      # Port from old (internal OrientationProvider)
│   │   ├── SolarTimezoneDataProvider.kt    # Port from old
│   │   ├── SolarLocationDataProvider.kt    # Port from old
│   │   ├── AmbientLightDataProvider.kt     # Port from old
│   │   ├── GpsSpeedProvider.kt             # GREENFIELD
│   │   ├── BatteryProvider.kt              # GREENFIELD
│   │   ├── AccelerometerProvider.kt        # GREENFIELD
│   │   ├── SpeedLimitProvider.kt           # GREENFIELD
│   │   ├── CallActionProvider.kt           # Port from old (ActionableProvider)
│   │   ├── SolarCalculator.kt             # Pure Kotlin algorithm port
│   │   ├── IanaTimezoneCoordinates.kt     # Data table port
│   │   ├── RegionDetector.kt              # Timezone-first MPH detection
│   │   └── TimezoneCountryMap.kt          # IANA → country code
│   ├── widgets/
│   │   ├── speedometer/SpeedometerRenderer.kt
│   │   ├── clock/ClockDigitalRenderer.kt
│   │   ├── clock/ClockAnalogRenderer.kt
│   │   ├── date/DateSimpleRenderer.kt
│   │   ├── date/DateStackRenderer.kt
│   │   ├── date/DateGridRenderer.kt
│   │   ├── compass/CompassRenderer.kt
│   │   ├── battery/BatteryRenderer.kt      # GREENFIELD
│   │   ├── speedlimit/SpeedLimitCircleRenderer.kt
│   │   ├── speedlimit/SpeedLimitRectRenderer.kt
│   │   ├── shortcuts/ShortcutsRenderer.kt
│   │   ├── solar/SolarRenderer.kt
│   │   └── ambientlight/AmbientLightRenderer.kt
│   ├── snapshots/                          # Pack-local snapshots
│   │   ├── SolarSnapshot.kt
│   │   └── SpeedLimitSnapshot.kt
│   ├── theme/
│   │   └── EssentialsThemeProvider.kt
│   └── di/
│       └── EssentialsModule.kt             # KSP generates this — DON'T hand-write
├── src/main/resources/themes/
│   ├── slate.theme.json
│   └── minimalist.theme.json
├── src/test/kotlin/.../pack/essentials/
│   ├── widgets/
│   │   ├── SpeedometerRendererTest.kt      # Contract + arc angle tests
│   │   ├── ClockDigitalRendererTest.kt
│   │   ├── ClockAnalogRendererTest.kt      # Contract + hand position tests
│   │   ├── DateSimpleRendererTest.kt
│   │   ├── DateStackRendererTest.kt
│   │   ├── DateGridRendererTest.kt
│   │   ├── CompassRendererTest.kt          # Contract + needle rotation tests
│   │   ├── BatteryRendererTest.kt          # Contract + charging state tests
│   │   ├── SpeedLimitCircleRendererTest.kt
│   │   ├── SpeedLimitRectRendererTest.kt
│   │   ├── ShortcutsRendererTest.kt
│   │   ├── SolarRendererTest.kt            # Contract + arc position tests
│   │   └── AmbientLightRendererTest.kt
│   ├── providers/
│   │   ├── TimeDataProviderTest.kt         # Contract
│   │   ├── OrientationDataProviderTest.kt  # Contract
│   │   ├── SolarTimezoneDataProviderTest.kt
│   │   ├── SolarLocationDataProviderTest.kt
│   │   ├── AmbientLightDataProviderTest.kt
│   │   ├── GpsSpeedProviderTest.kt         # Contract + greenfield-specific
│   │   ├── BatteryProviderTest.kt          # Contract + greenfield-specific
│   │   ├── AccelerometerProviderTest.kt    # Contract + greenfield-specific
│   │   └── SpeedLimitProviderTest.kt       # Contract + greenfield-specific
│   └── util/
│       ├── SolarCalculatorTest.kt
│       └── RegionDetectorTest.kt
└── build.gradle.kts
```

### Pattern 1: Snapshot Type Definition (`:pack:essentials:snapshots`)
**What:** Cross-boundary `@DashboardSnapshot` data classes in pure Kotlin sub-module
**When to use:** Snapshot types consumed across module boundaries (e.g., SpeedSnapshot used by both essentials and future plus pack)
**Example:**
```kotlin
// pack/essentials/snapshots/src/.../SpeedSnapshot.kt
package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

@DashboardSnapshot(dataType = "speed")
@Immutable
data class SpeedSnapshot(
    val speedMps: Float,          // m/s — widget handles unit conversion
    val accuracy: Float?,         // meters/second uncertainty
    override val timestamp: Long,
) : DataSnapshot
```
**Critical:** `dqxn.snapshot` plugin only wires `:sdk:contracts` — no Compose compiler. `@Immutable` is available because `:sdk:contracts` has `compileOnly(compose.runtime)`. Only `val` properties. No business logic.

### Pattern 2: Pack-Local Snapshot Type
**What:** Snapshot types consumed only within the pack, not extracted to sub-module
**When to use:** Only one pack consumes this type (e.g., SolarSnapshot for Solar widget)
**Example:**
```kotlin
// pack/essentials/src/.../pack/essentials/snapshots/SolarSnapshot.kt
package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

@DashboardSnapshot(dataType = "solar")
@Immutable
data class SolarSnapshot(
    val sunriseEpochMillis: Long,
    val sunsetEpochMillis: Long,
    val solarNoonEpochMillis: Long,
    val isDaytime: Boolean,
    val sourceMode: String,       // "location" or "timezone"
    override val timestamp: Long,
) : DataSnapshot
```

### Pattern 3: Greenfield callbackFlow Provider
**What:** `DataProvider<T>` using Android sensors via `callbackFlow`
**When to use:** All sensor-based providers
**Example (BatteryProvider):**
```kotlin
@DashboardDataProvider(
    localId = "battery",
    displayName = "Battery",
    description = "Device battery level and charging state",
)
@Singleton
class BatteryProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DataProvider<BatterySnapshot> {

    override val snapshotType: KClass<BatterySnapshot> = BatterySnapshot::class
    override val sourceId: String = "essentials:battery"
    override val dataType: String = DataTypes.BATTERY
    override val priority: ProviderPriority = ProviderPriority.DEVICE_SENSOR
    override val subscriberTimeout: Duration = 30.seconds
    override val firstEmissionTimeout: Duration = 5.seconds

    override fun provideState(): Flow<BatterySnapshot> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val percent = (level * 100) / scale
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                    .takeIf { it > 0 }?.let { it / 10f }
                trySend(BatterySnapshot(
                    level = percent,
                    isCharging = isCharging,
                    temperature = temp,
                    timestamp = SystemClock.elapsedRealtimeNanos(),
                ))
            }
        }
        // Sticky broadcast — first emission is immediate
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitClose { context.unregisterReceiver(receiver) }
    }
    // ... connectionState, setupSchema, schema, etc.
}
```
**Critical:** `awaitClose` is mandatory for `callbackFlow` — it prevents receiver leaks on cancellation. Battery uses sticky broadcast so first emission is guaranteed immediate.

### Pattern 4: Widget Renderer with `LocalWidgetData` + `derivedStateOf`
**What:** Widget reads data from `LocalWidgetData.current` and defers reads via `derivedStateOf`
**When to use:** All widget renderers
**Example:**
```kotlin
@DashboardWidget(
    typeId = "essentials:battery",
    displayName = "Battery",
)
class BatteryRenderer @Inject constructor() : WidgetRenderer {
    override val typeId: String = "essentials:battery"
    // ... spec fields ...

    @Composable
    override fun Render(
        isEditMode: Boolean,
        style: WidgetStyle,
        settings: ImmutableMap<String, Any>,
        modifier: Modifier,
    ) {
        val widgetData = LocalWidgetData.current
        val snapshot by remember {
            derivedStateOf { widgetData.snapshot<BatterySnapshot>() }
        }
        // Render using snapshot — null means no data yet
    }
}
```
**Critical:** `derivedStateOf` defers the snapshot read to the draw phase, preventing unnecessary recompositions when unrelated data in the composition changes.

### Pattern 5: Multi-Slot Widget (Speedometer)
**What:** Widget consumes data from multiple providers via KClass-keyed slots
**When to use:** Speedometer (Speed + Acceleration + SpeedLimit)
**Example:**
```kotlin
override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = setOf(
    SpeedSnapshot::class,
    AccelerationSnapshot::class,
    SpeedLimitSnapshot::class,
)

@Composable
override fun Render(...) {
    val widgetData = LocalWidgetData.current
    val speed by remember { derivedStateOf { widgetData.snapshot<SpeedSnapshot>() } }
    val acceleration by remember { derivedStateOf { widgetData.snapshot<AccelerationSnapshot>() } }
    val speedLimit by remember { derivedStateOf { widgetData.snapshot<SpeedLimitSnapshot>() } }
    // Render with whatever data is available — speed renders immediately,
    // acceleration arc fills in when available, speed limit overlay when configured
}
```
**Critical:** The `merge()+scan()` binder ensures Speedometer renders with speed data as soon as it arrives, even if acceleration and speed limit providers haven't emitted yet. Using `combine()` would block until ALL three emit — breaking the independent-availability guarantee.

### Pattern 6: KSP-Generated Hilt Module
**What:** `@DashboardWidget` and `@DashboardDataProvider` annotations trigger KSP to generate the `EssentialsModule` Hilt module
**When to use:** Automatic — never hand-write the Hilt module
**Key:** KSP processor generates `@Binds @IntoSet` bindings into `SingletonComponent`. The `dqxn.pack` plugin configures KSP args (`packId = "essentials"`, `themesDir`). Pack manifest also auto-generated.

### Anti-Patterns to Avoid
- **`combine()` for multi-slot binding** — blocks on slowest provider. Use `merge()+scan()`.
- **Hand-writing Hilt module** — KSP generates it. Adding manual `@Module` causes duplicate binding errors.
- **`rememberCoroutineScope()` in Render()** — bypasses `SupervisorJob` isolation. Use `LocalWidgetScope.current`. Enforced by `WidgetScopeBypass` lint rule.
- **`LazyLayout` for grid** — use custom `Layout` + `MeasurePolicy` per CLAUDE.md.
- **`BlurMaskFilter` for glow** — use `RenderEffect.createBlurEffect()` per CLAUDE.md.
- **`UnconfinedTestDispatcher`** — always `StandardTestDispatcher` per CLAUDE.md.
- **Storing location history** — GpsSpeedProvider emits speed only, no coordinates retained (NF-P1).
- **`var` in snapshot data classes** — all `val` properties, enforced by KSP.
- **Importing `:feature:*` or `:core:*` from pack** — module boundary violation. Use `:sdk:*` only.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Solar position calculation | Custom astro math | Port `SolarCalculator` from old codebase | Meeus/NOAA algorithm with polar edge cases — reimplementing from scratch risks ±15min errors that break theme auto-switch |
| Timezone → lat/lng lookup | Manual table | Port `IanaTimezoneCoordinates` (312 entries from zone1970.tab) | Data already validated and generated from IANA source |
| MPH country detection | Hardcoded locale check | Port `RegionDetector` with 3-step fallback chain | timezone → locale → "US" fallback. Naive locale check misses timezone-based detection (device locale ≠ country) |
| InfoCard weighted layout | Per-widget layout code | Use existing `InfoCardLayout` in `:sdk:ui` | Already ported in Phase 3 with `SizeOption.toMultiplier()` and 3 layout modes |
| Settings type serialization | Manual string encoding | Use existing `ProviderSettingsStore` + `SettingsSerialization` | Type-prefixed format (`"s:..."`, `"i:..."`, `"b:..."`) already implemented in `:data` |
| Widget error isolation | Custom try/catch per widget | `LocalWidgetScope` + `WidgetSlot` error boundary | Phase 7 delivers this — pack widgets just use `LocalWidgetScope.current` |
| Hilt multibinding registration | Manual `@Module` + `@Binds` | KSP `@DashboardWidget`/`@DashboardDataProvider` annotations | Auto-generated, prevents missed registrations and duplicate binding errors |

**Key insight:** The SDK, data layer, and convention plugins absorb the complexity. Pack implementation should be almost entirely domain logic (sensor integration, rendering math) and contract implementation — minimal boilerplate.

## Common Pitfalls

### Pitfall 1: SolarCalculator Precision Errors
**What goes wrong:** Ported solar algorithm produces sunrise/sunset times that are off by 5-15 minutes at certain latitudes
**Why it happens:** Julian day calculation or equation-of-time constants transcribed incorrectly. Polar edge cases (midnight sun, polar night) produce NaN/Infinity without guards.
**How to avoid:** Port `SolarCalculator.kt` verbatim from old codebase. Add `SolarCalculatorTest` with NOAA reference data: summer/winter solstice at London (51.5N), equatorial location, and Arctic Circle. Verify `minutesToLocalTime` edge cases (0, 1439, fractional). Check `toJulianDay` against NOAA spreadsheet values.
**Warning signs:** Theme auto-switch triggers at wrong times in different timezones. Solar widget shows sunrise 10+ minutes from actual.

### Pitfall 2: IanaTimezoneCoordinates ICU Dependency
**What goes wrong:** `IanaTimezoneCoordinates` has one `android.icu.util.TimeZone.getCanonicalID()` import for timezone alias resolution (e.g., "Asia/Saigon" → "Asia/Ho_Chi_Minh")
**Why it happens:** ICU4J is Android-only. The old code uses it for canonical zone resolution.
**How to avoid:** This is fine for `:pack:essentials` (Android library module). But verify: lookup by alias must resolve to canonical ID before table search. Alternative: replace ICU call with a static alias map if the ICU dependency causes issues. Spot-check 3-5 known aliases after port.
**Warning signs:** Solar times wrong for users whose timezone is an alias (common: "US/Eastern", "Asia/Saigon", "Pacific/Samoa").

### Pitfall 3: merge()+scan() vs combine() for Multi-Slot Binding
**What goes wrong:** Speedometer shows nothing until all three providers (speed, acceleration, speed limit) emit, or shows stale data from a slow provider
**Why it happens:** Using `combine()` instead of `merge()+scan()`. `combine()` waits for ALL upstreams to emit at least once.
**How to avoid:** The `WidgetDataBinder` in Phase 7 implements `merge()+scan()`. Pack widgets just declare `compatibleSnapshots`. Verify in integration test: delay `AccelerationSnapshot` by 2s, assert Speedometer renders speed immediately.
**Warning signs:** Speedometer blank for several seconds after add, then suddenly shows all data. Or: adding a speed limit removes speed display temporarily.

### Pitfall 4: callbackFlow Leak on Cancellation
**What goes wrong:** Sensor listeners or broadcast receivers not unregistered when provider flow is cancelled
**Why it happens:** Missing `awaitClose` block, or cleanup code that throws.
**How to avoid:** Every `callbackFlow` MUST have `awaitClose { unregister/cleanup }`. Enforce in code review and via `DataProviderContractTest#respects cancellation without leaking`. Test each provider: launch collector, cancel, verify no leaked listeners.
**Warning signs:** `ServiceConnectionLeaked` logcat entries, increasing sensor wakeups over time, test hanging on `cancelAndJoin()`.

### Pitfall 5: GpsSpeedProvider Location.getSpeed() Returns 0
**What goes wrong:** `Location.getSpeed()` returns 0.0 even when moving
**Why it happens:** Not all location providers populate the speed field. Only GPS hardware provider reliably sets speed. Network/passive providers return 0.
**How to avoid:** Use `LocationManager` with `GPS_PROVIDER` explicitly (not `FUSED`). Check `location.hasSpeed()` before emitting. If `hasSpeed() == false`, compute speed from consecutive locations: `distance / timeDelta`. Set `SpeedSnapshot.accuracy` to null when speed is computed (vs hardware-reported).
**Warning signs:** Speed shows 0 indoors or on emulators without GPS simulation.

### Pitfall 6: BroadcastReceiver Registration Without IntentFilter
**What goes wrong:** `ACTION_BATTERY_CHANGED` is a sticky broadcast — registering returns the last value. But `ACTION_TIMEZONE_CHANGED` is not sticky — registering without a previous broadcast returns nothing.
**Why it happens:** Confusing sticky vs non-sticky broadcast behavior.
**How to avoid:** Battery: register receiver, get immediate sticky result. Timezone: register receiver AND emit an initial value based on current `TimeZone.getDefault()`.
**Warning signs:** SolarTimezoneDataProvider has no initial emission until timezone actually changes.

### Pitfall 7: WidgetRendererContractTest is JUnit4 (Not JUnit5)
**What goes wrong:** Widget contract tests fail to run or mix JUnit4/5 annotations incorrectly
**Why it happens:** `WidgetRendererContractTest` uses `@org.junit.Test` (JUnit4) and `@Before` for future Compose UI test rule compatibility. But `DataProviderContractTest` uses `@org.junit.jupiter.api.Test` (JUnit5).
**How to avoid:** Widget test classes extending `WidgetRendererContractTest` use JUnit4 annotations (`@Test` from `org.junit`, `@Before`). Provider test classes extending `DataProviderContractTest` use JUnit5 annotations (`@Test` from `org.junit.jupiter.api`, `@BeforeEach`). The `de.mannodermaus.android-junit` plugin handles running both. Widget-specific tests beyond contract base can use either framework — but since the test class already extends a JUnit4 base, keep everything in the same class JUnit4.
**Warning signs:** "No tests found" or "Test framework mismatch" errors.

### Pitfall 8: AccelerometerProvider Gravity Component
**What goes wrong:** Raw accelerometer values include gravity (9.8 m/s^2 on Z axis when stationary), making acceleration readings appear non-zero at rest
**Why it happens:** `TYPE_ACCELEROMETER` includes gravity. Removing gravity requires a low-pass filter or using `TYPE_LINEAR_ACCELERATION`.
**How to avoid:** Implement configurable low-pass filter for gravity removal (alpha configurable via settings). Default ON. Alternative: use `TYPE_LINEAR_ACCELERATION` sensor which has gravity pre-removed by Android — simpler but not available on all devices. Best approach: try `TYPE_LINEAR_ACCELERATION` first, fall back to `TYPE_ACCELEROMETER` with manual gravity filter.
**Warning signs:** Stationary device shows 9.8 m/s^2 acceleration. Dashboard always showing "accelerating."

### Pitfall 9: Settings Map Type Mismatch
**What goes wrong:** Widget reads setting as wrong type — e.g., expects `Boolean` but gets `String "true"`
**Why it happens:** `ProviderSettingsStore` uses type-prefixed serialization (`"b:true"`), but `WidgetRenderer.Render()` receives `ImmutableMap<String, Any>`. The deserialization produces correct types, but code that compares with `settings["key"] == true` may fail if the value came from a different path.
**How to avoid:** Always use the setting definition's `default` value as fallback type reference. Cast with `as? Boolean ?: default`. Never trust the runtime type of map values — always cast defensively.
**Warning signs:** Settings appear to have no effect. Boolean toggles don't toggle.

### Pitfall 10: SpeedLimitProvider Stateless Until Configured
**What goes wrong:** SpeedLimitProvider emits nothing until user configures a speed limit value in settings
**Why it happens:** It reads from `ProviderSettingsStore` with key `essentials:speed-limit:value`. No key = no emission.
**How to avoid:** Define a sensible default in the provider — e.g., emit `SpeedLimitSnapshot(speedLimit = 0f, source = SpeedLimitSource.USER)` when no setting exists. Or emit nothing and let the multi-slot binder handle the missing slot gracefully (Speedometer should render without speed limit overlay when slot is empty).
**Warning signs:** Speedometer never shows speed limit warning even when the user expects it. Speed limit widgets show blank.

## Code Examples

### callbackFlow Sensor Provider (Orientation — port pattern)
```kotlin
override fun provideState(): Flow<OrientationSnapshot> = callbackFlow {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val listener = object : SensorEventListener {
        private val rotationMatrix = FloatArray(9)
        private val orientation = FloatArray(3)
        private var warmupCount = 0

        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            warmupCount++
            if (warmupCount < 10) return // Skip warm-up samples

            val bearing = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

            trySend(OrientationSnapshot(
                bearing = (bearing + 360f) % 360f, // Normalize 0-360
                pitch = pitch,
                roll = roll,
                timestamp = SystemClock.elapsedRealtimeNanos(),
            ))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    sensorManager.registerListener(
        listener,
        rotationSensor,
        SensorManager.SENSOR_DELAY_UI,
        200_000, // 200ms batch for NF14
    )

    awaitClose { sensorManager.unregisterListener(listener) }
}.conflate() // Latest value wins for display-only providers
```

### GpsSpeedProvider (Greenfield)
```kotlin
override fun provideState(): Flow<SpeedSnapshot> = callbackFlow {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val listener = object : LocationListener {
        private var lastLocation: Location? = null

        override fun onLocationChanged(location: Location) {
            val speed = if (location.hasSpeed()) {
                location.speed // m/s from GPS hardware
            } else {
                // Compute from consecutive locations
                lastLocation?.let { prev ->
                    val distance = prev.distanceTo(location)
                    val timeDelta = (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9f
                    if (timeDelta > 0f) distance / timeDelta else 0f
                } ?: 0f
            }
            lastLocation = location

            trySend(SpeedSnapshot(
                speedMps = speed,
                accuracy = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null,
                timestamp = SystemClock.elapsedRealtimeNanos(),
            ))
        }
    }

    try {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L, // 1s interval
            0f,    // 0m distance threshold
            listener,
            Looper.getMainLooper(),
        )
    } catch (e: SecurityException) {
        // Permission not granted — connectionState handles this
        close(e)
    }

    awaitClose { locationManager.removeUpdates(listener) }
}
```

### SpeedLimitProvider (Greenfield — Static User Value)
```kotlin
@Singleton
class SpeedLimitProvider @Inject constructor(
    private val settingsStore: ProviderSettingsStore,
) : DataProvider<SpeedLimitSnapshot> {

    override fun provideState(): Flow<SpeedLimitSnapshot> =
        settingsStore.getSetting("essentials", "speed-limit", "value")
            .map { value ->
                val limitKph = (value as? Number)?.toFloat() ?: 0f
                SpeedLimitSnapshot(
                    speedLimit = limitKph,
                    source = SpeedLimitSource.USER,
                    timestamp = SystemClock.elapsedRealtimeNanos(),
                )
            }
}
```

### Widget Contract Test Extension
```kotlin
class SpeedometerRendererTest : WidgetRendererContractTest() {
    override fun createRenderer(): WidgetRenderer = SpeedometerRenderer()

    override fun createTestWidgetData(): WidgetData = WidgetData.Empty
        .withSlot(SpeedSnapshot::class, SpeedSnapshot(speedMps = 16.67f, accuracy = 1.0f, timestamp = 1L))
        .withSlot(AccelerationSnapshot::class, AccelerationSnapshot(acceleration = 2.5f, timestamp = 1L))
        .withSlot(SpeedLimitSnapshot::class, SpeedLimitSnapshot(speedLimit = 60f, source = SpeedLimitSource.USER, timestamp = 1L))

    // Widget-specific test beyond contract base
    @Test
    fun `computeGaugeMax returns correct threshold at 60 kph`() {
        // Test the auto-scaling gauge max logic
        val max = SpeedometerRenderer.computeGaugeMax(60f)
        assertThat(max).isEqualTo(120f)
    }

    @Test
    fun `computeGaugeMax returns correct threshold at 140 kph`() {
        val max = SpeedometerRenderer.computeGaugeMax(140f)
        assertThat(max).isEqualTo(200f)
    }

    @Test
    fun `arc angle at zero speed is zero`() {
        val angle = SpeedometerRenderer.computeArcAngle(0f, 120f)
        assertThat(angle).isWithin(0.01f).of(0f)
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@RequiresData([DataTypes.SPEED])` annotation | `compatibleSnapshots: Set<KClass<out DataSnapshot>>` property | Phase 2 (new arch) | Type-safe KClass matching replaces string-based data type matching |
| `widgetData: DataSnapshot` parameter to `Render()` | `LocalWidgetData.current` CompositionLocal | Phase 2 (new arch) | Removes parameter coupling; enables deferred reads via `derivedStateOf` |
| `Map<String, Any?>` untyped data | `WidgetData` with `KClass`-keyed typed slots | Phase 2 (new arch) | `snapshot<SpeedSnapshot>()` inline reified access |
| Manual Hilt `@Module` with `@Binds @IntoSet` | KSP auto-generated module from `@DashboardWidget` | Phase 4 (KSP codegen) | Zero-boilerplate registration, prevents missed bindings |
| Old `core:` typeId prefix | New `essentials:` prefix | Project decision | Pack identity change, no functional impact |
| Old `free:` sourceId prefix | New `essentials:` prefix | Project decision | Provider identity change |
| OBD/EXTOL for speed | GPS `LocationManager` for speed | New arch (greenfield) | Old had OBU hardware; new uses phone GPS |
| No battery provider | Greenfield `BatteryProvider` | New arch (greenfield) | Old codebase never had battery monitoring |
| No standalone accelerometer | Greenfield `AccelerometerProvider` | New arch (greenfield) | Old embedded accelerometer inside OrientationProvider |
| OBU-specific speed limits | User-configured static `SpeedLimitProvider` | New arch (greenfield) | Old got limits from Singapore ERP OBU; new is user-set |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 (widget contract tests) + JUnit5 (provider contract tests, unit tests) via `de.mannodermaus.android-junit` |
| Config file | Convention plugins handle setup; no per-module config |
| Quick run command | `./gradlew :pack:essentials:testDebugUnitTest --console=plain` |
| Full suite command | `./gradlew test --console=plain` |
| Estimated runtime | ~30s for pack module, ~90s for full suite |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| F5.1 | Speedometer renders, arc angles correct | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.SpeedometerRendererTest" --console=plain` | No — Wave 0 |
| F5.2 | Clock digital renders with TimeSnapshot | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.ClockDigitalRendererTest" --console=plain` | No — Wave 0 |
| F5.3 | Clock analog hand positions | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.ClockAnalogRendererTest" --console=plain` | No — Wave 0 |
| F5.4 | Date 3 variants render | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.Date*RendererTest" --console=plain` | No — Wave 0 |
| F5.5 | Compass needle rotation | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.CompassRendererTest" --console=plain` | No — Wave 0 |
| F5.6 | Battery renders, provider emits | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.Battery*Test" --console=plain` | No — Wave 0 |
| F5.7 | SpeedLimit circle renders | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.SpeedLimitCircle*Test" --console=plain` | No — Wave 0 |
| F5.8 | Shortcuts tap action | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.ShortcutsRendererTest" --console=plain` | No — Wave 0 |
| F5.9 | Solar arc position at known times | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.SolarRendererTest" --console=plain` | No — Wave 0 |
| F5.10 | Ambient light category display | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.AmbientLightRendererTest" --console=plain` | No — Wave 0 |
| F5.11 | SpeedLimit rectangle renders | unit + contract | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.SpeedLimitRect*Test" --console=plain` | No — Wave 0 |
| NF14 | Sensor batching configured | unit | Verify `maxReportLatencyUs` parameter in provider tests | No — Wave 0 |
| NF40 | Color-blind safe warnings | unit | Speedometer test: warning includes icon + pulsing border, not just color | No — Wave 0 |
| NF-I2 | Locale-aware formatting | unit | Widget test: number formatting uses locale | No — Wave 0 |
| NF-P1 | No GPS coordinates stored | unit | GpsSpeedProvider test: verify no Location reference retained | No — Wave 0 |
| — | SolarCalculator accuracy | unit | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.SolarCalculatorTest" --console=plain` | No — Wave 0 |
| — | RegionDetector fallback chain | unit | `./gradlew :pack:essentials:testDebugUnitTest --tests "*.RegionDetectorTest" --console=plain` | No — Wave 0 |
| — | Multi-slot wiring (Speedometer) | integration | Binder test with delayed AccelerationSnapshot | No — Wave 0 |

### Nyquist Sampling Rate
- **Minimum sample interval:** After every committed task -> run: `./gradlew :pack:essentials:testDebugUnitTest --console=plain`
- **Full suite trigger:** Before merging final task of any plan wave
- **Phase-complete gate:** `./gradlew test --console=plain` all green + regression gate (Phase 2-7 tests pass)
- **Estimated feedback latency per task:** ~30s

### Wave 0 Gaps (must be created before implementation)
- [ ] All snapshot types in `:pack:essentials:snapshots` (compile-time dependency for everything else)
- [ ] Pack-local snapshot types (`SolarSnapshot`, `SpeedLimitSnapshot`)
- [ ] Widget contract test files (13 files, one per widget)
- [ ] Provider contract test files (9 files, one per provider)
- [ ] Greenfield provider unit test files (4 files: GpsSpeed, Battery, Accelerometer, SpeedLimit)
- [ ] Utility test files: `SolarCalculatorTest`, `RegionDetectorTest`
- [ ] Theme JSON files: `slate.theme.json`, `minimalist.theme.json`
- [ ] `WidgetScopeBypass` lint rule + tests (in `:lint-rules`)
- [ ] `play-services-location` dependency in essentials build.gradle.kts

## Open Questions

1. **`@DashboardWidget` annotation field mismatch**
   - What we know: The existing `@DashboardWidget` annotation has `typeId`, `displayName`, `icon` fields. The old codebase used `localId` (just the widget-local part, e.g., "speedometer") with the pack prefix added by KSP.
   - What's unclear: Does the KSP processor expect `typeId` to be the full colon-separated ID (`"essentials:speedometer"`) or just the local part? The annotation field is named `typeId`, suggesting full.
   - Recommendation: Check the `PluginProcessor` in `:codegen:plugin` during implementation. The `packId` KSP arg is set by convention plugin — if the processor concatenates `packId + ":" + localId`, use `localId`-style. If it expects the full string, provide `"essentials:speedometer"`.

2. **BatteryRenderer settings schema**
   - What we know: Old codebase had no BatteryRenderer. Phase-08.md says "BatteryRenderer is greenfield."
   - What's unclear: Exact settings schema. Requirements say "battery level percentage, charging state, optional temperature."
   - Recommendation: Implement minimal settings: `showPercentage` (Boolean, default true), `showTemperature` (Boolean, default false), `chargingIndicator` (Boolean, default true). `InfoCardLayout` as the layout primitive.

3. **`CallActionProvider` — ActionableProvider vs DataProvider**
   - What we know: `CallActionProvider` implements `ActionableProvider`, not `DataProvider<T>`. It's excluded from `DataProviderContractTest` count (phase-08.md says "9 typed data providers pass DataProviderContractTest (CallActionProvider excluded)").
   - What's unclear: What `snapshotType` does `CallActionProvider` declare? `ActionableProvider` extends `DataProvider<T>` — so it must have a `T`. The old code used it for Shortcuts widget tap-to-launch.
   - Recommendation: Use `UnitSnapshot` (already exists in `:sdk:contracts`) as the type parameter. `CallActionProvider` doesn't emit data — it receives `WidgetAction.Tap` and launches an intent. The `provideState()` returns an empty flow or a flow of `UnitSnapshot`.

4. **Date widgets read TimeSnapshot but old used separate DateDataProvider**
   - What we know: phase-08.md says "Formats date from TimeSnapshot — no separate DateSnapshot". The old codebase had a separate `DateDataProvider` wrapping `ClockProvider`.
   - What's unclear: Will `TimeSnapshot` carry enough date information? Currently defined as `epochMillis`, `zoneId`, `timestamp`.
   - Recommendation: `epochMillis` + `zoneId` is sufficient — widget extracts date from epoch using `java.time` APIs. No separate DateSnapshot needed.

## Sources

### Primary (HIGH confidence)
- `/Users/ohm/Workspace/dqxn/android/sdk/contracts/` — Read all contract interfaces (WidgetRenderer, DataProvider, DataSnapshot, WidgetData, WidgetSpec, DataProviderSpec, ActionableProvider, SettingDefinition)
- `/Users/ohm/Workspace/dqxn/android/sdk/contracts/src/testFixtures/` — Read WidgetRendererContractTest (14 assertions, JUnit4) and DataProviderContractTest (12 assertions, JUnit5)
- `/Users/ohm/Workspace/dqxn/android/build-logic/convention/` — Read PackConventionPlugin (auto-wires :sdk:*, KSP, Compose, Hilt, serialization) and SnapshotConventionPlugin (Android library + :sdk:contracts API)
- `/Users/ohm/Workspace/dqxn/android/sdk/ui/` — Read LocalWidgetData, LocalWidgetScope, InfoCardLayout, DashboardThemeDefinition
- `/Users/ohm/Workspace/dqxn/android/data/` — Read ProviderSettingsStore, DashboardWidgetInstance, LayoutRepository
- `/Users/ohm/Workspace/dqxn/.planning/migration/phase-08.md` — Phase specification with widget/provider tables, test requirements, gate criteria
- `/Users/ohm/Workspace/dqxn/.planning/ARCHITECTURE.md` — Module structure, dependency rules
- `/Users/ohm/Workspace/dqxn/.planning/arch/plugin-system.md` — Widget/provider contracts, KSP generation, error isolation, theme system
- `/Users/ohm/Workspace/dqxn/.planning/arch/state-management.md` — Multi-slot WidgetData, merge()+scan(), decomposed state
- `/Users/ohm/Workspace/dqxn/.planning/oldcodebase/packs.md` — Old codebase widget/provider inventory with implementation details

### Secondary (MEDIUM confidence)
- `/Users/ohm/Workspace/dqxn/.planning/migration/replication-advisory.md` §7 — Widget setup architecture (evaluator variants, overlay states, permission card three-state). Relevant for providers with setupSchema but Phase 8 providers are mostly permissionless or GPS-only
- `/Users/ohm/Workspace/dqxn/.planning/REQUIREMENTS.md` — Full F5.1-F5.11, NF14, NF40, NF-I2, NF-P1 requirement text

### Tertiary (LOW confidence)
- GpsSpeedProvider `LocationManager` behavior with `GPS_PROVIDER` — verified from Android SDK docs (training data), but the speed computation fallback (when `hasSpeed() == false`) should be validated on physical device

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries are already in the project, convention plugins tested
- Architecture: HIGH — all contracts read from source, patterns verified against existing infra
- Pitfalls: HIGH — most derived from reading actual code + old codebase mapping docs
- Validation: HIGH — test infrastructure exists, contract test bases read, command patterns established

**Research date:** 2026-02-24
**Valid until:** 2026-03-24 (stable — all dependencies are locked, no fast-moving externals)
