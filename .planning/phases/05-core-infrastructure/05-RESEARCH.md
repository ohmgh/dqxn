# Phase 5: Core Infrastructure - Research

**Researched:** 2026-02-24
**Domain:** Proto DataStore persistence, theme engine, thermal management, Firebase isolation, design system porting
**Confidence:** HIGH

## Summary

Phase 5 builds the shell internals that features depend on but packs never touch: `:core:design`, `:core:thermal`, `:data`, and `:core:firebase`. This is a breadth phase -- four modules, each with distinct concerns but all consumed by Phase 7's dashboard coordinators.

The highest-risk areas are (1) Proto DataStore schema design and migration infrastructure in `:data` -- this is entirely greenfield since the old codebase used Preferences DataStore with JSON blobs, and (2) `ThemeAutoSwitchEngine` in `:core:design` which must use late-binding `StateFlow` inputs rather than the old codebase's pack isolation violation (direct dependency on free pack providers). The thermal management system (`ThermalManager`, `FramePacer`, `RenderConfig`) is also fully greenfield -- the old codebase has no thermal management at all.

The `:core:design` module ports well-documented values from the old codebase (spacing, typography, card radii, emphasis levels, animation specs) and adds the `ThemeAutoSwitchEngine`. The `:core:firebase` module wraps Firebase SDKs behind the `CrashReporter` and `AnalyticsTracker` interfaces already defined in Phase 3. The `:data` module is the largest component with 6 repository implementations and the Proto DataStore schema infrastructure.

**Primary recommendation:** Build `:data:proto` schemas and `:core:thermal` first (no Compose dependency, pure logic), then `:core:design` (has Compose), then `:core:firebase` (thinnest module). The `:data` repositories depend on proto-generated classes from `:data:proto`, so the proto schemas must compile before repository implementations.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F4.1 | `DashboardThemeDefinition` -- colors + gradient brushes | Already exists in `:sdk:ui` (Phase 3). Phase 5 adds JSON parsing in `:core:design` via `ThemeSchema`/`ThemeColors`/`ThemeGradients` types that bridge JSON files to `DashboardThemeDefinition` |
| F4.2 | JSON-driven theme loading from bundled assets | `:core:design` -- `ThemeJsonParser` reads theme JSON files, deserializes via kotlinx.serialization, converts to `DashboardThemeDefinition`. JSON format documented in plugin-system.md |
| F4.3 | Dual-theme model (separate light/dark selections) | `UserPreferencesRepository` stores `lightThemeId` and `darkThemeId` separately. `ThemeAutoSwitchEngine` combines `isDarkActive` with both selections |
| F4.4 | 5 auto-switch modes: LIGHT, DARK, SYSTEM, SOLAR_AUTO, ILLUMINANCE_AUTO | `ThemeAutoSwitchEngine` in `:core:design`. `AutoSwitchMode` enum already exists in `:sdk:contracts`. Old source studied -- rewrite without pack isolation violation |
| F4.5 | `ThemeAutoSwitchEngine` with eager sharing (ready at cold start) | `SharingStarted.Eagerly` on `isDarkActive` and `activeTheme` StateFlows. `@Singleton` with constructor injection into `Application.onCreate()` |
| F7.1 | Proto DataStore for structured data, Preferences DataStore for simple settings | `:data:proto` generates proto classes (JVM module). `:data` consumes them with DataStore serializers. Preferences DataStore for `user_preferences` and `provider_settings` |
| F7.2 | Versioned layout schema with chained migration | `LayoutMigration` applies N->N+1 transformers sequentially on proto bytes. Pre-migration backup, 5-version gap reset, failure fallback to default preset |
| F7.3 | Debounced layout save (500ms) with atomic writes | `LayoutRepository` uses `MutableSharedFlow` + `debounce(500ms)` + DataStore atomic write. Old code used `Mutex` -- new arch uses DataStore's built-in atomicity |
| F7.4 | Type-safe provider settings store with pack-namespaced keys | `ProviderSettingsStore` -- Preferences DataStore with `{packId}:{providerId}:{key}` format. Type-prefixed serialization from old codebase (s:, i:, b:, f:, d:, l:, j:) |
| F7.5 | Paired device persistence | `PairedDeviceStore` -- Proto DataStore. `PairedDeviceMetadata` with MAC, associationId, lastConnected |
| F7.7 | Preset system -- JSON presets, region-aware defaults | `PresetLoader` in `:data`. Timezone-based region heuristic (no `ContextProvider` dependency). Old code's `PresetManifest`/`PresetWidget` models adapted for `essentials:*` typeIds |
| F7.8 | Layout corruption detection with fallback | `ReplaceFileCorruptionHandler` on ALL DataStore instances. Corrupted proto -> default instance + error report |
| F7.12 | Hardcoded minimal fallback layout | `LayoutRepository.FALLBACK_LAYOUT` -- code-level constant with clock widget centered. No JSON parsing, no asset file dependency |
| NF12 | Thermal headroom monitoring via `PowerManager.getThermalHeadroom()` | `ThermalManager` in `:core:thermal`. Uses `getThermalHeadroom(10)` for reactive decisions, `addThermalStatusListener` as fallback when headroom returns -1 |
| NF13 | Thermal degradation tiers: Normal -> Warm -> Degraded -> Critical | `ThermalLevel` enum + `RenderConfig` data class. Mapped from `THERMAL_STATUS_*` constants. FPS: 60 -> 45 -> 30 -> 24. Glow: full -> full -> RadialGradient -> disabled |
| NF43 | DataStore corruption handler required on ALL instances | 6 DataStore instances, each with `ReplaceFileCorruptionHandler`. Test verifies each instance has a handler |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Proto DataStore | 1.1.4 (via `datastore` BOM) | Type-safe binary persistence for layouts, devices, themes | Already in version catalog. Binary format faster than JSON, schema evolution via protobuf field addition |
| Preferences DataStore | 1.1.4 | Simple key-value settings (user prefs, provider settings) | Already in version catalog. Perfect for flat settings without schema |
| protobuf-kotlin-lite | 4.30.2 | Proto schema code generation | Already in version catalog. Lite variant for Android APK size |
| kotlinx.serialization-json | 1.10.0 | Theme JSON parsing, preset loading, provider settings serialization | Already in version catalog. Project standard for all JSON |
| Hilt | 2.59.2 | DI for all `@Singleton` stores and repositories | Already in version catalog. `@Singleton` scope for DataStore instances |
| Firebase BOM | 33.9.0 | Crashlytics, Analytics, Performance wrapping | Already in version catalog. BOM manages version alignment |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx-collections-immutable | 0.3.8 | `ImmutableList`/`ImmutableMap` for Compose-consumed state | All public state types emitted to UI |
| MockK | 1.14.9 | Mocking Firebase SDKs, PowerManager, Window for tests | `:core:firebase` and `:core:thermal` tests |
| Turbine | 1.2.0 | Testing StateFlow/Flow emissions from repositories | All DataStore repository tests |
| Truth | 1.4.4 | Test assertions | All test files |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Proto DataStore | Preferences DataStore (old approach) | Preferences with JSON blobs lacks type safety, schema evolution, and binary efficiency. Proto is strictly better for structured data |
| `LayoutMigration` on proto bytes | protobuf unknown field preservation | Protobuf preserves unknown fields for forward compat, but semantic migrations (renaming types, restructuring data) need explicit transformers |
| `ReplaceFileCorruptionHandler` | Custom deserialization try/catch | Handler is the DataStore API contract -- not using it means unhandled corruption crashes |

