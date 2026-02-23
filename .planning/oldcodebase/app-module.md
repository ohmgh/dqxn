# Old Codebase: App Module Analysis

**Source:** `/Users/ohm/Workspace/dqxn.old/android/app/`
**Analysis Date:** 2026-02-23

---

## 1. Entry Points

### Application Class

**File:** `app/src/main/java/app/dqxn/android/DqxnApp.kt`
**Class:** `DQXNApp` (note: uppercase `DQXN`, not `Dqxn`)
**Package:** `app.dqxn.android`

```kotlin
@HiltAndroidApp
class DQXNApp : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CdmPresenceEntryPoint {
        fun cdmPresenceActivator(): CdmPresenceActivator
    }

    override fun onCreate() {
        super.onCreate()
        // Activate CDM presence observation for all paired devices
        val entryPoint = EntryPointAccessors.fromApplication(this, CdmPresenceEntryPoint::class.java)
        entryPoint.cdmPresenceActivator().activate()
    }
}
```

**Responsibilities:**
- Hilt DI container initialization (`@HiltAndroidApp`)
- CDM (Companion Device Manager) presence activation on startup via `CdmPresenceActivator`
- Uses `EntryPoint` pattern to access Hilt-provided `CdmPresenceActivator` from `Application.onCreate()`

### Main Activity

**File:** `app/src/main/java/app/dqxn/android/MainActivity.kt`
**Class:** `MainActivity` extends `ComponentActivity` (not `AppCompatActivity`)
**Launch mode:** `singleTask`

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeAutoSwitchEngine: ThemeAutoSwitchEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(/* transparent bars */)
        // ...
        setContent {
            // Dynamic color + ThemeAutoSwitchEngine drives dark/light selection
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = ...) {
                    app.dqxn.android.navigation.DashboardShell()
                }
            }
        }
    }
}
```

**Responsibilities:**
- Splash screen via `installSplashScreen()` (AndroidX SplashScreen compat)
- Edge-to-edge rendering: transparent status bar + nav bar
- Injects `ThemeAutoSwitchEngine` for dark/light mode switching (5 modes: Light, Dark, System, Solar, Illuminance)
- `LaunchedEffect(darkTheme)` syncs: AppCompatDelegate night mode, `enableEdgeToEdge` styles, `insetsController` light/dark bars
- Root composable: `DashboardShell()` — the dashboard-as-shell architecture root
- Dynamic color support (Material You) on Android 12+, fallback to M3 default light/dark schemes

**Key detail:** Uses `AppCompatDelegate.setDefaultNightMode()` even though it extends `ComponentActivity` (not `AppCompatActivity`). This syncs DayNight for Dialogs.

---

## 2. DI Assembly

### App-Level Hilt Modules (main source set)

#### CoroutineModule
**File:** `app/src/main/java/app/dqxn/android/di/CoroutineModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

Provides a `@ApplicationScope`-qualified `CoroutineScope` with `SupervisorJob` + `Dispatchers.Default`. Used by `CdmPresenceActivator`, `DeviceServiceRegistryImpl`, and other app-scoped components.

#### DeviceModule
**File:** `app/src/main/java/app/dqxn/android/di/DeviceModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceModule {
    @Binds @Singleton
    abstract fun bindDeviceServiceRegistry(impl: DeviceServiceRegistryImpl): DeviceServiceRegistry

    @Binds @Singleton
    abstract fun bindConnectionNotifier(impl: ConnectionNotificationManager): ConnectionNotifier
}
```

Binds:
- `DeviceServiceRegistryImpl` → `DeviceServiceRegistry` (companion device dispatch)
- `ConnectionNotificationManager` → `ConnectionNotifier` (notification status for device connections)

### Debug Module
**File:** `app/src/debug/java/app/dqxn/android/debug/DebugModule.kt`

Provides all agentic/debug tooling bindings:

| Binding | Interface | Implementation |
|---------|-----------|----------------|
| `@Binds` | `PresetImportSource` | `PresetImportMediator` |
| `@Binds` | `NavControllerRegistry` | `NavControllerHolder` |
| `@Binds` | `CaptureSessionRegistry` | `CaptureSessionHolder` |
| `@Binds` | `DashboardViewModelRegistry` | `DashboardViewModelHolder` |
| `@Provides` | `LogcatSink` | (direct construction) |
| `@Provides` | `CaptureSession` | (context + sink) |
| `@Provides` | `DashboardStateProvider` | (context + layoutRepository) |
| `@Provides` | `DashboardExtendedStateProvider` | (context + layoutRepo + navRegistry + themeRepo + themeEngine + entitlementMgr + captureSession) |
| `@Provides` | `DashboardThemeCallback` | (themeRepository + themeAutoSwitchEngine) |
| `@Provides` | `DashboardSettingsCallback` | (no deps) |
| `@Provides` | `DashboardPresetCallback` | (presetLoader + layoutRepository) |
| `@Provides` | `DashboardWidgetCallback` | (layoutRepository + viewModelRegistry) |
| `@Provides` | `AgenticEngine` | (fully configured builder with all callbacks + GeneratedRouteRegistry) |

### Release Module
**File:** `app/src/release/java/app/dqxn/android/release/ReleaseModule.kt`

