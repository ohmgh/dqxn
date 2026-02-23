# Phase 2: SDK Contracts + Common

**What:** The API surface that every pack and feature module depends on. This is where the biggest architectural transformation lands — old untyped contracts become typed. Phase 2 defines the complete `:sdk:contracts` type surface — all types that live in this module are defined here so downstream phases never modify `:sdk:contracts` (only consume it).

## `:sdk:common` (Android library with Hilt — not pure Kotlin)

Despite the aspirational "pure Kotlin" label, `:sdk:common` requires Hilt for `DispatcherModule`. Resolution: qualifier annotations (`@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`) stay in `:sdk:common` using `javax.inject.Qualifier`. `DispatcherModule` (`@Module @InstallIn(SingletonComponent::class)`) also stays — making `:sdk:common` an Android library with `dqxn.android.hilt`. The alternative (moving `DispatcherModule` to `:app`) would scatter DI wiring and every module would still need the qualifiers.

Deliverables:
- `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher` qualifier annotations
- `DispatcherModule` — Hilt module providing dispatchers. Drop old redundant `DQXNDispatchers` interface
- `@ApplicationScope` qualifier
- `ConnectionStateMachine` + `ConnectionMachineState` + `ConnectionEvent` — implementation ports from old codebase with generalization: **old code references OBU-specific error types (`AppError.Obu.ConnectionTimeout`, `AppError.Obu.BluetoothDisabled`) which are in the drop list — replace with generalized `AppError.Bluetooth`/`AppError.Device` variants.** Old tests also use OBU-specific errors — port test structure but update error types. Test suite significantly expanded from 8 assertions to jqwik property-based coverage. New architecture additions: retry counter as state machine state (not external variable), exponential backoff (1s, 2s, 4s), max 3 retries → Idle, searching timeout (30s), connecting timeout (10s)
- `AppResult<T>` sealed interface + extension functions (`map`, `flatMap`, `onSuccess`, `onFailure`, `getOrNull`, `getOrElse`)
- `AppError` sealed hierarchy — port from old, strip OBU-specific variants (`Obu`, `SdkAuth`), keep general-purpose (`Network`, `Bluetooth`, `Permission`, `Device`, `Database`, `Unknown`). Add extensibility mechanism for packs
- `PermissionKind` enum — generalize (remove `ObuDataAccess`)
- Flow extension utilities migrated from `core:common`

## `:sdk:contracts` build configuration

`:sdk:contracts` applies `dqxn.android.library` (not `dqxn.pack`). Manual additions to `build.gradle.kts`:
- `java-test-fixtures` plugin (first real use — Phase 1 should include a smoke check for AGP 9.0.1 compatibility)
- `compileOnly(libs.compose.runtime)` — for `@Composable` in `WidgetRenderer.Render()` signature and `@Immutable` annotation. This is the standalone `compose-runtime` artifact, NOT mediated through the Compose BOM
- `implementation(libs.kotlinx.collections.immutable)` — `ImmutableMap` in `WidgetData`, `ImmutableList` in notification types
- `implementation(libs.kotlinx.coroutines.core)`
- `api(project(":sdk:common"))` — common types (`AppError`, `ConnectionStateMachine`) available transitively to all consumers
- `implementation(libs.kotlinx.serialization.json)` + `org.jetbrains.kotlin.plugin.serialization` — `WidgetStyle` and settings enums are `@Serializable` for layout persistence round-trip. Alternative: defer serialization to `:data` module, but this forces adapter layers in Phase 5
- Does NOT apply Compose compiler, Hilt, or KSP

## `:sdk:contracts` deliverables

**Widget contracts:**

