# Phase 9: Themes, Demo + Chaos - Research

**Researched:** 2026-02-25
**Domain:** Pack modules (themes, demo), chaos testing infrastructure, agentic command handlers
**Confidence:** HIGH

## Summary

Phase 9 has three independent work streams: (1) a themes pack delivering 22 premium JSON-driven themes via `ThemeProvider`, (2) a demo pack delivering 8 deterministic stub providers for all essentials snapshot types, and (3) a chaos testing infrastructure in `:core:agentic` with `ChaosProviderInterceptor`, `ChaosEngine`, and 3 agentic command handlers (`chaos-start`, `chaos-stop`, `chaos-inject`).

All three streams build on well-established patterns from Phase 8 (pack structure, provider contracts, Hilt multibinding) and Phase 6 (agentic command handlers). The themes pack is the simplest -- port 22 JSON files, create a `ThemeProvider`, wire via Hilt. The demo pack requires 8 providers that emit deterministic data on fixed timelines against existing `DataSnapshot` subtypes in `:pack:essentials:snapshots`. The chaos infrastructure is the most novel -- `ChaosProviderInterceptor` implements the existing `DataProviderInterceptor` interface (already wired into `WidgetDataBinder`), and `ChaosEngine` orchestrates seed-based deterministic fault sequences.

Key constraint: the old theme JSON format (`gradients.background.stops` as simple string arrays, `colors.highlight`) differs from the new `ThemeFileSchema` (`GradientStopSchema` with `{color, position}` objects, `colors.background/surface/onSurface`). The 22 theme JSON files must be migrated to the new schema. The old `DemoSimulator` uses `Random` (non-deterministic) -- the new demo providers must be fully deterministic (tick-based, no randomness).

**Primary recommendation:** Split into 4-5 focused plans: (1) theme JSON migration + ThemesPackThemeProvider, (2) demo providers, (3) ChaosProviderInterceptor + StubEntitlementManager extension, (4) ChaosEngine + agentic handlers, (5) chaos correlation tests. sg-erp2 deferred until Phase 10 delivers SetupSheet UI.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F6.1 | 22 premium themes (Cyberpunk, Aurora, Tron, Void, Carbon, Ocean Breeze, etc.) | Theme JSON files exist in old codebase at `dqxn.old/android/feature/packs/themes/src/main/resources/themes/`. Must migrate to new `ThemeFileSchema` format. `ThemeJsonParser` in `:core:design` handles parsing. Themes pack uses `ThemeProvider` interface + Hilt `@IntoSet`. |
| F6.2 | Theme Studio access (create/edit custom themes) | Theme Studio is Phase 11 UI work. Phase 9 only needs themes pack `requiredAnyEntitlement = setOf("themes")` on all 22 themes. SOLAR_AUTO and ILLUMINANCE_AUTO modes also gated behind `themes` entitlement. |
| F6.3 | SOLAR_AUTO mode (sunrise/sunset theme switching) | `ThemeAutoSwitchEngine` already exists in `:core:design` (Phase 5). The mode is already defined. Phase 9's role: ensure themes pack entitlement gating includes SOLAR_AUTO. No new code needed for the mode itself. |
| F6.4 | ILLUMINANCE_AUTO mode (ambient light sensor threshold) | Same as F6.3 -- mode already exists in `ThemeAutoSwitchEngine`. Phase 9 ensures entitlement gating. |
| F8.5 | Debug "Simulate Free User" toggle | `StubEntitlementManager` exists in `:app`. Phase 9 extends it with `simulateRevocation(id)` and `simulateGrant(id)` for programmatic chaos testing. The toggle UI is Phase 10/11. |
| F13.1 | Demo pack with simulated providers for all data types | 8 deterministic providers matching essentials snapshot types: Time, Speed, Orientation, Solar, AmbientLight, Battery, Acceleration, SpeedLimit. `ProviderPriority.SIMULATED`. |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `dqxn.pack` convention plugin | N/A | Auto-wires `:sdk:*` deps for pack modules | Already used by `:pack:essentials` |
| `ThemeJsonParser` (`:core:design`) | N/A | Parses theme JSON to `DashboardThemeDefinition` | Already built and tested (Phase 5) |
| `ThemeProvider` (`:sdk:contracts`) | N/A | Pack theme registration interface | Used by `EssentialsThemeProvider` |
| `DataProviderInterceptor` (`:sdk:contracts`) | N/A | Flow interception for chaos | Already wired into `WidgetDataBinder` |
| `ProviderFault` (`:sdk:contracts`) | N/A | Fault type sealed hierarchy | 7 variants, already used by `TestDataProvider` |
| `CommandHandler` (`:core:agentic`) | N/A | Agentic command interface | 16+ handlers already exist |
| kotlinx.serialization-json | (from catalog) | JSON parsing for themes | Already in version catalog |
| kotlinx-collections-immutable | (from catalog) | Immutable lists for theme stops | Already in use project-wide |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit5 + Truth | (from catalog) | Unit testing | All test files |
| MockK | (from catalog) | Mocking | ChaosEngine tests, handler tests |
| Turbine | (from catalog) | Flow testing | Demo provider emission tests, chaos flow tests |
| `StandardTestDispatcher` | (from catalog) | Virtual time | All flow-based tests |

