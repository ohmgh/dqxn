# Phase 10: Settings Foundation + Setup UI - Research

**Researched:** 2026-02-25
**Domain:** Schema-driven settings UI, setup wizard, overlay navigation, widget picker
**Confidence:** HIGH

## Summary

Phase 10 builds the UI layer for all overlay surfaces: the schema-driven settings row system, widget/global settings screens, setup wizard with BLE device pairing, widget picker, and overlay navigation routes. This is ~35 composables and ~4,000 lines of code across `:feature:settings` (currently an empty stub) and `:feature:dashboard` (OverlayNavHost route population).

The foundation types already exist in `:sdk:contracts` (all 12 `SettingDefinition` subtypes, all 7 `SetupDefinition` subtypes, `SetupPageDefinition`, `SetupEvaluator` interface, `ProviderSettingsStore` interface) and `:data` (concrete stores, repositories). The work is predominantly Compose UI: rendering those schemas as interactive composables, wiring overlay navigation, and extracting the `DeviceScanStateMachine` as a testable non-UI class.

**Primary recommendation:** Split into 5-6 focused plans organized by UI surface: (1) settings infrastructure + SettingRowDispatcher, (2) sub-overlay pickers, (3) setup wizard + DeviceScanStateMachine, (4) widget settings sheet + widget picker, (5) main settings + analytics consent + delete-all, (6) overlay navigation wiring + integration tests. Each plan should be tight enough for single-agent execution without context overflow.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F2.7 | Widget picker with live previews, descriptions, data type icons, entitlement badges | Widget picker composable using `WidgetRegistry.getAll()` + `WidgetSpec.description` + `Gated` interface. `StubEntitlementManager` treats all as free |
| F2.8 | Per-widget settings sheet (3 pages: Feature / Data Source / Info) | `WidgetSettingsSheet` with HorizontalPager, tabs read `WidgetSpec.settingsSchema`, data source from `DataProviderRegistry`, info from `WidgetSpec` |
| F2.9 | Schema-driven settings UI (toggles, button groups, dropdowns, hub routes) | `SettingRowDispatcher` dispatching all 12 `SettingDefinition` subtypes to row composables |
| F3.3 | `SetupPageDefinition` -- declarative multi-page setup wizard | Types exist in `:sdk:contracts`. UI rendering in `SetupSheet` with `AnimatedContent` page transitions |
| F3.4 | Setup definition types: RuntimePermission, SystemServiceToggle, DeviceScan, Instruction | Types exist. Card composables rendering each subtype with pass/fail semantics |
| F3.5 | `SetupEvaluator` -- checks provider readiness against setup requirements | Interface exists in `:sdk:contracts`. Implementation needed in `:feature:dashboard` or `:feature:settings` with persistence variant |
| F3.14 | Provider setup failure UX: inline error with retry, "Setup Required" overlay, permanently denied -> system settings | Permission card 3-state logic, DeviceScanStateMachine retry, widget status overlay integration |
| F8.1 | Entitlement system: `free`, `themes` tiers | `StubEntitlementManager` already provides all-free. Gating checks in UI for lock icons |
| F8.7 | Widget picker preview-regardless-of-entitlement, gate-at-persistence | Picker shows all widgets with lock badges on gated ones. Add button checks entitlement |
| F8.9 | Refund UX: EntitlementRevoked overlay, locked state in selectors | `WidgetStatusCache` already handles overlays. Picker shows locked icon via `Gated.isAccessible()` |
| F10.4 | Minimum touch target 76dp for all interactive elements | Applied across all Phase 10 composables |
| F12.5 | Analytics consent: opt-in toggle in Settings > Data & Privacy | New `analyticsConsent` preference in `UserPreferencesRepository`. Toggle calls `AnalyticsTracker.setEnabled()` |
| F14.2 | Diagnostics navigation from Settings | Settings > Diagnostics navigates to `:feature:diagnostics` (stub route, full UI in Phase 11) |
| F14.4 | Delete All Data: clear all DataStores, revoke analytics ID | Inject all store instances, call clear/reset on each. `ConfirmationDialog` with destructive styling |
| NF29 | Required hardware: `companion_device_setup` | Already declared in AndroidManifest.xml. CDM API usage in DeviceScanStateMachine |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose + Material 3 | BOM-managed (AGP 9) | All UI composables | Project standard, `dqxn.android.compose` plugin |
| Navigation Compose | In version catalog | Type-safe overlay routing | Already in `dqxn.android.feature` deps |
| kotlinx.serialization | Kotlin 2.3.10 | Route serialization | Already in project for type-safe nav |
| Hilt | In version catalog | DI for settings ViewModels | Already in `dqxn.android.feature` deps |
| Preferences DataStore | In version catalog | Settings persistence | Already implemented in `:data` |
| kotlinx-collections-immutable | In version catalog | `ImmutableList`/`ImmutableMap` in all UI types | Project requirement for Compose stability |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit5 (mannodermaus) | In version catalog | Unit tests for state machines, dispatchers | All non-Compose tests |
| MockK | In version catalog | Mocking coordinators, stores, registries | Test isolation |
| Truth | In version catalog | Assertions | All tests |
| Turbine | In version catalog | Flow testing for settings state | Flow emission verification |
| ComposeTestRule | Platform | Semantics-based UI verification | Compose rendering tests |

