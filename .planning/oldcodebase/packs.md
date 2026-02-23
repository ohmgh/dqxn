# Old Codebase: Pack Modules

**Source:** `/Users/ohm/Workspace/dqxn.old/android/feature/packs/`
**Packs:** `free`, `demo`, `themes`, `sg-erp2`

---

## 1. Free Pack (`feature/packs/free`)

### 1.1 Identity

| Field | Value |
|---|---|
| Old packId | `core` |
| Old module path | `feature/packs/free` |
| Old namespace | `app.dqxn.android.feature.packs.free` |
| Display name | "Essentials" |
| Category | `AUTOMOTIVE` |
| isCore | `true` |
| Manifest file | `FreePackManifest.kt` |
| KSP marker | `FreePackResources.kt` (`@PackResourceMarker`) |
| New packId | `essentials` |
| New module path | `pack/essentials` |
| New namespace | `app.dqxn.android.pack.essentials` |

**Manifest class** (`FreePackManifest.kt`):
- Uses KSP-generated `generatedWidgetRefs`, `generatedProviderRefs`, `generatedThemeRefs`
- Object with `val manifest = DashboardPackManifest(...)`

### 1.2 Build Dependencies

**File:** `feature/packs/free/build.gradle.kts`

```
implementation(project(":core:plugin-api"))
implementation(project(":core:widget-primitives"))
implementation(libs.play.services.location)     // FusedLocationProviderClient for SolarLocationDataProvider
implementation(libs.showkase)                    // Preview catalog
implementation(libs.kotlinx.serialization.json)  // JSON for timezone coords
ksp(project(":core:plugin-processor"))           // KSP code generation
```

Also has an IANA timezone coordinate generation task from `zone1970.tab`.

### 1.3 DI Wiring

**File:** `di/FreePackModule.kt` — Abstract `@Module @InstallIn(SingletonComponent::class)` with `@Binds @IntoSet`:

| Binding Type | Count | Implementations |
|---|---|---|
| `WidgetRenderer` | 12 | SpeedometerRenderer, ClockDigitalRenderer, ClockAnalogRenderer, DateSimpleRenderer, DateStackRenderer, DateGridRenderer, ShortcutsRenderer, SpeedLimitCircleRenderer, SpeedLimitRectRenderer, CompassRenderer, SolarRenderer, AmbientLightRenderer |
| `DataProvider` | 6 | TimeDataProvider, DateDataProvider, OrientationDataProvider, SolarTimezoneDataProvider, SolarLocationDataProvider, AmbientLightDataProvider |
| `ActionProvider` | 1 | CallActionProvider |
| `ThemeProvider` | 1 | FreePackThemeProvider |

**File:** `di/FreePackProvidesModule.kt` — `@Provides @IntoSet` for `DashboardPackManifest`.

### 1.4 Data Providers

#### TimeDataProvider
- **File:** `providers/TimeDataProvider.kt`
- **sourceId:** `free:time`
- **dataType:** `DataTypes.TIME`
- **Schema:** `formattedTime` (String), `formattedDate` (String), `hour` (Int), `minute` (Int), `second` (Int)
- **Implementation:** Wraps shared `ClockProvider`. Maps `currentTime`+`currentDate` StateFlows into a single `DataSnapshot` via `combine`.
- **New sourceId:** `essentials:time`

#### DateDataProvider
- **File:** `providers/DateDataProvider.kt`
- **sourceId:** `free:date`
- **dataType:** `DataTypes.DATE`
- **Schema:** `formattedDate` (String), `dayOfWeek` (String), `dayOfMonth` (Int), `month` (String), `year` (Int)
- **Implementation:** Wraps shared `ClockProvider`. Maps `currentDate` StateFlow.
- **New sourceId:** `essentials:date`

#### ClockProvider (internal, not a DataProvider)
- **File:** `providers/ClockProvider.kt`
- **Purpose:** Shared clock tick source consumed by TimeDataProvider and DateDataProvider.
- **Implementation:** `callbackFlow` with 1-second `delay` loop. `stateIn(scope, WhileSubscribed(5000), ...)`. Exposes `currentTime: StateFlow<LocalTime>`, `currentDate: StateFlow<LocalDate>`, `formattedTime: StateFlow<String>`, `formattedDate: StateFlow<String>`.
- **Pattern:** Single clock, multiple consumers. `@Singleton @Inject`.

#### OrientationDataProvider
- **File:** `providers/OrientationDataProvider.kt`
- **sourceId:** `free:orientation`
- **dataType:** `DataTypes.ORIENTATION`
- **Schema:** `heading` (Double), `pitch` (Double), `roll` (Double), `hasSensor` (Bool)
- **Implementation:** Wraps internal `OrientationProvider`.
- **New sourceId:** `essentials:orientation`

#### OrientationProvider (internal, not a DataProvider)
- **File:** `providers/OrientationProvider.kt`
- **Purpose:** Raw sensor fusion for compass heading, pitch, roll.
- **Implementation:** `callbackFlow` + `SensorManager`. Primary: `TYPE_ROTATION_VECTOR`. Fallback: `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD` with `getRotationMatrix()`/`getOrientation()`. Low-pass filter (`alpha = 0.15f`), hysteresis (`threshold = 0.5f`), warm-up period (10 samples before filtering). `stateIn(scope, WhileSubscribed(5000), ...)`.

#### SolarLocationDataProvider
- **File:** `providers/SolarLocationDataProvider.kt`
- **sourceId:** `free:solar_location`
- **dataType:** `DataTypes.SOLAR`
- **Schema:** `sunrise` (String HH:mm), `sunset` (String HH:mm), `solarNoon` (String HH:mm), `isDaytime` (Bool), `sourceMode` ("location")
- **Setup:** Requires `ACCESS_COARSE_LOCATION` via `SetupDefinition.RuntimePermission`. `isAvailable` gated on permission.
- **Implementation:** `FusedLocationProviderClient` with `PRIORITY_PASSIVE`. `coordinatesFlow: MutableStateFlow<Coordinates?>`. `flatMapLatest` to recalculate on coordinate change. `computeDelayUntilNextTransition()` for zero-polling (sleeps until next sunrise/sunset/midnight).
- **New sourceId:** `essentials:solar-location`

