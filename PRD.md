# DQXN Product Requirements Document

> Product definition for the DQXN Android dashboard platform.

## 1. Product Vision

DQXN is a modular automotive dashboard platform for Android. A phone or tablet mounted in a vehicle displays real-time telemetry through a fully configurable widget grid. The platform uses a pack-based architecture where feature packs (widgets, themes, data providers) are decoupled from the dashboard shell, enabling regional feature sets, premium gating, and first-party modular extensibility.

**Tagline**: "Life is a dash. Make it beautiful."

**Target users**: Drivers who want a customizable, visually rich heads-up display — starting with the Singapore market and expanding globally.

**Reference devices**: Snapdragon 6-series or equivalent (e.g., Pixel 7a, Samsung Galaxy A54), 6GB RAM, 1080p display. Performance targets validated against this baseline.

## 2. Core Concepts

### Dashboard Canvas

A 2D grid (unit = 16dp) where widgets are placed at absolute positions. The viewport is computed from screen dimensions. Widgets outside the viewport are culled from rendering. The canvas supports a single orientation (landscape or portrait, locked per device profile) — see F1.15.

### Widgets

Self-contained visual components that render data. Each widget:
- Has a `typeId` scoped to its pack (e.g., `core:speedometer`)
- Declares compatible data types it can consume
- Provides a declarative settings schema for its configuration UI
- Knows nothing about where its data comes from — the binder assigns providers
- Runs in an error-isolated container — a widget crash shows a fallback, not an app crash

### Data Providers

Reactive data sources that emit `Flow<DataSnapshot>`. Each provider:
- Serves one or more data types (SPEED, TIME, SOLAR, AMBIENT_LIGHT, ORIENTATION, BATTERY, MEDIA_SESSION, TRIP, etc.)
- Declares a setup schema (permissions, system services, device pairing)
- Reports connection state and errors
- Can be swapped per-widget without widget code changes

### Packs

Feature modules that bundle widgets, providers, and themes. Discovery is via Hilt multibinding sets — the shell never imports pack code directly. Regional packs (e.g., Singapore ERP) plug in as additional modules without shell changes. All packs are first-party compiled modules; there is no runtime plugin loading.

### Entitlements

Access control layer for premium content. OR-logic gating: any matching entitlement grants access. Applied to widgets, providers, themes, individual settings, and auto-switch modes.

## 3. Feature Requirements

### F1: Dashboard Shell

The always-present canvas that hosts widgets and controls.

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F1.1  | Full-screen edge-to-edge rendering, no system chrome by default                               | Must     |
| F1.2  | Toggleable status bar overlay (immersive/transient modes)                                     | Must     |
| F1.3  | 16dp grid unit system with dynamic viewport calculation                                       | Must     |
| F1.4  | Widget rendering with viewport culling (skip off-screen widgets)                              | Must     |
| F1.5  | Edit mode via Edit button (primary) or long-press on blank space (parked only)                | Must     |
| F1.6  | Drag-to-move widgets in edit mode                                                             | Must     |
| F1.7  | 4-corner resize handles in edit mode (minimum 76dp touch targets)                             | Must     |
| F1.8  | Widget focus animation (translate to center, scale up)                                        | Must     |
| F1.9  | Auto-hide floating button bar (3s timeout, minimum 76dp touch targets)                        | Must     |
| F1.10 | Z-index stacking for overlapping widgets                                                      | Must     |
| F1.11 | Edit mode visual feedback (wiggle animation, corner brackets)                                 | Must     |
| F1.12 | Widget limit enforcement: Free: 6 widgets. Plus: 12 (phone) / 20 (tablet)                    | Must     |
| F1.13 | Dashboard-as-shell pattern (canvas persists beneath all overlays)                             | Must     |
| F1.14 | Pause state collection for CPU-heavy overlay routes (settings, widget picker)                  | Must     |
| F1.15 | Orientation lock (landscape default, configurable in settings)                                | Must     |
| F1.16 | `FLAG_KEEP_SCREEN_ON` while dashboard is active, user-configurable                            | Must     |
| F1.17 | Haptic feedback on edit mode entry/exit, widget focus, resize snap, button press              | Must     |
| F1.18 | Pixel-shift for OLED burn-in prevention (1-2px translation every 5 minutes)                   | Should   |
| F1.19 | HUD mirror mode (horizontal canvas flip for windshield projection)                            | Should   |
| F1.20 | Grid snapping: widget snaps to nearest 2-unit grid boundary on drop. Visual grid overlay during drag. Haptic tick on snap | Must     |
| F1.21 | Widget add/remove animations: fade + scale-in on add (spring animation via `graphicsLayer`), fade + scale-out on delete | Should   |
| F1.22 | Auto-arrange: button in edit mode that repositions all widgets via `GridPlacementEngine` into a clean non-overlapping layout | Should   |
| F1.23 | Multi-window disabled: `resizeableActivity="false"`. Dashboard is fullscreen-only             | Must     |