### Alternatives Considered

None. All technology choices are locked by the existing architecture. No new dependencies needed.

## Architecture Patterns

### Recommended Project Structure

```
pack/themes/
  src/main/
    kotlin/app/dqxn/android/pack/themes/
      ThemesPackThemeProvider.kt       # ThemeProvider impl
      ThemesThemeModule.kt             # Hilt @Binds @IntoSet
    resources/themes/                  # 22 .theme.json files
  src/test/
    kotlin/app/dqxn/android/pack/themes/
      ThemesPackThemeProviderTest.kt   # Contract + JSON validation
      ThemeJsonValidationTest.kt       # Per-file structural validation

pack/demo/
  src/main/
    kotlin/app/dqxn/android/pack/demo/
      providers/
        DemoTimeProvider.kt            # @DashboardDataProvider
        DemoSpeedProvider.kt
        DemoOrientationProvider.kt
        DemoSolarProvider.kt
        DemoAmbientLightProvider.kt
        DemoBatteryProvider.kt
        DemoAccelerationProvider.kt
        DemoSpeedLimitProvider.kt
      simulation/
        DemoSimulator.kt               # Tick-based deterministic engine
      DemoModule.kt                    # Hilt module (if KSP doesn't cover all)
  src/test/
    kotlin/app/dqxn/android/pack/demo/
      providers/
        Demo*ProviderContractTest.kt   # Extend DataProviderContractTest
        DemoSimulatorTest.kt           # Determinism verification

core/agentic/src/main/              (already exists)
  kotlin/app/dqxn/android/core/agentic/
    chaos/
      ChaosProviderInterceptor.kt      # DataProviderInterceptor impl
      ChaosEngine.kt                   # Seed-based fault orchestration
      ChaosProfile.kt                  # 7 profile definitions
      ChaosSession.kt                  # Session state tracking

app/src/debug/
  kotlin/app/dqxn/android/debug/handlers/
    ChaosStartHandler.kt
    ChaosStopHandler.kt
    ChaosInjectHandler.kt
```

### Pattern 1: Pack ThemeProvider (verbatim from EssentialsThemeProvider)

**What:** A `ThemeProvider` that loads JSON theme files from resources and returns `DashboardThemeDefinition` instances.
**When to use:** Every theme pack module.
**Critical difference from essentials:** Themes pack cannot use `ThemeJsonParser` from `:core:design` because packs cannot depend on `:core:*`. Must use an inline approach like `EssentialsThemeProvider` did with inline `DashboardThemeDefinition` construction -- OR load JSON at runtime using `kotlinx.serialization` (which packs do have access to via `:sdk:*` transitive deps).

**Resolution:** The themes pack must include its own lightweight JSON parsing or construct `DashboardThemeDefinition` objects inline. The simplest approach: read JSON resources, deserialize to a pack-local schema type, convert to `DashboardThemeDefinition`. The `ThemeFileSchema` and `parseHexColor` live in `:core:design` which packs cannot import. **Two options:**

