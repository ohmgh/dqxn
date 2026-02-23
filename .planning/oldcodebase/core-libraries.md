# Old Codebase: Core Libraries

**Analysis Date:** 2026-02-23
**Source:** `/Users/ohm/Workspace/dqxn.old/android/core/`

---

## 1. core/common

**Path:** `/Users/ohm/Workspace/dqxn.old/android/core/common/`
**Namespace:** `app.dqxn.android.core.common`
**New location:** `sdk/common`

### Purpose

Shared foundation utilities: dispatcher injection, result types, DI qualifiers, and a connection state machine. Zero Compose dependency. Pure Android library with Hilt.

### Build Configuration

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.dqxn.android.library)
    alias(libs.plugins.dqxn.android.hilt)
}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

### Public API Surface

#### DI Qualifiers — `app.dqxn.android.common.di`

**`@ApplicationScope`** — `Scopes.kt`
```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

#### Dispatcher Injection — `app.dqxn.android.common.dispatcher`

**`@IoDispatcher`** / **`@DefaultDispatcher`** / **`@MainDispatcher`** — `DispatcherQualifiers.kt`
```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher
```

**`DispatcherModule`** — `DispatcherModule.kt`
```kotlin
@Module @InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    @Provides @MainDispatcher fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
```

**`DQXNDispatchers` interface** — `OversteerDispatchers.kt`
```kotlin
interface DQXNDispatchers {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
}

@Singleton
class DefaultDQXNDispatchers @Inject constructor() : DQXNDispatchers { ... }
```
Note: Two parallel dispatcher patterns exist (qualifier annotations AND interface). The interface `DQXNDispatchers` has no Hilt binding module -- it uses `@Inject constructor()` but is never bound via `@Binds`.

#### Result Types — `app.dqxn.android.common.result`

**`AppError`** sealed hierarchy — `AppError.kt`
```kotlin
sealed interface AppError {
    sealed interface Network : AppError {
        data class Unavailable(val message: String? = null) : Network
        data class Timeout(val message: String? = null) : Network
        data class Server(val code: Int, val message: String? = null) : Network
    }
    sealed interface Bluetooth : AppError {
        data class Disabled(val message: String? = null) : Bluetooth
        data class ConnectionFailed(val message: String? = null) : Bluetooth
        data class DeviceNotFound(val message: String? = null) : Bluetooth
        data class NotPaired(val message: String? = null) : Bluetooth
    }
    sealed interface Permission : AppError {
        data class Denied(val kind: PermissionKind, val message: String? = null) : Permission
    }
    sealed interface SdkAuth : AppError {
        data class AuthenticationRequired(val message: String? = null) : SdkAuth
        data class InvalidSdkKey(val message: String? = null) : SdkAuth
        data class DeveloperUnauthorised(val message: String? = null) : SdkAuth
        data class ApplicationUnauthorised(val message: String? = null) : SdkAuth
        data class DeveloperDeactivated(val message: String? = null) : SdkAuth
        data class RegistrationRequired(val message: String? = null) : SdkAuth
    }
    data class Device(val message: String? = null) : AppError
    data class Database(val message: String? = null) : AppError
    sealed interface Obu : AppError {
        data object NotInitialized : Obu
        data object BluetoothDisabled : Obu
        data class ConnectionTimeout(val deviceName: String) : Obu
        data class CardError(val status: String) : Obu
        data class SdkError(val code: String, val message: String? = null) : Obu
        data class SearchFailed(val message: String? = null) : Obu
    }
    data class Unknown(val code: String? = null, val message: String? = null) : AppError
}

enum class PermissionKind {
    ObuDataAccess, BluetoothScan, BluetoothConnect, Location,
    BackgroundLocation, Notification, Unknown
}
```

**`AppResult<T>`** sealed interface — `AppResult.kt`
```kotlin
sealed interface AppResult<out T> {
    data class Ok<out T>(val value: T) : AppResult<T>
    data class Err(val error: AppError) : AppResult<Nothing>
    val isSuccess: Boolean
    val isFailure: Boolean
    fun getOrNull(): T?
    fun errorOrNull(): AppError?
}

// Extension functions:
fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T>
fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T>
fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R>
fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R>
fun <T> AppResult<T>.getOrElse(defaultValue: T): T
fun <T> AppResult<T>.getOrElse(onFailure: (AppError) -> T): T
```

#### Connection State Machine — `app.dqxn.android.common.statemachine`

**`ConnectionStateMachine`** — `ConnectionStateMachine.kt`
```kotlin
sealed interface ConnectionEvent {
    data object StartSearch : ConnectionEvent
    data class DeviceFound(val deviceId: String, val deviceName: String) : ConnectionEvent
    data class DeviceSelected(val deviceId: String) : ConnectionEvent
    data object ConnectionSuccess : ConnectionEvent
    data class ConnectionFailed(val error: AppError) : ConnectionEvent
    data object Disconnect : ConnectionEvent
    data object SearchTimeout : ConnectionEvent
}

sealed interface ConnectionMachineState {
    data object Idle : ConnectionMachineState
    data object Searching : ConnectionMachineState
    data class DeviceDiscovered(val deviceId: String, val deviceName: String) : ConnectionMachineState
    data class Connecting(val deviceId: String) : ConnectionMachineState
    data class Connected(val deviceId: String) : ConnectionMachineState
    data class Error(val error: AppError) : ConnectionMachineState
}

class ConnectionStateMachine(initialState: ConnectionMachineState = ConnectionMachineState.Idle) {
    val state: ConnectionMachineState
    fun transition(event: ConnectionEvent): ConnectionMachineState
    fun reset()
}
```
State transitions: Idle -> Searching -> DeviceDiscovered -> Connecting -> Connected. Error recoverable via StartSearch. All invalid transitions silently stay in current state.

### Tests

- `AppResultTest.kt` — 9 tests covering Ok/Err, map, flatMap, getOrNull, getOrElse
- `ConnectionStateMachineTest.kt` — 8 tests covering full transition graph + invalid transitions + reset

### Migration Mapping

| Old | New | Notes |
|-----|-----|-------|
| `@ApplicationScope` | `sdk/common` | Retain |
| `@IoDispatcher` / `@DefaultDispatcher` / `@MainDispatcher` | `sdk/common` | New arch uses qualifier annotations only (drop `DQXNDispatchers` interface, it's redundant) |
| `DispatcherModule` | `sdk/common` | Retain |
| `AppError` | `sdk/common` | Strip OBU-specific variants (`Obu`, `SdkAuth`), make general-purpose. Add extensibility for packs. |
| `AppResult<T>` | `sdk/common` | Retain as-is |
| `ConnectionStateMachine` | Drop or `sdk/common` | OBU-specific FSM. Generalize if needed, or let packs own their own state machines. |
| `PermissionKind` | `sdk/common` | Generalize (remove `ObuDataAccess`) |

---

## 2. core/plugin-api

**Path:** `/Users/ohm/Workspace/dqxn.old/android/core/plugin-api/`
**Namespace:** `app.dqxn.android.core.pluginapi`
**New location:** `sdk/contracts`

### Purpose

The pack API surface. Defines all interfaces that packs implement and the shell consumes: widget renderers, data providers, action providers, themes, settings schemas, setup definitions, entitlements, and pack manifests. This is the **most critical module** for migration.

### Build Configuration

```kotlin
plugins {
    alias(libs.plugins.dqxn.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)  // Full Compose compiler
    alias(libs.plugins.ksp)
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutines.core)
    ksp(project(":core:plugin-processor"))
}
```

**Critical difference in new arch:** `sdk/contracts` uses `compileOnly(compose.runtime)` for annotations only -- NO Compose compiler. `@Composable` and `@Immutable` are annotation-only. The old codebase applies the full Compose compiler here, which is unnecessary since the only Compose usage is `@Composable` in function type parameters and `ImageVector` references.

### Public API Surface

#### Annotations

**`@DashboardWidget`** — `DashboardAnnotations.kt`
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class DashboardWidget(
    val localId: String,    // e.g., "compass"
    val displayName: String,
    val icon: String = ""   // Material icon name
)
```

