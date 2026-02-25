# Phase 11: Theme UI + Diagnostics + Onboarding - Research

**Researched:** 2026-02-25
**Domain:** Compose overlay UI (theme editing, diagnostics dashboards, onboarding flows), color conversion, analytics consent, session recording
**Confidence:** HIGH

## Summary

Phase 11 delivers three independent feature clusters: (1) theme editing/selection UI in `:feature:settings`, (2) a new `:feature:diagnostics` module, and (3) a new `:feature:onboarding` module. These complete all 7 `OverlayNavHost` routes. The clusters share zero inter-dependencies and can be planned/executed in any order.

The theme UI cluster is the densest -- ~8 composables ported from old codebase (~2200 lines) with critical state management patterns (preview lifecycle, race condition fix, auto-save, 60s timeout revert). The diagnostics module is entirely greenfield with moderate scope (4 dashboard views reading from existing observability infrastructure). Onboarding is greenfield with the highest novelty risk (session recording ring buffer + analytics consent gating).

**Primary recommendation:** Split into 3 independent plan streams (theme UI, diagnostics, onboarding) plus one shared plan for OverlayNavHost route wiring + analytics event call sites. Theme UI is highest-risk (replication advisory dependencies) and should be planned first. Keep session recording minimal (text-based timeline, no graphical replay at V1).

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F3.13 | Provider Health dashboard in `:feature:diagnostics` showing all active providers, connection state, last update timestamp, error descriptions, retry actions | `ProviderStatusProvider` interface exists in `:sdk:observability` with `providerStatuses(): Flow<Map<String, ProviderStatus>>`. Implementation needed in `:feature:dashboard` (WidgetBindingCoordinator) or as Hilt binding. Diagnostics reads via this interface -- no cross-feature dependency. |
| F4.6 | Theme preview: live preview before committing, reverts on cancel. Preview times out after 60s with toast | `ThemeCoordinator.handlePreviewTheme()` exists. Caller-managed preview pattern (set BEFORE nav) documented in replication advisory section 3. 60s timeout via `LaunchedEffect(previewTheme) { delay(60_000); clearPreview() }` |
| F4.7 | Theme Studio -- create/edit custom themes (max 12) | New composable `ThemeStudio`. ThemeStudioStateHolder class owns 8+ color states + `isDirty` via `derivedStateOf`. Auto-save via `LaunchedEffect(all color vars)`. Custom themes persisted as JSON via `ThemeJsonParser` (already in `:core:design`). |
| F4.8 | Gradient editor (5 types, 2-5 stops) | `GradientSpec` + `GradientType` + `GradientStop` already in `:sdk:ui`. New composables: `GradientTypeSelector` (FilterChip row) + `GradientStopRow` (add/remove/edit stops). |
| F4.9 | Preview-regardless-of-entitlement, gate-at-persistence | `Gated.isAccessible()` check at apply time, not preview time. ThemeSelector shows all themes with lock icons on gated. |
| F4.10 | Reactive entitlement revocation -- premium themes fall back to free default | `EntitlementManager.entitlementChanges` flow already defined. ThemeCoordinator reacts to revocation. |
| F4.12 | Clone built-in to custom via long-press | UI gesture on ThemeSelector; creates copy with new ID, opens in ThemeStudio. |
| F4.13 | Theme selector ordering: free first, then custom, then premium | Sorting logic in ThemeSelector: `freeThemes + customThemes + premiumThemes`. `BuiltInThemes.freeThemes` already provides free themes. |
| F7.6 | Connection event log (rolling 50 events) in diagnostics UI | `ConnectionEventStore` interface + implementation exist in `:data:device`. 50-event rolling window. Diagnostics reads `events: Flow<ImmutableList<ConnectionEvent>>`. |
| F11.1 | Progressive onboarding tips -- each shown once, tracked via Preferences DataStore | New `UserPreferencesRepository` fields: `hasSeenFirstLaunchTip`, `hasSeenEditModeTip`, `hasSeenFocusTip`, `hasSeenSettingsTip`. Tip composables in `:feature:onboarding` with `LaunchedEffect` + preference check. |
| F11.2 | Theme selection prompt on first launch -- free themes first | Simplified ThemeSelector subset in onboarding flow. Free themes only (no premium). |
| F11.5 | Default preset for first launch: clock + battery + date only (no GPS) | `PresetLoader` in `:data` already handles region-aware presets. Verify/add test that default preset excludes GPS-dependent widget typeIds. |
| F11.6 | Permission requests are lazy -- triggered by widget add/setup, not splash | No splash permission request. Permission cards in setup wizard (Phase 10 infrastructure). |
| F11.7 | Permission flow: Setup Required overlay, tap-to-setup, denied -> system settings | Reuses Phase 10 `SetupSheet` + permission card three-state model. |
| F12.2 | Key funnel events: widget_add, theme_change, upsell_impression, purchase_start | `AnalyticsEvent` sealed hierarchy already defines `WidgetAdded`, `ThemeChanged`, `UpsellImpression`. Wire `AnalyticsTracker.track()` calls at interaction points. All gated on consent. |
| F12.3 | Engagement metrics: session_start/end with widget count, edit frequency | `AnalyticsEvent.SessionStart` + `SessionEnd` already defined with all required params (durationMs, widgetCount, editCount, jankPercent, peakThermalLevel, widgetRenderFailures, providerErrors). |
| F12.4 | Privacy-compliant (Singapore PDPA, no PII in analytics) | No PII in event params. Consent-gated. User IDs are analytics-assigned, not personal. |
| F12.5 | Analytics consent: opt-IN on first launch | `UserPreferencesRepository.analyticsConsent` flow exists (defaults false). `MainSettingsViewModel.setAnalyticsConsent()` already wires enable/disable ordering. New: consent dialog composable in `:feature:onboarding`. |
| F12.6 | Upsell funnel params: trigger_source on upsell events | `UpsellImpression.trigger` param already mapped. Wire at theme_preview, widget_picker, settings call sites. |
| F12.7 | Session quality metrics: jank%, thermal, failures, errors in session_end | `SessionEnd` data class has all 4 params. Wire from `MetricsCollector.snapshot()` + `ThermalMonitor` + `WidgetHealthMonitor` at session lifecycle boundary. |
| F13.3 | Session recording: tap, move, resize, navigation events with timestamps | New `SessionRecorder` service class in `:feature:diagnostics`. Ring buffer (max 10,000 events, ~500KB). Text-based timeline viewer. Recording toggle -- not always-on. |
| NF-D1 | Speed/speed limit displays informational-only disclaimer | Disclaimer text in widget Info page (WidgetInfoContent in `:feature:settings`). String resource. |
| NF-D3 | First-launch disclaimer: speed/nav data informational only, shown once | New composable in onboarding flow. `UserPreferencesRepository.hasSeenDisclaimer` flag. Dismissable. Only relevant for automotive use. |
</phase_requirements>

