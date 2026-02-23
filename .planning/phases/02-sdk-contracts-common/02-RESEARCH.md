# Phase 2: SDK Contracts + Common - Research

**Researched:** 2026-02-24
**Domain:** Kotlin/Android SDK type surface, sealed hierarchies, testFixtures, `compileOnly` compose.runtime, jqwik property testing on Android
**Confidence:** HIGH

## Summary

Phase 2 delivers the foundational type surface for the entire pack ecosystem. Two modules: `:sdk:common` (Hilt-enabled Android library with dispatchers, result types, connection state machine) and `:sdk:contracts` (Android library with `compileOnly(compose.runtime)`, testFixtures, and the full widget/provider/theme/notification/entitlement contract hierarchy). The phase is entirely greenfield code with old codebase types as reference input — every interface changes signature significantly.

The core architectural transformation is `DataSnapshot` from `Map<String, Any?>` to typed `@DashboardSnapshot` subtypes with `KClass`-keyed `WidgetData`. This cascades through every widget and provider contract. The phase also introduces `ProviderFault` in main source (not testFixtures) for debug-runtime chaos injection, and establishes `WidgetRendererContractTest`/`DataProviderContractTest` abstract bases in testFixtures that every pack widget/provider must extend in Phase 8.

**Primary recommendation:** Build `:sdk:common` first (it has no upstream dependency beyond Phase 1 plugins), then `:sdk:contracts` which depends on it via `api(project(":sdk:common"))`. Write tests alongside types — the test suite is the primary validation mechanism since there's no UI or runtime to exercise yet.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F2.1 | `WidgetRenderer` contract with `@Composable Render()` | WidgetRenderer interface extends WidgetSpec + Gated. `Render()` signature: `(isEditMode, style, settings, modifier)`. Widget data via `LocalWidgetData.current`. `compileOnly(compose.runtime)` provides `@Composable` annotation without Compose compiler. |
| F2.2 | `WidgetSpec` with typeId, display name, compatible data types, settings schema | WidgetSpec interface with typed `compatibleSnapshots: Set<KClass<out DataSnapshot>>`, `settingsSchema: List<SettingDefinition<*>>`, `description: String` (F2.20), `aspectRatio: Float?` (F2.16). |
| F2.4 | `WidgetDataBinder` IoC binding (contracts only) | `WidgetData` multi-slot type with `KClass`-keyed slots. `DataProviderInterceptor` interface. Binder contract is implicit — types enable the Phase 7 implementation. |
| F2.5 | `WidgetStatusCache` overlays | `WidgetRenderState` sealed interface (7 variants), `WidgetIssue`, `WidgetStatusCache` data class. Icon representation changed from `ImageVector` to `String` icon names. |
| F2.10 | `WidgetContext` type | `WidgetContext(timezone: ZoneId, locale: Locale, region: String)` with `DEFAULT = (UTC, US, "US")`. Used by `getDefaults()`. |
| F2.11 | `SettingsAwareSizer` | Interface: `computeSize(settings: Map<String, Any?>): WidgetDefaults`. Drop deprecated `DynamicSizeProvider`. |
| F2.12 | `@DashboardWidget` annotation (KSP processor in Phase 4) | `@DashboardWidget(typeId, displayName, icon)` with `@Retention(SOURCE)`. Also `@DashboardDataProvider`, `@DashboardSnapshot`. Annotations only — no processor code here. |
| F2.16 | Aspect ratio constraint | `aspectRatio: Float?` on `WidgetSpec` (null = freeform). `WidgetDefaults` also carries `aspectRatio`. |
| F2.19 | Widget accessibility | `accessibilityDescription(data: WidgetData): String` on `WidgetRenderer`. Contract test #4/#5 validate non-empty and varying descriptions. |
| F2.20 | `WidgetSpec.description` | `description: String` — one-line widget explanation. Contract test #14 validates non-blank. |
| F3.1 | `DataProvider<T>` contract | Generic `DataProvider<T : DataSnapshot>` extends `DataProviderSpec` + `Gated`. `provideState(): Flow<T>` compiler-enforced. `snapshotType: KClass<T>`, timeouts, connection state flows. |
| F3.2 | `DataSchema` | `DataSchema(fields: List<DataFieldSpec>, stalenessThresholdMs: Long)`. Retained for staleness detection per data type. |
| F3.3 | `SetupPageDefinition` | `SetupPageDefinition(id, title, description?, definitions: List<SetupDefinition>)`. Multi-page setup wizard schema. |
| F3.4 | Setup definition types | `SetupDefinition` sealed interface: 7 subtypes (RuntimePermission, SystemServiceToggle, SystemService, DeviceScan, Instruction, Info, Setting). Icon names replace `ImageVector`, `ServiceType` enum replaces `Context`-dependent lambdas. |
| F3.5 | `SetupEvaluator` | Interface only in Phase 2: `fun evaluate(schema: List<SetupPageDefinition>): SetupResult`. Implementation deferred to Phase 7/10. |
| F3.6 | Connection state/error reporting | `isAvailable: Boolean`, `connectionState: Flow<Boolean>`, `connectionErrorDescription: Flow<String?>` on `DataProvider`. `ConnectionStateMachine` in `:sdk:common`. |
| F3.8 | `@DashboardDataProvider` annotation | `@DashboardDataProvider(localId, displayName, description)` with `@Retention(SOURCE)`. Phase 4 builds processor. |
| F8.1 | Entitlement contracts | `EntitlementManager` interface (minimal V1: `hasEntitlement`, `getActiveEntitlements`, `entitlementChanges`). `Entitlements` constants object. Defer `purchaseProduct()`/`restorePurchases()`. |
| F8.3 | `Gated` interface | `requiredAnyEntitlement: Set<String>?`. OR-logic: any matching entitlement grants access. `isAccessible(hasEntitlement: (String) -> Boolean)` extension function. |
| F9.1 | Connection status notification type | `InAppNotification.Toast` and `InAppNotification.Banner` sealed subtypes. `NotificationPriority` enum. |
| F9.2 | Per-alert mode selection | `AlertProfile` data class with `AlertMode` enum (SILENT/VIBRATE/SOUND). `AlertEmitter` interface. |
| F9.3 | TTS readout | `AlertProfile.ttsMessage: String?` field. Implementation in Phase 7. |
| F9.4 | Custom alert sound URIs | `AlertProfile.soundUri: String?` field. `AlertResult` enum. |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx-coroutines-core | 1.10.2 | `Flow`, `StateFlow`, coroutine primitives | Already in version catalog. Required by `DataProvider.provideState(): Flow<T>` |
| kotlinx-collections-immutable | 0.3.8 | `ImmutableMap`, `ImmutableList`, `persistentMapOf` | Already in version catalog. `WidgetData.snapshots`, notification `actions` |
| kotlinx-serialization-json | 1.10.0 | `@Serializable` on `WidgetStyle`, settings enums | Already in version catalog. Required for layout persistence round-trip |
| compose-runtime (compileOnly) | BOM 2026.02.00 | `@Composable`, `@Immutable`, `@Stable` annotations | compileOnly — no Compose compiler applied |
| javax.inject | via Hilt 2.59.2 | `@Qualifier` annotations in `:sdk:common` | Hilt dependency on `:sdk:common` provides this transitively |

