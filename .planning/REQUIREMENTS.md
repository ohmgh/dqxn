# DQXN Product Requirements Document

> Product definition for the DQXN Android dashboard platform.

## 1. Product Vision

DQXN is a modular dashboard platform for Android. Real-time data displayed through a fully configurable widget grid. Use cases include automotive (phone/tablet mounted in a vehicle), desk/bedside displays, home automation panels, and finance dashboards. The platform uses a pack-based architecture where feature packs (widgets, themes, data providers) are decoupled from the dashboard shell, enabling regional feature sets, premium gating, and first-party modular extensibility.

**Tagline**: "Life is a dash. Make it beautiful."

**Target users**: Drivers who want a customizable, visually rich heads-up display — starting with the Singapore market and expanding globally.

**Reference devices**: Snapdragon 6-series or equivalent (e.g., Pixel 7a, Samsung Galaxy A54), 6GB RAM, 1080p display. Performance targets validated against this baseline.

## 2. Core Concepts

### Dashboard Canvas

An unbounded 2D grid (unit = 16dp) where widgets are placed at absolute positions. The canvas extends beyond any single viewport — widgets can exist at any coordinate. The viewport is computed from screen dimensions and acts as a rendering window into the canvas. Widgets fully outside the viewport are culled (zero render cost). Widgets must not straddle viewport boundaries — the no-straddle snap constraint ensures every widget is either fully visible or fully invisible in any display configuration. See F1.15 for orientation lock, F1.26–F1.31 for configuration boundaries.

### Display Configurations

A device may have multiple display configurations: fold/unfold states × orientation lock settings. Each configuration defines a viewport rectangle on the canvas. The device reports configurations via Jetpack `WindowInfoTracker` / `WindowMetrics`. Configuration boundaries are the edges of these viewport rectangles.

- **Non-foldable, orientation-locked**: 1 configuration, 0 boundaries
- **Non-foldable, orientation-unlocked**: 2 configurations (landscape + portrait), 2 boundary rectangles
- **Foldable, orientation-locked**: 2 configurations (folded + unfolded), 2 boundary rectangles
- **Foldable, orientation-unlocked**: 4 configurations (folded×2 orientations + unfolded×2 orientations), 4 boundary rectangles

Free-sizing windows (OEM-forced split-screen) do not constitute a configuration change — widget positions do not change upon free window resizing.

### Dashboard Profiles

Each profile owns an independent `DashboardCanvas` — its own widget set, positions, and sizes. Profiles are full dashboards, not visibility filters. Profile switching is a page transition between separate canvases.