## Standard Stack

### Core

All from existing project dependencies -- no new library additions required.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose + Material 3 | BOM (project) | UI for all 3 feature clusters | Project standard, already wired via `dqxn.android.compose` |
| Navigation Compose | (project catalog) | `OverlayNavHost` route registration | Already in `dqxn.android.feature` convention plugin |
| Hilt + KSP | (project catalog) | DI for ViewModels + service classes | Convention plugin auto-wires |
| kotlinx-collections-immutable | (project catalog) | `ImmutableList`/`ImmutableMap` for Compose stability | Project mandate per CLAUDE.md |
| MockK + Truth + Turbine | (project catalog) | Testing | Project test stack |
| Robolectric | (project catalog) | Compose UI tests with `createAndroidComposeRule` | Pattern established in Phase 10 tests |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| compose-material-icons-extended | (project catalog) | Lock icons, status icons | Already in `:feature:settings` deps |
| kotlinx.serialization | (project catalog) | Custom theme JSON persistence | Already via `dqxn.android.feature` transitive |

### Alternatives Considered

None -- Phase 11 uses only existing project dependencies. No new libraries needed.

## Architecture Patterns

### Recommended Module Structure

```
feature/settings/src/main/kotlin/.../settings/
  theme/                    # Theme UI cluster (NEW)
    ThemeSelector.kt        # 2-page pager (built-in, custom), preview/apply lifecycle
    ThemeStudio.kt          # Custom theme CRUD (max 12)
    ThemeStudioStateHolder.kt  # Decomposed state holder (8+ color vars)
    InlineColorPicker.kt    # HSL sliders + hex editor
    GradientTypeSelector.kt # 5-type FilterChip selector
    GradientStopRow.kt      # 2-5 stop gradient editor
    ThemeSwatchRow.kt       # 7-value SwatchType enum selector
    AutoSwitchModeContent.kt # 5 modes with premium gating
    IlluminanceThresholdControl.kt  # Canvas logarithmic lux meter
    ColorConversion.kt      # Testable: colorToHsl/colorToHex/parseHexToColor
    LuxMapping.kt           # Testable: luxToPosition/positionToLux

feature/diagnostics/src/main/kotlin/.../diagnostics/
  ProviderHealthDashboard.kt     # F3.13: provider list + staleness
  ProviderDetailScreen.kt        # Connection log + retry
  DiagnosticSnapshotViewer.kt    # Browse snapshots by type
  SessionRecorder.kt             # Ring buffer event capture (F13.3)
  SessionRecorderViewer.kt       # Text-based timeline viewer
  ObservabilityDashboard.kt      # Frame times, recomps, memory
  DiagnosticsNavigation.kt       # Internal nav graph

feature/onboarding/src/main/kotlin/.../onboarding/
  AnalyticsConsentDialog.kt      # F12.5: opt-IN before collection
  FirstLaunchDisclaimer.kt       # NF-D3: informational-only disclaimer
  FirstRunFlow.kt                # Theme selection + edit mode tour
  ProgressiveTipManager.kt       # Tip state tracking + display
  PermissionRationale.kt         # F11.6/F11.7 lazy permission flow
  OnboardingViewModel.kt         # First-run orchestration
```