1. **Inline construction** (like essentials): Hardcode all 22 themes as Kotlin `DashboardThemeDefinition` objects. Verbose but zero parsing overhead.
2. **Pack-local parser**: Duplicate the minimal `ThemeFileSchema` + `parseHexColor` in the themes pack. ~50 lines. Enables JSON resource loading.

**Recommendation:** Option 2 (pack-local parser) -- keeps JSON files as source of truth, reduces error surface of manually transcribing 22 themes with hex colors and gradient stops.

```kotlin
// pack/themes: ThemesPackThemeProvider.kt
@Singleton
public class ThemesPackThemeProvider @Inject constructor() : ThemeProvider {
  override val packId: String = "themes"
  override fun getThemes(): List<ThemeSpec> = loadedThemes

  private val loadedThemes: List<DashboardThemeDefinition> by lazy {
    THEME_FILES.mapNotNull { filename ->
      val json = javaClass.classLoader!!.getResourceAsStream("themes/$filename")
        ?.bufferedReader()?.readText() ?: return@mapNotNull null
      parseThemeJson(json)
    }
  }
}
```

### Pattern 2: Deterministic Demo Provider

**What:** Providers that emit predictable data sequences based on tick count, not wall-clock or randomness.
**When to use:** Demo pack -- every provider must produce identical output given the same start conditions.
**Key difference from old DemoSimulator:** Old used `Random.nextFloat()` and `Random.nextInt()` (non-deterministic). New must use tick-based cycles only.

```kotlin
// DemoSpeedProvider.kt
@DashboardDataProvider(
  sourceId = "demo:speed",
  displayName = "Demo Speed",
  dataType = "speed",
)
@Singleton
public class DemoSpeedProvider @Inject constructor() : DataProvider<SpeedSnapshot> {
  override val snapshotType: KClass<SpeedSnapshot> = SpeedSnapshot::class
  override val priority: ProviderPriority = ProviderPriority.SIMULATED

  override fun provideState(): Flow<SpeedSnapshot> = flow {
    var tick = 0
    while (true) {
      // Triangle wave: 0-140 km/h over 48 ticks (9.6s at 200ms)
      val phase = tick % 48
      val speedKmh = if (phase < 24) phase * (140.0 / 24.0) else (48 - phase) * (140.0 / 24.0)
      val speedMps = (speedKmh / 3.6).toFloat()
      emit(SpeedSnapshot(speedMps = speedMps, accuracy = 1.0f, timestamp = System.nanoTime()))
      delay(200)
      tick++
    }
  }
}
```

### Pattern 3: ChaosProviderInterceptor (DataProviderInterceptor implementation)

**What:** Intercepts provider flows and applies `ProviderFault` transformations.
**When to use:** Debug builds only. Registered in Hilt `@IntoSet` for `DataProviderInterceptor`.
**Key insight:** `WidgetDataBinder` already iterates `interceptors: Set<DataProviderInterceptor>` and applies them to every provider flow. Production builds have an empty set (zero overhead). Debug builds register `ChaosProviderInterceptor`.

```kotlin
// core/agentic: ChaosProviderInterceptor.kt
@Singleton
public class ChaosProviderInterceptor @Inject constructor() : DataProviderInterceptor {
  private val activeFaults = ConcurrentHashMap<String, ProviderFault>()

  public fun injectFault(providerId: String, fault: ProviderFault) {
    activeFaults[providerId] = fault
  }

  public fun clearFault(providerId: String) { activeFaults.remove(providerId) }
  public fun clearAll() { activeFaults.clear() }

  override fun <T : DataSnapshot> intercept(
    provider: DataProvider<T>,
    upstream: Flow<T>,
  ): Flow<T> {
    val fault = activeFaults[provider.sourceId] ?: return upstream
    return applyFault(upstream, fault) // Same transformLatest pattern as TestDataProvider
  }
}
```

### Pattern 4: ChaosEngine (Seed-Based Deterministic Orchestration)

**What:** Orchestrates timed fault sequences from a seed-based `Random` for deterministic reproduction.
**When to use:** `chaos-start` with a seed. Same seed produces same fault sequence.

