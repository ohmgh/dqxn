# Old Codebase: Data Layer & Documentation

**Analysis Date:** 2026-02-23
**Source:** `/Users/ohm/Workspace/dqxn.old/android/`

---

## 1. Proto Schemas

**None.** The old codebase has zero `.proto` files. All persistence uses Preferences DataStore with JSON serialization via `kotlinx.serialization`. This is a significant difference from the new architecture which uses Proto DataStore + Preferences DataStore.

---

## 2. DataStore Setup

### 2.1 DataStore Instances

The old codebase uses **four separate Preferences DataStore** instances, each created via top-level extension property delegates:

| DataStore Name | File | Scope | Key Strategy |
|---|---|---|---|
| `user_preferences` | `UserPreferencesRepository.kt` | App-wide user settings | Named preference keys per setting |
| `paired_devices` | `PairedDeviceStore.kt` | BLE device pairing metadata | definitionId as key, JSON-serialized list as value |
| `connection_events` | `ConnectionEventStore.kt` | BLE connection diagnostics log | Single `"events"` key, JSON-serialized list |
| `provider_settings` | `ProviderSettingsStore.kt` | Per-provider config settings | `{providerId}:{key}` prefixed keys |
| `dashboard_layouts` | `LayoutDataStore.kt` | Widget layout persistence | Single `"canvas"` key, JSON-serialized canvas |

### 2.2 DataStore Instance Declarations

Each DataStore is declared as a `Context` extension property at file scope:

```kotlin
// UserPreferencesRepository.kt
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

// PairedDeviceStore.kt
internal val Context.pairedDevicesDataStore: DataStore<Preferences> by preferencesDataStore(name = "paired_devices")

// ConnectionEventStore.kt
private val Context.connectionEventDataStore: DataStore<Preferences> by preferencesDataStore(name = "connection_events")

// ProviderSettingsStore.kt
private val Context.providerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "provider_settings")

// LayoutDataStore.kt (in feature:dashboard, not data:persistence)
private val Context.layoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "dashboard_layouts")
```

**No corruption handlers.** None of the DataStore instances use `ReplaceFileCorruptionHandler`. Corrupt data falls through to `try/catch` in deserialization code, returning empty defaults.

### 2.3 Serialization Approach

All stores use `kotlinx.serialization.json.Json` for encoding/decoding with `ignoreUnknownKeys = true`. Complex types are serialized to JSON strings and stored in `stringPreferencesKey` entries. No type-safe proto schemas.

### 2.4 DI Configuration

**File:** `data/persistence/src/main/java/app/dqxn/android/data/persistence/di/PersistenceModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    @Binds
    abstract fun bindConnectionEventStore(
        impl: ConnectionEventStoreImpl
    ): ConnectionEventStore
}
```

Only `ConnectionEventStore` uses interface binding. The other stores (`PairedDeviceStore`, `ProviderSettingsStore`, `UserPreferencesRepository`) are injected directly as concrete classes with `@Singleton @Inject constructor`.

**LayoutDataStore** is in `feature:dashboard`, not `data:persistence`, and implements `LayoutRepository` interface. Its DI binding is presumably in a dashboard module (not shown in persistence).

---

## 3. Repository/DAO Layer

### 3.1 Build Dependencies

**File:** `data/persistence/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.dqxn.android.library)
    alias(libs.plugins.dqxn.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
}
```

Room is declared as a dependency but **no Room database, DAOs, or entities exist in the source tree**. Room was intended for SQLCipher-encrypted transaction history (per the architecture doc) but was never implemented. All persistence is purely DataStore-based.

### 3.2 Data Access Patterns

**UserPreferencesRepository** (`data/persistence/src/main/java/.../UserPreferencesRepository.kt`)
- Exposes each preference as an individual `Flow<T>` property
- Separate suspend setter functions per preference
- No batched reads/writes
- Preferences stored as strings even for booleans (`"true"/"false"` in stringPreferencesKey)
- Enums stored as `.name` strings, decoded with `valueOf()` + try/catch fallback