#### SolarTimezoneDataProvider
- **File:** `providers/SolarTimezoneDataProvider.kt`
- **sourceId:** `free:solar_timezone`
- **dataType:** `DataTypes.SOLAR`
- **Schema:** Same as SolarLocationDataProvider but `sourceMode` = "timezone"
- **Implementation:** No permissions needed. Uses `IanaTimezoneCoordinates` (312 pre-generated timezone lat/lng pairs from `zone1970.tab`). Listens for `ACTION_TIMEZONE_CHANGED` broadcast via `callbackFlow`. Recalculates at solar boundaries.
- **New sourceId:** `essentials:solar-timezone`

#### SolarCalculator (utility, not a provider)
- **File:** `providers/SolarCalculator.kt`
- **Purpose:** Meeus/NOAA solar position algorithm.
- **API:** `SolarCalculator.calculate(lat, lng, dateTime): SolarTimes` returning `SolarTimes(sunrise: LocalTime, sunset: LocalTime, solarNoon: LocalTime, isDaytime: Boolean)`.
- **Implementation:** Full Julian date calculation, equation of time, hour angle, declination. Handles polar edge cases.

#### IanaTimezoneCoordinates (data, not a provider)
- **File:** `providers/IanaTimezoneCoordinates.kt`
- **Purpose:** 312 IANA timezone → (latitude, longitude) mappings. Generated from IANA `zone1970.tab`.
- **Note:** Large file. Build task generates it.

#### AmbientLightDataProvider
- **File:** `providers/AmbientLightDataProvider.kt`
- **sourceId:** `free:ambient_light`
- **dataType:** `DataTypes.AMBIENT_LIGHT`
- **Schema:** `luxLevel` (Double), `category` (String: DARK/DIM/NORMAL/BRIGHT/VERY_BRIGHT), `isDim` (Bool), `hasSensor` (Bool)
- **Implementation:** `callbackFlow` + `SensorManager.TYPE_LIGHT`. `@Volatile lastKnownLux` for replay across flow restarts. Categorization: <10 DARK, <50 DIM, <500 NORMAL, <10000 BRIGHT, else VERY_BRIGHT. `stateIn(scope, WhileSubscribed(5000), ...)`.
- **New sourceId:** `essentials:ambient-light`

#### CallActionProvider
- **File:** `providers/CallActionProvider.kt`
- **actionId:** `free:call`
- **Implementation:** `ACTION_CALL` with `CALL_PHONE` permission, fallback to `ACTION_DIAL`.
- **New actionId:** `essentials:call`

### 1.5 Widgets

#### Speedometer
- **Files:** `widgets/speedometer/SpeedometerRenderer.kt`, `SpeedometerWidget.kt`, `SpeedometerSettings.kt`
- **Old typeId:** `core:speedometer`
- **New typeId:** `essentials:speedometer`
- **Annotation:** `@DashboardWidget(localId = "speedometer", displayName = "Speedometer", icon = "speed")`
- **Data:** `@RequiresData([DataTypes.SPEED, DataTypes.ACCELERATION, DataTypes.SPEED_LIMIT])`
- **Implements:** `WidgetRenderer`, `SettingsAwareSizer`
- **Default size:** 12x12, square, aspect ratio preserved
- **Settings:**
  - `showSpeedArc` (BooleanSetting, default true, group "arcs")
  - `showAccelerationArc` (BooleanSetting, default true, group "arcs")
  - `arcStrokeDp` (IntSetting, default 12, presets, conditionally visible)
  - `showTickMarks` (EnumSetting: NONE/MAJOR_ONLY/ALL, default ALL)
  - `speedUnit` (EnumSetting: KPH/MPH/AUTO, default AUTO)
  - `speedLimitOffsetKph` (IntSetting, visible when speedUnit != MPH)
  - `speedLimitOffsetMph` (IntSetting, visible when speedUnit == MPH)
  - `showWarningBackground` (BooleanSetting, default true)
  - `alertType` (EnumSetting: SILENT/VIBRATE/BEEP/TTS, default SILENT)
- **Render:** Canvas-based circular gauge. Speed arc + acceleration arc (segmented). Tick marks. Auto-detect gauge max via `computeGaugeMax()` (stepped thresholds: 120→320). Warning system with amber/red backgrounds based on speed limit offset. Backward compat for old `useImperial` boolean setting. `RegionDetector.detect()` for AUTO unit resolution.
- **`computeSize()`:** Base 10 units + 1 per visible arc, clamped 10-14.

#### Clock (2 variants)
- **Files:** `widgets/clock/ClockDigitalRenderer.kt`, `ClockAnalogRenderer.kt`, `ClockWidget.kt`, `ClockSettings.kt`
- **Old typeIds:** `core:clock-digital`, `core:clock-analog` (baseTypeId: `core:clock`)
- **New typeIds:** `essentials:clock-digital`, `essentials:clock-analog`
- **Digital annotation:** `@DashboardWidget(localId = "clock-digital", displayName = "Clock (Digital)", icon = "clock")`
- **Analog annotation:** `@DashboardWidget(localId = "clock-analog", displayName = "Clock (Analog)", icon = "clock")`
- **Data:** `@RequiresData([DataTypes.TIME])`
- **Shared settings schema:** `showSeconds`, `use24HourFormat`, `showLeadingZero`, `timezoneId` (TimezoneSetting), `timeSize` (EnumSetting: TINY/SMALL/MEDIUM/LARGE/HUGE), `timezoneSize` (EnumSetting), `timezonePosition` (EnumSetting: LEFT/RIGHT/NONE), `showTickMarks` (analog only)
- **Digital Render:** AnnotatedString with colored hour:minute:seconds + optional AM/PM + optional timezone label. `derivedStateOf` for time extraction.
- **Analog Render:** Canvas with hour/minute/second hands, tick marks, optional timezone label. `drawWithCache` for static elements.
- **Digital default:** 14x6. **Analog default:** 12x12 square.
- **Both implement:** `SettingsAwareSizer` with timezone-based width adjustment.

