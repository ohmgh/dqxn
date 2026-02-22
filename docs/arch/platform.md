# Platform Integration

> Navigation, alerts, security requirements, and permissions.

## Navigation

Type-safe routes via `sealed interface Route` with `@Serializable`:

| Route | Purpose |
|---|---|
| `Empty` | Dashboard only, no overlay |
| `Settings` | Main settings sheet |
| `ThemeSelector(isDark)` | Theme picker grid |
| `ThemeModeSelector` | Auto-switch mode selector |
| `WidgetPicker` | Widget picker with previews |
| `PackBrowser` | Installed packs listing |
| `WidgetSettings(widgetId, ...)` | Per-widget settings (3-page pager) |
| `ThemeEditor(baseThemeId, ...)` | Theme Studio |
| `TimezoneSelector(...)` | Searchable timezone picker |
| `DateFormatSelector(...)` | Date format picker with preview |
| `AppSelector(...)` | App picker for Shortcuts widget |
| `ProviderSetup(providerId)` | Schema-driven provider setup wizard |
| `Diagnostics(...)` | Device connection diagnostics |

Confirmation dialogs use composable state (`var showConfirm by remember`), not navigation destinations. Permission requests are side-effects within the calling screen.

### Animation Profiles

- **Sheets** (button bars): spring slide from bottom
- **Hubs** (fullscreen overlays): bouncy spring scale 0.85->1.0 + fade
- **Previews** (settings sheets): balanced spring slide up
- **Dialogs**: fade scrim + scale card
- **PackBrowser**: direction-sensitive (horizontal slide from Settings, crossfade from WidgetInfo)
- **Shared element transitions**: Pack card <-> Widget info

Deep links at `https://dqxn.app/open/*`.

### Deep Link Security

- `/.well-known/assetlinks.json` at `dqxn.app` with app signing certificate fingerprint
- `autoVerify="true"` on all `<intent-filter>` elements
- All parameters validated at `NavHost` level before routing
- Unknown/malformed parameters -> dashboard (safe fallback)

### Predictive Back Gesture

Android 14+ predictive back fully supported:
- `android:enableOnBackInvokedCallback="true"` in manifest
- Overlay sheets use `PredictiveBackHandler` for animated dismiss previews
- Confirmation dialogs in edit mode intercept back for save/discard prompt
- Dashboard Layer 0 does not consume back

## Alerts & Notifications

DQXN has three distinct notification surfaces, each with different lifetimes and consumer patterns. A fourth surface — widget status overlays (`WidgetStatusCache`) — is continuous per-widget state and intentionally excluded from notification coordination (see [State Management](state-management.md)).

```
┌──────────────────────────────────────────────────────────────┐
│                     Emission Sources                         │
│                                                              │
│  CrashRecovery  EntitlementManager  ConnectionStateMachine   │
│  StorageMonitor  ThemeCoordinator   LayoutCoordinator        │
│  Window metrics                                              │
└─────────────────────────┬────────────────────────────────────┘
                          │
              ┌───────────▼────────────┐
              │ NotificationCoordinator │  @ViewModelScoped
              │                        │  feature:dashboard
              │ • Priority ordering    │
              │ • Active banner state  │
              │ • Toast delivery       │
              │ • Auto-dismiss timers  │
              └──┬──────────┬──────────┘
                 │          │
    ┌────────────▼──┐  ┌───▼──────────────────┐
    │  In-App UI    │  │  AlertSoundManager    │  @Singleton
    │  (Layer 0.5)  │  │  (via AlertEmitter)   │  bound in :app
    │               │  │                       │
    │  Banners      │  │  SILENT/VIBRATE/SOUND │
    │  Toasts       │  │  TTS readout          │
    └───────────────┘  │  Custom sound URIs    │
                       └───────────────────────┘

    ┌──────────────────────────────────┐
    │  SystemNotificationBridge        │  @Singleton, :app
    │                                  │
    │  FGS notification (in-place)     │
    │  Connection status channel       │
    └──────────────────────────────────┘
```

### Notification Types