Provides no-op implementations for all debug-only interfaces:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ReleaseModule {
    @Provides @Singleton fun providePresetImportSource(): PresetImportSource = NoOpPresetImportSource
    @Provides @Singleton fun provideNavControllerRegistry(): NavControllerRegistry = NoOpNavControllerRegistry
    @Provides @Singleton fun provideCaptureSessionRegistry(): CaptureSessionRegistry = NoOpCaptureSessionRegistry
    @Provides @Singleton fun provideDashboardViewModelRegistry(): DashboardViewModelRegistry = NoOpDashboardViewModelRegistry
}
```

Also defines a private `NoOpCaptureSessionRegistry` object in the same file.

### Hilt Entry Points (main source set)

All in `app/src/main/java/app/dqxn/android/navigation/`:

| Entry Point | Interface | Component | Purpose |
|-------------|-----------|-----------|---------|
| `SetupEntryPoints` | `ProviderSettingsStore`, `PairedDeviceStore`, `DeviceServiceRegistry`, `ConnectionEventStore` | `SingletonComponent` | Accessed from `OverlayNavHost` composables for setup flow |
| `NavControllerRegistryEntryPoint` | `NavControllerRegistry` | `SingletonComponent` | Register NavController from DashboardShell |
| `DashboardViewModelRegistryEntryPoint` | `DashboardViewModelRegistry` | `SingletonComponent` | Register ViewModel from DashboardShell |
| `CaptureSessionRegistryEntryPoint` | `CaptureSessionRegistry` | `SingletonComponent` | Access capture session from DashboardShell |
| `CdmPresenceEntryPoint` (in DQXNApp) | `CdmPresenceActivator` | `SingletonComponent` | CDM activation from Application.onCreate() |

### Debug-only Entry Point

| Entry Point | Interface | File |
|-------------|-----------|------|
| `AgenticReceiverEntryPoint` | `AgenticEngine` | `app/src/debug/java/app/dqxn/android/debug/AgenticReceiverEntryPoint.kt` |

---

## 3. Navigation

### Architecture: Dashboard-as-Shell with OverlayNavHost

**File:** `app/src/main/java/app/dqxn/android/navigation/DashboardShell.kt`

The dashboard is NOT a NavHost destination. It composes at Layer 0 always. Overlays compose at Layer 1 via `OverlayNavHost`.

```
Box {
    // Layer 0: Dashboard (always present, optionally "suspended")
    DashboardLayer(viewModel, suspended, editingWidgetId, onNavigate)

    // Layer 1: Overlay NavHost (settings, pickers, confirmations)
    OverlayNavHost(navController, dashboardViewModel)
}
```

**DashboardShell responsibilities:**
- Creates activity-scoped `DashboardViewModel` via `hiltViewModel()`
- Creates `NavController` via `rememberNavController()`
- Registers NavController, ViewModel, and CaptureSessionRegistry with their holders (for debug tooling access)
- Tracks navigation for capture events (OnDestinationChangedListener)
- Determines suspension state (Layer 0 stops observing state for opaque overlays)
- Extracts `editingWidgetId` from back stack for widget peek animation

**Suspension:** When the current route is in `SUSPENDABLE_ROUTES`, the dashboard stops recomposing (frozen state). Suspendable routes:
- `Route.Settings`
- `Route.PackBrowser`
- `Route.ThemeModeSelector`
- `Route.ProviderSetup`

### Routes

**File:** `app/src/main/java/app/dqxn/android/navigation/Routes.kt`

All routes are `@Serializable` data classes/objects in a `sealed interface Route`:

| Route | Type | Category | Deep Link |
|-------|------|----------|-----------|
| `Empty` | `data object` | Start destination | - |
| `Dashboard` | `data object` | (Declared but not used in NavHost — dashboard is always-on) | - |
| `Settings` | `data object` | Preview overlay | `/open/sheets/settings` |
| `ThemeSelector(isDark)` | `data class` | Preview overlay | `/open/sheets/theme` |
| `ThemeModeSelector` | `data object` | Preview overlay | `/open/sheets/theme-mode` |
| `ThemeEditor(baseThemeId, customThemeId?)` | `data class` | Preview overlay | `/open/sheets/theme-editor/{baseThemeId}` |
| `WidgetPicker` | `data object` | Hub | `/open/sheets/widgets` |
| `WidgetSettings(widgetId, startOnDataSourcePage, startOnInfoPage)` | `data class` | Preview overlay (38% peek) | `/open/sheets/widget/{widgetId}` |
| `PackBrowser` | `data object` | Hub | `/open/sheets/packs` |
| `ProviderSetup(providerId)` | `data class` | Hub | `/open/sheets/provider-setup/{providerId}` |
| `Diagnostics(handlerId, definitionId)` | `data class` | Hub | - |
| `PermissionRequest(widgetId, sourceId)` | `data class` | Hub | `/open/sheets/permission/{widgetId}/{sourceId}` |
| `DeleteConfirmation(widgetId)` | `data class` | Confirmation | `/open/sheets/delete/{widgetId}` |
| `ResetConfirmation` | `data object` | Confirmation | `/open/sheets/reset` |
| `TimezoneSelector(widgetId, settingKey, currentValue?)` | `data class` | Hub | `/open/sheets/timezone/{widgetId}/{settingKey}` |
| `DateFormatSelector(widgetId, settingKey, currentValue)` | `data class` | Hub | `/open/sheets/dateformat/{widgetId}/{settingKey}` |
| `AppSelector(widgetId, settingKey, currentPackage?)` | `data class` | Hub | `/open/sheets/app/{widgetId}/{settingKey}` |

### OverlayNavHost

**File:** `app/src/main/java/app/dqxn/android/navigation/OverlayNavHost.kt` (1060 lines — the largest file in the module)

Uses Compose Navigation's `NavHost` with `Route.Empty` as start destination. Three overlay categories:

1. **Hubs** (opaque fullscreen): `PackBrowser`, `ProviderSetup`, `Diagnostics`, `PermissionRequest`, `WidgetPicker`, `TimezoneSelector`, `DateFormatSelector`, `AppSelector`
2. **Previews** (transparent peek): `Settings`, `ThemeSelector`, `ThemeModeSelector`, `ThemeEditor`, `WidgetSettings`
3. **Confirmations** (dialog scrim): `DeleteConfirmation`, `ResetConfirmation`

Each has custom transitions via `DashboardMotion`:
- Hubs: `hubEnter`/`hubExit` (fade+scale)
- Previews: `previewEnter`/`previewExit` (slide up/down)
- Confirmations: `dialogScrimEnter`/`dialogScrimExit` (fade scrim + scale dialog)

`PreviewOverlay` composable provides a tap-to-dismiss transparent zone above the content (dashboard peek visible).

### Navigation Registry Interfaces

Three interfaces with debug/release split:

| Interface | File | Debug Impl | Release Impl |
|-----------|------|------------|--------------|
| `NavControllerRegistry` | `navigation/NavControllerRegistry.kt` | `NavControllerHolder` | `NoOpNavControllerRegistry` |
| `DashboardViewModelRegistry` | `navigation/DashboardViewModelRegistry.kt` | `DashboardViewModelHolder` | `NoOpDashboardViewModelRegistry` |
| `CaptureSessionRegistry` | `navigation/CaptureSessionRegistry.kt` (typealias) | `CaptureSessionHolder` | `NoOpCaptureSessionRegistry` (in ReleaseModule) |

### KSP Route Generation

`ksp(project(":core:agentic-processor"))` generates `GeneratedRouteRegistry` from the `Route` sealed interface. Produces a `RouteInfo` list with path patterns, parameters, descriptions, and deep links. Used by `AgenticEngine` for the `nav-list-routes` command.

---

## 4. Manifest

### Main Manifest

**File:** `app/src/main/AndroidManifest.xml`

#### Permissions

| Permission | Purpose |
|------------|---------|
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | Legacy BT (SDK merge) |
| `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` | BLE for OBU device, `neverForLocation` flag |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Network access |
| `ACCESS_FINE_LOCATION` (maxSdk 30) | BT scanning on API ≤30 |
| `ACCESS_COARSE_LOCATION` (maxSdk 30) | BT scanning on API ≤30 |
| `FOREGROUND_SERVICE` | FGS for driving mode |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | FGS type for BLE |
| `FOREGROUND_SERVICE_SPECIAL_USE` | FGS type |
| `POST_NOTIFICATIONS` | Notification permission (API 33+) |
| `VIBRATE` | Haptic feedback for alerts |
| `${applicationId}.permission.OBU_DATA_ACCESS` | Custom permission for OBU SDK |
| `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE` | CDM presence observation |

#### Features

- `android.software.companion_device_setup` — required

#### Package Visibility (queries)

- `com.mls.nets.reader` — NETS card reader app
- `com.Daylight.EzLinkAndroid` — EzLink app
- `sg.com.transitlink` — TransitLink app
- Market scheme intent — Play Store

#### Application

- `android:name=".DQXNApp"`
- `android:enableOnBackInvokedCallback="true"` — predictive back
- `android:theme="@style/Theme.DQXN"` — Material3 DayNight NoActionBar

#### Components

| Component | Type | Details |
|-----------|------|---------|
| `MainActivity` | Activity | `singleTask`, splash theme, launcher, deep link `https://dqxn.app/open` |
| `AppCompanionDispatcher` | Service | CDM service, `BIND_COMPANION_DEVICE_SERVICE` permission, API 31+ |