## Architecture Patterns

### Recommended Module Structure

```
core/design/src/main/kotlin/app/dqxn/android/core/design/
  ├── theme/
  │   ├── ThemeAutoSwitchEngine.kt    # 5-mode engine with late-binding inputs
  │   ├── ThemeJsonParser.kt          # JSON -> DashboardThemeDefinition
  │   ├── ThemeSchema.kt              # JSON schema types (ThemeColors, ThemeGradients)
  │   └── BuiltInThemes.kt            # Hardcoded free theme references
  ├── motion/
  │   └── DashboardMotion.kt          # Spring configs + named transitions (ported)
  ├── token/
  │   ├── Spacing.kt                  # SpaceXXS-SpaceXXL + 10 semantic aliases
  │   ├── DashboardTypography.kt      # 8 named styles
  │   ├── TextEmphasis.kt             # 4 alpha constants
  │   └── CardSize.kt                 # SMALL/MEDIUM/LARGE corner radii
  └── di/
      └── DesignModule.kt             # Hilt bindings

core/thermal/src/main/kotlin/app/dqxn/android/core/thermal/
  ├── ThermalLevel.kt                 # NORMAL, WARM, DEGRADED, CRITICAL
  ├── ThermalManager.kt               # PowerManager.getThermalHeadroom() monitoring
  ├── RenderConfig.kt                 # targetFps, glowEnabled derived from ThermalLevel
  ├── FramePacer.kt                   # Window.setFrameRate() API 34+ / throttling API 31-33
  ├── FakeThermalManager.kt           # MutableStateFlow<ThermalLevel> for chaos/testing
  └── di/
      └── ThermalModule.kt            # @Binds ThermalManager

core/firebase/src/main/kotlin/app/dqxn/android/core/firebase/
  ├── FirebaseCrashReporter.kt        # CrashReporter -> Crashlytics
  ├── FirebaseAnalyticsTracker.kt     # AnalyticsTracker -> Firebase Analytics
  ├── FirebasePerformanceTracer.kt    # Performance trace wrapping
  └── di/
      └── FirebaseModule.kt           # @Binds into :sdk:observability + :sdk:analytics interfaces

data/proto/src/main/proto/
  ├── dashboard_layout.proto          # DashboardStore, ProfileCanvas, SavedWidget
  ├── paired_devices.proto            # PairedDeviceStore, PairedDeviceMetadata
  └── custom_themes.proto             # CustomThemeStore, CustomTheme

data/src/main/kotlin/app/dqxn/android/data/
  ├── layout/
  │   ├── LayoutRepository.kt         # Interface
  │   ├── LayoutRepositoryImpl.kt     # Proto DataStore, debounced save, profile CRUD
  │   ├── LayoutMigration.kt          # Chained N->N+1 migration
  │   └── DashboardWidgetInstance.kt  # Runtime domain model (deferred from Phase 2)
  ├── preset/
  │   ├── PresetLoader.kt             # Region-aware preset loading
  │   ├── PresetModels.kt             # PresetManifest, PresetWidget
  │   └── FallbackLayout.kt           # Hardcoded clock widget fallback
  ├── preferences/
  │   ├── UserPreferencesRepository.kt  # Preferences DataStore
  │   └── PreferenceKeys.kt             # Typed key constants
  ├── provider/
  │   ├── ProviderSettingsStore.kt    # Pack-namespaced provider settings
  │   └── SettingsSerialization.kt    # Type-prefixed encode/decode
  ├── device/
  │   ├── PairedDeviceStore.kt        # Proto DataStore for BLE devices
  │   └── ConnectionEventStore.kt     # Rolling 50-event log
  ├── style/
  │   └── WidgetStyleStore.kt         # Per-widget style persistence
  ├── serializer/
  │   ├── DashboardStoreSerializer.kt   # Proto DataStore serializer
  │   ├── PairedDeviceSerializer.kt     # Proto DataStore serializer
  │   └── CustomThemeSerializer.kt      # Proto DataStore serializer
  └── di/
      └── DataModule.kt               # @Provides DataStore instances, @Binds repos
```