#### Date (3 variants)
- **Files:** `widgets/date/DateSimpleRenderer.kt`, `DateStackRenderer.kt`, `DateGridRenderer.kt`, `DateWidget.kt`, `DateSettings.kt`
- **Old typeIds:** `core:date-simple`, `core:date-stack`, `core:date-grid` (baseTypeId: `core:date`)
- **New typeIds:** `essentials:date-simple`, `essentials:date-stack`, `essentials:date-grid`
- **Data:** `@RequiresData([DataTypes.DATE])`
- **Simple:** Single line, colored segments (weekday, day, month). `SettingsAwareSizer` with format-based width. Default varies by format.
- **Stack:** Vertical calendar tile: weekday / large day / month. Fixed 6x7.
- **Grid:** Horizontal: big day number + stacked weekday/month. Fixed 10x6.
- **Settings:** `dateFormat` (DateFormatSetting with locale-aware options), `timezoneId` (TimezoneSetting).

#### Compass
- **Files:** `widgets/compass/CompassRenderer.kt`, `CompassWidget.kt`, `CompassSettings.kt`
- **Old typeId:** `core:compass`
- **New typeId:** `essentials:compass`
- **Data:** `@RequiresData([DataTypes.ORIENTATION])`
- **Default size:** 10x10, square
- **Settings:** `cardinalLabelStyle` (EnumSetting: NONE/SPARSE/DENSE), `showTiltIndicators` (BooleanSetting, default true)
- **Render:** Rotating dial with tick marks (every 5deg minor, every 30deg major). Cardinal labels at N/E/S/W (SPARSE) or +NE/SE/SW/NW (DENSE). Fixed needle (red N tip, white S). Tilt indicators showing pitch/roll via offset circle. Heading text overlay.

#### Solar
- **Files:** `widgets/solar/SolarRenderer.kt`, `SolarWidget.kt`, `SolarSettings.kt`
- **Old typeId:** `core:solar`
- **New typeId:** `essentials:solar`
- **Data:** `@RequiresData([DataTypes.SOLAR])`
- **Implements:** `SettingsAwareSizer`
- **Default size:** Varies by display mode and arc visibility (8x8 to 12x12)
- **Settings:**
  - `displayMode` (EnumSetting: TIMES/NEXT_EVENT)
  - `nextEventFormat` (EnumSetting: TIME/COUNTDOWN, visible when NEXT_EVENT)
  - `arcSize` (EnumSetting with NONE/SMALL/MEDIUM/LARGE presets)
  - `timezoneId` (TimezoneSetting)
- **Render:** 1217-line widget. 24-hour arc visualization with dawn/day/dusk/night bands. Celestial markers (sun circle, moon crescent). Two layout modes: Times (sunrise/sunset/noon displayed) and NextEvent (countdown or time to next sunrise/sunset). Timezone badge. Complex Canvas drawing with `drawWithCache`, `graphicsLayer` for animations.
- **Notable:** Can calculate solar times directly for custom timezones using `IanaTimezoneCoordinates` + `SolarCalculator` (bypasses provider when timezone differs from device).

#### Speed Limit (2 variants)
- **Files:** `widgets/speedlimit/SpeedLimitCircleRenderer.kt` (typeId `core:speedlimit:circle`), `SpeedLimitRectRenderer.kt` (typeId `core:speedlimit:rect`), `SpeedLimitWidget.kt`, `SpeedLimitSettings.kt`
- **Old typeIds:** `core:speedlimit:circle`, `core:speedlimit:rect`
- **New typeIds:** `essentials:speedlimit-circle`, `essentials:speedlimit-rect`
- **Data:** `@RequiresData([DataTypes.SPEED_LIMIT])`
- **Circle (European):** Red ring, white background, black (or blue for Japan) digits. Settings: `borderSizePercent`, `speedUnit`, `digitColor` (AUTO/BLACK/BLUE). Default 9x9 square.
- **Rect (US MUTCD):** Black border, white background, "SPEED LIMIT" header. Settings: `speedUnit`. Default 9x10.
- **Both:** `SpeedUnit.AUTO` + `RegionDetector` for automatic km/h vs mph. Backward compat for old `useImperial` boolean.

#### Shortcuts (App Launcher)
- **Files:** `widgets/shortcuts/ShortcutsRenderer.kt`, `ShortcutsWidget.kt`, `ShortcutsSettings.kt`
- **Old typeId:** `core:shortcuts`
- **New typeId:** `essentials:shortcuts`
- **Data:** None required
- **Settings:** `packageName` (AppPickerSetting), `displayName` (StringSetting)
- **Render:** `InfoCardLayout` with app icon. Tap launches app or opens Play Store if not installed. Suggested apps list in settings.

#### Ambient Light
- **Files:** `widgets/ambientlight/AmbientLightRenderer.kt`, `AmbientLightWidget.kt`, `AmbientLightSettings.kt`
- **Old typeId:** `core:ambient_light`
- **New typeId:** `essentials:ambient-light`
- **Data:** `@RequiresData([DataTypes.AMBIENT_LIGHT])`
- **Settings:** None (empty settings class)
- **Render:** `InfoCardLayout` with custom `LightBulbPainter` icon. Displays lux value and category name. Color tint changes by category.

### 1.6 Themes

**Files:** `theme/FreePackThemeMarker.kt` (`@ThemePackMarker`), `theme/FreePackThemeProvider.kt`

- **Old packId for themes:** `free`
- **New packId for themes:** `essentials`
- Provides 2 free themes: Minimalist (light) + Slate (dark)
- Uses KSP-generated `allThemes` from JSON definitions
- `ThemeProvider` interface: `packId: String`, `getThemes(): List<DashboardTheme>`

### 1.7 Other Files

- `OversteerShowkaseRoot.kt` — Showkase preview catalog root (`@ShowkaseRoot`)
- `previews/WidgetPreviews.kt` — Composable previews for Showkase browser

---

## 2. Demo Pack (`feature/packs/demo`)

### 2.1 Identity

| Field | Value |
|---|---|
| Old packId | `demo` |
| Old module path | `feature/packs/demo` |
| Old namespace | `app.dqxn.android.feature.packs.demo` |
| Display name | "Demo" |
| isCore | `false` |
| Widgets | None (providers only) |
| Themes | None |

### 2.2 Build Dependencies

**File:** `feature/packs/demo/build.gradle.kts`

```
implementation(project(":core:plugin-api"))
implementation(project(":core:common"))
implementation(libs.kotlinx.coroutines.core)
implementation(libs.kotlinx.coroutines.android)
implementation(libs.kotlinx.serialization.json)
```

No widget primitives needed (no widgets rendered).

### 2.3 DI Wiring