Key preferences:
| Key | Type | Default | Purpose |
|---|---|---|---|
| `connected_obu_name` | String | null | Paired OBU device name |
| `connected_obu_mac` | String | null | Paired OBU MAC address |
| `onboarding_completed` | String ("true"/"false") | false | Onboarding gate |
| `theme_mode` | String (enum name) | LIGHT | ThemeMode enum |
| `dashboard_theme` | String | null | Active theme ID |
| `demo_mode_enabled` | String ("true"/"false") | BuildConfig.DEBUG | Demo mode toggle |
| `alert_mode` | String (enum name) | SILENT | AlertMode enum |
| `show_status_bar` | String ("true"/"false") | false | Status bar visibility |
| `light_theme` | String | "minimalist" | Light mode theme ID |
| `dark_theme` | String | "slate" | Dark mode theme ID |
| `auto_switch_mode` | String (enum name) | SYSTEM | AutoSwitchMode enum |
| `illuminance_threshold` | Int | 50 | Lux threshold for auto-switch |

**PairedDeviceStore** (`data/persistence/src/main/java/.../PairedDeviceStore.kt`)
- Stores `List<PairedDeviceMetadata>` per `definitionId` as JSON in string keys
- Supports multi-device per definition (added in Phase 18.1)
- Migration from single-object to list format (reads both transparently)
- MAC address operations are case-insensitive
- `@Singleton` with `@Inject constructor(@ApplicationContext context: Context)`
- CRUD: `markPaired`, `removePairedDevice`, `updateLastConnected`, `updateFirmwareVersion`, `clearPaired`, `clearAll`
- All operations are suspend functions using `dataStore.data.first()` for reads and `dataStore.edit` for writes

**PairedDeviceMetadata** (`data/persistence/src/main/java/.../PairedDeviceMetadata.kt`)
```kotlin
@Serializable
data class PairedDeviceMetadata(
    val macAddress: String,
    val deviceName: String?,
    val lastConnectedTime: Long,
    val firmwareVersion: String? = null,
    val associationId: Int? = null  // CDM API 36+
)
```

**ConnectionEventStore** (`data/persistence/src/main/java/.../ConnectionEventStore.kt`)
- Interface + Impl pattern (only store that uses this)
- Rolling window of MAX_EVENTS = 50
- Events stored as JSON-serialized list in single key `"events"`
- `ConnectionEvent`: timestamp, macAddress, deviceName, eventType (CONNECTED/DISCONNECTED/CONNECT_FAILED), handlerId, reason
- Operations: `logEvent`, `getRecentEvents`, `clearAll`, `clearForHandler`
- Corrupt data silently returns `emptyList()`

**ProviderSettingsStore** (`data/persistence/src/main/java/.../ProviderSettingsStore.kt`)
- Single shared DataStore for all providers, isolated by key prefix `{providerId}:{key}`
- Type-aware serialization with prefix encoding: `s:`, `i:`, `l:`, `f:`, `d:`, `b:`, `j:` (JSON for complex), `null`
- Supports `Flow<Map<String, Any?>>` observation via `observeAllSettings(providerId)`
- Operations: `getSetting`, `saveSetting`, `getAllSettings`, `observeAllSettings`, `clearSetting`, `clearAllForProvider`, `clearAll`
- Legacy values without prefix treated as plain strings

**LayoutDataStore** (`feature/dashboard/src/main/java/.../data/LayoutDataStore.kt`)
- Located in `feature:dashboard`, NOT in `data:persistence`
- Implements `LayoutRepository` interface
- Stores entire `DashboardCanvas` as single JSON blob under `"canvas"` key
- Thread-safe via `Mutex` for read-modify-write operations
- Canvas model: `DashboardCanvas(widgets: List<SavedWidget>, version: Int)`

