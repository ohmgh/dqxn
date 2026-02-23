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

// Pack modules: auto-wires all :sdk:* dependencies, wires KSP-generated stability config
id("dqxn.pack")

// Snapshot sub-modules: pure Kotlin, :sdk:contracts only, no Android/Compose
// Contains only @DashboardSnapshot-annotated data classes for cross-boundary access
id("dqxn.snapshot")

// Modules WITHOUT Compose: :sdk:contracts, :sdk:common, :sdk:observability, :sdk:analytics,
//   :core:thermal, :core:firebase, :core:agentic, :codegen:*, :data:*
```

## KSP Over KAPT

All annotation processing uses KSP. No KAPT — enables Gradle configuration cache and reduces incremental build time.

## Custom Lint Rules (`:lint-rules`)

All rules delivered in Phase 1, enforcement starts when consumers exist:

| Rule | Severity | Enforcement starts | Description |
|---|---|---|---|
| `KaptDetection` | Error | Phase 1 | Any module applying `kapt` plugin — breaks configuration cache |
| `NoHardcodedSecrets` | Error | Phase 1 | SDK keys, API tokens, credentials in source; secrets via `local.properties` or secrets plugin |
| `ModuleBoundaryViolation` | Error | Phase 2 | Pack modules importing outside `:sdk:*` / `*:snapshots` boundary |
| `ComposeInNonUiModule` | Error | Phase 2 | Compose imports in non-UI modules |
| `AgenticMainThreadBan` | Error | Phase 6 | `Dispatchers.Main` usage in agentic command handlers |

Additional rules added when consumers exist: `WidgetScopeBypass` (Phase 8, first widget renderer). `MutableCollectionInImmutable` deferred — caught by Compose stability report. `MainThreadDiskIo` deferred — standard Android discipline enforced by code review.

## Pre-commit Hooks

```bash
# .githooks/pre-commit
./gradlew :lint-rules:test --console=plain --warning-mode=summary
./gradlew spotlessCheck --console=plain --warning-mode=summary
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

@Test
fun `snapshot sub-modules contain only DashboardSnapshot types`() {
    // Parses source files in *:snapshots modules, asserts every public class is
    // annotated @DashboardSnapshot and implements DataSnapshot. No providers,
    // no widgets, no Android framework imports.
}
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

ContentProvider-based debug automation:

```
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method {command} \
  --arg '{"widgetType":"essentials:speedometer"}'
```

### Commands

| Command | Description |
|---|---|
| **Lifecycle** | |
| `ping` | Health check, returns app state summary |
| `navigate` | Navigate to a Route |
| `back` | Trigger back navigation |
| **Widget manipulation** | |
| `widget-add/remove/move/resize/focus/tap/settings-set` | Widget CRUD and interaction |
| `dashboard-reset` | Reset to default preset |
| **Theme / Presets** | |
| `theme-set-mode/apply-preset` | Theme operations |
| `preset-load/save/list/remove` | Preset management |
| **State inspection** | |
| `list-commands` | List all available commands with parameter schemas |
| `list-widgets` | Registry of available widget types + providers with metadata (typeId, compatibleSnapshots, settingsSchema) |
| `dump-state` | Full dashboard state as structured JSON |
| `dump-metrics` | MetricsCollector snapshot |
| `dump-log-buffer` | RingBufferSink contents as JSON-lines |
| `dump-traces` | Active and recent TraceContext spans |
| `dump-health` | Widget health + system context + recent diagnostic snapshot references (enriched) |
| `dump-connections` | ConnectionStateMachine state, paired devices |
| `dump-semantics` | Full Compose semantics tree with bounds, test tags, content descriptions, actions, text |
| `query-semantics` | Filtered semantics query — find nodes by test tag, text, content description, or bounds region |
| **Diagnostics** | |
| `diagnose-widget` | Correlated health, binding, data, errors, widget expectations, and log tail for one widget |
| `diagnose-performance` | Frame histogram + per-widget draw time + thermal trend + jank widgets + slow commands + memory |
| `diagnose-bindings` | All widget→provider bindings, stuck providers, stalled bindings, fallback activations |
| `diagnose-crash` | Most recent `DiagnosticSnapshot` for a widget (auto-captured on crash, fallback to SharedPrefs crash evidence) |
| `diagnose-thermal` | Thermal headroom history + frame rate adaptation history + glow toggle history + thermal snapshots |
| `list-diagnostics` | Diagnostic snapshot files with metadata; supports `since` timestamp param for polling |
| **Remediation** | |
| `rebind-widget` | Cancel current binding job and re-run `WidgetBindingCoordinator.bind()` for a widget |
| `clear-widget-errors` | Reset error counts and `WidgetStatusCache` for a widget |
| `restart-provider` | Cancel and restart a provider's `provideState()` flow |
| **Simulation / Testing** | |
| `simulate-thermal` | Force thermal level for testing |
| `trigger-anomaly` | Directly fire `DiagnosticSnapshotCapture.capture()` with a synthetic trigger (tests the observability pipeline) |
| `reset-diagnostics` | Clear diagnostic state: `snapshots` / `ring-buffer` / `all` (clean state between test runs) |
| **Chaos** | |
| `chaos-start` | Start ChaosEngine with optional seed for deterministic reproduction |
| `chaos-stop` | Stop ChaosEngine, return session summary (injected faults + system responses) |
| `chaos-inject` | Inject a fault: `provider-failure`, `provider-flap`, `corrupt`, `thermal`, `entitlement-revoke`, `process-death`, `anr-simulate` |