### Pattern 1: Caller-Managed Preview (Theme UI -- CRITICAL)

**What:** Theme preview is set BEFORE navigation, not by the destination on enter. This prevents a flash of non-previewed theme during transition animation.

**When to use:** ThemeSelector navigation, ThemeStudio open/close, clone-to-custom.

**Example (from replication advisory section 3):**
```kotlin
// In Settings composable:
onNavigateToDarkThemes = {
    state.darkTheme?.let { viewModel.setPreviewTheme(it) }  // SET FIRST
    navController.navigate(Route.ThemeSelector(isDark = true))  // THEN NAVIGATE
}
```

**Race condition fix (dual cleanup -- both required):**
```kotlin
// In ThemeSelector:
DisposableEffect(Unit) {
    onDispose { viewModel.clearPreviewTheme() }  // Covers quick dismiss race
}

// In Settings:
LaunchedEffect(Unit) {
    viewModel.clearPreviewTheme()  // Covers normal back-navigation
}
// LaunchedEffect(Unit) only runs once -- NavHost keeps Settings in composition during child pushes
```

### Pattern 2: ThemeStudio State Decomposition

**What:** ThemeStudio has 8+ mutable color/gradient state vars. These must be decomposed into a dedicated `ThemeStudioStateHolder` class (not a god-composable with `var` proliferation).

**When to use:** ThemeStudio composable.

```kotlin
class ThemeStudioStateHolder(initialTheme: DashboardThemeDefinition?) {
    var primaryTextColor by mutableStateOf(initialTheme?.primaryTextColor ?: Color.White)
    var accentColor by mutableStateOf(initialTheme?.accentColor ?: Color.Cyan)
    // ... 6 more color vars

    val isDirty: Boolean by derivedStateOf {
        primaryTextColor != savedPrimaryTextColor || accentColor != savedAccentColor || ...
    }

    fun buildCustomTheme(themeId: String): DashboardThemeDefinition { ... }
}
```

**Auto-save + live preview:**
```kotlin
LaunchedEffect(stateHolder.primaryTextColor, stateHolder.accentColor, /* all vars */) {
    if (stateHolder.isDirty) {
        onAutoSave(stateHolder.buildCustomTheme(stableThemeId))
    }
}
```

**Theme ID stability:** `remember { existingId ?: "custom_${currentTimeMillis()}" }` -- single ID generated once.

### Pattern 3: Provider Health Dashboard (Diagnostics)

**What:** Reads `ProviderStatusProvider.providerStatuses()` flow. No cross-feature dependency -- interface in `:sdk:observability`, implementation in `:feature:dashboard`.

**Architecture dependency chain:**
```
:feature:diagnostics -> :sdk:observability (ProviderStatusProvider interface)
:feature:dashboard -> :sdk:observability (implements ProviderStatusProvider)
:app -> both features (Hilt wires implementation to interface)
```

**Staleness calculation:**
```kotlin
val isStale = remember(status.lastUpdateTimestamp) {
    val elapsed = System.currentTimeMillis() - status.lastUpdateTimestamp
    elapsed > STALENESS_THRESHOLD_MS  // e.g., 10_000 for general providers
}
```

### Pattern 4: Analytics Consent Gating

**What:** All analytics events MUST be gated on consent. No Firebase events before user opts in.

**Implementation:** `AnalyticsTracker.isEnabled()` already gates `track()`. The consent dialog sets `UserPreferencesRepository.analyticsConsent`, which propagates to `AnalyticsTracker.setEnabled()`. Event call sites simply call `analyticsTracker.track(event)` -- gating is internal.

**Ordering matters (from MainSettingsViewModel):**
```kotlin
if (enabled) {
    userPreferencesRepository.setAnalyticsConsent(true)  // persist first
    analyticsTracker.setEnabled(true)                     // then enable
} else {
    analyticsTracker.setEnabled(false)                    // disable first
    userPreferencesRepository.setAnalyticsConsent(false)  // then persist
}
```

### Pattern 5: Session Recording Ring Buffer (Diagnostics)

**What:** `SessionRecorder` captures interaction events (tap, move, resize, navigation) to a ring buffer (max 10,000 events, ~500KB). Recording toggle -- NOT always-on.

