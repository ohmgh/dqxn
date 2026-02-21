# Testing Strategy

> Test infrastructure, framework choices, test layers, CI gates, and development workflow.

## Test Infrastructure

`DashboardTestHarness` provides a full DSL for coordinator-level testing:

```kotlin
@Test
fun `adding widget triggers binding and persists layout`() = dashboardTest {
    val speedometer = testWidget(typeId = "core:speedometer")
    dispatch(DashboardCommand.AddWidget(speedometer))

    assertThat(layoutState().widgets).hasSize(1)
    assertThat(bindingJobs()).containsKey(speedometer.id)
    turbine(layoutStore.flow) {
        assertThat(awaitItem().widgets).hasSize(1)
    }
}
```

`testFixtures` source sets per module share fakes and builders:
- `testWidget(typeId, size, position, ...)` — widget instance builder
- `testTheme(name, isDark, colors, ...)` — theme definition builder
- `testSnapshot<T>()` — typed snapshot builder
- `FakeLayoutRepository`, `FakeWidgetDataBinder`, `SpyActionProviderRegistry`, `TestDataProvider`

All tests use `StandardTestDispatcher` — no `Thread.sleep`, no real-time delays.

## Test Framework Split

JUnit5 for unit tests, flow tests, property-based tests, and visual regression tests. JUnit4 for Hilt integration tests (`HiltAndroidRule` is JUnit4-only).

| Test type | Framework | Why |
|---|---|---|
| Unit, flow, coordinator | JUnit5 + `de.mannodermaus.android-junit` | `@Tag` filtering, `@Nested`, jqwik requires JUnit5 Platform |
| Property-based | JUnit5 (jqwik test engine) | jqwik is a JUnit5 test engine, runs alongside Jupiter |
| Visual regression | JUnit5 + Roborazzi + Robolectric | Roborazzi 1.56.0+ confirmed AGP 9 compatible |
| Hilt integration | JUnit4 + `HiltAndroidRule` | Only supported path |
| Instrumented (device) | JUnit4 | Android test runner is JUnit4-native |

### Test Tags

- `@Tag("fast")` — pure logic, <100ms per test
- `@Tag("compose")` — requires `ComposeTestRule`
- `@Tag("visual")` — Roborazzi screenshot tests
- `@Tag("integration")` — full DI graph
- `@Tag("benchmark")` — device required

## Test Layers

**Unit Tests** (JUnit5 + MockK + Truth):
- Core types, plugin API contracts, persistence stores, coordinators
- `assertWithMessage()` everywhere for clear failure diagnostics
- Plugin: `de.mannodermaus.android-junit` 2.0.1+. Note: Jacoco is broken on AGP 9 — use Kover if needed.

**Flow Tests** (Turbine + StandardTestDispatcher):
- Widget data binding lifecycle
- Theme auto-switch transitions
- Entitlement change propagation
- Coordinator state emissions

**Visual Regression Tests** (Roborazzi 1.56.0+ + Robolectric):
- Robolectric 4.15.x — pin for now; upgrade to 4.16 when stable for `compileSdk 36` test coverage
- Baselines stored in `src/test/resources/screenshots/{testClass}/{testName}.png`
- Use `compare` mode in CI, `record` mode for baseline updates
- Screenshot matrix: ~108 manageable screenshots covering all widget/theme/state combinations

**Interaction Tests** (compose.ui.test + Robolectric):
- Drag-to-move, resize handle detection, long-press, widget focus/unfocus, HorizontalPager swipe

**Performance Tests** (Macrobenchmarks on managed device):
- Dashboard cold start, edit mode toggle, widget picker open, 12-widget steady-state frame duration

**Contract Tests** (abstract test classes in `:sdk:contracts` testFixtures):

```kotlin
abstract class WidgetRendererContractTest {
    abstract fun createRenderer(): WidgetRenderer

    @Test
    fun `typeId follows packId-colon-name format`() {
        assertThat(createRenderer().typeId).matches("[a-z]+:[a-z][a-z0-9-]+")
    }

    @Test
    fun `render does not throw with empty data`() { /* compose test rule */ }

    @Test
    fun `accessibility description is non-empty`() {
        assertThat(createRenderer().accessibilityDescription(WidgetData.Empty)).isNotEmpty()
    }
}
```