### No Additional Dependencies Needed

The `dqxn.android.feature` convention plugin already provides:
- `dqxn.android.library` + `dqxn.android.compose` + `dqxn.android.hilt` + `dqxn.android.test`
- `:sdk:contracts`, `:sdk:common`, `:sdk:ui`, `:sdk:observability`
- `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `hilt-navigation-compose`, `navigation-compose`

`:feature:settings` needs additional explicit deps: `:core:design` (tokens, motion, ConfirmationDialog) and `:data` (repository injection for Delete All Data, analytics consent).

## Architecture Patterns

### Recommended Project Structure

```
feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/
├── SettingsNavigation.kt              # SettingNavigation sealed interface (4 events)
├── row/                               # Setting row composables
│   ├── SettingRowDispatcher.kt        # 12-branch when dispatch
│   ├── BooleanSettingRow.kt
│   ├── EnumSettingRow.kt              # 3 render modes: chips/dropdown/preview
│   ├── IntSettingRow.kt
│   ├── FloatSettingRow.kt
│   ├── StringSettingRow.kt
│   ├── InfoSettingRow.kt
│   ├── InstructionSettingRow.kt
│   ├── AppPickerSettingRow.kt
│   ├── DateFormatSettingRow.kt
│   ├── TimezoneSettingRow.kt
│   ├── SoundPickerSettingRow.kt
│   └── SettingComponents.kt           # SettingLabel, SelectionChip, PreviewSelectionCard
├── picker/                            # Sub-overlay pickers
│   ├── TimezonePicker.kt
│   ├── DateFormatPicker.kt
│   ├── AppPicker.kt
│   └── SoundPicker.kt                # ActivityResultLauncher integration
├── setup/                             # Setup wizard
│   ├── SetupSheet.kt                 # Fullscreen paginated flow
│   ├── SetupDefinitionRenderer.kt    # 7-type dispatch
│   ├── SetupNavigationBar.kt         # Done button + page indicators
│   ├── SetupPermissionCard.kt        # 3 states: granted/can-request/denied
│   ├── SetupToggleCard.kt            # SystemServiceToggle + SystemService
│   ├── DeviceScanCard.kt             # UI layer for BLE scan
│   ├── DeviceScanStateMachine.kt     # Pure logic, ~300 lines, 5 states
│   ├── PairedDeviceCard.kt           # 3-state border, forget dialog
│   ├── DeviceLimitCounter.kt
│   ├── InstructionCard.kt
│   └── InfoCard.kt
├── widget/                            # Widget-specific settings
│   ├── WidgetSettingsSheet.kt         # 3-tab pager
│   ├── FeatureSettingsContent.kt      # Feature tab (schema rendering)
│   ├── DataProviderSettingsContent.kt # Data source selection
│   └── WidgetInfoContent.kt          # Info + issues + NF-D1 disclaimer
├── main/                              # Main settings
│   ├── MainSettings.kt               # 4 sections
│   ├── DeleteAllDataDialog.kt        # ConfirmationDialog with destructive styling
│   └── AnalyticsConsentDialog.kt     # Opt-in consent explanation
├── overlay/                           # Overlay infrastructure
│   ├── OverlayScaffold.kt            # Shared container for all overlay composables
│   └── OverlayTitleBar.kt            # Title + close button
└── WidgetPicker.kt                    # Widget selection grid
     PackBrowserContent.kt             # Pack list with shared elements