- **Default profile**: always exists, non-deletable. Created on first launch with the onboarding preset.
- **Custom profiles**: user-created (e.g., "Driving", "Desk", "Bedtime"). New profile clones the currently active dashboard — user edits from there, not from scratch. Packs can register `ProfileDescriptor`s with optional auto-switch triggers.
- **Profile switching**: horizontal swipe on the dashboard canvas (Android home screen page model) and/or tap profile icon in bottom bar. Manual switch always overrides auto-switching.
- **Adding widgets**: defaults to current profile. "Add to all profiles" option in widget picker for shared widgets (clock, battery).
- **Auto-switching**: pack-provided `ProfileTrigger`s (GPS speed, WiFi SSID, time of day). User configures which triggers activate which profiles, with priority ordering for simultaneous triggers. "Resume auto" re-enables trigger-based switching after manual override.
- **Launcher integration**: profile-as-page model directly maps to home screen pages for future launcher pack.

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
| F1.9  | Auto-hide bottom bar: Settings button (always), profile icons (when 2+ profiles exist, active profile highlighted), Add Widget button (edit mode only). Tap to reveal, auto-hide after 3s inactivity. Floats over canvas (no layout shift). Minimum 76dp touch targets | Must     |
| F1.10 | Z-index stacking for overlapping widgets                                                      | Must     |
| F1.11 | Edit mode visual feedback (wiggle animation, corner brackets)                                 | Must     |
| F1.12 | No widget count limit — users may place as many widgets as the viewport supports              | Must     |
| F1.13 | Dashboard-as-shell pattern (canvas persists beneath all overlays)                             | Must     |
| F1.14 | Pause state collection for CPU-heavy overlay routes (settings, widget picker)                  | Must     |
| F1.15 | Orientation lock (landscape default, configurable in settings)                                | Must     |
| F1.16 | `FLAG_KEEP_SCREEN_ON` while dashboard is active, user-configurable                            | Must     |
| F1.17 | Haptic feedback on edit mode entry/exit, widget focus, resize snap, button press              | Must     |
| F1.18 | Pixel-shift for OLED burn-in prevention (1-2px translation every 5 minutes)                   | Should   |
| F1.19 | HUD mirror mode: horizontal flip applied to Layer 0 (dashboard canvas) only via single `graphicsLayer { scaleX = -1f }`. Layer 1 overlays (settings, pickers) remain un-flipped for normal interaction. Text on dashboard is mirrored (correct for windshield reflection) | Should   |
| F1.20 | Grid snapping: widget snaps to nearest 2-unit grid boundary on drop. Visual grid overlay during drag. Haptic tick on snap | Must     |
| F1.21 | Widget add/remove animations: fade + scale-in on add (spring animation via `graphicsLayer`), fade + scale-out on delete | Should   |
| F1.22 | Auto-arrange: button in edit mode. Algorithm: sort widgets by area (descending), clear all positions, re-place each sequentially via `GridPlacementEngine.findOptimalPosition()` respecting cutout zones. Greedy approximation, not optimal packing, but acceptable for ≤20 widgets. Avoids cutout regions (F1.24) | Should   |
| F1.23 | Multi-window disabled: `resizeableActivity="false"`. Dashboard is fullscreen-only             | Must     |
| F1.24 | Cutout/punch-hole awareness: widgets placed in `DisplayCutout` exclusion zones show a visual warning in edit mode. Auto-arrange (F1.22) avoids cutout regions | Should   |
| F1.25 | When the app detects it is running in a window smaller than 480dp in either dimension (forced split-screen by OEM or accessibility), display a persistent banner: "DQXN works best in fullscreen" with a "Go Fullscreen" action. Continue rendering with viewport-adapted layout | Should   |
| F1.26 | Configuration boundaries: display viewport boundary lines for all device display configurations in edit mode. Each boundary labeled with its configuration name (e.g., "folded portrait", "unfolded landscape"). Boundaries visible only in edit mode | Must     |
| F1.27 | No-straddle snap: when dragging a widget, if its bounding box would cross a configuration boundary, snap it to the nearest side (inside or outside, whichever is closer). Hard constraint — a widget can never straddle a boundary in persisted layout. Distinct haptic + visual cue on boundary snap (different from regular grid snap) | Must     |
| F1.28 | Configuration-aware default placement: onboarding places core widgets (clock, battery, date) within the intersection region visible across all device configurations. Secondary widgets fill outward into larger-viewport zones. Every configuration gets a coherent widget subset | Must     |
| F1.29 | Profile switching via horizontal swipe on dashboard canvas (page transition) and tap on profile icon in bottom bar. Single-profile: no icons, no swipe affordance. Two+ profiles: profile icons appear, swipe activates | Must     |
| F1.30 | Per-profile dashboards: each profile owns an independent `DashboardCanvas` with its own widget set, positions, and sizes. New profile clones the current dashboard. Adding a widget defaults to current profile with "Add to all profiles" option | Must     |
| F1.31 | Profile auto-switching: packs register `ProfileDescriptor` with `ProfileTrigger`. User configures trigger enable/disable and priority ordering in profile automation settings (accessible via bottom bar or Settings). Manual profile selection pauses auto-switching; "Resume auto" re-enables | Should   |

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
| F2.7  | Widget picker with preview images, one-line description, required data type icons (GPS/BLE/none), and entitlement badges. Widget descriptions sourced from `WidgetSpec.description` field | Must     |
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
| F2.20 | `WidgetSpec` includes `description: String` — one-line explanation of what the widget shows (e.g., "Current speed with acceleration arc and limit warning"). Used in widget picker and widget info page | Must     |

### F3: Data Provider System

