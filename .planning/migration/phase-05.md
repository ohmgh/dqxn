# Phase 5: Core Infrastructure

**What:** Shell internals that features depend on but packs never touch.

## `:core:design` (has Compose — applies `dqxn.android.compose`)

- Material 3 theme wiring
- Color system, typography
- Theme resolution (free → custom → premium ordering — F4.13)
- `ThemeSchema`, `ThemeColors`, `ThemeGradients`, `GradientSpec` — JSON serialization types deferred from Phase 2. These bridge theme JSON files → runtime `DashboardThemeDefinition` (in `:sdk:ui`). Have `toBrush()` methods requiring Compose
- `ThemeAutoSwitchEngine` — `@Singleton`, `@Eagerly` initialized in `Application.onCreate()`. Accepts `StateFlow<Float>` for illuminance and solar event data. Before Phase 8 providers exist, SOLAR_AUTO and ILLUMINANCE_AUTO modes are structurally wired but non-functional (default to SYSTEM fallback). Input interfaces defined in `:sdk:contracts` allow late binding
- Ambient light auto-switch logic (migrate from old theme system)

## `:core:thermal`

- `ThermalManager` → `RenderConfig` (extract from old `feature:dashboard/thermal/`)
- `FramePacer` — `Window.setFrameRate()` API 34+, emission throttling API 31-33. Defined in Phase 5 but requires `Window` reference from Activity — integration with `DashboardLayer` happens in Phase 7. Phase 5 delivers the class with injectable `Window` parameter; testable with mock Window
- `FakeThermalManager` for chaos injection — controllable `MutableStateFlow<ThermalLevel>`

## `:data`

- Proto DataStore schemas (`.proto` files):
  - `dashboard_layout.proto` — `DashboardStore` (profiles, active profile ID), `ProfileCanvas` (profile metadata + widget list), `SavedWidget` (typeId, position, size, style, settings, dataSourceBindings). See `persistence.md` lines 9-42 for full message definitions
  - `paired_devices.proto` — `PairedDeviceStore`, `PairedDeviceMetadata` (definitionId, displayName, macAddress, lastConnected)
  - `custom_themes.proto` — `CustomThemeStore`, `CustomTheme` (themeId, displayName, isDark, colors JSON blob, gradients JSON blob). Max 12 custom themes
- `DashboardWidgetInstance` data class — deferred from Phase 2. Fields: `instanceId: String`, `typeId: String`, `position: GridPosition`, `size: GridSize`, `style: WidgetStyle`, `settings: ImmutableMap<String, Any>`, `dataSourceBindings: Map<String, String>`, `zIndex: Int`. References Phase 2's `WidgetStyle`. Used by `LayoutState` (Phase 7 coordinators)
- `WidgetSizeSpec` — deferred from Phase 2. `widthUnits: Int`, `heightUnits: Int`. Grid coordinate types: `GridPosition(col: Int, row: Int)`, `GridSize(widthUnits: Int, heightUnits: Int)`
- Repository interfaces and implementations:
  - `LayoutRepository` — CRUD for profiles and widgets, debounced save (500ms), corruption handler. Profile CRUD: `createProfile()`, `cloneProfile(sourceId)`, `switchProfile(targetId)`, `deleteProfile(id)`, `getActiveProfile(): Flow<ProfileCanvas>`
  - `UserPreferencesRepository` — Preferences DataStore for simple settings (theme mode, orientation lock, status bar, keep screen on)
  - `ProviderSettingsStore` — pack-namespaced keys: `{packId}:{providerId}:{key}`
  - `PairedDeviceStore` — CRUD for paired BLE devices (first consumer: Phase 9 sg-erp2)
  - `ConnectionEventStore` — rolling 50-event log (F7.6)
  - `WidgetStyleStore` — per-widget style persistence (container glow, rim, opacity, corner radius, background). Consumed by `WidgetContainer` rendering in Phase 7