#### Metadata

- `com.google.android.gms.car.application` → `@xml/automotive_app` (template-based AA)

### Debug Manifest

**File:** `app/src/debug/AndroidManifest.xml`

Adds two debug-only receivers:

| Component | Action | Purpose |
|-----------|--------|---------|
| `ImportReceiver` | `app.dqxn.android.DEBUG_IMPORT` | ADB-based preset import |
| `AgenticReceiver` | `app.dqxn.android.AGENTIC.*` (26 actions) | ADB-based agentic commands |

Both receivers are `android:exported="true"` — acceptable since debug-only.

Agentic command categories:
- **Discovery:** `list-commands`
- **Utility:** `ping`
- **Navigation:** `nav-goto`, `nav-back`, `nav-list-routes`
- **State:** `state-get`, `state-query`
- **Widget:** `widget-add`, `widget-remove`, `widget-move`, `widget-resize`, `widget-focus`, `widget-tap`, `widget-long-press`, `widget-settings-set`
- **Theme:** `theme-set-mode`, `theme-apply-preset`
- **Preset:** `preset-load`, `preset-save`, `preset-list`, `preset-remove`
- **Settings:** `settings-set`
- **Dashboard:** `dashboard-reset`
- **Capture:** `capture-start`, `capture-stop`

---

## 5. Build Config

**File:** `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.dqxn.android.application)
    alias(libs.plugins.dqxn.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

ksp {
    arg("routePackage", "app.dqxn.android.navigation")
}
```

| Setting | Value |
|---------|-------|
| `namespace` | `app.dqxn.android` |
| `applicationId` | `app.dqxn.android` |
| `versionCode` | `1` |
| `versionName` | `0.5.0` |
| `compileSdk` | `release(36) { minorApiLevel = 1 }` |
| `buildToolsVersion` | `36.1.0` |

**BuildConfigFields:**
- `OBU_SDK_KEY` (String) — hardcoded placeholder `"YOUR_SDK_KEY_HERE"`
- `DEMO_MODE_AVAILABLE` (Boolean) — `true` in debug, `false` in release

**Packaging:** Excludes `pack/manifest.json` from resources.