```kotlin
// :sdk:contracts
sealed interface InAppNotification {
    val id: String
    val priority: NotificationPriority
    val timestamp: Long  // elapsedRealtimeNanos
    val alertProfile: AlertProfile?  // null = silent visual-only

    // Ephemeral — auto-dismiss, no user action
    data class Toast(
        override val id: String,
        override val priority: NotificationPriority,
        override val timestamp: Long,
        override val alertProfile: AlertProfile?,
        val message: String,
        val durationMs: Long = 4_000L,
    ) : InAppNotification

    // Persistent — until dismissed or condition resolves, supports actions
    data class Banner(
        override val id: String,
        override val priority: NotificationPriority,
        override val timestamp: Long,
        override val alertProfile: AlertProfile?,
        val title: String,
        val message: String,
        val actions: ImmutableList<NotificationAction>,
        val dismissible: Boolean,  // false = persists until condition resolves
    ) : InAppNotification
}

enum class NotificationPriority {
    CRITICAL,  // thermal shutdown, safe mode — always shown
    HIGH,      // provider batch disconnect — shown, auto-dismiss shortened
    NORMAL,    // entitlement change, layout reset
    LOW,       // background info
}

@Immutable
data class AlertProfile(
    val mode: AlertMode,
    val soundUri: String?,           // custom URI, null = default
    val ttsMessage: String?,         // null = no TTS
    val vibrationPattern: ImmutableList<Long>?,
)

enum class AlertMode { SILENT, VIBRATE, SOUND }

@Immutable
data class NotificationAction(
    val label: String,
    val actionId: String,
)
```

### NotificationCoordinator

`@ViewModelScoped` in `:feature:dashboard`. Fifth coordinator alongside Layout, EditMode, Theme, and WidgetBinding.

Internally uses two primitives:
- `Channel<InAppNotification.Toast>(capacity = Channel.BUFFERED)` — exactly-once delivery, buffered to prevent producer suspension when multiple toasts fire in rapid succession (entitlement revocation + theme preview end). No replay on configuration change (toasts are ephemeral — replaying them after recreation is wrong)
- `StateFlow<ImmutableList<InAppNotification.Banner>>` — active banners, re-derived from `@Singleton` state sources on ViewModel recreation

**Banner stacking**: At most two banners visible: one CRITICAL + one non-critical. When no CRITICAL banner is active, the highest-priority non-critical banner is shown alone. This prevents queue starvation where a persistent CRITICAL banner (safe mode, `dismissible = false`) blocks visibility of all other conditions indefinitely — the user must still see HIGH-priority alerts like "storage critically low" or "offline grace period expired." A count indicator shows when additional banners are queued beyond the visible slots. Priority ordering: `CRITICAL > HIGH > NORMAL > LOW`.

**Banner dismissal**: Condition-based banners observe their source `StateFlow` and auto-remove when the condition resolves (e.g., low storage banner disappears when storage is freed, safe mode banner disappears after layout reset). The coordinator does not persist banner state — persistent conditions re-derive from their `@Singleton` sources on ViewModel recreation.

**Banner identity**: Banner IDs MUST be condition-keyed strings (`"safe_mode"`, `"ble_adapter_off"`, `"low_storage"`, `"grace_expired"`), not generated UUIDs. This ensures: (1) the same condition always produces the same banner identity for stable animation (no flicker on state oscillation like BLE connection flapping), (2) `showBanner` with an existing ID updates in-place rather than dismiss+recreate, and (3) `dismissBanner(id)` targets the correct instance without lookup.

**Banner action routing**: Banner actions route via `Channel<NotificationActionEvent>(capacity = Channel.BUFFERED)` consumed by the coordinator. When a user taps a banner action, the `NotificationBannerHost` sends a `NotificationActionEvent(bannerId, actionId)` to the channel. The coordinator's sequential consumption loop dispatches to the appropriate subsystem:

```kotlin
data class NotificationActionEvent(val bannerId: String, val actionId: String)

// In NotificationCoordinator init
scope.launch {
    for (event in actionChannel) {
        when (event.bannerId to event.actionId) {
            "safe_mode" to "reset_layout" -> layoutCoordinator.dispatch(DashboardCommand.ResetLayout())
            "safe_mode" to "report_crash" -> crashRecovery.reportAndDismiss()
            // ... other action mappings
        }
    }
}
```

Action routing via Channel + ID dispatch (not callback lambdas) keeps the banner type serializable and avoids capturing mutable state in the UI layer.

### Notification Ownership Table

Every user-facing message in the requirements maps to exactly one owner:

| Requirement | Message | Owner | Type |
|---|---|---|---|
| NF38 | "App recovered from crash" + reset/report | `NotificationCoordinator` | Banner, CRITICAL |
| NF41 | Low storage warning | `NotificationCoordinator` | Banner, HIGH |
| F1.25 | "DQXN works best in fullscreen" | `NotificationCoordinator` | Banner, NORMAL |
| F7.2 | "Your layout was reset after an update" | `NotificationCoordinator` | Toast, NORMAL |
| F4.6 | "Theme preview ended" | `NotificationCoordinator` | Toast, NORMAL |
| F8.9 | Entitlement revocation explanation | `NotificationCoordinator` | Toast, NORMAL |
| F8.10 | 7-day offline grace period expired | `NotificationCoordinator` | Banner, HIGH |
| NF42 | "Unable to save. Free up storage space" | `NotificationCoordinator` | Toast, HIGH |
| F10.5 | Speed limit alert (audio/haptic) | `AlertSoundManager` | AlertProfile only |
| F9.1 | Connection status | `SystemNotificationBridge` | System notification |
| F3.15 | Widget error states | `WidgetStatusCache` | Per-widget overlay |
| F3.10 | Provider fallback indicator | `WidgetStatusCache` | Per-widget overlay |

### Shell Subsystem Emission

`@Singleton` subsystems cannot call the `@ViewModelScoped` coordinator directly — that's a Hilt scope violation. Instead, the coordinator **observes** singleton state flows and derives banners reactively. Domain-specific logic (debouncing, what constitutes a BLE adapter failure vs individual disconnect) stays in the subsystem via its state representation. The coordinator maps states to banners.

```kotlin
// In NotificationCoordinator init — observes @Singleton state flows
// This is 5 launch blocks, not a rules engine. The domain knowledge stays
// in the subsystem's state representation, not in the coordinator.

scope.launch {
    connectionStateMachine.state
        .map { it.bleAdapterOff to it.affectedDeviceCount }
        .distinctUntilChanged()
        .collect { (adapterOff, count) ->
            if (adapterOff) {
                showBanner(
                    id = "ble_adapter_off",
                    title = "Bluetooth Off",
                    message = "$count devices disconnected",
                    priority = NotificationPriority.HIGH,
                    dismissible = false,
                )
            } else {
                dismissBanner("ble_adapter_off")
            }
        }
}

scope.launch {
    crashRecovery.safeModeActive  // @Singleton, backed by SharedPreferences
        .distinctUntilChanged()
        .collect { active ->
            if (active) {
                showBanner(
                    id = "safe_mode",
                    title = "App Recovered",
                    message = "Dashboard recovered from repeated crashes",
                    priority = NotificationPriority.CRITICAL,
                    actions = persistentListOf(
                        NotificationAction("Reset Layout", "reset_layout"),
                        NotificationAction("Report", "report_crash"),
                    ),
                    dismissible = false,
                )
            } else {
                dismissBanner("safe_mode")
            }
        }
}

// Similar for: StorageMonitor.isLow, EntitlementManager.graceExpired, windowMetrics
```

**Why this isn't the killed "rules engine"**: The rules engine was a standalone class injecting every subsystem's flows, creating hidden coupling. Here, the coordinator — whose explicit job is mapping state to user-facing notifications — observes the same flows it already needs for re-derivation on ViewModel recreation. No new coupling, no new abstraction. The subsystems expose meaningful state (`bleAdapterOff`, `safeModeActive`); the coordinator maps to banners.

**Scaling threshold**: V1 has ~5 `scope.launch` observer blocks. This inline pattern remains appropriate up to ~10 source flows. Beyond that (likely when V2 pack `NotificationEmitter` and thermal notifications are added), extract the state→banner mapping into a declarative `NotificationRule` list that the coordinator iterates. The trigger for extraction: when a new source requires modifying the coordinator's constructor signature and init block simultaneously, the inline pattern has outgrown its simplicity benefit.

**Activity-dead scenarios**: When the Activity is killed (low memory) while the FGS keeps running, subsystem state changes still occur in `@Singleton` scope. `SystemNotificationBridge` (`@Singleton`) independently observes `ConnectionStateMachine.state` for system notification updates — this path never depends on the coordinator. When the Activity recreates and the coordinator initializes, it re-derives all banners from current singleton state. No events are lost because the coordinator never stores events — it projects current conditions.

### In-App Notification Rendering (Layer 0.5)

Banners and toasts render between the dashboard canvas (Layer 0) and navigation overlays (Layer 1):

```kotlin
Box {
    // Layer 0: always present, draws behind system bars
    DashboardLayer(...)

    // Layer 0.5: non-critical banners and toasts — above widgets, below overlays
    NotificationBannerHost(
        banners = activeBanners.filter { it.priority != CRITICAL },
        toasts = toasts,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer { }  // isolated RenderNode — banner animations don't invalidate Layer 0
            .padding(WindowInsets.statusBars.asPaddingValues()),  // always respect status bar
    )

    // Layer 1: navigation overlays
    OverlayNavHost(...)

    // Layer 1.5: CRITICAL banners — above overlays, always visible
    CriticalBannerHost(
        banner = activeBanners.firstOrNull { it.priority == CRITICAL },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer { }  // isolated RenderNode
            .padding(WindowInsets.statusBars.asPaddingValues()),
    )
}
```