feature/dashboard/...layer/OverlayNavHost.kt   # Route population (modify existing)
feature/dashboard/...DashboardScreen.kt        # Wire navigation callbacks (modify existing)
```

### Pattern 1: SettingRowDispatcher (12-Branch Schema Dispatch)

**What:** Central dispatch hub routing `SettingDefinition` subtypes to row composables with three-layer visibility gating.

**When to use:** Wherever settings schemas are rendered (widget settings tab, setup flow Setting wrapper).

**Architecture:**
```kotlin
@Composable
fun SettingRowDispatcher(
    definition: SettingDefinition<*>,
    currentValue: Any?,
    currentSettings: Map<String, Any?>,
    entitlementManager: EntitlementManager,
    theme: DashboardThemeDefinition,
    onValueChanged: (String, Any?) -> Unit,
    onNavigate: ((SettingNavigation) -> Unit)? = null,
) {
    // Layer 1: hidden check (hard skip)
    if (definition.hidden) return

    // Layer 2: visibleWhen evaluation (null = always visible)
    val isVisible = definition.visibleWhen?.invoke(currentSettings) != false

    // Layer 3: entitlement gating
    val hasEntitlement = definition.isAccessible(entitlementManager)

    AnimatedVisibility(
        visible = isVisible && hasEntitlement,
        enter = DashboardMotion.expandEnter,
        exit = DashboardMotion.expandExit,
    ) {
        when (definition) {
            is SettingDefinition.BooleanSetting -> BooleanSettingRow(...)
            is SettingDefinition.EnumSetting<*> -> EnumSettingRow(...)
            // ... all 12 subtypes
            else -> SettingLabel(definition.label, definition.description, theme)
        }
    }
}
```

### Pattern 2: DeviceScanStateMachine (Pure Logic Extraction)

**What:** Extract the 5-state BLE device scan state machine from UI into a testable class.

**When to use:** BLE device pairing in setup flow. The single highest-complexity component in Phase 10.

**Architecture:**
```kotlin
// Pure logic, no Compose dependency
class DeviceScanStateMachine {
    sealed interface ScanState {
        data object PreCDM : ScanState
        data object Waiting : ScanState
        data class Verifying(
            val device: BluetoothDevice,
            val attempt: Int,
            val maxAttempts: Int = 3,
        ) : ScanState
        data class Success(val device: BluetoothDevice) : ScanState
        data class Failed(
            val device: BluetoothDevice?,
            val error: String,
        ) : ScanState
    }

    private val _state = MutableStateFlow<ScanState>(ScanState.PreCDM)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun onScanStarted() { _state.value = ScanState.Waiting }
    fun onDeviceFound(device: BluetoothDevice) { ... }
    fun onVerificationResult(success: Boolean, error: String? = null) { ... }
    fun onUserCancelled() { _state.value = ScanState.PreCDM }
    fun reset() { _state.value = ScanState.PreCDM }
}
```

### Pattern 3: evaluationTrigger Counter for Re-Evaluation

**What:** Counter-increment pattern to force LaunchedEffect re-evaluation after async external events.

**When to use:** Setup flow where permissions/services change outside composition.

**Critical because:** `LaunchedEffect(provider)` won't re-run when only external state changes. Three triggers increment: initial composition, ON_RESUME, device pairing success.

```kotlin
var evaluationTrigger by remember { mutableIntStateOf(0) }

// Re-evaluate on lifecycle resume (returns from system settings)
LifecycleResumeEffect(Unit) {
    evaluationTrigger++
    onPauseOrDispose {}
}

