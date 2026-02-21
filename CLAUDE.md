# CLAUDE.md

## Project

DQXN — modular Android automotive dashboard. Phone/tablet in a vehicle shows real-time telemetry through configurable widgets on a grid canvas. Pack-based plugin architecture: packs register widgets, providers, and themes via contracts; the shell discovers them at runtime via Hilt multibinding.

Pre-launch greenfield. Source under `android/`. Package namespace: `app.dqxn`. Read `docs/ARCHITECTURE.md` for full technical design, `docs/REQUIREMENTS.md` for product requirements.

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
./gradlew :feature:dashboard:test                                     # Single module tests
./gradlew :feature:dashboard:testDebugUnitTest --tests "*.ClassName"  # Single test class
./gradlew connectedAndroidTest             # Integration tests
./gradlew lintDebug                        # Lint

# Fast validation (no device needed)
./gradlew :pack:free:compileDebugKotlin                  # Compile check only
./gradlew :pack:free:testDebugUnitTest                   # Compile + unit tests
./gradlew assembleDebug -Pcompose.compiler.metrics=true   # Compose stability audit
```

## Module Map

Full annotated tree in `docs/ARCHITECTURE.md` Section 3.

```
sdk/      — contracts, common, ui, observability, analytics (pack API surface)
core/     — design, thermal, driving, firebase, agentic (shell internals)
codegen/  — plugin, agentic (KSP, build-time only)
data/     — Proto + Preferences DataStore, .proto schemas
feature/  — dashboard, settings, diagnostics, onboarding
pack/     — free, plus, themes, demo
app/      — single-activity entry, DI assembly
```

## Module Dependency Rules

**The single most important rule**: Packs depend on `:sdk:*` only, never on `:feature:dashboard` or `:core:*`. The shell imports nothing from packs at compile time. If you're adding a dashboard or core import in a pack, the design is wrong. The `dqxn.pack` convention plugin auto-wires all allowed sdk dependencies — packs should not manually add `:sdk:*` project dependencies.

Full dependency matrix in `docs/ARCHITECTURE.md` Section 3. Quick reference below.

### Module Isolation Guide

**When working in `:pack:{packId}`:**
- CAN import from: `:sdk:contracts`, `:sdk:common`, `:sdk:ui`, `:sdk:observability`, `:sdk:analytics`
- CANNOT import from: `:feature:*`, `:core:*`, `:data`, other packs
- If you need something from dashboard → it belongs in `:sdk:contracts` as a contract
- Dependencies are auto-wired by the `dqxn.pack` convention plugin

**When working in `:feature:dashboard`:**
- CAN import from: `:sdk:*`, `:core:design`, `:core:thermal`, `:data`
- CANNOT import from: any `:pack:*` module
- If you need a widget-specific type → the design is wrong, use contracts

**When working in `:core:firebase`:**
- CAN import from: `:sdk:observability`, `:sdk:analytics`, `:sdk:common`
- Implements interfaces defined in observability/analytics — this is the ONLY module that imports Firebase SDKs
- CANNOT be imported by: any module other than `:app`

**When working in `:sdk:contracts`:**
- CAN import from: `:sdk:common` only
- No Compose dependencies, no Android framework types (pure Kotlin + coroutines)
- Exception: `@Composable` allowed in `WidgetRenderer.Render()` signature only

**When working in `:core:driving`:**
- CAN import from: `:sdk:contracts`, `:sdk:common`, `:sdk:observability`
- Implements `DataProvider` (emits `DrivingSnapshot`) — the sole exception to "providers come from packs"
- Shell subscribes permanently for safety gating; widgets optionally subscribe for display

### Compose Compiler Scope

Convention plugins control which modules get the Compose compiler:
- `dqxn.android.compose` — modules WITH UI: `:app`, `:feature:*`, `:sdk:ui`, `:core:design`
- Modules WITHOUT Compose: `:sdk:contracts`, `:sdk:common`, `:sdk:observability`, `:sdk:analytics`, `:core:thermal`, `:core:driving`, `:core:firebase`, `:core:agentic`, `:codegen:*`, `:data`

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
1. `android/pack/free/src/main/kotlin/app/dqxn/pack/free/widgets/batterytemp/BatteryTempRenderer.kt`
2. `android/pack/free/src/test/kotlin/app/dqxn/pack/free/widgets/batterytemp/BatteryTempRendererTest.kt`

Package: `app.dqxn.pack.{packId}.widgets.{widgetname}` (flat, no hyphens in package)
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
        val data = LocalWidgetData.current
        val battery = data.snapshot<BatterySnapshot>()
        // High-frequency data: use derivedStateOf + drawWithCache to defer to draw phase
        // Low-frequency data (battery): direct composition reads are fine
        Box(modifier = modifier) {
            Text("${battery?.temperature ?: "--"}°C", color = style.primaryTextColor)
        }
        // Coroutines: use LocalWidgetScope.current, never GlobalScope
    }
}
```

