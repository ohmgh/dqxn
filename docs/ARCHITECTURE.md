# DQXN Architecture

> Target technical architecture for the DQXN Android dashboard platform.

## 1. Overview

DQXN is a single-activity Android dashboard platform that renders real-time telemetry on a fully configurable widget grid. A phone or tablet mounted in a vehicle displays speed, time, compass, ambient light, solar position, and data from feature packs — all through a modular, pack-based architecture.

Packs (widgets, themes, data providers) are fully decoupled from the dashboard shell. Packs know nothing about the shell; the shell discovers packs at runtime via Hilt multibinding. This enables regional feature sets, premium gating, and first-party modular extensibility without touching the core. All packs are compiled modules — there is no runtime plugin loading.

**Identity**: "Life is a dash. Make it beautiful." — The Dashing Dachshund

## 2. Tech Stack

| Category | Choice |
|---|---|
| Language | Kotlin 2.3+ (no Java) |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + MVI-style sealed events, single-Activity |
| DI | Hilt + KSP |
| Navigation | Navigation Compose (type-safe, kotlinx.serialization) |
| Async | Coroutines, StateFlow/SharedFlow |
| Persistence | Proto DataStore (structured data), Preferences DataStore (simple settings) |
| Serialization | kotlinx.serialization-json, Protocol Buffers (persistence) |
| Code Gen | KSP + KotlinPoet |
| Collections | kotlinx-collections-immutable (for Compose stability) |
| Android SDK | compileSdk 36, minSdk 31, targetSdk 36 |
| Build | AGP 9.0.1, Gradle 9.3.1, JDK 25 |
| Perf | Baseline Profiles, Macrobenchmarks, Compose compiler metrics |
| Firebase | Crashlytics, Analytics, Performance Monitoring — all behind interfaces in `:sdk:observability` / `:sdk:analytics`, implementations in `:core:firebase` |
| Testing | JUnit5 (unit/flow/property), JUnit4 (Hilt integration), MockK, Truth, Turbine, Roborazzi, jqwik, kotlinx.fuzz |
| Debug | LeakCanary, StrictMode (debug builds) |

## 3. Module Structure

```
android/
├── build-logic/convention/       # Gradle convention plugins (composite build)
├── sdk/                          # Pack-accessible API surface
│   ├── contracts/                # Plugin contracts (WidgetRenderer, DataProvider, DataSnapshot subtypes, annotations)
│   ├── common/                   # AppResult, AppError, coroutine dispatchers, stability config
│   ├── ui/                       # WidgetContainer, WidgetStyle, LocalWidgetData, DashboardThemeDefinition
│   ├── observability/            # Structured logging, tracing, metrics, health monitoring, ANR watchdog
│   └── analytics/                # AnalyticsTracker, PackAnalytics, sealed AnalyticsEvent hierarchy
├── core/                         # Shell internals (packs never depend on these)
│   ├── design-system/            # Theme tokens, typography, spacing, shared overlay composables
│   ├── thermal/                  # ThermalManager, RenderConfig, adaptive frame rate
│   ├── driving/                  # DrivingStateDetector — platform safety gate + DataProvider (DrivingSnapshot)
│   ├── firebase/                 # Firebase implementations (Crashlytics, Analytics, Perf) — sole Firebase dependency point
│   └── agentic/                  # ADB broadcast debug automation (debugImplementation only)
├── codegen/                      # KSP processors (build-time only)
│   ├── plugin/                   # KSP: @DashboardWidget → pack manifests, settings, themes, entitlements, validation
│   └── agentic/                  # KSP: command registry + route listing generation (debugKsp only)
├── data/
│   └── persistence/              # Proto DataStore (layouts, devices), Preferences DataStore (settings), .proto schemas
├── feature/
│   ├── dashboard/                # Dashboard shell — coordinators, grid, theme engine, presets
│   ├── settings/                 # Settings sheet — appearance, behavior, data & privacy, danger zone
│   ├── diagnostics/              # Provider Health dashboard, connection log, retry actions
│   └── onboarding/               # Progressive tips, first-launch theme selection, permission flows
├── packs/                        # Pack extensions (own convention plugin, own dependency rules)
│   ├── free/                     # "Essentials" — core widgets, providers, themes
│   ├── plus/                     # "Plus" — trip computer, media, G-force, altimeter, weather
│   ├── themes/                   # Premium themes (JSON-driven)
│   └── demo/                     # Hardware simulation for debug/demo
├── lint-rules/                   # Custom lint checks (module boundaries, KAPT detection, etc.)
├── baselineprofile/              # Baseline Profile generation
├── benchmark/                    # Macrobenchmark tests
└── app/                          # Single-activity entry, DI assembly, nav host
    ├── src/debug/                # Debug overlays, LeakCanary, StrictMode
    └── src/release/
```