**Design:**
```kotlin
@Singleton
class SessionRecorder @Inject constructor() {
    private val buffer = ArrayDeque<SessionEvent>(MAX_EVENTS)
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun record(event: SessionEvent) {
        if (!_isRecording.value) return
        synchronized(buffer) {
            if (buffer.size >= MAX_EVENTS) buffer.removeFirst()
            buffer.addLast(event)
        }
    }

    fun snapshot(): ImmutableList<SessionEvent> = synchronized(buffer) { buffer.toImmutableList() }
}

data class SessionEvent(
    val timestamp: Long,
    val type: EventType,  // TAP, MOVE, RESIZE, NAVIGATE, EDIT_MODE_ENTER/EXIT
    val details: String = "",
)
```

### Anti-Patterns to Avoid

- **God-composable ThemeStudio:** The old code had 672 lines in a single composable file with 8 inline `var` declarations. Decompose into `ThemeStudioStateHolder` + smaller composable functions.
- **Checking `currentEntry` for preview mode:** Must scan entire NavHost back stack for WidgetSettings route. See replication advisory section 1.
- **Destination-managed preview:** Never set preview theme inside ThemeSelector's `LaunchedEffect(Unit)`. Caller sets it before navigation.
- **Single cleanup for preview:** Both `DisposableEffect(onDispose)` in ThemeSelector AND `LaunchedEffect(Unit)` in Settings are required. See race condition fix.
- **Always-on session recording:** Must be toggle-gated. Ring buffer without gate wastes CPU and memory.
- **Cross-feature dependency for diagnostics:** `:feature:diagnostics` must NEVER depend on `:feature:dashboard`. Use `ProviderStatusProvider` interface from `:sdk:observability`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Color conversion (HSL/RGB/Hex) | Custom math from scratch | Extract from old codebase 413 lines + unit test thoroughly | HSL boundary conditions (0/360 hue wrap, achromatic grays) are subtle |
| Logarithmic lux mapping | Custom log math | Port `luxToPosition`/`positionToLux` from old codebase | Logarithmic scale has boundary issues at 0 lux and max range |
| Theme JSON serialization | Custom JSON builder | `ThemeJsonParser` already exists in `:core:design` | Round-trip tested, handles parse failures gracefully |
| Gradient brush creation | Manual Brush.linearGradient calls | `GradientSpec.toBrush(size)` already in `:sdk:ui` | Handles all 5 gradient types with proper stop interpolation |
| Analytics consent state machine | Custom boolean tracking | Use `MainSettingsViewModel.setAnalyticsConsent()` ordering pattern | Enable/disable ordering prevents data leaks between state changes |

**Key insight:** The color conversion utilities (`colorToHsl`/`colorToHex`/`parseHexToColor`) and logarithmic lux mapping (`luxToPosition`/`positionToLux`) are the most valuable test targets in Phase 11. Extract to pure functions in separate files for maximum testability.

## Common Pitfalls

### Pitfall 1: Theme Preview Race Condition
**What goes wrong:** Preview theme sticks after ThemeSelector is dismissed (never cleared).
**Why it happens:** `LaunchedEffect(Unit)` in Settings only runs once -- it doesn't re-run when ThemeSelector pops because NavHost keeps Settings in composition during child pushes.
**How to avoid:** Dual cleanup: `DisposableEffect(Unit) { onDispose { ClearPreviewTheme } }` in ThemeSelector AND `LaunchedEffect(Unit) { ClearPreviewTheme }` in Settings. Both are required.
**Warning signs:** After dismissing ThemeSelector, dashboard shows previewed theme instead of current theme.

### Pitfall 2: 60-Second Preview Timeout State
**What goes wrong:** Timeout fires but preview isn't cleared because timeout coroutine was cancelled by recomposition.
**Why it happens:** `LaunchedEffect` restarts on key change.
**How to avoid:** Key the timeout on the preview theme identity: `LaunchedEffect(previewTheme?.themeId) { if (previewTheme != null) { delay(60_000); clearPreview(); showToast("Theme preview ended.") } }`. Null key skips the timeout.
**Warning signs:** Preview never times out, or times out immediately on theme switch.

### Pitfall 3: Delete-While-Previewing
**What goes wrong:** User deletes the theme currently being previewed. Dashboard shows a deleted theme.
**Why it happens:** Preview reference outlives the theme.
**How to avoid:** `handleDeleteTheme` MUST revert preview to active dark/light default BEFORE dispatching the delete event. From replication advisory section 3.
**Warning signs:** Dashboard renders a theme with missing/default colors after deletion.

### Pitfall 4: HSL Color Boundary Conditions
**What goes wrong:** Hue wraps around at 360, achromatic grays (S=0) have undefined hue.
**Why it happens:** Standard HSL math treats hue as circular (0 == 360).
**How to avoid:** Clamp hue to [0, 360), handle achromatic case (S=0 => H=0 by convention). Test with: black, white, pure R/G/B, achromatic grays, hue boundaries 0/120/240/360. Round-trip accuracy: `color -> hsl -> color` within +-1/255 per channel.
**Warning signs:** Color picker shows jumps at red (0/360 boundary) or produces unexpected hues for grays.