**LayoutRepository interface:**
```kotlin
interface LayoutRepository {
    val canvas: Flow<DashboardCanvas>
    suspend fun saveCanvas(canvas: DashboardCanvas)
    suspend fun addWidgetToCanvas(widget: SavedWidget)
    suspend fun updateWidgetOnCanvas(widget: SavedWidget)
    suspend fun removeWidgetFromCanvas(widgetId: String)
    suspend fun clearCanvas()
}
```

**SavedWidget model:**
```kotlin
@Serializable
data class SavedWidget(
    val id: String,
    val type: String,                    // e.g. "core:speedometer"
    val gridX: Int, val gridY: Int,
    val widthUnits: Int, val heightUnits: Int,
    val backgroundStyle: String = "SOLID",
    val opacity: Float = 1.0f,
    val showBorder: Boolean = false,
    val hasGlowEffect: Boolean = false,
    val cornerRadiusPercent: Int = 25,
    val rimSizePercent: Int = 0,
    val variant: String? = null,
    @Serializable(with = AnyMapSerializer::class)
    val settings: Map<String, Any?> = emptyMap(),
    val selectedDataSourceIds: List<String> = emptyList(),
    val zIndex: Int = 0
)
```

### 3.3 AnyMapSerializer

**File:** `data/persistence/src/main/java/.../serialization/AnyMapSerializer.kt`

Custom `KSerializer<Map<String, Any?>>` that handles dynamic widget settings. Supports: null, Boolean, Int, Long, Double, Float, String, Enum (as name), nested List, nested Map. JSON-only (casts encoder/decoder to `JsonEncoder`/`JsonDecoder`).

### 3.4 LayoutMigrator

**File:** `feature/dashboard/src/main/java/.../data/LayoutMigrator.kt`

Sequential migration ladder for layout JSON schema versioning:
- **V0 -> V1**: Namespace widget type IDs (`"Speedometer"` -> `"core:speedometer"`, `"Balance"` -> `"obu:card"`, etc.)
- **V1 -> V2**: Consolidate typed settings fields (`speedometerSettings`, `clockSettings`, etc.) into generic `settings` map
- Current version: 2
- Failed migrations return original JSON with error flag (never delete user data)
- `@Singleton` with `@Inject constructor()`

---

## 4. Preferences Summary

### Enums Defined in Persistence Module

**AutoSwitchMode** (`data/persistence/src/main/java/.../AutoSwitchMode.kt`):
```kotlin
enum class AutoSwitchMode {
    LIGHT, DARK, SYSTEM, SOLAR_AUTO, ILLUMINANCE_AUTO
}
```
- `isPremium` property: SOLAR_AUTO and ILLUMINANCE_AUTO are premium (Themes Pack)
- `displayName` and `description` properties for UI

**ThemeMode** (defined inline in `UserPreferencesRepository.kt`):
```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }
```

**AlertMode** (defined inline in `UserPreferencesRepository.kt`):
```kotlin
enum class AlertMode { SILENT, VIBRATE, SOUND }
```

**ConnectedObuDevice** (defined inline in `UserPreferencesRepository.kt`):
```kotlin
data class ConnectedObuDevice(val name: String, val macAddress: String)
```

---

## 5. Migration Notes: Old -> New Architecture

### What maps to `:data` module in new arch

| Old Location | Old Approach | New Approach | Notes |
|---|---|---|---|
| `data:persistence` / UserPreferencesRepository | Preferences DataStore, strings for booleans | Preferences DataStore, proper typed keys | Stop storing booleans as strings. Use `booleanPreferencesKey`. |
| `data:persistence` / ProviderSettingsStore | Preferences DataStore, `{providerId}:{key}` prefix | Preferences DataStore, `{packId}:{providerId}:{key}` prefix | New arch adds packId as additional namespace level. |
| `data:persistence` / PairedDeviceStore | Preferences DataStore, JSON-serialized lists | Proto DataStore or keep Preferences | OBU-specific, may move to `:pack:sg-erp2` or be generalized for any BLE device pack. |
| `data:persistence` / ConnectionEventStore | Preferences DataStore, JSON-serialized rolling log | Proto DataStore | Better suited to Proto for structured event data. |
| `feature:dashboard` / LayoutDataStore | Preferences DataStore, single JSON blob | Proto DataStore, per-profile canvases | New arch has per-profile `DashboardCanvas`. Layout saves debounced 500ms on Dispatchers.IO. |
| `feature:dashboard` / LayoutMigrator | JSON-level migration ladder | Proto schema evolution | Proto handles backward compat natively; migration may be simpler. |
| `data:persistence` / AnyMapSerializer | Custom kotlinx.serialization KSerializer | Keep or replace with proto map | Still needed if widget settings remain JSON-backed. |