### F2: Widget System

The plugin API, container, and lifecycle.

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F2.1  | `WidgetRenderer` contract with `@Composable Render()`                                         | Must     |
| F2.2  | `WidgetSpec` with typeId, display name, compatible data types, settings schema                | Must     |
| F2.3  | `WidgetContainer` with glow, rim, border, opacity, corner radius, background style            | Must     |
| F2.4  | `WidgetDataBinder` — IoC binding of providers to widgets by data type                         | Must     |
| F2.5  | `WidgetStatusCache` — overlays for entitlement, setup, connection issues                      | Must     |
| F2.6  | `GridPlacementEngine` — optimal placement minimizing overlap, preferring center               | Must     |
| F2.7  | Widget picker with preview images (static rendered snapshots, not live composables)           | Must     |
| F2.8  | Per-widget settings sheet (3 pages: Feature / Data Source / Info)                              | Must     |
| F2.9  | Schema-driven settings UI (toggles, button groups, dropdowns, hub routes)                     | Must     |
| F2.10 | Context-aware defaults via `WidgetContext` (timezone, region)                                  | Should   |
| F2.11 | `SettingsAwareSizer` — dynamic default size based on enabled features                         | Should   |
| F2.12 | `@DashboardWidget` KSP annotation → auto-registration                                        | Must     |
| F2.13 | `UnknownWidgetPlaceholder` for deregistered widget types                                      | Must     |
| F2.14 | Widget error boundary — render failure shows fallback UI, not app crash                       | Must     |
| F2.15 | Widget picker with real live previews (scaled composables)                                     | Should   |
| F2.16 | Aspect ratio constraint: widgets declare optional `aspectRatio: Float?` in `WidgetSpec` (null = freeform). Resize handles enforce ratio when set. Circular widgets (speedometer, compass, analog clock, G-force) declare 1:1 | Must     |
| F2.17 | Widget duplicate: duplicate focused widget with all settings copied. New instance placed at next optimal position | Should   |
| F2.18 | Focus interaction model: focused widget shows overlay toolbar (delete, settings, style, duplicate). Tapping widget content area unfocuses. Interactive widget actions (play/pause, app launch) only work in non-focused, non-edit mode | Must     |
| F2.19 | Widget accessibility: each widget provides `accessibilityDescription(data)` for screen readers. Read-only: announces current value (e.g., "Speed: 65 km/h") | Must     |

### F3: Data Provider System

Reactive data sources with declarative setup.

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F3.1  | `DataProvider` contract with `provideState(): Flow<DataSnapshot>`                             | Must     |
| F3.2  | `DataSchema` describing output shape and data types                                           | Must     |
| F3.3  | `SetupPageDefinition` — declarative multi-page setup wizard                                   | Must     |
| F3.4  | Setup definition types: RuntimePermission, SystemServiceToggle, DeviceScan, Instruction       | Must     |
| F3.5  | `SetupEvaluator` — checks provider readiness against setup requirements                       | Must     |
| F3.6  | Connection state and error reporting (`isAvailable`, `connectionState`, `connectionErrorDescription`) | Must     |
| F3.7  | `DataProviderRegistry` with entitlement-filtered and unfiltered views                         | Must     |
| F3.8  | `@DashboardDataProvider` KSP annotation → auto-registration                                  | Must     |
| F3.9  | `WhileSubscribed` sharing — providers stop after last subscriber (configurable per-provider: 1s for clock, 5s default, 30s for GPS-heavy) | Must     |
| F3.10 | Provider fallback: when assigned provider becomes unavailable, `WidgetDataBinder` falls back to next available provider for same data type. Priority: user-selected > hardware > device sensor > network. User notified via transient indicator | Must     |
| F3.11 | Data staleness: staleness thresholds per data type — SPEED (3s), ORIENTATION (5s), TIME (2s), BATTERY (30s), WEATHER (30min). Widget shows stale indicator when exceeded. Provider reports expected emission interval in `DataSchema` | Must     |
| F3.12 | Weather polling: weather data refreshed every 30 minutes when widget visible. Last-known data displayed with age timestamp when offline. Respect HTTP cache headers | Must     |
| F3.13 | Provider health dashboard: Settings → Provider Health showing all active providers, connection state, last update timestamp, error descriptions, retry actions | Must     |
| F3.14 | Provider setup failure UX: failed setup shows inline error with retry. Dismissed wizard → widget shows "Setup Required" overlay with tap-to-setup. Permanently denied permissions → direct to system settings | Must     |
| F3.15 | Progressive error disclosure: driving mode shows status icon + color only. Parked mode shows icon + brief message + tap to open diagnostics | Must     |

### F4: Theme System