### Pitfall 5: ProviderStatusProvider Not Implemented
**What goes wrong:** Diagnostics module can't show provider health because no Hilt binding exists.
**Why it happens:** `ProviderStatusProvider` interface is defined but not implemented yet.
**How to avoid:** Phase 11 must include a plan task that implements `ProviderStatusProvider` in `:feature:dashboard` (likely via `WidgetBindingCoordinator`) and installs it in the Hilt graph. Alternatively, verify if this was done in Phase 7 gap closures.
**Warning signs:** Dagger MissingBinding error at compile time.

### Pitfall 6: Analytics Events Before Consent
**What goes wrong:** Firebase events fire before user grants analytics consent.
**Why it happens:** `AnalyticsTracker.track()` called without checking consent.
**How to avoid:** `AnalyticsTracker` already gates internally via `isEnabled()`. Just verify with tests: (1) track called when consent=false => no event logged, (2) track called when consent=true => event logged.
**Warning signs:** Analytics events in Firebase dashboard from users who haven't opted in.

### Pitfall 7: onboarding First-Run State Corruption
**What goes wrong:** Onboarding shows repeatedly, or never shows after first install.
**Why it happens:** First-run flag checked before preference write completes.
**How to avoid:** `UserPreferencesRepository` write-then-read pattern with `stateIn` ensures consistent state. Use `hasCompletedOnboarding` boolean pref, set to `true` AFTER final onboarding step.
**Warning signs:** Onboarding appears on every launch, or skips on first launch.

### Pitfall 8: Module Dependency Violations
**What goes wrong:** `:feature:diagnostics` accidentally depends on `:feature:dashboard`.
**Why it happens:** Temptation to import coordinator types directly.
**How to avoid:** Diagnostics depends on `:sdk:observability` + `:sdk:contracts` + `:core:design` only. All provider data comes through `ProviderStatusProvider` interface. Lint boundary checker will catch violations.
**Warning signs:** Lint boundary error: "diagnostics module importing from feature:dashboard".

## Code Examples

### Color Conversion Utilities (Extract to pure testable functions)

```kotlin
// ColorConversion.kt -- pure functions, no Compose dependencies

/**
 * Converts a Compose Color to HSL (Hue, Saturation, Lightness) representation.
 * Hue: [0, 360), Saturation: [0, 1], Lightness: [0, 1]
 */
fun colorToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2f

    if (delta == 0f) return floatArrayOf(0f, 0f, lightness) // Achromatic

    val saturation = if (lightness < 0.5f) delta / (max + min) else delta / (2f - max - min)
    val hue = when (max) {
        r -> ((g - b) / delta + (if (g < b) 6f else 0f)) * 60f
        g -> ((b - r) / delta + 2f) * 60f
        else -> ((r - g) / delta + 4f) * 60f
    }
    return floatArrayOf(hue % 360f, saturation, lightness)
}

/**
 * Converts a Compose Color to hex string (#AARRGGBB format).
 */
fun colorToHex(color: Color): String {
    val a = (color.alpha * 255).toInt()
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X%02X", a, r, g, b)
}

/**
 * Parses hex string (#RRGGBB or #AARRGGBB) to Compose Color.
 * Returns null for invalid input.
 */
fun parseHexToColor(hex: String): Color? {
    val stripped = hex.removePrefix("#")
    return try {
        when (stripped.length) {
            6 -> Color((0xFF000000L or stripped.toLong(16)).toInt())
            8 -> Color(stripped.toLong(16).toInt())
            else -> null
        }
    } catch (_: NumberFormatException) { null }
}
```

### Theme Selector Ordering Logic

```kotlin
// ThemeSelector.kt
val sortedThemes: ImmutableList<DashboardThemeDefinition> = remember(allThemes) {
    val free = allThemes.filter { it.requiredAnyEntitlement.isNullOrEmpty() && it.packId == null }
    val custom = allThemes.filter { it.themeId.startsWith("custom_") }
    val premium = allThemes.filter {
        !it.requiredAnyEntitlement.isNullOrEmpty() && !it.themeId.startsWith("custom_")
    }
    (free + custom + premium).toImmutableList()
}
```

### Preview Timeout (60s)

```kotlin
// Inside ThemeSelector
val previewTheme = themeState.previewTheme
LaunchedEffect(previewTheme?.themeId) {
    if (previewTheme != null) {
        delay(60_000L)
        onClearPreview()
        onShowToast("Theme preview ended.")
    }
}
```

### Provider Health Dashboard Entry

