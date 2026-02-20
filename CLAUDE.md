# CLAUDE.md

## Project

DQXN — modular Android automotive dashboard. Phone/tablet in a vehicle shows real-time telemetry through configurable widgets on a grid canvas. Pack-based plugin architecture: packs register widgets, providers, and themes via contracts; the shell discovers them at runtime via Hilt multibinding.

Pre-launch greenfield. Source under `android/`. Package namespace: `app.dqxn`. Read `ARCHITECTURE.md` for full technical design, `PRD.md` for product requirements.

## Tech Stack

Kotlin 2.3+ (no Java). Jetpack Compose + Material 3. Hilt + KSP (no KAPT — required for configuration cache). Proto DataStore + Preferences DataStore. kotlinx-collections-immutable. kotlinx.serialization.

compileSdk 36, minSdk 31, targetSdk 36. AGP 9.0.1, Gradle 9.3.1, JDK 25. AGP 9 manages Kotlin compilation directly — no `org.jetbrains.kotlin.android` plugin. Uses AGP 9's new DSL (not legacy `BaseExtension`).

## Build & Run

```bash
# All commands from android/ directory. Always use --console=plain for parseable output.
./gradlew assembleDebug                    # Debug build
./gradlew assembleRelease                  # Release build
./gradlew :app:installDebug                # Install on connected device
./gradlew test                             # All unit tests
./gradlew :feature:dashboard:test          # Single module tests
./gradlew :feature:dashboard:testDebugUnitTest --tests "*.ClassName"  # Single test class
./gradlew connectedAndroidTest             # Integration tests
./gradlew :benchmark:connectedBenchmarkAndroidTest  # Macrobenchmarks
./gradlew :baselineprofile:pixel6Api33DebugAndroidTest  # Baseline profiles
./gradlew lintDebug                        # Lint

# Fast validation (no device needed)
./gradlew :feature:packs:free:compileDebugKotlin         # Compile check only (~8s incremental)
./gradlew :feature:packs:free:testDebugUnitTest           # Compile + unit tests (~12s)
./gradlew assembleDebug -Pcompose.compiler.metrics=true   # Compose stability audit
```

## Module Map

```
android/
├── build-logic/convention/       # Gradle convention plugins (composite build)
├── app/                          # Single-activity entry, DI assembly, nav host
│   ├── src/debug/                # Debug overlays, agentic framework, LeakCanary
│   └── src/release/
├── core/
│   ├── common/                   # AppResult, AppError, dispatchers, stability config
│   ├── plugin-api/               # Plugin contracts (WidgetRenderer, DataProvider, etc.)
│   ├── plugin-processor/         # KSP: @DashboardWidget → generated pack manifests
│   ├── widget-primitives/        # WidgetContainer, glow, error boundary wrapper
│   ├── design-system/            # Theme tokens, spacing, typography, shared overlay composables
│   ├── observability/            # Structured logging, tracing, metrics, health monitoring
│   ├── analytics/                # AnalyticsTracker interface, sealed event hierarchy
│   ├── thermal/                  # ThermalManager, RenderConfig, FramePacer
│   ├── agentic/                  # ADB broadcast debug automation contracts
│   └── agentic-processor/        # KSP: route listing generation
├── data/
│   ├── persistence/              # Proto DataStore, Preferences DataStore
│   └── proto/                    # .proto schema definitions
├── feature/
│   ├── dashboard/                # Dashboard shell — coordinators, grid, theme engine, presets
│   ├── driving/                  # Driving mode detection, safety gating
│   └── packs/
│       ├── free/                 # Essentials — core widgets, providers, 2 themes
│       ├── plus/                 # Plus — trip, media, G-force, altimeter, weather
│       ├── themes/               # Premium themes (JSON-driven)
│       └── demo/                 # Hardware simulation for debug/demo
├── lint-rules/                   # Custom lint: module boundaries, KAPT detection, Compose stability
├── baselineprofile/              # Baseline Profile generation
└── benchmark/                    # Macrobenchmark tests
```

## Module Dependency Rules

**The single most important rule**: Packs depend on `:core:plugin-api`, never on `:feature:dashboard`. The shell imports nothing from packs at compile time. If you're adding a dashboard import in a pack, the design is wrong.

