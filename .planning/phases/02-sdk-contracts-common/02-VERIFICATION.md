---
phase: 02-sdk-contracts-common
verified: 2026-02-24T12:00:00Z
status: passed
score: 20/20 must-haves verified
---

# Phase 2: SDK Contracts + Common Verification Report

**Phase Goal:** Complete `:sdk:contracts` type surface and `:sdk:common` utilities. Every type that lives in `:sdk:contracts` is defined here — downstream phases consume but never modify it. Biggest architectural transformation: old untyped `DataSnapshot(Map<String, Any?>)` becomes typed `@DashboardSnapshot` subtypes with `KClass`-keyed `WidgetData`.
**Verified:** 2026-02-24T12:00:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | AppResult sealed interface provides Ok/Err with map, flatMap, onSuccess, onFailure, getOrNull, getOrElse, errorOrNull | VERIFIED | `AppResult.kt` — all 7 extension functions present, 15 unit tests passing |
| 2  | AppError hierarchy covers Network, Bluetooth, Permission, Device, Database, Pack, Unknown — no OBU-specific variants | VERIFIED | `AppError.kt` — 7 variants confirmed, grep for Obu/SdkAuth returns nothing |
| 3  | ConnectionStateMachine transitions through 6 states with retry counter, exponential backoff, max 3 retries | VERIFIED | `ConnectionStateMachine.kt` — 6 states, 7 events, retryCount with private set, retryDelay computed from bit shift |
| 4  | Dispatcher qualifiers and DispatcherModule provide coroutine dispatchers via Hilt | VERIFIED | `DispatcherModule.kt` — @InstallIn(SingletonComponent::class), all 4 bindings including @ApplicationScope with SupervisorJob |
| 5  | WidgetRenderer extends WidgetSpec + Gated with @Composable Render() that reads data from LocalWidgetData | VERIFIED | `WidgetRenderer.kt` — `interface WidgetRenderer : WidgetSpec, Gated`, @Composable Render() with correct signature |
| 6  | WidgetData uses KClass-keyed ImmutableMap for type-safe multi-slot snapshot access | VERIFIED | `WidgetData.kt` — `ImmutableMap<KClass<out DataSnapshot>, DataSnapshot>`, inline reified snapshot<T>(), PersistentMap.put() wiring |
| 7  | DataProvider<T : DataSnapshot> is a generic interface enforcing compile-time type safety | VERIFIED | `DataProvider.kt` — generic interface with snapshotType: KClass<T>, provideState(): Flow<T>, setupSchema using real SetupPageDefinition |
| 8  | Gated.isAccessible() implements OR-logic: null/empty = free, any matching entitlement grants access | VERIFIED | `Gated.kt` — extension function with isNullOrEmpty() guard then any{} check; 6 tests pass |
| 9  | All KSP annotations (@DashboardWidget, @DashboardDataProvider, @DashboardSnapshot) defined with SOURCE retention | VERIFIED | All 3 files — `@Retention(AnnotationRetention.SOURCE)` on each annotation |
| 10 | ProviderFault sealed interface in main source set with 7 variants | VERIFIED | `fault/ProviderFault.kt` in `src/main/kotlin/` — Kill, Delay, Error, ErrorOnNext, Corrupt, Flap, Stall |
| 11 | SetupDefinition sealed interface has 7 subtypes in 3 categories: requirement (RuntimePermission, SystemServiceToggle, SystemService, DeviceScan), display (Instruction, Info), input (Setting wrapper) | VERIFIED | `SetupDefinition.kt` — all 7 subtypes plus isRequirement/isDisplay/isInput extensions, asSetup() factory, getDefaultValue() |
| 12 | SettingDefinition sealed interface has 12 subtypes all implementing Gated with three-layer visibility | VERIFIED | `SettingDefinition.kt` — all 12 subtypes, each with hidden/visibleWhen/requiredAnyEntitlement; KDoc documents three-layer order |
| 13 | InAppNotification sealed interface has Toast and Banner subtypes with priority and alert profile | VERIFIED | `InAppNotification.kt` — Toast (message, durationMs) and Banner (title, actions, dismissible) both with alertProfile: AlertProfile? |
| 14 | WidgetRenderState sealed interface has 8 overlay state variants | VERIFIED | `WidgetRenderState.kt` — Ready, SetupRequired, ConnectionError, Disconnected, EntitlementRevoked, ProviderMissing, DataTimeout, DataStale |
| 15 | ThemeSpec metadata interface extends Gated — no Compose types | VERIFIED | `ThemeSpec.kt` — `interface ThemeSpec : Gated`, metadata only (themeId, displayName, isDark, packId), no Compose imports |
| 16 | WidgetRegistry and DataProviderRegistry interfaces defined | VERIFIED | Both files exist with correct method signatures; DataProviderRegistry has entitlement-filtered getFiltered() for F3.7 |
| 17 | WidgetRendererContractTest abstract class provides 14 inherited assertions (JUnit4) | VERIFIED | File has 13 @Test methods (test #9 intentionally in separate WidgetRendererPropertyTest per plan design), 13 pass in XML; plan comment in code documents skip |
| 18 | DataProviderContractTest abstract class provides 12 inherited assertions (JUnit5) | VERIFIED | 12 @Test methods in file, 12 pass in XML output |
| 19 | TestDataProvider supports mid-stream ProviderFault injection | VERIFIED | `TestDataProvider.kt` — MutableStateFlow<ProviderFault?> with injectFault(), transformLatest applies fault semantics |
| 20 | All tests pass: sdk:common (75 tests) and sdk:contracts (98 tests) | VERIFIED | BUILD SUCCESSFUL; 0 failures, 0 errors across all XMLs; jqwik confirmed via system-out in XML files |

**Score:** 20/20 truths verified

---

### Required Artifacts

| Artifact | Provides | Status | Details |
|----------|----------|--------|---------|
| `android/sdk/common/src/main/kotlin/.../result/AppResult.kt` | Sealed result type with 7 extension functions | VERIFIED | All extensions present and tested |
| `android/sdk/common/src/main/kotlin/.../statemachine/ConnectionStateMachine.kt` | State machine with retry logic | VERIFIED | class ConnectionStateMachine, 6 states, 7 events |
| `android/sdk/common/src/main/kotlin/.../di/DispatcherModule.kt` | Hilt module for coroutine dispatchers | VERIFIED | @InstallIn(SingletonComponent::class) |
| `android/sdk/common/src/test/.../result/AppResultTest.kt` | 15 AppResult unit tests | VERIFIED | XML confirms 15 testcases |
| `android/sdk/common/src/test/.../statemachine/ConnectionStateMachineTest.kt` | 60 tests (ported + matrix + behavior + jqwik) | VERIFIED | XML confirms 8+42+5+5=60 testcases, jqwik engine confirmed |
| `android/sdk/contracts/build.gradle.kts` | compileOnly compose BOM, testFixtures, serialization, api(:sdk:common) | VERIFIED | testFixtures { enable = true }, api(project(":sdk:common")), compileOnly(platform(compose.bom)) |
| `android/sdk/contracts/src/main/kotlin/.../widget/WidgetRenderer.kt` | Core widget rendering contract | VERIFIED | interface WidgetRenderer : WidgetSpec, Gated |
| `android/sdk/contracts/src/main/kotlin/.../widget/WidgetData.kt` | KClass-keyed multi-slot widget data | VERIFIED | data class WidgetData with ImmutableMap, snapshot<T>(), Empty, Unavailable |
| `android/sdk/contracts/src/main/kotlin/.../provider/DataProvider.kt` | Generic data provider contract | VERIFIED | interface DataProvider<T : DataSnapshot> : DataProviderSpec |
| `android/sdk/contracts/src/main/kotlin/.../entitlement/Gated.kt` | Entitlement gating with OR-logic | VERIFIED | interface Gated + isAccessible extension |
| `android/sdk/contracts/src/main/kotlin/.../fault/ProviderFault.kt` | Chaos injection fault types in main source | VERIFIED | In src/main/kotlin, 7 variants |
| `android/sdk/contracts/src/main/kotlin/.../setup/SetupDefinition.kt` | 7-type setup schema | VERIFIED | sealed interface SetupDefinition with 7 subtypes, extensions |
| `android/sdk/contracts/src/main/kotlin/.../settings/SettingDefinition.kt` | 12-type settings schema implementing Gated | VERIFIED | sealed interface SettingDefinition<T> : Gated, 12 subtypes |
| `android/sdk/contracts/src/main/kotlin/.../notification/InAppNotification.kt` | Notification types with priority and alert profile | VERIFIED | Toast + Banner sealed interface |
| `android/sdk/contracts/src/main/kotlin/.../status/WidgetRenderState.kt` | Widget overlay state sealed hierarchy | VERIFIED | 8 variants, String icon names, no ImageVector |
| `android/sdk/contracts/src/testFixtures/.../WidgetRendererContractTest.kt` | Abstract JUnit4 contract test base | VERIFIED | abstract class WidgetRendererContractTest, 13 @Test methods |
| `android/sdk/contracts/src/testFixtures/.../DataProviderContractTest.kt` | Abstract JUnit5 contract test base | VERIFIED | abstract class DataProviderContractTest, 12 @Test methods |
| `android/sdk/contracts/src/testFixtures/.../TestDataProvider.kt` | Configurable fake with fault injection | VERIFIED | class TestDataProvider with injectFault(ProviderFault?) |
| `android/sdk/contracts/src/test/.../testing/TestWidgetRendererContractTest.kt` | Concrete validation test | VERIFIED | class TestWidgetRendererContractTest : WidgetRendererContractTest() |
| `android/sdk/contracts/src/test/.../testing/TestDataProviderContractTest.kt` | Concrete validation test | VERIFIED | class TestDataProviderContractTest : DataProviderContractTest() |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ConnectionStateMachine.kt` | `ConnectionMachineState + ConnectionEvent` | sealed interface references | VERIFIED | Both imports present, exhaustive when-expression uses all variants |
| `AppResult.kt` | `AppError` | Err variant wraps AppError | VERIFIED | `data class Err(val error: AppError)` |
| `WidgetRenderer.kt` | `WidgetSpec.kt + Gated.kt` | interface inheritance | VERIFIED | `interface WidgetRenderer : WidgetSpec, Gated` |
| `DataProvider.kt` | `DataProviderSpec.kt + DataSnapshot.kt` | generic interface + inheritance | VERIFIED | `interface DataProvider<T : DataSnapshot> : DataProviderSpec` |
| `WidgetData.kt` | `DataSnapshot.kt` | KClass key + ImmutableMap | VERIFIED | `ImmutableMap<KClass<out DataSnapshot>, DataSnapshot>` |
| `build.gradle.kts` | `:sdk:common` | api project dependency | VERIFIED | `api(project(":sdk:common"))` in build.gradle.kts |
| `SetupDefinition.kt` | `SettingDefinition.kt + Gated.kt` | Setting wrapper delegates | VERIFIED | `data class Setting(val definition: SettingDefinition<*>, ...)` inheriting from Gated |
| `DataProvider.kt` | `SetupPageDefinition.kt` | setupSchema property type | VERIFIED | `val setupSchema: List<SetupPageDefinition>` (not `List<Any>` — forward reference resolved) |
| `WidgetSpec.kt` | `SettingDefinition.kt` | settingsSchema property type | VERIFIED | `val settingsSchema: List<SettingDefinition<*>>` |
| `TestWidgetRendererContractTest.kt` | `WidgetRendererContractTest.kt` | extends abstract base | VERIFIED | `class TestWidgetRendererContractTest : WidgetRendererContractTest()` |
| `TestDataProviderContractTest.kt` | `DataProviderContractTest.kt` | extends abstract base | VERIFIED | `class TestDataProviderContractTest : DataProviderContractTest()` |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|------------|-------------|-------------|--------|----------|
| F2.1 | 02-02, 02-05 | WidgetRenderer contract with @Composable Render() | SATISFIED | `WidgetRenderer.kt` with @Composable Render(), tested in contract base |
| F2.2 | 02-02, 02-05 | WidgetSpec with typeId, display name, compatible data types, settings schema | SATISFIED | `WidgetSpec.kt` with all 8 fields including compatibleSnapshots, settingsSchema |
| F2.4 | 02-04 | WidgetDataBinder — IoC binding of providers to widgets | PARTIAL | Phase 2 delivers WidgetData type contract + multi-slot tests. Actual WidgetDataBinder binding machinery is Phase 7. Plan 04 tests WidgetData semantics which are the type foundation for F2.4, not the binder itself. |
| F2.5 | 02-04 | WidgetStatusCache — overlays for entitlement, setup, connection issues | PARTIAL | `WidgetStatusCache.kt` data type delivered in Phase 2. Runtime overlay rendering is Phase 7. Plan 04 tests Gated/SettingDefinition which underpin the status model. |
| F2.10 | 02-02 | Context-aware defaults via WidgetContext (timezone, region) | SATISFIED | `WidgetContext.kt` with timezone: ZoneId, locale: Locale, region: String; getDefaults(context: WidgetContext) in WidgetSpec |
| F2.11 | 02-02 | SettingsAwareSizer — dynamic default size based on enabled features | SATISFIED | `SettingsAwareSizer.kt` — interface with computeSize(settings) returning WidgetDefaults |
| F2.12 | 02-02 | @DashboardWidget KSP annotation | SATISFIED | `DashboardWidget.kt` — @Retention(SOURCE) @Target(CLASS) annotation |
| F2.16 | 02-02 | Aspect ratio constraint: aspectRatio: Float? in WidgetSpec | SATISFIED | `WidgetSpec.kt` — `val aspectRatio: Float?` (null = freeform) |
| F2.19 | 02-02, 02-05 | Widget accessibility: accessibilityDescription(data) | SATISFIED | `WidgetRenderer.kt` — `fun accessibilityDescription(data: WidgetData): String`, contract test #4 and #5 enforce non-empty and data-sensitive |
| F2.20 | 02-02, 02-05 | WidgetSpec.description: String field | SATISFIED | `WidgetSpec.kt` — `val description: String`, TestWidgetRenderer uses it in displayName/description assertions |
| F3.1 | 02-02, 02-05 | DataProvider<T : DataSnapshot> contract | SATISFIED | `DataProvider.kt` — generic interface with provideState(): Flow<T> |
| F3.2 | 02-02, 02-05 | DataSchema describing output shape | SATISFIED | `DataSchema.kt` — data class with fields: List<DataFieldSpec>, stalenessThresholdMs: Long |
| F3.3 | 02-03 | SetupPageDefinition — declarative multi-page setup wizard | SATISFIED | `SetupPageDefinition.kt` — @Immutable data class with definitions: List<SetupDefinition> |
| F3.4 | 02-03 | Setup definition types: RuntimePermission, SystemServiceToggle, DeviceScan, Instruction | SATISFIED | `SetupDefinition.kt` — all 4 named types plus SystemService, Info, Setting wrapper |
| F3.5 | 02-03 | SetupEvaluator — checks provider readiness against setup requirements | SATISFIED | `SetupEvaluator.kt` — interface with evaluate() + SetupResult; two-variant semantics documented in KDoc |
| F3.6 | 02-01 | Connection state and error reporting (isAvailable, connectionState, connectionErrorDescription) | SATISFIED | `DataProvider.kt` — all 3 properties present |
| F3.8 | 02-02 | @DashboardDataProvider KSP annotation | SATISFIED | `DashboardDataProvider.kt` — @Retention(SOURCE) with localId, displayName, description |
| F8.1 | 02-02 | Entitlement system: free, themes tiers | SATISFIED | `Entitlements.kt` — FREE, THEMES, PLUS constants |
| F8.3 | 02-02 | Gated interface on renderers, providers, themes, settings | SATISFIED | Gated implemented by WidgetRenderer, DataProviderSpec, ThemeSpec, SettingDefinition, SetupDefinition |
| F9.1 | 02-03 | Silent connection status notification | PARTIAL | AlertEmitter interface + NotificationPriority enum delivered. Actual notification emission (AlertSoundManager, SystemNotificationBridge) is Phase 7. Type contract satisfies the Phase 2 scope. |
| F9.2 | 02-03 | Per-alert mode selection (SILENT/VIBRATE/SOUND) | SATISFIED | `AlertProfile.kt` — AlertMode enum with SILENT, VIBRATE, SOUND |
| F9.3 | 02-03 | TTS readout for alerts | SATISFIED | `AlertProfile.kt` — `val ttsMessage: String?` field |
| F9.4 | 02-03 | Custom alert sound URIs | SATISFIED | `AlertProfile.kt` — `val soundUri: String?` field |

**Notes on PARTIAL satisfactions:**

- **F2.4 (WidgetDataBinder):** Phase 2 delivers the WidgetData type contract (the key-value store that the binder populates). The IoC binding machinery itself is Phase 7. The plan's claim is consistent with this being a type-surface phase — the type contract for what gets bound is here, but the binder lives in the feature module.
- **F2.5 (WidgetStatusCache overlays):** WidgetStatusCache and WidgetRenderState types are delivered, which define the contract for overlay rendering. The actual overlay rendering in the dashboard shell is Phase 7.
- **F9.1 (silent notification):** InAppNotification sealed interface, AlertEmitter contract, and NotificationPriority are all delivered. The runtime NotificationCoordinator and SystemNotificationBridge are Phase 7.

All three PARTIAL items follow the same pattern: Phase 2 delivers the contract types; Phase 7 delivers the implementations. This is architecturally correct and intentional for a type-surface phase.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `sdk/common/src/main/kotlin/.../flow/FlowExtensions.kt` | 21 | `TODO(phase-3): Replace with DqxnLogger.w(tag, "Flow error", e)` | INFO | Intentional placeholder — catchAndLog uses e.printStackTrace() until Phase 3 observability. Non-blocking, documented in plan. |

No blockers or warnings. The single TODO is explicitly planned and the fallback behavior (e.printStackTrace() + emit fallback) is functional for Phase 2.

---

### Human Verification Required

None. All Phase 2 deliverables are type contracts and unit tests — fully verifiable programmatically. No UI, no runtime behavior, no external services.

---

### Build Verification

Both modules build and test clean:

- `:sdk:common:assembleDebug` — BUILD SUCCESSFUL (UP-TO-DATE)
- `:sdk:contracts:assembleDebug` — BUILD SUCCESSFUL (UP-TO-DATE)
- `:sdk:common:testDebugUnitTest` — 75 tests, 0 failures
- `:sdk:contracts:testDebugUnitTest` — 98 tests, 0 failures

Test engine confirmation via XML system-out:

- **JUnit5 Jupiter:** DataProviderContractTest, SettingDefinitionTest nested classes, SetupDefinitionTest nested classes, GatedTest
- **jqwik:** WidgetDataTest (2 properties, 1000 tries each), ConnectionStateMachineTest (5 properties), TestWidgetRendererPropertyTest (1 property, 50 tries)
- **JUnit4 Vintage:** TestWidgetRendererContractTest (13 tests)

testFixtures JAR artifacts confirmed:
- `build/intermediates/compile_library_classes_jar/debugTestFixtures/bundleLibCompileToJarDebugTestFixtures/testFixtures-classes.jar`
- `build/intermediates/runtime_library_classes_jar/debugTestFixtures/bundleLibRuntimeToJarDebugTestFixtures/testFixtures-classes.jar`

All 9 commit hashes referenced in summaries verified in git history.

---

### Test Discrepancy Note

`TestWidgetRendererContractTest` shows 13 tests in XML, not 14 as the plan's abstract base documents. This is correct: the plan explicitly states "Test #9 (jqwik property test) is NOT in this class — it's in the separate WidgetRendererPropertyTest." The WidgetRendererContractTest source file also has a `// --- Test #9: SKIP —` comment documenting this. The 13 tests in `TestWidgetRendererContractTest` + 1 test in `TestWidgetRendererPropertyTest` = 14 total assertions per the contract spec.

---

_Verified: 2026-02-24T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