### Testing

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 (Jupiter) | 5.11.4 | Unit tests, `@Tag`, `@Nested` | All unit tests in both modules |
| JUnit 4 (Vintage) | via vintage-engine | `WidgetRendererContractTest` (needs `ComposeContentTestRule`) | Contract tests requiring Compose test rule |
| jqwik | 1.9.3 | Property-based testing | `WidgetData` accumulation, `ConnectionStateMachine` invariants, renderer settings survival |
| Truth | 1.4.4 | Fluent assertions | `assertThat()`, `assertWithMessage()` |
| Turbine | 1.2.0 | Flow testing | `ConnectionStateMachine` state flow tests |
| MockK | 1.13.16 | Mocking | Minimal — most types are concrete data classes |
| kotlinx-coroutines-test | 1.10.2 | `StandardTestDispatcher`, `runTest` | All coroutine-involving tests |
| Robolectric | 4.16.1 | Android framework on JVM | Tests touching Android types (Context in testFixtures) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `compileOnly(compose.runtime)` | Full Compose compiler via `dqxn.android.compose` | Unnecessary — only annotations needed, no composable bodies. Would force full BOM resolution. |
| jqwik for property tests | kotest-property | jqwik is already in version catalog, runs as JUnit5 engine, integrates with existing `de.mannodermaus.android-junit5` setup |
| `Set<String>?` for `Gated` | `List<String>?` | Set enforces uniqueness, OR-logic is cleaner with `any{}` on Set. Old code used List — changed intentionally. |

## Architecture Patterns

### Recommended Package Structure

