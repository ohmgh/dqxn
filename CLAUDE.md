# CLAUDE.md

## Project

DQXN — modular Android dashboard platform. Displays real-time data through configurable widgets on a grid canvas. Use cases include automotive (phone/tablet mounted in a vehicle), desk/bedside displays, home automation panels, and finance dashboards. Pack-based plugin architecture: packs register widgets, providers, and themes via contracts; the shell discovers them at runtime via Hilt multibinding.

Pre-launch greenfield. Source under `android/`. Package namespace: `app.dqxn.android`. Read `docs/ARCHITECTURE.md` for full technical design, `docs/REQUIREMENTS.md` for product requirements.

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
core/     — design, thermal, firebase, agentic (shell internals)
codegen/  — plugin, agentic (KSP, build-time only)
data/     — Proto + Preferences DataStore, .proto schemas
feature/  — dashboard, settings, diagnostics, onboarding
pack/     — free (+ free/snapshots), plus, themes, demo
app/      — single-activity entry, DI assembly
```

## Module Dependency Rules

**The single most important rule**: Packs depend on `:sdk:*` and snapshot sub-modules (`:pack:*:snapshots`) only, never on `:feature:dashboard` or `:core:*`. The shell imports nothing from packs at compile time. If you're adding a dashboard or core import in a pack, the design is wrong. The `dqxn.pack` convention plugin auto-wires all allowed sdk dependencies — packs should not manually add `:sdk:*` project dependencies. Snapshot sub-module dependencies are declared explicitly per pack.

Full dependency matrix in `docs/ARCHITECTURE.md` Section 3. Quick reference below.

### Module Isolation Guide

**When working in `:pack:{packId}`:**
- CAN import from: `:sdk:contracts`, `:sdk:common`, `:sdk:ui`, `:sdk:observability`, `:sdk:analytics`
- CAN import from: `:pack:*:snapshots` (cross-boundary snapshot types)
- CANNOT import from: `:feature:*`, `:core:*`, `:data`, other packs (non-snapshot modules)
- If you need something from dashboard → it belongs in `:sdk:contracts` as a contract
- SDK dependencies are auto-wired by the `dqxn.pack` convention plugin; snapshot sub-module dependencies are declared explicitly

**When working in `:pack:{packId}:snapshots`:**
- CAN import from: `:sdk:contracts` only
- Pure Kotlin — no Android framework, no Compose, no business logic
- Contains ONLY `@DashboardSnapshot`-annotated data classes
- Uses `dqxn.snapshot` convention plugin

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

### Compose Compiler Scope

Convention plugins control which modules get the Compose compiler:
- `dqxn.android.compose` — modules WITH UI: `:app`, `:feature:*`, `:sdk:ui`, `:core:design`
- `dqxn.snapshot` — snapshot sub-modules: `:pack:*:snapshots` (pure Kotlin, no Compose)
- Modules WITHOUT Compose: `:sdk:contracts`, `:sdk:common`, `:sdk:observability`, `:sdk:analytics`, `:core:thermal`, `:core:firebase`, `:core:agentic`, `:codegen:*`, `:data`, `*:snapshots`

## Critical Constraints

These are non-negotiable. Violations cause real performance/correctness issues.

### State decomposition
- Each coordinator owns its own `StateFlow` slice. No god-object state class.
- Per-widget data is individual flows via `widgetData(widgetId)`. A clock tick must NOT recompose the speedometer. Never put all widget data in a shared `Map` inside a single state object.
- Discrete commands flow through sealed `DashboardCommand` → routed to coordinator via `Channel`. Continuous gestures (drag, resize) use `MutableStateFlow<DragUpdate>` — latest-value-wins, never queued behind slow commands.

### Canvas model
- **Unbounded canvas**: one canvas for all display configurations and profiles. Widgets can exist at any grid coordinate. The viewport is a rendering window — not the canvas boundary.
- **Configuration boundaries**: each device display configuration (fold states × orientation) defines a viewport rectangle. Boundary lines shown in edit mode with labels. **No-straddle snap**: widgets cannot cross configuration boundaries — every widget is fully visible or fully invisible in any configuration. Hard constraint enforced via snap on drag. Distinct haptic on boundary snap.
- **Configuration-aware defaults**: onboarding places core widgets in the intersection region visible across all configurations. Secondary widgets fill outward into larger-viewport zones.
- **Free-sizing windows** (OEM split-screen) do NOT trigger configuration change — positions unchanged, only culling re-evaluates.
- **No automatic relocation**: when viewport shrinks (fold, device switch), off-viewport widgets are simply not rendered. No reflow, no proportional anchoring, no axis swap. Edit mode is the discovery/rearrangement mechanism.

### Dashboard profiles
- **Per-profile dashboards**: each profile owns an independent `DashboardCanvas` — its own widget set, positions, sizes. Not a visibility filter on a shared canvas.
- **New profile clones current**: creating a profile copies the active dashboard. User edits from there, not from scratch.
- **Adding widgets**: defaults to current profile. "Add to all profiles" option in widget picker for shared widgets.
- **Profile switching**: horizontal swipe on canvas (Android home screen page model) + tap profile icon in bottom bar. Disabled in edit mode. Edits apply to current profile only.
- **Pack-extensible**: packs register `ProfileDescriptor` with optional `ProfileTrigger` (auto-switch). Shell manages activation, priority, storage.
- **Bottom bar**: auto-hides, floats over canvas. Contains: Settings (always), profile icons (2+ profiles, active highlighted), Add Widget (edit mode). Tap to reveal, 3s auto-hide.
- **Launcher path**: profiles = home screen pages. Direct mapping for future launcher pack.

### Compose performance (60fps with 12+ widgets)
- **State read deferral**: Widget data provided via `LocalWidgetData` CompositionLocal — widgets access data via `LocalWidgetData.current` and use `derivedStateOf` to defer high-frequency reads to draw phase (`drawWithCache`/`onDrawBehind`)
- **`graphicsLayer` on every widget**: Isolated RenderNode per widget
- **`@Immutable`/`@Stable` on all UI types**: Use `ImmutableList`, `ImmutableMap` from kotlinx-collections-immutable
- **`derivedStateOf`**: Use for all computed values from state (filtered lists, theme display, aggregations). Prevents unnecessary recomposition when inputs change but output doesn't.
- **Draw object caching**: `Path`, `Paint`, `Brush` via `remember` or `drawWithCache` — never allocate per frame
- **Glow**: `RenderEffect.createBlurEffect()` (GPU shader) — NOT `BlurMaskFilter` with offscreen buffers
- **Typed DataSnapshot**: `@DashboardSnapshot`-annotated subtypes per data type, 1:1 with provider boundaries (no `Map<String, Any>` boxing). Non-sealed `DataSnapshot` interface in `:sdk:contracts`; concrete subtypes live in snapshot sub-modules (`:pack:*:snapshots`) for cross-boundary access or pack-local for single-consumer types, validated by KSP. `WidgetData` uses `KClass`-keyed multi-slot delivery — `data.snapshot<SpeedSnapshot>()`. Target <4KB app-level allocation/frame (excluding Compose overhead). Total budget <64KB/frame.
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
1. `android/pack/free/src/main/kotlin/app/dqxn/android/pack/free/widgets/batterytemp/BatteryTempRenderer.kt`
2. `android/pack/free/src/test/kotlin/app/dqxn/android/pack/free/widgets/batterytemp/BatteryTempRendererTest.kt`

Package: `app.dqxn.android.pack.{packId}.widgets.{widgetname}` (flat, no hyphens in package)
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

Files: `android/pack/{packId}/src/main/kotlin/app/dqxn/android/pack/{packId}/providers/{Name}Provider.kt` + test.

Provider flows MUST use `callbackFlow` with `awaitClose` for sensor/BLE listeners. Accumulation providers (Trip) handle high-frequency accumulation internally on `Dispatchers.Default`, emit aggregated snapshots at reduced rate.

### New Snapshot Type (cross-boundary)

When a snapshot type needs to be consumed by modules other than its producer, place it in a snapshot sub-module:

1. Create `android/pack/{packId}/snapshots/build.gradle.kts` — apply `id("dqxn.snapshot")`
2. Add `include(":pack:{packId}:snapshots")` to `android/settings.gradle.kts`
3. Add snapshot data class with `@DashboardSnapshot` + `@Immutable` annotations
4. Consumer packs add `implementation(project(":pack:{packId}:snapshots"))` to their `build.gradle.kts`

Package: `app.dqxn.android.pack.{packId}.snapshots`

```kotlin
// android/pack/free/snapshots/build.gradle.kts
plugins {
    id("dqxn.snapshot")
}