- `WidgetSpec` interface (explicit — NOT folded into `WidgetRenderer`). Properties: `typeId`, `displayName`, `description` (F2.20), `compatibleSnapshots: Set<KClass<out DataSnapshot>>` (replaces old `compatibleDataTypes: List<String>`), `settingsSchema: List<SettingDefinition<*>>`, `getDefaults(context: WidgetContext): WidgetDefaults`, `aspectRatio: Float?` (F2.16 — replaces old `preserveAspectRatio: Boolean`, null = freeform), `supportsTap`, `priority`
- `WidgetRenderer` — extends `WidgetSpec` + `Gated`. New signature: `@Composable Render(isEditMode, style: WidgetStyle, settings: ImmutableMap<String, Any>, modifier)` — no `widgetData` param (read via `LocalWidgetData.current`). Adds `accessibilityDescription(data: WidgetData): String` (F2.19), `onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean` (drops old `Context` param)
- `SettingsAwareSizer` interface (F2.11) — `fun computeSize(settings: Map<String, Any?>): WidgetDefaults`. Used by 6+ widgets in Phase 8. Drop deprecated `DynamicSizeProvider` alias
- `WidgetData` with `KClass`-keyed multi-slot: `snapshot<T : DataSnapshot>(): T?`, `withSlot(type, snapshot)`, `hasData()`, `Empty` and `Unavailable` sentinels. `snapshots: ImmutableMap<KClass<out DataSnapshot>, DataSnapshot>`, `timestamp: Long`
- `WidgetContext` — `timezone: ZoneId`, `locale: Locale`, `region: String`. `DEFAULT = (UTC, US, "US")`
- `WidgetDefaults` — `widthUnits: Int`, `heightUnits: Int`, `aspectRatio: Float?`, `settings: Map<String, Any?>`
- `WidgetStyle` — `@Serializable @Immutable`. `backgroundStyle: BackgroundStyle`, `opacity: Float`, `showBorder: Boolean`, `hasGlowEffect: Boolean`, `cornerRadiusPercent: Int`, `rimSizePercent: Int`, `zLayer: Int`. `BackgroundStyle` enum: `NONE`, `SOLID`

**Data provider contracts:**

- `DataProviderSpec` interface (explicit — NOT folded into `DataProvider`). Properties: `sourceId`, `displayName`, `description`, `dataType: String`, `priority: ProviderPriority` (used by `WidgetDataBinder` for fallback resolution — F3.10)
- `ProviderPriority` enum: `HARDWARE`, `DEVICE_SENSOR`, `NETWORK`, `SIMULATED`. Provider fallback: user-selected > HARDWARE > DEVICE_SENSOR > NETWORK > SIMULATED
- `DataProvider<T : DataSnapshot>` — extends `DataProviderSpec` + `Gated`. Properties: `snapshotType: KClass<T>`, `provideState(): Flow<T>`, `schema: DataSchema`, `setupSchema: List<SetupPageDefinition>`, `subscriberTimeout: Duration` (default 5s), `firstEmissionTimeout: Duration` (default 5s), `isAvailable: Boolean`, `connectionState: Flow<Boolean>`, `connectionErrorDescription: Flow<String?>`
- `ActionableProvider<T : DataSnapshot>` — extends `DataProvider<T>` for bidirectional interaction: `onAction(action: WidgetAction)`. Required by Phase 8 `CallActionProvider` (Shortcuts widget). **Action-only providers** (no data emission) use a `UnitSnapshot` sentinel type: `@DashboardSnapshot("NONE") @Immutable data class UnitSnapshot(override val timestamp: Long) : DataSnapshot`. `provideState()` emits a single `UnitSnapshot` at init and never again. `CallActionProvider` does NOT pass `DataProviderContractTest` (it has no meaningful data flow) — exclude from the "9 providers pass contract tests" count
- `WidgetAction` sealed interface — `Tap(widgetId)`, `MediaControl(command)`, `TripReset(tripId)`. Non-sealed would allow pack extension but complicates exhaustive handling. Keep sealed at V1; packs needing custom actions use `data class Custom(val actionId: String, val params: Map<String, Any?>)` variant
- `DataProviderInterceptor` — interface for chaos/debug interception of provider flows. The interface itself requires no Hilt; the empty `Set<DataProviderInterceptor>` binding is declared in `:app`'s DI module (Phase 6), `:core:agentic` adds to it in debug builds (Phase 9)
- `DataSnapshot` `@Immutable` non-sealed interface with `val timestamp: Long`. `@DashboardSnapshot` KSP annotation for validation. No concrete subtypes in this module
- `DataSchema` / `DataFieldSpec` — retained on `DataProvider<T>` for staleness thresholds per data type and display metadata. **Must include `stalenessThresholdMs: Long`** — without this, `WidgetHealthMonitor` (Phase 3) and `WidgetBindingCoordinator` (Phase 7) have no programmatic source for F3.11 thresholds (SPEED 3s, ORIENTATION 5s, TIME 2s, BATTERY 30s). Provider declares the threshold; the binding system enforces it
- `DataTypeDescriptor` — type metadata: `typeId`, `displayName`, `unit: String?`, `formatting: FormatSpec?`. Define `FormatSpec` as `data class FormatSpec(val decimalPlaces: Int?, val suffix: String?, val prefix: String?)` or drop the field if it's premature
- `DataTypes` constants — retained as extensible `object` with core type strings (`SPEED`, `TIME`, `ORIENTATION`, `BATTERY`, `SOLAR`, `AMBIENT_LIGHT`, `ACCELERATION`, `SPEED_LIMIT`). Packs define additional constants in their own modules