Regional packs (e.g., Singapore ERP integration) plug in as additional `:packs:*` modules without any changes to the shell or core.

Convention plugins enforce shared defaults across all modules: compileSdk 36, minSdk 31, JVM target matching AGP/Gradle requirements. **Compose compiler is only applied to modules with UI** (not `:sdk:contracts`, not `:sdk:common`, not `:sdk:observability`, not `:sdk:analytics`, not `:core:*` except `:core:design-system`, not `:codegen:*`, not `:data:*`). The `dqxn.pack` convention plugin auto-wires all `:sdk:*` dependencies for pack modules — packs should not manually declare them.

### Module Dependency Rules

Packs depend on `:sdk:*` only, never on `:feature:dashboard` or `:core:*`. The shell imports nothing from packs at compile time. Discovery is pure runtime via Hilt `Set<T>` multibinding.

```
:app
  → :feature:* (dashboard, settings, diagnostics, onboarding)
  → :packs:* (widget/provider/theme implementations)
  → :sdk:* (contracts, common, ui, observability, analytics)
  → :core:* (design-system, thermal, driving, firebase)
  → :core:agentic (debugImplementation only)
  → :data:persistence (DataStore)

:packs:*
  → :sdk:* only (enforced by dqxn.pack convention plugin + validation task)

:feature:dashboard
  → :sdk:*
  → :core:design-system
  → :core:thermal
  → :data:persistence

:feature:settings
  → :sdk:*
  → :core:design-system
  → :data:persistence

:feature:diagnostics
  → :sdk:contracts, :sdk:common, :sdk:observability, :sdk:analytics

:feature:onboarding
  → :sdk:*
  → :data:persistence

:core:driving
  → :sdk:contracts, :sdk:common, :sdk:observability

:core:firebase
  → :sdk:observability (CrashReporter, CrashMetadataWriter, ErrorReporter interfaces)
  → :sdk:analytics (AnalyticsTracker interface)
  → :sdk:common

:core:design-system
  → :sdk:common
  → :sdk:ui

:core:thermal
  → :sdk:common
  → :sdk:observability

:core:agentic
  → :sdk:common, :sdk:contracts

:codegen:plugin
  → :sdk:contracts (reads annotations, no runtime dependency)

:codegen:agentic
  → :core:agentic (reads annotations, debugKsp only)

Every module → :sdk:observability
```

No module other than `:core:firebase` and `:app` depends on Firebase SDKs. This strict boundary means adding or removing a pack never requires changes to the shell.

