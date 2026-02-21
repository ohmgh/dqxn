# Build System

> Convention plugins, lint rules, agentic framework, and CI configuration.

## AGP 9.0 Key Changes

- **Built-in Kotlin support** — AGP manages Kotlin compilation directly; no `org.jetbrains.kotlin.android` plugin
- **New DSL interfaces** — old `BaseExtension` types are gone; convention plugins use the new DSL exclusively
- **New R8 options** — `-processkotlinnullchecks` for controlling Kotlin null-check processing

## Convention Plugins

Convention plugins (`:build-logic/convention`) enforce shared defaults. Compose compiler is only applied to modules with UI:

```kotlin
// Modules WITH Compose: :app, :feature:*, :sdk:ui, :core:design
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
grep -r "feature.dashboard" pack/ && exit 1
grep -r "kapt" --include="*.kts" */build.gradle.kts && exit 1
```

## Architectural Fitness Functions (CI)

```kotlin
@Test
fun `no pack module depends on dashboard`() {
    // Parses Gradle module dependencies, asserts no :pack:* -> :feature:dashboard edge
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
./gradlew :pack:free:scaffoldWidget --name=altimeter --snapshots=AltitudeSnapshot
./gradlew :pack:plus:scaffoldProvider --name=weather --snapshot=WeatherSnapshot
./gradlew :pack:themes:scaffoldTheme --name="Ocean Breeze" --isDark=false
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
| `diagnose-widget` | Correlated health, binding, data, errors, and log tail for one widget |
| `diagnose-performance` | Frame histogram + thermal trend + jank widgets + slow commands + memory |
| `diagnose-bindings` | All widget→provider bindings, stuck providers, fallback activations |
| `diagnose-crash` | Most recent `DiagnosticSnapshot` for a widget (auto-captured on crash) |
| `chaos-start` | Start ChaosEngine with optional seed for deterministic reproduction |
| `chaos-stop` | Stop ChaosEngine, return session summary (injected faults + system responses) |
| `chaos-inject` | Inject a specific fault: `provider-failure`, `thermal`, `entitlement-revoke`, `anr-simulate` |

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

### Compound Diagnostic Commands

Single-call correlated diagnostics. Each command produces a `DiagnosticSnapshot`-derived JSON bundle (see [observability.md](observability.md#anomaly-auto-capture)):

**`diagnose-widget {widgetId}`** returns:
- Widget health status (from `WidgetHealthMonitor`)
- Current binding: provider ID, connection state, last emission timestamp
- Data freshness: last snapshot timestamp vs staleness threshold
- Error history: crash count, last error, retry state
- Last 20 log entries tagged to this widget (filtered from `RingBufferSink` by `widgetId` field)
- Current `WidgetStatusCache` priority chain resolution

**`diagnose-performance`** returns:
- Frame histogram (from `MetricsCollector`)
- Thermal state + `ThermalTrendAnalyzer` prediction
- Top 5 widgets by recomposition count
- Slow command log (commands >100ms from ring buffer)
- Memory watermark + GC pressure indicator
- Current `RenderConfig` (target FPS, glow enabled, pixel shift active)

**`diagnose-bindings`** returns:
- All active widget→provider bindings with status
- Stuck providers (bound but no emission within `firstEmissionTimeout`)
- Fallback activations (where primary provider was unavailable)
- Provider error counts and retry states

**`diagnose-crash {widgetId}`** returns:
- Most recent `DiagnosticSnapshot` auto-captured for this widget (if any)
- Falls back to current live diagnostic if no snapshot exists
- Includes `agenticTraceId` if the crash was triggered by an agentic command

### Agentic Trace Correlation

Every agentic command receives a trace ID that propagates through the entire command chain:

```
Agent sends: widget-add --params '{"widgetType":"core:speedometer"}'
  → AgenticReceiver generates traceId: "agentic-1708444800123"
  → DashboardCommand.AddWidget(traceId = "agentic-1708444800123")
  → WidgetBindingCoordinator.bind() runs under TraceContext(traceId)
  → If anomaly occurs: DiagnosticSnapshot.agenticTraceId = "agentic-1708444800123"
  → Agent queries: diagnose-crash → sees its own traceId in the snapshot
```

This closes the causal loop: the agent can distinguish "I caused this" from "this happened coincidentally."

### Chaos Injection Commands

ChaosEngine is exposed via agentic commands for agent-driven fault injection and verification:

```
adb shell am broadcast \
  -a app.dqxn.android.AGENTIC.chaos-start \
  -n app.dqxn.android.debug/.debug.AgenticReceiver \
  --es params '{"seed":42,"profile":"provider-stress"}'
```

**Chaos profiles** (predefined fault injection patterns):

| Profile | What it does |
|---|---|
| `provider-stress` | Random provider failures (1-3 providers, 5-30s intervals) |
| `thermal-ramp` | Progressive thermal escalation NORMAL → MODERATE → DEGRADED over 60s |
| `entitlement-churn` | Rapid entitlement grant/revoke cycles (2s intervals) |
| `widget-storm` | Rapid add/remove of widgets (tests binding cleanup) |
| `combined` | All of the above simultaneously |

**`chaos-inject`** for targeted single-fault injection:

```
# Kill a specific provider
adb shell am broadcast -a app.dqxn.android.AGENTIC.chaos-inject \
  --es params '{"fault":"provider-failure","providerId":"core:gps-speed","duration":10}'

# Force thermal level
adb shell am broadcast -a app.dqxn.android.AGENTIC.chaos-inject \
  --es params '{"fault":"thermal","level":"DEGRADED"}'

# Revoke entitlement
adb shell am broadcast -a app.dqxn.android.AGENTIC.chaos-inject \
  --es params '{"fault":"entitlement-revoke","entitlementId":"plus"}'
```

**`chaos-stop`** returns a session summary:

```json
{
  "duration_ms": 30000,
  "seed": 42,
  "injected_faults": [
    {"type": "provider-failure", "target": "core:gps-speed", "at_ms": 5230},
    {"type": "provider-failure", "target": "core:compass", "at_ms": 12400}
  ],
  "system_responses": [
    {"type": "fallback-activated", "widget": "abc-123", "from": "core:gps-speed", "to": "core:network-speed", "at_ms": 5235},
    {"type": "widget-status-change", "widget": "def-456", "status": "ProviderMissing", "at_ms": 12405}
  ],
  "diagnostic_snapshots_captured": 2
}
```

The agent workflow for debugging: inject fault → observe system response → verify recovery → fix if wrong.

### Diagnostic File Index

All agent-accessible diagnostic artifacts in debug builds:

| Path | Content | Rotation |
|---|---|---|
| `${filesDir}/debug/dqxn.jsonl` | JSON-lines structured log | 10MB, 3 files |
| `${filesDir}/debug/diagnostics/snap_*.json` | Auto-captured `DiagnosticSnapshot` | 20 files max |
| `${cacheDir}/dump_*.json` | On-demand state dumps (from dump commands) | Cleared on app restart |
| `${cacheDir}/chaos_*.json` | Chaos session summaries | Cleared on app restart |

Agent pulls via `adb pull /data/data/app.dqxn.android.debug/...`.