- `PresetLoader` + preset JSON files (F7.7) — loads region-aware default layouts. Depends on `LayoutRepository` for writing. **Old codebase has `preset_demo_default.json` (schema version 2) — portable with `free:*` → `essentials:*` typeId updates.** Note: `RegionDetector` (which `PresetLoader` may use for region-aware defaults) lives in `:pack:essentials` (Phase 8). Phase 5 `PresetLoader` uses a simpler timezone-based region heuristic; Phase 8 can enhance if needed
- Hardcoded minimal fallback layout (F7.12) — clock widget centered, as a code-level constant (`LayoutRepository.FALLBACK_LAYOUT`), not dependent on JSON or asset files. Used when preset loading fails (APK integrity, asset corruption)
- `ReplaceFileCorruptionHandler` on ALL DataStore instances
- `consumer-proguard-rules.pro` — R8 keep rules for proto-generated classes (distributed with module, consumed by `:app` automatically)
- Migration from old Preferences-JSON-blob approach to Proto

## `:core:firebase`

- Firebase SDK isolation module — only module with Firebase SDK dependencies, only imported by `:app`
- `FirebaseCrashReporter : CrashReporter` — wraps Crashlytics: `log()`, `recordException()`, `setCustomKey()`, `setUserId()`
- `FirebaseAnalyticsTracker : AnalyticsTracker` — wraps Firebase Analytics: `logEvent()`, `setUserProperty()`
- `FirebasePerformanceTracer` — wraps Firebase Performance: custom traces, HTTP metrics (if applicable)
- Requires `google-services.json` in `:app` — Phase 5 test strategy: unit tests mock Firebase interfaces, no real Firebase calls. Binding verification via Hilt test in Phase 6

## Replication Advisory References

Before implementing Phase 5, consult the following sections of [replication-advisory.md](replication-advisory.md):

- **§3 Theme & Studio Preview** — `ThemeAutoSwitchEngine` five modes (LIGHT, DARK, SYSTEM, SOLAR_AUTO, ILLUMINANCE_AUTO), `combine(isDarkActive, lightTheme, darkTheme)` → `activeTheme: StateFlow`. Old code has a pack isolation violation (hard dep on free pack sensor providers) — fix in new arch. Auto-switch engine is delivered here; the preview lifecycle (`displayTheme`, caller-managed `SetPreviewTheme`, race condition fix) is Phase 7/11.
- **§4 Source-Varying Transitions** — Spring configs (`standardSpring` 0.65/300, `hubSpring` 0.50/300, `previewSpring` 0.75/380) and all named enter/exit transitions port to `:core:design`. Duration asymmetry pattern: enter 200ms, exit 150ms. These are tuned values — port verbatim, do not re-guess.
- **§5 UI Design System** — Spacing system (4dp grid, `SpaceXXS`–`SpaceXXL` with 10 semantic aliases), `CardSize` enum (`SMALL` 8dp, `MEDIUM` 12dp, `LARGE` 16dp), `DashboardTypography` (8 roles), `TextEmphasis` (4 alpha constants) all port to `:core:design`. Fix known inconsistencies: radius fragmentation (inline hardcoded values), `OverlayScaffold` conflating spacing with shape, `OverlayTitleBar` using 0.6 alpha instead of 0.7.

**Ported from old:** `UserPreferencesRepository` (rewritten for Proto — old used Preferences DataStore with Gson blobs), `LayoutDataStore` (rewritten from JSON-in-Preferences to Proto DataStore). Theme JSON loading (schema parser). `DashboardThemeExtensions.kt`, `DashboardTypography`, `TextEmphasis`, `CardSize` from `feature/dashboard` → `:core:design`. **Greenfield:** `ThermalManager`, `RenderConfig`, `FramePacer` — no thermal management exists in old codebase. Proto DataStore schemas are new (old uses Preferences DataStore exclusively). `ConnectionEventStore` is new.

**Port inventory:**