**Managed Devices:**
- `pixel6api33`: Pixel 6, API 33, `aosp-atd` image
- Group `phones` containing `pixel6api33`

### Dependencies

**Module dependencies:**
- `:core:common`, `:core:plugin-api`, `:core:widget-primitives` — core contracts
- `:feature:packs:sg-erp2`, `:feature:packs:demo`, `:feature:packs:themes` — pack modules (old structure under `feature/packs/`)
- `:data:persistence` — data layer
- `:feature:dashboard`, `:feature:driving` — features
- `:core:agentic` — agentic framework (should be `debugImplementation` but is `implementation` with "R8 will tree-shake" note)
- `:core:agentic-processor` — KSP processor for route listing

**Key library dependencies:**
- `androidx.core.splashscreen` — splash screen compat
- `com.google.android.material:material:1.12.0` — hardcoded (not from version catalog)
- `hilt.navigation.compose` — Hilt ViewModel integration
- `navigation.compose` + `kotlinx.serialization.json` — type-safe navigation
- `sqlcipher` — forced override for 16KB page compatibility (OBU SDK transitive dep)

---

## 6. Debug vs Release Differences

### DI Bindings

| Interface | Debug | Release |
|-----------|-------|---------|
| `PresetImportSource` | `PresetImportMediator` (SharedPrefs queue) | `NoOpPresetImportSource` |
| `NavControllerRegistry` | `NavControllerHolder` (volatile ref) | `NoOpNavControllerRegistry` |
| `CaptureSessionRegistry` | `CaptureSessionHolder` (wraps `CaptureSession`) | `NoOpCaptureSessionRegistry` |
| `DashboardViewModelRegistry` | `DashboardViewModelHolder` (volatile ref) | `NoOpDashboardViewModelRegistry` |
| `AgenticEngine` | Fully configured (debug only) | Not provided |
| `LogcatSink`, `CaptureSession`, `DashboardStateProvider`, etc. | Provided | Not provided |

### Build Type Config

| Setting | Debug | Release |
|---------|-------|---------|
| `applicationIdSuffix` | `.debug` | (none) |
| `isDebuggable` | `true` | (default `false`) |
| `isMinifyEnabled` | (default `false`) | `true` |
| `isShrinkResources` | (default `false`) | `true` |
| `DEMO_MODE_AVAILABLE` | `true` | `false` |
| ProGuard | (none) | `proguard-android-optimize.txt` + `proguard-rules.pro` |

### Debug-Only Components

**Broadcast Receivers:**
- `AgenticReceiver` — ADB command receiver (26 agentic commands)
- `ImportReceiver` — ADB preset import receiver

**Debug Classes (15 files in `app/src/debug/java/app/dqxn/android/debug/`):**

| Class | Role |
|-------|------|
| `DebugModule` | Hilt module providing all debug bindings |
| `AgenticReceiver` | BroadcastReceiver for agentic commands |
| `AgenticReceiverEntryPoint` | Hilt EntryPoint for AgenticEngine |
| `NavControllerHolder` | Volatile holder for NavController |
| `DashboardViewModelHolder` | Volatile holder for DashboardViewModel |
| `CaptureSessionHolder` | Wraps CaptureSession for main source set |
| `DashboardNavigationCallback` | NavigationCallback impl (nav-goto, nav-back) |
| `DashboardStateProvider` | Legacy state query (JSON capture of dashboard) |
| `DashboardExtendedStateProvider` | Domain-filtered state query (nav, widgets, theme, etc.) |
| `DashboardThemeCallback` | Theme mode and preset commands |
| `DashboardSettingsCallback` | Settings manipulation (in-memory v1) |
| `DashboardPresetCallback` | Preset CRUD (load from assets, in-memory save) |
| `DashboardWidgetCallback` | Widget CRUD, gestures, settings via ViewModel dispatch |
| `ImportReceiver` | Preset file import from device filesystem |
| `PresetImportMediator` | SharedPrefs-based import queue between receiver and ViewModel |

---

## 7. Device & Service Infrastructure

### CdmPresenceActivator
**File:** `app/src/main/java/app/dqxn/android/device/CdmPresenceActivator.kt`

