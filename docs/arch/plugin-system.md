# Plugin System

> Pack architecture, widget/provider/theme contracts, error isolation, and entitlement gating.

## Contracts (`:sdk:contracts`)

**`WidgetRenderer`** — extends `WidgetSpec` + `Gated`:
- `typeId: String` (e.g., `"core:speedometer"`)
- `compatibleSnapshots: Set<KClass<out DataSnapshot>>` — snapshot types this widget can consume
- `settingsSchema: List<SettingDefinition<*>>` — declarative settings UI
- `getDefaults(context: WidgetContext): WidgetDefaults` — context-aware sizing
- `@Composable Render(isEditMode, style, settings, modifier)` — widget data accessed via `LocalWidgetData.current`
- `supportsTap`, `onTap()`, `priority`
- `accessibilityDescription(data: WidgetData): String` — semantic description for TalkBack

**`DataProvider<T : DataSnapshot>`** — extends `DataProviderSpec`:
- `snapshotType: KClass<T>` — the `@DashboardSnapshot`-annotated subtype this provider emits
- `provideState(): Flow<T>` — reactive data stream, compiler-enforced to match `snapshotType`
- `schema: DataSchema` — describes output shape, staleness thresholds, and display metadata
- `setupSchema: List<SetupPageDefinition>` — declarative setup wizard
- `subscriberTimeout: Duration` — how long to keep alive after last subscriber (default 5s)
- `firstEmissionTimeout: Duration` — max time to wait for first emission (default 5s)
- `isAvailable`, `connectionState: Flow<Boolean>`, `connectionErrorDescription: Flow<String?>`

**`ActionableProvider<T : DataSnapshot>`** — extends `DataProvider<T>` for bidirectional interaction:
- `onAction(action: WidgetAction)` — receives actions from bound widgets

**`ThemeProvider`**:
- `themes: List<DashboardThemeDefinition>`

**`Gated`** interface — implemented by renderers, providers, themes, and individual settings:
- `requiredAnyEntitlement: Set<String>?` — OR logic (any one entitlement grants access)

## Widget-to-Widget Communication

Widgets never communicate directly. Interaction flows through the binding system:

```kotlin
sealed interface WidgetAction {
    data class Tap(val widgetId: String) : WidgetAction
    data class MediaControl(val command: MediaCommand) : WidgetAction
    data class TripReset(val tripId: String) : WidgetAction
}

interface ActionableProvider<T : DataSnapshot> : DataProvider<T> {
    fun onAction(action: WidgetAction)
}
```

`WidgetDataBinder` routes actions from widgets to their bound `ActionableProvider`. Widgets call `onAction()` via the binder — they never hold a reference to the provider directly.

## Data Type Extensibility

Data types are **string identifiers**, not a closed enum. Packs define new data types without modifying `:sdk:contracts`:

```kotlin
object DataTypes {
    const val SPEED = "SPEED"
    const val TIME = "TIME"
    const val ORIENTATION = "ORIENTATION"
    // ... core types
}

data class DataTypeDescriptor(
    val typeId: String,           // e.g., "sg-erp:toll-rate"
    val displayName: String,
    val unit: String?,
    val formatting: FormatSpec?,
)
```