```kotlin
abstract class DataProviderContractTest {
    abstract fun createProvider(): DataProvider

    @Test
    fun `emits within firstEmissionTimeout`() = runTest {
        val first = withTimeoutOrNull(createProvider().firstEmissionTimeout) {
            createProvider().provideState().first()
        }
        assertWithMessage("Provider must emit within timeout").that(first).isNotNull()
    }

    @Test
    fun `respects cancellation without leaking`() = runTest {
        val job = launch { createProvider().provideState().collect {} }
        job.cancelAndJoin()
        // Verify coroutine machinery is fully idle — no leaked child jobs
        advanceUntilIdle()
        assertWithMessage("Leaked coroutines after cancellation")
            .that(testScheduler.currentTime).isEqualTo(testScheduler.currentTime)
    }

    @Test
    fun `callbackFlow closes resources on cancellation`() = runTest {
        val fdCountBefore = File("/proc/self/fd/").listFiles()?.size ?: -1
        val job = launch { createProvider().provideState().collect {} }
        advanceTimeBy(1000) // let provider register listeners
        job.cancelAndJoin()
        advanceUntilIdle()
        val fdCountAfter = File("/proc/self/fd/").listFiles()?.size ?: -1
        // Allow small variance for GC timing, but flag gross leaks
        assertWithMessage("File descriptor leak after provider cancellation")
            .that(fdCountAfter).isAtMost(fdCountBefore + 2)
    }

    @Test
    fun `snapshotType is a valid DataSnapshot subtype`() {
        assertThat(createProvider().snapshotType).isAssignableTo(DataSnapshot::class)
    }
}
```

**State Machine Tests** (exhaustive + jqwik property-based):
- `ConnectionStateMachine`: exhaustive transition coverage (every state x every event)
- Property-based: random event sequences never reach an illegal state, always terminate

```kotlin
@Property
fun `connection FSM never reaches illegal state`(
    @ForAll("connectionEvents") events: List<ConnectionEvent>,
) {
    val fsm = ConnectionStateMachine()
    events.forEach { event ->
        assertThat(fsm.transition(event)).isNotInstanceOf(IllegalStateTransition::class.java)
    }
}
```

**Integration Tests** (JUnit4 + Hilt):
- Full DI graph construction, DataStore read/write roundtrip, registry population, entitlement propagation

**Accessibility Tests**:
- Semantics: every widget root has `contentDescription`
- Contrast: WCAG 2.1 AA for critical text
- Touch targets: all interactive elements >= 76dp

**Schema UI Tests** (parameterized):
- Every `SettingDefinition` subtype renders correctly
- Visibility predicates and entitlement gating

**Chaos Tests**:
- Random provider failures, thermal spikes, entitlement revocation, process death, rapid widget add/remove
- `ChaosEngine` accepts `seed: Long` for deterministic reproduction

## Mutation Testing

Two plugins depending on module type:
- **JVM-only modules**: `info.solidsoft.pitest` (v1.19.0+)
- **Android library modules**: `pl.droidsonroids.pitest` (v0.2.25+) — AGP 9 compatibility unverified as of Feb 2026. **Fallback**: if incompatible, mutation testing runs only on JVM-only modules (`:sdk:common`, `:sdk:contracts` domain types, `:codegen:*`, pure Kotlin domain logic). The >80% kill rate CI gate applies only to modules where pitest can run.
- `pitest-kotlin` extension with both plugins to filter Kotlin-specific false positives
- Kill rate target: > 80%

## Fuzz Testing

kotlinx.fuzz (JetBrains, built on Jazzer). JVM-only. Targets:
- JSON theme parsing (malformed gradients, missing/extra fields)
- JSON preset parsing
- Proto DataStore deserialization with corrupted bytes

## Widget Settings Property Testing

jqwik property-based testing validates that widget renderers survive arbitrary settings without crashing. Unlike fuzz testing (which targets byte-level deserialization), this generates *structurally valid* settings maps with random values:

```kotlin
@Property
fun `renderer survives arbitrary settings`(
    @ForAll("settingsMaps") settings: ImmutableMap<String, Any>,
) {
    val renderer = createRenderer()
    composeTestRule.setContent {
        CompositionLocalProvider(
            LocalWidgetData provides WidgetData.Empty,
            LocalWidgetScope provides TestWidgetScope(),
        ) {
            renderer.Render(
                isEditMode = false,
                style = testWidgetStyle(),
                settings = settings,
                modifier = Modifier.size(200.dp),
            )
        }
    }
    // No assertion — survival is the test
}

@Provide
fun settingsMaps(): Arbitrary<ImmutableMap<String, Any>> =
    Arbitraries.maps(
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
        Arbitraries.oneOf(
            Arbitraries.strings(),
            Arbitraries.integers(),
            Arbitraries.floats(),
            Arbitraries.of(true, false),
        )
    ).ofMaxSize(10).map { it.toImmutableMap() }
```