**File:** `di/DemoPackModule.kt` — `@Binds @IntoSet` for 8 `DataProvider` bindings:
- `DemoSpeedDataProvider`
- `DemoAccelerationDataProvider`
- `DemoBalanceDataProvider`
- `DemoChargingDataProvider`
- `DemoHistoryDataProvider`
- `DemoSpeedLimitDataProvider`
- `DemoTrafficDataProvider`
- `DemoDeviceDataProvider`

**File:** `di/DemoPackProvidesModule.kt` — `@Provides @IntoSet` for manifest.

### 2.4 Data Providers

**File:** `providers/DemoDataProviders.kt` — 8 provider classes, all `@Singleton`, each wrapping `DemoSimulator` flows.

| Provider | sourceId | dataType | Schema Fields |
|---|---|---|---|
| `DemoSpeedDataProvider` | `demo:speed` | `SPEED` | `speed` (Double) |
| `DemoAccelerationDataProvider` | `demo:acceleration` | `ACCELERATION` | `acceleration` (Double) |
| `DemoBalanceDataProvider` | `demo:balance` | `BALANCE` | `balance` (Int), `status` (String) |
| `DemoChargingDataProvider` | `demo:charging` | `CHARGING` | `amountCents` (Int), `zoneName` (String) |
| `DemoHistoryDataProvider` | `demo:history` | `HISTORY` | `totalCents` (Int), `transactionCount` (Int), `summary` (String) |
| `DemoSpeedLimitDataProvider` | `demo:speed_limit` | `SPEED_LIMIT` | `speedLimit` (Int) |
| `DemoTrafficDataProvider` | `demo:traffic` | `TRAFFIC` | Full traffic schema (trafficType, hasAlert, message, iconId, etc.) |
| `DemoDeviceDataProvider` | `demo:device` | `DEVICE` | `deviceName` (String), `isConnected` (Bool), `isSearching` (Bool), `isConnecting` (Bool) |

All providers follow the same pattern: map simulator flow → DataSnapshot.

### 2.5 Simulator

**File:** `simulation/DemoSimulator.kt`

`@Singleton` class with 4 grouped `StateFlow`s:

| Flow | Update Rate | Data |
|---|---|---|
| `motionFlow` | 200ms | speed (sinusoidal 0-120 km/h), acceleration (computed from delta speed) |
| `financialFlow` | 200ms | balance (cycling 500-5000 cents), charging (100-500 cents), history (accumulating) |
| `infoFlow` | 1s | speed limit (cycling 40-120), traffic (scenario rotation), device (always connected) |
| `erpFlow` | varies | Traffic + card scenario simulation |

All flows use `callbackFlow` with `delay` loops and `stateIn(WhileSubscribed)`.

**File:** `simulation/ErpSimulation.kt`
- `TrafficSimulation` data class with fields matching traffic schema
- `TrafficScenarios` — 6 predefined scenarios: incident, safety, roadworks, parking, travelTime, none
- `CardScenarios` — 4 predefined states: detected, lowBalance, noCard, insufficientBalance

**File:** `simulation/CaptureMode.kt`
- `CapturedSession`, `CapturedEvent` data classes
- `CaptureSessionRecorder`, `CaptureSessionPlayer` interfaces (not implemented — future feature for recording/replaying real OBU sessions)

### 2.6 Migration Notes

- Demo pack stays as `demo` in new codebase
- No widgets or themes to migrate
- Providers simulate all DataTypes used by other packs
- Useful for testing without real hardware
- `CaptureMode` interfaces are stubs — drop or defer

---

## 3. Themes Pack (`feature/packs/themes`)

### 3.1 Identity

| Field | Value |
|---|---|
| Old packId | `themes` |
| Old module path | `feature/packs/themes` |
| Old namespace | `app.dqxn.android.feature.packs.themes` |
| Display name | "Premium Themes" |
| Theme count | 22 (11 dark + 11 light) |
| entitlementId | `themes` |

### 3.2 Build Dependencies

**File:** `feature/packs/themes/build.gradle.kts`

```
implementation(project(":core:plugin-api"))
implementation(project(":core:widget-primitives"))
implementation(project(":core:common"))
implementation(project(":data:persistence"))
implementation(project(":feature:packs:free"))   // VIOLATION: depends on another pack's feature module
```

**Dependency violation:** `:feature:packs:free` is imported. In the new architecture, packs depend only on `:sdk:*` and `:pack:*:snapshots`. This dependency likely exists for shared theme infrastructure (`ThemeProvider` interface, theme JSON format) that should live in `:sdk:contracts` or `:sdk:ui`.

### 3.3 DI Wiring

**File:** `di/ThemesPackModule.kt` — Single `@Binds @IntoSet` for `ThemeProvider`:
```kotlin
@Binds @IntoSet
abstract fun bindThemesPackThemeProvider(impl: ThemesPackThemeProvider): ThemeProvider
```

**File:** `di/ThemesPackProvidesModule.kt` — `@Provides @IntoSet` for manifest.

### 3.4 Theme Provider

**File:** `theme/ThemesPackThemeProvider.kt`

```kotlin
class ThemesPackThemeProvider @Inject constructor() : ThemeProvider {
    override val packId = "themes"
    override fun getThemes() = allThemes  // KSP-generated from JSON
}
```

**File:** `theme/ThemesPackThemeMarker.kt` — `@ThemePackMarker` annotation for KSP discovery.

### 3.5 Manifest

**File:** `ThemesPackManifest.kt`
- packId = `themes`, version = 1, isCore = false
- 22 premium themes listed
- entitlementId = `themes` (requires purchase)

### 3.6 Migration Notes

- Themes pack stays as `themes` in new codebase at `pack/themes`
- Must break dependency on `:feature:packs:free` — extract shared theme infrastructure to `:sdk:*`
- KSP theme generation pattern can remain
- Theme JSON definitions presumably in `src/main/resources/` (generated by KSP from JSON files)

---

## 4. SG-ERP2 Pack (`feature/packs/sg-erp2`)

### 4.1 Identity

| Field | Value |
|---|---|
| Old packId | `sg-erp2` |
| Old module path | `feature/packs/sg-erp2` |
| Old namespace | `app.dqxn.android.feature.packs.sgerp2` |
| Display name | "SG ERP 2.0" |
| isCore | `false` |
| isRegional | `true` |
| region | `sg` |
| entitlementId | `sg-erp2` |
| Category | `AUTOMOTIVE` |

