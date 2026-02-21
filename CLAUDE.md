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
./gradlew :benchmark:connectedBenchmarkAndroidTest  # Macrobenchmarks
./gradlew :baselineprofile:pixel6Api33DebugAndroidTest  # Baseline profiles
./gradlew lintDebug                        # Lint

# Fast validation (no device needed)
./gradlew :pack:free:compileDebugKotlin                  # Compile check only (~8s incremental)
./gradlew :pack:free:testDebugUnitTest                   # Compile + unit tests (~12s)
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

- `:sdk:observability` provides structured logging, tracing, metrics, and anomaly auto-capture. No Timber — custom `DqxnLogger` with inline extensions (zero-allocation when disabled via two-overload pattern: no-fields and fields-lambda). Designed domain-free for reusability — no DQXN-specific types in public API.
- `LogTag` as a `@JvmInline value class LogTag(val value: String)` — each module defines its own tags without modifying `:sdk:observability`. Core tags in `LogTags` companion object (LAYOUT, THEME, SENSOR, BLE, CONNECTION_FSM, DATASTORE, THERMAL, BINDING, ANR, AGENTIC, DIAGNOSTIC, INTERACTION, etc.)
- `DiagnosticSnapshotCapture` auto-captures correlated state (ring buffer tail, metrics, thermal, widget health, active traces) on anomalies (widget crash, ANR, thermal escalation, jank spike, provider timeout, escalated staleness, binding stall, DataStore corruption). Debug builds persist to `${filesDir}/debug/diagnostics/` with separate rotation pools per trigger severity (crash: 20, thermal: 10, performance: 10). Storage pressure check skips writes below 10MB free. `capture()` accepts `agenticTraceId` for agentic correlation. Reentrance guard logs dropped captures at WARN level (cascade failures). `WidgetExpectations` computed on-demand by `diagnose-widget`, not stored in snapshot.
- `CrashEvidenceWriter` — `UncaughtExceptionHandler` that synchronously writes minimal crash record (typeId, exception, top 5 frames, thermal, timestamp) to `SharedPreferences` before process death. Ensures `diagnose-crash` always has evidence after safe mode activation, even when async snapshot writes don't complete.
- `TraceContext` via `CoroutineContext.Key` for cross-coordinator correlation. Widget effect trace propagation (`LocalWidgetTraceContext`) deferred — `widgetId` + timestamps provide sufficient correlation for v1.
- `JankDetector` monitors consecutive jank frames, fires `DiagnosticSnapshotCapture` at exponential thresholds (5, 20, 100 consecutive >16ms frames) to capture both onset and sustained jank progression
- `MetricsCollector` with pre-allocated counters — frame histograms, recomposition counts, provider latency, per-widget draw time (64-entry ring buffer per widget, ~25ns overhead via `System.nanoTime()` in draw modifier)
- `AnrWatchdog` on dedicated thread — 2s ping / 2.5s timeout, requires 2 consecutive missed pings (~5s block) before capturing. Skips under `Debug.isDebuggerConnected()`. Writes `anr_latest.json` via direct `FileOutputStream` (survives main-thread deadlock).
- Binding lifecycle events logged at INFO with `BINDING` tag — never sampled, always in ring buffer for `diagnose-widget` history. Required fields: `widgetId` + `traceId`. Specific events defined by implementation.
- Debug: agent polls `list-diagnostics` with `since` param every 2-5s for anomaly notification. Each entry includes `recommendedCommand` routing hint. 3 debug overlays (Frame Stats, Widget Health, Thermal Trending) in `:app:src/debug/`
- `DeduplicatingErrorReporter` wraps `ErrorReporter` (both `reportNonFatal` and `reportWidgetCrash`) with configurable time window via `ConcurrentHashMap.compute()` — `suppressedCount` tracks absorbed duplicates. Interaction events (tap, drag, resize) logged via standard `DqxnLogger` with `INTERACTION` tag — no separate capture system needed since RingBufferSink already retains them for diagnostic snapshots.
- Chaos injection via DI seams: `FakeThermalManager`, `StubEntitlementManager.simulateRevocation()`, `DataProviderInterceptor` + `ChaosProviderInterceptor`. Chaos→diagnostic correlation via temporal matching (`list-diagnostics since=`).
- Agentic commands run via debug `AgenticContentProvider` on binder threads — no main-thread involvement. Lock-free `query()` paths (`content://app.dqxn.android.debug.agentic/health`, `/anr`) for deadlock diagnosis. `AnrWatchdog` writes `anr_latest.json` on dedicated thread as last-resort escape.