Matching between widgets and providers is by `KClass` equality on snapshot types. A widget declaring `compatibleSnapshots = setOf(SpeedSnapshot::class)` binds to any provider whose `snapshotType == SpeedSnapshot::class`, regardless of which pack it comes from. Cross-pack `KClass` visibility is enabled by snapshot sub-modules (`:pack:*:snapshots`, `:core:*:snapshots`) — see [state-management.md](state-management.md#snapshot-sub-modules).

### Snapshot Type Validation

Runtime validation in `WidgetRegistry` catches unresolvable snapshot type declarations at startup:

```kotlin
fun validateBindings(providers: Set<DataProvider<*>>, widgets: Set<WidgetRenderer>) {
    val providedTypes = providers.map { it.snapshotType }.toSet()
    widgets.forEach { widget ->
        widget.compatibleSnapshots.forEach { type ->
            if (type !in providedTypes) {
                logger.warn(LogTag.BINDING, "widgetTypeId" to widget.typeId, "missingSnapshot" to type.simpleName) {
                    "Widget declares snapshot type '${type.simpleName}' but no provider emits it"
                }
            }
        }
    }
}
```

A CI fitness test validates this exhaustively after full DI graph assembly.

## Setup & Settings Schemas

### Setup Schema Types

`SetupDefinition` sealed interface:
- `RuntimePermission` — runtime permission request with satisfaction check
- `SystemServiceToggle` — Bluetooth/Location on/off toggle
- `SystemService` — service availability check
- `DeviceScan` — CDM device discovery
- `Instruction` — guidance text with icon
- `Info` — read-only information
- `Setting` — wraps a `SettingDefinition<*>` for setup-time configuration

### Settings Schema Types

`SettingDefinition<T>` with key, label, description, default value, visibility predicate, group, and entitlement gating:

| Definition Type | Rendered As |
|---|---|
| Boolean | Toggle switch |
| Int/Float with presets | FlowRow button group |
| Enum (2-10 options) | FlowRow button group |
| Enum (11-25 options) | Dropdown menu |
| Timezone | Hub route -> searchable timezone list |
| DateFormat | Hub route -> format list with live preview |
| AppPicker | Hub route -> installed app grid |
| Info | Read-only text row |
| Instruction | Text with icon |

No sliders — they interfere with HorizontalPager gestures.

## Plugin API Versioning

```kotlin
@PluginApiVersion(major = 1, minor = 0)
interface WidgetRenderer { ... }
```

Documentation annotation only. All packs ship in the same APK — no V1/V2 compatibility needed. If third-party packs are introduced, re-introduce versioning with adapter wrapping at that time.

## KSP Code Generation

`@DashboardWidget` and `@DashboardDataProvider` annotations -> KSP processor generates `PackManifest` classes wired into Hilt multibinding sets automatically.

The processor:
- Validates `typeId` uniqueness within a module (cross-module at runtime)
- Reports clear errors when annotations are misused
- Runs as a single processor alongside `agentic-processor`

## Runtime Discovery

`WidgetRegistry` and `DataProviderRegistryImpl` index the injected `Set<WidgetRenderer>` and `Set<DataProvider<*>>`. No dynamic loading — all compiled in, gated by entitlements at runtime.

Duplicate `typeId` detection: registry logs an error and uses the higher-priority registration.

## Provider Fallback

When a widget's assigned provider becomes unavailable, `WidgetDataBinder` falls back to the next available provider for the same snapshot type.

**Priority order**: user-selected > hardware (BLE device) > device sensor > network

```kotlin
private fun resolveProvider(snapshotType: KClass<out DataSnapshot>): DataProvider<*>? {
    return userSelectedProviders[snapshotType]?.takeIf { it.isAvailable }
        ?: findByPriority(snapshotType, ProviderPriority.HARDWARE)
        ?: findByPriority(snapshotType, ProviderPriority.DEVICE_SENSOR)
        ?: findByPriority(snapshotType, ProviderPriority.NETWORK)
}
```

Fallback is per-slot: if the hardware speed provider disconnects but the accelerometer is fine, only the speed slot falls back.

## Widget Error Isolation

### Composition Phase Limitation

**Known limitation**: Jetpack Compose has no equivalent of React's `ErrorBoundary` for the composition phase. The error boundary covers:
- **Draw-phase errors**: Via a wrapped `DrawScope` with try/catch
- **Effect errors**: Via `LocalWidgetScope` with `SupervisorJob` + `CoroutineExceptionHandler`
- **NOT composition-phase errors**: Mitigated by contract tests and crash recovery (safe mode after 3 crashes in 60s)

### WidgetSlot Error Boundary

```kotlin
@Composable
fun WidgetSlot(
    widget: WidgetInstance,
    renderer: WidgetRenderer,
    bindingCoordinator: WidgetBindingCoordinator,
) {
    var renderError by remember { mutableStateOf<Throwable?>(null) }
    val crashCount = remember { mutableIntStateOf(0) }

    // Trace context from the binding coordinator — non-null when this widget's
    // binding was initiated by an agentic command. Null for user-initiated widgets.
    val traceContext by bindingCoordinator.traceContextFor(widget.id).collectAsState()

    val widgetScope = rememberCoroutineScope().let { parentScope ->
        remember(parentScope, traceContext) {
            CoroutineScope(
                parentScope.coroutineContext +
                SupervisorJob(parentScope.coroutineContext.job) +
                (traceContext ?: EmptyCoroutineContext) +
                CoroutineExceptionHandler { _, e ->
                    crashCount.intValue++
                    renderError = e
                    errorReporter.reportWidgetCrash(widget, e)
                }
            )
        }
    }

    if (renderError != null || crashCount.intValue >= 3) {
        WidgetErrorFallback(
            widget = widget,
            error = renderError,
            onRetry = { renderError = null },
        )
    } else {
        CompositionLocalProvider(
            LocalWidgetScope provides widgetScope,
            LocalWidgetTraceContext provides traceContext,
            LocalWidgetErrorHandler provides { e ->
                crashCount.intValue++
                renderError = e
                errorReporter.reportWidgetCrash(widget, e)
            },
        ) {
            renderer.Render(...)
        }
    }
}
```

After 3 accumulated crashes, the fallback persists without retry option.

### LaunchedEffect Error Boundary

Standard composition error boundaries do NOT catch exceptions inside `LaunchedEffect`, `SideEffect`, or `DisposableEffect`. Every widget must use `LocalWidgetScope`:

```kotlin
val LocalWidgetScope = staticCompositionLocalOf<CoroutineScope> {
    error("No WidgetCoroutineScope provided")
}

val LocalWidgetTraceContext = staticCompositionLocalOf<TraceContext?> { null }
```

Enforced by lint rule and documented in the plugin API contract.

### Binding Lifecycle Observability

`WidgetBindingCoordinator` emits guaranteed `INFO`-level log entries for all binding state transitions — these are never sampled and always present in the `RingBufferSink`. Events: `BIND_STARTED`, `BIND_CANCELLED`, `BIND_TIMEOUT`, `REBIND_SCHEDULED`, `PROVIDER_FALLBACK`, `FIRST_EMISSION`. All include `widgetId`, `providerId`, and `traceId` in structured fields.

These events serve two purposes:
1. **Agentic diagnosis**: `diagnose-widget` filters the ring buffer by `widgetId` to show the binding history that led to the current state
2. **Trace correlation**: the `traceId` field links binding events back to the originating agentic command (if any), closing the observability loop from command → binding → anomaly → diagnostic snapshot

See [observability.md](observability.md#binding-lifecycle-events) for the full event table.

## Theme System

### Dual-Theme Model

Users maintain separate `lightTheme` and `darkTheme` selections. `ThemeAutoSwitchEngine` determines which is active.

### 5 Auto-Switch Modes

| Mode | Behavior | Premium |
|---|---|---|
| LIGHT | Always light | No |
| DARK | Always dark | No |
| SYSTEM | Follows OS setting | No |
| SOLAR_AUTO | GPS sunrise/sunset calculation | Yes (themes pack) |
| ILLUMINANCE_AUTO | Ambient light sensor vs configurable lux threshold | Yes (themes pack) |

### Theme Sources

- **Free**: 2 themes (Slate, Minimalist)
- **Themes pack**: 22 premium JSON-driven themes
- **Custom**: User-created via Theme Studio, max 12, stored in Proto DataStore

**Selector ordering**: Free themes first, then custom, then premium (gated with preview).

### Theme Definition

`DashboardThemeDefinition` (annotated `@Immutable`): `primaryTextColor`, `secondaryTextColor`, `accentColor`, `highlightColor`, `widgetBorderColor`, `backgroundBrush`, `widgetBackgroundBrush`, `isDark`, plus gradient specs for serialization. Default fallback: "Cyberpunk".

Spacing tokens and typography scale defined in `:core:design` as theme extension properties, keeping pack rebuilds isolated from settings UI changes.

### Theme JSON Format

```json
{
  "id": "tron",
  "name": "The Grid",
  "isDark": true,
  "colors": { "primary", "secondary", "accent", "highlight", "widgetBorder" },
  "gradients": {
    "background": { "type": "radial|vertical|horizontal|linear|sweep", "stops": [...] },
    "widgetBackground": { "type": "...", "stops": [...] }
  },
  "requiredAnyEntitlement": ["themes"]
}
```

### Theme Studio

Create/edit custom themes: name, 5 color fields, 2 gradient fields (type + 2-5 color stops each). Auto-saves on every change. Undo restores to entry state. Max 12 custom themes.

### Entitlement Gating

- All themes available for **preview** regardless of entitlement
- Gating at **persistence** layer — can't save a premium theme without the entitlement
- Reactive revocation — losing an entitlement auto-reverts to free defaults

## Entitlement System

| ID | Scope |
|---|---|
| `free` | Core pack — all users (speedometer, clock, date, compass, battery, speed limit, shortcuts) |
| `plus` | Plus pack — trip computer, media controller, G-force, altimeter, weather |
| `themes` | Themes pack — premium themes + Theme Studio + Solar/Illuminance auto-switch |

Regional packs define their own entitlement IDs.

`EntitlementManager` interface: `hasEntitlement(id)`, `getActiveEntitlements()`, `entitlementChanges: Flow<Set<String>>`, `purchaseProduct()`, `restorePurchases()`.

Debug builds provide `StubEntitlementManager` with a "Simulate Free User" toggle.