```
:feature:packs:*    → :core:plugin-api, :core:common, :core:widget-primitives, :core:observability
:feature:dashboard  → :core:plugin-api, :core:common, :core:widget-primitives, :core:design-system,
                      :core:thermal, :core:observability, :core:analytics, :data:persistence
:feature:driving    → :core:plugin-api, :core:common, :core:observability
:core:observability → :core:common
:core:design-system → :core:common, :core:widget-primitives
:core:analytics     → :core:common, :core:observability
:app                → everything (assembly point only)
```

### Module Isolation Guide

**When working in `:feature:packs:{packId}`:**
- CAN import from: `:core:plugin-api`, `:core:common`, `:core:widget-primitives`, `:core:observability`
- CANNOT import from: `:feature:dashboard`, `:data:persistence`, `:core:thermal`, `:core:design-system`, other packs
- If you need something from dashboard → it belongs in `:core:plugin-api` as a contract

**When working in `:feature:dashboard`:**
- CAN import from: `:core:plugin-api`, `:core:common`, `:core:widget-primitives`, `:core:design-system`, `:core:thermal`, `:core:observability`, `:core:analytics`, `:data:persistence`
- CANNOT import from: any `:feature:packs:*` module
- If you need a widget-specific type → the design is wrong, use contracts

**When working in `:core:plugin-api`:**
- CAN import from: `:core:common` only
- No Compose dependencies, no Android framework types (pure Kotlin + coroutines)
- Exception: `@Composable` allowed in `WidgetRenderer.Render()` signature only

### Compose Compiler Scope

Convention plugins control which modules get the Compose compiler:
- `dqxn.android.compose` — modules WITH UI: `:app`, `:feature:*`, `:core:widget-primitives`, `:core:design-system`
- Modules WITHOUT Compose: `:core:common`, `:core:plugin-api`, `:core:plugin-processor`, `:core:observability`, `:core:analytics`, `:core:thermal`, `:data:*`

## Critical Constraints

These are non-negotiable. Violations cause real performance/correctness issues.

### State decomposition
- Each coordinator owns its own `StateFlow` slice. No god-object state class.
- Per-widget data is individual flows via `widgetData(widgetId)`. A clock tick must NOT recompose the speedometer. Never put all widget data in a shared `Map` inside a single state object.
- Discrete commands flow through sealed `DashboardCommand` → routed to coordinator via `Channel`. Continuous gestures (drag, resize) use `MutableStateFlow<DragUpdate>` — latest-value-wins, never queued behind slow commands.

### Compose performance (60fps with 12+ widgets)
- **State read deferral**: Widget data provided via `LocalWidgetData` CompositionLocal — widgets access data via `LocalWidgetData.current` and use `derivedStateOf` to defer high-frequency reads to draw phase (`drawWithCache`/`onDrawBehind`)
- **`graphicsLayer` on every widget**: Isolated RenderNode per widget
- **`@Immutable`/`@Stable` on all UI types**: Use `ImmutableList`, `ImmutableMap` from kotlinx-collections-immutable
- **`derivedStateOf`**: Use for all computed values from state (filtered lists, theme display, aggregations). Prevents unnecessary recomposition when inputs change but output doesn't.
- **Draw object caching**: `Path`, `Paint`, `Brush` via `remember` or `drawWithCache` — never allocate per frame
- **Glow**: `RenderEffect.createBlurEffect()` (GPU shader) — NOT `BlurMaskFilter` with offscreen buffers
- **Typed DataSnapshot**: Sealed subtypes per data type, 1:1 with provider boundaries (no `Map<String, Any>` boxing). `WidgetData` uses `KClass`-keyed multi-slot delivery — `data.snapshot<SpeedSnapshot>()`. Target <4KB app-level allocation/frame (excluding Compose overhead). Total budget <64KB/frame.
- **Drag reordering**: Use `graphicsLayer` offset animation — NOT `movableContentOf` (wrong tool for same-parent reordering)
- **Grid layout**: Use `Layout` composable with custom `MeasurePolicy` for absolute positioning — NOT `LazyLayout` (adds SubcomposeLayout overhead without benefit for viewport-sized grids)
- **Dashboard lifecycle**: Layer 0 uses `collectAsState()` (no lifecycle awareness). Layer 1 overlays use `collectAsStateWithLifecycle()`. Manual pause/resume for CPU-heavy overlays.
- **Frame pacing**: `Window.setFrameRate()` on API 34+, data emission throttling on API 31-33. Never fight Compose's Choreographer.