```kotlin
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val providerStatusProvider: ProviderStatusProvider,
    private val connectionEventStore: ConnectionEventStore,
    private val diagnosticSnapshotCapture: DiagnosticSnapshotCapture,
    private val metricsCollector: MetricsCollector,
) : ViewModel() {

    val providerStatuses: StateFlow<Map<String, ProviderStatus>> =
        providerStatusProvider.providerStatuses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val connectionEvents: StateFlow<ImmutableList<ConnectionEvent>> =
        connectionEventStore.events
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())
}
```

### Onboarding Progressive Tip

```kotlin
@Composable
fun ProgressiveTip(
    tipKey: String,
    message: String,
    preferencesRepository: UserPreferencesRepository,
) {
    var hasSeenTip by remember { mutableStateOf(true) } // Assume seen until checked

    LaunchedEffect(tipKey) {
        hasSeenTip = preferencesRepository.hasSeenTip(tipKey).first()
    }

    AnimatedVisibility(visible = !hasSeenTip, enter = DashboardMotion.expandEnter, exit = DashboardMotion.expandExit) {
        TipCard(message = message, onDismiss = {
            hasSeenTip = true
            scope.launch { preferencesRepository.markTipSeen(tipKey) }
        })
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| God-composable ThemeStudio (672 lines) | Decomposed ThemeStudioStateHolder + smaller composables | New arch | Testable state, lower recomposition surface |
| Hard-dep on pack sensors for auto-switch | ThemeAutoSwitchEngine with injected inputs | Phase 5 | Clean pack isolation |
| No onboarding flow | Full first-run flow with consent + tips | New (no old equiv) | PDPA/GDPR compliance from launch |
| No diagnostics module | `:feature:diagnostics` with provider health + snapshots | New (no old equiv) | Debug observability |
| No analytics consent | Opt-IN consent dialog, gated collection | New (F12.5) | PDPA/GDPR compliance |

**Deprecated/outdated:**
- Old codebase had no onboarding, no diagnostics, no analytics consent -- all three are greenfield
- Old ThemeStudio's `focusSave/Restore` code was dead (permanently disabled) -- do NOT replicate

## Open Questions

1. **ProviderStatusProvider implementation location**
   - What we know: Interface defined in `:sdk:observability`. Phase 11 doc says implementation by `WidgetBindingCoordinator` in `:feature:dashboard`.
   - What's unclear: Whether Phase 7 already wired this or it's still TODO.
   - Recommendation: Check WidgetBindingCoordinator for implementation. If missing, add a plan task to implement + install in Hilt graph. LOW risk -- the interface is simple.

2. **Custom theme persistence format**
   - What we know: `ThemeJsonParser.parse()` can read JSON themes. `BuiltInThemes.loadBundledThemes()` reads from assets.
   - What's unclear: Where custom themes are stored (internal files? DataStore? SharedPrefs?). Phase 11 doc says "max 12 custom themes" but doesn't specify storage.
   - Recommendation: Store as JSON files in app internal storage (`context.filesDir/custom_themes/`). Simple file I/O, not DataStore (themes are self-contained JSON documents, not key-value pairs). `ThemeJsonParser` already handles parse/serialize.

3. **Onboarding tip delivery mechanism**
   - What we know: Tips tracked via Preferences DataStore flags. 4 tip types defined (F11.1).
   - What's unclear: How tips integrate with the existing `DashboardScreen` composition without heavy coupling.
   - Recommendation: `ProgressiveTipManager` as a `@Singleton` class injected into relevant composables via `LocalComposition` or direct injection. Tips are stateless UI components that query the flag.

4. **Session recording scope in diagnostics**
   - What we know: Phase 11 doc says "highest-risk novel component -- keep scope minimal for V1: event capture + text-based timeline."
   - What's unclear: How recording events from `:feature:dashboard` reach `:feature:diagnostics` without direct dependency.
   - Recommendation: `SessionRecorder` as `@Singleton` in `:sdk:observability` (or a shared module). Dashboard records events to it; diagnostics reads from it. Interface in `:sdk:observability`, implementation in `:app` or a shared location.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit5 (unit), JUnit4 + Robolectric (Compose UI), MockK + Truth |
| Config file | Convention plugin `dqxn.android.test` auto-configures |
| Quick run command | `./gradlew :feature:settings:testDebugUnitTest --console=plain` |
| Full suite command | `./gradlew :feature:settings:testDebugUnitTest :feature:diagnostics:testDebugUnitTest :feature:onboarding:testDebugUnitTest --console=plain` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| F3.13 | Provider health renders provider list with staleness | Compose UI | `./gradlew :feature:diagnostics:testDebugUnitTest --tests "*.ProviderHealthDashboardTest"` | No -- Wave 0 |
| F4.6 | Theme preview timeout (60s -> revert) | Unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.ThemeSelectorTest"` | No -- Wave 0 |
| F4.7 | ThemeStudio isDirty derivation, auto-save, max 12 limit | Unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.ThemeStudioStateHolderTest"` | No -- Wave 0 |
| F4.8 | GradientStopRow: min 2, max 5, position clamped | Compose UI | `./gradlew :feature:settings:testDebugUnitTest --tests "*.GradientStopRowTest"` | No -- Wave 0 |
| F4.9 | Preview-regardless-of-entitlement, gate-at-persistence | Unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.ThemeSelectorTest"` | No -- Wave 0 |
| F4.12 | Clone built-in to custom | Unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.ThemeSelectorTest"` | No -- Wave 0 |
| F4.13 | Free-first ordering | Unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.ThemeSelectorTest"` | No -- Wave 0 |
| F7.6 | Connection event log in diagnostics | Compose UI | `./gradlew :feature:diagnostics:testDebugUnitTest --tests "*.ProviderDetailScreenTest"` | No -- Wave 0 |
| F11.1 | Progressive tips shown once per flag | Unit | `./gradlew :feature:onboarding:testDebugUnitTest --tests "*.ProgressiveTipManagerTest"` | No -- Wave 0 |
| F11.2 | Theme selection on first launch -- free first | Compose UI | `./gradlew :feature:onboarding:testDebugUnitTest --tests "*.FirstRunFlowTest"` | No -- Wave 0 |
| F11.5 | Default preset: clock + battery + date only | Unit | `./gradlew :data:testDebugUnitTest --tests "*.PresetLoaderTest"` | Verify existing |
| F12.2 | widget_add, theme_change, upsell_impression events fire | Unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.AnalyticsEventCallSiteTest"` | No -- Wave 0 |
| F12.5 | Analytics consent dialog blocks events | Unit + Compose UI | `./gradlew :feature:onboarding:testDebugUnitTest --tests "*.AnalyticsConsentDialogTest"` | No -- Wave 0 |
| F12.6 | trigger_source param on upsell events | Unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.AnalyticsEventCallSiteTest"` | No -- Wave 0 |
| F12.7 | Session end includes quality metrics | Unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.AnalyticsEventCallSiteTest"` | No -- Wave 0 |
| F13.3 | Session recording ring buffer overflow, timeline | Unit | `./gradlew :feature:diagnostics:testDebugUnitTest --tests "*.SessionRecorderTest"` | No -- Wave 0 |
| NF-D1 | Speed disclaimer on widget info page | String resource verification | Compile check | N/A |
| NF-D3 | First-launch disclaimer shown once | Compose UI | `./gradlew :feature:onboarding:testDebugUnitTest --tests "*.FirstLaunchDisclaimerTest"` | No -- Wave 0 |
| Color conversion | colorToHsl/colorToHex/parseHexToColor | Unit (JUnit5) | `./gradlew :feature:settings:testDebugUnitTest --tests "*.ColorConversionTest"` | No -- Wave 0 |
| Lux mapping | luxToPosition/positionToLux inverses | Unit (JUnit5) | `./gradlew :feature:settings:testDebugUnitTest --tests "*.LuxMappingTest"` | No -- Wave 0 |