| Old artifact | Target | Notes |
|---|---|---|
| `DashboardThemeExtensions.kt` — spacing scale (`SpaceXXS`–`SpaceXXL`, 4dp grid) + 10 semantic aliases (`ScreenEdgePadding`, `SectionGap`, `ItemGap`, `InGroupGap`, `ButtonGap`, `IconTextGap`, `LabelInputGap`, `CardInternalPadding`, `NestedIndent`, `MinTouchTarget`) | `:core:design` | Port values; **old location is `feature/dashboard` — unreachable by packs.** Extension target changes from `DashboardThemeDefinition` to new `ThemeDefinition`. Must be in `:core:design` so `:feature:settings` and overlay UI can use them |
| `DashboardTypography` — 8 named styles (`title`, `sectionHeader`, `itemTitle`, `label`, `description`, `buttonLabel`, `primaryButtonLabel`, `caption`) + `getTightTextStyle` helper | `:core:design` | Port; verify against Material 3 type scale |
| `TextEmphasis` — 4 alpha constants (`High=1.0f`, `Medium=0.7f`, `Disabled=0.4f`, `Pressed=0.12f`) | `:core:design` | Port verbatim |
| `CardSize` enum — `SMALL(8dp)`, `MEDIUM(12dp)`, `LARGE(16dp)` corner radii | `:core:design` | Port verbatim |

**Tests:**

**Thermal:**
- Thermal state transition tests (`FakeThermalManager` flow emission)
- `FramePacer` API branching: verify `Window.setFrameRate()` called on API 34+ mock, verify emission throttling logic on API 31-33 mock

**DataStore resilience:**
- DataStore corruption recovery tests (all instances have `ReplaceFileCorruptionHandler`)
- Layout serialization round-trip tests (Proto DataStore) — `DashboardWidgetInstance`, `GridPosition`, `GridSize` survive serialize/deserialize

**Schema migration (F7.2):**
- `LayoutMigration` v1→v2: widget positions preserved after schema upgrade
- Chained migration: N→N+1→N+2 applied in sequence
- Migration failure: corrupted proto → fallback to default preset (not crash)
- Backup-before-migration: pre-migration snapshot exists on disk

**Repository CRUD (Critical — 6 repositories, all consumed by Phase 7 coordinators):**
- `LayoutRepository`: `createProfile()` → profile exists in flow, `cloneProfile()` duplicates widget list, `switchProfile()` updates active ID, `deleteProfile()` removes (cannot delete last), debounced save (500ms) batches rapid mutations
- `UserPreferencesRepository`: read/write round-trip for theme mode, orientation lock, status bar, keep screen on
- `ProviderSettingsStore`: key format `{packId}:{providerId}:{key}` round-trip, namespaced isolation (writing `essentials:gps-speed:unit` does not affect `essentials:compass:style`)
- `PairedDeviceStore`: add/remove/list paired devices, duplicate MAC address rejection
- `ConnectionEventStore`: rolling 50-event window — 51st event evicts oldest, ordering preserved
- `WidgetStyleStore`: per-widget style persistence round-trip, default style for missing widget

**Preset system:**
- `PresetLoader`: timezone-based region heuristic returns correct region for known timezones (e.g., "America/New_York" → "US", "Europe/London" → "GB")
- `PresetLoader` failure → `LayoutRepository.FALLBACK_LAYOUT` loaded (clock widget only)
- Hardcoded fallback layout loads without asset files (code-level constant, no JSON parsing)

**Theme:**
- `ThemeAutoSwitchEngine` tests with late-binding inputs (existing)

**Firebase (mock-based, no real Firebase):**
- `FirebaseCrashReporter`: `recordException()` delegates to Crashlytics mock with correct params
- `FirebaseAnalyticsTracker`: `logEvent()` delegates to Firebase Analytics mock, `setEnabled(false)` suppresses

**Validation:**

1. `./gradlew :core:design:compileDebugKotlin --console=plain` — theme system compiles with Compose
2. `./gradlew :core:thermal:compileDebugKotlin --console=plain` — thermal module compiles
3. `./gradlew :data:compileDebugKotlin --console=plain` — Proto DataStore schemas generate and compile
4. `./gradlew :core:firebase:compileDebugKotlin --console=plain` — Firebase isolation module compiles
5. `./gradlew :core:thermal:testDebugUnitTest --console=plain` — thermal tests pass
6. `./gradlew :data:testDebugUnitTest --console=plain` — DataStore tests pass
7. `LayoutRepository` CRUD tests pass — profile create/clone/switch/delete lifecycle verified
8. `ProviderSettingsStore` namespaced key format tests pass
9. Schema migration tests pass — v1→v2 preserves widget positions, chained migration works, failure falls back to default
10. `PresetLoader` region heuristic and fallback tests pass
11. `FramePacer` API branching tests pass