### Response Protocol

All responses are written to temp files. The ContentProvider `call()` returns a `Bundle` containing only the file path:

```
Result: Bundle[{filePath=/data/data/app.dqxn.android.debug/cache/agentic_xxx.json}]
```

Agent reads clean JSON via:
```bash
path=$(adb shell content call --uri content://app.dqxn.android.debug.agentic \
  --method dump-state | grep -oP 'filePath=\K[^}]+')
adb shell cat "$path"
```

This avoids `Bundle.toString()` parsing ambiguity (nested JSON in `Result: Bundle[{response=...}]` has quoting issues) and stays well under the Binder ~1MB transaction limit for all payload sizes.

### Structured State Dumps

```json
{
  "status": "ok",
  "data": {
    "layout": { "widgetCount": 8, "widgets": [...] },
    "theme": { "current": "cyberpunk", "mode": "DARK", "preview": null },
    "thermal": { "level": "NORMAL", "headroom": 0.45, "targetFps": 60 },
    "editMode": false,
    "safeMode": {
      "active": false,
      "crashCount": 0,
      "windowMs": 60000,
      "crashes": [],
      "lastCrashWidgetTypeId": null
    }
  }
}
```

`safeMode` is always present. When `active: true`, the dashboard shows only the clock widget with a reset banner. `crashes` contains timestamps of recent crashes within the 60s window. `lastCrashWidgetTypeId` identifies the most likely culprit for the agent to investigate.

### Debug Overlay System

Located in `:app:src/debug/`. Each overlay independently toggleable. V1 ships three overlays covering the critical debugging needs — additional overlays (Recomposition Visualizer, Provider Flow DAG, State Machine Viewer, Trace Viewer) deferred until specific debugging needs arise:

| Overlay | Content |
|---|---|
| Frame Stats | Real-time FPS, frame time histogram, per-widget draw time, jank count |
| Widget Health | Per-widget data freshness, error state, binding status, throttle info |
| Thermal Trending | Live thermal headroom graph with predicted transition, FPS adaptation history |

### Machine-Readable Log Format

`JsonFileLogSink` writes JSON-lines to `${filesDir}/debug/dqxn.jsonl` (debug builds only):

```json
{"ts":1708444800123,"level":"DEBUG","tag":"BINDING","trace":"abc123","span":"bind-speedometer","session":"sess-001","msg":"Provider bound","providerId":"essentials:gps-speed","widgetId":"def456","elapsedMs":12}
```

### Crash Report Enrichment

Widget crashes include structured context via `ErrorReporter`:

```kotlin
errorReporter.reportWidgetCrash(
    typeId = "essentials:speedometer",
    widgetId = "abc-123",
    throwable = throwable,
    context = WidgetErrorContext(
        lastSnapshot = SpeedSnapshot(speed = 65f, ...),
        settings = mapOf("showArcs" to "true", "speedUnit" to "KMH"),
        thermalLevel = ThermalLevel.NORMAL,
    ),
)
```