### Nyquist Sampling Rate
- **Minimum sample interval:** After every committed task -> run: `./gradlew :feature:settings:testDebugUnitTest :feature:diagnostics:testDebugUnitTest :feature:onboarding:testDebugUnitTest --console=plain`
- **Full suite trigger:** Before merging final task of any plan wave
- **Phase-complete gate:** Full suite green before verify
- **Estimated feedback latency per task:** ~20-30 seconds

### Wave 0 Gaps (must be created before implementation)

- [ ] `feature/settings/src/test/.../theme/ColorConversionTest.kt` -- covers F4.7, F4.8 color math
- [ ] `feature/settings/src/test/.../theme/LuxMappingTest.kt` -- covers IlluminanceThresholdControl math
- [ ] `feature/settings/src/test/.../theme/ThemeStudioStateHolderTest.kt` -- covers F4.7 isDirty, auto-save, max 12
- [ ] `feature/settings/src/test/.../theme/ThemeSelectorTest.kt` -- covers F4.6, F4.9, F4.12, F4.13
- [ ] `feature/settings/src/test/.../theme/GradientStopRowTest.kt` -- covers F4.8 stop boundaries
- [ ] `feature/diagnostics/build.gradle.kts` -- add dependencies (`:core:design`, `:data`, `:sdk:analytics`)
- [ ] `feature/diagnostics/src/test/.../ProviderHealthDashboardTest.kt` -- covers F3.13
- [ ] `feature/diagnostics/src/test/.../SessionRecorderTest.kt` -- covers F13.3 ring buffer
- [ ] `feature/onboarding/build.gradle.kts` -- add dependencies (`:data`, `:core:design`)
- [ ] `feature/onboarding/src/test/.../AnalyticsConsentDialogTest.kt` -- covers F12.5
- [ ] `feature/onboarding/src/test/.../ProgressiveTipManagerTest.kt` -- covers F11.1
- [ ] `feature/onboarding/src/test/.../FirstRunFlowTest.kt` -- covers F11.2