```
sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/
├── di/                     # @ApplicationScope, dispatchers
├── result/                 # AppResult, AppError, PermissionKind
├── statemachine/           # ConnectionStateMachine, ConnectionEvent, ConnectionMachineState
└── flow/                   # Flow extension utilities

sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/
├── widget/                 # WidgetRenderer, WidgetSpec, WidgetData, WidgetStyle, WidgetContext, WidgetDefaults, WidgetAction
├── provider/               # DataProvider, DataProviderSpec, DataSchema, DataSnapshot, ActionableProvider, DataProviderInterceptor, ProviderPriority
├── setup/                  # SetupDefinition, SetupPageDefinition, SetupEvaluator, ServiceType, VerificationStrategy
├── settings/               # SettingDefinition (12 subtypes), SettingsAwareSizer, InfoCardSettings, settings enums
├── notification/           # InAppNotification, AlertProfile, AlertEmitter, AlertResult, NotificationPriority
├── theme/                  # ThemeSpec, ThemeProvider, AutoSwitchMode
├── entitlement/            # Gated, EntitlementManager, Entitlements
├── status/                 # WidgetRenderState, WidgetIssue, WidgetStatusCache
├── pack/                   # DashboardPackManifest, PackWidgetRef, PackCategory
├── registry/               # WidgetRegistry, DataProviderRegistry (interfaces only)
├── annotation/             # @DashboardWidget, @DashboardDataProvider, @DashboardSnapshot
└── fault/                  # ProviderFault (main source set, not testFixtures)

sdk/contracts/src/testFixtures/kotlin/app/dqxn/android/sdk/contracts/testing/
├── WidgetRendererContractTest.kt       # JUnit4 abstract — 14 assertions
├── WidgetRendererPropertyTest.kt       # JUnit5/jqwik abstract — property-based settings survival
├── DataProviderContractTest.kt         # JUnit5 abstract — 12 assertions
├── TestWidgetRenderer.kt               # Minimal stub implementing WidgetRenderer
├── TestDataProvider.kt                 # Configurable fake with ProviderFault injection
├── TestWidgetScope.kt                  # Test-only WidgetCoroutineScope
└── Factories.kt                        # testWidget(), testTheme(), testDataSnapshot()

sdk/contracts/src/test/kotlin/app/dqxn/android/sdk/contracts/
├── widget/WidgetDataTest.kt
├── widget/WidgetStyleTest.kt
├── widget/WidgetContextTest.kt
├── entitlement/GatedTest.kt
├── settings/SettingDefinitionTest.kt
├── setup/SetupDefinitionTest.kt
├── fault/ProviderFaultTest.kt
└── TestWidgetRendererContractTest.kt   # Concrete subclass — validates abstract base runs
└── TestDataProviderContractTest.kt     # Concrete subclass — validates abstract base runs

sdk/common/src/test/kotlin/app/dqxn/android/sdk/common/
├── result/AppResultTest.kt
└── statemachine/ConnectionStateMachineTest.kt
```

### Pattern 1: compileOnly(compose.runtime) for Annotation-Only Usage

**What:** `:sdk:contracts` needs `@Composable` (for `Render()` signature) and `@Immutable` (for data classes) but must NOT apply the Compose compiler. The standalone `compose-runtime` artifact provides these annotations without triggering compilation.

**When to use:** Any non-UI module that declares `@Composable` function type parameters or uses `@Immutable`/`@Stable` annotations.

**Implementation:**
```kotlin
// sdk/contracts/build.gradle.kts
plugins {
    id("dqxn.android.library")
    id("dqxn.android.test")
    id("java-test-fixtures")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.dqxn.android.sdk.contracts" }

dependencies {
    api(project(":sdk:common"))
    compileOnly(libs.compose.runtime)  // NOT platform(compose-bom) — standalone artifact
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // testFixtures dependencies
    testFixturesImplementation(libs.compose.runtime)  // testFixtures COMPILE the annotation
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter.api)
    testFixturesImplementation(libs.truth)
    testFixturesImplementation(libs.jqwik)
    // JUnit4 for WidgetRendererContractTest (ComposeContentTestRule)
    testFixturesImplementation(libs.compose.ui.test.junit4)
}
```

**Critical detail:** The `compose.runtime` library alias in the version catalog (`compose-runtime`) does NOT carry a version because it's intended for BOM resolution. For `compileOnly`, we need it with an explicit version OR we use `platform(libs.compose.bom)` in a `compileOnly` configuration. The version catalog already has `compose-runtime = { group = "androidx.compose.runtime", name = "runtime" }` without a version. Two approaches:

1. Add a versioned `compose-runtime-standalone` alias to the catalog: `compose-runtime-standalone = { group = "androidx.compose.runtime", name = "runtime", version = "1.8.0" }` (the version from BOM 2026.02.00)
2. Use `compileOnly(platform(libs.compose.bom))` then `compileOnly(libs.compose.runtime)` — but `compileOnly` + `platform()` has Gradle compatibility concerns.

**Recommended:** Verify during implementation which approach resolves cleanly with Gradle 9.3.1 + AGP 9.0.1. Option 1 (explicit version) is more reliable.

### Pattern 2: KClass-Keyed Multi-Slot WidgetData

**What:** Type-safe widget data delivery where each snapshot type is independently available.

**Implementation:**
```kotlin
@Immutable
data class WidgetData(
    val snapshots: ImmutableMap<KClass<out DataSnapshot>, DataSnapshot>,
    val timestamp: Long,
) {
    inline fun <reified T : DataSnapshot> snapshot(): T? =
        snapshots[T::class] as? T

    fun withSlot(type: KClass<out DataSnapshot>, snapshot: DataSnapshot): WidgetData =
        WidgetData(snapshots.put(type, snapshot), snapshot.timestamp)

    fun hasData(): Boolean = snapshots.isNotEmpty()

    companion object {
        val Empty = WidgetData(persistentMapOf(), 0L)
        val Unavailable = WidgetData(persistentMapOf(), -1L)
    }
}
```

**Why this works:** `persistentMapOf()` from kotlinx-collections-immutable is `@Immutable`-compatible. The `put()` method returns a new `PersistentMap`, making `withSlot` allocation-efficient (structural sharing). The `as? T` cast is safe by construction — the binder guarantees type consistency via `DataProvider<T>.snapshotType`.