UI interaction events (TAP, WIDGET_MOVE, WIDGET_RESIZE, NAVIGATION) are logged as structured `LogEntry` items into the standard `RingBufferSink` pipeline (see [observability.md](observability.md#interaction-event-logging)). They appear automatically in `DiagnosticSnapshot.ringBufferTail` and `diagnose-widget` log tail — no separate capture session required.

The ContentProvider is restricted to debug builds only (registered in `src/debug/AndroidManifest.xml`). Demo providers are gated to debug builds.

### Command Result Envelope

All commands return a standardized JSON envelope:

```json
{"status": "ok", "data": {...}}
{"status": "error", "message": "Widget def-456 not found"}
```

`status` is always present. `data` is present on success. `message` is present on error. Large payloads still write to temp file — the envelope contains the file path in `data.filePath`.

### AgenticContentProvider (`:app/src/debug/`)

```kotlin
class AgenticContentProvider : ContentProvider() {

    @Volatile private var cachedHealthMonitor: WidgetHealthMonitor? = null
    @Volatile private var cachedAnrWatchdog: AnrWatchdog? = null

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AgenticEntryPoint {
        fun commandRouter(): AgenticCommandRouter
        fun logger(): DqxnLogger
        fun healthMonitor(): WidgetHealthMonitor
        fun anrWatchdog(): AnrWatchdog
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        // Runs on binder thread — NOT main thread. No ANR timeout.
        val entryPoint = try {
            EntryPointAccessors.fromApplication(
                context!!.applicationContext, AgenticEntryPoint::class.java)
        } catch (e: IllegalStateException) {
            return bundleOf("filePath" to writeResponse(
                envelope("error", "App initializing, retry after ping returns ok")))
        }

        if (cachedHealthMonitor == null) cachedHealthMonitor = entryPoint.healthMonitor()
        if (cachedAnrWatchdog == null) cachedAnrWatchdog = entryPoint.anrWatchdog()

        val traceId = "agentic-${SystemClock.elapsedRealtimeNanos()}"
        val resultJson = runBlocking(Dispatchers.Default) {
            withTimeout(8_000) {
                try {
                    val params = parseParams(arg, traceId)
                    entryPoint.commandRouter().route(method, params)
                        .let { envelope("ok", it) }
                } catch (e: CancellationException) { throw e }
                catch (e: TimeoutCancellationException) { envelope("error", "Command timed out after 8s") }
                catch (e: JsonParseException) { envelope("error", "Malformed params: ${e.message}") }
                catch (e: TargetNotFoundException) { envelope("error", "Not found: ${e.message}") }
                catch (e: Exception) {
                    entryPoint.logger().error(LogTags.AGENTIC, e) { "Command '$method' failed" }
                    envelope("error", "Internal: ${e.message}")
                }
            }
        }
        return bundleOf("filePath" to writeResponse(resultJson))
    }

    // Deadlock-safe direct reads — no runBlocking, no coroutines
    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        return when (uri.pathSegments.firstOrNull()) {
            "health" -> cachedHealthMonitor?.let { buildHealthCursor(it) }  // null if app still initializing
            "anr" -> cachedAnrWatchdog?.let { buildAnrCursor(it) }
            else -> null
        }
    }

    private fun writeResponse(json: String): String {
        val file = File(context!!.cacheDir, "agentic_${SystemClock.elapsedRealtimeNanos()}.json")
        file.writeText(json)
        return file.absolutePath
    }

    override fun onCreate(): Boolean {
        // Clean up response files from previous session
        context?.cacheDir?.listFiles { f -> f.name.startsWith("agentic_") }?.forEach { it.delete() }
        return true
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
```

### Threading Model

`ContentProvider.call()` runs on a binder thread (pool of ~15 threads), not the main thread. This eliminates the threading mismatch that `BroadcastReceiver.onReceive()` had:

- **Suspend handlers work naturally**: `runBlocking(Dispatchers.Default)` bridges to the suspend `CommandHandler.handle()`. The binder thread blocks, but the main thread is free.
- **`Channel.send()` in mutation handlers**: Suspends the coroutine on `Dispatchers.Default`, not the binder thread or main thread. No deadlock.
- **No ANR timeout**: Binder threads have no broadcast-style ANR window. The `withTimeout(8_000)` is a safety net, not a system constraint.
- **Concurrent commands**: Each `content call` gets its own binder thread. `AgenticCommandRouter` and all data sources (`MetricsCollector`, `RingBufferSink`, etc.) are thread-safe by design.

**Critical constraint**: `CommandHandler.handle()` must NEVER use `Dispatchers.Main`. If the main thread is busy (Compose rendering) and a handler calls `withContext(Dispatchers.Main)`, the handler blocks until main is free. This is not a deadlock (the main thread isn't waiting on the handler), but it couples handler latency to main-thread load. Enforced via lint rule `AgenticMainThreadBan`.

### Hilt Integration

`ContentProvider.onCreate()` runs before `Application.onCreate()`. Hilt's `SingletonComponent` is created in `Application.onCreate()`. The provider uses `@EntryPoint` + `EntryPointAccessors.fromApplication()` in `call()`, not `onCreate()`, so the Hilt graph is available. If `call()` races with cold start (rare), the try-catch returns an "App initializing" error — the agent retries after `ping` succeeds.

### Error Semantics

Error semantics (included in JSON envelope):
- **Malformed params**: Invalid JSON in `--arg`. Agent should fix the command syntax.
- **Not found**: Target widget/provider/preset doesn't exist. Agent should verify IDs via `dump-state`.
- **Internal**: Unexpected failure. Logged for investigation.
- **Timeout**: Command exceeded 8s. Likely the dashboard coordinator is stuck.
- **App initializing**: Hilt graph not ready. Retry after `ping` returns ok.

### Compound Diagnostic Commands

Single-call correlated diagnostics. Each command produces a `DiagnosticSnapshot`-derived JSON bundle (see [observability.md](observability.md#anomaly-auto-capture)):

**`diagnose-widget {widgetId}`** returns self-describing diagnostic output with expected vs actual state:
- Widget health status (from `WidgetHealthMonitor`)
- Current binding: provider ID, connection state, last emission timestamp
- **Widget expectations** (computed on-demand): expected emission interval, staleness threshold, first emission timeout, required vs available snapshots. Uses `KClass.qualifiedName` for snapshot type identification. Computed at query time from `WidgetRegistry` and `DataProviderRegistry` — not stored in `DiagnosticSnapshot` since expectations may change between anomaly time and investigation time

```kotlin
@Immutable
data class WidgetExpectations(
    val expectedEmissionIntervalMs: Long,
    val stalenessThresholdMs: Long,
    val firstEmissionTimeoutMs: Long,
    val requiredSnapshots: ImmutableSet<String>,      // KClass.qualifiedName
    val availableSnapshots: ImmutableSet<String>,      // KClass.qualifiedName
)
```
- **Throttle metadata**: `{ "nativeEmissionRateHz": 60, "effectiveEmissionRateHz": 30, "throttleReason": "thermal:DEGRADED" }` — disambiguates "provider slow because thermal" from "provider broken"
- **Data freshness with expectations**: `{ "actual": "15s", "threshold": "3s", "expectedInterval": "16ms", "status": "ESCALATED_STALE" }` — the contradiction between actual and expected is self-evident to any reasoning system
- Error history: crash count, last error, retry state
- **Binding lifecycle history**: last 10 binding events (`BIND_STARTED`, `BIND_CANCELLED`, `REBIND_SCHEDULED`, `PROVIDER_FALLBACK`, `FIRST_EMISSION`) from `RingBufferSink` — shows the *sequence* that led to the current state, not just the state itself
- Last 20 log entries tagged to this widget (filtered from `RingBufferSink` by `widgetId` field)
- Current `WidgetStatusCache` priority chain resolution

**`diagnose-performance`** returns:
- Frame histogram with CI-gate threshold annotations: `{ "p50Ms": 7, "p50ThresholdMs": 8, "p95Ms": 13, "p95ThresholdMs": 12, "p95Status": "EXCEEDED", "p99Ms": 14, "p99ThresholdMs": 16, "jankRate": 0.01, "jankThreshold": 0.02 }` — self-describing for autonomous agents, thresholds sourced from CI gate config
- **Per-widget draw time**: `{ "essentials:speedometer": { "p50Ms": 2, "p99Ms": 5 }, "plus:trip": { "p50Ms": 8, "p99Ms": 14 } }` — enables performance bisection without trial-and-error widget removal. Measured via `System.nanoTime()` around widget draw in `WidgetContainer`'s draw modifier.
- Thermal state + `ThermalTrendAnalyzer` prediction
- Top 5 widgets by recomposition count
- Slow command log (commands >100ms from ring buffer)
- Memory watermark + GC pressure indicator
- Current `RenderConfig` (target FPS, glow enabled, pixel shift active)

**`diagnose-bindings`** returns:
- All active widget→provider bindings with status
- Stuck providers (bound but no emission within `firstEmissionTimeout`)
- **Stalled bindings**: widgets where binding active >2x `firstEmissionTimeout` and `widgetData` still `Empty` — all upstream providers failed initial emission (per-slot watchdog tracks individual provider timeouts)
- Fallback activations (where primary provider was unavailable)
- Provider error counts and retry states

**`diagnose-crash {widgetId}`** returns:
- Most recent `DiagnosticSnapshot` auto-captured for this widget (if any), from the crash rotation pool
- Falls back to `SharedPreferences` crash evidence (typeId, exception, top 5 stack frames, thermal level, timestamp) written synchronously by `CrashEvidenceWriter` — survives process death when async snapshot write doesn't complete
- Final fallback: assembles a live diagnostic from current `WidgetHealthMonitor`, `MetricsCollector`, and `RingBufferSink` state — no `OnDemandCapture` trigger needed
- Includes `agenticTraceId` if the crash was triggered by an agentic command

**`diagnose-thermal`** returns correlated thermal diagnostics — thermal is the primary automotive failure mode requiring cross-source correlation:
- Current thermal level + headroom + `ThermalTrendAnalyzer` prediction (time to next tier)
- **Headroom history**: raw samples from `ThermalTrendAnalyzer` ring buffer (last 60s at 5s intervals)
- **Frame rate adaptation history**: timestamps of `FramePacer` FPS changes (60→30→15→30→60) with reasons
- **Glow toggle history**: timestamps of glow enable/disable transitions
- **Thermal snapshot references**: list of `snap_thermal_*.json` files with timestamps, from `list-diagnostics`
- Current `RenderConfig` state (target FPS, glow enabled, pixel shift active)

**`dump-health`** returns enriched health overview — widget health plus system context, replacing the need for a separate "anomaly summary" command:
```json
{
  "status": "ok",
  "data": {
    "widgets": {
      "abc-123": {"typeId": "essentials:speedometer", "status": "Ready", "providerId": "essentials:gps-speed", "lastEmissionMs": 150},
      "def-456": {"typeId": "plus:trip", "status": "BindingStalled", "providerId": "plus:trip-accumulator", "lastEmissionMs": null}
    },
    "systemContext": {
      "thermalLevel": "NORMAL",
      "thermalHeadroom": 0.45,
      "safeMode": false,
      "widgetCount": 12,
      "healthyWidgetCount": 10,
      "recentCrashes": 0
    },
    "recentSnapshots": [
      {"file": "snap_perf_1708444802000.json", "trigger": "BindingStalled", "typeId": "plus:trip", "timestamp": 1708444802000}
    ]
  }
}
```

The agent's first call after detecting an issue should be `dump-health` — it provides the complete picture needed to decide which `diagnose-*` command to drill into.

**`list-widgets`** returns the full registry of available widget types and providers:
```json
{
  "status": "ok",
  "data": {
    "widgets": [
      {
        "typeId": "essentials:speedometer",
        "displayName": "Speedometer",
        "packId": "essentials",
        "compatibleSnapshots": ["SpeedSnapshot", "AccelerationSnapshot", "SpeedLimitSnapshot"],
        "requiredAnyEntitlement": null,
        "settingsSchema": ["speedUnit", "showArcs", "showDigital", "limitOffset"]
      }
    ],
    "providers": [
      {
        "providerId": "essentials:gps-speed",
        "snapshotType": "SpeedSnapshot",
        "packId": "essentials",
        "isAvailable": true,
        "connectionState": "CONNECTED"
      }
    ]
  }
}
```

Essential for agent autonomy — the agent needs this to verify widget-add results, construct regression tests with correct `testWidget()` parameters, and understand the provider→snapshot→widget compatibility graph.

**`list-diagnostics`** returns enriched metadata for all diagnostic snapshot files. Supports `since` timestamp param for efficient polling:
```json
// list-diagnostics {"since": 1708444800000}
{
  "status": "ok",
  "data": {
    "snapshots": [
      {"file": "snap_crash_1708444800456.json", "trigger": "WidgetCrash", "typeId": "essentials:speedometer", "widgetId": "abc-123", "timestamp": 1708444800456, "recommendedCommand": "diagnose-crash"},
      {"file": "snap_thermal_1708444801000.json", "trigger": "ThermalEscalation", "from": "NORMAL", "to": "DEGRADED", "timestamp": 1708444801000, "recommendedCommand": "diagnose-thermal"},
      {"file": "snap_perf_1708444802000.json", "trigger": "BindingStalled", "typeId": "plus:trip", "widgetId": "def-456", "timestamp": 1708444802000, "recommendedCommand": "diagnose-bindings"}
    ],
    "anrLatest": "anr_latest.json",
    "crashEvidence": {"exists": true, "typeId": "essentials:speedometer", "timestamp": 1708444800456}
  }
}
```

Replaces fragile `adb shell ls` with structured, filterable metadata. Agent can find "most recent thermal snapshot" or "all crash snapshots for widget X" without parsing filenames. Primary notification mechanism for autonomous agent loops. Agent polls every 2-5s during active debugging sessions using the `since` parameter for efficient incremental queries.

### Semantics Tree Inspection

`dump-semantics` and `query-semantics` expose the Compose semantics tree — the same data source `compose.ui.test` uses internally. This gives the agent (and E2E tests) pixel-accurate element positions, visibility, text content, and available actions without `UiAutomator` overhead or accessibility service setup.

#### SemanticsOwnerHolder

A debug-only `@Singleton` that the `ComposeView` registers into at composition:

```kotlin
// In :core:agentic (debug only)
@Singleton
class SemanticsOwnerHolder @Inject constructor() {
    @Volatile var owner: SemanticsOwner? = null

    fun snapshot(): SemanticsSnapshot? {
        val root = owner?.rootSemanticsNode ?: return null
        return buildSnapshot(root)
    }

    fun query(filter: SemanticsFilter): List<SemanticsNodeSnapshot> {
        val root = owner?.rootSemanticsNode ?: return emptyList()
        return walkTree(root).filter { filter.matches(it) }
    }
}
```

Registration happens in `DashboardLayer` (the always-present Layer 0):

```kotlin
// In :app/src/debug/ — DashboardLayerDebugExt.kt
@Composable
fun DashboardLayer.RegisterSemanticsOwner(holder: SemanticsOwnerHolder) {
    val view = LocalView.current
    LaunchedEffect(view) {
        // ComposeView exposes SemanticsOwner via ViewTreeSemanticsOwner
        val composeView = view as? RootForTest
        holder.owner = composeView?.semanticsOwner
    }
}
```

`RootForTest.semanticsOwner` is a stable Compose testing API (used by `ComposeTestRule` internally). No internal/hidden API access.

#### AgenticEntryPoint Extension

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AgenticEntryPoint {
    fun commandRouter(): AgenticCommandRouter
    fun logger(): DqxnLogger
    fun healthMonitor(): WidgetHealthMonitor
    fun anrWatchdog(): AnrWatchdog
    fun semanticsHolder(): SemanticsOwnerHolder  // <-- new
}
```

#### `dump-semantics` Command

Dumps the full semantics tree. Use for initial orientation or when you don't know what to filter on.

```
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method dump-semantics \
  --arg '{"maxDepth":10}'
```

Parameters:
- `maxDepth` (Int, optional, default 20): Maximum tree traversal depth. Use lower values for large UIs.
- `includeUnmerged` (Boolean, optional, default false): Include unmerged semantics nodes. Merged tree (default) matches what accessibility services and test matchers see.

Response:

```json
{
  "status": "ok",
  "data": {
    "rootBounds": {"left": 0, "top": 0, "right": 1080, "bottom": 2400},
    "nodeCount": 47,
    "tree": [
      {
        "id": 1,
        "testTag": "dashboard_grid",
        "bounds": {"left": 0, "top": 0, "right": 1080, "bottom": 2400},
        "size": {"width": 1080, "height": 2400},
        "isVisible": true,
        "children": [
          {
            "id": 5,
            "testTag": "widget_abc-123",
            "contentDescription": "Speedometer: 65 km/h",
            "bounds": {"left": 0, "top": 0, "right": 540, "bottom": 400},
            "size": {"width": 540, "height": 400},
            "isVisible": true,
            "text": [],
            "actions": ["OnClick", "OnLongClick"],
            "children": [
              {
                "id": 8,
                "text": ["65"],
                "bounds": {"left": 120, "top": 80, "right": 420, "bottom": 280},
                "size": {"width": 300, "height": 200},
                "isVisible": true,
                "children": []
              },
              {
                "id": 9,
                "text": ["km/h"],
                "bounds": {"left": 200, "top": 300, "right": 340, "bottom": 360},
                "size": {"width": 140, "height": 60},
                "isVisible": true,
                "children": []
              }
            ]
          }
        ]
      }
    ]
  }
}
```

Each node includes: `id` (semantics ID), `testTag` (if set), `contentDescription` (if set), `text` (list of text values), `bounds` (pixel rect — global coordinates), `size` (width × height in pixels), `isVisible` (accounts for `alpha`, `visibility`, and clip), `actions` (available semantics actions), `role` (if set — Button, Checkbox, etc.), `stateDescription` (if set), `children` (recursive).

Fields that are null/empty on a node are omitted from the JSON for compactness.

#### `query-semantics` Command

Filtered query — returns matching nodes without full tree traversal in the response. Preferred for targeted assertions.

```
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method query-semantics \
  --arg '{"testTag":"widget_abc-123"}'
```

Filter parameters (all optional, combined with AND):
- `testTag` (String): Exact match on `SemanticsProperties.TestTag`
- `testTagPattern` (String): Regex match on test tag (e.g., `"widget_.*"`)
- `text` (String): Substring match on any text value in the node
- `textExact` (String): Exact match on any text value
- `contentDescription` (String): Substring match on content description
- `hasAction` (String): Node has the named action (e.g., `"OnClick"`, `"SetText"`)
- `role` (String): Semantics role (e.g., `"Button"`, `"Checkbox"`, `"Image"`)
- `boundsIntersect` (Object `{left, top, right, bottom}`): Node bounds intersect the given region
- `isVisible` (Boolean): Filter by visibility (default: true — only visible nodes)
- `includeChildren` (Boolean, default false): Include child subtree of each matched node

Response:

```json
{
  "status": "ok",
  "data": {
    "matchCount": 1,
    "nodes": [
      {
        "id": 5,
        "testTag": "widget_abc-123",
        "contentDescription": "Speedometer: 65 km/h",
        "bounds": {"left": 0, "top": 0, "right": 540, "bottom": 400},
        "size": {"width": 540, "height": 400},
        "isVisible": true,
        "text": [],
        "actions": ["OnClick", "OnLongClick"]
      }
    ]
  }
}
```

#### Compound Query: `diagnose-widget` Enhancement

`diagnose-widget` now includes a `semantics` field when `SemanticsOwnerHolder.owner` is available:

```json
{
  "widgetId": "abc-123",
  "health": { ... },
  "binding": { ... },
  "semantics": {
    "testTag": "widget_abc-123",
    "bounds": {"left": 0, "top": 0, "right": 540, "bottom": 400},
    "isVisible": true,
    "contentDescription": "Speedometer: 65 km/h",
    "childCount": 4,
    "textContent": ["65", "km/h"],
    "actions": ["OnClick", "OnLongClick"]
  }
}
```

If the widget's semantics node is not found (widget not rendered, off-viewport), `semantics` is `null` — the agent now knows whether a "stale data" issue is a binding problem (semantics present, data stale) or a rendering problem (semantics absent).

#### Test Tag Convention

Widgets and key dashboard elements MUST set `Modifier.testTag()` for agentic discoverability:

| Element | Test tag pattern | Example |
|---|---|---|
| Widget container | `widget_{widgetId}` | `widget_abc-123` |
| Dashboard grid | `dashboard_grid` | |
| Bottom bar | `bottom_bar` | |
| Profile icon | `profile_{profileId}` | `profile_driving` |
| Add widget button | `add_widget_button` | |
| Edit mode toggle | `edit_mode_toggle` | |
| Settings button | `settings_button` | |
| Notification banner | `banner_{bannerId}` | `banner_ble_adapter_off` |
| Toast | `toast_{index}` | `toast_0` |
| Widget status overlay | `widget_status_{widgetId}` | `widget_status_abc-123` |

Test tags are debug-only overhead (string allocation per frame for recomposed nodes). Two options:
1. Always set test tags — Compose skips the property entirely when `testTagsAsResourceId` is disabled in release builds. The `Modifier.testTag()` call still allocates but is near-zero cost.
2. Conditional: `Modifier.thenIf(BuildConfig.DEBUG) { testTag(...) }` — zero release cost but clutters code.

**Decision**: Always set test tags. The allocation is negligible for dashboard-scale UI (~50 nodes), and unconditional tags enable accessibility tooling and Espresso matchers in instrumented tests.

#### Threading and Performance

The semantics tree is a snapshot of Compose layout state. `SemanticsOwner.rootSemanticsNode` reads from the layout tree on the calling thread — no main-thread dispatch required.

**However**: The semantics tree can be mid-update during composition/layout phases. Reading from the binder thread while main thread is composing may observe a partially-consistent tree. This is acceptable for debugging — a stale-by-one-frame tree is fine. For test assertions, `query-semantics` is called after `awaitCondition` settles the UI.

| Operation | Budget |
|---|---|
| `dump-semantics` (50 nodes) | < 5ms |
| `query-semantics` (single match) | < 2ms |
| Tree serialization to JSON | < 3ms |

If the tree exceeds 200 nodes, `dump-semantics` automatically truncates at `maxDepth` and includes `"truncated": true` in the response. `query-semantics` always walks the full tree regardless of size (filter evaluation is cheap).

### Deadlock-Safe Diagnostic Paths

The primary `AgenticContentProvider.call()` runs on binder threads, so it works even when the main thread is blocked. However, if a `CommandHandler` accidentally calls `withContext(Dispatchers.Main)` despite the `AgenticMainThreadBan` lint rule, that specific handler will deadlock when the main thread is busy with Compose rendering. `Dispatchers.Default` saturation is not the real risk in a dashboard app — the risk is handler-touching-Main.

For true deadlock diagnosis, `AgenticContentProvider.query()` provides lock-free direct reads that bypass handler routing entirely:

```bash
adb shell content query --uri content://app.dqxn.android.debug.agentic/health
adb shell content query --uri content://app.dqxn.android.debug.agentic/anr
```

These `query()` methods read directly from `WidgetHealthMonitor` and `AnrWatchdog` without `runBlocking` or coroutines — pure concurrent data structure reads on the binder thread.

If the binder thread pool itself is exhausted (full process deadlock), `AnrWatchdog` writes `anr_latest.json` via direct `FileOutputStream` on its dedicated thread. Agent retrieves via `adb pull`.

### Command Registry (`:codegen:agentic`)

KSP processor generates command routing, param validation, and schema metadata from `@AgenticCommand` annotations. Runs as `debugKsp` only — zero presence in release builds.

```kotlin
// In :core:agentic — annotation + handler colocated
@AgenticCommand(
    name = "widget-add",
    params = [Param("widgetType", String::class, required = true)],
)
class WidgetAddHandler @Inject constructor(
    private val commandChannel: Channel<DashboardCommand>,
) : CommandHandler {
    override suspend fun handle(params: TypedParams): JsonObject {
        val typeId = params.require<String>("widgetType")
        commandChannel.send(DashboardCommand.AddWidget(typeId = typeId, traceId = params.traceId))
        return jsonObject { "added" to typeId }
    }
}
```

KSP generates:
- `AgenticCommandRouter` — maps command name strings to handler instances, injected via Hilt
- Param validation with typed extraction and clear error messages on missing/wrong-type params
- `list-commands` schema output from annotation metadata
- Compilation error if `@AgenticCommand` exists without a `CommandHandler` implementation

### Agentic Trace Correlation

Every agentic command receives a trace ID that propagates through the entire command chain:

```
Agent sends: content call --method widget-add --arg '{"widgetType":"essentials:speedometer"}'
  → AgenticContentProvider.call() generates traceId: "agentic-1708444800123"
  → DashboardCommand.AddWidget(traceId = "agentic-1708444800123")
  → WidgetBindingCoordinator.bind() runs under TraceContext(traceId)
  → If anomaly occurs: DiagnosticSnapshot.agenticTraceId = "agentic-1708444800123"
  → Agent queries: diagnose-crash → sees its own traceId in the snapshot
```

This closes the causal loop: the agent can distinguish "I caused this" from "this happened coincidentally."

### TraceContext Propagation to Widget Effects

Deferred. Widget crash diagnostics include `widgetId` and `typeId`; the agentic command log includes timestamps and target widgets. Temporal correlation by `widgetId` is sufficient for v1. If agentic debugging reveals cases where trace correlation through widget effects would have saved investigation time, add `LocalWidgetTraceContext` CompositionLocal propagation then.

### Chaos Injection Commands

ChaosEngine is exposed via agentic commands for agent-driven fault injection and verification:

```
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method chaos-start \
  --arg '{"seed":42,"profile":"provider-stress"}'
```

**Example chaos sequences** (composed by tests using `chaos-inject`, not built into ChaosEngine):

| Profile | What it does |
|---|---|
| `provider-stress` | Example: random provider failures (1-3 providers, 5-30s intervals) |
| `provider-flap` | Example: rapid connect/disconnect cycling on 1-2 providers (500ms intervals). Tests retry/backoff stability under oscillation — common with real BLE hardware |
| `thermal-ramp` | Example: progressive thermal escalation NORMAL → MODERATE → DEGRADED over 60s |
| `entitlement-churn` | Example: rapid entitlement grant/revoke cycles (2s intervals) |
| `widget-storm` | Example: rapid add/remove of widgets (tests binding cleanup) |
| `process-death` | Example: `am force-stop` + restart. Tests DataStore persistence, binding re-establishment, safe mode activation |
| `combined` | Example: all of the above (except process-death) simultaneously |

**`chaos-inject`** for targeted single-fault injection:

```
# Kill a specific provider
adb shell content call --uri content://app.dqxn.android.debug.agentic \
  --method chaos-inject --arg '{"fault":"provider-failure","providerId":"essentials:gps-speed","duration":10}'

# Force thermal level
adb shell content call --uri content://app.dqxn.android.debug.agentic \
  --method chaos-inject --arg '{"fault":"thermal","level":"DEGRADED"}'

# Revoke entitlement
adb shell content call --uri content://app.dqxn.android.debug.agentic \
  --method chaos-inject --arg '{"fault":"entitlement-revoke","entitlementId":"plus"}'

# Provider flap (rapid connect/disconnect)
adb shell content call --uri content://app.dqxn.android.debug.agentic \
  --method chaos-inject --arg '{"fault":"provider-flap","providerId":"essentials:gps-speed","intervalMs":500,"durationMs":10000}'

# Corrupt data (valid-but-wrong snapshots)
adb shell content call --uri content://app.dqxn.android.debug.agentic \
  --method chaos-inject --arg '{"fault":"corrupt","providerId":"essentials:gps-speed","corruption":"nan-speed"}'

# Process death + restart (handler returns response, then kills after 500ms delay)
adb shell content call --uri content://app.dqxn.android.debug.agentic \
  --method chaos-inject --arg '{"fault":"process-death"}'
# Response: {"status":"ok","data":{"willTerminate":true,"delayMs":500}}
# Process dies after delay — agent waits, then restarts:
#   adb shell am start app.dqxn.android.debug/.MainActivity
#   await ping ok (max 10s)
```

**`chaos-stop`** returns a session summary:

```json
{
  "duration_ms": 30000,
  "seed": 42,
  "injected_faults": [
    {"type": "provider-failure", "target": "essentials:gps-speed", "at_ms": 5230, "resultingSnapshots": ["snap_perf_1708444805230.json"]},
    {"type": "provider-failure", "target": "essentials:compass", "at_ms": 12400, "resultingSnapshots": []}
  ],
  "system_responses": [
    {"type": "fallback-activated", "widget": "abc-123", "from": "essentials:gps-speed", "to": "essentials:network-speed", "at_ms": 5235},
    {"type": "widget-status-change", "widget": "def-456", "status": "ProviderMissing", "at_ms": 12405}
  ],
  "diagnostic_snapshots_captured": 1
}
```

The agent workflow for debugging: inject fault → observe system response → verify recovery → fix if wrong.

### Diagnostic File Index

All agent-accessible diagnostic artifacts in debug builds:

| Path | Content | Rotation |
|---|---|---|
| `${filesDir}/debug/dqxn.jsonl` | JSON-lines structured log | 10MB, 3 files |
| `${filesDir}/debug/diagnostics/snap_crash_*.json` | Crash/ANR/DataStoreCorruption `DiagnosticSnapshot` | 20 files max |
| `${filesDir}/debug/diagnostics/snap_thermal_*.json` | Thermal escalation snapshots | 10 files max |
| `${filesDir}/debug/diagnostics/snap_perf_*.json` | Jank, timeout, staleness, binding stall snapshots | 10 files max |
| `${filesDir}/debug/diagnostics/anr_latest.json` | Most recent ANR state (direct file write, no IO dispatcher) | 1 file, overwritten |
| `${cacheDir}/agentic_*.json` | Command response files | Cleaned in AgenticContentProvider.onCreate() |
| `${cacheDir}/chaos_*.json` | Chaos session summaries | Cleared on app restart |

Agent pulls via `adb pull /data/data/app.dqxn.android.debug/...`. Use `list-diagnostics` command for enriched metadata instead of raw `ls`.
