# Phase 10: Settings Foundation + Setup UI

**What:** Schema-driven settings row system, widget/global settings screens, setup wizard UI, and widget picker. This is the foundation layer that all other overlay UI depends on. Deliberately sequenced before Phase 9 to unblock sg-erp2's BLE device pairing UI.

**Depends on:** Phase 8 (architecture validation gate — coordinator APIs confirmed correct before building UI on them)

**Composable count:** ~35. **Port volume:** ~3,500 lines from old codebase + ~500 lines new.

## Settings row system

**`SettingRowDispatcher`** — dispatch hub routing `SettingDefinition` subtypes to row composables. Old code: 158 lines, 12-branch `when` with `AnimatedVisibility`, `visibleWhen` evaluation, entitlement gating. Each row type:

| Row type | Old lines | Complexity | Notes |
|---|---|---|---|
| `BooleanSettingRow` | 57 | Low | Stateless toggle |
| `EnumSettingRow` | 198 | Medium | 3-way render: dropdown (>10 options), preview cards, chips (≤10). Chunked layout (rows of 5) |
| `IntSettingRow` | 71 | Low | Dynamic presets via `getEffectivePresets(currentSettings)` |
| `FloatSettingRow` | 52 | Low | Discrete selection (no sliders — conflicts with pager swipe) |
| `StringSettingRow` | 175 | Medium | `textFieldValue` + `isEditing` + keyboard auto-defocus (ADR-003), `maxLength` filter |
| `InfoSettingRow` | 84 | Low | 4-style color mapping (INFO/WARNING/SUCCESS/ERROR) |
| `InstructionSettingRow` | 127 | Low | Step number badge, action button, direct intent launch |
| `AppPickerSettingRow` | 81 | Low | Package name → app name resolution, navigation to app picker |
| `DateFormatSettingRow` | 79 | Low | Live date preview, navigation to format picker |
| `TimezoneSettingRow` | 110 | Low | City + GMT offset display, navigation to timezone picker |
| `SoundPickerSettingRow` | 117 | Medium | `ActivityResultLauncher` for system ringtone picker, URI-to-name resolution |

Building blocks: `SettingComponents.kt` (189 lines) — `SettingLabel`, `SelectionChip`, `PreviewSelectionCard` + `formatGmtOffset`, `executeInstructionAction`.

Additional building blocks: `SelectionBox`, `CornerRadiusPreview` (extracted from `StyleSettingsContent`).

## Sub-overlay pickers

Full-screen sub-overlays consumed by setting row types:
- `TimezonePicker` — timezone search/filter with city + GMT offset (from old `FeatureSettingsContent.kt` timezone section, ~100 lines)
- `DateFormatPicker` — live preview of date formats (from old `FeatureSettingsContent.kt` date format section, ~80 lines)
- `AppPicker` — launcher app grid with search, suggested-first sorting (old `AppPickerContent.kt`, 270 lines)
- `SoundPicker` — system ringtone picker integration via `ActivityResultContracts`

## Widget settings sheet

**`WidgetSettingsSheet`** — 3-tab pager (Feature / Data Source / Info). Old `WidgetSettingsPager.kt` (157 lines) + `SettingsSheetDispatcher` (270 lines — orchestrates schema merging, feature + style settings persistence) + `FeatureSettingsContent` (376 lines) + `StyleSettingsContent` (282 lines) + `DataProviderSettingsContent` (256 lines) + `WidgetInfoContent` (353 lines with shared element transitions, 5 issue types with resolution actions).

Key risk: `SettingsSheetDispatcher` in old code threads state through `DashboardViewModel` (god-object). New architecture routes through `WidgetBindingCoordinator.updateSettings()` — decompose the dispatcher into a state holder per widget.

**NF-D1 (speed disclaimer):** `WidgetInfoContent` for speed/speed-limit widgets includes disclaimer text: "Speed and speed limit data are approximate. Always refer to your vehicle speedometer and posted signs." Sourced from Android string resources.

## Main settings

**`MainSettings`** — 4 sections: Appearance, Behavior, Data & Privacy, Danger Zone. Old `MainSettingsContent.kt` (390 lines). Mostly navigation dispatch with a status bar toggle.

**`DeleteAllData` (F14.4):** Clear ALL DataStores, revoke Firebase analytics ID, reset to factory state. Uses `ConfirmationDialog` from `:core:design` (delivered Phase 7) with destructive styling. Implementation: inject all DataStore repositories (`LayoutRepository`, `UserPreferencesRepository`, `ProviderSettingsStore`, `PairedDeviceStore`, `WidgetStyleStore`) and call `clear()` on each, then `FirebaseAnalytics.resetAnalyticsData()`.