### Pattern 1: Proto DataStore with Corruption Handler

**What:** Every Proto DataStore instance MUST have a `ReplaceFileCorruptionHandler`.
**When to use:** All Proto DataStore instances (3 in Phase 5).

```kotlin
// Source: CLAUDE.md + arch/persistence.md
@Provides
@Singleton
fun provideDashboardDataStore(
    @ApplicationContext context: Context,
    logger: DqxnLogger,
    errorReporter: ErrorReporter,
): DataStore<DashboardStore> = DataStoreFactory.create(
    serializer = DashboardStoreSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        logger.error(LogTag.DATASTORE, "corruption" to true, "file" to "dashboard_store") {
            "Dashboard DataStore corrupted, resetting"
        }
        errorReporter.reportNonFatal(exception, ErrorContext.DataStoreCorruption("dashboard_store"))
        DashboardStore.getDefaultInstance()
    },
    produceFile = { context.dataStoreFile("dashboard_store.pb") }
)
```

### Pattern 2: ThemeAutoSwitchEngine with Late-Binding Inputs

**What:** The engine accepts `StateFlow<Float>` for illuminance and solar data rather than directly depending on pack providers.
**When to use:** `ThemeAutoSwitchEngine` in `:core:design`.

```kotlin
// Source: replication-advisory.md section 3 + CLAUDE.md constraints
@Singleton
class ThemeAutoSwitchEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    // Late-binding inputs -- providers register these in Phase 8
    // Default: flows that never emit (SOLAR_AUTO/ILLUMINANCE_AUTO fall back to SYSTEM)
    private val _illuminanceFlow = MutableStateFlow<Float?>(null)
    private val _solarIsDaytimeFlow = MutableStateFlow<Boolean?>(null)

    fun bindIlluminance(flow: StateFlow<Float>) { /* merge into _illuminanceFlow */ }
    fun bindSolarDaytime(flow: StateFlow<Boolean>) { /* merge into _solarIsDaytimeFlow */ }

    val isDarkActive: StateFlow<Boolean> = combine(
        userPreferencesRepository.autoSwitchMode,
        systemDarkMode(),
        _solarIsDaytimeFlow,
        _illuminanceFlow,
        userPreferencesRepository.illuminanceThreshold,
    ) { mode, systemDark, solarDaytime, lux, threshold ->
        when (mode) {
            AutoSwitchMode.LIGHT -> false
            AutoSwitchMode.DARK -> true
            AutoSwitchMode.SYSTEM -> systemDark
            AutoSwitchMode.SOLAR_AUTO -> solarDaytime?.not() ?: systemDark // fallback
            AutoSwitchMode.ILLUMINANCE_AUTO -> lux?.let { it < threshold } ?: systemDark // fallback
        }
    }.distinctUntilChanged()
     .stateIn(scope, SharingStarted.Eagerly, isSystemInDarkMode())
}
```

**Critical:** Old codebase had `private val solarProvider = dataProviders.find { it.dataType == DataTypes.SOLAR }` -- a direct pack dependency violation. New arch uses late-binding `StateFlow` inputs that packs register at runtime.

### Pattern 3: Debounced Layout Save

**What:** Layout mutations are buffered and written to DataStore with a 500ms debounce.
**When to use:** `LayoutRepositoryImpl` for all widget position/size/add/remove operations.