**Widget status types (F2.5, F3.6, F3.15):**

- `WidgetRenderState` sealed interface: `Ready`, `SetupRequired(requirements)`, `ConnectionError`, `Disconnected`, `EntitlementRevoked(upgradeEntitlement)`, `ProviderMissing`, `DataTimeout(message)`, `DataStale`. Note: old code had `icon: ImageVector` on several variants — replaced with string icon names (see Icon Representation below). Priority ordering logic is Phase 7 implementation, but the type hierarchy is Phase 2
- `WidgetIssue` data class with `IssueType` enum and `ResolutionAction` sealed class
- `WidgetStatusCache` — holds `overlayState: WidgetRenderState` + `issues: List<WidgetIssue>`. `EMPTY` companion
- `WidgetRenderState.computeOverlayState()` — port from old codebase, adapt for new state variants

**Setup system (F3.3, F3.4, F3.5 — required for `DataProvider<T>.setupSchema` to compile):**

- `SetupPageDefinition` — `id`, `title`, `description?`, `definitions: List<SetupDefinition>`
- `SetupDefinition` sealed interface with 7 subtypes: `RuntimePermission`, `SystemServiceToggle`, `SystemService`, `DeviceScan`, `Instruction`, `Info`, `Setting`. All implement `Gated`
- **Icon representation**: Old code used `ImageVector` — incompatible with `:sdk:contracts` (no `compose-ui-graphics`). Replace with `val iconName: String?` (Material icon name, e.g., `"bluetooth"`, `"gps_fixed"`). Resolution to `ImageVector` happens in `:sdk:ui` (Phase 3) via a lookup utility
- **Context dependency**: Old `SystemServiceToggle.checkEnabled: (Context) -> Boolean` — incompatible with pure-contracts module. Replace with declarative `serviceType: ServiceType` enum. The `:feature:dashboard` or `:feature:settings` shell interprets the enum to perform the actual system check. This aligns with the architecture's "declarative schemas" principle
- `ServiceType` enum: `BLUETOOTH`, `LOCATION`, `WIFI`
- `VerificationStrategy` interface + `VerificationResult` sealed (Verified/Failed/Skipped) — used by `SetupDefinition.Instruction.verificationStrategy?`. Port from old. Implementations (`SystemServiceVerification`, `ClipboardVerification`) have `Context` dependency — they move to `:feature:settings` (Phase 10) or `:sdk:ui` (Phase 3). Only the interface and result types are Phase 2
- `SetupDefinition` extension functions: `asSetup()`, `isRequirement`, `isDisplay`, `isInput`, `getDefaultValue()`
- `SetupEvaluator` — interface only in Phase 2 (Phase 7/10 implements). `fun evaluate(schema: List<SetupPageDefinition>): SetupResult`

**Settings system:**

- `SettingDefinition<T>` sealed interface with concrete types: `BooleanSetting`, `IntSetting`, `FloatSetting`, `StringSetting`, `EnumSetting<E>`, `TimezoneSetting`, `DateFormatSetting`, `UriSetting`, `AppPickerSetting`, `SoundPickerSetting`, `InstructionSetting`, `InfoSetting`. All implement `Gated`
- **`EnumSetting.optionPreviews` stripped** — old code had `@Composable` lambda for preview rendering. This is a Compose dependency that cannot exist in `:sdk:contracts` (no Compose compiler). Preview rendering moves to `:sdk:ui` (Phase 3) as a separate registry or extension function. Packs register previews in their module, not in the setting definition
- Settings enums (required by widget settings schemas, must ship with `SettingDefinition`): `SizeOption`, `TimezonePosition`, `DateLayoutOption`, `DateFormatOption`, `InfoCardLayoutMode`, `SoundType`, `InfoStyle`, `InstructionAction` sealed interface (`OpenSystemSettings`, `OpenUrl`, `LaunchApp`)

