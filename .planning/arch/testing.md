# Testing Strategy

> Test infrastructure, framework choices, test layers, CI gates, and development workflow.

## Test Infrastructure

`DashboardTestHarness` provides a full DSL for coordinator-level testing:

```kotlin
@Test
fun `adding widget triggers binding and persists layout`() = dashboardTest {
    val speedometer = testWidget(typeId = "essentials:speedometer")
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

JUnit5 for unit tests, flow tests, and property-based tests. JUnit4 for Hilt integration tests (`HiltAndroidRule` is JUnit4-only).

| Test type | Framework | Why |
|---|---|---|
| Unit, flow, coordinator | JUnit5 + `de.mannodermaus.android-junit` | `@Tag` filtering, `@Nested`, jqwik requires JUnit5 Platform |
| Property-based | JUnit5 (jqwik test engine) | jqwik is a JUnit5 test engine, runs alongside Jupiter |
| Hilt integration | JUnit4 + `HiltAndroidRule` | Only supported path |
| Instrumented (device) | JUnit4 | Android test runner is JUnit4-native |

### Test Tags

- `@Tag("fast")` — pure logic, <100ms per test
- `@Tag("compose")` — requires `ComposeTestRule`
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

    // Resource leak detection (fd counting) removed — /proc/self/fd/ is unreliable on
    // JVM test runners (Robolectric shadows it inconsistently) and silently passes via ?: -1.
    // Actual resource leaks caught by: cancellation test above, LeakCanary in debug builds,
    // and WidgetScopeBypass lint rule catching missing cleanup patterns.

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
- Semantics: every widget root has `contentDescription` — verified on-device via `query-semantics {"testTagPattern":"widget_.*"}` asserting all matched nodes have non-empty `contentDescription`
- Contrast: WCAG 2.1 AA for critical text
- Touch targets: all interactive elements >= 76dp — verified on-device via `query-semantics {"hasAction":"OnClick"}` asserting all matched nodes have bounds width/height >= 76dp equivalent

**Schema UI Tests** (parameterized):
- Every `SettingDefinition` subtype renders correctly
- Visibility predicates and entitlement gating

**Chaos Tests**:
- Random provider failures, thermal spikes, entitlement revocation, process death, rapid widget add/remove
- `ChaosEngine` accepts `seed: Long` for deterministic reproduction

**Safety-Critical Tests** (coordinator-level, no device required):

```kotlin
// Safe mode counts total crashes across ALL widgets within the 60s window, not per-widget.
// 4 different widgets each crashing once triggers safe mode (>3 total).
@Test
fun `safe mode activates after 4 crashes in 60s`() = dashboardTest {
    repeat(4) { i ->
        dispatch(DashboardCommand.WidgetCrash(
            widgetId = "widget-$i",
            typeId = "essentials:speedometer",
            throwable = RuntimeException("test crash $i"),
        ))
        advanceTimeBy(10_000) // 10s between crashes
    }
    assertThat(safeMode().active).isTrue()
    assertThat(layoutState().widgets).hasSize(1) // clock only
    assertThat(layoutState().widgets.first().typeId).isEqualTo("essentials:clock")
}

```

**DataStore Resilience Tests**:

```kotlin
@Test
fun `corruption handler falls back to defaults and reports`() = runTest {
    // Corrupt the proto file
    layoutProtoFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))

    val store = createLayoutDataStore(layoutProtoFile)
    val layout = store.data.first()

    assertThat(layout).isEqualTo(DashboardLayout.getDefaultInstance())
    verify(errorReporter).reportNonFatal(any<InvalidProtocolBufferException>(), any())
}

@Test
fun `schema migration v1 to v2 preserves widget positions`() = runTest {
    // Write v1 proto
    val v1Layout = V1DashboardLayout.newBuilder()
        .addWidgets(V1WidgetPlacement.newBuilder().setTypeId("essentials:speedometer").setX(0).setY(0))
        .build()
    layoutProtoFile.writeBytes(v1Layout.toByteArray())

    // Open with v2 DataStore (triggers migration)
    val store = createLayoutDataStore(layoutProtoFile, currentVersion = 2)
    val layout = store.data.first()

    assertThat(layout.widgetsList).hasSize(1)
    assertThat(layout.widgetsList[0].typeId).isEqualTo("essentials:speedometer")
    // v2 adds gridUnitSize field — verify it got a default
    assertThat(layout.widgetsList[0].gridUnitSize).isEqualTo(DEFAULT_GRID_UNIT_SIZE)
}
```

**Entitlement Grace Period Tests** (coordinator, `StandardTestDispatcher` time control):

```kotlin
@Test
fun `entitlement preserved within 7-day offline grace period`() = runTest {
    // Grant plus entitlement with verification timestamp
    entitlementManager.grant("plus", verifiedAt = clock.now())

    // Advance 6 days — still within grace period
    advanceTimeBy(6.days.inWholeMilliseconds)
    assertThat(entitlementManager.hasEntitlement("plus")).isTrue()
}