```kotlin
@Singleton
public class ChaosEngine @Inject constructor(
  private val interceptor: ChaosProviderInterceptor,
  private val entitlementManager: EntitlementManager,
  // thermal injection via existing simulate-thermal path
) {
  private var session: ChaosSession? = null

  public fun start(seed: Long, profile: String, durationMs: Long?): ChaosSession {
    val random = Random(seed)
    // Profile determines fault types + timing distribution
    // Random determines exact targets + intervals from that distribution
  }
}
```

### Anti-Patterns to Avoid

- **Packs importing `:core:design`**: Themes pack CANNOT use `ThemeJsonParser`. Must self-contain parsing logic.
- **Non-deterministic demo data**: Old `DemoSimulator` used `Random` -- new must use tick-based sequences only.
- **`ChaosProviderInterceptor` in production**: Must be debug-only, registered via `DebugModule` Hilt binding.
- **`StubEntitlementManager` with mutable `activeEntitlements` as immutable `Set`**: The existing field is `private val`. Extension requires making the backing store mutable (`MutableStateFlow`).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Theme JSON parsing | Full theme parser in pack | Pack-local `ThemeFileSchema` + `parseHexColor` (~50 lines) + `kotlinx.serialization` | Reuse existing schema shape, minimize code |
| Provider flow interception | Custom flow wrapping | `DataProviderInterceptor` interface + `WidgetDataBinder` integration | Already wired, zero production overhead |
| Fault transformations | Custom fault logic | `ProviderFault` sealed hierarchy + `TestDataProvider.transformLatest` pattern | Proven in Phase 2 testFixtures |
| Deterministic random | Custom PRNG | `kotlin.random.Random(seed: Long)` | Standard library, cross-platform deterministic |
| Theme entitlement gating | Custom gating logic | `requiredAnyEntitlement = setOf("themes")` on each `DashboardThemeDefinition` | `Gated` interface already enforced by shell |

**Key insight:** Nearly every building block already exists. This phase is integration, not invention.

## Common Pitfalls

### Pitfall 1: Theme JSON Schema Mismatch
**What goes wrong:** Old theme JSONs have `colors.highlight`, `gradients.background.stops` as string arrays with implicit even spacing. New `ThemeFileSchema` expects `colors.background/surface/onSurface` and `GradientStopSchema` with explicit `{color, position}`.
**Why it happens:** The architecture evolved between old and new codebase. `ThemeJsonParser` expects the new format.
**How to avoid:** Migrate all 22 JSON files to new schema format before loading. Map: old `highlight` stays as separate field on `DashboardThemeDefinition`, old gradient stops become `{color: "#xxx", position: 0.0/0.5/1.0}` with evenly distributed positions, add `background`/`surface`/`onSurface` fields derived from gradient stops or as explicit values.
**Warning signs:** `ThemeJsonParser.parse()` returns null for a theme file.

### Pitfall 2: Pack Cannot Import ThemeJsonParser
**What goes wrong:** Themes pack tries to use `:core:design` `ThemeJsonParser` and fails module boundary lint check.
**Why it happens:** Packs depend only on `:sdk:*` and `:pack:*:snapshots`. `:core:design` is off-limits.
**How to avoid:** Create a pack-local `ThemeFileParser` with the minimal deserialization logic. Alternatively, construct `DashboardThemeDefinition` objects inline (verbose but dependency-free).
**Warning signs:** `ModuleBoundaryViolation` lint error.

### Pitfall 3: Non-Deterministic Demo Providers
**What goes wrong:** Demo providers use `System.currentTimeMillis()` or `Random()` (unseeded), producing different data on each run. CI E2E tests with demo data become flaky.
**Why it happens:** Copy-pasting from old `DemoSimulator` which used `Random.nextFloat()`.
**How to avoid:** Use tick-counting loops with pure math (sine waves, triangle waves, modulo cycling). Only `System.nanoTime()` for `timestamp` field (required by `DataSnapshot`). No `Random` in any provider's `provideState()`.
**Warning signs:** Same test produces different values across runs.