```kotlin
// Source: arch/persistence.md + REQUIREMENTS.md F7.3
private val saveChannel = Channel<Unit>(Channel.CONFLATED)

init {
    scope.launch {
        saveChannel.receiveAsFlow()
            .debounce(500L)
            .collect { persistCurrentState() }
    }
}

private suspend fun persistCurrentState() {
    dataStore.updateData { current ->
        // Atomic proto update
        currentState.toProto()
    }
}

fun requestSave() {
    saveChannel.trySend(Unit)
}
```

### Pattern 4: Type-Prefixed Provider Settings Serialization

**What:** Provider settings use type-prefixed string encoding for type-safe round-tripping.
**When to use:** `ProviderSettingsStore` for all provider configuration persistence.

Key format: `{packId}:{providerId}:{key}` (Phase 5 adds `packId` prefix to old `{providerId}:{key}`)

```kotlin
// Source: old codebase ProviderSettingsStore.kt (studied directly)
private fun serializeValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> "s:$value"
    is Int -> "i:$value"
    is Long -> "l:$value"
    is Float -> "f:$value"
    is Double -> "d:$value"
    is Boolean -> "b:$value"
    else -> "j:${json.encodeToString(value)}"
}

private fun deserializeValue(serialized: String?): Any? {
    if (serialized == null || serialized == "null") return null
    val prefix = serialized.substringBefore(":", "")
    val data = serialized.substringAfter(":", serialized)
    return when (prefix) {
        "s" -> data
        "i" -> data.toIntOrNull()
        "l" -> data.toLongOrNull()
        "f" -> data.toFloatOrNull()
        "d" -> data.toDoubleOrNull()
        "b" -> data.toBooleanStrictOrNull()
        "j" -> try { json.decodeFromString<Any>(data) } catch (_: Exception) { null }
        else -> serialized // Legacy fallback: treat as raw string
    }
}
```

### Pattern 5: Chained Schema Migration

**What:** Layout migrations apply N->N+1 transformers sequentially, with backup and failure fallback.
**When to use:** `LayoutMigration` for proto schema version upgrades.

```kotlin
// Source: REQUIREMENTS.md F7.2 + old codebase LayoutMigrator.kt (studied)
class LayoutMigration {
    companion object {
        const val CURRENT_VERSION = 1
        const val MAX_VERSION_GAP = 5
    }

    fun migrate(store: DashboardStore): MigrationResult {
        val fromVersion = store.schemaVersion
        if (fromVersion >= CURRENT_VERSION) return MigrationResult.NoOp(store)
        if (CURRENT_VERSION - fromVersion > MAX_VERSION_GAP) return MigrationResult.Reset

        var current = store
        for (version in fromVersion until CURRENT_VERSION) {
            current = applyMigration(current, version) ?: return MigrationResult.Failed(version)
        }
        return MigrationResult.Success(current)
    }
}
```

### Anti-Patterns to Avoid

- **DataStore without corruption handler:** EVERY DataStore instance must have `ReplaceFileCorruptionHandler`. No exceptions. Missing it means a corrupt file crashes the app.
- **Multiple DataStore instances for same file:** DataStore enforces single-instance-per-file. Creating two instances for the same `.pb` file throws `IllegalStateException`. Use `@Provides @Singleton` always.
- **Direct pack dependency from `:core:design`:** The old `ThemeAutoSwitchEngine` directly imported free pack data providers. This violates module boundaries. Use late-binding `StateFlow` inputs instead.
- **`UnconfinedTestDispatcher` for flow tests:** Per CLAUDE.md, ALWAYS use `StandardTestDispatcher`. Unconfined causes non-deterministic emission ordering.
- **Non-debounced layout saves:** Rapid mutations (drag, resize) without debounce cause write storms. 500ms debounce is a hard requirement (F7.3).
- **Mutable state in DataStore proto messages:** Proto objects are value types. Always use `DataStore.updateData { }` for mutations, never hold mutable references to proto instances.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Binary persistence | Custom file serialization | Proto DataStore | Schema evolution, atomicity, corruption handling built in |
| DataStore file corruption | Custom try/catch wrapping | `ReplaceFileCorruptionHandler` | API-level contract, handles corruption before deserialization |
| Settings key-value store | SQLite or custom file | Preferences DataStore | Perfect for flat settings, built-in Kotlin Flow API |
| Frame rate management | Choreographer callback skipping | `Window.setFrameRate()` API 34+ | Compose fights manual frame skipping; setFrameRate is the platform API |
| Theme auto-switching | Custom timer/callback system | `combine()` + `StateFlow` | Reactive by nature -- all inputs are flows, output should be too |
| Proto code generation | Hand-written serializers | `protobuf-gradle-plugin` + lite | Generated code is faster, type-safe, handles unknown fields |

**Key insight:** Proto DataStore and Preferences DataStore are the only persistence mechanisms. No Room, no SQLite, no SharedPreferences (except `CrashEvidenceWriter` which is sync for crash safety). The architecture decision is deliberate -- DataStore's Flow-first API matches the reactive coordinator model.

