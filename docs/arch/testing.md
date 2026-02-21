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
- **Android library modules**: `pl.droidsonroids.pitest` (v0.2.25+) — AGP 9 compatibility unverified as of Feb 2026
- `pitest-kotlin` extension with both plugins to filter Kotlin-specific false positives
- Kill rate target: > 80%

## Fuzz Testing

kotlinx.fuzz (JetBrains, built on Jazzer). JVM-only. Targets:
- JSON theme parsing (malformed gradients, missing/extra fields)
- JSON preset parsing
- Proto DataStore deserialization with corrupted bytes

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

**Verification After Fix**: Re-run the failing tier before proceeding. On success, continue from next tier.

## Test Principles

- **Deterministic**: `StandardTestDispatcher` everywhere
- **Clear failures**: `assertWithMessage()` on every assertion
- **Fast**: < 10s per module for unit tests
- **Self-contained**: No test depends on device state, network, or file system outside sandbox