### Widget binding isolation
- `WidgetBindingCoordinator` uses `SupervisorJob` as parent for all binding jobs. One provider crash must NOT cancel siblings.
- Widget `LaunchedEffect`/`SideEffect` run inside a supervised `WidgetCoroutineScope` provided via CompositionLocal. This covers the error boundary gap where effects bypass composition catch.
- Binding jobs use `CoroutineExceptionHandler` — failures report via `widgetStatus`, never propagate.

### DataStore singletons
- Never create multiple DataStore instances for the same file — all `@Singleton`
- **`ReplaceFileCorruptionHandler` required on ALL DataStore instances** — corruption falls back to defaults, never crashes
- Proto DataStore for structured data (layouts, paired devices, custom themes)
- Preferences DataStore for simple settings
- Provider settings: pack-namespaced keys `{packId}:{providerId}:{key}`
- Layout saves debounced at 500ms, actual writes on `Dispatchers.IO`

## Creating New Components

### New Widget (in an existing pack)

Files to create (example: `core:battery-temp` in free pack):
1. `android/feature/packs/free/src/main/kotlin/app/dqxn/feature/packs/free/widgets/batterytemp/BatteryTempRenderer.kt`
2. `android/feature/packs/free/src/test/kotlin/app/dqxn/feature/packs/free/widgets/batterytemp/BatteryTempRendererTest.kt`

Package: `app.dqxn.feature.packs.{packId}.widgets.{widgetname}` (flat, no hyphens in package)
TypeId: `{packId}:{widget-name}` (hyphens in typeId, not in package)

Widget skeleton:
```kotlin
@DashboardWidget(
    typeId = "free:battery-temp",
    displayName = "Battery Temperature",
    packId = "free",
)
class BatteryTempRenderer @Inject constructor() : WidgetRenderer {
    override val typeId = "free:battery-temp"
    override val displayName = "Battery Temperature"
    override val description = "Battery level and temperature"
    override val compatibleSnapshots = setOf(BatterySnapshot::class)
    override val requiredAnyEntitlement: Set<String>? = null // free
    override val settingsSchema: List<SettingDefinition<*>> = emptyList()
    override val aspectRatio: Float? = null // null = freeform, 1f = square

    override fun getDefaults(context: WidgetContext) = WidgetDefaults(widthUnits = 8, heightUnits = 6)

    override fun accessibilityDescription(data: WidgetData): String {
        val battery = data.snapshot<BatterySnapshot>()
        return "Battery temperature: ${battery?.temperature ?: "unavailable"}"
    }

    @Composable
    override fun Render(
        isEditMode: Boolean,
        style: WidgetStyle,
        settings: ImmutableMap<String, Any>,
        modifier: Modifier,
    ) {
        // Widget data accessed via CompositionLocal — enables state read deferral
        val data = LocalWidgetData.current
        val battery = data.snapshot<BatterySnapshot>()

        // For high-frequency data, use derivedStateOf + drawWithCache to defer reads to draw phase
        // For low-frequency data (battery), direct composition reads are fine
        Box(modifier = modifier) {
            Text("${battery?.temperature ?: "--"}°C", color = style.primaryTextColor)
        }

        // If this widget needs coroutines, use LocalWidgetScope.current:
        // val widgetScope = LocalWidgetScope.current
        // LaunchedEffect(Unit) { widgetScope.launch { ... } }
    }
}
```

Multi-data-type widget example (speedometer consuming 3 snapshot types):
```kotlin
override val compatibleSnapshots = setOf(
    SpeedSnapshot::class,
    AccelerationSnapshot::class,
    SpeedLimitSnapshot::class,
)

@Composable
override fun Render(isEditMode: Boolean, style: WidgetStyle, settings: ImmutableMap<String, Any>, modifier: Modifier) {
    val data = LocalWidgetData.current
    // Each slot is independently nullable — widget renders with whatever data is available
    val speed = remember { derivedStateOf { data.snapshot<SpeedSnapshot>()?.speed ?: 0f } }
    val accel = remember { derivedStateOf { data.snapshot<AccelerationSnapshot>()?.acceleration } }
    val limit = remember { derivedStateOf { data.snapshot<SpeedLimitSnapshot>()?.speedLimit } }
    SpeedometerCanvas(speed, accel, limit)
}
```