### 4.2 Build Dependencies

**File:** `feature/packs/sg-erp2/build.gradle.kts`

```
implementation(project(":feature:packs:free"))       // VIOLATION
implementation(project(":core:plugin-api"))
implementation(project(":core:common"))
implementation(project(":core:widget-primitives"))
implementation(project(":data:persistence"))          // PairedDeviceStore for CDM
implementation(libs.extol.sdk)                        // Singapore LTA OBU SDK (proprietary)
implementation(libs.datastore.preferences)            // Pack-local preferences
implementation(libs.kotlinx.serialization.json)
ksp(project(":core:plugin-processor"))
```

**BuildConfig:** `OBU_SDK_KEY` from `local.properties` or env var. `buildFeatures { buildConfig = true }`.

**Dependency violations:**
1. `:feature:packs:free` — likely for shared widget primitives or SpeedUnit enum. Must extract to `:sdk:*`.
2. `:data:persistence` — for `PairedDeviceStore`. Must extract device pairing API to `:sdk:contracts`.

### 4.3 DI Wiring

**File:** `di/ObuModule.kt` — `@Module @InstallIn(SingletonComponent::class)`:

| Binding Type | Count | Implementations |
|---|---|---|
| `SdkKeyProvider` | 1 | `RealSdkKeyProvider` (singleton) |
| `CompanionDeviceHandler` | 1 | `ObuConnectionManager` (into set) |
| `DataProvider` | 8 | ObuSpeed, ObuCard, ObuAcceleration, ObuSpeedLimit, ObuTraffic, ObuHistory, ObuCharging, ObuDevice |
| `WidgetRenderer` | 4 | ObuCardRenderer, DeviceRenderer, TrafficRenderer, HistoryRenderer |

**File:** `di/SgErp2PackProvidesModule.kt` — `@Provides @IntoSet` for manifest.

### 4.4 SDK Bridge Layer

#### ObuSdkBridge
- **File:** `sdk/ObuSdkBridge.kt`
- **Purpose:** Pure wrapper around Singapore LTA `OBUSDK`. Handles initialization (with retry), connection, disconnection, and data streaming.
- **SDK imports:** `sg.gov.lta.obu.sdk.data.services.OBUSDK`, `sg.gov.lta.obu.sdk.core.*`, etc.
- **Event flows** (all `MutableSharedFlow` with `replay = 1`):
  - `velocity: Flow<ObuEvent.Velocity>`
  - `cardInfo: Flow<ObuEvent.CardInfo>`
  - `paymentHistory: Flow<ObuEvent.PaymentHistory>`
  - `traffic: Flow<ObuEvent.Traffic>`
  - `travelSummary: Flow<ObuEvent.TravelSummary>`
  - `totalTripCharged: Flow<ObuEvent.TotalTripCharged>`
  - `charging: Flow<ObuEvent.Charging>` (extraBufferCapacity = 2)
  - `acceleration: Flow<ObuEvent.Acceleration>`
  - `speedLimit: Flow<ObuEvent.SpeedLimit>`
  - `errors: Flow<AppError>` (extraBufferCapacity = 2)
- **Init:** `OBUSDK.init(context, sdkKey, listener)` with `suspendCancellableCoroutine`. Retries up to 3 times with exponential backoff (1s, 2s, 4s). Retryable: network/timeout/server errors. Non-retryable: auth/signature errors.
- **Connect:** `OBUSDK.connect(obu, listener)` via `suspendCancellableCoroutine` with 30s timeout (`withTimeoutOrNull`). Gets `BluetoothDevice` from `BluetoothAdapter.getRemoteDevice(mac)`. Sets `OBUDataListener` on success.
- **Disconnect:** `OBUSDK.disconnect()` + `clearReplayCaches()` (resets all replay caches to prevent stale data).
- **Data listener:** Implements `OBUDataListener` with callbacks for velocity, acceleration, charging, card info, traffic, travel summary, payment history. All use `tryEmit` to shared flows.

#### SdkKeyProvider
- **File:** `sdk/SdkKeyProvider.kt`
- **Interface:** `SdkKeyProvider { fun getSdkKey(): String }`
- **Implementation:** `RealSdkKeyProvider` reads `BuildConfig.OBU_SDK_KEY`
- **Testing:** Abstracted for `@TestInstallIn` with fake key.

#### ObuErrorMapper
- **File:** `sdk/mapper/ObuErrorMapper.kt`
- **Purpose:** Maps `OBUError` codes → domain `AppError` types.
- **Mappings:** Network (unavailable/timeout/server), Bluetooth (disabled/connection failed/not paired), Search, SDK Auth (auth required/invalid key/developer unauthorized/application unauthorized/developer deactivated/code signature fail/no public key), Permission (OBU data access), Device, Unknown.

#### ObuEventMapper
- **File:** `sdk/mapper/ObuEventMapper.kt`
- **Purpose:** Maps SDK data types → domain `ObuEvent` types.
- **Key mappings:**
  - `OBUCardStatus` → `CardStatus` (10 states including Blacklisted, Expired, NfcError)
  - `OBUChargingType` → `ChargingType` (PointBased, DistanceBased, EntryExit, CommonAlert, Erp1)
  - `OBUChargingMessageType` → `ChargingMessageType` (AlertPoint, EnteringPricedRoad, Charging, DeductionSuccessful, etc.)
  - `OBUBusinessFunction` → `BusinessFunction` (ERP, EEP, EPS)
  - `OBUTextColor` → `TextColor`, `OBUTextStyle` → `TextStyle`
  - Traffic: Template-based parsing (1=Incident, 2/12/13=Safety, 8=Roadworks, 110=TravelTime, 111=Parking, else=General)
  - Traffic text parsing with regex: road extraction, speed limit extraction, action extraction
- **Incomplete:** `mapTravelSummary()` returns placeholder zeros (unresolved SDK fields).

### 4.5 Connection Management