Visual customization with auto-switching.

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F4.1  | `DashboardThemeDefinition` — colors + gradient brushes                                        | Must     |
| F4.2  | JSON-driven theme loading from bundled assets                                                 | Must     |
| F4.3  | Dual-theme model (separate light/dark selections)                                             | Must     |
| F4.4  | 5 auto-switch modes: LIGHT, DARK, SYSTEM, SOLAR_AUTO, ILLUMINANCE_AUTO                       | Must     |
| F4.5  | `ThemeAutoSwitchEngine` with eager sharing (ready at cold start)                              | Must     |
| F4.6  | Theme preview (live preview before committing, reverts on cancel)                             | Must     |
| F4.7  | Theme Studio — create/edit custom themes (max 12)                                             | Should   |
| F4.8  | Gradient editor (vertical, horizontal, linear, radial, sweep; 2-5 stops)                      | Should   |
| F4.9  | Preview-regardless-of-entitlement, gate-at-persistence                                        | Must     |
| F4.10 | Reactive entitlement revocation (auto-revert to free defaults)                                | Must     |
| F4.11 | Spacing tokens and typography scale in theme definition                                       | Should   |
| F4.12 | Clone built-in → custom via long-press                                                        | Should   |

### F5: Essentials Pack (Free)

Core widgets available to all users.

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F5.1  | **Speedometer** — circular arc gauge, 12-segment acceleration arc, auto-scaling max, speed limit warning (amber/red), configurable alert types | Must     |
| F5.2  | **Clock (Digital)** — large HH:mm, optional seconds, AM/PM, timezone label, configurable timezone | Must     |
| F5.3  | **Clock (Analog)** — Canvas circular clock with hour/minute/second hands, tick marks          | Should   |
| F5.4  | **Date** — 3 variants (Simple, Stack, Grid), configurable format and timezone                 | Must     |
| F5.5  | **Compass** — cardinal direction, tick marks, tilt indicators (pitch/roll)                    | Must     |
| F5.6  | **Battery** — battery level percentage, charging state, optional temperature                  | Must     |
| F5.7  | **Speed Limit (Circle)** — European-style circular sign, region-aware (auto KPH/MPH, Japan blue digits) | Must     |
| F5.8  | **Shortcuts** — tappable widget launching a chosen app, with suggested apps                   | Should   |
| F5.9  | **Solar** — sunrise/sunset times or next event countdown, optional 24h circular arc with sun/moon marker | Should   |
| F5.10 | **Ambient Light** — lux level, category (DARK/DIM/NORMAL/BRIGHT), InfoCard layout             | Should   |
| F5.11 | **Speed Limit (Rectangle)** — US-style rectangular sign                                       | Should   |

### F5B: Plus Pack (Premium)

Extended functional widgets for power users.

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F5B.1 | **Trip Computer** — current/average/max speed, trip distance (odometer), trip duration, resettable per-trip | Must     |
| F5B.2 | **Media Controller** — now-playing track/artist/album art, prev/play-pause/next controls via Android `MediaSession` | Must     |
| F5B.3 | **G-Force** — lateral/longitudinal acceleration display (circular or vector), peak hold        | Should   |
| F5B.4 | **Altimeter** — GPS altitude with optional barometric correction                              | Should   |
| F5B.5 | **Weather** — current temperature, condition icon, high/low (via OpenMeteo API, graceful offline fallback to last-known) | Should   |

### F6: Themes Pack (Premium)

Extended visual customization.

| Req  | Description                                                                                    | Priority |
|------|------------------------------------------------------------------------------------------------|----------|
| F6.1 | 22 premium themes (Cyberpunk, Aurora, Tron, Void, Carbon, Ocean Breeze, etc.)                  | Must     |
| F6.2 | Theme Studio access (create/edit custom themes)                                                | Must     |
| F6.3 | SOLAR_AUTO mode (sunrise/sunset theme switching)                                               | Must     |
| F6.4 | ILLUMINANCE_AUTO mode (ambient light sensor threshold)                                         | Must     |

### F7: Persistence & Data

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F7.1  | Proto DataStore-based persistence for structured data (layouts, paired devices); Preferences DataStore for simple key-value settings | Must     |
| F7.2  | Versioned layout schema with explicit migration transformers (N → N+1)                        | Must     |
| F7.3  | Debounced layout save (500ms) with atomic writes (write to temp, swap on success)             | Must     |
| F7.4  | Type-safe provider settings store with pack-namespaced keys                                   | Must     |
| F7.5  | Paired device persistence (survives restarts)                                                 | Must     |
| F7.6  | Connection event log (rolling 50 events)                                                      | Should   |
| F7.7  | Preset system — JSON presets, region-aware defaults                                           | Must     |
| F7.8  | Layout corruption detection with fallback to last-known-good or default preset                | Must     |
| F7.9  | Edit-mode cancel restores pre-edit layout state                                               | Must     |
| F7.10 | Android Auto Backup support (include DataStore files, handle missing packs on restore)         | Should   |
| F7.11 | Preset import/export via share intent (user-facing)                                           | Should   |

### F8: Entitlements & Monetization