Test skeleton:
```kotlin
class BatteryTempRendererTest : WidgetRendererContractTest() {
    override fun createRenderer() = BatteryTempRenderer()

    // Contract tests inherited. Add widget-specific tests below:
}
```

### New Data Provider (in an existing pack)

Files: `android/feature/packs/{packId}/src/main/kotlin/app/dqxn/feature/packs/{packId}/providers/{Name}Provider.kt` + test.

Provider flows MUST use `callbackFlow` with `awaitClose` for sensor/BLE listeners. Accumulation providers (Trip) handle high-frequency accumulation internally on `Dispatchers.Default`, emit aggregated snapshots at reduced rate.

### New Pack Module

1. Create `android/feature/packs/{packId}/build.gradle.kts` (copy free pack as template)
2. Add `include(":feature:packs:{packId}")` to `android/settings.gradle.kts`
3. Add `implementation(project(":feature:packs:{packId}"))` to `android/app/build.gradle.kts`
4. **Never** add it to `:feature:dashboard` dependencies

## Plugin Conventions

- `@DashboardWidget` / `@DashboardDataProvider` annotations → KSP generates `PackManifest` → Hilt multibinding
- `typeId` format: `{packId}:{widgetName}` — e.g., `core:speedometer`, `plus:trip`, `core:speed-limit-circle` (hyphens for multi-word)
- Settings UI is schema-driven via `SettingDefinition<T>`. No custom composables per widget for settings. **No sliders** (conflict with HorizontalPager gestures).
- Entitlement gating: `Gated` interface, OR logic (`requiredAnyEntitlement`). Themes: preview always, gate at persistence. Revocation is reactive — auto-revert to free defaults.
- Data types are string identifiers, not closed enums. Packs register `DataTypeDescriptor` alongside providers.

## Architecture Patterns

- **Dashboard-as-shell**: Dashboard is Layer 0, always present — NOT a navigation destination. Overlays navigate on Layer 1 via `OverlayNavHost`.
- **IoC data binding**: Widgets never choose their data source. `WidgetDataBinder` assigns providers by data type, with fallback to next available on failure.
- **Widget error isolation**: Each widget in a catch boundary (effects via `WidgetCoroutineScope`). Compose has NO composition-phase try/catch — mitigated by contract tests, crash count tracking, and safe mode fallback. Failed widget → fallback UI, never app crash.
- **ConnectionStateMachine**: FSM with validated transitions. No ad-hoc `MutableStateFlow<ConnectionState>`.
- **Thermal adaptation**: `ThermalManager` → `RenderConfig`. Glow disabled at DEGRADED, frame rate reduced via `Window.setFrameRate()` (API 34+) or data emission throttling (API 31-33).
- **Driving mode**: `isDriving == true` → edit mode / settings / widget picker disabled. Only tap interactions on interactive widgets.
- **Edge-to-edge**: `enableEdgeToEdge()` in onCreate. Dashboard draws behind system bars. Overlays respect `WindowInsets.systemBars`. Status bar toggle via `WindowInsetsControllerCompat`.
- **Crash recovery**: >3 crashes in 60s → safe mode (clock widget only, reset banner).

## Observability

- `:core:observability` provides structured logging, tracing, metrics. No Timber — custom `DqxnLogger` with inline zero-allocation extensions.
- `LogTag` enum for all subsystems (LAYOUT, THEME, SENSOR, BLE, CONNECTION_FSM, DATASTORE, THERMAL, etc.)
- `TraceContext` via `CoroutineContext.Key` for cross-coordinator correlation
- `MetricsCollector` with pre-allocated counters — frame histograms, recomposition counts, provider latency
- `AnrWatchdog` on dedicated thread — 2s ping / 2.5s timeout, captures stack + ring buffer context on stall
- Debug overlays in `:app:src/debug/` — frame stats, recomposition viz, provider flow DAG, thermal trending

## Security