This catches settings handling bugs (missing keys, wrong types, null values) that unit tests with hand-picked settings miss. Every pack's `WidgetRendererContractTest` subclass inherits this.

## KSP Processor Tests

Compilation tests for `@DashboardWidget` and `@DashboardDataProvider`:
- Valid annotations produce correct `PackManifest`
- Duplicate `typeId` within module -> compilation error
- Missing required fields -> clear error message

## CI Gates

| Gate | Threshold |
|---|---|
| P50 frame duration | < 8ms |
| P95 frame duration | < 12ms |
| P99 frame duration | < 16ms |
| Jank rate (frames > 16ms) | < 2% |
| Cold startup time | < 1.5s |
| Compose stability | max 0 unstable classes |
| Non-skippable composables | max 5 |
| Mutation kill rate (critical modules) | > 80% |
| Unit test coverage (coordinators) | > 90% line |
| Release smoke test | Dashboard renders with data |
| P50 trend detection | Alert when P50 increases >20% from 7-day rolling average |

## Agentic Validation Pipeline

Tiered validation for fast feedback loops:

```
Tier 1 — Compile Check (~8s):
  ./gradlew :affected:module:compileDebugKotlin --console=plain

Tier 2 — Fast Unit Tests (~12s):
  ./gradlew :affected:module:testDebugUnitTest --console=plain -PincludeTags=fast

Tier 3 — Full Module Tests (~30s):
  ./gradlew :affected:module:testDebugUnitTest --console=plain

Tier 4 — Dependent Module Tests (~60s):
  ./gradlew :dep1:test :dep2:test --console=plain

Tier 5 — Visual Regression (if UI changed, ~45s):
  ./gradlew :affected:module:verifyRoborazzi --console=plain

Tier 6 — Full Suite (before commit):
  ./gradlew assembleDebug test lintDebug --console=plain
```

Stop at the first failing tier and fix before proceeding.

## Agentic Debug Runbook

**Compile Errors**: Parse for `e:` prefixed lines, extract file path and line number, fix, re-run Tier 1.

**Test Failures**: Parse for `FAILED` test names, read test + source file, fix the source (not the test) unless the test expectation is wrong, re-run Tier 2/3.

**Visual Regressions**: Compare `_actual.png` vs `_expected.png` in `build/outputs/roborazzi/`. If intentional: `recordRoborazzi`. If not: fix rendering, re-run Tier 5.

**Runtime Crashes (on-device)**: Check `${filesDir}/debug/diagnostics/` for auto-captured `DiagnosticSnapshot` files. These contain correlated ring buffer tail, metrics, thermal state, and widget health at the moment of the anomaly — no need to manually call dump commands. If `agenticTraceId` is present, the snapshot was triggered by an agentic command and the causal chain is traceable.

**Verification After Fix**: Re-run the failing tier before proceeding. On success, continue from next tier.

## Agentic Chaos Testing

ChaosEngine is accessible via agentic broadcast commands for agent-driven fault injection. This bridges the gap between programmatic chaos tests (unit/integration) and agentic E2E verification.

### ChaosEngine Injection Architecture

ChaosEngine injects faults through clean DI seams — no `@VisibleForTesting` backdoors, no production code mutation:

**ThermalManager**: Already an injectable interface. Tests use `FakeThermalManager` with a controllable `MutableStateFlow<ThermalLevel>`. The existing `simulate-thermal` agentic command delegates to the same override path. No production code changes needed.

**EntitlementManager**: Already has `StubEntitlementManager` in debug builds with a "Simulate Free User" toggle. Extended with `simulateRevocation(entitlementId: String)` and `simulateGrant(entitlementId: String)` for programmatic chaos control. No production code changes needed.

**WidgetDataBinder**: New `DataProviderInterceptor` interface, registered via Hilt multibinding:

```kotlin
interface DataProviderInterceptor {
    fun intercept(providerId: String, upstream: Flow<DataSnapshot>): Flow<DataSnapshot>
}

// In :core:agentic (debug only)
class ChaosProviderInterceptor @Inject constructor() : DataProviderInterceptor {
    private val activeFaults = ConcurrentHashMap<String, ProviderFault>()

    fun injectFault(providerId: String, fault: ProviderFault) { activeFaults[providerId] = fault }
    fun clearFault(providerId: String) { activeFaults.remove(providerId) }

    override fun intercept(providerId: String, upstream: Flow<DataSnapshot>): Flow<DataSnapshot> {
        val fault = activeFaults[providerId] ?: return upstream
        return when (fault) {
            is ProviderFault.Kill -> emptyFlow()
            is ProviderFault.Delay -> upstream.onEach { delay(fault.delayMs) }
            is ProviderFault.Error -> upstream.onStart { throw fault.exception }
        }
    }
}

sealed interface ProviderFault {
    data object Kill : ProviderFault
    data class Delay(val delayMs: Long) : ProviderFault
    data class Error(val exception: Exception) : ProviderFault
}
```

`WidgetDataBinder` applies all registered interceptors in order. Production builds have an empty interceptor set (zero overhead). Debug builds register `ChaosProviderInterceptor` via Hilt.

### Chaos ↔ Diagnostic Correlation

Each chaos injection carries an `injectionId` that propagates through to any resulting `DiagnosticSnapshot`:

```
chaos-inject → injectionId: "chaos-inj-001"
  → ChaosProviderInterceptor kills provider
  → WidgetBindingCoordinator detects timeout
  → DiagnosticSnapshotCapture.capture(ProviderTimeout, injectionId = "chaos-inj-001")
  → snap_perf_xxx.json includes injectionId
```

The `chaos-stop` summary maps each injection to its downstream effects:

```json
{
  "injected_faults": [
    {
      "injectionId": "chaos-inj-001",
      "type": "provider-failure",
      "target": "core:gps-speed",
      "at_ms": 5230,
      "resultingSnapshots": ["snap_perf_1708444805230.json"]
    }
  ]
}
```

This eliminates timestamp-proximity guessing for fault-to-effect correlation. Agents and CI assertions can programmatically verify: "injection X caused snapshot Y with system response Z."

### Agent Debug Loop

The detect → investigate → reproduce → verify cycle using all three systems together:

```
1. DETECT    — Observability auto-captures DiagnosticSnapshot on anomaly
2. INVESTIGATE — Agent calls `diagnose-widget {id}` for correlated context
3. REPRODUCE  — Agent calls `chaos-inject` to trigger the same fault deterministically
4. VERIFY     — Agent calls `dump-health` / `diagnose-widget` to confirm recovery
```

### Chaos Test Examples

**Provider failure recovery** (agent verifies fallback activation):
```
chaos-inject {"fault":"provider-failure","providerId":"core:gps-speed","duration":10}
  → wait 2s
dump-health
  → verify: widget binding fell back to "core:network-speed"
  → verify: widget status is "Ready", not "ProviderMissing"
chaos-inject {"fault":"provider-failure","providerId":"core:gps-speed","duration":0}  // clear
  → wait 2s
dump-health
  → verify: widget rebinds to primary "core:gps-speed"
```

**Thermal degradation** (agent verifies frame pacing):
```
chaos-inject {"fault":"thermal","level":"DEGRADED"}
  → wait 5s
diagnose-performance
  → verify: targetFps reduced, glow disabled, frame times within budget
chaos-inject {"fault":"thermal","level":"NORMAL"}
  → wait 5s
diagnose-performance
  → verify: targetFps restored, glow re-enabled
```

**Entitlement revocation** (agent verifies reactive downgrade):
```
chaos-inject {"fault":"entitlement-revoke","entitlementId":"plus"}
  → wait 1s
diagnose-widget {plusWidgetId}
  → verify: status is "EntitlementRevoked", fallback UI shown
```

### Chaos in CI

Deterministic chaos runs as part of Tier 6 (full suite) using seed-based reproduction:

```kotlin
@Test
fun `dashboard survives 30s combined chaos with seed 42`() {
    client.send("chaos-start", mapOf("seed" to 42, "profile" to "combined"))
    Thread.sleep(30_000)
    val summary = client.send("chaos-stop")
    // All widgets should be in Ready or fallback state — none in error
    val health = client.send("dump-health")
    health.widgets.forEach { (id, status) ->
        assertThat(status).isNotInstanceOf(WidgetHealthStatus.Error::class.java)
    }
}
```

> **Note**: This is an instrumented test running on a real device — `Thread.sleep` is intentional here (testing real system timing behavior). The `StandardTestDispatcher` / no-real-time-delays principle applies to JVM-hosted unit tests, not instrumented tests where real concurrency and system interactions are under test.