### Pitfall 4: ChaosProviderInterceptor in Release Builds
**What goes wrong:** `ChaosProviderInterceptor` ends up in the release dependency graph, adding dead code and potential security surface.
**Why it happens:** Registering it in `AppModule` instead of `DebugModule`.
**How to avoid:** Register `ChaosProviderInterceptor` via Hilt `@IntoSet` ONLY in `DebugModule` (`:app/src/debug/`). `AppModule.dataProviderInterceptors()` `@Multibinds` provides the empty set in release builds.
**Warning signs:** R8 report shows `ChaosProviderInterceptor` in release APK.

### Pitfall 5: StubEntitlementManager MutableStateFlow Emission
**What goes wrong:** `simulateRevocation()` / `simulateGrant()` don't trigger `entitlementChanges` flow emission because the backing `MutableStateFlow` value is the same object reference after mutation.
**Why it happens:** Mutating a `MutableSet` in place and re-assigning to `MutableStateFlow.value` -- `StateFlow` uses structural equality, but `MutableSet` identity can confuse things.
**How to avoid:** Always create a new `Set` instance on mutation: `_entitlements.value = _entitlements.value.toMutableSet().apply { remove(id) }.toSet()`.
**Warning signs:** `entitlementChanges` flow doesn't emit after `simulateRevocation()`.

### Pitfall 6: ChaosEngine Session Scope Leak
**What goes wrong:** Chaos session launches coroutines that outlive the session, continuing to inject faults after `chaos-stop`.
**Why it happens:** Not cancelling the session's `CoroutineScope` on stop.
**How to avoid:** Each `ChaosSession` gets a child `Job` of the engine's scope. `stop()` cancels the Job and clears all active faults via `interceptor.clearAll()`.
**Warning signs:** Faults continue after `chaos-stop` returns.

## Code Examples

### Theme JSON (New Format)

Each of the 22 theme files must conform to this schema:

```json
{
  "id": "cyberpunk",
  "name": "Cyberpunk",
  "isDark": true,
  "colors": {
    "primary": "#00F0FF",
    "secondary": "#80FFFFFF",
    "accent": "#F0F000",
    "background": "#0F0C29",
    "surface": "#24243E",
    "onSurface": "#8000F0FF"
  },
  "backgroundGradient": {
    "type": "VERTICAL",
    "stops": [
      { "color": "#FF0F0C29", "position": 0.0 },
      { "color": "#FF302B63", "position": 0.5 },
      { "color": "#FF24243E", "position": 1.0 }
    ]
  },
  "widgetBackgroundGradient": {
    "type": "LINEAR",
    "stops": [
      { "color": "#40FFFFFF", "position": 0.0 },
      { "color": "#10FFFFFF", "position": 1.0 }
    ]
  },
  "requiredAnyEntitlement": ["themes"]
}
```

Note: `requiredAnyEntitlement` is not part of `ThemeFileSchema` (the parser produces `DashboardThemeDefinition` without it). The pack's `ThemeProvider` must set `requiredAnyEntitlement = setOf("themes")` on each definition post-parse. This mirrors how `EssentialsThemeProvider` sets `requiredAnyEntitlement = null`.

### StubEntitlementManager Extension

```kotlin
public class StubEntitlementManager : EntitlementManager {
  private val _entitlements = MutableStateFlow(setOf("free"))

  override fun hasEntitlement(id: String): Boolean = id in _entitlements.value
  override fun getActiveEntitlements(): Set<String> = _entitlements.value
  override val entitlementChanges: Flow<Set<String>> = _entitlements

  public fun simulateRevocation(id: String) {
    _entitlements.value = _entitlements.value - id
  }

  public fun simulateGrant(id: String) {
    _entitlements.value = _entitlements.value + id
  }

  public fun reset() {
    _entitlements.value = setOf("free")
  }
}
```

### ChaosEngine Fault Scheduling