**Analytics toggle (F12.5):** Settings → Data & Privacy includes analytics consent toggle. Default OFF (opt-in per PDPA/GDPR). State persisted in `UserPreferencesRepository`. Toggling ON shows consent dialog explaining data collected, purpose, and right to revoke. Toggling OFF immediately stops analytics collection via `AnalyticsTracker.setEnabled(false)`. No analytics events fire before opt-in.

## Widget picker

**`WidgetPicker`** — staggered grid grouped by pack, live previews via `WidgetPreviewData` (scaled composables fed by single-shot data providers). Old `WidgetPickerContent.kt` (300 lines) + `WidgetPreviewData.kt` (56 lines). Shows entitlement badges, one-line descriptions from `WidgetSpec.description`, required data type icons (GPS/BLE/none).

**Entitlement gating in picker (F8.7):** Preview-regardless-of-entitlement, gate-at-persistence. Gated widgets show lock icon + "Coming soon" for purchase. `StubEntitlementManager` from Phase 6 treats all as free.

## Setup wizard UI

**`SetupSheet`** + setup card system. Old `SetupSheetContent.kt` (354 lines) — multi-page paginated flow, `persistedSettings` (produceState from DataStore), verification results (`mutableStateMapOf`), ON_RESUME re-verification.

Setup card composables:

| Card | Old lines | Complexity | Notes |
|---|---|---|---|
| `SetupDefinitionRenderer` | 151 | Low | Dispatch to 7 card types with visibility/entitlement gating |
| `SetupNavigationBar` | 167 | Low | Done button disabled until all items satisfied |
| `SetupPermissionCard` | 189 | Medium | 3 states (granted, rationale, permanently denied). Replace Accompanist with native `rememberLauncherForActivityResult` + `ActivityResultContracts.RequestMultiplePermissions` |
| `SetupToggleCard` + `SystemServiceCard` | 225 | Low | Satisfied/unsatisfied binary state |
| `DeviceScanCard` | 759 | **Very High** | CDM/BLE state machine (5 states), multi-attempt verification (3 attempts, 2s retry, 30s timeout), API-level branching (31-32, 33+, 36+), paired device management with forget dialog. Extract state machine to testable non-UI class |
| `PairedDeviceCard` | 161 | Low | 3-state border color, `isForgetting` progress indicator |
| `DeviceLimitCounter` | 47 | Low | Visibility gated on `pairedCount >= 2` |
| `InstructionCard` | 65 | Low | Bridge to `SettingRowDispatcher` |
| `InfoCard` | 37 | Low | Bridge to `InfoSettingRow` |

**Critical migration note:** `DeviceScanCard` is the single highest-complexity composable in Phase 10. Extract the CDM state machine (`ScanState` sealed interface, verification retry logic, API-level dispatch) into a `DeviceScanStateMachine` class — testable without Compose, ~300 lines of pure logic separated from ~450 lines of UI.

## OverlayNavHost partial population

Scaffold empty `OverlayNavHost` from Phase 7 receives its first routes:

| Route | Destination | Coordinator interaction |
|---|---|---|
| `WidgetPicker` | Widget selection grid | `LayoutCoordinator.addWidget()` |
| `Settings` | Main settings sheet | Various coordinators via commands |
| `WidgetSettings(widgetId)` | Per-widget settings | `WidgetBindingCoordinator.updateSettings()` |
| `Setup(providerId)` | Provider setup wizard | Provider-specific, driven by `SetupDefinition` |

Remaining routes (`ThemeSelector`, `Diagnostics`, `Onboarding`) populated in Phase 11.

## `OverlayScaffold` infrastructure

**`OverlayScaffold`** — shared container for all overlay composables. Old `OverlayScaffold.kt` (175 lines). `OverlayType` enum (Hub/Preview/Confirmation) determines sheet shape + padding. `OverlayTitleBar` with close button.

**`ConfirmationDialog`** — uses `ConfirmationDialog` from `:core:design` (delivered Phase 7). Animated entry/exit via `DashboardMotion.dialogEnter/dialogExit`. Used by Delete All Data, Reset Dash, widget delete.

## `PackBrowserContent`

**`PackBrowserContent`** — pack list with shared element transitions, entitlement status derivation, debug entitlement toggle. Old `PackBrowserContent.kt` (449 lines). Accessible from Settings → Behavior → Dash Packs.

## Replication Advisory References

Before implementing Phase 10, consult the following sections of [replication-advisory.md](replication-advisory.md). This phase has the second-densest advisory coverage — 5 of 7 sections apply:

- **§1 Widget Preview Mode** — WidgetSettings sheet is the preview trigger. Transparent area above sheet is tap-to-dismiss target. Sheet itself consumes all touches. Dismissal uses `navigate(Route.Empty) { popUpTo<WidgetSettings> { inclusive = true } }` — NOT `popBackStack()`. Viewport fraction coordination: sheet uses `1 - viewportFraction`. Fix landscape mismatch (old: sheet always uses `0.38f` even in landscape while widget targets `0.28f`).
- **§2 Jankless Navigation** — Source-varying transitions on Settings: `fadeOut(100ms)` to preview overlays vs `ExitTransition.None` to hubs. PackBrowser enter varies by source: `slideInHorizontally(spring 0.65/300) + fadeIn(200ms)` from Settings, `fadeIn(300ms)` from WidgetSettings (shared element drives), default `fadeIn(200ms)`. Shared element pack card morph: `SharedTransitionLayout` wraps OverlayNavHost, key `"pack-card-${pack.packId}"`, `skipToLookaheadSize()` on source side only. Dismissal stack management: simple destinations use `popBackStack()`, root-level use `navigate(Empty) { popUpTo }`.
- **§4 Source-Varying Transitions** — Route-level transition specs: ThemeModeSelector/ThemeSelector/ThemeEditor popEnter uses `fadeIn(150ms)` NOT previewEnter (avoids double-slide). WidgetSettings uses `ExitTransition.None`/`EnterTransition.None` (stays visible under hubs). Source-varying logic uses `destination.hasRoute<RouteClass>()` on `initialState`/`targetState`.
- **§5 UI Design System** — Apply spacing tokens consistently: `SpaceM` (16dp) for `ScreenEdgePadding`/`SectionGap`/`CardInternalPadding`, `SpaceS` (12dp) for `ItemGap`, `SpaceXS` (8dp) for `InGroupGap`/`ButtonGap`. Use `DashboardTypography` roles — not raw M3 tokens. Apply `TextEmphasis.Medium` (0.7) not inline alpha values. Use `CardSize` enum — not inline `8.dp` hardcoded. Fix `OverlayScaffold` using `SpaceM` for corner radius (should use `CardSize.LARGE` 16dp).
- **§7 Widget Setup Architecture** — Full setup flow lifecycle: `SetupSheetContent` is fullscreen paginated (not bottom sheet). One-shot `produceState` for settings loading, local `MutableMap` for in-memory mutations, immediate write-through on change (no debounce). Forward navigation gating: `Setting` types always satisfied, only requirement types block. Buttons alpha-dimmed (50%) when disabled but remain tappable. Two exclusive `BackHandler` instances (page > 0 vs page == 0). `evaluationTrigger` counter pattern for re-evaluation after async events (composition, `ON_RESUME`, device pairing success). Permission card 3 states (granted/can-request/permanently-denied) with `hasRequestedPermissions` guard against false permanent-denial detection. BLE `DeviceScanCard` 5-state machine (PreCDM→Waiting→Verifying→Success/Failed), CDM setup with `setSingleDevice(true)`, 3-retry verification with 2s delay and 30s timeout, API-level branching (31-32, 33+, 36+). Four picker patterns (Timezone=fullscreen hub, DateFormat=popup, App=fullscreen grid, Sound=system activity). Two-layer dispatcher: `SetupDefinitionRenderer` dispatches `SetupDefinition` subtypes, `SettingRowDispatcher` dispatches `SettingDefinition` subtypes. `SettingNavigation` sealed interface with 4 events. `ProviderSettingsStore` type-prefixed serialization with legacy fallback. `PairedDeviceStore` additive upsert by MAC. Edge cases: `visibleWhen` during empty map, setup interrupted mid-flow, CDM not available, EnumSetting string serialization comparison.

**Tests:**
- `SettingRowDispatcher`: parameterized test rendering all 12 `SettingDefinition` subtypes (including `UriSetting` via `SoundPickerSettingRow` and `AppPickerSetting` via `AppPickerSettingRow`). Verifies: each subtype renders non-empty content, value changes propagate to `ProviderSettingsStore`, `visibleWhen` evaluation hides/shows correctly, entitlement gating shows lock icon for gated settings, `AnimatedVisibility` for hidden rows
- `SetupSheet` navigation: multi-step setup with back navigation, permission request delegation, completion callback. `DeviceScanStateMachine` unit tests: 5-state transitions, retry logic boundaries, API-level dispatch, device limit enforcement
- `WidgetSettingsSheet`: 3-page navigation, schema default extraction, settings merge, provider selection, style property updates
- `MainSettings`: navigation dispatch, analytics consent toggle state transitions, `DeleteAllData` clears all DataStore instances
- `WidgetPicker`: widget grouping by pack, entitlement badge display, scale calculation
- Sub-overlay pickers: `TimezonePicker` search filters correctly, `DateFormatPicker` shows live preview, `AppPicker` renders launcher apps with search, `SoundPicker` launches system ringtone picker intent correctly
- Overlay navigation: route to each Phase 10 overlay (WidgetPicker, Settings, WidgetSettings, Setup), verify content renders, back navigation returns to dashboard
- NF-D1 speed disclaimer: `WidgetInfoContent` for speed/speed-limit widget types includes disclaimer string resource text (composition test with semantics assertion)