**Performance**: `graphicsLayer` isolation on the banner host ensures banner appear/dismiss animations don't trigger measure/layout passes on the dashboard grid beneath. Notifications are infrequent events — `AnimatedVisibility` with spring animations is appropriate here (unlike widget drag, which is continuous).

**Inset behavior**: Banners always respect `WindowInsets.statusBars` regardless of Layer 0's edge-to-edge rendering. Banners are informational chrome, not dashboard content.

**When overlays are open**: Non-critical banners are suppressed while a Layer 1 overlay is active. Critical banners (safe mode) render in `CriticalBannerHost` at Layer 1.5, above `OverlayNavHost`. In Compose `Box`, later children draw on top — placing `CriticalBannerHost` after `OverlayNavHost` achieves this without `zIndex` hacks.

**Lifecycle collection**: `NotificationBannerHost` uses `collectAsStateWithLifecycle()` for toast consumption — unlike Layer 0 widgets which use `collectAsState()`. This prevents toasts from being consumed and auto-dismissed while the app is backgrounded (e.g., user left to system settings). Toasts remain in the `Channel` until the Activity resumes. Banners use `collectAsState()` because they're state-derived and re-derive on resume regardless.

### Overlay-Local Feedback vs App-Level Notifications

Setup-flow confirmations ("Permission granted", "Bluetooth enabled", "Sensor calibrated") are **not** app-level notifications. They are contextual UI feedback within a specific overlay and use `SnackbarHostState` in the overlay's own `Scaffold`:

```kotlin
// Inside ProviderSetup overlay — local feedback, not NotificationCoordinator
val snackbarHostState = remember { SnackbarHostState() }
Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { ... }
```

The distinction: if the **user caused** the event (toggled a permission, completed calibration), feedback belongs to the screen that caused it. If the **system caused** the event (crash recovery, storage pressure, entitlement revocation), it goes through `NotificationCoordinator`.

### AlertEmitter & AlertSoundManager

`AlertEmitter` is the SDK contract for triggering audio/haptic alerts. `AlertSoundManager` is the shell implementation.

```kotlin
// :sdk:contracts — interface visible to packs
interface AlertEmitter {
    suspend fun fire(profile: AlertProfile): AlertResult
}

enum class AlertResult {
    PLAYED,         // sound/vibration delivered successfully
    SILENCED,       // user's global alert mode overrode to SILENT
    FOCUS_DENIED,   // audio focus unavailable (phone call, navigation)
    UNAVAILABLE,    // hardware unavailable (no vibrator, TTS init failed)
}
```

Implementation is `@Singleton` in `:app`, bound via Hilt. Packs trigger alerts through `WidgetCoroutineScope` which provides `AlertEmitter` — no new `CompositionLocal` needed, uses the existing supervised scope.

```kotlin
// :app — @Singleton implementation
class AlertSoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferencesRepository,
) : AlertEmitter {
    private val tts: Lazy<TextToSpeech> = lazy { /* 500ms warmup */ }

    override suspend fun fire(profile: AlertProfile): AlertResult {
        val userMode = userPreferences.getAlertMode()
        val effectiveMode = if (userMode == AlertMode.SILENT) AlertMode.SILENT else profile.mode

        return when (effectiveMode) {
            AlertMode.SILENT -> AlertResult.SILENCED
            AlertMode.VIBRATE -> vibrate(profile.vibrationPattern) // returns PLAYED or UNAVAILABLE
            AlertMode.SOUND -> {
                val focusResult = requestAudioFocus()
                if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return AlertResult.FOCUS_DENIED
                playSound(profile.soundUri)
                profile.ttsMessage?.let { ttsReadout(it) }
                AlertResult.PLAYED
            }
        }
    }
}
```

**Audio focus**: Respects `AUDIOFOCUS_LOSS_TRANSIENT` (phone calls, navigation prompts). Alert playback pauses during transient focus loss and does not resume — the moment has passed.

**Why `@Singleton`, not `@ViewModelScoped`**: Alert sounds must survive ViewModel recreation (configuration change mid-alert). `AlertSoundManager` holds system resources (`SoundPool`, `AudioManager`, `Vibrator`) that should have application lifecycle.

**Why not a `:core:alert` module**: At V1, no consumer beyond `:feature:dashboard` and `:app` needs the implementation. The interface in `:sdk:contracts` and the impl in `:app` follow the established pattern (cf. `CrashReporter` in `:sdk:observability`, `FirebaseCrashReporter` in `:core:firebase`). Extract to `:core:alert` only when a second consumer module appears.

