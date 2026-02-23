# CLAUDE.md

## Project

DQXN — modular Android dashboard platform. Real-time data widgets on a grid canvas. Use cases: automotive, desk/bedside, home automation, finance. Pack-based plugin architecture via Hilt multibinding.

Pre-launch greenfield. Source under `android/`. Namespace: `app.dqxn.android`. Full design: `.planning/ARCHITECTURE.md`. Requirements: `.planning/REQUIREMENTS.md`. Decision rationale: `.planning/DECISIONS.md`.

## Old Codebase Reference

A prior implementation exists and is mapped in `.planning/oldcodebase/` (8 docs, ~6000 lines). Use these when building new modules to understand prior art — what worked, what to avoid, and how old components map to the new architecture.

| Document | Covers |
|---|---|
| `core-libraries.md` | plugin-api, common, widget-primitives, agentic — full API surfaces → `sdk/*`, `core/agentic` |
| `feature-dashboard.md` | Grid, edit mode, drag/resize, state, data binding — god-object ViewModel to decompose → `feature/dashboard` |
| `packs.md` | 4 packs (free/demo/themes/sg-erp2), all widgets/providers/themes → `pack/*` |
| `ksp-processors.md` | 7 plugin handlers + 2 agentic processors with codegen templates → `codegen/*` |
| `app-module.md` | DI assembly, 17 nav routes, manifest, debug/release split → `app` |
| `build-system.md` | Convention plugins, version catalog, module graph, pack isolation violations → `android/build-logic` |
| `data-layer-docs.md` | Preferences DataStore (zero Proto), no corruption handlers → `data` (Proto schemas from scratch) |
| `feature-driving-diagnostics.md` | Both empty stubs; real diagnostics in dashboard → `feature/diagnostics` (driving deferred) |

**When to consult:** Building a module that has an old-codebase counterpart. Start with the mapping doc for orientation, then read actual source files in the old codebase at `../dqxn.old/android/` for implementation details, algorithm logic, and migration-ready code. The mapping docs are indexes — the real code is the source of truth for how things actually work. **Do not** copy old patterns that conflict with this CLAUDE.md — the new architecture intentionally diverges on state decomposition, Proto DataStore, pack isolation, and canvas model.

## Tech Stack

Kotlin 2.3+ (no Java). Jetpack Compose + Material 3. Hilt + KSP (no KAPT). Proto DataStore + Preferences DataStore. kotlinx-collections-immutable. kotlinx.serialization.