#### ObuConnectionManager
- **File:** `connection/ObuConnectionManager.kt`
- **Implements:** `CompanionDeviceHandler`, `DeviceManagement`
- **Purpose:** Manages OBU Bluetooth connection lifecycle. Registers as CDM handler for `OBU-*` prefixed devices.
- **State:** `connectionState: StateFlow<ConnectionState>` (Idle/Searching/Connecting/Connected/Disconnected/Error)
- **CDM integration:** `DeviceHandlerRegistration` with `handlerId = "sg-erp2:obu"`, `allowedNamePrefixes = setOf("OBU-")`.
- **Multi-device:** First-device-wins. On disconnect, tries next available from `deviceRegistry.getPresentDevices()`.
- **Retry:** `connectWithRetryLocked()` with 3 attempts, exponential backoff (500ms, 1s, 2s). Error classification: retryable (connection failed, timeout, search failed, SDK error, network, unknown) vs non-retryable (BT disabled, permissions, auth).
- **Device management:** `getPairedDevices()` queries CDM `myAssociations`, maps to `PairedDevice` with status (CONNECTED/AVAILABLE/UNAVAILABLE). `forgetDevice()` disconnects, removes CDM association, updates paired store.
- **Dependencies:** `ObuSdkBridge`, `SgErp2PackPreferencesRepository`, `ConnectionNotifier`, `DeviceServiceRegistry`, `PairedDeviceStore`.

#### ConnectionState
- **File:** `model/ConnectionState.kt`
- **Sealed interface:** `Idle`, `Searching`, `Connecting(obu)`, `Connected(obu)`, `Disconnected(reason)`, `Error(error)`
- **DisconnectReason enum:** `UserRequested`, `BluetoothDisabled`, `OutOfRange`, `Timeout`, `Unknown`

#### ObuDevice
- **File:** `model/ObuDevice.kt`
- **Data class:** `name: String`, `macAddress: String`

#### SgErp2PackPreferencesRepository
- **File:** `repository/SgErp2PackPreferencesRepository.kt`
- **Purpose:** Pack-local Preferences DataStore (`sgerp2_pack_preferences`).
- **Stores:** `connected_device_name`, `connected_device_mac`
- **API:** `connectedDevice: Flow<ConnectedObuDevice?>`, `saveConnectedDevice()`, `clearConnectedDevice()`, `clearAll()`
- **Pattern:** Separate DataStore file per pack for privacy isolation.

### 4.6 Domain Models

#### ObuEvent
- **File:** `model/ObuEvent.kt`
- **Sealed interface** with timestamp. Subtypes:
  - `Velocity(velocityKmh: Double)`
  - `Acceleration(accelerationMps2: Double)`
  - `SpeedLimit(limitKmh: Int?, source: String)`
  - `CardInfo(balanceCents: Int, status: CardStatus)`
  - `Charging(type, messageType, businessFunction, amountCents, zoneName)`
  - `Traffic(info: TrafficInfo, priority: TrafficPriority)`
  - `TravelSummary(distanceMeters, durationSeconds, averageSpeedKmh)`
  - `TotalTripCharged(totalCents: Int)`
  - `PaymentHistory(entries: List<PaymentHistoryEntry>)`
- **Supporting enums:** `CardStatus` (10 states), `ChargingType` (6), `ChargingMessageType` (8), `BusinessFunction` (5), `TrafficPriority` (3), `TextColor` (7), `TextStyle` (2)
- **TrafficInfo sealed interface:** `Incident`, `Safety`, `Roadworks`, `General`, `Parking`, `TravelTime` — each with typed parsed fields + raw `lines: List<TrafficTextLine>` + `iconId`

### 4.7 Data Providers

**File:** `providers/ObuDataProviders.kt` — All 8 providers in one file.

All providers share a common pattern:
1. Gate on `connectionManager.connectionState` via `flatMapLatest`
2. When `Connected`: emit real SDK data from `sdkBridge.*` flow, mapped to `DataSnapshot`
3. When not connected: emit disconnected snapshot with `null` values
4. Expose `connectionState`, `hasConnectionError`, `connectionErrorDescription` flows
5. All have `requiredAnyEntitlement = listOf("sg-erp2")`

| Provider | sourceId | dataType | SDK Flow | Schema |
|---|---|---|---|---|
| `ObuSpeedDataProvider` | `sg-erp2:speed` | `SPEED` | `sdkBridge.velocity` | `speed` (Double) |
| `ObuCardDataProvider` | `sg-erp2:card` | `BALANCE` | `sdkBridge.cardInfo` | `balance` (Int cents), `status` (String) |
| `ObuAccelerationDataProvider` | `sg-erp2:acceleration` | `ACCELERATION` | `sdkBridge.acceleration` | `acceleration` (Double m/s2) |
| `ObuSpeedLimitDataProvider` | `sg-erp2:speed_limit` | `SPEED_LIMIT` | `sdkBridge.speedLimit` | `speedLimit` (Int kmh) |
| `ObuTrafficDataProvider` | `sg-erp2:traffic` | `TRAFFIC` | `sdkBridge.traffic` | 18 fields (trafficType, hasAlert, priority, message, iconId, defaultIconName, linesJson, road, location, action, speedLimitKmh, warning, nearLocation, parkingLocation, lotsAvailable, destination, travelTimeMinutes) |
| `ObuHistoryDataProvider` | `sg-erp2:history` | `HISTORY` | `sdkBridge.paymentHistory` | `totalCents`, `transactionCount`, `summary` |
| `ObuChargingDataProvider` | `sg-erp2:charging` | `CHARGING` | `sdkBridge.charging` | `amountCents`, `zoneName` |
| `ObuDeviceDataProvider` | `sg-erp2:device` | `DEVICE` | `connectionManager.connectionState` | `deviceName`, `isConnected`, `isSearching`, `isConnecting` |