```kotlin
public class ChaosEngine @Inject constructor(
  private val interceptor: ChaosProviderInterceptor,
  private val providerRegistry: DataProviderRegistry,
  private val logger: DqxnLogger,
) {
  private var activeSession: ChaosSession? = null

  public fun start(seed: Long, profile: String, scope: CoroutineScope): ChaosSession {
    val random = Random(seed)
    val sessionJob = Job(scope.coroutineContext.job)
    val sessionScope = CoroutineScope(scope.coroutineContext + sessionJob)
    val session = ChaosSession(seed = seed, profile = profile, job = sessionJob)
    activeSession = session

    val faultPlan = ChaosProfile.forName(profile).generatePlan(random, providerRegistry)
    sessionScope.launch {
      for (scheduledFault in faultPlan) {
        delay(scheduledFault.delayMs)
        interceptor.injectFault(scheduledFault.providerId, scheduledFault.fault)
        session.recordInjection(scheduledFault)
      }
    }
    return session
  }

  public fun stop(): ChaosSessionSummary {
    val session = activeSession ?: error("No active session")
    session.job.cancel()
    interceptor.clearAll()
    activeSession = null
    return session.toSummary()
  }
}
```

### AgenticTestClient.assertChaosCorrelation

```kotlin
public fun assertChaosCorrelation(summary: JsonObject) {
  val faults = summary.getJsonArray("injected_faults")
  faults.forEach { fault ->
    val faultTimestamp = fault.jsonObject.getLong("at_ms")
    val diagnostics = send("list-diagnostics", mapOf("since" to faultTimestamp))
    val snapshots = diagnostics.getJsonArray("snapshots")
    assertWithMessage("Fault at $faultTimestamp should produce at least one diagnostic snapshot")
      .that(snapshots.size()).isGreaterThan(0)
  }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Random()` in DemoSimulator | Tick-based deterministic sequences | Phase 9 (new) | CI determinism, reproducible screenshots |
| Simple gradient stop arrays | `GradientStopSchema` with explicit positions | Phase 5 (ThemeJsonParser) | Must migrate 22 JSON files |
| Pack importing `:feature:packs:free` | Pack self-contained, `:sdk:*` only | Phase 1 (architecture) | Themes pack needs pack-local parser |
| No chaos infrastructure | `ChaosProviderInterceptor` + `ChaosEngine` | Phase 9 (new) | Seed-based deterministic fault reproduction |

## Open Questions

1. **Theme JSON field mapping for `highlight` color**
   - What we know: Old format has `colors.highlight`, new `ThemeFileSchema` has no `highlight` field (it has `background`, `surface`, `onSurface`). `DashboardThemeDefinition` has `highlightColor` which defaults to `accentColor`.
   - What's unclear: Should the old `highlight` values be preserved as a separate `highlightColor` on the `DashboardThemeDefinition`, or dropped in favor of the default (`accentColor`)?
   - Recommendation: Preserve. Add optional `highlight` field to the pack-local schema. If present, map to `highlightColor` on `DashboardThemeDefinition`. If absent, falls back to `accentColor` via default param.

2. **Theme JSON field mapping for `defaults.backgroundStyle` and `defaults.glowEffect`**
   - What we know: Old JSON has `"defaults": { "backgroundStyle": "SOLID", "glowEffect": false }`. `DashboardThemeDefinition` has `defaultBackgroundStyle` and `defaultHasGlowEffect`.
   - What's unclear: Whether all 22 themes have this field or only some.
   - Recommendation: Include as optional fields in pack-local schema. Default to `BackgroundStyle.SOLID` / `false`.

3. **sg-erp2 pack scope**
   - What we know: Phase 9 roadmap mentions sg-erp2 "contingent on EXTOL SDK compatibility" and depends on Phase 10 for SetupSheet UI.
   - What's unclear: Phase 10 is not yet complete. sg-erp2 requires BLE device pairing UI from Phase 10.
   - Recommendation: Defer sg-erp2 entirely from Phase 9. SC5 ("Connection state machine exhaustive transition tests") only runs "if EXTOL compatible" -- skip it. Focus Phase 9 on themes + demo + chaos.