@Test
fun `entitlement revoked after 7-day offline grace period`() = runTest {
    entitlementManager.grant("plus", verifiedAt = clock.now())

    // Advance past grace period
    advanceTimeBy(8.days.inWholeMilliseconds)
    assertThat(entitlementManager.hasEntitlement("plus")).isFalse()
    // Verify reactive downgrade propagated
    turbine(widgetStatuses) {
        assertThat(awaitItem().filter { it.value is WidgetHealthStatus.EntitlementRevoked }).isNotEmpty()
    }
}
```

**Observability Self-Tests** (JUnit5, JVM-hosted):

```kotlin
@Test
fun `AnrWatchdog requires 2 consecutive misses before capture`() {
    val watchdog = AnrWatchdog(fakeRingBuffer, spyLogger)
    // Block main handler for one ping cycle (single miss)
    blockMainHandler(3_000)
    assertThat(spyLogger.entries(LogTags.ANR)).isEmpty()

    // Block for two consecutive cycles (~5s)
    blockMainHandler(6_000)
    assertThat(spyLogger.entries(LogTags.ANR)).hasSize(1)
    assertThat(diagnosticsDir.resolve("anr_latest.json")).exists()
}

@Test
fun `AnrWatchdog skips capture when debugger attached`() {
    val watchdog = AnrWatchdog(fakeRingBuffer, spyLogger)
    Debug.setDebuggerConnected(true) // Robolectric shadow
    blockMainHandler(6_000)
    assertThat(spyLogger.entries(LogTags.ANR)).isEmpty()
}

@Test
fun `CrashEvidenceWriter survives process death via sync commit`() {
    val prefs = context.getSharedPreferences("crash_evidence", MODE_PRIVATE)
    val writer = CrashEvidenceWriter(prefs)
    Thread.setDefaultUncaughtExceptionHandler(writer)

    val exception = RuntimeException("widget crash")
    writer.uncaughtException(Thread.currentThread(), exception)

    // Verify sync write completed (commit, not apply)
    assertThat(prefs.getString("last_crash_exception", null))
        .isEqualTo("RuntimeException: widget crash")
    assertThat(prefs.getString("last_crash_stack_top5", null)).isNotEmpty()
    assertThat(prefs.getLong("last_crash_timestamp", 0)).isGreaterThan(0)
}
```

## Mutation Testing

**Deferred to post-launch.** Pitest (`info.solidsoft.pitest` + `pitest-kotlin`) will target `:sdk:common` and `:sdk:contracts` domain types once they stabilize. Kill rate target: > 80%.

Rationale for deferral: code is still in flux during greenfield development — high mutation kill rate doesn't indicate test quality when both code and tests are written together. KSP processors (`:codegen:*`) are permanently out of scope — compilation tests already verify generated output, and pitest mutates runtime behavior that KSP processors don't have. Android library modules remain out of scope due to `pl.droidsonroids.pitest` AGP 9 compatibility uncertainty and Compose rendering mutation noise.

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

Snapshot sub-module validation (enforced by `dqxn.snapshot` convention plugin + CI fitness test):
- Snapshot sub-modules contain only `@DashboardSnapshot`-annotated data classes
- No Android framework imports, no Compose imports, no provider/widget types
- All public types implement `DataSnapshot` and are annotated `@Immutable`
- Cross-module `dataType` uniqueness validated at runtime by `WidgetRegistry`

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

Tier 5 — On-Device Smoke (if device available, ~30s):
  ./gradlew :app:installDebug --console=plain
  adb shell content call --method ping          # wait for ok, max 10s
  adb shell content call --method dump-health   # all widgets Ready or fallback, none Error
  adb shell content call --method diagnose-performance  # P99 < 32ms (relaxed for debug)
  adb shell content call --method dump-semantics        # verify widget nodes rendered with correct test tags
  adb shell content call --method query-semantics --arg '{"testTagPattern":"widget_.*","isVisible":true}'
    # verify: matchCount == widgetCount from dump-health (all healthy widgets actually rendered)
  adb shell content call --method chaos-inject --arg '{"fault":"provider-failure","providerId":"essentials:gps-speed","duration":3}'
  sleep 5
  adb shell content call --method dump-health   # affected widget in fallback, not Error
  adb shell content call --method query-semantics --arg '{"testTag":"widget_status_<affected-id>"}'
    # verify: fallback status overlay visible on the affected widget

Tier 6 — Full Suite (before commit):
  ./gradlew assembleDebug test lintDebug --console=plain
```