Multi-data widget: use `derivedStateOf { data.snapshot<T>() }` per slot — each independently nullable for graceful degradation.

Test skeleton:
```kotlin
class BatteryTempRendererTest : WidgetRendererContractTest() {
    override fun createRenderer() = BatteryTempRenderer()
    // Contract tests inherited. Add widget-specific tests below.
}
```

### New Data Provider (in an existing pack)

Files: `android/pack/{packId}/src/main/kotlin/app/dqxn/pack/{packId}/providers/{Name}Provider.kt` + test.

Provider flows MUST use `callbackFlow` with `awaitClose` for sensor/BLE listeners. Accumulation providers (Trip) handle high-frequency accumulation internally on `Dispatchers.Default`, emit aggregated snapshots at reduced rate.

### New Pack Module

1. Create `android/pack/{packId}/build.gradle.kts` — apply `id("dqxn.pack")` (auto-wires all sdk dependencies)
2. Add `include(":pack:{packId}")` to `android/settings.gradle.kts`
3. Add `implementation(project(":pack:{packId}"))` to `android/app/build.gradle.kts`
4. **Never** add it to `:feature:dashboard` or `:core:*` dependencies

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
- **Driving mode**: `:core:driving` is both a platform safety gate and a `DataProvider` emitting `DrivingSnapshot`. Shell subscribes permanently for safety gating (edit mode / settings / widget picker disabled). Widgets optionally subscribe for display. Only tap interactions on interactive widgets while driving.
- **Edge-to-edge**: `enableEdgeToEdge()` in onCreate. Dashboard draws behind system bars. Overlays respect `WindowInsets.systemBars`. Status bar toggle via `WindowInsetsControllerCompat`.
- **Crash recovery**: >3 crashes in 60s → safe mode (clock widget only, reset banner).

## Observability

- `:sdk:observability` — structured logging, tracing, metrics, anomaly auto-capture. No Timber — custom `DqxnLogger` with inline extensions (zero-allocation when disabled). Domain-free API.
- `LogTag` as `@JvmInline value class` — modules define own tags. Core tags in `LogTags` companion.
- `DiagnosticSnapshotCapture` auto-captures on anomalies (crash, ANR, jank, thermal, timeout, binding stall, DataStore corruption). Separate rotation pools per severity (crash: 20, thermal: 10, perf: 10). `capture()` accepts `agenticTraceId`.
- `CrashEvidenceWriter` — sync SharedPrefs in UncaughtExceptionHandler (survives process death).
- `AnrWatchdog` — dedicated thread, 2-consecutive-miss trigger, writes `anr_latest.json` via direct FileOutputStream.
- `MetricsCollector` — per-widget draw time (64-entry ring buffer), frame histograms, provider latency.
- Chaos injection via DI seams: `ChaosProviderInterceptor`, `FakeThermalManager`, `StubEntitlementManager`.
- Debug: 3 overlays (Frame Stats, Widget Health, Thermal Trending) in `:app:src/debug/`. Agentic via `AgenticContentProvider` on binder threads (debug only), file-based responses.

## Security

- SDK keys via `local.properties` / secrets gradle plugin — never in source
- Agentic ContentProvider + demo providers: **debug builds only**
- BT scan: `neverForLocation="true"`
- Deep links: Digital Asset Links verification, `autoVerify="true"`, parameter validation at NavHost level
- R8/ProGuard: each module owns `consumer-proguard-rules.pro`. Release smoke test in CI.

## Testing