### Critical new requirements not present in old code

1. **`ReplaceFileCorruptionHandler` required on ALL DataStore instances** -- Old code has none. Every new DataStore must include one.

2. **All DataStore providers must be `@Singleton`** -- Old code already does this correctly.

3. **Provider settings key format changes**: Old = `{providerId}:{key}`, New = `{packId}:{providerId}:{key}`.

4. **Layout saves debounced 500ms on `Dispatchers.IO`** -- Old LayoutDataStore saves immediately with no debouncing. The `Mutex` for thread safety is a good pattern to keep.

5. **Per-profile canvases** -- Old code has a single `DashboardCanvas`. New arch has per-profile independent `DashboardCanvas` instances. This is a fundamental data model change.

6. **Proto DataStore** -- Old code uses zero Proto DataStore. New arch requires Proto DataStore for structured data (layout, device metadata, connection events). This means defining `.proto` schemas from scratch.

7. **No Room** -- Old code declares Room as a dependency but has no DAOs/entities/database. New arch also drops Room (not mentioned in new CLAUDE.md). Clean break.

### What can be reused

- `ProviderSettingsStore` pattern: The prefix-based key isolation and type-aware serialization (`s:`, `i:`, `b:`, etc.) is sound. Adapt key format to `{packId}:{providerId}:{key}`.
- `AnyMapSerializer`: Still needed for dynamic widget settings if using JSON-backed settings in Proto DataStore `bytes` fields.
- `LayoutRepository` interface: Clean contract. Extend for per-profile support.
- `Mutex`-based thread safety for read-modify-write: Good pattern, keep it.
- `ConnectionEvent` model: Adapt to Proto message.
- `PairedDeviceMetadata` model: Adapt to Proto message if BLE pairing is generalized.

### What to drop

- `UserPreferencesRepository` as monolithic prefs class: Break into focused stores per concern (theme prefs, onboarding prefs, etc.) or use Proto DataStore with structured messages.
- Boolean-as-string storage: Use proper typed preference keys.
- `BuildConfig.DEMO_MODE_DEFAULT_ENABLED`: Old pattern for debug/release defaults. New arch likely handles this differently.
- OBU-specific prefs in core data module (`connected_obu_name`, `connected_obu_mac`): These belong in `:pack:sg-erp2` or a pack-specific DataStore.
- `LayoutMigrator` JSON migration ladder: Will be replaced by Proto schema evolution. The concept of versioned migrations is sound but the JSON manipulation is not needed.

---

## 6. Old Architecture Documentation

### 6.1 DQXN Android Architecture Specification (V2.0)

**File:** `/Users/ohm/Workspace/dqxn.old/docs/DQXN Android Architecture Specification.md`

Clean Architecture with Modularization by Feature. Three layers: Presentation (Compose, ViewModels), Domain (UseCases, Repository interfaces, pure Kotlin), Data (Repositories impl, SDK wrappers). Key patterns:
- `AppResult<T>` sealed interface (Ok/Err) instead of exceptions
- Repository pattern with UseCase layer for complex logic
- MVI-Hybrid: single `StateFlow<UiState>`, typed `UiEvent`, `Channel<UiEffect>`
- Room + SQLCipher planned for encrypted local storage (never implemented)
- `EncryptedSharedPreferences` for secrets (never implemented -- DataStore used instead)
- ForegroundService for BLE connection maintenance

### 6.2 DQXN Product Requirements (V3.0)

**File:** `/Users/ohm/Workspace/dqxn.old/docs/DQXN Product Requirements.md`