| Req  | Description                                                                                    | Priority |
|------|------------------------------------------------------------------------------------------------|----------|
| F8.1 | Entitlement system: `free`, `plus`, `themes` tiers (extensible for regional packs)             | Must     |
| F8.2 | Play Billing integration (purchase, restore)                                                   | Must     |
| F8.3 | `Gated` interface on renderers, providers, themes, settings                                    | Must     |
| F8.4 | Upsell overlays on gated widgets with frequency caps                                           | Must     |
| F8.5 | Debug "Simulate Free User" toggle                                                              | Should   |
| F8.6 | Contextual upsell triggers: theme preview → purchase, widget limit → upgrade, plus widget attempt → purchase | Must     |
| F8.7 | Trial for Plus widgets: Plus widgets can be added and used for current session (not persisted). On restart, show upsell overlay | Should   |
| F8.8 | Family sharing: enable Google Play Family Library for one-time IAP packs (Play Console toggle) | Should   |
| F8.9 | Refund UX: on entitlement revocation, widgets show EntitlementRevoked overlay, remain in layout but non-functional. Premium themes revert to free. One-time toast explains change | Must     |

**Monetization Model**: One-time IAP per pack (aligned with market expectations of $3–$10 range). No subscriptions for v1. Regional pricing for Singapore (SGD). Bundle discount for themes + plus together.

| Entitlement | Scope                                                                                                      | Price Range    |
|-------------|------------------------------------------------------------------------------------------------------------|----------------|
| `free`      | Core widgets (speedometer, clock, date, compass, battery, speed limit, shortcuts) + 2 themes + full customization | Free           |
| `plus`      | Trip computer, media controller, G-force, altimeter, weather widgets + widget limit increase (6 free → 12/20) | $4.99–$6.99   |
| `themes`    | 22 premium themes + Theme Studio + Solar/Illuminance auto-switch                                           | $3.99–$5.99   |

### F9: Notifications & Alerts

| Req  | Description                                                  | Priority |
|------|--------------------------------------------------------------|----------|
| F9.1 | Silent connection status notification (connect/disconnect)   | Must     |
| F9.2 | Per-alert mode selection (SILENT/VIBRATE/SOUND)              | Must     |
| F9.3 | TTS readout for alerts                                       | Should   |
| F9.4 | Custom alert sound URIs                                      | Should   |

### F10: Driving Safety

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F10.1 | Motion detection via GPS speed or paired device speed data                                    | Must     |
| F10.2 | While in motion: disable edit mode, widget picker, settings, Theme Studio                     | Must     |
| F10.3 | While in motion: allow only tap interactions on interactive widgets (Shortcuts, Media Controller) | Must     |
| F10.4 | Minimum touch target size: 76dp for all interactive elements (per Android Automotive Design Guidelines) | Must     |
| F10.5 | Speed limit alert with configurable offset (amber at +5, red at +10, user-configurable)       | Must     |
| F10.6 | Parked-mode detection: speed = 0 for >5s unlocks full interaction                             | Must     |
| F10.7 | Adaptive rendering: reduce frame rate to 30fps when stationary, disable glow effects under thermal pressure | Should   |
| F10.8 | Parking location save — GPS fix on app exit, deep-link to Maps for navigation back            | Should   |
| F10.9 | Quick theme toggle: theme mode cycle button on floating bar (accessible while driving — single tap, no distraction). Cycles: current light → current dark → auto | Must     |

### F11: Onboarding

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F11.1 | First-run coach-mark overlay: "Tap the Edit button to customize, tap + to add widgets"        | Must     |
| F11.2 | Theme selection prompt on first launch (upsell moment for premium themes)                     | Must     |
| F11.3 | BLE device pairing prompt if BLE-dependent widgets are in default layout                      | Should   |
| F11.4 | Recommended layout guidance: 3-5 widgets for phone (within free tier limit of 6), 6-10 for tablet | Should   |

### F12: Analytics & Crash Reporting

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F12.1 | Crash reporting integration (Firebase Crashlytics or equivalent)                              | Must     |
| F12.2 | Key funnel events: install, first edit, widget add, theme change, upsell impression, purchase start/complete | Must     |
| F12.3 | Engagement metrics: session duration, widgets per layout, edit frequency                      | Should   |
| F12.4 | Privacy-compliant (Singapore PDPA, no PII in analytics)                                       | Must     |
| F12.5 | Opt-out toggle in settings                                                                    | Must     |
| F12.6 | Upsell funnel parameters: upsell events include trigger source (`theme_preview`, `widget_limit`, `widget_picker`, `settings`) as event parameter | Must     |
| F12.7 | Session quality metrics: session end events include jank%, peak thermal level, widget render failures, provider errors | Should   |