**Notification contracts (per platform.md — all explicitly `:sdk:contracts` types):**

- `InAppNotification` sealed interface: `Toast(id, priority, timestamp, alertProfile?, message, durationMs)`, `Banner(id, priority, timestamp, alertProfile?, title, message, actions: ImmutableList<NotificationAction>, dismissible)`
- `NotificationPriority` enum: `CRITICAL`, `HIGH`, `NORMAL`, `LOW`
- `AlertProfile` `@Immutable` data class: `mode: AlertMode`, `soundUri: String?`, `ttsMessage: String?`, `vibrationPattern: ImmutableList<Long>?`
- `AlertMode` enum: `SILENT`, `VIBRATE`, `SOUND`
- `NotificationAction` `@Immutable` data class: `label: String`, `actionId: String`
- `AlertEmitter` interface: `suspend fun fire(profile: AlertProfile): AlertResult`. Implementation (`AlertSoundManager`) is `@Singleton` in `:app` (Phase 6)
- `AlertResult` enum: `PLAYED`, `SILENCED`, `FOCUS_DENIED`, `UNAVAILABLE`

**Theme contracts:**

- `ThemeSpec` interface (metadata only, no Compose types): `themeId: String`, `displayName: String`, `isDark: Boolean`, `packId: String?`. Extends `Gated`
- `ThemeProvider` interface: `val packId: String`, `fun getThemes(): List<ThemeSpec>`
- `AutoSwitchMode` enum: `LIGHT`, `DARK`, `SYSTEM`, `SOLAR_AUTO`, `ILLUMINANCE_AUTO` (F4.4). Used by `ThemeCoordinator` (Phase 7) and `ThemeAutoSwitchEngine` (Phase 5). Simple enum belongs in `:sdk:contracts` alongside `ThemeSpec`
- Note: `DashboardThemeDefinition` (full runtime type with `Brush`, `Color`) lives in **`:sdk:ui`** (Phase 3) — NOT `:core:design`. Packs implement `ThemeProvider` and return concrete `DashboardThemeDefinition` extending `ThemeSpec`; packs depend on `:sdk:*` only, never `:core:*`. `:core:design` consumes `DashboardThemeDefinition` from `:sdk:ui`
- `ThemeSchema`, `ThemeColors`, `ThemeGradients`, `GradientSpec` (JSON serialization types) — deferred to Phase 5 (`:core:design` — these are shell-internal parsing types, packs provide themes as JSON files loaded by the shell). They have `toBrush()` methods requiring Compose

**Registry interfaces (consumed by `:sdk:observability` in Phase 3, implemented in Phase 7):**

- `WidgetRegistry` interface: `fun getAll(): Set<WidgetRenderer>`, `fun findByTypeId(typeId: String): WidgetRenderer?`, `fun getTypeIds(): Set<String>`. Used by `MetricsCollector` (Phase 3) for pre-populating per-widget tracking maps. Implementation wraps Hilt `Set<WidgetRenderer>` in Phase 7
- `DataProviderRegistry` interface (F3.7): `fun getAll(): Set<DataProvider<*>>`, `fun findByDataType(dataType: String): List<DataProvider<*>>`, `fun getFiltered(entitlementCheck: (String) -> Boolean): Set<DataProvider<*>>`. Entitlement-filtered view per F3.7. Used by `MetricsCollector` and `WidgetHealthMonitor` (Phase 3). Implementation in Phase 7

**Entitlement contracts:**