### Pattern 3: Sealed Interface + `Gated` Composition

**What:** Settings, setup definitions, and widget render states use sealed interfaces with `Gated` for entitlement checking.

**Implementation:**
```kotlin
interface Gated {
    val requiredAnyEntitlement: Set<String>?  // null = free
}

fun Gated.isAccessible(hasEntitlement: (String) -> Boolean): Boolean {
    val required = requiredAnyEntitlement
    if (required.isNullOrEmpty()) return true
    return required.any { hasEntitlement(it) }
}
```

The three-layer visibility system for settings:
1. `hidden: Boolean` — hard skip, no animation
2. `visibleWhen: ((Map<String, Any?>) -> Boolean)?` — null = always visible
3. `requiredAnyEntitlement: Set<String>?` — null/empty = no gating

### Pattern 4: ConnectionStateMachine with Retry State

**What:** State machine with retry counter as internal state, not external variable.

**Implementation:**
```kotlin
class ConnectionStateMachine(
    initialState: ConnectionMachineState = ConnectionMachineState.Idle
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<ConnectionMachineState> = _state.asStateFlow()

    private var retryCount = 0

    fun transition(event: ConnectionEvent): ConnectionMachineState {
        val newState = computeTransition(_state.value, event)
        _state.value = newState
        return newState
    }

    private fun computeTransition(
        current: ConnectionMachineState,
        event: ConnectionEvent,
    ): ConnectionMachineState = when (current) {
        is ConnectionMachineState.Idle -> when (event) {
            is ConnectionEvent.StartSearch -> ConnectionMachineState.Searching
            else -> current
        }
        is ConnectionMachineState.Searching -> when (event) {
            is ConnectionEvent.DeviceFound ->
                ConnectionMachineState.DeviceDiscovered(event.deviceId, event.deviceName)
            is ConnectionEvent.SearchTimeout -> ConnectionMachineState.Error(AppError.Device("Search timeout"))
            is ConnectionEvent.Disconnect -> ConnectionMachineState.Idle
            else -> current
        }
        // ... exhaustive when branches
    }

    fun reset() {
        retryCount = 0
        _state.value = ConnectionMachineState.Idle
    }
}
```

**New vs old:** Old code had 6 states and 7 events with 8 tests. New code adds: retry counter as internal state, exponential backoff (1s, 2s, 4s), max 3 retries to Idle, SearchTimeout (30s) and ConnectingTimeout (10s) events, exhaustive 6x7 transition matrix test, and 5 jqwik property-based tests.

### Anti-Patterns to Avoid

- **Compose compiler on `:sdk:contracts`:** Adding `dqxn.android.compose` plugin when only annotation usage is needed. Would trigger full Compose compilation, break incremental builds, and couple the module to Compose BOM version.

- **`ImageVector` in contract types:** Old code used `ImageVector` on `SetupDefinition` and `WidgetRenderState` variants. Requires `compose-ui-graphics` dependency. Replaced with `iconName: String?` (Material icon name). Resolution to `ImageVector` happens in `:sdk:ui` (Phase 3).

- **`Context`-dependent lambdas in contracts:** Old `SystemServiceToggle.checkEnabled: (Context) -> Boolean` leaks Android framework into pure contracts. Replaced with `serviceType: ServiceType` enum — the shell interprets the enum.

- **`@Composable` lambdas in contracts:** Old `EnumSetting.optionPreviews` stored `@Composable` lambdas. Cannot exist without Compose compiler. Stripped — preview rendering moves to `:sdk:ui` Phase 3 registry pattern.

- **Mutable `var` in data classes:** `WidgetData`, `WidgetStyle`, `WidgetContext` — all `val` properties. `@Immutable` annotation + `ImmutableMap`/`ImmutableList` for Compose stability.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Immutable persistent collections | Custom immutable map wrapper | `kotlinx-collections-immutable` `persistentMapOf()` | Structural sharing, `@Immutable`-compatible, tested |
| Property-based testing | Manual random generators | jqwik 1.9.3 (JUnit5 engine) | `@Property`, `@ForAll`, `Arbitraries` API for composable generators |
| Coroutine test dispatchers | `Thread.sleep` or `runBlocking` | `StandardTestDispatcher` + `runTest` | Deterministic time control via `advanceTimeBy`/`advanceUntilIdle` |
| Flow testing assertions | Manual `collect` + list comparison | Turbine 1.2.0 | `test {}`, `awaitItem()`, timeout-aware |
| JSON serialization | Manual string building | kotlinx.serialization `@Serializable` | Compile-time codegen, schema validation |

**Key insight:** The type surface in Phase 2 is entirely declaration — no runtime behavior beyond `ConnectionStateMachine` and `WidgetData.withSlot()`. The test suite IS the validation. Hand-rolling test infrastructure wastes effort that should go into thorough assertion coverage.

## Common Pitfalls

### Pitfall 1: compose-runtime Version Resolution Without BOM