**`@DashboardDataProvider`** — `DashboardAnnotations.kt`
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class DashboardDataProvider(
    val localId: String,
    val displayName: String,
    val description: String = ""
)
```

**`@DataContract`** / **`@RequiresData`** — `DataContract.kt`
```kotlin
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
annotation class DataContract(val provides: Array<String> = [], val consumes: Array<String> = [])

@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
annotation class RequiresData(val types: Array<String>)
```

**`@PackResourceMarker`** — `PackResourceMarker.kt`
```kotlin
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
annotation class PackResourceMarker
```

**`@SettingsSchema`** — `SettingsSchema.kt`
```kotlin
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
annotation class SettingsSchema
```

**`@ThemePackMarker`** — `ThemePackMarker.kt`
```kotlin
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.RUNTIME)
annotation class ThemePackMarker
```

**`@ValidConstraints`** — `ValidConstraints.kt`
```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
annotation class ValidConstraints
```

#### Core Widget Interfaces

**`WidgetSpec`** — `WidgetSpec.kt`
```kotlin
interface WidgetSpec {
    val typeId: String                          // "packId:localId"
    val displayName: String
    val contentDescription: String
    val baseTypeId: String?                     // For variant grouping
    val variantName: String?
    @Deprecated val constraints: WidgetConstraints
    fun getDefaults(context: WidgetContext): WidgetDefaults  // Default: 8x8 units
    val supportsMultipleSources: Boolean
    val compatibleDataTypes: List<String>       // Default: empty
    val settingsSchema: List<SettingDefinition<*>>  // Default: empty
}
```

**`WidgetRenderer`** — `WidgetRenderer.kt`
```kotlin
interface WidgetRenderer : WidgetSpec, Gated {
    val priority: Int                           // Default: 0
    @Composable fun Render(
        widgetData: WidgetData,
        isEditMode: Boolean,
        style: WidgetStyle,
        settings: Map<String, Any?>,
        modifier: Modifier
    )
    val supportsTap: Boolean                    // Default: false
    fun onTap(context: Context, widgetId: String, settings: Map<String, Any?>): Boolean  // Default: false
}
```

**`WidgetConstraints`** — `WidgetConstraints.kt`
```kotlin
@Serializable
data class WidgetConstraints(
    val defaultWidthUnits: Int,
    val defaultHeightUnits: Int,
    val preserveAspectRatio: Boolean = false
)
```

**`WidgetDefaults`** — `WidgetDefaults.kt`
```kotlin
data class WidgetDefaults(
    val widthUnits: Int,
    val heightUnits: Int,
    val preserveAspectRatio: Boolean = false,
    val settings: Map<String, Any?> = emptyMap()
) {
    fun toConstraints(): WidgetConstraints
}
```

**`WidgetContext`** — `WidgetContext.kt`
```kotlin
data class WidgetContext(
    val timezone: ZoneId,
    val locale: Locale,
    val region: String          // ISO 3166-1 alpha-2
) {
    companion object { val DEFAULT = WidgetContext(ZoneId.of("UTC"), Locale.US, "US") }
}
```

**`WidgetData`** — `WidgetData.kt`
```kotlin
data class WidgetData(
    val snapshots: Map<String, DataSnapshot> = emptyMap()
) {
    companion object { val EMPTY = WidgetData() }
    fun getSnapshot(dataType: String): DataSnapshot?
    fun hasData(): Boolean
}
```

**`WidgetStyle`** — `WidgetStyle.kt`
```kotlin
@Serializable
data class WidgetStyle(
    val backgroundStyle: BackgroundStyle = BackgroundStyle.NONE,
    val opacity: Float = 1.0f,
    val showBorder: Boolean = false,
    val hasGlowEffect: Boolean = false,
    val cornerRadiusPercent: Int = 25,   // 0-100
    val rimSizePercent: Int = 0,          // 0-100
    val zLayer: Int = 0                   // -1=Background, 0=Standard, 1=Top
)

@Serializable
enum class BackgroundStyle { NONE, SOLID }
```

#### Data Provider Interfaces

**`DataProviderSpec`** — `DataProviderSpec.kt`
```kotlin
interface DataProviderSpec : Gated {
    val sourceId: String        // "packId:localId"
    val displayName: String
    val description: String
    val dataType: String
}
```

**`DataProvider`** — `DataProviderSpec.kt`
```kotlin
interface DataProvider : DataProviderSpec {
    val schema: DataSchema
    fun provideState(): Flow<DataSnapshot>
    val isAvailable: Boolean                              // Default: true
    val connectionState: Flow<Boolean>                    // Default: flowOf(true)
    val hasConnectionError: Flow<Boolean>                 // Default: flowOf(false)
    val connectionErrorDescription: Flow<String?>         // Default: flowOf(null)
    val setupSchema: List<SetupPageDefinition>            // Default: empty
}
```

**`DataSchema` / `DataFieldSpec`** — `DataSchema.kt`
```kotlin
data class DataSchema(val fields: List<DataFieldSpec>)
sealed class DataFieldSpec(open val key: String) {
    data class IntField(override val key: String) : DataFieldSpec(key)
    data class DoubleField(override val key: String) : DataFieldSpec(key)
    data class StringField(override val key: String) : DataFieldSpec(key)
    data class BoolField(override val key: String) : DataFieldSpec(key)
    data class EnumField(override val key: String, val options: List<String>) : DataFieldSpec(key)
    data class ListField(override val key: String, val itemSchema: DataSchema) : DataFieldSpec(key)
}
```

**`DataSnapshot`** — `DataSnapshot.kt`
```kotlin
data class DataSnapshot(val values: Map<String, Any?>) {
    companion object { val EMPTY = DataSnapshot(emptyMap()) }
    fun getInt(key: String): Int?
    fun getDouble(key: String): Double?
    fun getString(key: String): String?
    fun getBool(key: String): Boolean?
    inline fun <reified E : Enum<E>> getEnum(key: String): E?
    fun <T> getList(key: String): List<T>?
}
```

**`DataTypes`** constants — `DataProviderSpec.kt`
```kotlin
object DataTypes {
    const val SPEED = "speed"
    const val BALANCE = "balance"
    const val TIME = "time"
    const val DATE = "date"
    const val TRAFFIC = "traffic"
    const val HISTORY = "history"
    const val CONNECTION = "connection"
    const val ACCELERATION = "acceleration"
    const val SPEED_LIMIT = "speed_limit"
    const val CHARGING = "charging"
    const val DEVICE = "device"
    const val ORIENTATION = "orientation"
    const val SOLAR = "solar"
    const val AMBIENT_LIGHT = "ambient_light"
}
```

#### Action Provider Interface

**`ActionProvider`** — `ActionProvider.kt`
```kotlin
interface ActionProviderSpec : Gated {
    val actionId: String        // "packId:actionId"
    val displayName: String
    val description: String
    val actionType: String
}

