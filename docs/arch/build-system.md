# Build System

> Convention plugins, lint rules, agentic framework, and CI configuration.

## AGP 9.0 Key Changes

- **Built-in Kotlin support** — AGP manages Kotlin compilation directly; no `org.jetbrains.kotlin.android` plugin
- **New DSL interfaces** — old `BaseExtension` types are gone; convention plugins use the new DSL exclusively
- **New R8 options** — `-processkotlinnullchecks` for controlling Kotlin null-check processing

## Convention Plugins

Convention plugins (`:build-logic/convention`) enforce shared defaults. Compose compiler is only applied to modules with UI:

```kotlin
// Modules WITH Compose: :app, :feature:*, :sdk:ui, :core:design-system
id("dqxn.android.compose")

// Pack modules: auto-wires all :sdk:* dependencies
id("dqxn.pack")

// Modules WITHOUT Compose: :sdk:contracts, :sdk:common, :sdk:observability, :sdk:analytics,
//   :core:thermal, :core:driving, :core:firebase, :core:agentic, :codegen:*, :data:*
```

## KSP Over KAPT

All annotation processing uses KSP. No KAPT — enables Gradle configuration cache and reduces incremental build time.

## Custom Lint Rules (`:lint-rules`)

| Rule | Severity | Description |
|---|---|---|
| `ModuleBoundaryViolation` | Error | Pack modules importing outside `:sdk:*` boundary |
| `KaptDetection` | Error | Any module applying `kapt` plugin |
| `ComposeInNonUiModule` | Error | Compose imports in non-UI modules |
| `MutableCollectionInImmutable` | Warning | `MutableList`/`MutableMap` inside `@Immutable` types |
| `WidgetScopeBypass` | Error | `LaunchedEffect` without `LocalWidgetScope` in widget renderers |
| `MainThreadDiskIo` | Warning | DataStore/SharedPreferences access without `Dispatchers.IO` |

## Pre-commit Hooks

```bash
# .githooks/pre-commit
./gradlew :lint-rules:test --console=plain --warning-mode=summary
grep -r "feature.dashboard" packs/ && exit 1
grep -r "kapt" --include="*.kts" */build.gradle.kts && exit 1
```

## Architectural Fitness Functions (CI)

```kotlin
@Test
fun `no pack module depends on dashboard`() {
    // Parses Gradle module dependencies, asserts no :packs:* -> :feature:dashboard edge
}

@Test
fun `all registered widgets pass contract tests`() {
    widgetRegistry.all().forEach { renderer ->
        assertThat(renderer.typeId).matches("[a-z]+:[a-z][a-z0-9-]+")
        assertThat(renderer.compatibleSnapshots).isNotEmpty()
        assertThat(renderer.accessibilityDescription(WidgetData.Empty)).isNotEmpty()
    }
}

@Test
fun `all widget snapshot types have at least one provider`() {
    val providedTypes = providerRegistry.all().map { it.snapshotType }.toSet()
    widgetRegistry.all().forEach { widget ->
        widget.compatibleSnapshots.forEach { type ->
            assertWithMessage("Widget ${widget.typeId} declares '${type.simpleName}' but no provider emits it")
                .that(providedTypes).contains(type)
        }
    }
}
```

## Gradle Scaffold Tasks

```bash
./gradlew :packs:free:scaffoldWidget --name=altimeter --snapshots=AltitudeSnapshot
./gradlew :packs:plus:scaffoldProvider --name=weather --snapshot=WeatherSnapshot
./gradlew :packs:themes:scaffoldTheme --name="Ocean Breeze" --isDark=false
```

## Build Configuration

```properties
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
ksp.incremental=true
```

Agent builds use `--console=plain --warning-mode=summary` for machine-parseable output.

## Build Time Budget

- Incremental (single file change): < 15s
- Clean build: < 120s

## Agentic Framework (Debug Only)

ADB broadcast-based automation:

```
adb shell am broadcast \
  -a app.dqxn.android.AGENTIC.{command} \
  -n app.dqxn.android.debug/.debug.AgenticReceiver \
  --es params '{"widgetType":"core:speedometer"}'
```

### Commands

| Command | Description |
|---|---|
| `ping` | Health check, returns app state summary |
| `navigate` | Navigate to a Route |
| `back` | Trigger back navigation |
| `query-state` | Return current dashboard state as JSON |
| `capture-start/stop` | Start/stop event capture session |
| `widget-add/remove/move/resize/focus/tap/settings-set` | Widget manipulation |
| `dashboard-reset` | Reset to default preset |
| `theme-set-mode/apply-preset` | Theme operations |
| `preset-load/save/list/remove` | Preset management |
| `list-commands` | List all available commands with parameter schemas |
| `dump-state` | Full dashboard state as structured JSON |
| `dump-metrics` | MetricsCollector snapshot |
| `dump-log-buffer` | RingBufferSink contents as JSON-lines |
| `dump-traces` | Active and recent TraceContext spans |
| `dump-health` | WidgetHealthMonitor status per widget |
| `dump-connections` | ConnectionStateMachine state, paired devices |
| `simulate-thermal` | Force thermal level for testing |

### Large Payload Handling

Large payloads write to temp file and return the path via broadcast result data:

```kotlin
val file = File(context.cacheDir, "dump_${System.currentTimeMillis()}.json")
file.writeText(jsonPayload)
setResultData(file.absolutePath)
// Agent reads via: adb pull /data/data/app.dqxn.android.debug/cache/dump_xxx.json
```

### Structured State Dumps

```json
{
  "command": "dump-state",
  "timestamp": 1708444800000,
  "data": {
    "layout": { "widgetCount": 8, "widgets": [...] },
    "theme": { "current": "cyberpunk", "mode": "DARK", "preview": null },
    "thermal": { "level": "NORMAL", "headroom": 0.45, "targetFps": 60 },
    "driving": false,
    "editMode": false
  }
}
```

### Debug Overlay System

Located in `:app:src/debug/`. Each overlay independently toggleable:

| Overlay | Content |
|---|---|
| Frame Stats | Real-time FPS, frame time histogram, jank count |
| Recomposition Visualizer | Color flash on recomposing widgets |
| Provider Flow DAG | Visual graph with live throughput |
| State Machine Viewer | ConnectionStateMachine state + history |
| Thermal Trending | Live thermal headroom graph with predicted transition |
| Widget Health | Per-widget data freshness, error state, binding status |
| Trace Viewer | Active spans and recent completed spans |

### Machine-Readable Log Format

`JsonFileLogSink` writes JSON-lines to `${filesDir}/debug/dqxn.jsonl` (debug builds only):

```json
{"ts":1708444800123,"level":"DEBUG","tag":"BINDING","trace":"abc123","span":"bind-speedometer","session":"sess-001","msg":"Provider bound","providerId":"core:gps-speed","widgetId":"def456","elapsedMs":12}
```

### Crash Report Enrichment

Widget crashes include structured context via `ErrorReporter`:

```kotlin
errorReporter.reportWidgetCrash(
    typeId = "core:speedometer",
    widgetId = "abc-123",
    lastSnapshot = SpeedSnapshot(speed = 65f, ...),
    settings = mapOf("showArcs" to "true", "speedUnit" to "KMH"),
    stackTrace = throwable,
    thermalLevel = ThermalLevel.NORMAL,
    drivingState = false,
)
```

`CaptureSessionRegistry` records TAP, WIDGET_MOVE, WIDGET_RESIZE, NAVIGATION events.

The receiver is restricted to debug builds only. Demo providers are gated to debug builds.