## Security

- SDK keys via `local.properties` / secrets gradle plugin — never in source
- Agentic ContentProvider + demo providers: **debug builds only**
- BT scan: `neverForLocation="true"`
- Deep links: Digital Asset Links verification, `autoVerify="true"`, parameter validation at NavHost level
- R8/ProGuard: each module owns `consumer-proguard-rules.pro`. Release smoke test in CI.

## Testing

- **Unit**: JUnit5 (`de.mannodermaus.android-junit` 2.0.1+) + MockK + Truth
- **Flow**: Turbine + `StandardTestDispatcher` (never `UnconfinedTestDispatcher` for production flow tests)
- **Visual regression**: Roborazzi 1.56.0+ + Robolectric (Paparazzi broken on AGP 9)
- **Interaction**: `compose.ui.test` + Robolectric for drag, resize, long-press
- **Performance**: Macrobenchmarks, CI-gated (P99 frame < 16ms, startup < 1.5s)
- **Contract**: Abstract test classes in `:sdk:contracts` testFixtures — every pack widget/provider extends them
- **State machine**: Exhaustive transitions + jqwik property-based testing (jqwik is a JUnit5 test engine)
- **Hilt integration**: JUnit4 + `HiltAndroidRule` (no JUnit5 extension exists for Hilt)
- **Chaos**: `ProviderFault` sealed interface (`Kill`, `Delay`, `Error`, `ErrorOnNext`, `Corrupt`, `Flap`) shared between `ChaosProviderInterceptor` (E2E) and `TestDataProvider` (unit) via `:sdk:contracts:testFixtures` — identical fault primitives across all test layers. `chaos-inject` for targeted single faults, `chaos-start`/`chaos-stop` for session tracking with optional seed. Chaos→diagnostic correlation via temporal matching (`list-diagnostics since=`). `assertChaosCorrelation` helper validates fault→snapshot linkage by timestamp.
- **Fuzz**: kotlinx.fuzz (JetBrains, built on Jazzer) on JSON theme/preset parsing — better Kotlin coverage than raw Jazzer
- **Mutation**: Pitest deferred to post-launch. JVM-only target modules (`:sdk:common`, `:sdk:contracts`, `:codegen:plugin`, `:codegen:agentic`) + `pitest-kotlin` extension when enabled. Android module mutation produces noisy false positives in Compose UI code.
- **Coordinators**: `DashboardTestHarness` DSL — `dashboardTest { dispatch(...); assertThat(...) }`
- **Tier 5.5**: On-device smoke validation (~30s) between visual regression and full instrumented suite — `assembleDebug` + `install` + `adb shell content call` health check. Catches manifest/DI/provider failures without full instrumented test overhead.
- **Observability self-tests**: `AnrWatchdog` debounce (2-miss threshold), debugger skip, `CrashEvidenceWriter` sync commit verification
- **Safety-critical**: Safe mode activation (>3 total crashes across all widgets in 60s → clock-only + reset banner), driving gate (speed > threshold → edit/settings disabled), entitlement grace period (7-day offline → downgrade) — all coordinator-level tests with `StandardTestDispatcher`
- **DataStore resilience**: Corruption handler fallback-to-defaults + `ErrorReporter` verification, schema migration N→N+1 roundtrip, corruption-during-migration recovery
- **Accessibility**: Semantics assertions for touch targets (76dp automotive), contrast verification per theme

Shared test infrastructure via Gradle `testFixtures` source sets per module. Factory functions: `testWidget()`, `testTheme()`, `testDataSnapshot()`. `ProviderFault` in `:sdk:contracts:testFixtures` for shared fault injection.