## Agentic E2E Protocol

Instrumented E2E tests reuse the agentic broadcast protocol. `AgenticTestClient` wraps ADB broadcasts with assertion helpers:

```kotlin
class AgenticTestClient(private val device: UiDevice) {
    fun send(command: String, params: Map<String, Any> = emptyMap()): JsonObject {
        val paramsJson = Json.encodeToString(params)
        device.executeShellCommand(
            "am broadcast -a app.dqxn.android.AGENTIC.$command " +
            "-n app.dqxn.android.debug/.debug.AgenticReceiver " +
            "--es params '$paramsJson'"
        )
        // Read result from broadcast or file
        return parseResponse(command)
    }

    fun assertState(path: String, expected: Any) {
        val state = send("dump-state")
        val actual = JsonPath.read<Any>(state.toString(), path)
        assertWithMessage("State at $path").that(actual).isEqualTo(expected)
    }

    fun awaitCondition(
        command: String,
        path: String,
        expected: Any,
        timeoutMs: Long = 5000,
        pollMs: Long = 500,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = send(command)
            if (JsonPath.read<Any>(state.toString(), path) == expected) return
            Thread.sleep(pollMs)
        }
        fail("Condition not met within ${timeoutMs}ms: $path == $expected")
    }
}
```

This ensures E2E tests exercise the same command paths the agent uses — no parallel infrastructure to maintain.

### E2E Test Examples

```kotlin
@Test
fun `widget add persists across process death`() {
    client.send("widget-add", mapOf("widgetType" to "core:speedometer"))
    client.assertState("$.data.layout.widgetCount", 1)

    // Simulate process death
    device.executeShellCommand("am force-stop app.dqxn.android.debug")
    device.executeShellCommand("am start app.dqxn.android.debug/.MainActivity")
    Thread.sleep(3000) // cold start

    client.assertState("$.data.layout.widgetCount", 1)
}

@Test
fun `thermal degradation reduces frame rate`() {
    client.send("chaos-inject", mapOf("fault" to "thermal", "level" to "DEGRADED"))
    client.awaitCondition("diagnose-performance", "$.thermal.targetFps", 30, timeoutMs = 5000)
    client.send("chaos-inject", mapOf("fault" to "thermal", "level" to "NORMAL"))
    client.awaitCondition("diagnose-performance", "$.thermal.targetFps", 60, timeoutMs = 5000)
}
```

## Test Principles

- **Deterministic**: `StandardTestDispatcher` everywhere
- **Clear failures**: `assertWithMessage()` on every assertion
- **Fast**: < 10s per module for unit tests
- **Self-contained**: No test depends on device state, network, or file system outside sandbox

## Test Failure Diagnostics

### Coordinator Tests (JUnit5 TestWatcher)

A JUnit5 `TestWatcher` extension auto-dumps harness state on test failure:

```kotlin
class HarnessStateOnFailure : TestWatcher {
    override fun testFailed(context: ExtensionContext, cause: Throwable) {
        val harness = context.getStore(NAMESPACE).get("harness", DashboardTestHarness::class.java)
            ?: return
        println("=== Harness State at Failure ===")
        println("Layout: ${harness.layoutState()}")
        println("Theme: ${harness.themeState()}")
        println("Widget statuses: ${harness.widgetStatuses()}")
        println("Binding jobs: ${harness.bindingJobs().keys}")
        println("Ring buffer tail: ${harness.ringBufferTail(20)}")
    }
}
```

No dependency on `DiagnosticSnapshotCapture` — the harness already has direct access to coordinator state. This is faster, simpler, and doesn't require the full observability graph in unit tests.

### E2E Tests (AgenticTestClient)

`AgenticTestClient` auto-captures diagnostic state on assertion failure:

```kotlin
class AgenticTestClient(private val device: UiDevice) {
    fun assertState(path: String, expected: Any) {
        val state = send("dump-state")
        val actual = JsonPath.read<Any>(state.toString(), path)
        if (actual != expected) {
            // Auto-capture before failing
            val health = send("dump-health")
            val metrics = send("dump-metrics")
            println("=== Diagnostic Context on Failure ===")
            println("Health: $health")
            println("Metrics: $metrics")
        }
        assertWithMessage("State at $path").that(actual).isEqualTo(expected)
    }
}
```

This gives E2E test failures the same rich context as runtime anomalies, without modifying production code or the `AnomalyTrigger` sealed hierarchy.