## Common Pitfalls

### Pitfall 1: Proto DataStore in Android Module Under AGP 9

**What goes wrong:** `protobuf-gradle-plugin` 0.9.6 casts to `BaseExtension` (removed in AGP 9), crashing on apply in Android modules.
**Why it happens:** Plugin hasn't been updated for AGP 9's new extension API.
**How to avoid:** Already resolved in Phase 1. Proto generation stays in `:data:proto` (JVM module via `dqxn.kotlin.jvm`). `:data` (Android module) consumes via `api(project(":data:proto"))`. Do NOT move proto generation to `:data`.
**Warning signs:** Build fails with `ClassCastException: cannot cast to BaseExtension`.

### Pitfall 2: DataStore Instance Duplication

**What goes wrong:** Creating two `DataStore<T>` instances for the same file path throws `IllegalStateException` at runtime.
**Why it happens:** Injecting `Context.dataStore` property delegate from multiple injection sites, or providing two `@Singleton` instances that resolve to the same file.
**How to avoid:** Single `@Provides @Singleton` per DataStore file. Use `DataStoreFactory.create()` with explicit file paths, not `preferencesDataStore()` delegate in Hilt modules.
**Warning signs:** "There are multiple DataStores active for the same file" crash.

### Pitfall 3: ThemeAutoSwitchEngine Startup Race

**What goes wrong:** `ThemeAutoSwitchEngine` accesses `UserPreferencesRepository` flows before DataStore is initialized, causing a suspend-forever hang.
**Why it happens:** Eager initialization in `Application.onCreate()` + DataStore lazy init.
**How to avoid:** `SharingStarted.Eagerly` with a sensible default initial value (`isSystemInDarkMode()`). The engine emits the system default immediately and updates when DataStore loads. Exactly what the old codebase does (studied source).
**Warning signs:** Theme flashes wrong mode on cold start.

### Pitfall 4: Proto Message Default Instance Confusion

**What goes wrong:** Proto `getDefaultInstance()` returns empty messages with zero/empty defaults. Checking `store.activeProfileId` returns empty string, not null.
**Why it happens:** Protobuf doesn't have null -- unset fields have type-specific defaults (0 for int, "" for string, empty for repeated).
**How to avoid:** Use explicit sentinel values or check `hasField()`. For `activeProfileId`, empty string means "default profile". For `schemaVersion`, 0 means "unversioned/new".
**Warning signs:** First-launch logic breaks because "empty" and "not set" are indistinguishable.

### Pitfall 5: LayoutMigration Failure Cascade

**What goes wrong:** A migration transformer throws, but the partially-migrated data is persisted, leaving the schema in an inconsistent state.
**Why it happens:** Migration applied directly to DataStore without backup.
**How to avoid:** F7.2 requires pre-migration backup. Copy current proto bytes to a backup file before attempting migration. On failure, restore from backup AND fall back to default preset.
**Warning signs:** Layout loads with missing or corrupted widget data after app update.

### Pitfall 6: PresetLoader TypeId Mismatch

**What goes wrong:** Old preset JSON files use `free:*` widget type IDs, but new arch uses `essentials:*`.
**Why it happens:** Pack renamed from "free" to "essentials" (MEMORY.md decision).
**How to avoid:** `PresetLoader` must remap `free:*` -> `essentials:*` when parsing preset JSON. Phase 5 creates new preset JSON files with `essentials:*` typeIds, but also handles legacy presets for safety.
**Warning signs:** Widgets fail to resolve after preset load -- `WidgetRegistry` can't find renderer for `free:speedometer`.

### Pitfall 7: ProviderSettingsStore Key Format Change

**What goes wrong:** Old code uses `{providerId}:{key}` but new arch requires `{packId}:{providerId}:{key}`.
**Why it happens:** Pack isolation requires namespacing settings by pack.
**How to avoid:** Phase 5 implements the new 3-part key format. No migration needed since this is a greenfield DataStore. Tests verify isolation between different pack namespaces.
**Warning signs:** Settings from one pack's provider overwrite another's.

### Pitfall 8: FramePacer Window Reference

**What goes wrong:** `FramePacer` needs `Window` reference from Activity, but it's constructed at `@Singleton` scope.
**Why it happens:** `Window` is only available after `Activity.onCreate()`.
**How to avoid:** Phase 5 delivers `FramePacer` with an injectable `Window` parameter on `applyFrameRate()`. Integration with `DashboardLayer` happens in Phase 7. Phase 5 tests mock `Window`.
**Warning signs:** NPE on `window.setFrameRate()` before Activity is created.

## Code Examples

### Proto Schema: dashboard_layout.proto