### F13: Debug & Development

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F13.1 | Demo pack with simulated providers for all data types                                         | Must     |
| F13.2 | Agentic framework for ADB-driven automation                                                   | Should   |
| F13.3 | Capture session recording (tap, move, resize, navigation events)                              | Should   |
| F13.4 | Demo mode flag (debug builds only)                                                            | Must     |
| F13.5 | Structured state dumps: ADB-queryable JSON state dumps — dashboard state, provider health, metrics snapshot, trace history, log buffer. Debug builds only | Must     |
| F13.6 | Debug overlays: toggleable overlays for frame stats, recomposition visualization, provider data flow DAG, state machine viewer, thermal trending, widget health. Debug builds only | Should   |
| F13.7 | Machine-readable logs: JSON-lines file log sink (rotated 10MB, max 3 files) for agent-parseable diagnostics. Debug builds only | Should   |

### F14: Settings

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F14.1 | Orientation lock expanded: Landscape / Reverse Landscape / Portrait / Reverse Portrait        | Must     |
| F14.2 | Diagnostics: navigation to Provider Health dashboard                                          | Must     |
| F14.3 | Report a Problem: opens email intent with pre-filled device info, app version, connection event log | Should   |
| F14.4 | Delete All Data: clear all DataStores, revoke analytics ID, reset to factory state (GDPR compliance) | Must     |

## 4. Non-Functional Requirements

### Performance

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF1  | Dashboard renders at 60fps with 12 active widgets on reference hardware (Snapdragon 6-series, 6GB RAM) |
| NF2  | Cold start to first meaningful paint (widgets visible with placeholder data) < 1.5s            |
| NF3  | Cold start to live data < 3s                                                                   |
| NF4  | Widget data binding starts within 100ms of widget add                                          |
| NF5  | Provider shutdown within configured `WhileSubscribed` timeout of last subscriber detaching     |
| NF6  | Layout save debounce at 500ms to prevent write storms                                          |
| NF7  | Viewport culling — zero render cost for off-screen widgets                                     |
| NF8  | Heap usage < 200MB with 12 active widgets                                                      |
| NF9  | Baseline Profiles generated for critical paths (dashboard load, widget picker, edit mode)       |
| NF10 | Macrobenchmarks in CI gating: P99 frame duration < 16ms, startup < 1.5s                        |

### Battery & Thermal

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF11 | Battery drain < 5% per hour of active dashboard use (screen-on, 12 widgets, BLE connected)     |
| NF12 | Thermal headroom monitoring via `PowerManager.getThermalHeadroom()` with proactive degradation  |
| NF13 | Thermal degradation tiers: Normal (60fps, full effects) → Warm (45fps) → Degraded (30fps, no glow) → Critical (24fps, reduced widgets) |
| NF14 | Sensor batching for non-critical sensors (compass, ambient light) to reduce SoC wakeups        |
| NF37 | Background battery: < 1% per hour with BLE service active, < 0.1% with no connections. GPS uses passive provider when backgrounded. Driving detection pauses when not foreground |

### Reliability

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF15 | Device reconnection within 5s of reappearance (via CDM)                                        |
| NF16 | Exponential backoff on connection failures (3 attempts max)                                    |
| NF17 | Graceful degradation — widgets show placeholder states when providers unavailable               |
| NF18 | Entitlement revocation handled reactively (no stale premium state)                             |
| NF19 | Widget error isolation — individual widget render failure does not crash the app                |
| NF36 | Crash-free rate: target 99.5% users / 99.9% sessions. Release blocked if 7-day rate drops below 99% |
| NF38 | Crash recovery: >3 crashes in 60s → safe mode (clock widget only, reset banner)                |

### Security

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF20 | No hardcoded secrets in source (SDK keys via local.properties or secrets plugin)                |
| NF21 | Agentic receiver restricted to debug builds                                                    |
| NF22 | Demo providers gated to debug builds only                                                      |
| NF23 | `neverForLocation="true"` on BT scan permission                                               |

### Privacy & Compliance

| Req   | Description                                                                                   |
|-------|-----------------------------------------------------------------------------------------------|
| NF-P1 | Location retention: no GPS track stored. Trip accumulator stores only derived distance/duration. Parking location: single coord, overwritten on exit, user-deletable. No location leaves device except coarse location for weather API |
| NF-P2 | Trip data privacy: trip computer stores current aggregates only (distance, duration, avg speed, max speed). No per-second speed log. Resets on manual reset. No historical records |
| NF-P3 | GDPR compliance: privacy policy URL in Play listing. Analytics opt-out (F12.5). "Delete All Data" in settings (F14.4). No user accounts = simplified compliance. Applies alongside Singapore PDPA |
| NF-P4 | Data export: on-device data is user-accessible. For Firebase analytics data, deletion via Firebase data deletion API noted in privacy policy |

### Offline

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF24 | App is fully functional offline (all persistence local, all sensor data local)                  |
| NF25 | Hardware device data via Bluetooth — no internet dependency                                    |
| NF26 | Internet required only for entitlement purchase/restore and weather data (with offline fallback) |