Originally Singapore ERP 2.0 OBU companion app. Features: card balance display, ERP charge notifications, traffic alerts, parking availability, trip summary, Android Auto/CarPlay. Diagnostics dashboard via Settings > About > 5 taps. Dual-theme model (light/dark) with auto-switch modes.

### 6.3 DQXN Product Strategy

**File:** `/Users/ohm/Workspace/dqxn.old/docs/DQXN Product Strategy.md`

Broader platform vision. Brand: "Daxen", mascot: Dashing Dachshund. 24 theme system (2 free, 22 paid). Pack ecosystem: Essentials (free), Themes ($9.99), Plus ($19.99), regional packs (SG ERP $4.99). Phase 1 = launch with core widgets + weather + system sensors. Phase 2 = CarPlay/AA. Phase 3 = Home launcher, marketplace. "Buy once, own forever" monetization.

### 6.4 Plugin Architecture

**File:** `/Users/ohm/Workspace/dqxn.old/docs/Plugin Architecture.md`

Extensive document covering the transition from hardcoded `sealed class WidgetType` / `enum WidgetDataSource` / `object DashboardThemes` to a registry-based plugin system. Proposes `WidgetRenderer`, `ThemeProvider`, `DataSourceProvider` interfaces with Hilt multibinding. Covers DFM delivery, entitlement-aware registry, context-aware dashboard switching, gesture-based switching, deep linking. Much of this informed the new architecture's pack system.

### 6.5 Module Boundaries

**File:** `/Users/ohm/Workspace/dqxn.old/docs/module_boundaries.md`

Documents the elimination of `core:dashboard-api` module. Types redistributed: `DashboardThemeDefinition`, `DefaultTheme`, `ThemeProvider`, `DashboardWidgetInstance`, `WidgetSizeSpec` -> `core:widget-primitives`. `ThemeRepository` -> `feature:dashboard`. `AnyMapSerializer` -> `data:persistence`.

### 6.6 Gesture Architecture

**File:** `/Users/ohm/Workspace/dqxn.old/docs/gesture-architecture.md`

Decentralized gesture handling: per-widget `WidgetGestureHandler` (tap, long press, drag, resize) + `BlankSpaceGestureHandler` for empty areas. Events collected via `SharedFlow` in `DashboardGrid`. Resize uses dominant axis logic with proportional boundary scaling.

### 6.7 Layout Migration Strategy

**File:** `/Users/ohm/Workspace/dqxn.old/docs/layout_migration_strategy.md`

Sequential Migration Ladder pattern for layout JSON schema evolution. V0 (unversioned) -> V1 (namespaced types) -> V2 (consolidated settings). Immutable history principle. Fail-safe: corrupt data returns original with error flag.

### 6.8 OBU SDK Event Analysis

**File:** `/Users/ohm/Workspace/dqxn.old/docs/obu_sdk_event_analysis.md`

Detailed mapping of EXTOL SDK v2.0.1 events to data providers. Connection events, road pricing (ERP/EPS/EEP), card balance, payment history, trip summary, traffic information. Traffic info taxonomy by template ID (1, 2, 8, 110, 111). Fire-and-forget event model with no explicit clear signals.

### 6.9 OBU Provider Evaluation

**File:** `/Users/ohm/Workspace/dqxn.old/docs/evaluation_report.md`

Gap analysis: Connection, Road Pricing, Traffic implemented. Card Balance, Payment History, Trip Summary, Trip Costs are unimplemented stubs.

### 6.10 RequiresSetup Architecture Evaluation

**File:** `/Users/ohm/Workspace/dqxn.old/docs/requires_setup_architecture_evaluation.md`

Evaluation of the `RequiresSetup` protocol for permission/setup requirements on data providers. Parallel Provider Model where providers own requirements, renderers are pure, dashboard orchestrates overlays. Production-ready, rated 4.5/5.

---

## 7. Old CLAUDE.md

**File:** `/Users/ohm/Workspace/dqxn.old/CLAUDE.md`