```protobuf
// Source: arch/persistence.md
syntax = "proto3";

option java_package = "app.dqxn.android.data.proto";
option java_multiple_files = true;

message DashboardStore {
    int32 schema_version = 1;
    repeated ProfileCanvas profiles = 2;
    string active_profile_id = 3;
    bool auto_switch_enabled = 4;
}

message ProfileCanvas {
    string profile_id = 1;
    string display_name = 2;
    int32 sort_order = 3;
    bool auto_switch_enabled = 4;
    repeated SavedWidget widgets = 5;
}

message SavedWidget {
    string id = 1;
    string type = 2;
    int32 grid_x = 3;
    int32 grid_y = 4;
    int32 width_units = 5;
    int32 height_units = 6;
    string background_style = 7;
    float opacity = 8;
    bool show_border = 9;
    bool has_glow_effect = 10;
    int32 corner_radius_percent = 11;
    int32 rim_size_percent = 12;
    optional string variant = 13;
    map<string, string> settings = 14;
    repeated string selected_data_source_ids = 15;
    int32 z_index = 16;
}
```

### DataStore Serializer

```kotlin
// Source: DataStore API documentation
object DashboardStoreSerializer : Serializer<DashboardStore> {
    override val defaultValue: DashboardStore = DashboardStore.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): DashboardStore =
        try {
            DashboardStore.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto", e)
        }

    override suspend fun writeTo(t: DashboardStore, output: OutputStream) {
        t.writeTo(output)
    }
}
```

### ThermalManager

```kotlin
// Source: arch/compose-performance.md
@Singleton
class ThermalManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: DqxnLogger,
) {
    private val _thermalLevel = MutableStateFlow(ThermalLevel.NORMAL)
    val thermalLevel: StateFlow<ThermalLevel> = _thermalLevel.asStateFlow()

    val renderConfig: StateFlow<RenderConfig> = _thermalLevel.map { level ->
        when (level) {
            ThermalLevel.NORMAL -> RenderConfig(targetFps = 60f, glowEnabled = true)
            ThermalLevel.WARM -> RenderConfig(targetFps = 45f, glowEnabled = true)
            ThermalLevel.DEGRADED -> RenderConfig(targetFps = 30f, glowEnabled = false)
            ThermalLevel.CRITICAL -> RenderConfig(targetFps = 24f, glowEnabled = false)
        }
    }.stateIn(scope, SharingStarted.Eagerly, RenderConfig(60f, true))

    fun start() {
        val pm = context.getSystemService(PowerManager::class.java)
        pm.addThermalStatusListener(context.mainExecutor) { status ->
            _thermalLevel.value = when (status) {
                PowerManager.THERMAL_STATUS_NONE,
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.NORMAL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.WARM
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.DEGRADED
                else -> ThermalLevel.CRITICAL
            }
        }
    }
}
```

### DashboardMotion (ported from old codebase verbatim)

```kotlin
// Source: old codebase DashboardAnimations.kt (verified)
object DashboardMotion {
    private val standardSpring = spring<Float>(dampingRatio = 0.65f, stiffness = 300f)
    private val standardSpringInt = spring<IntOffset>(dampingRatio = 0.65f, stiffness = 300f)
    private val hubSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)
    private val previewSpring = spring<IntOffset>(dampingRatio = 0.75f, stiffness = 380f)

    val sheetEnter = slideInVertically(standardSpringInt) { it } + fadeIn(tween(200))
    val sheetExit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150))
    // ... etc. Port all named transitions verbatim.
}
```

### Spacing Tokens (ported from old codebase)

```kotlin
// Source: old codebase DashboardThemeExtensions.kt (verified)
// NOTE: Extension target changes from DashboardThemeDefinition to top-level object
object DashboardSpacing {
    val SpaceXXS = 4.dp
    val SpaceXS = 8.dp
    val SpaceS = 12.dp
    val SpaceM = 16.dp
    val SpaceL = 24.dp
    val SpaceXL = 32.dp
    val SpaceXXL = 48.dp
    // 10 semantic aliases
    val ScreenEdgePadding = 16.dp
    val SectionGap = 16.dp
    val ItemGap = 12.dp
    val InGroupGap = 8.dp
    val ButtonGap = 8.dp
    val IconTextGap = 8.dp
    val LabelInputGap = 8.dp
    val CardInternalPadding = 16.dp
    val NestedIndent = 16.dp
    val MinTouchTarget = 48.dp
}
```

### Hardcoded Fallback Layout (F7.12)