## 4. Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│  PRESENTATION                                           │
│  DashboardScreen / DashboardGrid / OverlayNavHost       │
│  Jetpack Compose — stateless renderers                  │
│  Layer 0: collectAsState() — Layer 1: collectAsState-   │
│  WithLifecycle()                                        │
├─────────────────────────────────────────────────────────┤
│  COORDINATION                                           │
│  Focused coordinators (NOT a monolithic ViewModel)      │
│  LayoutCoordinator, ThemeCoordinator,                   │
│  EditModeCoordinator, WidgetBindingCoordinator          │
│  Each owns its own state slice                          │
├─────────────────────────────────────────────────────────┤
│  DOMAIN                                                 │
│  ThemeAutoSwitchEngine, GridPlacementEngine              │
│  SetupEvaluator, EntitlementManager                     │
│  DrivingModeDetector, ThermalManager                    │
├─────────────────────────────────────────────────────────┤
│  PLUGIN / PACK                                          │
│  DataProvider (Flow<DataSnapshot>)                       │
│  WidgetRenderer (@Composable Render)                    │
│  ThemeProvider (List<DashboardThemeDefinition>)          │
├─────────────────────────────────────────────────────────┤
│  DATA                                                   │
│  LayoutDataStore (Proto), UserPreferencesRepository      │
│  PairedDeviceStore (Proto), ConnectionEventStore         │
│  ProviderSettingsStore (Preferences, namespaced keys)   │
├─────────────────────────────────────────────────────────┤
│  OBSERVABILITY                                          │
│  DqxnLogger, DqxnTracer, MetricsCollector               │
│  WidgetHealthMonitor, ThermalTrendAnalyzer, AnrWatchdog │
│  ErrorReporter, CrashContextProvider                    │
└─────────────────────────────────────────────────────────┘
```

### State Collection Policy

- **Dashboard Layer 0 (always present)**: Use `collectAsState()` (no lifecycle awareness) for widget data flows. Lifecycle pausing causes a jank spike when all 12+ widgets resume simultaneously.
- **Overlay Layer 1**: Use `collectAsStateWithLifecycle()` for overlay-specific state.
- **Manual pause/resume**: Suspend widget data collection explicitly when CPU-heavy overlays are open (via `WidgetBindingCoordinator.pauseAll()`/`resumeAll()`), not via lifecycle.

### Design Principles

- **Decomposed state** — Widget data, theme state, layout state, and UI mode are separate `StateFlow`s. No god-object state class.
- **IoC data binding** — widgets never choose their data source; the system binds providers by data type compatibility
- **Declarative schemas** — settings UI and setup wizards driven by schema definitions, not custom composables
- **Entitlement gating via `Gated` interface** — applied uniformly to renderers, providers, themes, settings, and auto-switch modes
- **Widget error isolation** — each widget renders inside a catch boundary; one widget's failure shows a fallback, not an app crash
- **ConnectionStateMachine** — validated state transitions with explicit rules, retry counts, and timeouts
- **Thermal adaptation** — rendering quality degrades gracefully under thermal pressure before the OS forces throttling

## 5. Dashboard-as-Shell Pattern

The dashboard is **not a navigation destination**. It lives as a persistent layer below the NavHost:

```
Box {
    Layer 0: DashboardLayer (always present, live canvas)
    Layer 1: OverlayNavHost (settings, pickers, dialogs)
}
```

Benefits:
- Dashboard state never destroyed on navigation
- Overlays stack visually on top
- Suspendable routes pause dashboard state collection to reduce CPU — identified via **type-safe route matching**, not string comparison

## Detailed Architecture

| Document | Scope |
|---|---|
| [State Management](docs/arch/state-management.md) | Decomposed coordinators, data binding, typed DataSnapshot, backpressure, DI scoping, background lifecycle |
| [Compose Performance](docs/arch/compose-performance.md) | Recomposition isolation, state read deferral, grid layout, thermal management, startup optimization, edge-to-edge, memory leaks |
| [Plugin System](docs/arch/plugin-system.md) | Widget/provider/theme contracts, error isolation, KSP generation, theme system, entitlement gating |
| [Observability](docs/arch/observability.md) | Structured logging, distributed tracing, metrics, health monitoring, crash/error reporting, analytics, Firebase integration |
| [Persistence](docs/arch/persistence.md) | Proto DataStore, corruption handling, schema migration, preset system, R8 rules |
| [Testing](docs/arch/testing.md) | Test infrastructure, framework choices, test layers, CI gates, agentic validation pipeline |
| [Build System](docs/arch/build-system.md) | Convention plugins, lint rules, agentic framework, CI configuration |
| [Platform Integration](docs/arch/platform.md) | Navigation, driving mode, alerts, security, permissions |