**Agentic debug loop** (5 steps): detect (poll `list-diagnostics` with `since`) → investigate (`diagnose-*` via `recommendedCommand` hint) → reproduce (`chaos-inject`, temporal correlation) → fix+verify (code change + targeted test) → guard (write regression test that fails without the fix). `HarnessStateOnFailure` outputs JSON matching `diagnose-*` response shapes for seamless agent consumption. CI collects `adb pull` diagnostic artifacts on test failure.

## Package Naming

```
app.dqxn.sdk.contracts                — plugin contracts (WidgetRenderer, DataProvider, DataSnapshot subtypes)
app.dqxn.sdk.common                   — shared utilities (AppResult, AppError, dispatchers)
app.dqxn.sdk.ui                       — widget primitives (WidgetContainer, WidgetStyle, LocalWidgetData)
app.dqxn.sdk.observability            — logging, tracing, metrics
app.dqxn.sdk.observability.log        — DqxnLogger, LogEntry, LogTag
app.dqxn.sdk.observability.trace      — DqxnTracer, TraceContext, Span
app.dqxn.sdk.observability.metrics    — MetricsCollector, FrameTracer
app.dqxn.sdk.observability.health     — WidgetHealthMonitor, ThermalTrendAnalyzer
app.dqxn.sdk.observability.crash      — CrashReporter (includes metadata keys), ErrorReporter, CrashContextProvider, AnrWatchdog
app.dqxn.sdk.observability.diagnostic — DiagnosticSnapshotCapture, DiagnosticSnapshot, AnomalyTrigger
app.dqxn.sdk.analytics                — AnalyticsTracker, PackAnalytics, AnalyticsEvent
app.dqxn.core.design                  — design system tokens, shared composables
app.dqxn.core.thermal                 — ThermalManager, RenderConfig, FramePacer
app.dqxn.core.driving                 — DrivingStateDetector, DrivingSnapshot DataProvider
app.dqxn.core.firebase                — Hilt module, FirebaseCrashReporter, FirebaseErrorReporter
app.dqxn.core.firebase.analytics      — FirebaseAnalyticsTracker
app.dqxn.core.firebase.perf           — Firebase Performance Monitoring (direct usage, no abstraction layer — extract PerformanceTracer interface when a second consumer needs it)
app.dqxn.core.agentic                 — agentic command handlers + annotations (debug only)
app.dqxn.codegen.plugin               — KSP plugin processor handlers
app.dqxn.codegen.agentic              — KSP agentic command processor (param validation, schema generation, handler wiring)
app.dqxn.data                         — DataStore implementations, proto schemas
app.dqxn.feature.dashboard            — dashboard shell root
app.dqxn.feature.dashboard.coordinator — state coordinators
app.dqxn.feature.dashboard.ui         — composables
app.dqxn.feature.settings             — settings sheet
app.dqxn.feature.diagnostics          — provider health, connection log
app.dqxn.feature.onboarding           — progressive tips, first-launch flows
app.dqxn.pack.free                   — free pack root
app.dqxn.pack.free.widgets.{name}    — one subpackage per widget
app.dqxn.pack.free.providers         — providers
app.dqxn.pack.free.themes            — theme definitions
app.dqxn.pack.free.di                — Hilt modules
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
- No `GlobalScope`. No `runBlocking` (except tests and debug-only `AgenticContentProvider.call()` on binder threads).
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
Likely two KSP processors conflicting. Ensure `:codegen:plugin` and `:codegen:agentic` run as a single pass. Check `ksp.incremental=true` in `gradle.properties`.

### `AgenticMainThreadBan` lint error in CommandHandler (planned)
This lint rule is deferred — not in the initial 3-rule set (ModuleBoundaryViolation, KaptDetection, ComposeInNonUiModule). When added: a `CommandHandler.handle()` implementation is using `Dispatchers.Main` or `withContext(Dispatchers.Main)`. Agentic handlers run via `runBlocking(Dispatchers.Default)` on a binder thread — switching to `Dispatchers.Main` inside would couple handler latency to main-thread load and risk deadlock if the main thread is waiting on a binder call. Use `Dispatchers.Default` or `Dispatchers.IO` instead.

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
| Why `:core:firebase` module, not Firebase in `:sdk:observability`? | `:sdk:observability` is a transitive dependency of every module. Putting Firebase there means the entire Firebase SDK becomes a transitive dependency of packs, contracts, and data modules. Interfaces in observability, implementations in `:core:firebase`, wired via Hilt `@Binds`. |
| Why no Crashlytics NDK? | No first-party native code. `RenderEffect.createBlurEffect()` is the only non-trivial GPU path, but it's a standard Android API — Compose/Skia/HWUI native code is framework-level, not app-specific risk. If unexplained silent process deaths appear post-launch (via `session_active` flag), add `firebase-crashlytics-ndk` then. |
| Why Firebase Perf alongside MetricsCollector? | Different scopes. `MetricsCollector` handles hot-path instrumentation (per-frame timing, atomic counters) locally. Firebase Perf provides remote-visible coarse-grained operation traces (startup, theme switch, widget bind) in the Firebase console. They complement, not overlap. |
| Why not KSP for Firebase provider discovery? | Exactly one crash reporter and one analytics tracker per build variant. No set to discover, no manifest to generate. Standard Hilt `@Binds` is the right tool — KSP adds complexity for zero value here. |
| Why JUnit5 for unit tests but JUnit4 for Hilt integration? | Hilt has no JUnit5 extension — `HiltAndroidRule` is JUnit4-only. No credible community bridge exists. JUnit5 provides `@Tag` filtering (tiered validation) and hosts jqwik as a test engine. Pragmatic split, not an oversight. |
| Why kotlinx.fuzz over raw Jazzer? | Raw Jazzer has coverage collection failures on Kotlin language features. kotlinx.fuzz (JetBrains, 2025) wraps Jazzer with a Kotlin-native API, Gradle plugin, and better Kotlin coverage. Same engine, better DX. |
| Why Roborazzi over Paparazzi? | Paparazzi is broken on AGP 9 with no fix merged (Feb 2026). Roborazzi fixed in 1.56.0 (Jan 2026). Roborazzi also supports pre-capture interactions (tap, drag, state changes) via Robolectric, which matters for widget edit/resize/drag states. |
| Why `sdk/` vs `core/` split? | Packs must depend only on contracts, not shell internals. Flat `core/` mixed pack-visible modules with shell-only modules — boundary was documented but not structural. `sdk/` makes the pack API surface visible in the file tree. |
| Why packs at top level, not under `feature/`? | Packs are extensions discovered via Hilt multibinding, not features with screens/routes. Nesting them under `feature/` incorrectly implies they're the same module category as dashboard or settings. |
| Why driving in `core/`, not `feature/`? | Driving detection has no UI — it's a runtime service (GPS speed → threshold → state). It's also a safety gate that must work regardless of which features are present. Cross-cutting platform concern, not a user-facing feature. |
| Why driving is also a `DataProvider`? | Driving state is a reactive data source derived from sensors — same pattern as every other provider. Widgets (trip computer, driving indicator) should consume it through standard binding, not a parallel side-channel. Shell permanently subscribes for safety; widgets optionally subscribe for display. |
| Why `codegen/` folder for KSP processors? | Plugin processor runs 7 handlers (settings, themes, entitlements, resources, 3 validators). Substantial shared KSP/KotlinPoet infrastructure. Build-time only with zero runtime presence. Grouping separates build-time from runtime modules. |
| Why KSP for agentic command registry? | ~30 commands each with distinct param schemas, type coercion, null checking, error wrapping, and response serialization. KSP eliminates per-handler boilerplate and generates param schemas for `list-commands`. Missing handler = compilation error (same safety as exhaustive `when`, but handler is colocated with the annotation instead of a centralized routing function). Discovery across modules isn't the value — boilerplate elimination is. |
| Why analytics accessible to packs? | Packs know their interaction semantics (Media Controller play/pause, Trip reset). Shell shouldn't proxy every pack interaction. `PackAnalytics` is a scoped interface — packs fire structured events, implementation prepends pack namespace. |
| Why observability and analytics not split into api + impl? | Both have the same pattern (packs use a subset, shell uses the whole thing). Splitting each into two modules doubles module count for marginal enforcement. `sdk/` vs `core/` handles the big boundary; within-module access uses Kotlin visibility. Consistent treatment for both. |
| Why separate snapshot rotation pools? | Thermal oscillation in vehicles is frequent (sun exposure, AC cycling). A shared 20-file pool would evict crash snapshots within one drive session. Separate pools (crash: 20, thermal: 10, perf: 10) guarantee crash data survives. |
| Why poll `list-diagnostics`, not file-based event stream? | File-based approaches (`tail -f`) are unreliable across process death and log rotation. Polling `list-diagnostics` with `since` param is idempotent, survives restarts, and returns structured JSON with `recommendedCommand` per entry. 2-5s poll interval provides adequate notification latency for debug loops. |
| Why `DataProviderInterceptor`, not `@VisibleForTesting` on `WidgetDataBinder`? | `@VisibleForTesting` is a lint annotation, not access control. `internal` doesn't cross module boundaries. An interceptor interface via Hilt multibinding is a clean seam — production builds register nothing (zero overhead), debug builds register `ChaosProviderInterceptor`. |
| Why `EscalatedStaleness` at 3x threshold, not 1x? | 1x staleness is routine and transient (brief provider hiccup, thermal throttling). 3x while provider reports CONNECTED indicates a genuine data flow breakdown — worth capturing diagnostics for. |
| Why no `OnDemandCapture` in `AnomalyTrigger`? | Pollutes the production sealed hierarchy with a test concern. Every `when` expression matching `AnomalyTrigger` gains a branch. `diagnose-crash` falls back to assembling live state from existing components — same data, no production code change. |
| Why reentrance guard on `DiagnosticSnapshotCapture`? | `capture()` calls `DqxnTracer.activeSpans()` which may log. If logging triggers another anomaly detection, recursive capture would stack overflow. `AtomicBoolean` guard returns `null` on reentry. |
| Why `CompositionLocal` for TraceContext in widgets, not injecting into `WidgetData`? | **Deferred to post-v1** — widget-level trace propagation not needed until performance profiling requires per-widget span correlation. Design rationale preserved: `WidgetData` is the data model consumed by every widget renderer. Adding trace metadata to it pollutes the data contract with debug concerns. A `CompositionLocal` is zero-cost when null and invisible to widget implementations that don't use it. |
| Why sync crash evidence via SharedPreferences, not just async DiagnosticSnapshot? | `DiagnosticSnapshotCapture` writes files asynchronously on `Dispatchers.IO`. If a crash kills the process (which is exactly when safe mode triggers), the async write may not complete. Sync `SharedPreferences.commit()` in `UncaughtExceptionHandler` ensures the agent always has crash evidence after safe mode. Same pattern as `AnrWatchdog`. |
| Why `BindingStalled` trigger separate from `ProviderTimeout`? | `ProviderTimeout` fires when a single provider's `firstEmissionTimeout` is exceeded. `BindingStalled` catches the downstream `combine()` starvation case: all providers may have emitted individually, but `combine()` blocks because one of N upstream flows failed initial emission. Different detection point, different root cause. |
| Why `DataStoreCorruption` in crash pool, not performance pool? | DataStore corruption is a data loss event — the user's layouts/settings are gone. It should have the same retention priority as crash data (20 files), not performance data (10 files) where thermal oscillation could evict it. |
| Why `ContentProvider` for agentic commands, not `BroadcastReceiver`? | `BroadcastReceiver.onReceive()` runs on the main thread. `CommandHandler.handle()` is suspend — needs a coroutine bridge (`goAsync()` + dedicated dispatcher + `NonCancellable` + `withTimeout`). `ContentProvider.call()` runs on a binder thread: `runBlocking(Dispatchers.Default)` is safe, no ANR timeout, no `PendingResult` lifecycle. Also subsumes the deadlock escape hatch — the primary path IS non-main-thread. `query()` provides lock-free reads for true deadlock diagnosis. |
| Why file-based response for all agentic commands, not inline Bundle data? | `Bundle.toString()` output (`Result: Bundle[{response={...}}]`) has nested JSON quoting ambiguity — JSON containing `}]` breaks naive parsing. Binder transaction limit is ~1MB shared across all concurrent transactions. Writing every response to a temp file and returning only the file path eliminates both problems. One extra `adb shell cat` call per command is negligible for a debug-only sequential protocol. |
| Why `runBlocking` in `AgenticContentProvider`, despite the "no runBlocking" rule? | `runBlocking` on the main thread causes deadlocks and ANR. On a binder thread, neither applies: the binder pool has ~15 threads, the agentic protocol is sequential (one command at a time), and `Dispatchers.Default` ensures suspend work runs on the coroutine pool. The only constraint: handlers must never use `Dispatchers.Main` (enforced via lint rule). |
| Why per-widget draw time, not just recomposition count? | Recomposition count ≠ frame cost. A widget might recompose frequently but cheaply (text update) while another recomposees rarely but expensively (complex canvas). Per-widget draw time enables performance bisection without trial-and-error widget removal. `System.nanoTime()` overhead is ~25ns — negligible in a 16ms frame budget. |
| Why command result envelope, not raw JSON? | Without standardized envelope, the agent must poll `dump-state` after every mutation command to verify success. `{"status": "ok\|error", "message": "...", "data": {...}}` gives synchronous confirmation. No error code taxonomy — just human/agent-readable message strings. |
| Why shared `ProviderFault` in testFixtures, not separate fault types? | `ChaosProviderInterceptor` (E2E via agentic commands) and `TestDataProvider` (unit via `DashboardTestHarness`) must inject identical faults. If fault mechanisms differ, a chaos-discovered bug may not reproduce in unit tests. Shared sealed interface + flow transformation ensures the same fault produces the same system response at both test layers. |
| Why defer mutation testing to post-launch? | Pitest on Android Compose modules produces noisy false positives (UI rendering mutations). JVM-only modules (`:sdk:common`, `:sdk:contracts`, `:codegen:plugin`, `:codegen:agentic`) are the high-signal targets — add after core development stabilizes. Not worth the CI time budget during greenfield iteration. |
| Why 3 debug overlays, not 7? | Frame Stats, Widget Health, and Thermal Trending serve the critical debugging needs. The deferred 4 (Recomposition Visualizer, Provider Flow DAG, State Machine Viewer, Trace Viewer) are human-developer tools — the autonomous agent uses agentic commands (`dump-metrics`, `diagnose-bindings`, `dump-connections`, `dump-traces`) for the same data. Build human-facing UI when a human developer needs it. |
| Why `WidgetExpectations` on-demand in `diagnose-widget`, not in `DiagnosticSnapshot`? | Expectations are widget-specific diagnostic data — they belong in the command that diagnoses a specific widget, not in every anomaly snapshot. `diagnose-widget` computes expectations on demand from `WidgetBindingCoordinator` state. Uses `KClass.qualifiedName` (not `simpleName`) for snapshot type identification to avoid ambiguity across packages. |
| Why interaction events via `DqxnLogger`, not a separate `CaptureSessionRegistry`? | A separate capture system requires the agent to predict problems (start capture before anomaly). `RingBufferSink` already retains all log entries. Structured `LogEntry` with `INTERACTION` tag and typed metadata gives the same data with zero new infrastructure. |
| Why `recommendedCommand` in `list-diagnostics` entries, not a `DiagnosticClassifier` rules engine? | The LLM agent IS the triage engine. A static classifier produces wrong hints on novel failures, which mislead more than help. Static trigger→command lookup (e.g., `WIDGET_CRASH` → `diagnose-crash`) is a routing hint, not a diagnosis — the agent decides what to investigate. |