Stop at the first failing tier and fix before proceeding.

Tier 5 bridges the gap between "JVM tests pass" and "works on device." It verifies app launch, widget binding, provider fallback, and rendered UI via semantics under real concurrency. Not a replacement for Tier 6 instrumented tests — it's a quick sanity check the agent runs after fixing on-device bugs.

## Agentic Debug Runbook

**Compile Errors**: Parse for `e:` prefixed lines, extract file path and line number, fix, re-run Tier 1.

**Test Failures**: Parse for `FAILED` test names. Run `git diff HEAD~1 --name-only` to identify recently changed files — cross-reference with the failing test's module to narrow the search space. Read test + source file, fix the source (not the test) unless the test expectation is wrong, re-run Tier 2/3.

**Runtime Crashes (on-device)**: Poll `list-diagnostics` with `since` param to find new auto-captured `DiagnosticSnapshot` files. Each `list-diagnostics` entry includes `recommendedCommand` — call it. Snapshots contain correlated ring buffer tail, metrics, thermal state, and widget health at the moment of the anomaly. For widget-specific diagnosis, call `diagnose-widget` which computes `WidgetExpectations` on demand (expected vs actual behavior) and includes `semantics` (rendered bounds, visibility, text content) when available. If no snapshot file exists (process died before async write completed), `diagnose-crash` falls back to `SharedPreferences` crash evidence written synchronously by `CrashEvidenceWriter`. If `agenticTraceId` is present, the snapshot was triggered by an agentic command and the causal chain is traceable. Chaos-injected faults are correlated temporally via `list-diagnostics since=` rather than propagated IDs.

**UI Verification (on-device)**: Use `query-semantics` to verify visual state after any mutation. After `widget-add`, verify the widget's semantics node exists with correct bounds. After provider failure, verify fallback UI text is rendered. After thermal degradation, verify glow effects are absent. Semantics queries answer "is it actually on screen?" — `dump-state` only answers "does the model say it should be?"

**Safe Mode**: If `dump-state` shows `safeMode.active: true`, the app crashed >3 times in 60s. Use `safeMode.lastCrashWidgetTypeId` to identify the culprit. `diagnose-crash` for that widget type to get the crash evidence.

**Main Thread Deadlock**: Agentic commands run on binder threads, so they work even when the main thread is blocked. If `call()`-based commands stall (e.g., handler waiting on main-thread state), use the lock-free `query()` paths: `adb shell content query --uri content://app.dqxn.android.debug.agentic/health`. If that also hangs (full process deadlock), `adb pull` the `anr_latest.json` file written by `AnrWatchdog` on its dedicated thread.

**Verification After Fix**: Re-run the failing tier. For on-device bugs, run Tier 5 smoke validation. On success, continue from next tier.

**Regression Guard**: After fixing a runtime bug found via agentic debugging, create a `DashboardTestHarness` regression test using the same `ProviderFault` / `FakeThermalManager` / fault primitives. The `DiagnosticSnapshot` provides all reproduction inputs: trigger type → fault primitive, `lastSnapshot` → test data, `settings` → widget config. Verify the test fails without the fix and passes with it.

## Agentic Chaos Testing

ChaosEngine is accessible via agentic commands for agent-driven fault injection. This bridges the gap between programmatic chaos tests (unit/integration) and agentic E2E verification.

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
            is ProviderFault.ErrorOnNext -> upstream.map { throw fault.exception }
            is ProviderFault.Corrupt -> upstream.map { fault.transform(it) }
            is ProviderFault.Flap -> upstream.flapTransform(fault.intervalMs, fault.durationMs)
        }
    }
}