### Compatibility

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF27 | minSdk 31 (Android 12) — required for CDM, BT permission model, RenderEffect                  |
| NF28 | targetSdk 36 with API 36 CDM event handling                                                    |
| NF29 | Required hardware: `companion_device_setup`                                                    |

### Accessibility

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF30 | WCAG 2.1 AA contrast ratios for critical text (speed, time, speed limit)                       |
| NF31 | Minimum text size for driving-distance readability (speed: 48sp+, secondary info: 24sp+)       |
| NF32 | TalkBack support for setup/settings flows (not dashboard rendering)                            |
| NF33 | System font scale respected in settings UI (dashboard widgets use fixed sizes for layout stability) |
| NF39 | Reduced motion: when system `animator_duration_scale` = 0, disable wiggle, replace spring with instant transitions, disable pixel-shift. Glow remains |
| NF40 | Color-blind safety: speed limit warnings use color + pattern/icon (pulsing border + warning icon), not color alone. Free themes verified for deuteranopia contrast |

### Build & APK

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF34 | APK size < 30MB base, < 50MB with all packs                                                   |
| NF35 | Incremental build time < 15s, clean build < 120s                                              |

### App Lifecycle

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF-L1 | Phone calls: dashboard pauses state collection, resumes on return. BLE connection maintained via foreground service. `FLAG_KEEP_SCREEN_ON` behavior standard (released when Activity stops) |
| NF-L2 | In-app updates: Google Play In-App Updates API — IMMEDIATE for critical bugs, FLEXIBLE for features |
| NF-L3 | In-app review: Google Play In-App Review API. Trigger after 3+ sessions, 1+ layout customization, no crash in current session. Never while driving. Frequency cap: once per 90 days |

### Localization

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF-I1 | v1 launches English-only. All user-facing strings in Android string resources (no hardcoded)   |
| NF-I2 | Widget data uses locale-aware formatting (decimal separators, unit labels)                     |
| NF-I3 | Target languages for v1.1: Simplified Chinese, Bahasa Melayu                                  |

## 5. Widgets Inventory

### Essentials Pack (`core`) — Free

| Widget              | Type ID                | Default Size | Data Types    | Key Settings                                                               |
|---------------------|------------------------|--------------|---------------|----------------------------------------------------------------------------|
| Speedometer         | `core:speedometer`     | 12x12        | SPEED, ACCELERATION, SPEED_LIMIT | arcs, tick marks, speed unit, limit offset, warning bg, alert type        |
| Clock (Digital)     | `core:clock`           | 10x9         | TIME          | seconds, 24h, leading zero, timezone, sizes                               |
| Clock (Analog)      | `core:clock-analog`    | 10x10        | TIME          | tick marks, timezone                                                       |
| Date (Simple)       | `core:date-simple`     | —            | TIME          | date format, timezone                                                      |
| Date (Stack)        | `core:date-stack`      | —            | TIME          | date format, timezone                                                      |
| Date (Grid)         | `core:date-grid`       | 10x6         | TIME          | timezone                                                                   |
| Compass             | `core:compass`         | 10x10        | ORIENTATION   | tick marks, cardinal labels, tilt indicators                               |
| Battery             | `core:battery`         | 6x6          | BATTERY       | show percentage, show temperature, charging indicator                      |
| Speed Limit (Circle)| `core:speedlimit-circle`| —           | SPEED_LIMIT   | border size, speed unit, digit color                                       |
| Speed Limit (Rect)  | `core:speedlimit-rect` | —            | SPEED_LIMIT   | border size, speed unit                                                    |
| Shortcuts           | `core:shortcuts`       | 9x9          | —             | app package, show label, InfoCard layout                                   |
| Solar               | `core:solar`           | —            | SOLAR         | arc, display mode, countdown                                               |
| Ambient Light       | `core:ambient-light`   | 8x8          | AMBIENT_LIGHT | InfoCard layout options                                                    |

### Plus Pack (`plus`) — Premium

| Widget           | Type ID           | Default Size | Data Types       | Key Settings                                          |
|------------------|-------------------|--------------|------------------|-------------------------------------------------------|
| Trip Computer    | `plus:trip`       | 12x8         | SPEED, TRIP      | display fields, reset button, distance unit           |
| Media Controller | `plus:media`      | 12x6         | MEDIA_SESSION    | show album art, controls size                         |
| G-Force          | `plus:gforce`     | 10x10        | ACCELERATION     | display mode (circle/vector), peak hold, sensitivity  |
| Altimeter        | `plus:altimeter`  | 8x6          | ALTITUDE         | unit (m/ft), barometric correction                    |
| Weather          | `plus:weather`    | 8x8          | WEATHER          | show forecast, temperature unit                       |

Regional packs contribute their own widgets to the grid — the shell renders them identically.

## 6. Data Provider Inventory

### Device Sensors