- SDK keys via `local.properties` / secrets gradle plugin — never in source
- Agentic receiver + demo providers: **debug builds only**
- BT scan: `neverForLocation="true"`
- Deep links: Digital Asset Links verification, `autoVerify="true"`, parameter validation at NavHost level
- R8/ProGuard: each module owns `consumer-proguard-rules.pro`. Release smoke test in CI.

## Testing

- **Unit**: JUnit5 + MockK + Truth
- **Flow**: Turbine + `StandardTestDispatcher` (never `UnconfinedTestDispatcher` for production flow tests)
- **Visual regression**: Roborazzi + Robolectric
- **Interaction**: `compose.ui.test` + Robolectric for drag, resize, long-press
- **Performance**: Macrobenchmarks, CI-gated (P99 frame < 16ms, startup < 1.5s)
- **Contract**: Abstract test classes in `:core:plugin-api` testFixtures — every pack widget/provider extends them
- **State machine**: Exhaustive transitions + jqwik property-based testing
- **Chaos**: ChaosEngine with random provider failures, thermal spikes, entitlement churn
- **Fuzz**: Jazzer on JSON theme/preset parsing
- **Coordinators**: `DashboardTestHarness` DSL — `dashboardTest { dispatch(...); assertThat(...) }`
- **Accessibility**: Semantics assertions for touch targets (76dp automotive), contrast verification per theme

Shared test infrastructure via Gradle `testFixtures` source sets per module. Factory functions: `testWidget()`, `testTheme()`, `testDataSnapshot()`.

## Package Naming

```
app.dqxn.core.plugin.api              — plugin contracts
app.dqxn.core.common                  — shared utilities
app.dqxn.core.widget                  — widget primitives (container, theme types)
app.dqxn.core.design                  — design system tokens, shared composables
app.dqxn.core.observability           — logging, tracing, metrics
app.dqxn.core.observability.log       — DqxnLogger, LogEntry, LogTag
app.dqxn.core.observability.trace     — DqxnTracer, TraceContext, Span
app.dqxn.core.observability.metrics   — MetricsCollector, FrameTracer
app.dqxn.core.observability.health    — WidgetHealthMonitor, ThermalTrendAnalyzer
app.dqxn.core.observability.crash     — CrashContextProvider, AnrWatchdog, ErrorReporter
app.dqxn.core.analytics               — AnalyticsTracker, AnalyticsEvent
app.dqxn.core.thermal                 — ThermalManager, RenderConfig, FramePacer
app.dqxn.data.persistence             — DataStore implementations
app.dqxn.feature.dashboard            — dashboard shell root
app.dqxn.feature.dashboard.coordinator — state coordinators
app.dqxn.feature.dashboard.ui         — composables
app.dqxn.feature.driving              — driving mode
app.dqxn.feature.packs.free           — free pack root
app.dqxn.feature.packs.free.widgets.{name} — one subpackage per widget
app.dqxn.feature.packs.free.providers — providers
app.dqxn.feature.packs.free.themes    — theme definitions
app.dqxn.feature.packs.free.di        — Hilt modules
```

## File Naming

```
Widgets:           {PascalCaseName}Renderer.kt     (SpeedometerRenderer.kt)
Providers:         {PascalCaseName}Provider.kt      (GpsSpeedProvider.kt)
Tests:             {ClassName}Test.kt               (SpeedometerRendererTest.kt)
Hilt modules:      {PackName}Module.kt              (FreePackModule.kt)
Theme files:       {pack_id}_themes.json            (themes_pack_themes.json)
Proto schemas:     {entity_name}.proto              (dashboard_canvas.proto)
Convention plugins: dqxn.android.{purpose}.gradle.kts
```

## Code Style

- Explicit return types on all public functions (helps Compose compiler stability inference)
- `internal` visibility by default for all non-contract types in pack modules
- No `var` in data classes — always `val` with `.copy()`
- Prefer `sealed interface` over `sealed class` (no unnecessary state in the type)
- Coroutine scope: never create `CoroutineScope(Dispatchers.Main)` manually — use Hilt injection or `viewModelScope`
- No `GlobalScope`. No `runBlocking` (except tests).
- String resources: widgets use strings from their pack module, not hardcoded English
- All user-facing strings in Android string resources (localization-ready)
- `ImmutableList`/`ImmutableMap` everywhere in UI state — regular `List`/`Map` triggers recomposition

## Common Build Errors