compileSdk 36, minSdk 31, targetSdk 36. AGP 9.0.1, Gradle 9.3.1, JDK 25. AGP 9 manages Kotlin directly — no `org.jetbrains.kotlin.android` plugin. Uses AGP 9's new DSL.

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
./gradlew :pack:essentials:compileDebugKotlin              # Compile check only
./gradlew :pack:essentials:testDebugUnitTest               # Compile + unit tests
```

## Module Map

```
sdk/      — contracts, common, ui, observability, analytics (pack API surface)
core/     — design, thermal, firebase, agentic (shell internals)
codegen/  — plugin, agentic (KSP, build-time only)
data/     — Proto + Preferences DataStore, .proto schemas
feature/  — dashboard, settings, diagnostics, onboarding
pack/     — essentials (+ essentials/snapshots), plus, themes, demo
app/      — single-activity entry, DI assembly
```

## Module Dependency Rules

**The single most important rule**: Packs depend on `:sdk:*` and `:pack:*:snapshots` only, never on `:feature:*` or `:core:*`. Shell imports nothing from packs at compile time. The `dqxn.pack` convention plugin auto-wires sdk deps.

**`:pack:{packId}`** — CAN: `:sdk:*`, `:pack:*:snapshots`. CANNOT: `:feature:*`, `:core:*`, `:data`, other packs. Need something from dashboard? → belongs in `:sdk:contracts`.

**`:pack:{packId}:snapshots`** — CAN: `:sdk:contracts` only. Pure Kotlin, no Compose compiler (no `dqxn.android.compose`). `@Immutable` available transitively via `:sdk:contracts` → `compose.runtime`. Only `@DashboardSnapshot` data classes. Uses `dqxn.snapshot` plugin.

**`:feature:dashboard`** — CAN: `:sdk:*`, `:core:design`, `:core:thermal`, `:data`. CANNOT: any `:pack:*`.

**`:core:firebase`** — CAN: `:sdk:observability`, `:sdk:analytics`, `:sdk:common`. Only module with Firebase SDKs. Only `:app` imports it.

**`:sdk:contracts`** — CAN: `:sdk:common` only. Pure Kotlin + coroutines + `compileOnly(compose.runtime)` for `@Composable` and `@Immutable` annotations. No Compose compiler — annotations only, no composable function bodies.

### Compose Compiler Scope

- `dqxn.android.compose` (explicit) — UI modules: `:app`, `:feature:*`, `:sdk:ui`, `:core:design`
- `dqxn.pack` (applies `dqxn.android.compose` internally) — all `:pack:{packId}` modules (packs contain `@Composable Render()`)
- `dqxn.snapshot` — `:pack:*:snapshots` (pure Kotlin, no Compose compiler — `@Immutable` available transitively via `compose.runtime`)
- `dqxn.kotlin.jvm` — `:codegen:*` (pure JVM, no Android, no Compose)
- WITHOUT Compose compiler: `:sdk:contracts` (`compileOnly(compose.runtime)` for annotations only), `:sdk:common`, `:sdk:observability`, `:sdk:analytics`, `:core:thermal`, `:core:firebase`, `:core:agentic`, `:data`

## Critical Constraints

### State decomposition
- Each coordinator owns its own `StateFlow` slice. No god-object state class.
- Per-widget data via individual flows — `widgetData(widgetId)`. Clock tick must NOT recompose speedometer.
- Discrete commands: sealed `DashboardCommand` → `Channel`. Continuous gestures: `MutableStateFlow<DragUpdate>` (latest-value-wins).

### Canvas model
- **Unbounded canvas** per profile, viewport is a rendering window. Configuration boundaries (fold x orientation) shown in edit mode. **No-straddle snap**: widgets fully visible or fully invisible per configuration.
- **No automatic relocation**: viewport shrinks → off-viewport widgets not rendered. Edit mode for rearrangement.
- Free-sizing windows don't trigger config change.

### Dashboard profiles
- Per-profile independent `DashboardCanvas`. New profile clones current. Profile switching: horizontal swipe + bottom bar icons.
- Bottom bar: settings + profile icons (2+) + add-widget (edit mode). Auto-hides, floats over canvas.
- Pack-extensible via `ProfileDescriptor` + `ProfileTrigger`.

### Compose performance (60fps, 12+ widgets)
- `LocalWidgetData` CompositionLocal + `derivedStateOf` to defer reads to draw phase
- `graphicsLayer` on every widget (isolated RenderNode)
- `@Immutable`/`@Stable` on all UI types. `ImmutableList`/`ImmutableMap` everywhere.
- Draw objects (`Path`, `Paint`, `Brush`) via `remember`/`drawWithCache` — never per frame
- Glow: `RenderEffect.createBlurEffect()` — NOT `BlurMaskFilter`
- Typed `@DashboardSnapshot` subtypes, `KClass`-keyed multi-slot `WidgetData`. Target <4KB alloc/frame.
- Grid: `Layout` + custom `MeasurePolicy` — NOT `LazyLayout`
- Drag: `graphicsLayer` offset animation — NOT `movableContentOf`
- Layer 0: `collectAsState()`. Layer 1: `collectAsStateWithLifecycle()`.
- Frame pacing: `Window.setFrameRate()` API 34+, emission throttling API 31-33.

### Widget binding isolation
- `SupervisorJob` parent for all binding jobs. One crash must NOT cancel siblings.
- `WidgetCoroutineScope` via CompositionLocal for effects. `CoroutineExceptionHandler` → `widgetStatus`, never propagate.

### DataStore
- All `@Singleton`. `ReplaceFileCorruptionHandler` required on ALL instances.
- Provider settings: `{packId}:{providerId}:{key}`. Layout saves debounced 500ms on `Dispatchers.IO`.

## Creating New Components

### New Widget

Path: `android/pack/{packId}/src/main/kotlin/app/dqxn/android/pack/{packId}/widgets/{widgetname}/{Name}Renderer.kt` + test.
Package: `app.dqxn.android.pack.{packId}.widgets.{widgetname}` (flat, no hyphens). TypeId: `{packId}:{widget-name}` (hyphens OK).

Key patterns: `@DashboardWidget` annotation with typeId/displayName/packId. Implements `WidgetRenderer`. `Render()` reads `LocalWidgetData.current`, uses `derivedStateOf` for high-frequency data. Coroutines via `LocalWidgetScope.current`. Test extends `WidgetRendererContractTest`.

### New Data Provider

Path: `android/pack/{packId}/src/main/kotlin/app/dqxn/android/pack/{packId}/providers/{Name}Provider.kt` + test.
Sensor/BLE flows MUST use `callbackFlow` + `awaitClose`. Accumulation providers aggregate internally on `Dispatchers.Default`.

### New Snapshot Type (cross-boundary)

Create `:pack:{packId}:snapshots` sub-module with `id("dqxn.snapshot")`. `@DashboardSnapshot` + `@Immutable` data class implementing `DataSnapshot`. Only extract to sub-module when a second consumer appears.

### New Pack Module

1. `android/pack/{packId}/build.gradle.kts` — `id("dqxn.pack")`
2. `include(":pack:{packId}")` in settings.gradle.kts
3. `implementation(project(":pack:{packId}"))` in `:app` build.gradle.kts
4. **Never** add to `:feature:*` or `:core:*` deps

## Architecture Patterns

- **Dashboard-as-shell**: Layer 0 always present. Overlays on Layer 1 via `OverlayNavHost`.
- **IoC data binding**: `WidgetDataBinder` assigns providers by data type, fallback on failure.
- **Widget error isolation**: Catch boundary + `WidgetCoroutineScope`. Failed → fallback UI, never crash. >3 crashes in 60s → safe mode.
- **Notifications**: `NotificationCoordinator` (banners+toasts, `@ViewModelScoped`), `AlertSoundManager` (audio/haptic, `@Singleton`), `SystemNotificationBridge` (FGS). Priority levels: CRITICAL/HIGH/NORMAL/LOW.
- **Thermal**: `ThermalManager` → `RenderConfig`. Glow off at DEGRADED, frame rate reduced.
- **Edge-to-edge**: Dashboard behind system bars. Overlays respect `WindowInsets.systemBars`.

## Observability

`DqxnLogger` (zero-alloc when disabled, no Timber). `DiagnosticSnapshotCapture` on anomalies. `CrashEvidenceWriter` (sync SharedPrefs in UncaughtExceptionHandler). `AnrWatchdog`. `MetricsCollector` (per-widget draw time, frame histograms). Debug: agentic `ContentProvider` + `dump-semantics`/`query-semantics` for Compose semantics tree inspection.

## Testing

JUnit5 + MockK + Truth (unit). JUnit4 + `HiltAndroidRule` (Hilt integration). Turbine + `StandardTestDispatcher` (flows — never `UnconfinedTestDispatcher`). Contract tests in `:sdk:contracts` testFixtures. `DashboardTestHarness` DSL for coordinators. Semantics-based UI verification (no screenshot tests). Test tags on all key elements: `widget_{id}`, `dashboard_grid`, `bottom_bar`, `banner_{id}`.

## Code Style

- Explicit return types on public functions. `internal` by default in packs.
- No `var` in data classes. Prefer `sealed interface` over `sealed class`.
- No `GlobalScope`, no `runBlocking` (except tests/debug agentic). Scopes via Hilt or `viewModelScope`.
- All user-facing strings in Android string resources.
- `ImmutableList`/`ImmutableMap` in all UI state.

## Naming

```
Widgets:    {Name}Renderer.kt       Providers:  {Name}Provider.kt
Tests:      {Name}Test.kt           Hilt:       {Pack}Module.kt
Snapshots:  {Name}Snapshot.kt       Proto:      {entity}.proto
Plugins:    dqxn.{purpose}.gradle.kts / dqxn.pack.gradle.kts / dqxn.snapshot.gradle.kts
```

## Common Build Errors

| Error | Fix |
|---|---|
| `Composable` is internal | Add `id("dqxn.android.compose")` to module |
| DataStore already created | Make provider `@Singleton` |
| MissingBinding Set\<WidgetRenderer\> | Install `@Module` in `SingletonComponent` + add pack to `:app` deps |
| typeId format KSP error | `{packId}:{widget-name}` — lowercase, colon, hyphens for multi-word |
| ImmutableList args mismatch | Use `.toImmutableList()` |
| Config cache serialization | KAPT leaked in — only `ksp()` allowed |
| Unstable Compose params | `@Immutable`/`@Stable` + kotlinx immutable collections |
| KSP OOM | Verify single-pass codegen, `ksp.incremental=true` |