// android/pack/free/snapshots/src/main/kotlin/.../SpeedSnapshot.kt
@DashboardSnapshot(dataType = "speed")
@Immutable
data class SpeedSnapshot(
    val speed: Float,
    override val timestamp: Long,
) : DataSnapshot
```

**When NOT to create a sub-module**: If the snapshot type is only consumed within its producing pack, keep it in the pack module directly. Extract to a sub-module only when a second consumer appears.

### New Pack Module

1. Create `android/pack/{packId}/build.gradle.kts` — apply `id("dqxn.pack")` (auto-wires all sdk dependencies)
2. Add `include(":pack:{packId}")` to `android/settings.gradle.kts`
3. Add `implementation(project(":pack:{packId}"))` to `android/app/build.gradle.kts`
4. **Never** add it to `:feature:dashboard` or `:core:*` dependencies
5. If the pack has cross-boundary snapshot types, create a `:pack:{packId}:snapshots` sub-module (see above)

## Plugin Conventions

- `@DashboardWidget` / `@DashboardDataProvider` annotations → KSP generates `PackManifest` → Hilt multibinding
- `typeId` format: `{packId}:{widgetName}` — e.g., `core:speedometer`, `plus:trip`, `core:speed-limit-circle` (hyphens for multi-word)
- Settings UI is schema-driven via `SettingDefinition<T>`. No custom composables per widget for settings. **No sliders** (conflict with HorizontalPager gestures).
- Entitlement gating: `Gated` interface, OR logic (`requiredAnyEntitlement`). Themes: preview always, gate at persistence. Revocation is reactive — auto-revert to free defaults.
- Data types are string identifiers, not closed enums. Packs register `DataTypeDescriptor` alongside providers.

## Architecture Patterns

- **Unbounded canvas with profiles**: Each profile owns an independent unbounded canvas. Viewport is a rendering window. Configuration boundaries (fold × orientation) prevent widget straddling. Horizontal swipe switches between profile canvases (page model). Bottom bar: settings + profile icons + add-widget (edit mode), auto-hides.
- **Dashboard-as-shell**: Dashboard is Layer 0, always present — NOT a navigation destination. Overlays navigate on Layer 1 via `OverlayNavHost`.
- **IoC data binding**: Widgets never choose their data source. `WidgetDataBinder` assigns providers by data type, with fallback to next available on failure.
- **Widget error isolation**: Each widget in a catch boundary (effects via `WidgetCoroutineScope`). Compose has NO composition-phase try/catch — mitigated by contract tests, crash count tracking, and safe mode fallback. Failed widget → fallback UI, never app crash.
- **ConnectionStateMachine**: FSM with validated transitions. No ad-hoc `MutableStateFlow<ConnectionState>`.
- **Thermal adaptation**: `ThermalManager` → `RenderConfig`. Glow disabled at DEGRADED, frame rate reduced via `Window.setFrameRate()` (API 34+) or data emission throttling (API 31-33).
- **Edge-to-edge**: `enableEdgeToEdge()` in onCreate. Dashboard draws behind system bars. Overlays respect `WindowInsets.systemBars`. Status bar toggle via `WindowInsetsControllerCompat`.
- **Crash recovery**: >3 crashes in 60s → safe mode (clock widget only, reset banner).
- **Notification separation**: Three independent surfaces — `NotificationCoordinator` (banners + toasts, `@ViewModelScoped`), `AlertSoundManager` (audio/haptic, `@Singleton` via `AlertEmitter` contract), `SystemNotificationBridge` (FGS + connection channels). Widget status overlays (`WidgetStatusCache`) are continuous per-widget state and intentionally excluded from notification coordination. Toasts route through `NotificationCoordinator`, NOT `DashboardEffect`. Priority levels (CRITICAL/HIGH/NORMAL/LOW) govern persistence, auto-dismiss timing, and ordering — no driving-mode conditional behavior at V1.

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
- **Notifications**: Banner derivation from singleton state combinations (Turbine), priority-based ordering and persistence, toast ordering under concurrent emission, safe mode banner lifecycle (CRITICAL persistence + action routing), `AlertSoundManager` audio focus interaction (MockK)
- Shared test infra via `testFixtures` source sets. Factories: `testWidget()`, `testTheme()`, `testDataSnapshot()`.
- **Agentic debug loop**: detect → investigate → reproduce → fix+verify → guard. `HarnessStateOnFailure` outputs JSON matching `diagnose-*` shapes.

## Package & File Naming

Package pattern: `app.dqxn.android.{group}.{module}[.subpackage]`. Pack widgets: `app.dqxn.android.pack.{packId}.widgets.{name}`. Full listing in `docs/ARCHITECTURE.md`.

```
Widgets:           {PascalCaseName}Renderer.kt     (SpeedometerRenderer.kt)
Providers:         {PascalCaseName}Provider.kt      (GpsSpeedProvider.kt)
Tests:             {ClassName}Test.kt               (SpeedometerRendererTest.kt)
Hilt modules:      {PackName}Module.kt              (FreePackModule.kt)
Theme files:       {pack_id}_themes.json            (themes_pack_themes.json)
Proto schemas:     {entity_name}.proto              (dashboard_canvas.proto)
Convention plugins: dqxn.android.{purpose}.gradle.kts, dqxn.pack.gradle.kts, dqxn.snapshot.gradle.kts
Snapshot types:    {PascalCaseName}Snapshot.kt       (SpeedSnapshot.kt) — in *:snapshots sub-module if cross-boundary
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
| Why generic `DataProvider<T>`? | Untyped `provideState(): Flow<DataSnapshot>` lets a provider declare `snapshotType = SpeedSnapshot::class` but emit `BatterySnapshot` — silent `null` from `as? T`. Generic bound makes the compiler enforce consistency. |
| Why `merge() + scan()`, not `combine()`? | `combine()` requires all upstreams to emit before producing any value. A stuck provider blocks all slots — contradicts multi-slot independent availability. `merge() + scan()` surfaces partial data immediately. |
| Why typed DataSnapshot, not Map? | `Map<String, Any?>` boxes primitives. 60 emissions/sec × 12 widgets = 720 garbage objects/sec. |
| Why non-sealed DataSnapshot? | Sealed forces all subtypes into `:sdk:contracts` — packs can't define snapshot types without modifying SDK. KSP `@DashboardSnapshot` gives compile-time validation (no duplicate dataType, `@Immutable` required) without same-module restriction. `KClass`-keyed `WidgetData.snapshot<T>()` doesn't need sealed. |
| Why snapshot sub-modules, not promote to `:sdk:contracts`? | Promotion divorces types from producers, grows `:sdk:contracts` into a domain dumping ground, and recompiles all modules on every change. Sub-modules (`:pack:*:snapshots`) preserve producer ownership, limit blast radius, and keep `:sdk:contracts` as pure mechanism. |
| Why `KClass` keys in `WidgetData`? | String keys allow typos, no compiler enforcement. `snapshot<SpeedSnapshot>()` can't reference a nonexistent type. |
| Why multi-slot `WidgetData`? | Speedometer consumes 3 independent providers. Single-slot loses independent availability and graceful degradation. |
| Why plain `Layout`, not `LazyLayout`? | Absolute-position grid. `LazyLayout` adds `SubcomposeLayout` overhead without benefit. |
| Why `callbackFlow` for sensors? | Ensures cleanup via `awaitClose`. Direct `SensorEventListener` leaks registrations. |
| Why `:core:firebase`, not in `:sdk:observability`? | Would make Firebase a transitive dependency of every module. |
| Why `ContentProvider`, not `BroadcastReceiver` for agentic? | BR runs on main thread. CP runs on binder thread — `runBlocking` is safe, no ANR risk. |
| Why not unify widget status + notifications? | Widget status is continuous state (`StateFlow`), notifications are discrete events. Folding both into one system creates a god-object that owns per-widget status AND app-level alerts — violates decomposed-state principle. |
| Why toasts through `NotificationCoordinator`, not `DashboardEffect`? | `DashboardEffect` is a raw `Channel` — no priority ordering, no rate limiting. Toasts need both. |
| Why `AlertSoundManager` separate from `NotificationCoordinator`? | Scope mismatch (`@Singleton` vs `@ViewModelScoped`), independent triggers (speed limit alert is audio-only, no banner), and audio focus handling requires application-lifetime resources. |
| Why no pack `NotificationEmitter` at V1? | Every V1 pack notification is already modeled as widget state (`WidgetStatusCache`) or shell-originated. Adding `NotificationEmitter` to `:sdk:contracts` is premature API commitment with no validated consumer. |
| Why no notification rules engine? | Only ~5 rules at launch. Coordinator observes `@Singleton` state flows directly — not a standalone engine class injecting every subsystem. Domain knowledge stays in subsystem state representation (`bleAdapterOff`, `safeModeActive`); coordinator just maps state to banners. |
| Why `AlertResult` return type? | Fire-and-forget `fire()` gives callers no feedback on audio focus denial, hardware unavailability, or user silence override. V2 pack alerts will need this to fall back to visual indicators. Changing a contract interface post-V1 is painful — return type costs nothing now. |
| Why condition-keyed banner IDs? | Generated UUIDs cause dismiss+recreate flicker on state oscillation (BLE connection flapping). Condition keys (`"ble_adapter_off"`) enable stable animation, in-place updates, and targeted dismissal. |
| Why split CRITICAL banner to Layer 1.5? | Compose `Box` draws later children on top. `NotificationBannerHost` at Layer 0.5 is occluded by `OverlayNavHost` at Layer 1. CRITICAL banners (safe mode) must be visible above overlays — a separate `CriticalBannerHost` after `OverlayNavHost` achieves this without `zIndex` hacks. |
| Why `Channel.BUFFERED` for toasts? | Default rendezvous channel (capacity 0) suspends the producer when the consumer isn't collecting. Multiple simultaneous toasts (entitlement revocation + theme preview end) would block the emitting coroutine. Buffered capacity prevents silent producer suspension. |
| Why defer driving mode? | DQXN is a general-purpose dashboard, not vehicle-first. Driving mode is a pack-provided feature, not a shell concern. Post-launch: packs supply driving detection providers (GPS speed, OBD-II), users choose per-widget and system-level via standard data binding and dashboard settings. |
| Why unbounded canvas, not viewport-bounded? | Viewport-bounded means widgets placed on a tablet silently disappear on a phone with no recovery path. Unbounded canvas preserves all widget positions — smaller viewports simply render a subset. Configuration boundaries + no-straddle snap make the subset boundaries explicit and clean. |
| Why no-straddle snap? | Partially visible widgets are visual corruption — clipped gauges, truncated text. Worse than fully hidden. The snap constraint ensures every widget is either fully rendered or not rendered at all. Binary visibility, never partial. |
| Why no automatic relocation on viewport change? | Proportional anchoring degrades ungracefully (widget pile-up on small screens, aspect ratio distortion). Axis swapping only works for rotation, destructive on round-trip, fragments widget clusters. Accepting hidden widgets + providing edit mode for manual rearrangement is simpler and more predictable. |
| Why per-profile dashboards, not per-widget visibility? | Per-widget visibility is a filter on one canvas — creates sparse layouts, requires duplicate widgets for different sizes per context, and the cross-fade animation feels like nothing happened. Per-profile dashboards are independently designed, make horizontal swipe correct (actual pages), and map directly to home screen pages for launcher integration. Configuration burden mitigated by "new profile clones current" default. |
| Why horizontal swipe for profiles? | Muscle memory — every Android user knows home screen page swiping. Per-profile dashboards are actual pages with independent canvases, so the page transition metaphor is correct (unlike a visibility filter where the canvas doesn't move). Directly positions DQXN for future home launcher integration. |
| Why bottom bar with profile icons, not page dots? | Icons give one-tap direct access to any profile — no intermediary UI. Page dots require sequential swiping. Icon row also works as a visual profile indicator without needing the swipe gesture. |
| Why bottom bar, not floating action buttons? | Bottom bar composes cleanly: settings + profile icons + add-widget in one auto-hiding strip. FABs scatter across the screen and conflict with widget tap targets. |
| Why profiles from packs? | The shell can't know all contexts (driving, home, bedtime). Packs define domain-specific `ProfileDescriptor`s with `ProfileTrigger`s. Same discovery pattern as widgets/providers — Hilt multibinding, KSP validation, runtime registration. |

Full rationale in `docs/ARCHITECTURE.md`.