Reactive data sources with declarative setup.

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F3.1  | `DataProvider<T : DataSnapshot>` contract with `provideState(): Flow<T>`                      | Must     |
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
| F3.15 | Progressive error disclosure with user-facing messages. All `WidgetStatusCache` states map to specific user-facing strings. Icon + message + tap to diagnose. Messages are user-friendly (e.g., "GPS signal lost — move to an open area"), not developer-oriented (no exception messages). All error strings in Android string resources (localizable) | Must     |

### F4: Theme System

Visual customization with auto-switching.

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F4.1  | `DashboardThemeDefinition` — colors + gradient brushes                                        | Must     |
| F4.2  | JSON-driven theme loading from bundled assets                                                 | Must     |
| F4.3  | Dual-theme model (separate light/dark selections)                                             | Must     |
| F4.4  | 5 auto-switch modes: LIGHT, DARK, SYSTEM, SOLAR_AUTO, ILLUMINANCE_AUTO                       | Must     |
| F4.5  | `ThemeAutoSwitchEngine` with eager sharing (ready at cold start)                              | Must     |
| F4.6  | Theme preview: live preview before committing, reverts on cancel. Preview times out after 60 seconds with toast "Theme preview ended." | Must     |
| F4.7  | Theme Studio — create/edit custom themes (max 12)                                             | Should   |
| F4.8  | Gradient editor (vertical, horizontal, linear, radial, sweep; 2-5 stops)                      | Should   |
| F4.9  | Preview-regardless-of-entitlement, gate-at-persistence                                        | Must     |
| F4.10 | Reactive entitlement revocation (auto-revert to free defaults)                                | Must     |
| F4.11 | Spacing tokens and typography scale in theme definition                                       | Should   |
| F4.12 | Clone built-in → custom via long-press                                                        | Should   |
| F4.13 | Theme selector ordering: free themes always listed first, then custom themes, then premium themes | Must     |

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
| F5.7  | **Speed Limit (Circle)** — European-style circular sign, region-aware (auto KPH/MPH, Japan blue digits). Speed limit value is user-configured as a static value in widget settings (per-widget). Future regional packs may provide dynamic speed limit data via provider (e.g., OpenStreetMap speed zones). In v1, SPEED_LIMIT is a widget-internal setting, not a data provider type | Must     |
| F5.8  | **Shortcuts** — tappable widget launching a chosen app, with suggested apps                   | Should   |
| F5.9  | **Solar** — sunrise/sunset times or next event countdown, optional 24h circular arc with sun/moon marker | Should   |
| F5.10 | **Ambient Light** — lux level, category (DARK/DIM/NORMAL/BRIGHT), InfoCard layout             | Should   |
| F5.11 | **Speed Limit (Rectangle)** — US-style rectangular sign. Speed limit value is user-configured as a static value in widget settings (per-widget). Future regional packs may provide dynamic speed limit data via provider (e.g., OpenStreetMap speed zones). In v1, SPEED_LIMIT is a widget-internal setting, not a data provider type | Should   |

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
| F7.2  | Versioned layout schema with chained migration transformers (N → N+1 → N+2). Migrations run sequentially. Pre-migration backup created before attempting migration. On migration failure: fall back to default preset, display toast "Your layout was reset after an update", log failure for crash reporting. If schema version is >5 behind current, reset to default instead of running fragile migration chain | Must     |
| F7.3  | Debounced layout save (500ms) with atomic writes (write to temp, swap on success)             | Must     |
| F7.4  | Type-safe provider settings store with pack-namespaced keys                                   | Must     |
| F7.5  | Paired device persistence (survives restarts)                                                 | Must     |
| F7.6  | Connection event log (rolling 50 events)                                                      | Should   |
| F7.7  | Preset system — JSON presets, region-aware defaults                                           | Must     |
| F7.8  | Layout corruption detection with fallback to last-known-good or default preset                | Must     |
| F7.9  | Edit-mode cancel restores pre-edit layout state. When more than 1 action has been performed (add, move, resize, delete), show confirmation dialog: "Discard all changes? You [action summary]." Single undo not supported in v1 | Must     |
| F7.10 | Android Auto Backup support: include DataStore files. On restore, handle missing packs: widgets from uninstalled packs show `UnknownWidgetPlaceholder` with pack name and "Install [pack] to restore" CTA. Theme selections from missing packs revert to free default. Provider settings from missing packs preserved silently. Entitlement state re-verified via Play Billing on restore | Should   |
| F7.11 | Preset import/export via share intent (user-facing)                                           | Should   |
| F7.12 | Hardcoded minimal fallback layout (clock widget, centered) as a code-level constant, not dependent on JSON or asset files. Used when preset loading fails (APK integrity issue, asset corruption) | Must     |