### System Notifications

Two channels at V1. System notifications serve backgrounded/lock-screen scenarios — the in-app banner system is the primary user-facing surface.

| Channel ID | Importance | Purpose | Behavior |
|---|---|---|---|
| `dqxn_service` | `IMPORTANCE_LOW` | FGS telemetry notification | Persistent, silent, updates in-place |
| `dqxn_connection` | `IMPORTANCE_LOW` | Device connect/disconnect | Silent, no badge, dismissed on disconnect |

#### Foreground Service Notification

Required for persistent BLE connection and sensor polling. Updates in-place (same notification ID) to reflect current state:

```kotlin
// "Connected: OBD + GPS" → "Reconnecting: OBD" → "2 providers active"
startForeground(
    NOTIFICATION_ID,
    notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
)
```

- `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` — no 10s display delay
- `setSilent(true)` — no sound on update
- Content intent navigates back to dashboard
- API 34+ foreground service types: `connectedDevice` (BLE/OBD), `location` (GPS speed)

#### Connection Notification

Per F9.1: shows on device connect ("Connected to {deviceName}"), dismissed on disconnect. Low importance, silent, no badge. CDM handles background wakeups via `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`.

#### User Channel Overrides

If the user sets a channel to `IMPORTANCE_NONE` (blocked) in system settings, the notification is suppressed. This is respected — the FGS continues running regardless. On some OEM skins, blocked FGS notifications may trigger aggressive task killers; monitor via Crashlytics but don't design around it at V1.

#### POST_NOTIFICATIONS Permission (API 33+)

`SystemNotificationBridge` checks `POST_NOTIFICATIONS` grant status on initialization and observes changes via `ActivityResult` callback. When denied:

- **FGS notification**: Exempt from permission — always displayed regardless of grant status.
- **`dqxn_connection` channel**: Silently dropped by the OS. `SystemNotificationBridge` tracks denial state and skips building connection notifications entirely (avoids wasting cycles constructing `Notification` objects the OS will discard).
- **In-app banners/toasts**: Unaffected — `NotificationCoordinator` does not use system notifications.
- **Observability**: Permission denial state is set as a crash context key (`notification_permission = "denied"`) for correlation in crash reports.

### V2 Notification Roadmap

Deferred to post-launch, contingent on validated need:

| Feature | Trigger | Notes |
|---|---|---|
| Pack notification emission | Real pack that can't model notification as state change | `NotificationEmitter` contract in `:sdk:contracts`, rate limiting (10/min, 60/hr per pack), priority cap (no CRITICAL), deferred queue with 60s TTL |
| Critical system notification channel | Validated full-screen intent scenario | Safe mode / crash recovery when app fails to restart |
| Notification history | Analytics showing user demand | Persistence via DataStore, dedicated overlay route |
| `:core:alert` module | Second consumer beyond `:feature:dashboard` | Extract `AlertSoundManager` impl from `:app` |
| Thermal user notification | Support tickets correlating "app looks different" with thermal events | One-time NORMAL toast on rendering degradation |

## Security Requirements

| Requirement | Approach |
|---|---|
| No hardcoded secrets | SDK keys via `local.properties` / secrets gradle plugin |
| Agentic ContentProvider | Debug builds only (`src/debug/AndroidManifest.xml`) |
| Demo providers | Gated to debug builds |
| BT scan permission | `neverForLocation="true"` |
| Deep links | Digital Asset Links verification, parameter validation |
| R8 rules | Per-module `consumer-proguard-rules.pro`, release smoke test in CI |
| No NDK | No first-party native code. If silent process deaths appear post-launch, add `firebase-crashlytics-ndk` then. |

## Permissions

| Permission | Purpose |
|---|---|
| `BLUETOOTH_CONNECT` | Device connection (API 31+) |
| `BLUETOOTH_SCAN` (neverForLocation) | Device scanning (API 31+) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Billing/entitlement validation, weather data |
| `ACCESS_FINE_LOCATION` | GPS speed, altitude, trip accumulation |
| `ACCESS_COARSE_LOCATION` | Solar GPS (passive priority — zero additional battery) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Device connection service |
| `POST_NOTIFICATIONS` | Connection status notification |
| `VIBRATE` | Alert vibration, haptic feedback |
| `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE` | CDM auto-wake |

**Required hardware**: `android.software.companion_device_setup` (CDM support mandatory).

The app is fully functional offline — all persistence is local, all sensor data is device-native. Internet is required only for entitlement purchase/restore and optional weather data.