| Provider      | Data Types          | Source                              | Permissions          |
|---------------|---------------------|-------------------------------------|----------------------|
| Clock         | TIME                | System clock (1s tick)              | None                 |
| GPS Speed     | SPEED, ACCELERATION | Fused Location (high accuracy)      | ACCESS_FINE_LOCATION |
| GPS Altitude  | ALTITUDE            | Fused Location                      | ACCESS_FINE_LOCATION |
| Orientation   | ORIENTATION         | Rotation vector sensor              | None                 |
| Ambient Light | AMBIENT_LIGHT       | Light sensor (lux)                  | None                 |
| Battery       | BATTERY             | BatteryManager broadcast            | None                 |
| Accelerometer | ACCELERATION        | TYPE_ACCELEROMETER sensor           | None                 |

### Location-Based

| Provider           | Data Types | Source                                          | Permissions            |
|--------------------|------------|-------------------------------------------------|------------------------|
| Solar (Location)   | SOLAR      | GPS (passive priority) + astronomical calc      | ACCESS_COARSE_LOCATION |
| Solar (Timezone)   | SOLAR      | IANA timezone + astronomical calc               | None                   |

### System Services

| Provider          | Data Types    | Source                                                      | Permissions                                 |
|-------------------|---------------|-------------------------------------------------------------|---------------------------------------------|
| Media Session     | MEDIA_SESSION | `MediaSessionManager`                                       | MEDIA_CONTENT_CONTROL or NotificationListener |
| Trip Accumulator  | TRIP          | Derived from GPS Speed (distance integration, timing)       | ACCESS_FINE_LOCATION                        |

### Network (Optional, Offline Fallback)

| Provider | Data Types | Source                                 | Permissions |
|----------|------------|----------------------------------------|-------------|
| Weather  | WEATHER    | OpenMeteo API (free, no key required)  | INTERNET    |

Regional packs contribute additional providers for hardware-specific data (BLE devices, vehicle buses, OBD-II adapters, etc.).

## 7. Theme Inventory

### Free

| Theme      | Mode  |
|------------|-------|
| Slate      | Dark  |
| Minimalist | Light |

### Premium (Themes Pack)

Cyberpunk, Aurora, Tron ("The Grid"), Void, Carbon, Ocean Breeze, Forest, Ember, Sunset Glow, Arctic, Sand, Rose, Expressive, Cloud, Sage, Lavender, Mint, Mocha, Neon Pink, Sky, Peach, Midnight Purple.

Gradient types: vertical, horizontal, linear, radial, sweep. Each theme defines background and widget background gradients with 2–5 color stops.

### Custom (User-Created)

Max 12. Created via Theme Studio (requires Themes Pack entitlement). Editable: name, 5 colors, 2 gradients (type + stops). Auto-saved on change.

## 8. User Flows

### First Launch

1. App starts → `LayoutRepository` is empty → `loadOrInitializeWidgets()` runs
2. `PresetLoader` loads a regional preset (if matched by `RegionDetector`)
3. Default layout renders with initial widget set
4. Coach-mark overlay displays: "Tap the Edit button to customize, tap + to add widgets" (dismissible, shown once)
5. Theme selection prompt (showcases premium themes as upsell)
6. Dashboard is immediately usable

### Adding a Widget

1. User taps Edit → edit mode → taps "+" button (disabled while in motion)
2. Widget picker opens: staggered grid grouped by pack, preview images (with entitlement badges)
3. User taps a widget → `GridPlacementEngine.findOptimalPosition()` places it
4. Default data sources auto-assigned
5. Widget settings sheet auto-opens

### Widget Settings

Three-page pager (only available when parked):
- **Feature page**: Schema-driven controls (toggles, button groups, dropdowns, hub route pickers)
- **Data Source page**: Pick which provider feeds this widget; shows available providers with status
- **Info page**: Widget type, pack, description, entitlement requirements; shared element transition to PackBrowser

### Theme Management

1. Settings → Theme Mode: 5 modes (LIGHT, DARK, SYSTEM, SOLAR_AUTO, ILLUMINANCE_AUTO)
2. Settings → Light/Dark Theme: horizontal pager (built-in page, custom page)
3. Tapping a theme previews it live (reverts on exit if not confirmed)
4. Long-press built-in → clone into Theme Studio
5. Long-press custom → open in Theme Studio for editing

### Edit Mode

1. Tap Edit button → enter edit mode (long-press only available when parked)
2. Widgets show wiggle animation + corner brackets
3. Tap widget → focus (translate to center, scale up, show overlay toolbar: delete/settings/style/duplicate)
4. Drag to move (snaps to 2-unit grid), corner handles to resize (76dp minimum touch targets)
5. Tapping widget content area unfocuses; tapping blank space or Edit button exits edit mode
6. Cancel discards all changes; confirm persists via debounced save

### Driving Mode

1. Speed > 0 for 3s → driving mode activates
2. Edit button, widget picker, settings, Theme Studio become inaccessible
3. Interactive widgets (Shortcuts, Media Controller) remain tappable with 76dp+ targets
4. Quick theme toggle on floating bar remains accessible (single tap cycle: light → dark → auto)
5. Speed = 0 for 5s → parked mode, full interaction restored
6. Speed alerts (speed limit warning) active with configured alert mode (silent/vibrate/sound)