**Notable:** `ObuDeviceDataProvider` has an extensive `setupSchema` with 3 pages:
1. **Registration** — OneMotoring registration guide with step-by-step instructions. `SetupDefinition.Instruction` with `ClipboardVerification` for MAC address, `InstructionAction.OpenSystemSettings` and `InstructionAction.OpenUrl`.
2. **Permissions** — `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (minSdk S), OBU_DATA_ACCESS custom permission.
3. **Device** — System service checks (Bluetooth, Location) + `SetupDefinition.DeviceScan` with CDM handler ID, OBU- name pattern, max 3 devices.

**Error mapping utility:** `mapErrorToDescription(error: AppError): String` — maps all AppError subtypes to user-facing strings. Used by all providers for `connectionErrorDescription`.

### 4.8 Widgets

#### OBU Card (Balance)
- **Files:** `widgets/obucard/ObuCardRenderer.kt`, `ObuCardWidget.kt`
- **typeId:** `sg-erp2:card`
- **Data:** `DataTypes.BALANCE`
- **Implements:** `WidgetRenderer`, `SettingsAwareSizer`
- **Settings:** `lowBalanceThresholdCents` (IntSetting, presets: $5/$10/$20/$50) + InfoCardSettings (layout mode, sizes)
- **Features:** `supportsTap = true` — launches SimplyGo app (`sg.com.transitlink`) or Play Store fallback.
- **Render:** `InfoCardLayout` with CreditCard icon. Color-coded: green (card connected), amber (low balance), red (card invalid), theme secondary (no card/disconnected). Dollar amount displayed with AnnotatedString (integer + smaller decimal part).
- **Layout modes:** STACK (8x8), GRID (10x6), COMPACT (10x9)

#### Device Connection
- **Files:** `widgets/device/DeviceRenderer.kt`, `DeviceWidget.kt`, `DeviceConnectionModel.kt`
- **typeId:** `sg-erp2:device`
- **Data:** `DataTypes.DEVICE`
- **Implements:** `WidgetRenderer`, `SettingsAwareSizer`
- **Settings:** InfoCardSettings only (layout mode, sizes, show icon/title)
- **Render:** `InfoCardLayout` with Bluetooth icons (connected/searching/default). Status text: Connected/Connecting/Searching/Not Connected. Title = device name or "Device".
- **Layout modes:** STACK (9x9), GRID (10x6), COMPACT (10x9)
- **`DeviceConnectionModel`:** Simple data class: `isConnected`, `deviceName`, `isSearching`, `isConnecting`

#### Traffic Alerts
- **Files:** `widgets/traffic/TrafficRenderer.kt`, `TrafficWidget.kt`
- **typeId:** `sg-erp2:traffic`
- **Data:** `DataTypes.TRAFFIC`
- **Implements:** `WidgetRenderer`, `SettingsAwareSizer`
- **Settings:** InfoCardSettings (layout mode, sizes)
- **Render:** `InfoCardLayout` with Warning icon. Body: "--" when disconnected, "Clear" when connected with no alerts, joined alert messages otherwise.
- **Layout modes:** Same as Device

#### Transaction History
- **Files:** `widgets/history/HistoryRenderer.kt`, `HistoryWidget.kt`
- **typeId:** `sg-erp2:history`
- **Data:** `DataTypes.HISTORY`
- **Implements:** `WidgetRenderer`, `SettingsAwareSizer`
- **Settings:** InfoCardSettings (layout mode, sizes)
- **Render:** `InfoCardLayout` with Receipt icon. Body: "--" when disconnected, total amount when available, "{count} today" otherwise.
- **Layout modes:** Same as Device

### 4.9 Tests

**Test files present:**
- `src/test/java/.../providers/ObuDataProvidersTest.kt`
- `src/test/java/.../sdk/ObuSdkBridgeTest.kt`
- `src/test/java/.../widgets/DeviceRendererTest.kt`
- `src/test/java/.../widgets/HistoryRendererTest.kt`
- `src/test/java/.../widgets/ObuCardRendererTest.kt`
- `src/test/java/.../widgets/TrafficRendererTest.kt`

### 4.10 Migration Decision

**Status: Deferred / Dropped for V1.**

The SG-ERP2 pack is a regional pack for Singapore's Electronic Road Pricing system. It depends on a proprietary SDK (`libs.extol.sdk`) and hardware (OBU devices). Key considerations:

1. **Proprietary SDK dependency** — `sg.gov.lta.obu.sdk.*` imports cannot be compiled without the SDK binary.
2. **Regional scope** — Only relevant to Singapore market.
3. **Complex setup** — 3-page setup flow with OneMotoring registration, Bluetooth/Location permissions, CDM device scanning.
4. **Companion Device Manager integration** — `CompanionDeviceHandler`, `DeviceServiceRegistry`, `PairedDeviceStore` — these APIs need to exist in `:sdk:contracts` first.

**What to extract to `:sdk:contracts`:**
- `CompanionDeviceHandler` interface
- `DeviceManagement` interface
- `ConnectionNotifier` interface
- `DeviceServiceRegistry` interface
- `ConnectedDevice`, `PairedDevice`, `DeviceStatus` data types
- `DeviceHandlerRegistration` data type
- `SetupDefinition.DeviceScan`, `SetupDefinition.Instruction`, `SetupDefinition.SystemService` sealed subtypes
- `InstructionAction`, `ClipboardVerification`, `VerificationResult` types
- `InfoCardLayoutMode`, `InfoCardSettings`, `SizeOption` (already in plugin-api?)

**What stays sg-erp2 pack-internal:**
- `ObuSdkBridge`, `ObuConnectionManager`, `ObuErrorMapper`, `ObuEventMapper`
- `ObuEvent` sealed hierarchy
- `ConnectionState`, `ObuDevice`, all ERP-specific enums
- `SgErp2PackPreferencesRepository`
- All 4 widget renderers + all 8 data providers

---

## 5. Cross-Pack Patterns

### 5.1 WidgetRenderer Interface Implementation

Every renderer follows this pattern:
```kotlin
@DashboardWidget(localId = "...", displayName = "...", icon = "...")
@RequiresData([DataTypes.X])              // optional
class XRenderer @Inject constructor() : WidgetRenderer {
    override val typeId = "{packId}:{widget-name}"
    override val displayName = "..."
    override val contentDescription = "..."
    override val supportsMultipleSources = false
    override val compatibleDataTypes = listOf(...)
    override val priority = 100
    override val settingsSchema: List<SettingDefinition<*>> = listOf(...)

    override val constraints = WidgetConstraints(...)

    override fun getDefaults(context: WidgetContext): WidgetDefaults { ... }

    @Composable
    override fun Render(
        widgetData: WidgetData,
        isEditMode: Boolean,
        style: WidgetStyle,
        settings: Map<String, Any?>,
        modifier: Modifier
    ) { ... }
}
```

### 5.2 DataProvider Interface Implementation

Every provider follows this pattern:
```kotlin
@DashboardDataProvider(localId = "...", displayName = "...", description = "...")
@DataContract(provides = [DataTypes.X])
@Singleton
class XDataProvider @Inject constructor(...) : DataProvider {
    override val sourceId = "{packId}:{name}"
    override val displayName = "..."
    override val description = "..."
    override val dataType = DataTypes.X
    override val schema = DataSchema(listOf(...))
    override val setupSchema: List<SetupPageDefinition> = ...