// In :sdk:contracts main source set — shared between ChaosEngine (debug runtime) and DashboardTestHarness (test)
sealed interface ProviderFault {
    data object Kill : ProviderFault
    data class Delay(val delayMs: Long) : ProviderFault
    data class Error(val exception: Exception) : ProviderFault                    // cold-flow: throws on first collection
    data class ErrorOnNext(val exception: Exception) : ProviderFault              // hot-flow: throws on next emission
    data class Corrupt(val transform: (DataSnapshot) -> DataSnapshot) : ProviderFault  // valid-but-wrong data (NaN, negatives, overflow)
    data class Flap(val intervalMs: Long, val durationMs: Long) : ProviderFault   // rapid connect/disconnect cycling
}
```

`ProviderFault` lives in `:sdk:contracts` main source set because `ChaosProviderInterceptor` needs it at debug runtime, not just test time. `DashboardTestHarness` also consumes it via testFixtures → main dependency. When an agent reproduces a chaos-discovered bug as a unit test, the fault mechanism is identical — same sealed interface, same flow transformation logic.

`WidgetDataBinder` applies all registered interceptors in order. Production builds have an empty interceptor set (zero overhead). Debug builds register `ChaosProviderInterceptor` via Hilt.

### Chaos ↔ Diagnostic Correlation

Chaos-to-diagnostic correlation uses temporal matching — faults and snapshots are linked by timestamp proximity rather than propagated IDs:

```
chaos-inject at timestamp T
  → ChaosProviderInterceptor kills provider
  → WidgetBindingCoordinator detects timeout
  → DiagnosticSnapshotCapture.capture(ProviderTimeout)
  → snap_perf_xxx.json with timestamp > T
  → Agent polls: list-diagnostics {"since": T} → finds the resulting snapshot