### F8: Entitlements & Monetization

| Req  | Description                                                                                    | Priority |
|------|------------------------------------------------------------------------------------------------|----------|
| F8.1 | Entitlement system: `free`, `plus`, `themes` tiers (extensible for regional packs)             | Must     |
| F8.2 | Play Billing integration (purchase, restore)                                                   | Must     |
| F8.3 | `Gated` interface on renderers, providers, themes, settings                                    | Must     |
| F8.4 | Upsell overlays on gated widgets with frequency caps                                           | Must     |
| F8.5 | Debug "Simulate Free User" toggle                                                              | Should   |
| F8.6 | Contextual upsell triggers: theme preview → purchase, plus widget attempt → purchase          | Must     |
| F8.7 | Plus widget preview: users can preview plus widgets with live data in the widget picker (scaled composable). Adding to layout requires entitlement. Mirrors theme preview-regardless-of-entitlement, gate-at-persistence pattern | Should   |
| F8.8 | Family sharing: enable Google Play Family Library for one-time IAP packs (Play Console toggle) | Should   |
| F8.9 | Refund UX: on entitlement revocation, widgets show EntitlementRevoked overlay, remain in layout but non-functional. Premium themes revert to free. One-time toast explains change | Must     |
| F8.10 | When entitlement state cannot be determined (offline + stale Play Billing cache), preserve last-known entitlement state for up to 7 days. After 7 days without verification, downgrade to free tier with explanatory banner. Re-verify immediately when connectivity returns | Must     |

**Monetization Model**: One-time IAP per pack (aligned with market expectations of $3–$10 range). No subscriptions for v1. Regional pricing for Singapore (SGD). Bundle discount for themes + plus together.

| Entitlement | Scope                                                                                                      | Price Range    |
|-------------|------------------------------------------------------------------------------------------------------------|----------------|
| `free`      | Core widgets (speedometer, clock, date, compass, battery, speed limit, shortcuts) + 2 themes + full customization | Free           |
| `plus`      | Trip computer, media controller, G-force, altimeter, weather widgets                                          | $4.99–$6.99   |
| `themes`    | 22 premium themes + Theme Studio + Solar/Illuminance auto-switch                                           | $3.99–$5.99   |

### F9: Notifications & Alerts

| Req  | Description                                                  | Priority |
|------|--------------------------------------------------------------|----------|
| F9.1 | Silent connection status notification (connect/disconnect)   | Must     |
| F9.2 | Per-alert mode selection (SILENT/VIBRATE/SOUND)              | Must     |
| F9.3 | TTS readout for alerts                                       | Should   |
| F9.4 | Custom alert sound URIs                                      | Should   |

### F10: Driving Safety (Deferred Post-Launch)

> Driving mode is deferred to post-launch. DQXN is a general-purpose dashboard platform — driving safety is a context-specific feature, not a shell concern. When implemented, driving detection providers will be supplied by packs (GPS speed, OBD-II, etc.), users choose the provider per-widget via standard data binding and at the system level via dashboard settings (none or pick a provider).

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F10.1 | Motion detection via pack-supplied driving providers (GPS speed, OBD-II, etc.)                | Deferred |
| F10.2 | While in motion: disable edit mode, widget picker, settings, Theme Studio                     | Deferred |
| F10.3 | While in motion: allow only tap interactions on interactive widgets (Shortcuts, Media Controller) | Deferred |
| F10.4 | Minimum touch target size: 76dp for all interactive elements                                  | Must     |
| F10.5 | Speed limit alert with configurable offset (amber at +5, red at +10, user-configurable)       | Deferred |
| F10.6 | Parked-mode detection: speed = 0 for >5s unlocks full interaction                             | Deferred |
| F10.7 | Adaptive rendering: reduce frame rate to 30fps when stationary, disable glow effects under thermal pressure | Should   |
| F10.8 | Parking location save — GPS fix on app exit, deep-link to Maps for navigation back            | Deferred |
| F10.9 | Quick theme toggle: theme mode cycle button on floating bar (single tap, no distraction)       | Must     |