interface ActionProvider : ActionProviderSpec {
    suspend fun execute(context: Context, params: Map<String, Any?>): ActionResult
    val isAvailable: Boolean    // Default: true
}

sealed class ActionResult {
    data class Success(val message: String? = null) : ActionResult()
    data class Failure(val error: String, val recoverable: Boolean = false) : ActionResult()
    data class RequiresSetup(val requirements: List<SetupDefinition>) : ActionResult()
}

object ActionTypes {
    const val CALL = "call"; const val SMS = "sms"; const val SHARE = "share"
    const val NAVIGATE = "navigate"; const val OPEN_URL = "open_url"
}
```

#### Entitlements / Gating

**`Gated`** interface — `Gated.kt`
```kotlin
interface Gated {
    val requiredAnyEntitlement: List<String>?    // Default: null (free)
}
fun Gated.isAccessible(hasEntitlement: (String) -> Boolean): Boolean
```

**`Entitlements`** (KSP-generated) — `Entitlements.kt`
```kotlin
object Entitlements {
    const val FREE: String = "free"
    const val PLUS: String = "plus"
    const val SG_ERP2: String = "sg-erp2"
    const val THEMES: String = "themes"
    val all: List<String> = listOf(FREE, PLUS, SG_ERP2, THEMES)
    const val DEFAULT: String = "free"
}
```

#### Settings System

**`SettingDefinition<T>`** sealed interface — `SettingDefinition.kt`

All variants implement `Gated` for entitlement gating:

```kotlin
sealed interface SettingDefinition<T> : Gated {
    val key: String
    val label: String
    val description: String?
    val default: T
    val visibleWhen: ((Map<String, Any?>) -> Boolean)?
    val groupId: String?
    val hidden: Boolean
}
```

Concrete types:
- **`BooleanSetting`** — Toggle. Fields: `key`, `label`, `default: Boolean`
- **`IntSetting`** — Slider/segmented. Fields: `min`, `max`, `presets`, `presetsWhen` (dynamic)
- **`FloatSetting`** — Slider. Fields: `min`, `max`, `step`
- **`StringSetting`** — Text field. Fields: `placeholder`, `maxLength`
- **`EnumSetting<E : Enum<E>>`** — Segmented/dropdown. Fields: `options`, `optionLabels`, `optionPreviews` (Composable), `previewAspectRatio`
- **`TimezoneSetting`** — Full-screen picker. Value: null=None, "SYSTEM"=System, else=zone ID
- **`DateFormatSetting`** — Popup picker with previews
- **`UriSetting`** — System file/ringtone picker. Fields: `mimeType`, `allowNone`
- **`AppPickerSetting`** — App selection dialog. Fields: `suggestedPackages`, `showAllApps`
- **`SoundPickerSetting`** — System ringtone picker. Fields: `soundType: SoundType`
- **`InstructionSetting`** — Display-only step card. Fields: `stepNumber`, `icon`, `action: InstructionAction?`, `verificationStrategy`
- **`InfoSetting`** — Display-only info card. Fields: `icon`, `style: InfoStyle`

Supporting enums:
- `SoundType { RINGTONE, NOTIFICATION, ALARM, ALL }`
- `InfoStyle { INFO, WARNING, SUCCESS, ERROR }`
- `InstructionAction` sealed interface: `OpenSystemSettings`, `OpenUrl`, `LaunchApp`

#### Settings Enums — `SettingsEnums.kt`

```kotlin
@Serializable enum class SizeOption(val displayName: String, val weight: Float) {
    NONE("None", 0.0f), SMALL("S", 0.3f), MEDIUM("M", 0.5f), LARGE("L", 0.7f), XL("XL", 1.0f)
}
@Serializable enum class TimezonePosition(val displayName: String) { TOP("Top"), BOTTOM("Bottom") }
@Serializable enum class DateLayoutOption(val displayName: String) { SIMPLE("Simple"), STACK("Stack"), GRID("Grid") }
@Serializable enum class DateFormatOption(val displayName: String, val pattern: String) {
    EEE("Mon","EEE"), MMM_D("Dec 31","MMM d"), D_MMM("31 Dec","d MMM"),
    EEE_MMM_D("Mon, Dec 31","EEE, MMM d"), EEE_D_MMM("Mon, 31 Dec","EEE, d MMM"),
    MM_DD("12/31","MM/dd"), DD_MM("31/12","dd/MM"),
    MM_DD_YYYY("12/31/2025","MM/dd/yyyy"), DD_MM_YYYY("31/12/2025","dd/MM/yyyy"), YYYY_MM_DD("2025-12-31","yyyy-MM-dd")
}
@Serializable enum class InfoCardLayoutMode(val displayName: String) { STACK("Stack"), GRID("Grid"), COMPACT("Compact") }
```

#### InfoCardSettings Helper — `InfoCardSettings.kt`

```kotlin
object InfoCardSettings {
    fun layoutSettings(
        defaultLayoutMode: InfoCardLayoutMode = STACK,
        showIconDefault: Boolean = true,
        topTextLabel: String = "Title",
        bottomTextLabel: String = "Body",
        topTextBoldDefault: Boolean = false,
        bottomTextBoldDefault: Boolean = true
    ): List<SettingDefinition<*>>

    fun parseLayoutMode(settings: Map<String, Any?>): InfoCardLayoutMode
    fun parseSize(settings: Map<String, Any?>, key: String, default: SizeOption): SizeOption
    fun parseTopTextSize(settings: Map<String, Any?>, default: SizeOption): SizeOption
    fun parseBottomTextSize(settings: Map<String, Any?>, default: SizeOption): SizeOption
    fun parseTopTextBold(settings: Map<String, Any?>, default: Boolean): Boolean
    fun parseBottomTextBold(settings: Map<String, Any?>, default: Boolean): Boolean
}
```

#### Setup System

**`SetupPageDefinition`** — `SetupDefinition.kt`
```kotlin
data class SetupPageDefinition(
    val id: String,
    val title: String,
    val description: String? = null,
    val definitions: List<SetupDefinition>
)
```

**`SetupDefinition`** sealed interface — `SetupDefinition.kt`
```kotlin
sealed interface SetupDefinition : Gated {
    val id: String
    val displayName: String
    val description: String?
    val visibleWhen: ((Map<String, Any?>) -> Boolean)?
    val groupId: String?
    val hidden: Boolean

    // Requirement types:
    data class RuntimePermission(id, displayName, description?, permissions: List<String>, icon: ImageVector, minSdk: Int = 0, ...)
    data class SystemServiceToggle(id, displayName, description?, settingsAction: String, icon: ImageVector, checkEnabled: (Context) -> Boolean, ...)
    data class SystemService(id, displayName, description?, serviceType: ServiceType, icon: ImageVector?, ...)
    data class DeviceScan(id, handlerId: String, displayName, description?, title, ctaButtonLabel, deviceNamePattern?, serviceUuids?, maxDevices: Int = 1, onPaired?, onCancel?, ...)