**What goes wrong:** `compileOnly(libs.compose.runtime)` without a version fails to resolve because the catalog entry has no version (expects BOM).
**Why it happens:** Version catalog uses `compose-runtime = { group = "androidx.compose.runtime", name = "runtime" }` — no `version.ref`. BOM normally resolves this.
**How to avoid:** Either: (a) add a versioned alias `compose-runtime-standalone` to the catalog, or (b) use `compileOnly(platform(libs.compose.bom))` + `compileOnly(libs.compose.runtime)`. Test resolution with `./gradlew :sdk:contracts:dependencies --configuration debugCompileClasspath --console=plain`.
**Warning signs:** `Could not resolve androidx.compose.runtime:runtime:` in Gradle output.

### Pitfall 2: java-test-fixtures Plugin with AGP 9.0.1

**What goes wrong:** `java-test-fixtures` may have incompatibilities with AGP 9.0.1's new DSL. The plugin was historically Java-centric.
**Why it happens:** AGP 9 introduced significant DSL changes. testFixtures support for Android libraries has been evolving.
**How to avoid:** Phase 1 success criteria #9 already validates testFixtures with a throwaway check. If that passed, the plugin works. If not, track the workaround in STATE.md. The testFixtures consumer verification (SC #12) catches JAR packaging issues early.
**Warning signs:** `Could not find method testFixturesImplementation()` or testFixtures JAR not published.

### Pitfall 3: jqwik on Android JUnit5 Platform

**What goes wrong:** jqwik tests silently skipped — JUnit XML shows 0 tests executed.
**Why it happens:** `de.mannodermaus.android-junit5` plugin must register both Jupiter and jqwik engines. jqwik runs as its own JUnit5 `TestEngine` (not Jupiter). If the vintage engine is present but jqwik engine isn't on the test runtime classpath, jqwik `@Property` tests are invisible.
**How to avoid:** The `dqxn.android.test` convention plugin already adds `testImplementation(libs.jqwik)`. Verify with SC #13: check `build/test-results/` XML for jqwik-executed tests. jqwik 1.9.3 bundles its own engine JAR.
**Warning signs:** Test count lower than expected. No `net.jqwik` entries in XML.

### Pitfall 4: WidgetRendererContractTest JUnit4/JUnit5 Split

**What goes wrong:** `WidgetRendererContractTest` is JUnit4 (for `ComposeContentTestRule`), but the rest of the test suite is JUnit5. Tests don't run if vintage engine isn't configured.
**Why it happens:** `ComposeContentTestRule` requires JUnit4 `@Rule`. jqwik property tests require JUnit5. Two frameworks must coexist.
**How to avoid:** The convention plugin already adds `junit-vintage-engine` to `testRuntimeOnly`. `WidgetRendererContractTest` is a JUnit4 abstract class, `WidgetRendererPropertyTest` is a JUnit5 abstract class. Pack widget tests extend BOTH in separate files. Verify both frameworks execute via XML output.
**Warning signs:** `ComposeContentTestRule` initialization errors, or vintage tests not discovered.

### Pitfall 5: ProviderFault in Main Source Set

**What goes wrong:** Putting `ProviderFault` in testFixtures when it's needed at debug runtime by `ChaosProviderInterceptor`.
**Why it happens:** Natural instinct to put test-related types in testFixtures. But `ProviderFault` is consumed by `:core:agentic` debug builds, not just tests.
**How to avoid:** `ProviderFault` sealed interface goes in main source set (`src/main/kotlin/.../fault/`). `TestDataProvider` (which uses `ProviderFault`) stays in testFixtures.
**Warning signs:** `Unresolved reference: ProviderFault` in `:core:agentic` debug source set.

### Pitfall 6: DataSnapshot @Immutable Without Compose Compiler