- `Gated` interface: `requiredAnyEntitlement: Set<String>?` (changed from old `List<String>?`). OR-logic: any matching entitlement grants access
- `Gated.isAccessible(hasEntitlement: (String) -> Boolean): Boolean` — extension function
- `EntitlementManager` interface — **minimal V1**: `hasEntitlement(id: String): Boolean`, `getActiveEntitlements(): Set<String>`, `entitlementChanges: Flow<Set<String>>`. Defer `purchaseProduct()` / `restorePurchases()` to Phase 10 when Play Billing integration shapes the API — premature commitment now would force `:sdk:contracts` modification later
- `Entitlements` constants: `FREE = "free"`, `THEMES = "themes"`, `PLUS = "plus"` (extensible by packs)

**KSP annotations:**

- `@DashboardWidget(typeId, displayName, icon)` — `@Retention(SOURCE)`. Phase 4 builds the processor. `typeId` format is `{packId}:{widget-name}` — the KSP processor (Phase 4) derives `packId` by parsing before the colon. No separate `packId` annotation field or KSP arg needed
- `@DashboardDataProvider(localId, displayName, description)` — `@Retention(SOURCE)`. Phase 4 builds the processor
- `@DashboardSnapshot(dataType)` — `@Retention(SOURCE)`. Phase 4 validates: no duplicate `dataType`, `@Immutable` required, only `val` properties, implements `DataSnapshot`
- Note: `@AgenticCommand` lives in `:core:agentic` (Phase 6), not `:sdk:contracts`

**Pack manifest:**

- `DashboardPackManifest` — simplified from old codebase. KSP generates manifests from annotations (Phase 4), so the data class is primarily for runtime introspection. Retain: `packId`, `displayName`, `description`, `version`, `widgets: List<PackWidgetRef>`, `themes: List<PackThemeRef>`, `dataProviders: List<PackDataProviderRef>`, `category: PackCategory`, `entitlementId`