    // Display types:
    data class Instruction(id, displayName, description?, stepNumber?, icon?, action?, verificationStrategy?, verificationOptional, alternativeResolution?, ...)
    data class Info(id, displayName, description?, icon?, style: InfoStyle, ...)

    // Wrapper type:
    data class Setting(val definition: SettingDefinition<*>)
}

enum class ServiceType { BLUETOOTH, LOCATION, WIFI }

// Extensions:
fun SettingDefinition<*>.asSetup(): SetupDefinition.Setting
fun List<SettingDefinition<*>>.asSetup(): List<SetupDefinition>
val SetupDefinition.isRequirement: Boolean
val SetupDefinition.isDisplay: Boolean
val SetupDefinition.isInput: Boolean
fun SetupDefinition.getDefaultValue(): Any?
```

#### SettingsAwareSizer — `SettingsAwareSizer.kt`

```kotlin
interface SettingsAwareSizer {
    fun computeSize(settings: Map<String, Any?>): WidgetConstraints
}

@Deprecated("Renamed to SettingsAwareSizer")
interface DynamicSizeProvider : SettingsAwareSizer {
    @Deprecated fun computeDefaultConstraints(settings: Map<String, Any?>): WidgetConstraints
}
```

#### Verification — `VerificationStrategy.kt`

```kotlin
sealed interface VerificationResult {
    data object Verified : VerificationResult
    data class Failed(val message: String, val suggestion: String? = null) : VerificationResult
    data object Skipped : VerificationResult
}

interface VerificationStrategy {
    suspend fun verify(context: Context): VerificationResult
}

class SystemServiceVerification(private val serviceType: ServiceType) : VerificationStrategy
class ClipboardVerification(private val pattern: Regex, failureMessage: String, suggestion: String?) : VerificationStrategy
```

#### Widget Render State — `WidgetRenderState.kt`

```kotlin
sealed interface WidgetRenderState {
    data object Ready : WidgetRenderState
    data class SetupRequired(val icon: ImageVector, val requirements: List<SetupDefinition>) : WidgetRenderState
    data class ConnectionError(val icon: ImageVector) : WidgetRenderState
    data class Disconnected(val icon: ImageVector) : WidgetRenderState
    data class EntitlementRevoked(val upgradeEntitlement: String) : WidgetRenderState
    data object ProviderMissing : WidgetRenderState

    companion object {
        fun computeOverlayState(revokedEntitlement, providerMissing, requirements, isDisconnected, hasConnectionError, defaultIcon): WidgetRenderState
        fun computeAllIssues(revokedRendererEntitlement, revokedProviderEntitlements, missingProviderIds, requirements, setupProviderId?, isDisconnected, hasConnectionError, connectionErrorDescription?): List<WidgetIssue>
    }
}

data class WidgetIssue(type: IssueType, resourceId: String, title: String, description: String, resolution: ResolutionAction) {
    enum class IssueType { ENTITLEMENT_REVOKED, PROVIDER_MISSING, SETUP_REQUIRED, CONNECTION_ERROR, DISCONNECTED }
    sealed class ResolutionAction {
        data class Upgrade(val entitlementId: String)
        data object ChangeDataSource
        data class Setup(val requirements: List<SetupDefinition>, val providerId: String?)
        data object WaitForConnection
    }
}

data class WidgetStatusCache(val overlayState: WidgetRenderState, val issues: List<WidgetIssue>) {
    companion object { val EMPTY = WidgetStatusCache(Ready, emptyList()) }
}

val SetupDefinition.icon: ImageVector?  // Extension property
```

#### Theme Interfaces

**`ThemeSpec`** — `ThemeSpec.kt`
```kotlin
interface ThemeSpec : Gated {
    val themeId: String        // "packId:localId"
    val displayName: String
    val isDark: Boolean
    val packId: String?        // Extracted from themeId
}
```

**`ThemeSchema`** (JSON-defined themes) — `ThemeSchema.kt`
```kotlin
@Serializable data class ThemeSchema(id, name, isDark, colors: ThemeColors, gradients: ThemeGradients, defaults: ThemeDefaults, requiredAnyEntitlement?)
@Serializable data class ThemeColors(primary: String, secondary: String, accent: String, highlight: String?, widgetBorder: String)
@Serializable data class ThemeGradients(background: GradientSpec, widgetBackground: GradientSpec)
@Serializable data class GradientSpec(type: GradientType, stops: List<String>, angle?, centerX?, centerY?) {
    fun toBrush(): Brush
}
@Serializable enum class GradientType { VERTICAL, HORIZONTAL, LINEAR, RADIAL, SWEEP }
@Serializable data class ThemeDefaults(backgroundStyle: String = "SOLID", glowEffect: Boolean = false)
```
Private `parseHexColor(hex: String): Color` function supports #RGB, #RRGGBB, #AARRGGBB.

#### Connection Types

**`ConnectionState`** — `ConnectionState.kt`
```kotlin
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceMac: String) : ConnectionState()
    data class Error(val reason: String) : ConnectionState()
}
```

**`ConnectionNotifier`** — `ConnectionNotifier.kt`
```kotlin
interface ConnectionNotifier {
    fun showConnected(deviceName: String)
    fun showDisconnected()
}
```

#### Device Management

**`DeviceManagement`** — `DeviceManagement.kt`
```kotlin
interface DeviceManagement {
    val pairedDevicesFlow: StateFlow<List<PairedDevice>>
    suspend fun getPairedDevices(): List<PairedDevice>
    suspend fun forgetDevice(macAddress: String)
}

data class PairedDevice(macAddress: String, deviceName: String?, status: DeviceStatus, lastSeenTime: Long?)
enum class DeviceStatus { CONNECTED, AVAILABLE, UNAVAILABLE }
data class PresentDevice(macAddress: String, deviceName: String?, detectedTime: Long, lastSeenTime: Long)
data class ConnectedDevice(macAddress: String, deviceName: String?, connectedTime: Long)
```

**`DeviceServiceRegistry`** — `DeviceServiceRegistry.kt`
```kotlin
interface CompanionDeviceHandler {
    fun onDeviceAppeared(macAddress: String, associationId: Int?)
    fun onDeviceDisappeared(macAddress: String)
}

data class DeviceHandlerRegistration(handlerId: String, registeredMacAddresses: Set<String>, allowedNamePrefixes: Set<String>, handler: CompanionDeviceHandler)

interface DeviceServiceRegistry {
    fun register(registration: DeviceHandlerRegistration)
    fun addRegisteredMac(handlerId: String, macAddress: String)
    fun dispatchAppeared(macAddress: String, deviceName: String?, associationId: Int?)
    fun dispatchDisappeared(macAddress: String)
    fun getPresentDevices(handlerId: String): Set<PresentDevice>
    fun reportConnected(handlerId: String, device: ConnectedDevice)
    fun reportDisconnected(handlerId: String, macAddress: String, reason: String?)
    fun reportConnectFailed(handlerId: String, macAddress: String, reason: String?)
    suspend fun disassociate(macAddress: String)
    fun getDeviceManagement(handlerId: String): DeviceManagement?
}
```

#### Pack Manifest

**`DashboardPackManifest`** — `DashboardPackManifest.kt`
```kotlin
@Serializable data class DashboardPackManifest(
    packId, displayName, description, version: Int, minHostVersion: Int = 1,
    isCore: Boolean = false, isRegional: Boolean = false, region: String?,
    widgets: List<PackWidgetRef>, themes: List<PackThemeRef>, dataProviders: List<PackDataProviderRef>,
    dependencies: List<PackDependency>, defaultLayoutJson: String?, thumbnailUrl: String?, author: String?,
    category: PackCategory, entitlementId: String?, icon: String?
)