LaunchedEffect(provider, evaluationTrigger) {
    satisfiedDefinitions = evaluator.evaluateWithPersistence(...)
        .filter { it.satisfied }.map { it.definitionId }.toSet()
}
```

### Pattern 4: Overlay Route Navigation

**What:** Type-safe routes for overlay navigation. The current OverlayNavHost uses string-based routing.

**Architecture choice:** The platform.md architecture specifies `sealed interface Route` with `@Serializable`. However, the existing OverlayNavHost uses string routes (`ROUTE_EMPTY = "empty"`). Phase 10 should migrate to type-safe routes with `@Serializable` data classes for the 4 new routes while keeping backward compatibility with the empty route.

```kotlin
// Routes as @Serializable objects/classes for type-safe navigation
@Serializable data object WidgetPickerRoute
@Serializable data object SettingsRoute
@Serializable data class WidgetSettingsRoute(val widgetId: String)
@Serializable data class SetupRoute(val providerId: String)
```

**Dismissal patterns from replication advisory:**
- Simple destinations: `popBackStack()`
- Root-level destinations (Settings, WidgetSettings): `navigate(Empty) { popUpTo<Route> { inclusive = true } }` -- pops entire sub-stack

### Pattern 5: Two-Layer Dispatcher

**What:** SetupDefinitionRenderer dispatches SetupDefinition subtypes; within it, the Setting wrapper delegates to SettingRowDispatcher.

**Critical:** Both dispatchers apply visibility/entitlement gating independently. The Setting wrapper's `visibleWhen` is checked first (setup-context), then inner `SettingDefinition`'s `visibleWhen` (definition-level). This intentional double-gating is load-bearing.

### Anti-Patterns to Avoid

- **God-state for settings:** Don't create a single `SettingsState` data class. Each settings surface reads its own slice from the relevant coordinator/store.
- **Compose try-catch for error boundaries:** Compose forbids try-catch around `@Composable` calls. Use state-based error tracking (already established in Phase 7).
- **`popBackStack()` for WidgetSettings dismissal:** Use `navigate(Empty) { popUpTo<WidgetSettings> { inclusive = true } }` per replication advisory. Timing mismatch on animations otherwise.
- **`UnconfinedTestDispatcher`:** Forbidden per project decisions. All tests use `StandardTestDispatcher`.
- **Slider for FloatSetting:** Discrete selection chips/presets, not sliders. Sliders conflict with pager swipe gestures.
- **`Thread.sleep` or real delays in tests:** Use `testScheduler.advanceTimeBy()`.
- **Hardcoded color values for InfoStyle:** Use design tokens from `:core:design`. Gap: error/warning/success tokens don't exist yet in the theme model -- need to define them.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Animation specs | Custom spring/tween values | `DashboardMotion.*` from `:core:design` | Already defined, consistent across app |
| Spacing values | Inline dp values | `DashboardSpacing.*` from `:core:design` | Prevents inconsistencies flagged in replication advisory |
| Card radii | Inline `8.dp`/`12.dp`/`16.dp` | `CardSize.SMALL/MEDIUM/LARGE.cornerRadius` | Known inconsistency fix from advisory |
| Typography roles | Raw M3 `MaterialTheme.typography.*` | `DashboardTypography.*` from `:core:design` | Consistent with design system |
| Text emphasis | Inline `alpha = 0.6f` | `TextEmphasis.Medium` (0.7f) | Fix known inconsistency from advisory |
| Permission handling | Accompanist | `rememberLauncherForActivityResult` + `ActivityResultContracts.RequestMultiplePermissions` | Accompanist deprecated, native API sufficient |
| Sound picker | Custom file picker | System `ACTION_RINGTONE_PICKER` via `ActivityResultLauncher` | Platform-standard, handles all system sounds |
| Confirmation dialogs | Custom AlertDialog | `ConfirmationDialog` pattern from `:core:design` with `DashboardMotion.dialogEnter/Exit` | Consistent animation treatment |

**Key insight:** Most of Phase 10's UI complexity is in correctly dispatching schema types and managing state across async boundaries (permissions, BLE pairing). The composables themselves are relatively simple once the dispatch and state patterns are established.

## Common Pitfalls

### Pitfall 1: visibleWhen Evaluation During Initial Load
**What goes wrong:** `produceState` initializes to `emptyMap()`. Lambda checking `currentSettings["key"] == true` sees `null`, which evaluates to `false`, hiding rows that should be visible.
**Why it happens:** Settings map is loaded asynchronously; first composition sees empty state.
**How to avoid:** Ensure `visibleWhen` lambdas treat `null` as default-visible. The `!= false` evaluation in the dispatcher handles this (`null != false` is `true`).
**Warning signs:** Settings rows flickering on initial render.

### Pitfall 2: Permission Card False Permanent-Denial Detection
**What goes wrong:** Pre-request state (`shouldShowRationale = false` AND `allGranted = false`) is indistinguishable from permanent denial.
**Why it happens:** Both states have the same API return values.
**How to avoid:** Track `hasRequestedPermissions` as local state. Only classify as permanently denied when `hasRequestedPermissions == true` AND `shouldShowRationale == false` AND `allGranted == false`.
**Warning signs:** "Open Settings" button shown immediately before user has ever been asked.

### Pitfall 3: EnumSetting Serialization Comparison
**What goes wrong:** Settings map stores `Any?`. Enum values serialized as `.name` string. Direct `==` comparison between `String` and `Enum` fails.
**Why it happens:** ProviderSettingsStore uses type-prefixed string serialization.
**How to avoid:** Compare with `value == option || value?.toString() == option.name`.
**Warning signs:** Enum settings always showing as default despite stored value.

### Pitfall 4: CDM Not Available / ActivityNotFoundException
**What goes wrong:** `CompanionDeviceManager.associate()` throws on devices without CDM support or when the CDM service is unavailable.
**Why it happens:** Not all devices support CDM despite `companion_device_setup` feature declaration.
**How to avoid:** Wrap CDM association launch in try-catch for both `ActivityNotFoundException` and `UnsupportedOperationException`. Fall back to `Failed` state.
**Warning signs:** Crash on scan button tap on specific devices.

### Pitfall 5: Two BackHandler Instances in Setup Flow
**What goes wrong:** Unifying the two BackHandlers (page > 0 goes back, page == 0 dismisses) into a single handler breaks the exclusivity.
**Why it happens:** Compose `BackHandler` instances have priority ordering; the first enabled one consumes the event.
**How to avoid:** Keep two separate `BackHandler` instances. The exclusivity (only one active at a time) is load-bearing.
**Warning signs:** Back button either always dismisses or always goes to previous page.

### Pitfall 6: Settings Buttons Alpha-Dimmed But Still Tappable
**What goes wrong:** Using `enabled = false` on buttons disables tap handling entirely.
**Why it happens:** The design intentionally keeps buttons tappable when disabled (showing feedback) but visually dimmed.
**How to avoid:** Apply `alpha(0.5f)` modifier instead of `enabled = false`. Keep `onClick` handler active but check state internally.
**Warning signs:** Disabled buttons don't respond at all (should still be tappable with dimmed appearance).

### Pitfall 7: InstructionAction Dual Execution
**What goes wrong:** Splitting action execution to either the row composable or the navigation callback breaks either the launch or the verification tracking.
**Why it happens:** The row handles `OpenUrl`/`LaunchApp` locally, while the nav callback tracks `actionPerformedKeys` for ON_RESUME verification.
**How to avoid:** BOTH paths execute: row executes locally AND fires nav callback. Test both paths.
**Warning signs:** Instruction verification never shows "Verified" even after action completes.

### Pitfall 8: Module Dependency for :feature:settings
**What goes wrong:** `:feature:settings` needs `:core:design` and `:data` but the `dqxn.android.feature` convention plugin doesn't include them.
**Why it happens:** Convention plugin provides sdk modules only; core and data are feature-specific.
**How to avoid:** Explicitly add `:core:design` and `:data` to `:feature:settings/build.gradle.kts`.
**Warning signs:** Compilation errors for `DashboardMotion`, `DashboardSpacing`, `LayoutRepository`, etc.

### Pitfall 9: Analytics Consent Default State
**What goes wrong:** Analytics fire before user opts in, violating PDPA/GDPR.
**Why it happens:** `FirebaseAnalyticsTracker.enabled` defaults to `AtomicBoolean(true)`.
**How to avoid:** The `analyticsConsent` preference must default to `false` (opt-in model). App initialization must read consent preference and call `setEnabled(false)` if no consent recorded. The current `FirebaseAnalyticsTracker` defaults to `enabled = true` -- this must be changed or overridden at initialization.
**Warning signs:** Analytics events logged before consent dialog is shown.

## Code Examples

### SettingNavigation Sealed Interface

```kotlin
// :feature:settings
sealed interface SettingNavigation {
    data class ToTimezonePicker(
        val settingKey: String,
        val currentValue: String?,
    ) : SettingNavigation