    override fun provideState(): Flow<DataSnapshot> = ...
}
```

### 5.3 Sensor Provider Pattern

Used by: AmbientLightDataProvider, OrientationProvider, SolarLocationDataProvider

```kotlin
private val sensorFlow = callbackFlow {
    // Register sensor listener
    val listener = object : SensorEventListener { ... trySend(...) }
    sensorManager.registerListener(listener, sensor, SENSOR_DELAY_NORMAL)
    awaitClose { sensorManager.unregisterListener(listener) }
}.stateIn(scope, SharingStarted.WhileSubscribed(5000), default)
```

### 5.4 Connection-Gated Provider Pattern

Used by: All 8 OBU data providers

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
override fun provideState(): Flow<DataSnapshot> = connectionManager.connectionState.flatMapLatest { state ->
    when (state) {
        is ConnectionState.Connected -> realDataFlow
        else -> flowOf(disconnectedSnapshot)
    }
}
```

### 5.5 Settings Parsing Pattern

All renderers parse settings with backward-compat fallbacks:
```kotlin
val speedUnit = (settings["speedUnit"] as? String)?.let { name ->
    SpeedUnit.entries.find { it.name == name }
} ?: run {
    val legacyImperial = settings["useImperial"] as? Boolean ?: false
    if (legacyImperial) SpeedUnit.MPH else SpeedUnit.KPH
}
```

### 5.6 InfoCardLayout Widgets

Used by: Device, Traffic, History, ObuCard, Shortcuts, AmbientLight

All share `InfoCardSettings.layoutSettings()` for layout mode (STACK/GRID/COMPACT), icon/title visibility, text sizes. All implement `SettingsAwareSizer` with layout-mode-dependent `computeSize()`.

### 5.7 Variant System

Widgets with `baseTypeId` share a category but have different visual implementations:
- `core:clock` → `core:clock-digital`, `core:clock-analog`
- `core:date` → `core:date-simple`, `core:date-stack`, `core:date-grid`
- `core:speedlimit` → `core:speedlimit:circle`, `core:speedlimit:rect`

### 5.8 Widget Composable Structure

Two patterns:
1. **Canvas-based:** Speedometer, Compass, Solar, Clock (Analog), SpeedLimit signs — custom `Canvas` drawing with `drawWithCache`, `graphicsLayer`, `remember` for draw objects.
2. **InfoCardLayout-based:** Device, Traffic, History, ObuCard, Shortcuts, AmbientLight — shared card framework with icon + top text + bottom text in configurable layouts.

---

## 6. Migration Mapping Summary

### Module Paths

| Old Path | New Path | Status |
|---|---|---|
| `feature/packs/free` | `pack/essentials` | Migrate |
| `feature/packs/demo` | `pack/demo` | Migrate |
| `feature/packs/themes` | `pack/themes` | Migrate |
| `feature/packs/sg-erp2` | `pack/sg-erp2` | Deferred V1 |

### Namespace Changes

| Old | New |
|---|---|
| `app.dqxn.android.feature.packs.free` | `app.dqxn.android.pack.essentials` |
| `app.dqxn.android.feature.packs.demo` | `app.dqxn.android.pack.demo` |
| `app.dqxn.android.feature.packs.themes` | `app.dqxn.android.pack.themes` |
| `app.dqxn.android.feature.packs.sgerp2` | `app.dqxn.android.pack.sgerp2` |

### TypeId Remapping (free → essentials)

| Old typeId | New typeId |
|---|---|
| `core:speedometer` | `essentials:speedometer` |
| `core:clock-digital` | `essentials:clock-digital` |
| `core:clock-analog` | `essentials:clock-analog` |
| `core:date-simple` | `essentials:date-simple` |
| `core:date-stack` | `essentials:date-stack` |
| `core:date-grid` | `essentials:date-grid` |
| `core:compass` | `essentials:compass` |
| `core:solar` | `essentials:solar` |
| `core:speedlimit:circle` | `essentials:speedlimit-circle` |
| `core:speedlimit:rect` | `essentials:speedlimit-rect` |
| `core:shortcuts` | `essentials:shortcuts` |
| `core:ambient_light` | `essentials:ambient-light` |

### SourceId Remapping (free → essentials)

| Old sourceId | New sourceId |
|---|---|
| `free:time` | `essentials:time` |
| `free:date` | `essentials:date` |
| `free:orientation` | `essentials:orientation` |
| `free:solar_location` | `essentials:solar-location` |
| `free:solar_timezone` | `essentials:solar-timezone` |
| `free:ambient_light` | `essentials:ambient-light` |
| `free:call` | `essentials:call` |

### Dependency Remapping

| Old Dependency | New Dependency |
|---|---|
| `:core:plugin-api` | `:sdk:contracts` (interfaces) + `:sdk:ui` (Compose) |
| `:core:widget-primitives` | `:sdk:ui` |
| `:core:common` | `:sdk:common` |
| `:data:persistence` | Extract needed APIs to `:sdk:contracts` |
| `:feature:packs:free` | Extract shared code to `:sdk:*` |

### Convention Plugin

Old: `alias(libs.plugins.dqxn.android.feature)` + manual deps
New: `id("dqxn.pack")` auto-wires `:sdk:*` deps

### DataTypes Used Across Packs

| DataType | Free Pack | Demo Pack | SG-ERP2 Pack |
|---|---|---|---|
| `TIME` | Provider + Widget | - | - |
| `DATE` | Provider + Widget | - | - |
| `ORIENTATION` | Provider + Widget | - | - |
| `SOLAR` | Provider + Widget | - | - |
| `AMBIENT_LIGHT` | Provider + Widget | - | - |
| `SPEED` | Widget only | Provider | Provider |
| `ACCELERATION` | Widget only | Provider | Provider |
| `SPEED_LIMIT` | Widget only | Provider | Provider |
| `BALANCE` | - | Provider | Provider + Widget |
| `CHARGING` | - | Provider | Provider |
| `HISTORY` | - | Provider | Provider + Widget |
| `TRAFFIC` | - | Provider | Provider + Widget |
| `DEVICE` | - | Provider | Provider + Widget |