@Serializable data class PackWidgetRef(localId, displayName, icon?) { fun globalId(packId): String }
@Serializable data class PackThemeRef(localId, displayName, isDark, previewColors?)
@Serializable data class PackDataProviderRef(localId, displayName, description)
@Serializable data class PackDependency(resourceType: PackResourceType, globalId, optional)
@Serializable enum class PackResourceType { WIDGET, THEME, DATA_PROVIDER }
@Serializable enum class PackCategory { GENERAL, AUTOMOTIVE, FINANCE, PRODUCTIVITY, MEDIA, TRAVEL, SMART_HOME, GAMING }
```

### Tests

- `SetupDefinitionTest.kt` — 14 tests covering all 7 sealed subtypes, Setting wrapper delegation, `asSetup()` extensions, `getDefaultValue()`, exhaustive matching
- `VerificationStrategyTest.kt` — 12 tests (Robolectric) covering SystemServiceVerification (BT/Location/WiFi) and ClipboardVerification (pattern matching, empty clipboard)

### Migration Mapping

| Old (`core/plugin-api`) | New (`sdk/contracts`) | Notes |
|-------------------------|----------------------|-------|
| `WidgetRenderer` | `WidgetRenderer` | Drop `context: Context` from `onTap`. Add `@Immutable` to all state types. |
| `WidgetSpec` | `WidgetSpec` | Remove deprecated `constraints` property |
| `DataProvider` / `DataProviderSpec` | Retain | Add `@DashboardSnapshot` typed snapshots instead of `DataSnapshot(Map<String, Any?>)` |
| `DataSnapshot` | Replace with typed `@DashboardSnapshot` subtypes | Per-widget typed data classes keyed by `KClass` |
| `WidgetData` | Replace with multi-slot `WidgetData` keyed by `KClass<DataSnapshot>` | Type-safe access |
| `ThemeSpec` | Retain | |
| `ThemeSchema` / `GradientSpec` | Move `toBrush()` to `sdk/ui` or `core/design` | Pure data in contracts, rendering in UI |
| `SettingDefinition<*>` hierarchy | Retain | Drop `EnumSetting.optionPreviews` (Composable) from contracts; move preview rendering to `sdk/ui` |
| `SetupDefinition` hierarchy | Retain | |
| `DashboardPackManifest` | Simplify or drop | New arch uses KSP annotation-based discovery, not manifest objects |
| `Gated` / `Entitlements` | Retain | |
| `DeviceServiceRegistry` / `DeviceManagement` | Generalize | Remove OBU-specific assumptions |
| `ConnectionState` | Retain | |
| `ConnectionNotifier` | Move to `sdk/contracts` | |
| `ActionProvider` | Retain | |
| `SettingsAwareSizer` | Retain, drop deprecated `DynamicSizeProvider` | |
| `WidgetRenderState` / `WidgetIssue` | Retain | |
| `InfoCardSettings` | Move to `sdk/ui` | Contains rendering logic helpers |
| Settings enums (`SizeOption`, etc.) | Retain in `sdk/contracts` | Used in settings schemas |

---

## 3. core/widget-primitives

**Path:** `/Users/ohm/Workspace/dqxn.old/android/core/widget-primitives/`
**Namespace:** `app.dqxn.android.core.widgetprimitives`
**New location:** Split across `sdk/ui` + `core/design`

### Purpose

Compose UI primitives consumed by packs for rendering widgets: theme CompositionLocals, widget container with glow/border/background, info card layout system, setup overlays, and utility helpers (region detection, timezone mapping).

### Build Configuration

```kotlin
plugins {
    alias(libs.plugins.dqxn.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(project(":core:plugin-api"))
    implementation(project(":data:persistence"))  // For AnyMapSerializer
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
}
```

### Public API Surface

#### Theme Definition — `DashboardTheme.kt`

```kotlin
data class DashboardThemeDefinition(
    val id: String, val name: String,
    val backgroundBrush: Brush, val widgetBackgroundBrush: Brush,
    val widgetBorderColor: Color, val primaryTextColor: Color, val secondaryTextColor: Color,
    val accentColor: Color, val highlightColor: Color = accentColor,
    override val isDark: Boolean = true,
    val defaultBackgroundStyle: BackgroundStyle = BackgroundStyle.SOLID,
    val defaultHasGlowEffect: Boolean = false,
    override val requiredAnyEntitlement: List<String>? = null,
    val backgroundGradientSpec: GradientSpec? = null,
    val widgetBackgroundGradientSpec: GradientSpec? = null
) : ThemeSpec

interface ThemeProvider {
    val packId: String
    fun getThemes(): List<DashboardThemeDefinition>
}

val DefaultTheme = DashboardThemeDefinition(id = "cyberpunk", name = "Cyberpunk", ...)
```

#### CompositionLocals — `LocalDashboardTheme.kt`, `WidgetScale.kt`

```kotlin
val LocalDashboardTheme = staticCompositionLocalOf { DefaultTheme }

data class WidgetPreviewUnits(val widthUnits: Int, val heightUnits: Int)
val LocalWidgetPreviewUnits = compositionLocalOf<WidgetPreviewUnits?> { null }

data class WidgetScale(val scaleX: Float = 1f, val scaleY: Float = 1f)
val LocalWidgetScale = compositionLocalOf { WidgetScale() }
```

#### Widget Instance Model — `DashboardWidget.kt`

```kotlin
val GRID_UNIT_SIZE = 16.dp

interface DashboardWidget {
    val id: String
    val typeId: String
    val sizeSpec: WidgetSizeSpec
    val style: WidgetStyle
}

@Serializable
data class WidgetSizeSpec(val widthUnits: Int, val heightUnits: Int, val preserveAspectRatio: Boolean = false)

@Serializable
data class DashboardWidgetInstance(
    override val id: String,
    override val typeId: String,
    override val sizeSpec: WidgetSizeSpec,
    override val style: WidgetStyle = WidgetStyle(),
    val selectedDataSourceIds: List<String> = emptyList(),
    @Serializable(with = AnyMapSerializer::class) val settings: Map<String, Any?> = emptyMap(),
    val gridX: Int = 0, val gridY: Int = 0,
    val zIndex: Int = 0,
    val distinctId: Long = System.nanoTime()
) : DashboardWidget
```

#### WidgetContainer — `WidgetContainer.kt`

The main container composable. ~270 lines. Applies:
- Glow effect via `drawWithCache` + `BlurMaskFilter` (responsive sizing: 4dp/8dp/12dp based on widget min dimension)
- Background (NONE=transparent, SOLID=opaque base + theme overlay at opacity)
- Border (2dp, theme color)
- Rim padding (0-15% of min dimension)
- Corner radius (maps 0-100 to 0-50 for RoundedCornerShape percent)
- Accessibility semantics

```kotlin
@Composable
fun WidgetContainer(
    style: WidgetStyle,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    warningColor: Color? = null,
    content: @Composable BoxWithConstraintsScope.() -> Unit
)
```

**Migration note:** Uses `BlurMaskFilter` -- the new arch specifies `RenderEffect.createBlurEffect()` instead.

#### InfoCardLayout — `InfoCardLayout.kt`

~536 lines. Deterministic weighted normalization layout for info card widgets.

```kotlin
@Composable
fun InfoCardLayout(
    topText: String? = null,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    bottomText: String,
    bottomTextAnnotated: AnnotatedString? = null,
    style: WidgetStyle,
    layoutMode: InfoCardLayoutMode = InfoCardLayoutMode.STACK,
    topTextSize: SizeOption = SizeOption.MEDIUM,
    bottomTextSize: SizeOption = SizeOption.MEDIUM,
    iconSize: SizeOption = SizeOption.MEDIUM,
    iconTint: Color? = null,
    topTextBold: Boolean = false,
    bottomTextBold: Boolean = true,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
)
```

Layout strategy:
1. Elements weighted by SizeOption (SMALL=0.3, MEDIUM=0.5, LARGE=0.7, XL=1.0)
2. Normalize to fill 80% of available height (20% safety buffer for font leading/descenders)
3. Fixed spacers subtracted before normalization
4. Three layout modes: STACK (vertical), GRID (icon left, text right), COMPACT (icon+title inline, body below)

#### Setup Overlays — `SetupOverlays.kt`

Five overlay composables matching `WidgetRenderState` variants:

```kotlin
@Composable fun SetupRequiredOverlay(icon: ImageVector, cornerRadiusPercent: Int, onClick: (() -> Unit)?)
    // 60% black scrim, centered icon
@Composable fun DisconnectedOverlay(icon: ImageVector, cornerRadiusPercent: Int, iconSize: Dp = 20.dp, iconPadding: Dp = 8.dp)
    // 15% black scrim, bottom-right corner icon
@Composable fun ConnectionErrorOverlay(cornerRadiusPercent: Int, onClick: (() -> Unit)?)
    // 30% black scrim, centered Warning icon
@Composable fun EntitlementRevokedOverlay(cornerRadiusPercent: Int, onClick: (() -> Unit)?)
    // 60% black scrim, centered Star icon
@Composable fun ProviderMissingOverlay(cornerRadiusPercent: Int, onClick: (() -> Unit)?)
    // 60% black scrim, centered Warning icon
```

#### PermissionRequestPage — `PermissionRequestPage.kt`

```kotlin
@Composable
fun PermissionRequestPage(
    permissions: List<SetupDefinition.RuntimePermission>,
    allGranted: Boolean,
    theme: DashboardThemeDefinition,
    onRequestAllPermissions: () -> Unit,
    onAllGranted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
)
```
Shows permission list with provider-supplied icons. Settings redirect after 2 denials.

#### Region Detection — `RegionDetector.kt`

```kotlin
object RegionDetector {
    val MPH_COUNTRIES: Set<String>  // US, GB, territories, Caribbean
    fun detect(): String           // ISO 3166-1 alpha-2
}
```
Fallback chain: TimezoneCountryMap -> Locale.getDefault().country -> "US"

#### Timezone Mapping — `TimezoneCountryMap.kt`

```kotlin
object TimezoneCountryMap {
    fun getCountry(timezoneId: String): String?
}
```
~100 timezone-to-country mappings covering Asia-Pacific, Oceania, Americas, Europe, Africa, UTC.

### Migration Mapping

| Old (`core/widget-primitives`) | New | Notes |
|-------------------------------|-----|-------|
| `DashboardThemeDefinition` | `core/design` | Runtime theme type with Compose colors/brushes |
| `ThemeProvider` | `sdk/contracts` | Interface for pack theme contribution |
| `DefaultTheme` | `core/design` | |
| `LocalDashboardTheme` | `core/design` | |
| `LocalWidgetScale` / `LocalWidgetPreviewUnits` | `sdk/ui` | |
| `DashboardWidget` / `DashboardWidgetInstance` | `data` module or `sdk/contracts` | Instance model is persistence concern |
| `WidgetSizeSpec` | `sdk/contracts` | |
| `GRID_UNIT_SIZE` | `sdk/ui` or `core/design` | |
| `WidgetContainer` | `sdk/ui` | Replace `BlurMaskFilter` with `RenderEffect.createBlurEffect()` |
| `InfoCardLayout` | `sdk/ui` | |
| Setup overlays | `sdk/ui` | |
| `PermissionRequestPage` | `sdk/ui` | |
| `RegionDetector` | `sdk/common` | Pure logic, no Compose dependency |
| `TimezoneCountryMap` | `sdk/common` | Pure data mapping |

---

## 4. core/agentic

**Path:** `/Users/ohm/Workspace/dqxn.old/android/core/agentic/`
**Namespace:** `app.dqxn.android.core.agentic`
**New location:** `core/agentic`

### Purpose

Debug-mode framework for external agents (ADB, automation tools) to control the app. Command-based architecture: transport receives commands via ADB broadcast, dispatches to registered handlers, outputs results via logcat JSONL. Includes event capture for test replay.

### Build Configuration

```kotlin
plugins {
    alias(libs.plugins.dqxn.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    ksp(project(":core:agentic-processor"))
}
```

No Compose dependency. No Hilt dependency. Pure Android library with coroutines + serialization.

### Public API Surface

#### Core Framework

**`CommandHandler`** interface — `CommandHandler.kt`
```kotlin
interface CommandHandler {
    val name: String
    val description: String
    val category: String
    val aliases: List<String>       // Default: empty
    suspend fun execute(params: JsonObject, commandId: String): CommandResult
    fun paramsSchema(): JsonObject  // JSON Schema for params
}
```

**`CommandRegistry`** — `CommandRegistry.kt`
```kotlin
interface CommandRegistry {
    fun register(handler: CommandHandler)
    fun getHandler(name: String): CommandHandler?
    fun findHandler(nameOrAlias: String): CommandHandler?  // Name + alias lookup
    fun getAllHandlers(): List<CommandHandler>
}

class DefaultCommandRegistry : CommandRegistry  // MutableMap-backed implementation
```

**`CommandResult`** — `CommandResult.kt`
```kotlin
@Serializable sealed class CommandResult {
    @Serializable data class Success(val data: JsonObject = buildJsonObject {}) : CommandResult()
    @Serializable data class Error(val code: String, val message: String) : CommandResult()
}
```

**`CommandDispatcher`** — `CommandDispatcher.kt`
```kotlin
class CommandDispatcher(registry: CommandRegistry, outputSink: OutputSink, scope: CoroutineScope) {
    fun dispatch(commandId: String, commandName: String, params: JsonObject?)
}
```
Non-blocking. Launches coroutine per command. Catches exceptions -> `Error("EXECUTION_ERROR", ...)`.

**`AgenticEngine`** — `AgenticEngine.kt`

Builder-pattern main entry point:
```kotlin
class AgenticEngine private constructor(...) {
    fun start()
    fun stop()
    fun dispatch(commandId: String, commandName: String, params: JsonObject?)

    class Builder(context: Context) {
        fun navigationCallback(callback: NavigationCallback): Builder
        fun stateProvider(provider: StateProvider): Builder
        fun extendedStateProvider(provider: ExtendedStateProvider): Builder
        fun captureSession(session: CaptureSession): Builder
        fun outputSink(sink: OutputSink): Builder
        fun widgetCallback(callback: WidgetCallback): Builder
        fun themeCallback(callback: ThemeCallback): Builder
        fun settingsCallback(callback: SettingsCallback): Builder
        fun presetCallback(callback: PresetCallback): Builder
        fun routeRegistry(registry: RouteRegistry): Builder
        fun build(): AgenticEngine
    }
}
```

**Key design decision:** Handlers are manually instantiated in `Builder.build()` (NOT auto-registered by KSP) because many require constructor dependencies. KSP-generated `GeneratedCommandRegistry` only handles zero-arg handlers like `PingHandler`.

**`@AgenticCommand`** annotation — `AgenticCommand.kt`
```kotlin
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
annotation class AgenticCommand(val name: String, val description: String, val category: String = "")
```

#### Callback Interfaces

**`NavigationCallback`** — `NavigationCallback.kt`
```kotlin
interface NavigationCallback {
    suspend fun navigate(route: String): Boolean
    suspend fun back(): Boolean
    fun getCurrentRoute(): String?
}
```

**`StateProvider`** — `StateProvider.kt`
```kotlin
interface StateProvider {
    fun getState(path: String? = null): JsonObject
}
```

**`ExtendedStateProvider`** — `ExtendedStateProvider.kt`
```kotlin
interface ExtendedStateProvider : StateProvider {
    fun getNavState(): JsonObject
    fun getWidgetsState(): JsonObject
    fun getWidgetSettingsState(): JsonObject
    fun getThemeState(): JsonObject
    fun getSettingsState(): JsonObject
    fun getEntitlementsState(): JsonObject
    fun getCaptureState(): JsonObject
    fun getProvidersState(): JsonObject
    fun getPermissionsState(): JsonObject
    fun getTransientState(): JsonObject
}
```
10 queryable domains for GraphQL-like filtered state access.

**`WidgetCallback`** — `callback/WidgetCallback.kt`
```kotlin
interface WidgetCallback {
    suspend fun addWidget(typeId: String): String?
    suspend fun removeWidget(widgetId: String): Boolean
    suspend fun moveWidget(widgetId: String, newX: Int, newY: Int): Boolean
    suspend fun resizeWidget(widgetId: String, width: Int, height: Int): Boolean
    suspend fun focusWidget(widgetId: String): Boolean
    suspend fun tapWidget(widgetId: String): Boolean
    suspend fun longPressWidget(widgetId: String): Boolean
    suspend fun updateWidgetSettings(widgetId: String, settings: JsonObject): Boolean
    fun getWidgetIds(): List<String>
    fun getWidgetInfo(widgetId: String): WidgetInfo?
    suspend fun clearAllWidgets(): Boolean
}

@Serializable data class WidgetInfo(id, typeId, gridX, gridY, widthUnits, heightUnits, settings: JsonObject)
```

**`ThemeCallback`** — `callback/ThemeCallback.kt`
```kotlin
interface ThemeCallback {
    suspend fun setMode(mode: String): Boolean
    suspend fun setColors(hue: Float, lightSaturation: Float, darkSaturation: Float): Boolean
    suspend fun applyPreset(presetId: String): Boolean
    fun getPresets(): List<ThemePresetInfo>
    fun getCurrentTheme(): ThemeInfo
}

@Serializable data class ThemePresetInfo(id, name)
@Serializable data class ThemeInfo(mode, hue: Float, isDark: Boolean)
```

**`SettingsCallback`** — `callback/SettingsCallback.kt`
```kotlin
interface SettingsCallback {
    suspend fun setSetting(key: String, value: JsonElement): Boolean
    fun getSettings(): JsonObject
}
```

**`PresetCallback`** — `callback/PresetCallback.kt`
```kotlin
interface PresetCallback {
    suspend fun loadPreset(presetId: String): Boolean
    suspend fun savePreset(name: String): String?
    fun listPresets(): List<PresetInfo>
    suspend fun removePreset(presetId: String): Boolean
}

@Serializable data class PresetInfo(id, name, widgetCount: Int)
```

#### Route System

**`@RouteDescription`** annotation — `RouteDescription.kt`
```kotlin
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
annotation class RouteDescription(val value: String)
```

**`RouteInfo`** / **`ParamInfo`** — `RouteInfo.kt`
```kotlin
@Serializable data class RouteInfo(name, path, params: List<ParamInfo>, description, deepLink?)
@Serializable data class ParamInfo(name, type, required: Boolean, nullable: Boolean, example?)
```

**`RouteRegistry`** — `RouteRegistry.kt`
```kotlin
interface RouteRegistry {
    fun getAllRoutes(): List<RouteInfo>
}
```

#### Transport Layer

**`Transport`** interface — `transport/Transport.kt`
```kotlin
interface Transport {
    fun start(dispatcher: CommandDispatcher)
    fun stop()
}
```

**`BroadcastTransport`** — `transport/BroadcastTransport.kt`

ADB broadcast receiver. Action format: `app.dqxn.android.AGENTIC.{command}`.
- Security: Rejects non-shell callers (`Binder.getCallingUid() != Process.SHELL_UID`)
- Extras: `command_id` (required), `params` (optional JSON)

#### Output Layer

**`OutputSink`** interface — `output/OutputSink.kt`
```kotlin
interface OutputSink {
    fun emit(commandId: String, result: CommandResult)
    fun emitEvent(event: EventEnvelope)
}
```

**`LogcatSink`** — `output/LogcatSink.kt`

Tag: `DQXN_AGENT`. Format: JSONL. Max 4000 bytes/chunk with UTF-8 aware chunking.

#### Capture System

**`CaptureSession`** — `capture/CaptureSession.kt`
```kotlin
class CaptureSession(outputSink: OutputSink, context: Context) {
    val isActive: Boolean
    fun start(commandId: String): String    // Returns sessionId
    fun stop(reason: String = "user_stop")
    fun emitNavigation(event: NavigationEvent, commandId: String?)
    fun emitInput(event: InputEvent, commandId: String?)
    fun emitMarker(label: String, commandId: String?)
    fun emitState(state: JsonElement, commandId: String?)
}
```
Thread-safe via synchronized blocks. Emits header with device metadata on start.

**`CaptureSessionRegistry`** interface — `capture/CaptureSessionRegistry.kt`
```kotlin
interface CaptureSessionRegistry {
    val isActive: Boolean
    fun emitNavigation(event: NavigationEvent)
    fun emitInput(event: InputEvent)
}
```

**`EventEnvelope`** — `capture/EventEnvelope.kt`
```kotlin
@Serializable data class EventEnvelope(type: String, timestamp: String, commandId: String?, sessionId: String, payload: JsonElement) {
    companion object { fun create(type, sessionId, commandId, payload): EventEnvelope }
}
```

**Event Types** — `capture/EventTypes.kt`
```kotlin
object EventTypes {
    const val HEADER = "header"; const val NAVIGATION = "navigation"; const val INPUT = "input"
    const val STATE = "state"; const val MARKER = "marker"; const val END = "end"
}

@Serializable data class SessionHeader(deviceModel, appVersion, appVersionCode, screenWidth, screenHeight, density, orientation)
@Serializable data class SessionEnd(eventCount, durationMs, endReason)
```

**`NavigationEvent`** — `capture/NavigationEvent.kt`
```kotlin
@Serializable data class NavigationEvent(
    route, params: Map<String, String>, sourceScreen?, navigationType: NavigationType,
    overlayType?, backStackDepth, isSystemBack, isDeepLink, intentData?
)
@Serializable enum class NavigationType { PUSH, POP, REPLACE, RESET, TAB_SWITCH }
```

**`InputEvent`** — `capture/InputEvent.kt`
```kotlin
@Serializable data class InputEvent(
    gestureType: GestureType, element: ElementIdentifier?, screenX: Float, screenY: Float,
    componentX?, componentY?, widgetId?, gridPosition: GridPosition?, text?, duration?
)
@Serializable enum class GestureType { TAP, LONG_PRESS, DOUBLE_TAP, SWIPE, PINCH, DRAG, TEXT_ENTRY, FOCUS_CHANGE, WIDGET_RESIZE, WIDGET_MOVE, WIDGET_DELETE }
@Serializable data class ElementIdentifier(testTag?, contentDescription?, className?, resourceId?)
@Serializable data class GridPosition(x: Int, y: Int)
```

#### Command Handlers (21 total)

| Handler | Name | Category | Dependencies |
|---------|------|----------|-------------|
| `PingHandler` | `ping` | discovery | None |
| `ListCommandsHandler` | `list-commands` | discovery | `CommandRegistry` |
| `NavigateHandler` | `nav-goto` (alias: `navigate`) | nav | `NavigationCallback` |
| `BackHandler` | `nav-back` | nav | `NavigationCallback` |
| `NavListRoutesHandler` | `nav-list-routes` | nav | `RouteRegistry` |
| `QueryStateHandler` | `query-state` | state | `StateProvider` |
| `StateGetHandler` | `state-get` | state | `ExtendedStateProvider` |
| `CaptureStartHandler` | `capture-start` | capture | `CaptureSession` |
| `CaptureStopHandler` | `capture-stop` | capture | `CaptureSession` |
| `WidgetAddHandler` | `widget-add` | widget | `WidgetCallback` |
| `WidgetRemoveHandler` | `widget-remove` | widget | `WidgetCallback` |
| `WidgetMoveHandler` | `widget-move` | widget | `WidgetCallback` |
| `WidgetResizeHandler` | `widget-resize` | widget | `WidgetCallback` |
| `WidgetFocusHandler` | `widget-focus` | widget | `WidgetCallback` |
| `WidgetTapHandler` | `widget-tap` | widget | `WidgetCallback` |
| `WidgetLongPressHandler` | `widget-long-press` | widget | `WidgetCallback` |
| `WidgetSettingsSetHandler` | `widget-settings-set` | widget | `WidgetCallback` |
| `DashboardResetHandler` | `dashboard-reset` | widget | `WidgetCallback` |
| `ThemeSetModeHandler` | `theme-set-mode` | theme | `ThemeCallback` |
| `ThemeApplyPresetHandler` | `theme-apply-preset` | theme | `ThemeCallback` |
| `AppSettingsSetHandler` | `app-settings-set` | settings | `SettingsCallback` |
| `PresetLoadHandler` | `preset-load` | preset | `PresetCallback` |
| `PresetSaveHandler` | `preset-save` | preset | `PresetCallback` |
| `PresetListHandler` | `preset-list` | preset | `PresetCallback` |
| `PresetRemoveHandler` | `preset-remove` | preset | `PresetCallback` |

Each handler follows the pattern:
```kotlin
class FooHandler(private val callback: FooCallback) : CommandHandler {
    override val name = "foo-command"
    override val description = "..."
    override val category = "foo"
    override suspend fun execute(params: JsonObject, commandId: String): CommandResult { ... }
    override fun paramsSchema(): JsonObject = buildJsonObject { ... }
}
```

### Key Patterns

1. **Builder pattern** for AgenticEngine assembly with optional capability callbacks
2. **Manual handler registration** (not KSP-generated) due to constructor dependency injection
3. **ADB shell security** via `Binder.getCallingUid()` check
4. **JSONL output** via logcat with UTF-8 aware chunking for large payloads
5. **Thread-safe capture** via `synchronized` blocks
6. **No Hilt** -- pure manual DI via builder. This is intentional for debug-only code.

### Migration Mapping

| Old (`core/agentic`) | New (`core/agentic`) | Notes |
|---------------------|---------------------|-------|
| All framework types | Retain | `CommandHandler`, `CommandRegistry`, `CommandResult`, `CommandDispatcher`, `AgenticEngine` |
| All callback interfaces | Retain | `NavigationCallback`, `StateProvider`, etc. |
| All capture types | Retain | `CaptureSession`, `EventEnvelope`, `NavigationEvent`, `InputEvent` |
| `BroadcastTransport` | Retain | Transport layer |
| `LogcatSink` | Retain | Output layer |
| Route types | Retain | `RouteInfo`, `RouteRegistry`, `@RouteDescription` |
| Command handlers | Adapt | Update widget/theme/settings callbacks to match new arch types |
| `@AgenticCommand` + KSP processor | Retain | For zero-arg handlers |
| `ContentProvider` transport | Add | New arch mentions agentic ContentProvider for `dump-semantics`/`query-semantics` |

The agentic module transfers almost 1:1. The main adaptation needed is updating callback interfaces to use the new type system (e.g., typed `DataSnapshot` subtypes instead of `Map<String, Any?>`, new profile concepts, new widget ID format).

---

## Cross-Module Dependency Graph

```
core/common  <--  core/plugin-api  <--  core/widget-primitives
                                               |
                                               +-- data:persistence (for AnyMapSerializer)

core/agentic  (independent, no deps on above)
```

## Summary of Major Migration Concerns

1. **`DataSnapshot` type safety**: Old uses `Map<String, Any?>` with runtime casting. New arch uses typed `@DashboardSnapshot` subtypes keyed by `KClass`. Every widget's data consumption pattern needs rewriting.

2. **Compose compiler in contracts**: Old applies full Compose compiler to `plugin-api`. New uses `compileOnly(compose.runtime)` -- must remove `EnumSetting.optionPreviews` (a `@Composable` lambda) from contracts and move to UI layer.

3. **Glow rendering**: Old uses `BlurMaskFilter` in `WidgetContainer`. New arch specifies `RenderEffect.createBlurEffect()`.

4. **Pack manifest**: Old uses `DashboardPackManifest` serializable data class. New arch uses KSP annotation-based discovery (`@DashboardWidget` with `typeId`/`packId` directly).

5. **Device management**: Heavily OBU-specific in old code (`DeviceServiceRegistry`, `CompanionDeviceHandler`, CDM integration). Needs generalization or scoping to pack-provided device support.

6. **Dual dispatcher patterns**: `DispatcherModule` (qualifier annotations) vs `DQXNDispatchers` (interface). Pick one. New arch should use qualifier annotations only.

7. **`InfoCardSettings` coupling**: Mixes data parsing with rendering concerns. Split pure parsing (contracts) from composable helpers (UI).