**Explicitly deferred from Phase 2 (types that live in `:sdk:contracts` per architecture but aren't needed until their first consumer):**

- `ProfileDescriptor`, `ProfileTrigger` — deferred to Phase 7. No pack needs these before Phase 9+ (F1.31 is Deferred). Also has `ImageVector?` dependency that needs the same icon-name treatment before inclusion
- `DeviceManagement`, `DeviceServiceRegistry`, `CompanionDeviceHandler`, device model types (`PairedDevice`, `PresentDevice`, `ConnectedDevice`) — deferred to Phase 5/7. First consumer is Phase 9 (sg-erp2). `SetupDefinition.DeviceScan` references `handlerId: String`, not the full interface
- `ConnectionNotifier` — deferred to Phase 7, shell internal
- `DashboardWidgetInstance`, `WidgetSizeSpec` — deferred to Phase 5, persistence/layout model
- `InfoCardSettings` helper — include in Phase 2 (`:sdk:contracts`). Pure parser/factory methods constructing `List<SettingDefinition<*>>` — zero Compose deps, co-located with `SettingDefinition` in old `core/plugin-api`. Not a rendering helper

**Explicitly dropped (old types superseded by new architecture):**

- `DQXNDispatchers` interface — redundant with qualifier annotations (old `OversteerDispatchers.kt` never used via `@Binds` anyway)
- `@DataContract`, `@RequiresData` annotations — replaced by typed `compatibleSnapshots: Set<KClass<out DataSnapshot>>`
- `@PackResourceMarker`, `@SettingsSchema`, `@ValidConstraints` — old KSP markers replaced by new annotation system
- `@ThemePackMarker` — replaced by `ThemeProvider` Hilt multibinding
- `DataSnapshot` as concrete `data class(values: Map<String, Any?>)` — replaced by non-sealed interface + typed `@DashboardSnapshot` subtypes
- `EnumSetting.optionPreviews` (`@Composable` lambda) — Compose dependency, replacement registry in Phase 3 `:sdk:ui`
- Old `DashboardPackManifest` field structure — old refs `PackWidgetRef`/`PackThemeRef`/`PackDataProviderRef` replaced. **The concept survives as a new `DashboardPackManifest`** (see deliverables above) with updated fields; it's the old type's structure that's dropped, not the concept
- `DynamicSizeProvider` — already `@Deprecated` in old code, replaced by `SettingsAwareSizer`
- `DataSchema` / `DataFieldSpec` — staleness threshold migrated to `DataProvider.firstEmissionTimeout` + `DataFieldSpec` on `DataSchema`; the rest is superseded by typed snapshots. **Note: `DataSchema.stalenessThresholdMs` must survive** — it's explicitly retained on `DataProvider<T>` per Phase 2 deliverables. Only the `Map<String, Any?>` runtime type-check machinery is dropped

## `:sdk:contracts` testFixtures

- `WidgetRendererContractTest` — abstract JUnit4 test base (requires `ComposeContentTestRule` for `Render()` tests). Every widget extends this. Inherited assertions (see Contract Test Specification below)
- `DataProviderContractTest` — abstract JUnit5 test base. Every provider extends this
- `TestDataProvider<T>` — configurable fake that applies `ProviderFault` transformations to a base flow. Supports mid-stream fault injection

**Note:** `ProviderFault` sealed interface lives in `:sdk:contracts` **main source set** (not testFixtures) — `ChaosProviderInterceptor` (Phase 9) needs it at debug runtime. Variants: `Kill`, `Delay(millis)`, `Error(exception)`, `ErrorOnNext`, `Corrupt(transform)`, `Flap(onMillis, offMillis)`, `Stall`. `TestDataProvider` stays in testFixtures (test-only)
- `TestWidgetRenderer` — minimal stub implementing `WidgetRenderer`. Used to run concrete contract tests in Phase 2 (validates the abstract test base itself)
- `TestWidgetScope` — test-only `WidgetCoroutineScope` wrapping `TestScope` from `kotlinx-coroutines-test`. Provides `LocalWidgetScope` in contract tests. Required by `WidgetRendererContractTest` jqwik property test (#9) which composes the widget with `LocalWidgetScope provides TestWidgetScope()`
- `testWidget()`, `testTheme()`, `testDataSnapshot()` factories

## Contract Test Specification

**`WidgetRendererContractTest` inherited assertions:**

| # | Test | Severity | Assertion |
|---|---|---|---|
| 1 | `typeId follows packId-colon-name format` | Critical | Regex `[a-z]+:[a-z][a-z0-9-]+` |
| 2 | `render does not throw with WidgetData.Empty` | Critical | Compose test rule, no exception |
| 3 | `render does not throw with WidgetData.Unavailable` | Critical | Distinct sentinel from Empty |
| 4 | `accessibility description is non-empty for empty data` | Critical | `accessibilityDescription(WidgetData.Empty).isNotBlank()` |
| 5 | `accessibility description changes with real data` | High | `accessibilityDescription(Empty) != accessibilityDescription(testData)` |
| 6 | `compatibleSnapshots entries are DataSnapshot subtypes` | Critical | Each `KClass` is assignable to `DataSnapshot` |
| 7 | `settingsSchema keys are unique` | Critical | `schema.map { it.key }.distinct().size == schema.size` |
| 8 | `settingsSchema defaults valid for type constraints` | Critical | `IntSetting.default in min..max`, `EnumSetting.default in options`, etc. |
| 9 | `render survives arbitrary settings (jqwik property-based)` | Critical | Random settings map → no crash |
| 10 | `getDefaults returns positive dimensions` | High | `widthUnits > 0 && heightUnits > 0` |
| 11 | `getDefaults respects aspect ratio if declared` | High | If `aspectRatio != null`, width/height ratio matches |
| 12 | `gating defaults to free when requiredAnyEntitlement is null` | High | `isAccessible { false } == true` |
| 13 | `displayName is non-blank` | Medium | `displayName.isNotBlank()` |
| 14 | `description is non-blank` (F2.20) | Medium | `description.isNotBlank()` |

Abstract methods pack tests must implement:
- `fun createRenderer(): WidgetRenderer` (required)
- `fun createTestWidgetData(): WidgetData` (required — for accessibility variation test)

**JUnit4/JUnit5 framework split:** `WidgetRendererContractTest` is a JUnit4 abstract class (required by `ComposeContentTestRule`). The jqwik property test (#9, "render survives arbitrary settings") lives in a separate abstract class `WidgetRendererPropertyTest` (JUnit5/jqwik). Pack widget tests extend BOTH: `class SpeedometerRendererContractTest : WidgetRendererContractTest()` and `class SpeedometerRendererPropertyTest : WidgetRendererPropertyTest()`. Two test files per widget in testFixtures consumption. `junit-vintage-engine` (from `dqxn.android.test`) ensures both run on the JUnit5 platform.

**`DataProviderContractTest` inherited assertions:**

| # | Test | Severity | Assertion |
|---|---|---|---|
| 1 | `emits within firstEmissionTimeout` | Critical | `withTimeout(provider.firstEmissionTimeout) { provideState().first() }` |
| 2 | `emitted type matches declared snapshotType` | Critical | `provideState().first()::class == snapshotType` — prevents silent null from `as? T` |
| 3 | `emitted snapshot has non-zero timestamp` | Critical | `snapshot.timestamp > 0` — required for staleness detection |
| 4 | `respects cancellation without leaking` | Critical | Cancel collector, `advanceUntilIdle()`, verify `testScheduler.isIdle` (no pending tasks — catches leaked coroutines). Note: fd-count approach deliberately dropped as unreliable; runtime leak detection via LeakCanary and `WidgetScopeBypass` lint rule |
| 5 | `snapshotType is a valid DataSnapshot subtype` | High | Not `DataSnapshot::class` itself |
| 6 | `connectionState emits at least one value` | High | `connectionState.first()` doesn't hang |
| 7 | `connectionErrorDescription null when connected` | High | Consistency between connection flows |
| 8 | `setupSchema definitions have unique IDs` | High | Same logic as settingsSchema key uniqueness |
| 9 | `subscriberTimeout is positive` | High | `> Duration.ZERO` |
| 10 | `firstEmissionTimeout is positive` | High | `> Duration.ZERO` |
| 11 | `gating defaults correctly` | High | Same as WidgetRenderer gating test |
| 12 | `multiple concurrent collectors receive same data` | Medium | Two collectors, both receive emissions |

## `:sdk:contracts` unit tests (not in testFixtures — test concrete types)

- **`WidgetData` multi-slot tests (Critical — untested = silent null data in every widget):**
  - `snapshot returns typed value for matching KClass`
  - `snapshot returns null for missing KClass`
  - `snapshot returns null for wrong KClass`
  - `withSlot adds new slot without removing existing`
  - `withSlot replaces existing slot of same KClass`
  - `hasData false for Empty, true with any slot`
  - `Empty and Unavailable are distinct` (timestamp 0 vs -1)
  - jqwik: `withSlot accumulation is order-independent for distinct KClasses`
  - jqwik: `withSlot is idempotent for same KClass` (last write wins)

- **`Gated.isAccessible()` tests (High — monetization gate logic):**
  - `null entitlement list means free` → `isAccessible { false } == true`
  - `empty set means free` → `isAccessible { false } == true`
  - `single required, user has it` → true
  - `single required, user lacks it` → false
  - `OR logic: user has one of two` → true
  - `OR logic: user has neither` → false

- **`SettingDefinition` construction tests (High — 12 subtypes, each needs validation):**
  - Each of 12 types constructs with required fields
  - `IntSetting` min/max constraints: `default in min..max`
  - `FloatSetting` step is positive
  - `EnumSetting` options non-empty, default in options
  - `visibleWhen` predicate functions correctly
  - `Gated` inheritance works on each subtype
  - Key uniqueness utility function

- **`AppResult<T>` tests (port 9 existing + expand):**
  - Port existing: Ok/Err, map, flatMap, getOrNull, getOrElse
  - Add: `onSuccess` invoked for Ok / not for Err
  - Add: `onFailure` invoked for Err / not for Ok
  - Add: `isSuccess`/`isFailure` properties
  - Add: `errorOrNull` returns error for Err, null for Ok
  - Add: `getOrElse` lambda variant receives the error
  - Update test data: replace old OBU-specific `AppError` variants with generalized ones

- **`ConnectionStateMachineTest` (port 8 + exhaustive + jqwik property-based):**
  - Port existing 8 transition tests
  - Exhaustive transition matrix (6 states × 7 events = 42 cells — each valid→new or invalid→stay)
  - New: `SearchTimeout from Searching → Error`
  - New: `ConnectionFailed increments retry counter`
  - New: `After 3 ConnectionFailed → Idle` (max retries exhausted)
  - New: `ConnectionSuccess resets retry counter`
  - jqwik: `no event sequence reaches an illegal state`
  - jqwik: `all event sequences from Idle terminate` (reaches Connected, Idle, or Error)
  - jqwik: `retry counter never exceeds max`
  - jqwik: `state machine is deterministic` (same sequence → same final state)
  - jqwik: `reset always returns to Idle regardless of current state`

- **`ProviderFault` transformation tests:**
  - Each variant: `Kill`, `Delay`, `Error`, `ErrorOnNext`, `Corrupt`, `Flap`, `Stall`
  - `TestDataProvider` with no fault emits base flow (pass-through)
  - `TestDataProvider` fault injection mid-stream
  - `TestDataProvider` integration: fault applied → verify observable behavior

- **`WidgetStyle` defaults test (Medium):** Verify defaults match documented values: `NONE, 1.0, false, false, 25, 0, 0`
- **`WidgetContext.DEFAULT` test (Low):** UTC, US locale, "US" region
- **`SetupDefinition` tests (port 14 existing):** All 7 sealed subtypes, Setting wrapper, `asSetup()` extensions, `getDefaultValue()`. Update for icon-name changes (no `ImageVector`)

## KSP processor dependency clarification

`:codegen:plugin` (Phase 4) uses `dqxn.kotlin.jvm` (pure JVM). It **cannot** have a Gradle module dependency on `:sdk:contracts` (Android library). KSP processors read annotation metadata from the compilation environment (`KSAnnotation`), not from a compile dependency. The architecture doc's `codegen:plugin → :sdk:contracts` arrow is conceptual (reads annotations defined in), NOT a Gradle dependency. Phase 4 does not need modification for this.

## `compileOnly(compose.runtime)` pattern

Phase 2 establishes the pattern for non-UI modules needing `@Immutable`: add `compileOnly(libs.compose.runtime)` (standalone artifact, not BOM-mediated). Phase 3's `:sdk:observability` will need the same pattern. Document in Phase 2 implementation notes.

**Ported from old:** `core/plugin-api/*` — but every interface changes signature significantly. Key transformations: `DataSnapshot` from `Map<String, Any?>` to typed `@DashboardSnapshot` subtypes (new design informed by old data shapes). `WidgetData` from string-keyed to `KClass`-keyed multi-slot. `WidgetRenderer.Render()` drops `widgetData` param (read via `LocalWidgetData`). `SetupDefinition` subtypes lose `ImageVector` and `Context` dependencies (replaced with string icon names and declarative `ServiceType` enums). `EnumSetting.optionPreviews` (`@Composable`) stripped from contracts. `Gated.requiredAnyEntitlement` changes from `List<String>?` to `Set<String>?`. `SettingDefinition` ports cleanly. `ConnectionStateMachine` ports nearly verbatim (implementation) with significantly expanded test coverage.

**Validation:**

1. `./gradlew :sdk:contracts:compileDebugKotlin --console=plain` — Kotlin compilation succeeds with `compileOnly(compose.runtime)` resolving
2. `./gradlew :sdk:contracts:assembleDebug --console=plain` — Android library artifact produced
3. `./gradlew :sdk:common:assembleDebug --console=plain` — common module compiles with Hilt
4. `WidgetData` multi-slot unit tests pass (slot insertion, retrieval, type safety, jqwik accumulation)
5. `Gated.isAccessible()` logic tests pass
6. `SettingDefinition` construction and constraint validation tests pass for all 12 subtypes
7. `AppResult<T>` tests pass (ported + expanded `onSuccess`/`onFailure`)
8. `ConnectionStateMachineTest` passes including exhaustive transition matrix and jqwik property-based tests
9. `ProviderFault` transformation tests pass
10. `WidgetRendererContractTest` and `DataProviderContractTest` abstract classes in testFixtures produce a JAR
11. At least one concrete contract test subclass executes in Phase 2: `TestWidgetRendererContractTest` and `TestDataProviderContractTest` using testFixtures stubs — validates the abstract test base itself runs, not just compiles
12. testFixtures consumer verification: a throwaway `testImplementation(testFixtures(project(":sdk:contracts")))` in `:sdk:common` (or dedicated test module) imports a type from testFixtures — catches JAR packaging issues 6 phases before Phase 8
13. Test output XML present in `build/test-results/` — confirms JUnit5 platform actually executed (catches silent test skip under AGP 9.0.1)
