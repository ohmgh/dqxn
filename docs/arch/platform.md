# Platform Integration

> Navigation, driving mode, alerts, security requirements, and permissions.

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

## Driving Mode

`:core:driving` provides motion detection, safety gating, and driving state as a data source. It implements `DataProvider` and emits `DrivingSnapshot` — the sole exception to the rule that providers come from packs. This exception exists because driving state is a platform-level safety concern, not a pack feature.

```kotlin
class DrivingModeProvider : DataProvider {
    override val typeId = "core:driving"
    override val snapshotType = DrivingSnapshot::class

    // Emits DrivingSnapshot with isDriving, speed, duration
    // Speed > 0 for 3s -> driving
    // Speed = 0 for 5s -> parked
}
```

The shell permanently subscribes to `DrivingSnapshot` for safety gating — this subscription is never unbound. Widgets may optionally subscribe to `DrivingSnapshot` for display purposes (e.g., showing driving duration) via standard data binding, the same as any other snapshot type.

When `isDriving == true`:
- Edit mode disabled (button hidden, long-press suppressed)
- Widget picker, settings, Theme Studio inaccessible
- Interactive widgets (Shortcuts tap, Media Controller) remain functional
- All touch targets enforce 76dp minimum

## Alerts & Notifications

### Connection Notification

- Channel: `device_connection`, low importance, silent, no badge
- Shows on device connect ("Connected to {deviceName}"), dismissed on disconnect
- CDM handles background wakeups

### Alert Sound Manager

Configurable per-alert: SILENT / VIBRATE / SOUND mode. Custom sound URIs supported. TTS readout via Android `TextToSpeech` (500ms delay). Vibration patterns per alert type.

## Security Requirements

| Requirement | Approach |
|---|---|
| No hardcoded secrets | SDK keys via `local.properties` / secrets gradle plugin |
| Agentic receiver | Debug builds only |
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