### F11: Onboarding

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F11.1 | Progressive onboarding — each tip shown once, tracked via Preferences DataStore: (a) First launch: "Tap Edit to customize your dashboard" (b) First edit mode entry: "Tap a widget to select it. Drag to move." (c) First widget focus: "Use corners to resize. Tap the gear for settings." (d) First widget settings open: dot indicators with brief page labels | Must     |
| F11.2 | Theme selection prompt on first launch — free themes listed first, then premium (upsell moment) | Must     |
| F11.3 | BLE device pairing prompt if BLE-dependent widgets are in default layout                      | Should   |
| F11.4 | Recommended layout guidance: 3-5 widgets for phone, 6-10 for tablet                          | Should   |
| F11.5 | Default preset for first launch must NOT include GPS-dependent widgets until location permission is granted. Initial layout: clock, battery, date only | Must     |
| F11.6 | Permission requests are lazy — triggered when user adds a GPS-dependent widget or enters setup for a BLE provider, not during splash screen | Must     |
| F11.7 | Permission flow: widget shows "Setup Required" overlay → tap opens setup wizard → wizard requests permissions → granted: widget binds data; denied: widget shows "Permission needed" with link to system settings | Must     |

### F12: Analytics & Crash Reporting

| Req   | Description                                                                                   | Priority |
|-------|-----------------------------------------------------------------------------------------------|----------|
| F12.1 | Crash reporting integration (Firebase Crashlytics or equivalent)                              | Must     |
| F12.2 | Key funnel events: install, first edit, widget add, theme change, upsell impression, purchase start/complete | Must     |
| F12.3 | Engagement metrics: session duration, widgets per layout, edit frequency                      | Should   |
| F12.4 | Privacy-compliant (Singapore PDPA, no PII in analytics)                                       | Must     |
| F12.5 | Analytics consent: opt-IN on first launch (consent dialog before collection begins). Toggle in settings to revoke consent at any time. Singapore PDPA requires consent before collection, not after | Must     |
| F12.6 | Upsell funnel parameters: upsell events include trigger source (`theme_preview`, `widget_picker`, `settings`) as event parameter | Must     |
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
| F13.8 | Structured test output: all test tasks output JUnit XML to predictable paths. Convention plugin configures `{module}/build/test-results/{variant}/` | Must     |
| F13.9 | Tiered validation pipeline documented for agentic development: compile check (~8s) → fast tests (~12s) → full module tests (~30s) → dependent tests (~60s) → on-device smoke with semantics verification (~30s) → full suite | Must     |
| F13.10 | Test categorization via JUnit5 tags: `fast`, `compose`, `integration`, `benchmark`. Convention plugin provides `fastTest`/`composeTest` tasks | Should   |
| F13.11 | Semantics tree inspection: ADB-queryable Compose semantics tree — element bounds, visibility, test tags, text content, actions, content descriptions. Enables agentic UI verification and autonomous accessibility auditing. Debug builds only | Should   |

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
| NF13 | Thermal degradation tiers: Normal (60fps, full effects) → Warm (45fps) → Degraded (30fps, no glow) → Critical (24fps, reduced effects) |
| NF14 | Sensor batching for non-critical sensors (compass, ambient light) to reduce SoC wakeups        |
| NF37 | Background battery: < 1% per hour with BLE service active, < 0.1% with no connections. GPS uses passive provider when backgrounded |

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
| NF41 | When device storage is critically low (<50MB free), display a persistent but dismissable warning banner |
| NF42 | Layout saves that fail due to storage must surface a user-visible error: "Unable to save. Free up storage space." |
| NF43 | Proto DataStore corruption handler required on ALL DataStore instances — corruption falls back to defaults, not crash |

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
| NF-P3 | PDPA compliance: consent collected before analytics begins (F12.5). Privacy policy URL in Play listing. "Delete All Data" in settings (F14.4). Purpose of data collection stated in privacy policy. Data breach notification procedures documented in operational runbook. No user accounts = simplified compliance. Applies alongside GDPR |
| NF-P4 | Data export: on-device data is user-accessible. For Firebase analytics data, deletion via Firebase data deletion API noted in privacy policy |
| NF-P5 | GDPR Article 15 right of access: Settings includes "Export My Data" function generating a human-readable summary of all stored data (layout, preferences, connection logs, analytics ID). For Firebase analytics, privacy policy includes instructions for requesting data via Firebase's data access mechanism |