4. **ChaosProviderInterceptor module location**
   - What we know: Architecture doc says `:core:agentic` (debug only). But `:core:agentic` is currently a non-debug module (all sources in `src/main/`). Packs/features that depend on it use it as `debugImplementation`.
   - What's unclear: Whether `ChaosProviderInterceptor` goes in `:core:agentic` main source set (which is only consumed as debugImplementation by `:app`) or needs to be in `:app/src/debug/`.
   - Recommendation: Place in `:core:agentic` main source set. It's already only consumed via `debugImplementation` by `:app`. The Hilt binding (`@Binds @IntoSet DataProviderInterceptor`) goes in `DebugModule` (`:app/src/debug/`). This keeps the interceptor testable in `:core:agentic` tests.

5. **Demo pack dependency on `:pack:essentials:snapshots`**
   - What we know: Demo providers emit `SpeedSnapshot`, `TimeSnapshot`, etc. from `:pack:essentials:snapshots`. Pack convention plugin allows `:pack:*:snapshots` dependencies.
   - What's unclear: Whether `dqxn.pack` convention plugin auto-wires snapshot dependencies or they need explicit declaration.
   - Recommendation: Demo pack needs `implementation(project(":pack:essentials:snapshots"))` in its `build.gradle.kts`. The `dqxn.pack` plugin auto-wires `:sdk:*` but NOT cross-pack snapshot deps.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit5 + Truth + MockK + Turbine |
| Config file | Convention plugin `dqxn.pack` / `dqxn.android.test` |
| Quick run command | `./gradlew :pack:themes:testDebugUnitTest :pack:demo:testDebugUnitTest --console=plain` |
| Full suite command | `./gradlew test --console=plain` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| F6.1 | 22 premium themes parse correctly | unit | `./gradlew :pack:themes:testDebugUnitTest --tests "*.ThemeJsonValidationTest" --console=plain` | No -- Wave 0 gap |
| F6.1 | Each theme has required fields (themeId, displayName, isDark, colors, gradients) | unit | Same test class | No -- Wave 0 gap |
| F6.1 | All color values are valid hex format | unit | Same test class | No -- Wave 0 gap |
| F6.1 | Gradient stops in 0.0-1.0 range | unit | Same test class | No -- Wave 0 gap |
| F6.2-F6.4 | Themes entitlement gating (all 22 themes have `requiredAnyEntitlement = setOf("themes")`) | unit | `./gradlew :pack:themes:testDebugUnitTest --tests "*.ThemesPackThemeProviderTest" --console=plain` | No -- Wave 0 gap |
| F8.5 | `simulateRevocation(id)` removes from active set and emits via flow | unit | `./gradlew :app:testDebugUnitTest --tests "*.StubEntitlementManagerTest" --console=plain` | Partial (existing tests, needs extension) |
| F8.5 | `simulateGrant(id)` adds to active set and emits via flow | unit | Same test class | No -- Wave 0 gap |
| F13.1 | 8 demo providers pass DataProviderContractTest | contract | `./gradlew :pack:demo:testDebugUnitTest --tests "*.Demo*ContractTest" --console=plain` | No -- Wave 0 gap |
| F13.1 | Demo providers are deterministic (same tick = same output) | unit | `./gradlew :pack:demo:testDebugUnitTest --tests "*.DemoSimulatorTest" --console=plain` | No -- Wave 0 gap |
| SC2 | Demo providers use `ProviderPriority.SIMULATED` | unit | `./gradlew :pack:demo:testDebugUnitTest --tests "*.Demo*ContractTest" --console=plain` | No -- Wave 0 gap |
| SC3 | `ChaosProviderInterceptor` + `inject-fault` produces correlated diagnostic | unit + integration | `./gradlew :core:agentic:testDebugUnitTest --tests "*.ChaosProviderInterceptorTest" --console=plain` | No -- Wave 0 gap |
| SC4 | Deterministic chaos: same seed same sequence | unit | `./gradlew :core:agentic:testDebugUnitTest --tests "*.ChaosEngineTest" --console=plain` | No -- Wave 0 gap |
| SC4 | ChaosEngine profiles produce non-empty fault sequences | unit | Same test class | No -- Wave 0 gap |

### Nyquist Sampling Rate

- **Minimum sample interval:** After every committed task, run: `./gradlew :pack:themes:testDebugUnitTest :pack:demo:testDebugUnitTest :core:agentic:testDebugUnitTest :app:testDebugUnitTest --console=plain`
- **Full suite trigger:** Before merging final task of any plan wave
- **Phase-complete gate:** `./gradlew test --console=plain` all green