Called from `DQXNApp.onCreate()`. Launched on `@ApplicationScope` coroutine. Responsibilities:
1. Gets CDM associations (source of truth)
2. Cleans stale local `PairedDeviceStore` entries not in CDM
3. Calls `startObservingDevicePresence` for each association
   - API 36+: `ObservingDevicePresenceRequest.Builder().setAssociationId()`
   - API 31-35: MAC-based (deprecated)

### DeviceServiceRegistryImpl
**File:** `app/src/main/java/app/dqxn/android/device/DeviceServiceRegistryImpl.kt`

Central dispatcher for companion device events. Implements `DeviceServiceRegistry`:
- `register(registration)` — packs register handlers with MAC addresses and name prefixes
- `dispatchAppeared(mac, name, associationId)` — routes to matching handler (MAC match > name prefix match)
- `dispatchDisappeared(mac)` — routes to handler
- `getPresentDevices(handlerId)` — central presence tracking
- `reportConnected/Disconnected/ConnectFailed` — logs to `ConnectionEventStore`
- `disassociate(mac)` — CDM disassociation + local cleanup
- `getDeviceManagement(handlerId)` — returns handler as `DeviceManagement` if supported

### AppCompanionDispatcher
**File:** `app/src/main/java/app/dqxn/android/service/AppCompanionDispatcher.kt`

`CompanionDeviceService` subclass, `@AndroidEntryPoint`. System-woken on device presence events. Delegates to `DeviceServiceRegistry.dispatchAppeared/dispatchDisappeared`. Three API level patterns:
- API 36+: `onDevicePresenceEvent(DevicePresenceEvent)` — BLE appeared/disappeared, BT connected/disconnected, self-managed
- API 33+: `onDeviceAppeared(AssociationInfo)` / `onDeviceDisappeared(AssociationInfo)`
- API 31-32: `onDeviceAppeared(address: String)` / `onDeviceDisappeared(address: String)`

### AlertSoundManager
**File:** `app/src/main/java/app/dqxn/android/service/AlertSoundManager.kt`

`@Singleton`. ERP-specific audio alerts:
- Three modes: `SILENT`, `VIBRATE`, `SOUND`
- `playErpChargeAlert()` — ringtone + vibration + TTS readout (dollar/cent amounts)
- `playLowBalanceWarning()` — alarm sound + TTS
- `playTrafficAlert()` — notification sound + TTS
- Custom sound URIs via `StateFlow`
- TTS via `TextToSpeech` (Locale.US)
- Vibration patterns per alert type

### ConnectionNotificationManager
**File:** `app/src/main/java/app/dqxn/android/notification/ConnectionNotificationManager.kt`

`@Singleton`, implements `ConnectionNotifier`. Lightweight — no foreground service:
- `showConnected(deviceName)` — posts low-priority notification
- `showDisconnected()` — cancels notification
- Channel: `device_connection`, `IMPORTANCE_LOW`, no badge

---

## 8. Resources

### Themes

**`values/themes.xml`:** `Theme.DQXN` parent `Theme.Material3.DayNight.NoActionBar` with transparent bars. `Theme.App.Starting` parent `Theme.SplashScreen` with dark blue background (`#0f172a`).

**`values-v31/themes.xml`:** Extended splash attributes (`windowSplashScreenBehavior`, `windowSplashScreenBrandingImage`), both native and compat splash attributes.

### Colors

`ic_launcher_background` = `#0f172a` (dark navy), `ic_launcher_foreground` = `#FFFFFF`.

### Strings

Only 3 entries: `app_name` = "DQXN", OBU permission label/description.

### Drawables

- `ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` — adaptive icon
- `ic_launcher_runner_foreground.xml` — runner variant
- `ic_logo_letterform.xml` — splash logo

### XML

`automotive_app.xml` — Android Auto template declaration.

---

## 9. Tests

**File:** `app/src/androidTest/java/app/dqxn/android/DashboardSmokeTest.kt`

Minimal smoke test (JUnit4 + `AndroidJUnit4` runner):

```kotlin
@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {
    @Test fun appContext_hasCorrectPackage() {
        assertEquals("app.dqxn.android.debug", appContext.packageName)
    }
    @Test fun appContext_isNotNull() {
        assertNotNull(appContext)
    }
}
```

Run command: `./gradlew pixel6api33DebugAndroidTest --tests "*DashboardSmokeTest"`