### Legal & Disclaimers

| Req   | Description                                                                                   |
|-------|-----------------------------------------------------------------------------------------------|
| NF-D1 | Speed and speed limit displays are for informational purposes only. Widget Info page must include disclaimer: "Speed and speed limit data are approximate. Always refer to your vehicle speedometer and posted signs." |
| NF-D2 | Terms of service must explicitly disclaim liability for speed data accuracy |
| NF-D3 | First-launch onboarding includes a brief, dismissable disclaimer that the app supplements — does not replace — the vehicle dashboard |

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
| NF44 | App must handle display cutouts, punch-holes, and camera notches. Dashboard renders behind cutouts (edge-to-edge) but widget placement warnings surface when content overlaps |
| NF45 | Default presets are configuration-aware: core widgets placed in the intersection region visible across all device display configurations (fold states × orientation). Secondary widgets placed in larger-viewport zones. Every configuration renders a coherent subset |
| NF46 | Foldable behavior: when display configuration changes (fold/unfold), viewport recalculates. Widgets outside the new viewport are simply not rendered (no reflow, no relocation). The no-straddle constraint (F1.27) ensures widgets are always fully visible or fully invisible — never partially clipped. Configuration boundary lines in edit mode (F1.26) let users make informed placement decisions |

### Accessibility

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF30 | WCAG 2.1 AA contrast ratios for critical text (speed, time, speed limit)                       |
| NF31 | Minimum text size for at-a-glance readability (speed: 48sp+, secondary info: 24sp+)       |
| NF32 | TalkBack support for setup/settings flows. Dashboard rendering is explicitly excluded from screen reader support — real-time updating Canvas content at 60fps is fundamentally incompatible with screen reader patterns. Per-widget `accessibilityDescription` (F2.19) provides read-only value announcements on demand |
| NF33 | System font scale respected in settings UI (dashboard widgets use fixed sizes for layout stability) |
| NF39 | Reduced motion: when system `animator_duration_scale` = 0, disable wiggle, replace spring with instant transitions, disable pixel-shift. Glow remains |
| NF40 | Color-blind safety: speed limit warnings use color + pattern/icon (pulsing border + warning icon), not color alone. Free themes verified for deuteranopia contrast |
| NF47 | Critical data (speed, speed limit, time) must be readable at 10,000 lux ambient light. Free themes must include at least one high-contrast variant optimized for direct sunlight |

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
| NF-L3 | In-app review: Google Play In-App Review API. Trigger after 3+ sessions, 1+ layout customization, no crash in current session. Frequency cap: once per 90 days |

### Localization

| Req  | Description                                                                                    |
|------|------------------------------------------------------------------------------------------------|
| NF-I1 | v1 launches English-only. All user-facing strings in Android string resources (no hardcoded)   |
| NF-I2 | Widget data uses locale-aware formatting (decimal separators, unit labels)                     |
| NF-I3 | Target languages for v1.1: Simplified Chinese, Bahasa Melayu                                  |

## 5. Widgets Inventory

### Essentials Pack (`core`) — Free