## 9. Settings

### Main Settings Sheet

| Setting                | Type                                                                              | Description                                                      |
|------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------------|
| Dash Packs             | Navigation                                                                        | Opens Pack Browser                                               |
| Theme Mode             | Selection (5 modes)                                                               | Light / Dark / System / Solar Auto / Illuminance Auto            |
| Light Theme            | Navigation                                                                        | Theme selector for light mode                                    |
| Dark Theme             | Navigation                                                                        | Theme selector for dark mode                                     |
| Illuminance Threshold  | Segmented button group: Dark 50lux / Dim 200lux / Normal 500lux / Bright 1000lux  | Lux threshold for dark mode trigger (visible in ILLUMINANCE_AUTO mode) |
| Show Status Bar        | Toggle                                                                            | System status bar visibility                                     |
| Keep Screen On         | Toggle (default: on)                                                              | Prevents screen timeout while dashboard is active                |
| Orientation Lock       | Selection (Landscape / Reverse Landscape / Portrait / Reverse Portrait)           | Locks display orientation                                        |
| HUD Mirror Mode        | Toggle                                                                            | Horizontally flips canvas for windshield projection              |
| Diagnostics            | Navigation                                                                        | Opens Provider Health dashboard                                  |
| Analytics              | Toggle (default: on)                                                              | Opt-out of anonymous usage analytics                             |
| Report a Problem       | Action                                                                            | Opens email intent with pre-filled device info, app version, connection event log |
| Reset Dash             | Destructive action                                                                | Resets layout to default preset                                  |
| Delete All Data        | Destructive action                                                                | Clears all DataStores, revokes analytics ID, resets to factory state |

### Widget Style Properties (per-widget)

| Property             | Range     | Description                                            |
|----------------------|-----------|--------------------------------------------------------|
| backgroundStyle      | SOLID / TRANSPARENT | Widget background fill                       |
| opacity              | 0.0–1.0   | Widget transparency                                    |
| showBorder           | Boolean   | Theme-colored border                                   |
| hasGlowEffect        | Boolean   | Outer glow (disabled under thermal pressure)           |
| cornerRadiusPercent  | 0–100     | Rounded corners                                        |
| rimSizePercent       | 0–100     | Inner breathing room                                   |

## 10. Error & Empty State Catalog

| Screen                     | Empty/Error State              | Behavior                                                                                   |
|----------------------------|--------------------------------|--------------------------------------------------------------------------------------------|
| Dashboard (0 widgets)      | Empty dashboard                | Show persistent coach-mark + "Add your first widget" CTA                                   |
| Widget picker (at limit)   | Widget limit reached           | Show limit message + upgrade prompt (if free tier)                                         |
| Widget picker (all added)  | All widgets placed             | Show "All widgets placed" message                                                          |
| Theme selector (no customs)| No custom themes               | Show "Create your first theme" CTA                                                        |
| BLE scan (no devices)      | No devices found               | Show "No devices found" + troubleshooting tips                                             |
| Provider setup (denied)    | Permission denied              | Inline error + retry + system settings link                                                |
| Widget (provider unavail.) | Provider unavailable           | `WidgetStatusCache` overlay per priority                                                   |
| Widget (data stale)        | Stale data                     | Dimmed display + stale age badge                                                           |
| Dashboard (safe mode)      | Crash recovery                 | Clock only + "App recovered from crash" banner + reset/report options                      |

## 11. Out of Scope (v1)

These are acknowledged gaps deferred to future versions:

| Feature                          | Rationale                                                                                        |
|----------------------------------|--------------------------------------------------------------------------------------------------|
| Android Auto projection          | Different hardware model; requires Car App Library, incompatible with widget grid UX             |
| OBD-II / ELM327 pack            | High-value but high-complexity; planned as a future regional/premium pack                        |
| Speed camera alerts              | Requires online POI database, conflicts with offline-first model                                 |
| Voice commands                   | High ambient noise makes custom wake-words unreliable; revisit with Google Assistant integration  |
| Dashcam integration              | Different product category; battery/thermal/storage concerns                                     |
| Third-party runtime plugins      | Compile-time module system is sufficient; runtime loading adds security and stability risks       |
| Preset community sharing         | Requires backend infrastructure; defer to post-launch                                            |
| Track recording / GPX export     | Medium complexity; planned for plus pack v2                                                      |
| Picture-in-Picture mode          | Requires separate mini-dashboard render mode                                                     |
| Undo/redo in edit mode           | Cancel-all sufficient for v1 widget counts                                                       |
| Widget grouping/locking          | Post-launch power-user feature                                                                   |
| Multi-select in edit mode        | Low widget count makes individual ops sufficient                                                 |
| High-contrast dedicated mode     | Free Slate theme designed for high contrast                                                      |