No unit tests in the app module. No Hilt integration tests. No UI tests.

---

## 10. Migration Notes (Old → New)

### What maps directly to new `:app`

| Old Component | New Equivalent | Notes |
|---------------|---------------|-------|
| `DQXNApp` (`@HiltAndroidApp`) | Same pattern | CDM activation moves to pack-level or is generalized |
| `MainActivity` (single activity) | Same pattern | Keep `ComponentActivity` + `enableEdgeToEdge` + splash |
| `DashboardShell` composable | Same concept but new module structure | Dashboard-as-shell with `OverlayNavHost` is the target pattern per CLAUDE.md |
| `Route` sealed interface | Same pattern | Routes will change (no driving, different pack structure) |
| `CoroutineModule` | Same | `@ApplicationScope` + `SupervisorJob` + `Dispatchers.Default` |
| `DeviceModule` | Same concept | Bindings may change as CDM moves pack-side |

### What changes significantly

1. **Pack dependencies:** Old uses `:feature:packs:sg-erp2`, `:feature:packs:demo`, `:feature:packs:themes`. New uses `:pack:essentials`, `:pack:plus`, `:pack:themes`, `:pack:demo`. App depends on packs, packs depend on `:sdk:*` only.

2. **Driving mode:** Old imports `:feature:driving`. New defers driving entirely post-launch. Remove `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` unless packs need them.

3. **Agentic framework:** Old has `implementation(project(":core:agentic"))` in main (R8 tree-shake claim). New should use `debugImplementation` properly since the new `core/agentic` module is build-time only per CLAUDE.md.

4. **OBU-specific code:** `AlertSoundManager` (ERP alerts, TTS), CDM device handling, OBU SDK key, Bluetooth permissions, `sqlcipher` override — all are Singapore-ERP-specific. In the new codebase, these belong in `:pack:essentials` or a dedicated SG-ERP pack, NOT in `:app`.

5. **Navigation:** Routes will be restructured. The overlay categories (Hub/Preview/Confirmation) pattern stays, but specific routes like `ProviderSetup`, `Diagnostics`, pack-specific pickers need redesign.

6. **Deep links:** Old uses `https://dqxn.app/open/sheets/*`. New should maintain the pattern but with updated route names.

7. **Auto Switch Modes:** Old has 5 modes (Light, Dark, System, Solar, Illuminance). This moves to `:core:design` or `:feature:dashboard` — not app-level.

8. **`automotive_app.xml`:** Android Auto template metadata. Evaluate if this stays in the new codebase or defers post-launch.

### What to drop entirely

- `OBU_SDK_KEY` build config field — pack-level concern
- Singapore-specific package visibility queries (NETS, EzLink, TransitLink)
- `OBU_DATA_ACCESS` custom permission
- ERP-specific `AlertSoundManager` — generalize into `:sdk:common` notification abstraction
- `DashboardStateProvider` legacy class — replaced by `DashboardExtendedStateProvider` pattern
- `PresetImportMediator` SharedPrefs approach — use DataStore in new codebase
- `DashboardSettingsCallback` in-memory implementation — needs proper DataStore backing

### Critical patterns to preserve

1. **Dashboard-as-Shell:** Dashboard always composes at Layer 0, overlays at Layer 1 via `OverlayNavHost`. This is explicitly the target architecture per CLAUDE.md.
2. **Suspension pattern:** Freeze dashboard recomposition during opaque hub overlays.
3. **Debug/Release module split:** No-op implementations in release, full implementations in debug.
4. **Volatile holders pattern:** `NavControllerHolder` / `DashboardViewModelHolder` for bridging Compose-scoped objects to Singleton-scoped debug tooling.
5. **EntryPoint pattern:** Using `@EntryPoint` + `EntryPointAccessors` for accessing Hilt dependencies from Application, BroadcastReceivers, and composables that can't use `@AndroidEntryPoint`.
6. **`SupervisorJob` for application scope:** Ensures child coroutine failures don't cancel the scope.
7. **PreviewOverlay pattern:** Tap-to-dismiss transparent zone with content anchored to bottom.
8. **CdmPresenceActivator startup pattern:** Must re-register presence observation on app startup (doesn't persist across process death).