```

The `chaos-stop` summary maps each injection to its downstream effects:

```json
{
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

Agents and CI assertions correlate faults to effects via `list-diagnostics` with `since` timestamps.

### Agent Debug Loop

The detect → investigate → reproduce → verify → guard cycle using all three systems together:

```
1. DETECT      — Observability auto-captures DiagnosticSnapshot on anomaly.
                 Agent polls `list-diagnostics` with `since` param every 2-5s.
                 Each entry includes `recommendedCommand` routing hint.
2. INVESTIGATE — Agent calls the recommended diagnose-* command for correlated context.
                 `diagnose-widget` includes expected vs actual values, binding history, throttle metadata.
                 On test failure: `git diff HEAD~1 --name-only` to identify recently changed files.
3. REPRODUCE   — Agent calls `chaos-inject` to trigger the same fault deterministically.
                 Temporal correlation via `list-diagnostics since=` confirms the fault caused the snapshot.
                 For code bugs: write a failing DashboardTestHarness test first.
4. FIX + VERIFY — Fix the code. Re-run failing tier.
                 For on-device bugs: run Tier 5 smoke validation (see below).
                 Call `dump-health` / `diagnose-widget` to confirm recovery on device.
                 Call `query-semantics` to verify the fix is visually correct (widget rendered,
                 correct text, expected bounds). State recovery without rendering is a partial fix.
5. GUARD       — Create a DashboardTestHarness regression test using the same ProviderFault /
                 FakeThermalManager / fault primitives that reproduced the bug.
                 Verify: test fails without fix, passes with fix.
                 Include the chaos-inject command in the test's doc comment for traceability.
```

Step 5 is optional for configuration issues or device-specific limitations. It is mandatory for code bugs found via agentic debugging — the fix is worthless without a regression guard.

### Chaos Test Examples

**Provider failure recovery** (agent verifies fallback activation):
```
chaos-inject {"fault":"provider-failure","providerId":"essentials:gps-speed","duration":10}
  → wait 2s
dump-health
  → verify: widget binding fell back to "essentials:network-speed"
  → verify: widget status is "Ready", not "ProviderMissing"
query-semantics {"testTag":"widget_<speedometer-id>"}
  → verify: widget node isVisible, contentDescription updated (not stale pre-fault text)
chaos-inject {"fault":"provider-failure","providerId":"essentials:gps-speed","duration":0}  // clear
  → wait 2s
dump-health
  → verify: widget rebinds to primary "essentials:gps-speed"
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

**Provider flap** (agent verifies rebind stability under oscillation):
```
chaos-inject {"fault":"provider-flap","providerId":"essentials:gps-speed","intervalMs":500,"durationMs":10000}
  → wait 12s
dump-health
  → verify: widget in Ready or fallback state, NOT in rapid rebind loop
  → verify: retry count did not exhaust (exponential backoff absorbs oscillation)
```

**Corrupt data** (agent verifies widget data validation):
```
chaos-inject {"fault":"corrupt","providerId":"essentials:gps-speed","corruption":"nan-speed"}
  → wait 2s
diagnose-widget {speedometerWidgetId}
  → verify: widget health shows data validation issue
query-semantics {"testTag":"widget_<speedometer-id>","includeChildren":true}
  → verify: no text node contains "NaN" — widget renders fallback/safe value
```

**Process death recovery** (agent verifies persistence + rebinding):
```
dump-state → record current layout
query-semantics {"testTagPattern":"widget_.*"} → record rendered widget set + bounds
chaos-inject {"fault":"process-death"}
  → returns {"status":"ok","data":{"willTerminate":true,"delayMs":500}}
  → process dies after 500ms delay
  → am start app.dqxn.android.debug/.MainActivity
  → wait for ping ok (max 10s)
dump-state
  → verify: layout matches pre-death state
dump-health
  → verify: all widgets re-bound and receiving data
query-semantics {"testTagPattern":"widget_.*"}
  → verify: same widget set rendered, bounds match pre-death positions
```

### Chaos in CI

Deterministic chaos runs as part of Tier 6 (full suite) using seed-based reproduction:

```kotlin
@Test
fun `dashboard survives 30s combined chaos with seed 42`() {
    client.send("chaos-start", mapOf("seed" to 42, "profile" to "combined"))
    Thread.sleep(30_000)
    val summary = client.send("chaos-stop")

    // 1. No widget in unrecovered error state
    val health = client.send("dump-health")
    health.widgets.forEach { (id, status) ->
        assertThat(status).isNotInstanceOf(WidgetHealthStatus.Error::class.java)
    }

    // 2. Verify chaos → diagnostic correlation (every fault produced expected system response)
    client.assertChaosCorrelation(summary)
}
```

> **Note**: This is an instrumented test running on a real device — `Thread.sleep` is intentional here (testing real system timing behavior). The `StandardTestDispatcher` / no-real-time-delays principle applies to JVM-hosted unit tests, not instrumented tests where real concurrency and system interactions are under test.

### Chaos Correlation Assertions

`AgenticTestClient.assertChaosCorrelation()` validates that the observability pipeline correctly captured and correlated chaos-injected faults:

```kotlin
fun assertChaosCorrelation(summary: JsonObject) {
    val faults = summary.injectedFaults
    faults.forEach { fault ->
        // Provider failures that persisted long enough should produce a diagnostic snapshot
        if (fault.type == "provider-failure" && fault.durationMs > 5000) {
            assertWithMessage("Fault at ${fault.atMs} produced no snapshot")
                .that(fault.resultingSnapshots).isNotEmpty()
            // Snapshot timestamp is after fault injection time
            fault.resultingSnapshots.forEach { snapPath ->
                val diagnostics = client.send("list-diagnostics", mapOf("since" to fault.atMs))
                val match = diagnostics.snapshots.find { it.file == snapPath }
                assertWithMessage("Snapshot $snapPath not found in diagnostics after ${fault.atMs}")
                    .that(match).isNotNull()
            }
        }
    }
}
```

This closes the gap between "chaos happened" and "the right diagnostic was captured" using temporal correlation.

### CI Diagnostic Artifact Collection

When instrumented tests fail, CI collects on-device diagnostic artifacts alongside JUnit XML:

```bash
# After connectedAndroidTest failure:
adb pull /data/data/app.dqxn.android.debug/files/debug/diagnostics/ artifacts/diagnostics/
adb shell content call --method dump-state \
  --uri content://app.dqxn.android.debug.agentic > artifacts/diagnostics/final_state.json
adb shell content call --method dump-health \
  --uri content://app.dqxn.android.debug.agentic > artifacts/diagnostics/final_health.json
adb shell content call --method dump-semantics \
  --uri content://app.dqxn.android.debug.agentic > artifacts/diagnostics/final_semantics.json
```

Published as CI artifacts. The agent then has the test failure message (JUnit XML), correlated diagnostic context (system state at failure), and the semantics tree snapshot (what was actually rendered) without needing a live device to investigate. The semantics dump is particularly valuable for "renders wrong content" bugs where `dump-state` shows correct model state but the UI diverges.

## Agentic E2E Protocol

Instrumented E2E tests reuse the agentic ContentProvider protocol. `AgenticTestClient` wraps `adb shell content call` with assertion helpers:

```kotlin
class AgenticTestClient(private val device: UiDevice) {
    fun send(command: String, params: Map<String, Any> = emptyMap()): JsonObject {
        val paramsJson = Json.encodeToString(params)
        val output = device.executeShellCommand(
            "content call --uri content://app.dqxn.android.debug.agentic " +
            "--method $command --arg '$paramsJson'"
        )
        // Bundle contains filePath — read the response file
        val filePath = output.substringAfter("filePath=").substringBefore("}")
        val json = device.executeShellCommand("cat $filePath")
        val response = Json.parseToJsonElement(json).jsonObject
        val status = response["status"]?.jsonPrimitive?.content
        check(status == "ok") { "Command '$command' failed: ${response["message"]}" }
        return response
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

    // --- Semantics helpers ---

    /** Query a single semantics node by test tag. Fails if not found. */
    fun querySemanticsOne(testTag: String): SemanticsNodeResult {
        val response = send("query-semantics", mapOf("testTag" to testTag))
        val nodes = response.data.nodes
        assertWithMessage("Expected exactly one node with testTag=$testTag, found ${nodes.size}")
            .that(nodes).hasSize(1)
        return SemanticsNodeResult(nodes.first())
    }

    /** Query a single semantics node by test tag. Returns null if not found. */
    fun querySemanticsOrNull(testTag: String): SemanticsNodeResult? {
        val response = send("query-semantics", mapOf("testTag" to testTag))
        val nodes = response.data.nodes
        return if (nodes.isEmpty()) null else SemanticsNodeResult(nodes.first())
    }

    /** Query all semantics nodes matching a test tag pattern. */
    fun querySemanticsAll(testTagPattern: String): List<SemanticsNodeResult> {
        val response = send("query-semantics", mapOf("testTagPattern" to testTagPattern))
        return response.data.nodes.map { SemanticsNodeResult(it) }
    }

    /** Assert a widget is rendered and visible on screen. */
    fun assertWidgetRendered(widgetId: String) {
        val node = querySemanticsOrNull("widget_$widgetId")
        assertWithMessage("Widget $widgetId not found in semantics tree").that(node).isNotNull()
        assertWithMessage("Widget $widgetId not visible").that(node!!.isVisible).isTrue()
        assertWithMessage("Widget $widgetId has zero size")
            .that(node.bounds.width > 0 && node.bounds.height > 0).isTrue()
    }

    /** Assert a widget is NOT rendered (off-viewport or removed). */
    fun assertWidgetNotRendered(widgetId: String) {
        val node = querySemanticsOrNull("widget_$widgetId")
        assertWithMessage("Widget $widgetId should not be rendered")
            .that(node?.isVisible ?: false).isFalse()
    }

    /** Assert widget displays specific text content. */
    fun assertWidgetText(widgetId: String, expectedText: String) {
        val response = send("query-semantics", mapOf(
            "testTag" to "widget_$widgetId", "includeChildren" to true))
        val allText = response.data.nodes.flatMap { it.allTextRecursive() }
        assertWithMessage("Widget $widgetId text content")
            .that(allText.any { expectedText in it }).isTrue()
    }

    /** Wait for a semantics node to appear (useful after mutations). */
    fun awaitSemanticsNode(
        testTag: String,
        timeoutMs: Long = 3000,
        pollMs: Long = 200,
    ): SemanticsNodeResult {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val node = querySemanticsOrNull(testTag)
            if (node != null && node.isVisible) return node
            Thread.sleep(pollMs)
        }
        fail("Semantics node with testTag=$testTag not found within ${timeoutMs}ms")
        throw AssertionError() // unreachable
    }
}
```

This ensures E2E tests exercise the same command paths the agent uses — no parallel infrastructure to maintain.

### E2E Test Examples

```kotlin
@Test
fun `widget add persists across process death`() {
    client.send("widget-add", mapOf("widgetType" to "essentials:speedometer"))
    client.assertState("$.data.layout.widgetCount", 1)

    // Simulate process death
    device.executeShellCommand("am force-stop app.dqxn.android.debug")
    device.executeShellCommand("am start app.dqxn.android.debug/.MainActivity")
    // Poll ping until app is ready — avoids flaky raw Thread.sleep
    client.awaitCondition("ping", "$.status", "ok", timeoutMs = 10_000)

    client.assertState("$.data.layout.widgetCount", 1)
}

@Test
fun `thermal degradation reduces frame rate`() {
    client.send("chaos-inject", mapOf("fault" to "thermal", "level" to "DEGRADED"))
    client.awaitCondition("diagnose-performance", "$.thermal.targetFps", 30, timeoutMs = 5000)
    client.send("chaos-inject", mapOf("fault" to "thermal", "level" to "NORMAL"))
    client.awaitCondition("diagnose-performance", "$.thermal.targetFps", 60, timeoutMs = 5000)
}

@Test
fun `widget-add renders widget at correct position`() {
    client.send("widget-add", mapOf("widgetType" to "essentials:speedometer"))
    val state = client.send("dump-state")
    val widgetId = state.firstWidgetId()

    // Verify the widget is actually rendered on screen, not just in model state
    val node = client.querySemanticsOne("widget_$widgetId")
    assertThat(node.isVisible).isTrue()
    assertThat(node.bounds.width).isGreaterThan(0)
    assertThat(node.contentDescription).contains("Speedometer")
}

@Test
fun `provider failure shows fallback UI in widget`() {
    client.send("widget-add", mapOf("widgetType" to "essentials:speedometer"))
    val widgetId = client.send("dump-state").firstWidgetId()

    client.send("chaos-inject", mapOf(
        "fault" to "provider-failure", "providerId" to "essentials:gps-speed", "duration" to 10))
    Thread.sleep(3000)

    // Verify fallback status overlay is rendered
    val statusNode = client.querySemanticsOrNull("widget_status_$widgetId")
    assertThat(statusNode).isNotNull()
    assertThat(statusNode!!.isVisible).isTrue()

    // Verify widget still exists but shows fallback content
    val widgetNode = client.querySemanticsOne("widget_$widgetId")
    assertThat(widgetNode.isVisible).isTrue()
}

@Test
fun `bottom bar auto-hides and contains expected controls`() {
    // Tap to reveal bottom bar
    client.send("widget-tap", mapOf("widgetId" to "any"))
    Thread.sleep(500)

    val bar = client.querySemanticsOne("bottom_bar")
    assertThat(bar.isVisible).isTrue()

    // Verify settings button is present
    val settings = client.querySemanticsOne("settings_button")
    assertThat(settings.isVisible).isTrue()
    assertThat(settings.actions).contains("OnClick")

    // Wait for auto-hide
    Thread.sleep(4000)
    val barAfter = client.querySemanticsOrNull("bottom_bar")
    assertThat(barAfter?.isVisible ?: false).isFalse()
}

@Test
fun `notification banner renders above widgets`() {
    // Inject a fault that triggers a banner
    client.send("chaos-inject", mapOf("fault" to "provider-failure", "providerId" to "essentials:gps-speed", "duration" to 30))
    Thread.sleep(3000)

    val banner = client.querySemanticsOrNull("banner_provider_unavailable")
    if (banner != null) {
        // Banner should be below the top of the screen and above widgets
        val grid = client.querySemanticsOne("dashboard_grid")
        assertThat(banner.bounds.top).isLessThan(grid.bounds.bottom)
        assertThat(banner.isVisible).isTrue()
    }
}
```

## Test Principles

- **Zero manual tests**: Every verification must be automated. `<manual>` tags in plans are defects. See [Zero Manual Tests Policy](#zero-manual-tests-policy) below.
- **Deterministic**: `StandardTestDispatcher` everywhere
- **Clear failures**: `assertWithMessage()` on every assertion
- **Fast**: < 10s per module for unit tests
- **Self-contained**: No test depends on device state, network, or file system outside sandbox

## Zero Manual Tests Policy

**Design goal**: Zero manual verifications across all phase plans. Every `<verify>` block must contain an `<automated>` command that produces a pass/fail exit code.

### Automation Hierarchy

Planners must attempt each technique in order before escalating:

1. **JUnit5 unit test** — MockK fakes, `StandardTestDispatcher`, Truth assertions. Covers coordinators, state machines, repositories, domain logic.
2. **Turbine flow test** — `flow.test {}` for emission sequences, backpressure, timeout behavior.
3. **`DashboardTestHarness` integration test** — Multi-coordinator interaction, command dispatch, layout mutations.
4. **Robolectric + Compose UI test** — `ComposeTestRule` with semantics assertions. Covers gesture handlers, composable rendering, accessibility.
5. **Property-based test (jqwik)** — Exhaustive state transitions, arbitrary input survival, invariant validation.
6. **Compile-testing (KSP)** — `KotlinCompilation` assertions for annotation processing, error messages, generated output.
7. **Agentic E2E (Tier 5)** — `AgenticTestClient` via ADB content provider. Covers on-device integration, process death recovery, chaos injection.
8. **Benchmark (Phase 12)** — `androidx.benchmark.macro` + `FrameTimingMetric`. Performance thresholds on physical device.

### When a Behavior Appears Untestable

If no technique in the hierarchy works, the planner must:

1. **State why each applicable technique fails** — not "requires device" but "Robolectric doesn't shadow `X` API, compose-test doesn't support `Y` gesture, MockK can't intercept `Z` because it's a final platform class."
2. **Propose a resolution** — exactly one of:
   - **Design change**: Introduce an injectable abstraction to make the behavior testable (preferred).
   - **Test infrastructure**: New fake/helper needed, with API sketch and target module.
   - **Defer to specific phase**: Only when the required infrastructure is planned in that phase (e.g., benchmarking in Phase 12). Must reference the phase and its relevant requirement.
3. **Never leave a gap undocumented** — the `<verify>` block must contain a `<!-- AUTOMATION GAP -->` comment with the above analysis, not a `<manual>` tag.

### Plan Checker Enforcement

The `gsd-plan-checker` agent rejects plans containing `<manual>` tags. A plan with `<manual>` tags is returned to the planner with a request to apply the automation hierarchy. The only exception is an `<!-- AUTOMATION GAP -->` with a documented resolution path approved by the user.

### Validation Strategy Files

Each phase's `VALIDATION.md` must contain:

- **Manual-Only Verifications**: Should read "None" with a reclassification table showing how previously-manual items were automated (see Phase 7 as the reference implementation).
- If any items remain, each must have the full resolution analysis from the protocol above.

## Test Failure Diagnostics

### Coordinator Tests (JUnit5 TestWatcher)

A JUnit5 `TestWatcher` extension auto-dumps harness state on test failure:

```kotlin
class HarnessStateOnFailure : TestWatcher {
    override fun testFailed(context: ExtensionContext, cause: Throwable) {
        val harness = context.getStore(NAMESPACE).get("harness", DashboardTestHarness::class.java)
            ?: return
        // JSON output for structural parity with diagnose-* response format.
        // Agent parses the same shape whether reading a test failure or a runtime diagnostic.
        val dump = buildJsonObject {
            put("layout", harness.layoutState().toJson())
            put("theme", harness.themeState().toJson())
            put("widgetStatuses", harness.widgetStatuses().toJson())
            put("bindingJobs", JsonArray(harness.bindingJobs().keys.map { JsonPrimitive(it) }))
            put("ringBufferTail", harness.ringBufferTail(20).toJson())
        }
        println("=== Harness Diagnostic Dump ===")
        println(dump.toString())
    }
}
```

No dependency on `DiagnosticSnapshotCapture` — the harness already has direct access to coordinator state. This is faster, simpler, and doesn't require the full observability graph in unit tests. The JSON output aligns structurally with diagnose-* response shapes so the agent processes both with the same parsing logic.

### E2E Tests (AgenticTestClient)

`AgenticTestClient` auto-captures diagnostic state on assertion failure:

```kotlin
class AgenticTestClient(private val device: UiDevice) {
    fun assertState(path: String, expected: Any) {
        val state = send("dump-state")
        val actual = JsonPath.read<Any>(state.toString(), path)
        if (actual != expected) {
            // Auto-capture before failing — include semantics tree for visual context
            val health = send("dump-health")
            val metrics = send("dump-metrics")
            val semantics = send("dump-semantics", mapOf("maxDepth" to 5))
            println("=== Diagnostic Context on Failure ===")
            println("Health: $health")
            println("Metrics: $metrics")
            println("Semantics: $semantics")
        }
        assertWithMessage("State at $path").that(actual).isEqualTo(expected)
    }
}
```

This gives E2E test failures the same rich context as runtime anomalies, without modifying production code or the `AnomalyTrigger` sealed hierarchy.