| Widget              | Type ID                | Default Size | Data Types              | Description                                      | Key Settings                                                               |
|---------------------|------------------------|--------------|-------------------------|--------------------------------------------------|----------------------------------------------------------------------------|
| Speedometer         | `core:speedometer`     | 12x12        | SPEED, ACCELERATION     | Current speed with acceleration arc and limit warning | arcs, tick marks, speed unit, limit offset, warning bg, alert type        |
| Clock (Digital)     | `core:clock`           | 10x9         | TIME                    | Large digital time display with timezone support  | seconds, 24h, leading zero, timezone, sizes                               |
| Clock (Analog)      | `core:clock-analog`    | 10x10        | TIME                    | Traditional analog clock face                     | tick marks, timezone                                                       |
| Date (Simple)       | `core:date-simple`     | —            | TIME                    | Current date in configurable format               | date format, timezone                                                      |
| Date (Stack)        | `core:date-stack`      | —            | TIME                    | Current date in configurable format               | date format, timezone                                                      |
| Date (Grid)         | `core:date-grid`       | 10x6         | TIME                    | Current date in configurable format               | timezone                                                                   |
| Compass             | `core:compass`         | 10x10        | ORIENTATION             | Heading direction with tilt indicators            | tick marks, cardinal labels, tilt indicators                               |
| Battery             | `core:battery`         | 6x6          | BATTERY                 | Battery level, charging state, temperature        | show percentage, show temperature, charging indicator                      |
| Speed Limit (Circle)| `core:speedlimit-circle`| —           | —                       | European-style speed limit sign                   | speed limit value, border size, speed unit, digit color                    |
| Speed Limit (Rect)  | `core:speedlimit-rect` | —            | —                       | US-style speed limit sign                         | speed limit value, border size, speed unit                                 |
| Shortcuts           | `core:shortcuts`       | 9x9          | —                       | Tap to launch any installed app                   | app package, show label, InfoCard layout                                   |
| Solar               | `core:solar`           | —            | SOLAR                   | Sunrise and sunset times with arc visualization   | arc, display mode, countdown                                               |
| Ambient Light       | `core:ambient-light`   | 8x8          | AMBIENT_LIGHT           | Current ambient light level                       | InfoCard layout options                                                    |

### Plus Pack (`plus`) — Premium

| Widget           | Type ID           | Default Size | Data Types       | Description                                      | Key Settings                                          |
|------------------|-------------------|--------------|------------------|--------------------------------------------------|-------------------------------------------------------|
| Trip Computer    | `plus:trip`       | 12x8         | SPEED, TRIP      | Speed, distance, and duration tracking            | display fields, reset button, distance unit           |
| Media Controller | `plus:media`      | 12x6         | MEDIA_SESSION    | Now playing with playback controls                | show album art, controls size                         |
| G-Force          | `plus:gforce`     | 10x10        | ACCELERATION     | Lateral and longitudinal acceleration             | display mode (circle/vector), peak hold, sensitivity  |
| Altimeter        | `plus:altimeter`  | 8x6          | ALTITUDE         | GPS altitude display                              | unit (m/ft), barometric correction                    |
| Weather          | `plus:weather`    | 8x8          | WEATHER          | Current conditions and temperature                | show forecast, temperature unit                       |

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
2. `PresetLoader` loads a permission-safe preset (clock, battery, date — no GPS widgets)
3. Default layout renders immediately with no permission dialogs
4. Coach-mark overlay displays
5. Theme selection prompt (showcases premium themes as upsell)
6. User adds speedometer or compass → triggers permission request → granted: data flows; denied: Setup Required overlay

### Adding a Widget

1. User taps Edit → edit mode → taps "+" button (disabled while in motion)
2. Widget picker opens: staggered grid grouped by pack, preview images (with entitlement badges)
3. User taps a widget → `GridPlacementEngine.findOptimalPosition()` places it
4. Default data sources auto-assigned
5. Widget settings sheet auto-opens

### Widget Settings

Three-page pager:
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
2. Widgets show wiggle animation + corner brackets. Configuration boundary lines appear with labels
3. Tap widget → focus (translate to center, scale up, show overlay toolbar: delete/settings/style/duplicate)
4. Drag to move (snaps to 2-unit grid + no-straddle boundary snap), corner handles to resize (76dp minimum touch targets)
5. Profile switching disabled during edit mode — horizontal swipe is widget drag territory. Edits apply to the current profile's canvas only
6. Tapping widget content area unfocuses; tapping blank space or Edit button exits edit mode
7. Cancel discards all changes; confirm persists via debounced save

### Driving Mode (Deferred Post-Launch)

See F10. Driving mode is deferred to post-launch. When implemented, driving detection will be pack-provided via standard data binding, not a shell-level safety gate.

## 9. Settings

### Main Settings Sheet