```kotlin
// Source: REQUIREMENTS.md F7.12
object FallbackLayout {
    val FALLBACK: DashboardWidgetInstance = DashboardWidgetInstance(
        instanceId = "fallback-clock",
        typeId = "essentials:clock",
        position = GridPosition(col = 10, row = 5),
        size = GridSize(widthUnits = 10, heightUnits = 9),
        style = WidgetStyle.Default,
        settings = persistentMapOf(),
        dataSourceBindings = emptyMap(),
        zIndex = 0,
    )
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Preferences DataStore + JSON blobs for layouts | Proto DataStore with binary proto | Phase 5 (new arch) | Type safety, schema evolution, faster serialization |
| No corruption handling | `ReplaceFileCorruptionHandler` on all instances | Phase 5 (new arch) | Crash-free degradation (NF43) |
| Direct pack provider dependency in ThemeAutoSwitchEngine | Late-binding `StateFlow` inputs | Phase 5 (new arch) | Module boundary compliance, testability |
| No thermal management | `ThermalManager` + `RenderConfig` + `FramePacer` | Phase 5 (greenfield) | Proactive degradation before OS throttling |
| Extension properties on `DashboardThemeDefinition` for spacing | Top-level object `DashboardSpacing` | Phase 5 (port) | Accessible from `:core:design` (not locked to `:sdk:ui` type) |
| `LayoutMigrator` on JSON strings | `LayoutMigration` on proto messages | Phase 5 (new arch) | Binary migration, backup-before-migrate, failure fallback |

**Deprecated/outdated:**
- Old `Context.dataStore by preferencesDataStore()` pattern for Proto DataStore -- use `DataStoreFactory.create()` with explicit file paths for testability and Hilt integration
- Old `LayoutDataStore` JSON serialization -- replaced entirely by Proto DataStore
- Old `DQXNDispatchers` interface -- Phase 2 decided on qualifier annotations only (`@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`)
- Old `free:*` widget type IDs in presets -- replaced by `essentials:*`

## Open Questions

1. **ThemeAutoSwitchEngine `isSystemDarkMode` reactivity**
   - What we know: Old code uses a one-shot `flow { emit(...) }` which only emits the value at subscription time. System dark mode changes won't propagate until resubscription.
   - What's unclear: Whether `Configuration.UI_MODE_NIGHT_MASK` changes trigger a configuration change that restarts the Activity (yes on default manifest, no with `android:configChanges`).
   - Recommendation: Use a `BroadcastReceiver` for `ACTION_CONFIGURATION_CHANGED` or observe `Configuration` changes via `Lifecycle` to make it reactive. Alternatively, since targetSdk 36 forces edge-to-edge, rely on `AppCompatDelegate.getDefaultNightMode()` observation. The planner should decide the reactivity mechanism.

2. **LayoutRepository Profile Cloning Semantics**
   - What we know: F1.30 says "New profile clones the current dashboard." `cloneProfile(sourceId)` needs to deep-copy all widgets.
   - What's unclear: Whether cloned widgets get new instance IDs (yes, they must -- otherwise two profiles share the same widget ID, causing provider binding conflicts).
   - Recommendation: Generate new UUIDs for all widgets during clone. Preserve all other fields.

3. **Custom Theme Proto vs JSON Storage**
   - What we know: Architecture says "custom themes in Proto DataStore" (`custom_themes.proto`). But theme definitions include gradient specs which are Compose types (Color, Brush).
   - What's unclear: Whether to store the full theme as a proto message or store a JSON blob within proto (since gradient specs are already JSON-serializable via `GradientSpec`).
   - Recommendation: Store `CustomTheme` proto with `string colors_json` and `string gradients_json` fields. Parse JSON at read time to create `DashboardThemeDefinition`. This matches the "colors JSON blob, gradients JSON blob" described in phase-05.md.

4. **ConnectionEventStore: Proto vs Preferences**
   - What we know: Phase-05.md lists it under `:data` but the store organization table shows it as Preferences DataStore. Old code uses Preferences DataStore with a JSON-serialized list.
   - What's unclear: Whether to migrate to Proto or keep as Preferences.
   - Recommendation: Keep as Preferences DataStore (consistent with old code, rolling list of serialized events is simple key-value). It's a diagnostic log, not structured data requiring schema evolution.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit5 + MockK + Truth + Turbine |
| Config file | Convention plugin `dqxn.android.test` configures JUnit5 automatically |
| Quick run command | `./gradlew :core:thermal:testDebugUnitTest --console=plain` |
| Full suite command | `./gradlew :core:design:testDebugUnitTest :core:thermal:testDebugUnitTest :data:testDebugUnitTest :core:firebase:testDebugUnitTest --console=plain` |
| Estimated runtime | ~15-25 seconds (4 modules, unit tests only) |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| F4.1 | Theme definition with colors + gradients | unit | `:core:design:testDebugUnitTest --tests "*.ThemeJsonParserTest"` | No -- Wave 0 |
| F4.2 | JSON-driven theme loading | unit | `:core:design:testDebugUnitTest --tests "*.ThemeJsonParserTest"` | No -- Wave 0 |
| F4.3 | Dual-theme model (light/dark) | unit | `:data:testDebugUnitTest --tests "*.UserPreferencesRepositoryTest"` | No -- Wave 0 |
| F4.4 | 5 auto-switch modes | unit | `:core:design:testDebugUnitTest --tests "*.ThemeAutoSwitchEngineTest"` | No -- Wave 0 |
| F4.5 | Eager sharing at cold start | unit | `:core:design:testDebugUnitTest --tests "*.ThemeAutoSwitchEngineTest"` | No -- Wave 0 |
| F7.1 | Proto + Preferences DataStore setup | compile | `:data:compileDebugKotlin --console=plain` | N/A (compile check) |
| F7.2 | Versioned migration chain | unit | `:data:testDebugUnitTest --tests "*.LayoutMigrationTest"` | No -- Wave 0 |
| F7.3 | Debounced layout save | unit | `:data:testDebugUnitTest --tests "*.LayoutRepositoryTest"` | No -- Wave 0 |
| F7.4 | Pack-namespaced provider settings | unit | `:data:testDebugUnitTest --tests "*.ProviderSettingsStoreTest"` | No -- Wave 0 |
| F7.5 | Paired device persistence | unit | `:data:testDebugUnitTest --tests "*.PairedDeviceStoreTest"` | No -- Wave 0 |
| F7.7 | Preset system | unit | `:data:testDebugUnitTest --tests "*.PresetLoaderTest"` | No -- Wave 0 |
| F7.8 | Layout corruption fallback | unit | `:data:testDebugUnitTest --tests "*.CorruptionHandlerTest"` | No -- Wave 0 |
| F7.12 | Hardcoded fallback layout | unit | `:data:testDebugUnitTest --tests "*.FallbackLayoutTest"` | No -- Wave 0 |
| NF12 | Thermal headroom monitoring | unit | `:core:thermal:testDebugUnitTest --tests "*.ThermalManagerTest"` | No -- Wave 0 |
| NF13 | Thermal degradation tiers | unit | `:core:thermal:testDebugUnitTest --tests "*.ThermalManagerTest"` | No -- Wave 0 |
| NF43 | Corruption handler on all DataStore instances | unit | `:data:testDebugUnitTest --tests "*.CorruptionHandlerTest"` | No -- Wave 0 |

### Nyquist Sampling Rate

- **Minimum sample interval:** After every committed task -> run quick compile check: `./gradlew :core:thermal:compileDebugKotlin :data:compileDebugKotlin :core:design:compileDebugKotlin :core:firebase:compileDebugKotlin --console=plain`
- **Full suite trigger:** Before merging final task of any plan wave -> run: `./gradlew :core:design:testDebugUnitTest :core:thermal:testDebugUnitTest :data:testDebugUnitTest :core:firebase:testDebugUnitTest --console=plain`
- **Phase-complete gate:** Full suite green across all 4 modules
- **Estimated feedback latency per task:** ~8s compile, ~15s tests

### Wave 0 Gaps (must be created before implementation)

- [ ] `:core:thermal` test directory and source set
- [ ] `:core:design` test directory and source set
- [ ] `:core:firebase` test directory and source set
- [ ] `:data` test directory and source set
- [ ] `:data:proto` proto source files (`.proto` schemas) -- must exist before `:data` can compile
- [ ] Dependencies in build.gradle.kts for all 4 modules (DataStore, Hilt, Firebase, etc.)
- [ ] `consumer-proguard-rules.pro` for `:data` (R8 keep rules for proto-generated classes)

## Sources

### Primary (HIGH confidence)

- Old codebase source files (read directly):
  - `ThemeAutoSwitchEngine.kt` -- verified 5-mode combine pattern, pack isolation violation, eager sharing
  - `PresetLoader.kt` -- verified region mapping, UUID generation, fallback chain
  - `PresetModels.kt` -- verified `PresetManifest`/`PresetWidget`/`PresetWidgetStyle` schema
  - `LayoutMigrator.kt` -- verified chained N->N+1 migration, version tracking, error handling
  - `ProviderSettingsStore.kt` -- verified type-prefixed serialization, key format, API surface
  - `DashboardThemeExtensions.kt` -- verified spacing tokens, typography, emphasis, card radii (all values)
  - `DashboardAnimations.kt` -- verified spring configs, named transitions (all values)
- Project architecture docs: `persistence.md`, `compose-performance.md`, `plugin-system.md`, `ARCHITECTURE.md`
- Project requirements: `REQUIREMENTS.md` (F4.1-F4.5, F7.1-F7.12, NF12, NF13, NF43)
- Existing codebase (Phase 2-3 outputs): `DashboardThemeDefinition.kt`, `GradientSpec.kt`, `AutoSwitchMode.kt`, `CrashReporter.kt`, `AnalyticsTracker.kt`, `ErrorReporter.kt`, `WidgetStyle.kt`
- `CLAUDE.md` project conventions

### Secondary (MEDIUM confidence)

- Phase-05.md migration guide -- detailed module-by-module breakdown with port inventory
- Replication advisory sections 3, 4, 5 -- exact values for themes, animations, design tokens

### Tertiary (LOW confidence)

- `ThemeAutoSwitchEngine` system dark mode reactivity -- needs validation of `configChanges` manifest attribute interaction
- `Window.setFrameRate()` behavior on API 34 with Compose -- limited documentation on interaction with Compose's rendering pipeline

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in version catalog, patterns validated in Phases 1-3
- Architecture: HIGH -- architecture docs are detailed, old codebase source verified, module boundaries clear
- Pitfalls: HIGH -- Proto DataStore JVM split already validated in Phase 1, corruption handler pattern well-documented, old codebase patterns studied at source level

**Research date:** 2026-02-24
**Valid until:** 2026-03-24 (stable stack, no version churn expected)