Key points:
- Module structure: `:app`, `:feature:dashboard`, `:feature:driving`, `:feature:packs:{free,themes,sg-erp2,demo}`, `:core:{plugin-api,common,widget-primitives,plugin-processor,agentic,agentic-processor}`, `:data:persistence`
- Convention plugins in `build-logic/convention/`: `dqxn.android.{application,library,feature,hilt}`
- Testing: JUnit + Turbine + Robolectric, screenshot tests via Roborazzi
- Tech: Kotlin, Jetpack Compose (M3), Hilt, Room + DataStore + SQLCipher, Coroutines, Min SDK 31 / Target 36
- External: LTA EXTOL SDK 2.1.0 for BLE OBU comms
- GSD methodology references (`.gsd/STATE.md`, `.gsd/ROADMAP.md`, etc.)
- Old package namespace: `app.dqxn.android` (same as new)
- Old brand name referenced as "Oversteer" in some file paths within docs (pre-rename)

### Old .claude/settings.json

Permissive defaults (`bypassPermissions`). Allows git operations, basic shell commands. Denies destructive git operations. Standard safety.

### Old .claude/settings.local.json

More permissive. Allows Gradle builds, ADB operations, GSD skills, web fetches. Also allows Python scripts (used for solar calculation verification during development).

---

## 8. Key Differences: Old vs New Architecture

| Aspect | Old | New | Impact |
|---|---|---|---|
| **Data storage** | Preferences DataStore only (JSON strings) | Proto DataStore + Preferences DataStore | Need to define `.proto` schemas from scratch |
| **Corruption handling** | Silent fallback to empty defaults | `ReplaceFileCorruptionHandler` required | Must add handlers to all DataStore instances |
| **Layout model** | Single `DashboardCanvas` | Per-profile `DashboardCanvas` | Fundamental data model change |
| **Layout persistence** | In `feature:dashboard` | In `:data` module | Needs relocation |
| **Layout saving** | Immediate on every change | Debounced 500ms on Dispatchers.IO | Performance improvement needed |
| **Provider settings keys** | `{providerId}:{key}` | `{packId}:{providerId}:{key}` | Additional namespace level |
| **Pack module structure** | `:feature:packs:{name}` | `:pack:{name}` | Different module path |
| **Convention plugins** | `dqxn.android.{library,feature,hilt}` | `dqxn.pack`, `dqxn.snapshot`, `dqxn.android.compose`, `dqxn.kotlin.jvm` | Entirely new plugin system |
| **Build toolchain** | Standard AGP + Kotlin plugin | AGP 9.0.1 (manages Kotlin directly), Gradle 9.3.1, JDK 25 | No `org.jetbrains.kotlin.android` plugin |
| **Driving mode** | `:feature:driving` module exists | Deferred post-launch (no driving module) | Drop all driving-specific code |
| **Room** | Declared but unused | Not present | Clean removal |
| **SQLCipher** | Declared in architecture doc, never implemented | Not mentioned | Drop entirely |
| **OBU-specific prefs** | In core `UserPreferencesRepository` | Should be in `:pack:sg-erp2` scope | Decouple from core data layer |
| **SDK contracts** | `core:plugin-api` | `sdk:contracts` (+ `sdk:common`, `sdk:ui`, etc.) | New SDK module hierarchy |
| **Snapshots** | Not present | `:pack:{packId}:snapshots` sub-modules with `@DashboardSnapshot` | New cross-boundary data pattern |
| **Widget type IDs** | `core:speedometer`, `obu:card` | `{packId}:{widget-name}` (e.g., `essentials:speedometer`) | Pack renamed from "core"/"free" to "essentials" |
| **Package paths** | `app.dqxn.android.data.persistence` | `app.dqxn.android.data` (for `:data` module) | Simpler package structure likely |
| **Testing framework** | JUnit4 + Robolectric + Truth + Turbine | JUnit5 + MockK + Truth + Turbine + `StandardTestDispatcher` | JUnit5 migration, add MockK |