- **Unit**: JUnit5 + MockK + Truth. **Hilt integration**: JUnit4 + `HiltAndroidRule` (no JUnit5 extension).
- **Flow**: Turbine + `StandardTestDispatcher` (never `UnconfinedTestDispatcher` for production flow tests)
- **Visual regression**: Roborazzi 1.56.0+ + Robolectric (Paparazzi broken on AGP 9)
- **Interaction**: `compose.ui.test` + Robolectric for drag, resize, long-press
- **Performance**: Macrobenchmarks, CI-gated (P99 frame < 16ms, startup < 1.5s)
- **Contract**: Abstract test classes in `:sdk:contracts` testFixtures — every pack widget/provider extends them
- **State machine**: Exhaustive transitions + jqwik property-based testing
- **Chaos**: `ProviderFault` sealed interface in `:sdk:contracts:testFixtures` — shared between `ChaosProviderInterceptor` (E2E) and `TestDataProvider` (unit). Correlation via `list-diagnostics since=`.
- **Coordinators**: `DashboardTestHarness` DSL — `dashboardTest { dispatch(...); assertThat(...) }`
- Shared test infra via `testFixtures` source sets. Factories: `testWidget()`, `testTheme()`, `testDataSnapshot()`.
- **Agentic debug loop**: detect → investigate → reproduce → fix+verify → guard. `HarnessStateOnFailure` outputs JSON matching `diagnose-*` shapes.

## Package & File Naming

Package pattern: `app.dqxn.{group}.{module}[.subpackage]`. Pack widgets: `app.dqxn.pack.{packId}.widgets.{name}`. Full listing in `docs/ARCHITECTURE.md`.

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
- No `GlobalScope`. No `runBlocking` (except tests and debug-only `AgenticContentProvider.call()` on binder threads).
- String resources: widgets use strings from their pack module, not hardcoded English
- All user-facing strings in Android string resources (localization-ready)
- `ImmutableList`/`ImmutableMap` everywhere in UI state — regular `List`/`Map` triggers recomposition

## Common Build Errors

### "Cannot access 'Composable': it is internal in 'androidx.compose.runtime'"
Module missing Compose compiler. Add `id("dqxn.android.compose")` to `build.gradle.kts`.

### "DataStore instance already created for file: X"
Duplicate DataStore instance. All DataStore providers must be `@Singleton`.

### Hilt "MissingBinding" for Set&lt;WidgetRenderer&gt;
Pack's `@Module` not installed in `SingletonComponent`, or pack module not in `:app` dependencies.

### KSP error: "typeId must match format {packId}:{widgetName}"
Format: lowercase, colon separator, hyphens for multi-word. Example: `core:speed-limit-circle`.

### "None of the following functions can be called with the arguments supplied" on ImmutableList
Use `.toImmutableList()`. Import: `kotlinx.collections.immutable.toImmutableList`.

### Configuration cache problems ("cannot serialize object of type")
KAPT leaked in. Only `ksp()` allowed, no `kapt()`.

### Compose stability report shows unstable parameters
Use `ImmutableList`/`ImmutableMap`/`ImmutableSet`. Annotate data classes with `@Immutable`/`@Stable`.

### Build hangs or OOM in KSP
Check `:codegen:plugin` and `:codegen:agentic` run as single pass. Verify `ksp.incremental=true`.

## Why Decisions

| Question | Answer |
|---|---|
| Why not a single DashboardState? | 60+ `.copy()` allocations/sec, universal recomposition. |
| Why not KAPT? | Breaks Gradle configuration cache. KSP is a hard requirement. |
| Why ImmutableList everywhere? | Compose treats regular `List` as unstable → forces recomposition. |
| Why no sliders in settings? | Conflict with `HorizontalPager` swipe gestures. Use discrete button groups. |
| Why Proto DataStore, not Room? | No queries needed. Proto is faster for document-style persistence with schema evolution. |
| Why no Timber? | Varargs allocation, no structured data, no trace correlation. `DqxnLogger` is zero-allocation when disabled. |
| Why `SupervisorJob` for bindings? | Without it, one provider crash propagates and kills all widget bindings. |
| Why typed DataSnapshot, not Map? | `Map<String, Any?>` boxes primitives. 60 emissions/sec × 12 widgets = 720 garbage objects/sec. |
| Why `KClass` keys in `WidgetData`? | String keys allow typos, no compiler enforcement. `snapshot<SpeedSnapshot>()` can't reference a nonexistent type. |
| Why multi-slot `WidgetData`? | Speedometer consumes 3 independent providers. Single-slot loses independent availability and graceful degradation. |
| Why plain `Layout`, not `LazyLayout`? | Absolute-position grid. `LazyLayout` adds `SubcomposeLayout` overhead without benefit. |
| Why `callbackFlow` for sensors? | Ensures cleanup via `awaitClose`. Direct `SensorEventListener` leaks registrations. |
| Why `:core:firebase`, not in `:sdk:observability`? | Would make Firebase a transitive dependency of every module. |
| Why `ContentProvider`, not `BroadcastReceiver` for agentic? | BR runs on main thread. CP runs on binder thread — `runBlocking` is safe, no ANR risk. |

Full rationale in `docs/ARCHITECTURE.md`.