**What goes wrong:** `@Immutable` annotation on `DataSnapshot` interface in `:sdk:contracts` has no effect since the Compose compiler isn't applied.
**Why it happens:** `@Immutable` is a Compose compiler directive — without the compiler, it's documentation-only.
**How to avoid:** This is expected and correct. The `@Immutable` annotation serves as a contract marker. Concrete `@DashboardSnapshot` subtypes live in `:pack:*:snapshots` modules (which also don't have Compose compiler via `dqxn.snapshot`). The annotation becomes effective when the type is consumed in a Compose-compiled module (`:sdk:ui`, packs via `dqxn.pack`). Document this intentional pattern.
**Warning signs:** None — this is the designed behavior per CLAUDE.md.

### Pitfall 7: testFixtures Dependency Configuration

**What goes wrong:** testFixtures can't find dependencies like compose-runtime or coroutines-test.
**Why it happens:** testFixtures have their own dependency scope. `implementation` deps from main source are NOT automatically available to testFixtures.
**How to avoid:** Explicitly declare `testFixturesImplementation(...)` for every dependency testFixtures code uses. The `testFixturesApi(...)` configuration makes deps transitive to consumers.
**Warning signs:** `Unresolved reference` errors only in `src/testFixtures/` source sets.

## Code Examples

### WidgetRenderer Contract (Core Interface)

```kotlin
// Source: phase-02.md deliverables + arch/plugin-system.md
interface WidgetSpec {
    val typeId: String
    val displayName: String
    val description: String
    val compatibleSnapshots: Set<KClass<out DataSnapshot>>
    val settingsSchema: List<SettingDefinition<*>>
    val aspectRatio: Float?  // null = freeform
    val supportsTap: Boolean
    val priority: Int

    fun getDefaults(context: WidgetContext): WidgetDefaults
}

interface WidgetRenderer : WidgetSpec, Gated {
    @Composable
    fun Render(
        isEditMode: Boolean,
        style: WidgetStyle,
        settings: ImmutableMap<String, Any>,
        modifier: Modifier,
    )

    fun accessibilityDescription(data: WidgetData): String

    fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean
}
```

### AppResult<T> (Port from Old Codebase)

```kotlin
// Source: old codebase core/common AppResult.kt + phase-02.md expanded API
sealed interface AppResult<out T> {
    data class Ok<out T>(val value: T) : AppResult<T>
    data class Err(val error: AppError) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Ok
    val isFailure: Boolean get() = this is Err

    fun getOrNull(): T? = when (this) {
        is Ok -> value
        is Err -> null
    }

    fun errorOrNull(): AppError? = when (this) {
        is Ok -> null
        is Err -> error
    }
}

fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Ok -> AppResult.Ok(transform(value))
    is AppResult.Err -> this
}

fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Ok -> transform(value)
    is AppResult.Err -> this
}

fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Ok) block(value)
    return this
}

fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Err) block(error)
    return this
}

fun <T> AppResult<T>.getOrElse(defaultValue: T): T = when (this) {
    is AppResult.Ok -> value
    is AppResult.Err -> defaultValue
}

fun <T> AppResult<T>.getOrElse(onFailure: (AppError) -> T): T = when (this) {
    is AppResult.Ok -> value
    is AppResult.Err -> onFailure(error)
}
```

### SettingDefinition Sealed Interface (12 Subtypes)

```kotlin
// Source: old codebase SettingDefinition.kt + phase-02.md modifications
sealed interface SettingDefinition<T> : Gated {
    val key: String
    val label: String
    val description: String?
    val default: T
    val visibleWhen: ((Map<String, Any?>) -> Boolean)?
    val groupId: String?
    val hidden: Boolean

    data class BooleanSetting(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val default: Boolean,
        override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
        override val groupId: String? = null,
        override val hidden: Boolean = false,
        override val requiredAnyEntitlement: Set<String>? = null,
    ) : SettingDefinition<Boolean>

    data class IntSetting(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val default: Int,
        val min: Int,
        val max: Int,
        val presets: List<Int>? = null,
        val presetsWhen: ((Map<String, Any?>) -> List<Int>?)? = null,
        override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
        override val groupId: String? = null,
        override val hidden: Boolean = false,
        override val requiredAnyEntitlement: Set<String>? = null,
    ) : SettingDefinition<Int>

    // ... 10 more subtypes following same pattern
}
```

### DataProvider Contract

```kotlin
// Source: arch/plugin-system.md + state-management.md
interface DataProviderSpec : Gated {
    val sourceId: String
    val displayName: String
    val description: String
    val dataType: String
    val priority: ProviderPriority
}

enum class ProviderPriority {
    HARDWARE,       // BLE device, OBD-II
    DEVICE_SENSOR,  // GPS, accelerometer, light sensor
    NETWORK,        // API-based (weather, maps)
    SIMULATED,      // Demo/debug providers
}

interface DataProvider<T : DataSnapshot> : DataProviderSpec {
    val snapshotType: KClass<T>
    fun provideState(): Flow<T>
    val schema: DataSchema
    val setupSchema: List<SetupPageDefinition>
    val subscriberTimeout: Duration  // default 5s
    val firstEmissionTimeout: Duration  // default 5s
    val isAvailable: Boolean
    val connectionState: Flow<Boolean>
    val connectionErrorDescription: Flow<String?>
}

interface ActionableProvider<T : DataSnapshot> : DataProvider<T> {
    fun onAction(action: WidgetAction)
}
```

### ProviderFault (Main Source Set)

```kotlin
// Source: arch/testing.md + phase-02.md
sealed interface ProviderFault {
    data object Kill : ProviderFault
    data class Delay(val delayMs: Long) : ProviderFault
    data class Error(val exception: Exception) : ProviderFault
    data class ErrorOnNext(val exception: Exception) : ProviderFault
    data class Corrupt(val transform: (DataSnapshot) -> DataSnapshot) : ProviderFault
    data class Flap(val onMillis: Long, val offMillis: Long) : ProviderFault
    data object Stall : ProviderFault
}
```

### InAppNotification Types

```kotlin
// Source: arch/platform.md notification types section
sealed interface InAppNotification {
    val id: String
    val priority: NotificationPriority
    val timestamp: Long
    val alertProfile: AlertProfile?

    data class Toast(
        override val id: String,
        override val priority: NotificationPriority,
        override val timestamp: Long,
        override val alertProfile: AlertProfile?,
        val message: String,
        val durationMs: Long = 4_000L,
    ) : InAppNotification

    data class Banner(
        override val id: String,
        override val priority: NotificationPriority,
        override val timestamp: Long,
        override val alertProfile: AlertProfile?,
        val title: String,
        val message: String,
        val actions: ImmutableList<NotificationAction>,
        val dismissible: Boolean,
    ) : InAppNotification
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `DataSnapshot(Map<String, Any?>)` | Typed `@DashboardSnapshot` subtypes | Phase 2 | Eliminates 720 boxing ops/sec, enables compiler-enforced type safety |
| `compatibleDataTypes: List<String>` | `compatibleSnapshots: Set<KClass<out DataSnapshot>>` | Phase 2 | Compile-time binding validation instead of runtime string matching |
| `Gated.requiredAnyEntitlement: List<String>?` | `Set<String>?` | Phase 2 | Uniqueness enforced, cleaner OR-logic |
| `ImageVector` in contract types | `iconName: String?` | Phase 2 | Removes `compose-ui-graphics` dependency from contracts |
| `Context`-dependent `checkEnabled` lambdas | `ServiceType` enum | Phase 2 | Declarative, testable without Android Context |
| `DQXNDispatchers` interface + qualifier annotations | Qualifier annotations only | Phase 2 | Single pattern, `DQXNDispatchers` interface was never properly bound |
| `WidgetData(Map<String, DataSnapshot>)` | `WidgetData(ImmutableMap<KClass<out DataSnapshot>, DataSnapshot>)` | Phase 2 | Type-safe access, Compose stability |
| `preserveAspectRatio: Boolean` | `aspectRatio: Float?` | Phase 2 | Null = freeform, Float = specific ratio (1:1 for circular widgets) |

## Open Questions

1. **compose-runtime compileOnly version resolution**
   - What we know: The version catalog has `compose-runtime` without a version, expecting BOM resolution.
   - What's unclear: Whether `compileOnly(platform(libs.compose.bom))` + `compileOnly(libs.compose.runtime)` works with Gradle 9.3.1 config cache.
   - Recommendation: Try both approaches during implementation. Fallback: add explicit versioned alias.

2. **testFixtures + AGP 9.0.1 compatibility for Compose dependencies**
   - What we know: Phase 1 SC #9 validates basic testFixtures resolution. `WidgetRendererContractTest` needs `compose-ui-test-junit4` in testFixtures.
   - What's unclear: Whether AGP 9.0.1 properly handles Compose test dependencies in testFixtures source sets (this is a niche configuration).
   - Recommendation: If testFixtures Compose tests fail to compile, move the Compose-dependent contract tests to a separate `:sdk:contracts:test-compose` module with `dqxn.android.compose` applied.

3. **jqwik `@Provide` methods with Compose types**
   - What we know: jqwik generates arbitrary `ImmutableMap<String, Any>` for settings survival test.
   - What's unclear: Whether jqwik's reflection-based generation works with `ImmutableMap` (kotlinx-collections-immutable type, not stdlib).
   - Recommendation: Write a custom `@Provide` method that constructs `ImmutableMap` from a generated regular `Map`. This is what `arch/testing.md` already shows.

4. **`@DashboardSnapshot` annotation retention**
   - What we know: phase-02.md says `@Retention(SOURCE)`. KSP processors read from compilation environment.
   - What's unclear: Whether KSP can read `SOURCE`-retained annotations in all processing scenarios, or if `BINARY`/`RUNTIME` is needed.
   - Recommendation: Use `SOURCE` as designed. KSP operates during compilation and has access to all annotations regardless of retention. Retention only affects runtime reflection, which isn't used.

5. **AppError extensibility for packs**
   - What we know: Old `AppError` had OBU-specific variants that are dropped. Phase-02.md says "add extensibility mechanism for packs."
   - What's unclear: Whether this means a `data class Pack(val packId: String, val code: String, val message: String?)` variant in the sealed hierarchy, or a separate mechanism.
   - Recommendation: Add `data class Pack(val packId: String, val code: String, val message: String? = null) : AppError` as a catch-all variant for pack-originated errors. Packs can define their own error hierarchies internally but must wrap them in `Pack` when crossing the SDK boundary.

## Build Configuration Details

### `:sdk:common` build.gradle.kts

```kotlin
plugins {
    id("dqxn.android.library")
    id("dqxn.android.hilt")
    id("dqxn.android.test")
}

android { namespace = "app.dqxn.android.sdk.common" }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
```

Already matches the existing stub. Add `dqxn.android.test` if not present (it is present). No additional dependencies needed — Hilt provides `javax.inject.Qualifier`.

### `:sdk:contracts` build.gradle.kts

```kotlin
plugins {
    id("dqxn.android.library")
    id("dqxn.android.test")
    id("java-test-fixtures")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.dqxn.android.sdk.contracts" }

dependencies {
    api(project(":sdk:common"))

    compileOnly(libs.compose.runtime)  // @Composable, @Immutable annotations only
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // testFixtures dependencies (separate scope)
    testFixturesImplementation(project(":sdk:common"))
    testFixturesImplementation(libs.compose.runtime)
    testFixturesImplementation(libs.kotlinx.collections.immutable)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter.api)
    testFixturesImplementation(libs.junit.vintage.engine)
    testFixturesImplementation(libs.truth)
    testFixturesImplementation(libs.jqwik)
    testFixturesImplementation(libs.turbine)
    // Compose test for WidgetRendererContractTest (JUnit4)
    testFixturesImplementation(libs.compose.ui.test.junit4)
    testFixturesImplementation(libs.robolectric)
}
```

### Key Dependency Notes

- `api(project(":sdk:common"))` — exposes `AppError`, `AppResult`, `ConnectionStateMachine` transitively to all consumers of `:sdk:contracts`
- `compileOnly(libs.compose.runtime)` — annotation-only, NOT runtime. Consumers that call `Render()` need the full Compose stack
- `kotlin-serialization` plugin — needed for `@Serializable` on `WidgetStyle`, `BackgroundStyle`, settings enums, `DashboardPackManifest`
- `java-test-fixtures` — produces testFixtures JAR consumed by pack test modules

## Replication Advisory Reference

### Section 7: Widget Setup Architecture

The following semantics from the old codebase MUST be preserved in the new type definitions:

1. **SetupDefinition 7-type schema in 3 categories:** Requirement (RuntimePermission, SystemServiceToggle, SystemService, DeviceScan), Display (Instruction, Info), Input (Setting wrapper). The `Setting` wrapper delegates `id`, `label`, `visibleWhen`, `requiredAnyEntitlement`, `hidden`, `default` to the wrapped `SettingDefinition`.

2. **Three-layer conditional visibility:** `hidden` (hard skip) -> `visibleWhen` (lambda, null=visible) -> `requiredAnyEntitlement` (OR-logic, null/empty=free). Double-gating on `Setting` wrapper is intentional — wrapper checks its own `visibleWhen`, then `SettingRowDispatcher` checks the inner definition's `visibleWhen`.

3. **`visibleWhen` null-means-visible convention:** `null` predicate === always visible. Critical: check is `!= false`, not `== true`. This handles the null case.

4. **SettingDefinition 12-type schema:** BooleanSetting, IntSetting, FloatSetting, StringSetting, EnumSetting, TimezoneSetting, DateFormatSetting, UriSetting, AppPickerSetting, SoundPickerSetting, InstructionSetting, InfoSetting. All carry `key`, `label`, `description?`, `default`, `visibleWhen?`, `groupId?`, `hidden`, `requiredAnyEntitlement?`.

5. **IntSetting.presetsWhen:** Dynamic lambda `(Map<String, Any?>) -> List<Int>?` that overrides static `presets`. Must call `getEffectivePresets(currentSettings)`.

6. **SetupEvaluator two-variant semantics:** `evaluateWithPersistence()` (checks `pairedDeviceStore`) vs `evaluate()` (real-time only). Interface only in Phase 2 — but the type signatures must accommodate both variants.

7. **ProviderSettingsStore type-prefixed serialization:** Key format `{packId}:{providerId}:{key}`. Value prefixes: `s:`, `i:`, `l:`, `f:`, `d:`, `b:`, `j:`. Legacy fallback: no prefix = raw String. This affects `SettingDefinition` default value types — must be serializable to these prefixed formats.

## Sources

### Primary (HIGH confidence)
- Project architecture docs: ARCHITECTURE.md, arch/plugin-system.md, arch/state-management.md, arch/platform.md, arch/testing.md
- Phase 2 detailed design: .planning/migration/phase-02.md
- Old codebase mapping: .planning/oldcodebase/core-libraries.md
- Replication advisory: .planning/migration/replication-advisory.md (Section 7)
- Existing codebase: build-logic convention plugins, version catalog, module stubs

### Secondary (MEDIUM confidence)
- DECISIONS.md: 89 architectural decisions providing rationale for design choices
- REQUIREMENTS.md: Product requirements mapped to phase deliverables

### Tertiary (LOW confidence)
- compose-runtime compileOnly pattern with Gradle 9.3.1: untested with this specific Gradle/AGP version combination (based on general Gradle knowledge)
- java-test-fixtures + AGP 9.0.1: Phase 1 SC #9 should have validated basic compatibility, but Compose test dependencies in testFixtures are an edge case

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in version catalog, versions locked by Phase 1
- Architecture: HIGH — exhaustive documentation in phase-02.md + architecture docs, old codebase reference available
- Pitfalls: HIGH — identified from concrete codebase constraints (AGP 9.0.1, Gradle 9.3.1, convention plugin structure)
- Testing: HIGH — test framework already configured by `dqxn.android.test` convention plugin, jqwik in catalog
- Build configuration: MEDIUM — compileOnly compose-runtime version resolution is the one uncertainty

**Research date:** 2026-02-24
**Valid until:** 2026-03-24 (stable — all dependency versions locked)