### "Cannot access 'Composable': it is internal in 'androidx.compose.runtime'"
Module is missing the Compose compiler plugin. Add `id("dqxn.android.compose")` to its `build.gradle.kts`. Only for modules with `@Composable` functions.

### "DataStore instance already created for file: X"
Two DataStore instances for the same file. Find the duplicate `@Provides` function. All DataStore providers must be `@Singleton` in a single Hilt module.

### Hilt "MissingBinding" for Set&lt;WidgetRenderer&gt;
A pack's `@Module` is not installed in `SingletonComponent`, or the pack module isn't in `:app`'s dependencies.

### KSP error: "typeId must match format {packId}:{widgetName}"
Invalid `@DashboardWidget` typeId. Format: lowercase, colon separator, hyphens for multi-word. Example: `core:speed-limit-circle`.

### "None of the following functions can be called with the arguments supplied" on ImmutableList
Passing `List` where `ImmutableList` expected. Use `.toImmutableList()`. Import: `kotlinx.collections.immutable.toImmutableList`.

### Configuration cache problems ("cannot serialize object of type")
KAPT leaked in. Check no module uses `kapt()` — only `ksp()` allowed.

### Compose stability report shows unstable parameters
`List`, `Map`, or `Set` parameter instead of `ImmutableList`, `ImmutableMap`, `ImmutableSet`. Or a data class missing `@Immutable`/`@Stable`.

### Build hangs or OOM in KSP
Likely two KSP processors conflicting. Ensure `plugin-processor` and `agentic-processor` run as a single pass. Check `ksp.incremental=true` in `gradle.properties`.

## Why Decisions

For agents that wonder "why not just...":

| Question | Answer |
|---|---|
| Why not a single DashboardState? | 60+ `.copy()` allocations/sec, universal recomposition. See ARCHITECTURE.md Section 5. |
| Why not KAPT? | Breaks Gradle configuration cache. KSP is a hard requirement. |
| Why not runtime plugin loading? | Security, stability, compile-time type safety. All packs are first-party. |
| Why ImmutableList everywhere? | Compose treats regular `List` as unstable → forces recomposition even when contents unchanged. |
| Why no sliders in settings? | They conflict with `HorizontalPager` swipe gestures. Use discrete button groups. |
| Why Proto DataStore, not Room? | No queries needed. Layout is a single document. Proto is faster for document-style persistence with schema evolution. |
| Why no Timber? | Varargs allocation on every call, no structured data, no trace correlation. Custom `DqxnLogger` with inline extensions is zero-allocation when disabled. |
| Why `SupervisorJob` for bindings? | Without it, a `CancellationException` in one provider propagates up and kills all widget bindings. |
| Why typed DataSnapshot, not Map? | `Map<String, Any?>` boxes every `Float`/`Int`. At 60 emissions/sec across 12 widgets = 720 garbage objects/sec. Typed sealed subtypes store primitives in data class fields — zero boxing. |
| Why `KClass` keys in `WidgetData`, not string keys? | String-keyed `Map<String, DataSnapshot>` allows typos, has no compiler enforcement, and requires runtime `DataTypeDescriptor` validation. `KClass` keys make the type system do the work — `snapshot<SpeedSnapshot>()` cannot reference a nonexistent type. |
| Why multi-slot `WidgetData`, not single-slot? | Speedometer consumes speed + acceleration + speed limit from 3 independent providers. Single-slot forces a composite provider that owns all three sources, violating IoC and losing independent availability (no accelerometer → fabricated zero value). Multi-slot preserves graceful degradation. |
| Why 1:1 provider-to-snapshot, not composite snapshots? | GPS speed, accelerometer, and map speed-limit have different availability, frequency, and failure modes. Composites push binding logic into providers and lose independent availability. 1:1 alignment means each provider emits its own type; the binder `combine()`s them. |
| Why plain `Layout`, not `LazyLayout` or `LazyGrid`? | Grid placement is absolute-position, not flow-based. `LazyLayout` adds `SubcomposeLayout` overhead without benefit for viewport-sized grids. Plain `Layout` + custom `MeasurePolicy` is lighter. |
| Why `callbackFlow` for sensor providers? | Ensures proper cleanup via `awaitClose`. Direct `SensorEventListener` without it leaks the registration. |