**Appearance**

| Setting                | Type                                                                              | Description                                                      |
|------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------------|
| Theme Mode             | Selection (5 modes)                                                               | Light / Dark / System / Solar Auto / Illuminance Auto            |
| Light Theme            | Navigation                                                                        | Theme selector for light mode                                    |
| Dark Theme             | Navigation                                                                        | Theme selector for dark mode                                     |
| Illuminance Threshold  | Segmented button group: Dark 50lux / Dim 200lux / Normal 500lux / Bright 1000lux  | Lux threshold for dark mode trigger (visible in ILLUMINANCE_AUTO mode) |
| Show Status Bar        | Toggle                                                                            | System status bar visibility                                     |
| HUD Mirror Mode        | Toggle                                                                            | Horizontally flips canvas for windshield projection              |

**Behavior**

| Setting                | Type                                                                              | Description                                                      |
|------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------------|
| Keep Screen On         | Toggle (default: on)                                                              | Prevents screen timeout while dashboard is active                |
| Orientation Lock       | Selection (Landscape / Reverse Landscape / Portrait / Reverse Portrait)           | Locks display orientation                                        |
| Profiles               | Navigation                                                                        | Profile management: create/edit/delete profiles, configure auto-switch triggers and priority ordering. Also accessible via bottom bar profile dots |
| Dash Packs             | Navigation                                                                        | Opens Pack Browser                                               |

**Data & Privacy**

| Setting                | Type                                                                              | Description                                                      |
|------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------------|
| Diagnostics            | Navigation                                                                        | Opens Provider Health dashboard                                  |
| Analytics              | Toggle (default: off, opt-in)                                                     | Analytics consent — opt-in required before collection (PDPA). Toggle to revoke consent at any time |
| Export My Data         | Action                                                                            | Generates human-readable summary of all stored data (layout, preferences, connection logs, analytics ID) |

**Danger Zone**

| Setting                | Type                                                                              | Description                                                      |
|------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------------|
| Reset Dash             | Destructive action                                                                | Resets layout to default preset                                  |
| Delete All Data        | Destructive action                                                                | Clears all DataStores, revokes analytics ID, resets to factory state |
| Report a Problem       | Action                                                                            | Opens email intent with pre-filled device info, app version, connection event log |

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

| Screen                     | Empty/Error State              | User-Facing Message                                                                        |
|----------------------------|--------------------------------|--------------------------------------------------------------------------------------------|
| Dashboard (0 widgets)      | Empty dashboard                | Show persistent coach-mark + "Add your first widget" CTA                                   |
| Widget picker (all added)  | All widgets placed             | Show "All widgets placed" message                                                          |
| Theme selector (no customs)| No custom themes               | Show "Create your first theme" CTA                                                        |
| BLE scan (no devices)      | No devices found               | Show "No devices found" + troubleshooting tips                                             |
| Provider setup (denied)    | Permission denied              | Inline error + retry + system settings link                                                |
| Widget (provider GPS unavail.) | GPS signal lost            | "GPS signal lost — move to an open area"                                                   |
| Widget (provider BLE unavail.) | Device disconnected        | "Device disconnected — check Bluetooth"                                                    |
| Widget (data stale)        | Stale data                     | Dimmed display + "Last updated [time ago]"                                                 |
| Widget (setup required)    | Setup needed                   | "Tap to set up"                                                                            |
| Widget (entitlement revoked) | Upgrade required             | "Upgrade to [pack] to use this widget"                                                     |
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
| Home launcher mode               | Profile-as-page model and horizontal swipe UX position DQXN for future launcher integration, possibly as a pack |
| Picture-in-Picture mode          | Requires separate mini-dashboard render mode                                                     |
| Undo/redo in edit mode           | Cancel-all sufficient for v1                                                                     |
| Widget grouping/locking          | Post-launch power-user feature                                                                   |
| Multi-select in edit mode        | Individual ops sufficient for v1                                                                 |
| High-contrast dedicated mode     | Free Slate theme designed for high contrast                                                      |
| Driving safety features          | Deferred post-launch. DQXN is a general-purpose dashboard platform. When implemented, driving detection providers will come from packs via standard data binding, not as a shell-level concern |