## OverlayNavHost Route Completion Analysis

Phase 10 established 4 routes (WidgetPicker, Settings, WidgetSettings, Setup). Phase 11 adds 3 more:

| Route | Phase | Destination | Overlay Type | Transition |
|-------|-------|-------------|-------------|------------|
| Empty | 7 | No overlay | N/A | N/A |
| WidgetPicker | 10 | Widget selection grid | Hub | hubEnter/hubExit |
| Settings | 10 | Main settings | Preview | previewEnter/previewExit |
| WidgetSettings | 10 | Per-widget settings | Preview | ExitTransition.None (stays under hubs) |
| Setup | 10 | Setup wizard | Hub | hubEnter/hubExit |
| **ThemeSelector** | **11** | Theme browser + preview | **Preview** | **previewEnter/previewExit; popEnter: fadeIn(150ms) NOT previewEnter** |
| **Diagnostics** | **11** | Provider health + snapshots | **Hub** | **hubEnter/hubExit** |
| **Onboarding** | **11** | First-run flow | **Hub** | **hubEnter/hubExit** |

**Critical from replication advisory section 4:** ThemeModeSelector/ThemeSelector/ThemeEditor popEnter must use `fadeIn(150ms)` -- NOT previewEnter. This avoids a double-slide when returning from a theme sub-screen.

**Source-varying transitions for Settings:**
- Exit to ThemeSelector/ThemeEditor: `fadeOut(100ms)` (not full sheetExit)
- popEnter from ThemeSelector/ThemeEditor: `fadeIn(150ms)` (not previewEnter)
- Exit to Diagnostics/Onboarding (hub routes): `ExitTransition.None`
- popEnter from Diagnostics/Onboarding: `EnterTransition.None`

## Module Dependency Analysis

### `:feature:diagnostics` (new build.gradle.kts)

```kotlin
plugins { id("dqxn.android.feature") }
android { namespace = "app.dqxn.android.feature.diagnostics" }
dependencies {
    implementation(project(":core:design"))     // DashboardMotion, tokens, typography
    implementation(project(":data"))            // ConnectionEventStore
    implementation(project(":sdk:analytics"))   // AnalyticsTracker (for consent-gated events)
    // :sdk:contracts, :sdk:common, :sdk:ui, :sdk:observability auto-wired by dqxn.android.feature
}
```

Per ARCHITECTURE.md: `:feature:diagnostics` -> `:sdk:contracts, :sdk:common, :sdk:observability, :sdk:analytics, :sdk:ui, :core:design`.

### `:feature:onboarding` (new build.gradle.kts)

```kotlin
plugins { id("dqxn.android.feature") }
android { namespace = "app.dqxn.android.feature.onboarding" }
dependencies {
    implementation(project(":data"))            // UserPreferencesRepository
    implementation(project(":core:design"))     // Tokens, motion, OverlayScaffold reuse
    implementation(project(":sdk:analytics"))   // Analytics consent wiring
}
```

Per ARCHITECTURE.md: `:feature:onboarding` -> `:sdk:*, :data`.

### `:feature:settings` (existing -- additions needed)

No new dependency additions needed. Already has `:core:design`, `:data`, `:sdk:analytics`, material-icons-extended. Theme UI composables live here under a `theme/` subpackage.

## Sources

### Primary (HIGH confidence)
- Codebase analysis: 22 source files read directly from current Phase 1-10 implementation
- `/Users/ohm/Workspace/dqxn/.planning/migration/phase-11.md` -- Phase 11 detailed design
- `/Users/ohm/Workspace/dqxn/.planning/migration/replication-advisory.md` -- Sections 3 and 4
- `/Users/ohm/Workspace/dqxn/.planning/REQUIREMENTS.md` -- F3.13, F4.6-F4.13, F7.6, F11.1-F11.7, F12.2-F12.7, F13.3, NF-D1, NF-D3
- `/Users/ohm/Workspace/dqxn/.planning/ARCHITECTURE.md` -- Module dependency rules

### Secondary (MEDIUM confidence)
- Phase 11 doc's old codebase line counts (~2200 lines port + ~2000 new) -- based on old source analysis, not verified against current old codebase files

### Tertiary (LOW confidence)
- Custom theme persistence approach (JSON files in internal storage) -- recommendation, not verified against any existing pattern in the codebase. May need investigation during planning.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in project, no new additions
- Architecture: HIGH -- patterns directly from replication advisory + existing codebase
- Pitfalls: HIGH -- 8 pitfalls derived from replication advisory race conditions + existing test patterns
- Module dependencies: HIGH -- verified against ARCHITECTURE.md and convention plugin source
- Session recording design: MEDIUM -- novel component, no prior art in codebase

**Research date:** 2026-02-25
**Valid until:** 2026-03-27 (stable -- no external dependency changes expected)