    data class ToDateFormatPicker(
        val settingKey: String,
        val currentValue: DateFormatOption,
    ) : SettingNavigation

    data class ToAppPicker(
        val settingKey: String,
        val currentPackage: String?,
    ) : SettingNavigation

    data class OnInstructionAction(
        val settingKey: String,
        val action: InstructionAction,
    ) : SettingNavigation
}
```

### OverlayScaffold Pattern

```kotlin
@Composable
fun OverlayScaffold(
    title: String,
    overlayType: OverlayType,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = when (overlayType) {
        OverlayType.Hub -> RoundedCornerShape(0.dp)
        OverlayType.Preview -> RoundedCornerShape(
            topStart = CardSize.LARGE.cornerRadius,
            topEnd = CardSize.LARGE.cornerRadius,
        )
        OverlayType.Confirmation -> RoundedCornerShape(CardSize.LARGE.cornerRadius)
    }
    // ... scaffold with OverlayTitleBar and content
}
```

### Delete All Data Implementation

```kotlin
// Inject all store instances
class MainSettingsViewModel @Inject constructor(
    private val layoutRepository: LayoutRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val providerSettingsStore: ProviderSettingsStore,
    private val pairedDeviceStore: PairedDeviceStore,
    private val widgetStyleStore: WidgetStyleStore,
    private val connectionEventStore: ConnectionEventStore,
    private val analyticsTracker: AnalyticsTracker,
) {
    suspend fun deleteAllData() {
        // Clear each store
        // Note: UserPreferencesRepository needs a clear() method added
        // ProviderSettingsStore needs a clearAll() method (current API is per-provider)
        // LayoutRepository needs a clearAll() method
        analyticsTracker.setEnabled(false)
        // FirebaseAnalytics.resetAnalyticsData() via tracker extension
    }
}
```

### Widget Picker Grouping

```kotlin
@Composable
fun WidgetPicker(
    widgetRegistry: WidgetRegistry,
    entitlementManager: EntitlementManager,
    onSelectWidget: (WidgetRenderer) -> Unit,
    onDismiss: () -> Unit,
) {
    val widgets = remember { widgetRegistry.getAll() }
    val groupedByPack = remember(widgets) {
        widgets.groupBy { it.typeId.substringBefore(':') }
            .toImmutableMap()
    }
    // Staggered grid with pack headers, entitlement badges
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Accompanist permissions | Native `rememberLauncherForActivityResult` | Accompanist deprecated 2024 | One less dependency, same functionality |
| String-based NavHost routes | Type-safe `@Serializable` routes | Navigation Compose 2.8+ | Compile-time route validation |
| God-ViewModel settings dispatch | Schema-driven dispatcher + coordinator interaction | Phase 7 decomposition | Each coordinator owns its state slice |

**Deprecated/outdated:**
- Accompanist Permissions: Use native `ActivityResultContracts` with `rememberLauncherForActivityResult`
- `popBackStack()` for root overlays: Use `navigate(Empty) { popUpTo { inclusive = true } }` per replication advisory

## Open Questions

1. **Analytics consent default value in FirebaseAnalyticsTracker**
   - What we know: `FirebaseAnalyticsTracker` initializes `enabled = AtomicBoolean(true)`. F12.5 requires opt-in (default OFF).
   - What's unclear: Should the tracker default change, or should app initialization override it?
   - Recommendation: Add `analyticsConsent: Flow<Boolean>` to `UserPreferencesRepository` (default `false`). App init reads preference and calls `setEnabled()`. `FirebaseAnalyticsTracker` default stays `true` (Firebase SDK default), but app disables before any events fire. This is a Phase 10 wiring task: read consent on startup, gate analytics accordingly.

2. **Clear-all methods on repositories**
   - What we know: `ProviderSettingsStore.clearSettings()` is per-provider. `LayoutRepository` and `UserPreferencesRepository` don't have `clearAll()` methods.
   - What's unclear: Add methods to interfaces vs. direct DataStore access in the delete-all handler?
   - Recommendation: Add `suspend fun clearAll()` to `UserPreferencesRepository`, add a `suspend fun clearAll()` to `ProviderSettingsStore` interface (and impl), add `suspend fun clearAll()` to `LayoutRepository`. This keeps the abstraction clean and testable.

3. **SetupEvaluator implementation location**
   - What we know: Interface is in `:sdk:contracts`. Two evaluator variants needed (real-time and persistence-aware).
   - What's unclear: Should implementation live in `:feature:settings` or `:feature:dashboard`?
   - Recommendation: Concrete implementation in `:feature:settings` (or a shared location both features can access). The real-time variant is already used by widget status overlay in `:feature:dashboard`. Consider: interface in `:sdk:contracts`, implementation in `:data` or `:feature:dashboard` (where widget status overlay lives), shared via Hilt binding.

4. **Error/warning/success semantic color tokens**
   - What we know: Replication advisory flags missing tokens. Old code uses hardcoded colors.
   - What's unclear: Should these be added to `DashboardThemeDefinition` or defined as static colors in `:core:design`?
   - Recommendation: Add as static semantic colors in `:core:design` (not per-theme). Info=blue, Warning=amber, Success=green, Error=red. These are informational states, not themed aesthetics.

5. **OverlayNavHost migration to type-safe routes**
   - What we know: Current implementation uses `ROUTE_EMPTY = "empty"` string. Architecture specifies `sealed interface Route`.
   - What's unclear: Should Phase 10 fully migrate to `@Serializable` type-safe routes or use string routes for the 4 new routes?
   - Recommendation: Migrate to type-safe routes. Define `@Serializable` route classes. Use `composable<RouteType>` syntax. This is the modern pattern and enables `hasRoute<T>()` checks needed for source-varying transitions in Phase 11. The empty route becomes `@Serializable data object EmptyRoute`.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit5 + MockK + Truth (unit), ComposeTestRule (UI) |
| Config file | Convention plugin configured via `dqxn.android.test` |
| Quick run command | `./gradlew :feature:settings:testDebugUnitTest --console=plain` |
| Full suite command | `./gradlew :feature:settings:testDebugUnitTest :feature:dashboard:testDebugUnitTest --console=plain` |
| Estimated runtime | ~30 seconds |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| F2.7 | Widget picker grouping, entitlement badges | unit + compose | `./gradlew :feature:settings:testDebugUnitTest --tests "*.WidgetPickerTest" --console=plain` | No -- Wave 0 |
| F2.8 | WidgetSettingsSheet 3-tab navigation | compose | `./gradlew :feature:settings:testDebugUnitTest --tests "*.WidgetSettingsSheetTest" --console=plain` | No -- Wave 0 |
| F2.9 | SettingRowDispatcher 12 subtypes | unit + compose | `./gradlew :feature:settings:testDebugUnitTest --tests "*.SettingRowDispatcherTest" --console=plain` | No -- Wave 0 |
| F3.3 | SetupSheet multi-page navigation | compose | `./gradlew :feature:settings:testDebugUnitTest --tests "*.SetupSheetTest" --console=plain` | No -- Wave 0 |
| F3.4 | Setup card rendering per type | compose | `./gradlew :feature:settings:testDebugUnitTest --tests "*.SetupDefinitionRendererTest" --console=plain` | No -- Wave 0 |
| F3.5 | SetupEvaluator readiness check | unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.SetupEvaluatorImplTest" --console=plain` | No -- Wave 0 |
| F3.14 | Permission card 3 states, DeviceScan retry | unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.DeviceScanStateMachineTest" --console=plain` | No -- Wave 0 |
| F8.7 | Picker preview + entitlement gating | compose | `./gradlew :feature:settings:testDebugUnitTest --tests "*.WidgetPickerTest" --console=plain` | No -- Wave 0 |
| F10.4 | Touch target 76dp | compose (semantics) | Part of individual component tests | No -- Wave 0 |
| F12.5 | Analytics consent toggle | unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.MainSettingsTest" --console=plain` | No -- Wave 0 |
| F14.4 | Delete All Data clears all stores | unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.DeleteAllDataTest" --console=plain` | No -- Wave 0 |
| NF29 | CDM API usage | unit | Part of `DeviceScanStateMachineTest` | No -- Wave 0 |
| SC6 | Overlay navigation routes render + back | compose | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.OverlayNavHostTest" --console=plain` | No -- Wave 0 |

### Nyquist Sampling Rate

- **Minimum sample interval:** After every committed task -> run `./gradlew :feature:settings:testDebugUnitTest --console=plain`
- **Full suite trigger:** Before merging final task of any plan wave
- **Phase-complete gate:** Full suite green before verification runs. Include `./gradlew :feature:dashboard:testDebugUnitTest --console=plain` for overlay nav tests
- **Estimated feedback latency per task:** ~20-30 seconds

### Wave 0 Gaps (must be created before implementation)

All test files are new since `:feature:settings` is currently an empty stub module:

- [ ] `:feature:settings` build.gradle.kts -- add `:core:design` and `:data` deps
- [ ] `SettingRowDispatcherTest.kt` -- parameterized test for 12 subtypes + visibility gating + entitlement gating
- [ ] `DeviceScanStateMachineTest.kt` -- 5-state transitions, retry logic, API dispatch, device limit
- [ ] `SetupSheetTest.kt` -- multi-page navigation, back handler, forward gating
- [ ] `WidgetSettingsSheetTest.kt` -- 3-tab navigation, schema rendering
- [ ] `MainSettingsTest.kt` -- analytics consent, delete all data
- [ ] `WidgetPickerTest.kt` -- grouping, entitlement badges, scale calc
- [ ] `SetupEvaluatorImplTest.kt` -- real-time vs persistence evaluation
- [ ] `OverlayNavHostTest.kt` (in `:feature:dashboard`) -- route rendering + back navigation
- [ ] Sub-picker tests: TimezonePicker search, DateFormatPicker preview, AppPicker search

## Existing Infrastructure Inventory

### Types Already Defined (`:sdk:contracts`)

| Type | Location | Status |
|------|----------|--------|
| `SettingDefinition<T>` (12 subtypes) | `sdk/contracts/.../settings/SettingDefinition.kt` | Complete with tests |
| `SetupDefinition` (7 subtypes) | `sdk/contracts/.../setup/SetupDefinition.kt` | Complete with tests |
| `SetupPageDefinition` | `sdk/contracts/.../setup/SetupPageDefinition.kt` | Complete |
| `SetupEvaluator` (interface) | `sdk/contracts/.../setup/SetupEvaluator.kt` | Interface only, needs impl |
| `SetupResult` | `sdk/contracts/.../setup/SetupEvaluator.kt` | Complete |
| `InstructionAction` | `sdk/contracts/.../setup/SetupDefinition.kt` | Complete |
| `InfoStyle` | `sdk/contracts/.../setup/SetupDefinition.kt` | Complete |
| `VerificationStrategy` | `sdk/contracts/.../setup/VerificationStrategy.kt` | Exists |
| `ServiceType` | `sdk/contracts/.../setup/ServiceType.kt` | Exists |
| `DateFormatOption` | `sdk/contracts/.../settings/SettingsEnums.kt` | Exists |
| `SoundType` | `sdk/contracts/.../settings/SettingsEnums.kt` | Exists |
| `WidgetSpec` | `sdk/contracts/.../widget/WidgetSpec.kt` | Complete (has settingsSchema, description) |
| `WidgetRenderer` | `sdk/contracts/.../widget/WidgetRenderer.kt` | Complete |
| `EntitlementManager` | `sdk/contracts/.../entitlement/EntitlementManager.kt` | Complete |
| `Gated` | `sdk/contracts/.../entitlement/Gated.kt` | Complete |
| `ProviderSettingsStore` (interface) | `sdk/contracts/.../settings/ProviderSettingsStore.kt` | Complete |

### Infrastructure Already Implemented

| Component | Location | Status |
|-----------|----------|--------|
| `ProviderSettingsStoreImpl` | `data/.../provider/ProviderSettingsStoreImpl.kt` | Complete with tests |
| `PairedDeviceStore` + impl | `data/.../device/PairedDeviceStore*.kt` | Complete with tests |
| `WidgetStyleStore` + impl | `data/.../style/WidgetStyleStore*.kt` | Complete with tests |
| `UserPreferencesRepository` + impl | `data/.../preferences/UserPreferences*.kt` | Complete (needs analyticsConsent + clearAll) |
| `LayoutRepository` + impl | `data/.../layout/LayoutRepository*.kt` | Complete (needs clearAll) |
| `ConnectionEventStore` + impl | `data/.../device/ConnectionEventStore*.kt` | Complete (has clear()) |
| `WidgetRegistry` + impl | `feature/dashboard/.../binding/WidgetRegistryImpl.kt` | Complete |
| `DataProviderRegistry` + impl | `feature/dashboard/.../binding/DataProviderRegistryImpl.kt` | Complete |
| `StubEntitlementManager` | `app/.../StubEntitlementManager.kt` | Complete (all free) |
| `DashboardMotion` | `core/design/.../motion/DashboardMotion.kt` | Complete (all named transitions) |
| `DashboardSpacing` | `core/design/.../token/DashboardSpacing.kt` | Complete (all semantic aliases) |
| `DashboardTypography` | `core/design/.../token/DashboardTypography.kt` | Complete |
| `TextEmphasis` | `core/design/.../token/TextEmphasis.kt` | Complete |
| `CardSize` | `core/design/.../token/CardSize.kt` | Complete |
| `OverlayNavHost` | `feature/dashboard/.../layer/OverlayNavHost.kt` | Empty scaffold, needs routes |
| `DashboardScreen` | `feature/dashboard/.../DashboardScreen.kt` | Complete, needs nav wiring |
| `DashboardCommand` | `feature/dashboard/.../command/DashboardCommand.kt` | Complete (AddWidget, RemoveWidget, etc.) |
| `DashboardCommandBus` | `feature/dashboard/.../command/DashboardCommandBus.kt` | Complete |
| `AnalyticsTracker` | `sdk/analytics/.../AnalyticsTracker.kt` | Interface + NoOp impl |
| `FirebaseAnalyticsTracker` | `core/firebase/.../FirebaseAnalyticsTracker.kt` | Complete (setEnabled, resetAnalyticsData via Firebase SDK) |

### What Needs Building (Phase 10 Scope)

1. **`:feature:settings` module content** -- All composables, ViewModel, SettingNavigation, dispatchers, pickers, setup flow
2. **DeviceScanStateMachine** -- Pure logic extraction from UI (~300 lines)
3. **SetupEvaluator implementation** -- Concrete evaluator with both evaluate() and evaluateWithPersistence()
4. **OverlayNavHost route population** -- 4 routes with type-safe `@Serializable` classes
5. **DashboardScreen navigation callbacks** -- Wire settings/add-widget button clicks to NavController
6. **UserPreferencesRepository extensions** -- `analyticsConsent`, `clearAll()`
7. **ProviderSettingsStore.clearAll()** -- New interface method + implementation
8. **LayoutRepository.clearAll()** -- New interface method + implementation
9. **Error/warning/success semantic colors** -- Static tokens in `:core:design`
10. **OverlayScaffold + OverlayTitleBar** -- Shared overlay container
11. **PackBrowserContent** -- Pack list with shared element transitions

## Sources

### Primary (HIGH confidence)
- Existing codebase: all `:sdk:contracts` types, `:data` stores, `:feature:dashboard` coordinators, `:core:design` tokens -- read and verified
- `.planning/migration/phase-10.md` -- phase specification
- `.planning/migration/replication-advisory.md` -- sections 1, 2, 4, 5, 7
- `.planning/ARCHITECTURE.md` -- module dependency rules, architecture layers
- `.planning/arch/platform.md` -- navigation routes, overlay patterns
- `.planning/REQUIREMENTS.md` -- all 15 requirement IDs verified

### Secondary (MEDIUM confidence)
- Navigation Compose type-safe routes pattern -- based on Architecture doc specification and standard Compose Navigation patterns

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in use, no new dependencies needed
- Architecture: HIGH -- all foundation types exist, patterns established in Phases 2-9
- Pitfalls: HIGH -- replication advisory provides exhaustive edge case documentation
- Testing: HIGH -- JUnit5 + MockK + Truth + Turbine patterns well-established; ComposeTestRule for UI

**Research date:** 2026-02-25
**Valid until:** 2026-03-25 (stable -- all foundations exist, no external dependency changes expected)