### Wave 0 Gaps (must be created before implementation)

- [ ] `pack/themes/src/test/kotlin/.../ThemesPackThemeProviderTest.kt` -- covers F6.1, F6.2-F6.4 entitlement
- [ ] `pack/themes/src/test/kotlin/.../ThemeJsonValidationTest.kt` -- covers F6.1 per-file validation
- [ ] `pack/demo/src/test/kotlin/.../providers/Demo*ContractTest.kt` (8 files) -- covers F13.1
- [ ] `pack/demo/src/test/kotlin/.../DemoSimulatorTest.kt` -- covers determinism SC4
- [ ] `core/agentic/src/test/kotlin/.../chaos/ChaosProviderInterceptorTest.kt` -- covers SC3
- [ ] `core/agentic/src/test/kotlin/.../chaos/ChaosEngineTest.kt` -- covers SC4
- [ ] `app/src/test/kotlin/.../StubEntitlementManagerTest.kt` -- extend existing for F8.5
- [ ] `app/src/test/kotlin/.../debug/handlers/ChaosStartHandlerTest.kt` -- covers handler wiring
- [ ] `app/src/test/kotlin/.../debug/handlers/ChaosInjectHandlerTest.kt` -- covers handler wiring
- [ ] `app/src/test/kotlin/.../debug/handlers/ChaosStopHandlerTest.kt` -- covers handler wiring

## Sources

### Primary (HIGH confidence)

- Codebase inspection: All existing types, interfaces, and patterns verified by reading actual source files
- `/Users/ohm/Workspace/dqxn/android/sdk/contracts/src/main/kotlin/.../DataProviderInterceptor.kt` -- interceptor interface
- `/Users/ohm/Workspace/dqxn/android/sdk/contracts/src/main/kotlin/.../ProviderFault.kt` -- fault sealed hierarchy
- `/Users/ohm/Workspace/dqxn/android/sdk/contracts/src/main/kotlin/.../ThemeProvider.kt` -- theme provider interface
- `/Users/ohm/Workspace/dqxn/android/pack/essentials/src/main/kotlin/.../EssentialsThemeProvider.kt` -- reference pack theme impl
- `/Users/ohm/Workspace/dqxn/android/core/design/src/main/kotlin/.../ThemeJsonParser.kt` -- existing JSON parser
- `/Users/ohm/Workspace/dqxn/android/core/design/src/main/kotlin/.../ThemeSchema.kt` -- new JSON schema types
- `/Users/ohm/Workspace/dqxn/android/app/src/main/kotlin/.../StubEntitlementManager.kt` -- existing stub
- `/Users/ohm/Workspace/dqxn/android/app/src/debug/kotlin/.../DebugModule.kt` -- debug Hilt wiring
- `/Users/ohm/Workspace/dqxn/android/feature/dashboard/src/main/kotlin/.../WidgetDataBinder.kt` -- interceptor consumption
- `.planning/arch/testing.md` -- chaos testing architecture
- `.planning/arch/build-system.md` -- agentic command framework
- `.planning/migration/phase-09.md` -- phase migration details

### Secondary (MEDIUM confidence)

- Old codebase: `/Users/ohm/Workspace/dqxn.old/android/feature/packs/themes/src/main/resources/themes/*.theme.json` -- 22 theme files, need format migration
- Old codebase: `/Users/ohm/Workspace/dqxn.old/android/feature/packs/demo/src/main/java/.../DemoSimulator.kt` -- reference pattern (non-deterministic)
- `.planning/oldcodebase/packs.md` -- old codebase mapping doc

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries and patterns already established in earlier phases
- Architecture: HIGH -- all interfaces exist, patterns proven in Phase 8
- Pitfalls: HIGH -- based on direct source code inspection, not inference
- Theme JSON migration: MEDIUM -- old format verified, new format verified, but exact mapping for all 22 themes untested

**Research date:** 2026-02-25
**Valid until:** 2026-03-27 (stable -- no external dependency changes expected)
