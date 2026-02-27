# Phase 14: UI Visual & Interactive Parity - Research

**Researched:** 2026-02-27
**Domain:** Compose UI visual/animation/interaction restoration
**Confidence:** HIGH

## Summary

Phase 14 restores UI visual and interactive parity with the old codebase after migration to the new architecture. Research compared actual old source files (`../dqxn.old/android/`) against the new codebase to identify exact deltas.

The design token layer (DashboardMotion, DashboardSpacing, DashboardTypography, TextEmphasis, CardSize) has already been ported verbatim to `:core:design` -- no work needed there. The deltas fall into 8 distinct areas: (1) widget focus overlay toolbar, (2) bottom bar visual/behavioral regressions, (3) drag lift effects, (4) widget status overlay theme-awareness, (5) PreviewOverlay dashboard-peek pattern, (6) splash screen theme, (7) confirmation dialog routes, (8) edit mode corner brackets. Each area is scoped, self-contained, and can be planned independently.

**Primary recommendation:** Decompose into 8-10 focused plans ordered by visual impact and dependency. Focus overlay toolbar (F1.8/F2.18) and bottom bar auto-hide (F1.9) are highest impact. Design tokens are already ported -- plans reference `:core:design` directly.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
#### Old Codebase as Reference
- Use `.planning/oldcodebase/` mapping docs as INDEX into actual source at `../dqxn.old/android/`
- Key mapping files: `feature-dashboard.md` (dashboard UI), `core-libraries.md` (SDK/design tokens), `packs.md` (widget renderers)
- Do NOT copy patterns that conflict with CLAUDE.md -- new architecture diverges on state decomposition, Proto DataStore, pack isolation, canvas model
- Extract exact values (timing curves, dp values, color tokens, animation specs) from old source

#### Context Pressure Management
- Each plan must be executable with minimal context -- self-contained with all necessary values, file paths, and specs
- No plan should require reading the entire old codebase -- each plan gets only the specific old-codebase references it needs
- Prefer many small focused plans over fewer large ones

### Claude's Discretion
- How to decompose UI areas into plans (wave structure, grouping)
- Technical approach for each fix (as long as it respects new architecture constraints)
- Test strategy per area (must be automated, zero manual tests)
- Whether to create shared design token infrastructure vs. inline fixes

### Deferred Ideas (OUT OF SCOPE)
- F4.11 (spacing tokens, typography scale in Theme Studio) -- explicitly deferred in requirements
- F1.19 (HUD mirror mode) -- explicitly deferred
- F1.31 (profile auto-switching) -- explicitly deferred
- Full design system token extraction as reusable library -- only extract what's needed for parity
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F1.8 | Widget focus state (overlay toolbar: delete/settings). No translate or scale -- follow old codebase | Delta B: Focus overlay toolbar completely missing. Old source has corner brackets + action buttons (delete/settings) positioned above widget. New EditState has `focusedWidgetId` field but DashboardGrid/WidgetSlot don't render any focus UI. |
| F1.9 | Auto-hide bottom bar: Settings button (always), profile icons (when 2+), Add Widget (edit mode). 76dp touch targets. Auto-hide after 3s, tap to reveal. Floats over canvas | Delta A: Auto-hide timer logic missing from DashboardScreen (hardcoded `isVisible = true`). Old source used `lastInteractionTime` + LaunchedEffect 3s delay. Button styling wrong (IconButton vs FAB, no accent color). |
| F1.11 | Edit mode visual feedback (wiggle animation, corner brackets) | Delta H: Wiggle animation correctly ported. Corner brackets missing -- old draws Canvas arcs with pulsing stroke 3-6dp. New only has scale-based "bracket pulse" (1.0-1.02f) which is wrong. |
| F1.20 | Grid snapping: widget snaps to nearest 2-unit boundary on drop. Visual grid overlay during drag. Haptic tick on snap | Partially implemented via WidgetGestureHandler. Visual grid overlay during drag not implemented. Haptic tick exists in DashboardHaptics. |
| F1.21 | Widget add/remove animations: fade+scale-in on add (spring via graphicsLayer), fade+scale-out on delete | Already correctly implemented in DashboardGrid via AnimatedVisibility with spring(StiffnessMediumLow). No delta. |
| F1.29 | Profile switching via horizontal swipe and tap on profile icon in bottom bar | ProfilePageTransition exists. Bottom bar profile icons exist. Swipe gesture and page transition mechanics need visual verification -- profile icon sizing and accent coloring may be off. |
| F2.5 | WidgetStatusCache -- overlays for entitlement, setup, connection issues | Delta D: Overlays exist but are generic. Old had per-type themed composables with accent color, corner radius respect, varied icon sizes, corner positioning for Disconnected state. |
| F2.18 | Focus interaction model: focused widget shows overlay toolbar (delete, settings). Tap content area unfocuses. Interactive actions only in non-focused, non-edit mode | Delta B: `isInteractionAllowed` gating exists in WidgetSlot. Focus overlay toolbar (the visual part) not rendered. Tap-to-focus/unfocus gestures not wired. |
| F3.14 | Provider setup failure UX: failed setup shows inline error with retry. Dismissed wizard -> "Setup Required" overlay with tap-to-setup. Permanently denied -> system settings | WidgetStatusOverlay renders SetupRequired but has no tap handler. Old had clickable overlays routing to widget info / setup. |
| F4.6 | Theme preview: live preview before committing, reverts on cancel. Preview times out after 60s with toast | Theme preview infrastructure exists in ThemeCoordinator (previewTheme StateFlow). Preview timeout (60s) needs verification -- may be missing. Revert-on-cancel via PreviewTheme(null) exists in OverlayNavHost. |
| F11.7 | Permission flow: "Setup Required" overlay -> tap opens setup wizard -> wizard requests permissions -> granted: bind; denied: show "Permission needed" with system settings link | SetupSheet exists. Overlay tap-to-setup not wired (see F3.14). Permission flow within SetupSheet needs verification. |
</phase_requirements>

## Standard Stack

### Core (already in project)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Jetpack Compose + Material 3 | BOM 2025.06.01 | UI framework | In use |
| Compose Animation | via BOM | Spring, tween, infiniteRepeatable, AnimatedVisibility | In use |
| AndroidX SplashScreen | 1.2.0-alpha02 | Splash screen compat | In use (incomplete config) |
| kotlinx-collections-immutable | 0.3.8 | ImmutableList/ImmutableMap everywhere | In use |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| DashboardMotion | n/a (`:core:design`) | All animation specs (springs, tweens, transitions) | Every animation in this phase |
| DashboardSpacing | n/a (`:core:design`) | Spacing tokens (SpaceXXS=4dp through SpaceXXL=48dp) | Padding, margins |
| DashboardTypography | n/a (`:core:design`) | Typography scale (title=17sp, sectionHeader=13sp, etc.) | Text styling |
| TextEmphasis | n/a (`:core:design`) | Alpha values (High=1.0f, Medium=0.7f, Disabled=0.4f) | Content emphasis |
| CardSize | n/a (`:core:design`) | Corner radius categories (SMALL=8dp, MEDIUM=12dp, LARGE=16dp) | Widget corners |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Canvas-drawn corner brackets | Border modifier | Canvas matches old source exactly and allows animated stroke width |
| Custom FAB composable | Material3 FAB | Custom gives exact old styling (accent color, shape) without fighting M3 defaults |
| PreviewOverlay in OverlayNavHost | ModalBottomSheet | PreviewOverlay preserves dashboard-peek; BottomSheet doesn't support transparent zone |

## Architecture Patterns

### Delta Map: Old vs New

```
AREA                        OLD SOURCE LOCATION                                    NEW SOURCE LOCATION                                          STATUS
Design tokens               feature/dashboard/DashboardThemeExtensions.kt          core/design/token/*.kt                                       PORTED
Animation specs             feature/dashboard/DashboardAnimations.kt               core/design/motion/DashboardMotion.kt                        PORTED
Bottom bar (visual)         feature/dashboard/ui/DashboardButtonBar.kt             feature/dashboard/ui/DashboardButtonBar.kt                   REGRESSED
Bottom bar (auto-hide)      feature/dashboard/DashboardScreen.kt                   feature/dashboard/DashboardScreen.kt                         MISSING
Focus overlay toolbar       feature/dashboard/grid/DashboardGrid.kt                feature/dashboard/grid/DashboardGrid.kt                      MISSING
Corner brackets (edit)      feature/dashboard/grid/DashboardGrid.kt                feature/dashboard/grid/DashboardGrid.kt                      MISSING
Drag lift effect            feature/dashboard/grid/DashboardGrid.kt                feature/dashboard/grid/DashboardGrid.kt                      MISSING
Widget status overlays      core/widget-primitives/SetupOverlays.kt                feature/dashboard/ui/WidgetStatusOverlay.kt                  REGRESSED
PreviewOverlay (sheets)     app/navigation/OverlayNavHost.kt                       feature/dashboard/layer/OverlayNavHost.kt                    MISSING
Splash theme                app/res/values/themes.xml + values-v31/themes.xml      app/res/values/themes.xml                                   MISSING
Confirmation dialogs        app/navigation/OverlayNavHost.kt                       (not implemented)                                            MISSING
```

### Pattern 1: Focus Overlay Toolbar (F1.8, F2.18)

**What:** When a widget is focused in edit mode, corner brackets + action buttons (delete/settings) appear above it.

**Old implementation (DashboardGrid.kt):**
- `focusedWidgetId` tracked in grid state
- Canvas-drawn curved arcs for corner brackets
- Bracket stroke pulsing: 3dp to 6dp, tween(800ms, FastOutSlowInEasing), RepeatMode.Reverse
- Action buttons (delete + settings) positioned in Row above widget, 16dp spacing
- Action button press scale: 0.85f, spring(DampingRatioMediumBouncy, StiffnessMedium)
- `settingsAlpha`: 0f for non-focused widgets during settings peek, tween(300ms); snap() for the editing widget
- `focusScale` / `focusTranslationX/Y`: spring(StiffnessLow, DampingRatioNoBouncy)

**New architecture adaptation:**
- `EditState.focusedWidgetId` already exists in `EditModeCoordinator`
- `EditModeCoordinator.isInteractionAllowed(widgetId)` already exists
- Focus UI rendering needs to be added to DashboardGrid (per-widget graphicsLayer + Canvas brackets + action Row)
- Tap-to-focus/unfocus needs to be wired in WidgetGestureHandler
- `settingsAlpha` needed for settings peek (F1.8 interaction with OverlayNavHost WidgetSettings route)

### Pattern 2: Bottom Bar Auto-Hide (F1.9)

**What:** Bar auto-hides after 3 seconds of inactivity, tap to reveal, edit mode forces visible.

**Old implementation (DashboardScreen.kt):**
```kotlin
val autoHideDelayMs = 3000L
var isBarVisible by remember { mutableStateOf(true) }
var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

LaunchedEffect(isBarVisible, editMode) {
  if (isBarVisible && !editMode) {
    delay(autoHideDelayMs)
    if (System.currentTimeMillis() - lastInteractionTime >= autoHideDelayMs) {
      isBarVisible = false
    }
  }
}
// Edit mode forces visible
LaunchedEffect(editMode) {
  if (editMode) isBarVisible = true
}
// Bar hidden during widget manipulation
LaunchedEffect(isManipulating) {
  if (isManipulating) isBarVisible = false
}
```

**New adaptation:**
- `isVisible` is currently hardcoded `true` in DashboardScreen.kt line 174
- Add `isBarVisible` + `lastInteractionTime` state
- Wire `onInteraction` callback to reset timer
- `editState.isEditMode` forces visible
- Drag/resize state hides bar

### Pattern 3: PreviewOverlay Dashboard-Peek (OverlayNavHost sheets)

**What:** Overlay sheets leave a transparent tap-to-dismiss zone at top, showing dashboard behind.

**Old implementation (OverlayNavHost.kt):**
```kotlin
@Composable
fun PreviewOverlay(
  previewFraction: Float, // fraction of screen height showing dashboard
  onDismiss: () -> Unit,
  content: @Composable () -> Unit,
) {
  Box(Modifier.fillMaxSize()) {
    // Transparent zone: tap to dismiss
    Box(
      Modifier
        .fillMaxSize()
        .clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
          onClick = onDismiss,
        )
    )
    // Content anchored to bottom
    Box(
      Modifier
        .fillMaxHeight(1f - previewFraction)
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
    ) {
      content()
    }
  }
}
```

**Preview fractions:**
- Settings: 0.15f (85% of screen)
- WidgetSettings: 0.38f (62% of screen)
- ThemeSelector: 0.15f (85% of screen, changed from 0.2f)
- ThemeStudio: 0.15f (85% of screen, changed from 0.2f)

**New adaptation:**
- Create `PreviewOverlay` composable in `:feature:dashboard` layer package
- Wrap Settings, WidgetSettings, ThemeSelector, ThemeStudio routes
- Tap-to-dismiss zone calls `navController.popBackStack(EmptyRoute, inclusive = false)`

### Pattern 4: Widget Status Overlays (F2.5, F3.14)

**What:** Per-status themed overlays with accent color, corner radius, and per-type differentiation.

**Old implementation (SetupOverlays.kt in :core:widget-primitives):**
```
Status             Scrim    Icon                   IconSize  Position    Clickable
SetupRequired      60%      themed settings icon   32dp      centered    yes -> widget info
Disconnected       15%      corner block icon      20dp      corner      yes -> widget info
ConnectionError    30%      Warning icon           32dp      centered    no
EntitlementRevoked 60%      Star icon              32dp      centered    yes -> widget info
ProviderMissing    60%      Warning icon           32dp      centered    no
```

All overlays used `theme.accentColor` for icon tint and respected widget corner radius via `RoundedCornerShape(percent = cornerRadiusPercent / 2)`.

**New state:**
- All icons are Material filled, 24dp, white tint (no accent)
- No corner radius respect (fillMaxSize with no clipping)
- All states centered (Disconnected should be corner-positioned)
- No tap handlers (SetupRequired and EntitlementRevoked should be tappable)

### Pattern 5: Splash Screen Theme

**What:** Themed splash screen with dark background and app icon.

**Old themes.xml:**
```xml
<style name="Theme.App.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">#0f172a</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_logo_letterform</item>
    <item name="postSplashScreenTheme">@style/Theme.App</item>
</style>
```

**Old values-v31/themes.xml:**
```xml
<style name="Theme.App.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">#0f172a</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_logo_letterform</item>
    <item name="postSplashScreenTheme">@style/Theme.App</item>
    <item name="android:windowSplashScreenBackground">#0f172a</item>
    <item name="android:windowSplashScreenAnimatedIcon">@drawable/ic_logo_letterform</item>
    <item name="android:windowSplashScreenBehavior">@integer/icon_preferred</item>
</style>
```

**New state:**
- `installSplashScreen()` called in MainActivity but no `Theme.App.Starting` style exists
- `Theme.Dqxn.NoActionBar` parents `Theme.DeviceDefault.NoActionBar` (wrong parent for splash)
- No `values-v31` directory
- Missing: `ic_logo_letterform` drawable (needs verification)
- Need: `Theme.App.Starting` in themes.xml + AndroidManifest activity theme, post-splash theme swap

### Anti-Patterns to Avoid

- **Bracket pulse as scale instead of stroke width:** New code animates `scaleX/scaleY` 1.0-1.02f for "bracket pulse". Old code animated Canvas stroke width 3f-6f. Scale animation is visually wrong and causes layout shift. Replace with Canvas-drawn brackets with animated stroke.
- **Focus via Modifier.offset instead of graphicsLayer:** Per CLAUDE.md, all animation positioning must use graphicsLayer. Focus scale/translation must be graphicsLayer properties.
- **Auto-hide timer in viewModel:** Timer is UI-only state (visibility toggle). Keep in Composable via `remember` + `LaunchedEffect`, NOT in coordinator.
- **PreviewOverlay as ModalBottomSheet:** M3 BottomSheet handles its own gestures and scrim. PreviewOverlay needs transparent non-scrim zone for dashboard peek. Custom composable required.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Animation specs | Custom spring/tween values | `DashboardMotion.*` from `:core:design` | Already ported, consistent, DRY |
| Spacing values | Hardcoded dp values | `DashboardSpacing.*` from `:core:design` | Already ported with all old values |
| Typography | Custom TextStyle creation | `DashboardTypography.*` from `:core:design` | Already ported with all old scales |
| Splash screen | Custom Activity theme | AndroidX SplashScreen compat | Already in deps, handles API 31+ and compat |
| Edge-to-edge | Manual insets handling | `enableEdgeToEdge()` | Already called in MainActivity |

**Key insight:** Design tokens are already extracted and ported. Plans should import and USE them, not re-derive values. Every dp, sp, alpha, spring constant, and tween duration referenced in this research exists in `:core:design`.

## Common Pitfalls

### Pitfall 1: Bracket Pulse Misimplementation
**What goes wrong:** Implementing bracket visual as scale animation instead of Canvas stroke width animation produces wrong visual (widget content scales) and breaks layout.
**Why it happens:** The new code already has a "bracketScale" animation that animates the entire widget's scaleX/Y. This is not what the old code did.
**How to avoid:** Bracket pulse must be Canvas-drawn arcs with `drawArc` or `drawPath`, animating the `strokeWidth` parameter from 3f.dp to 6f.dp. The widget itself should NOT scale for bracket pulse.
**Warning signs:** Widget content appearing to breathe/zoom during edit mode.

### Pitfall 2: Focus Overlay Toolbar Z-ordering
**What goes wrong:** Action buttons (delete/settings) render behind adjacent widgets.
**Why it happens:** Widgets have explicit `zIndex` in the grid layout. Action buttons positioned above a widget can clip under a higher-z neighbor.
**How to avoid:** Action button Row must have `zIndex` higher than any widget. In the Layout MeasurePolicy, place the focus toolbar overlay last (highest z). Or use `Modifier.zIndex(Float.MAX_VALUE)` on the overlay.
**Warning signs:** Buttons disappearing when widgets overlap.

### Pitfall 3: Auto-Hide Timer Race
**What goes wrong:** Bar hides immediately after interaction because elapsed time check uses stale `lastInteractionTime`.
**Why it happens:** `LaunchedEffect` captures the interaction time at launch, not at check.
**How to avoid:** Check `System.currentTimeMillis() - lastInteractionTime >= autoHideDelayMs` AFTER the delay, not before. Store interaction time in `mutableStateOf` and read it inside the coroutine.
**Warning signs:** Bar flickering or hiding mid-interaction.

### Pitfall 4: PreviewOverlay Gesture Conflict
**What goes wrong:** Tap-to-dismiss transparent zone intercepts drag gestures meant for dashboard widgets.
**Why it happens:** The transparent zone covers the visible dashboard peek area where widgets are rendered.
**How to avoid:** Use `clickable` (tap only), not `pointerInput` (which would intercept drags). The old code used `clickable(indication = null)` specifically.
**Warning signs:** Unable to interact with widgets visible in the peek zone.

### Pitfall 5: Splash Theme Not Applied
**What goes wrong:** Splash screen shows white/system default instead of branded dark background.
**Why it happens:** `installSplashScreen()` works but the activity's XML theme must be `Theme.App.Starting` (splash theme), which then transitions to the app theme via `postSplashScreenTheme`.
**How to avoid:** AndroidManifest must reference `@style/Theme.App.Starting`. The `postSplashScreenTheme` item must reference the actual app theme (`@style/Theme.Dqxn.NoActionBar`).
**Warning signs:** White flash on cold start.

### Pitfall 6: Widget Status Overlay Corner Radius
**What goes wrong:** Status overlay doesn't clip to widget corners, producing visible square overlay corners on rounded widgets.
**Why it happens:** Overlay uses `fillMaxSize()` without inheriting the widget's corner radius.
**How to avoid:** Pass corner radius (from widget style or CardSize) to WidgetStatusOverlay. Apply `clip(RoundedCornerShape(...))` to the overlay Box.
**Warning signs:** Square black corners visible on rounded widget overlays.

## Code Examples

### Corner Brackets Drawing (from old DashboardGrid.kt)
```kotlin
// Corner brackets via Canvas with animated stroke width
val bracketStrokeWidth by infiniteTransition.animateFloat(
  initialValue = 3f,
  targetValue = 6f,
  animationSpec = infiniteRepeatable(
    animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
    repeatMode = RepeatMode.Reverse,
  ),
)

Canvas(modifier = Modifier.matchParentSize()) {
  val strokePx = bracketStrokeWidth.dp.toPx()
  val bracketLength = 16.dp.toPx()
  val color = /* theme accent or white */

  // Top-left corner
  drawLine(color, Offset(0f, strokePx/2), Offset(bracketLength, strokePx/2), strokePx)
  drawLine(color, Offset(strokePx/2, 0f), Offset(strokePx/2, bracketLength), strokePx)

  // Top-right corner
  drawLine(color, Offset(size.width - bracketLength, strokePx/2), Offset(size.width, strokePx/2), strokePx)
  drawLine(color, Offset(size.width - strokePx/2, 0f), Offset(size.width - strokePx/2, bracketLength), strokePx)

  // Bottom-left, Bottom-right similarly...
}
```

### Auto-Hide Bar Timer (from old DashboardScreen.kt)
```kotlin
val autoHideDelayMs = 3000L
var isBarVisible by remember { mutableStateOf(true) }
var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

// Auto-hide timer
LaunchedEffect(isBarVisible, editState.isEditMode) {
  if (isBarVisible && !editState.isEditMode) {
    delay(autoHideDelayMs)
    if (System.currentTimeMillis() - lastInteractionTime >= autoHideDelayMs) {
      isBarVisible = false
    }
  }
}

// Edit mode forces visible
LaunchedEffect(editState.isEditMode) {
  if (editState.isEditMode) isBarVisible = true
}

// Drag/resize hides bar
LaunchedEffect(dragState) {
  if (dragState != null) isBarVisible = false
}
```

### Bottom Bar Old Styling (from old DashboardButtonBar.kt)
```kotlin
// FAB with accent color (old style)
FloatingActionButton(
  onClick = onSettingsClick,
  modifier = Modifier.size(56.dp),
  shape = CircleShape,
  containerColor = theme.accentColor, // theme-derived accent
  contentColor = if (theme.accentColor.luminance() > 0.5f) Color.Black else Color.White,
) {
  Icon(Icons.Default.Settings, "Settings")
}

// AnimatedContent for edit/add button swap
AnimatedContent(
  targetState = isEditMode,
  transitionSpec = {
    scaleIn(spring(dampingRatio = 0.65f, stiffness = 300f)) + fadeIn(tween(200))
    togetherWith
    scaleOut(tween(150)) + fadeOut(tween(150))
  },
)
```

### Lift Scale on Drag (from old DashboardGrid.kt)
```kotlin
val liftScale by animateFloatAsState(
  targetValue = if (isDragging) 1.03f else 1f,
  animationSpec = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
  ),
)

// Applied in graphicsLayer alongside wiggle
Modifier.graphicsLayer {
  scaleX = liftScale * focusScale
  scaleY = liftScale * focusScale
  rotationZ = wiggleRotation
  alpha = settingsAlpha
}
```

### Action Button Press Scale (from old DashboardGrid.kt)
```kotlin
val actionScale by animateFloatAsState(
  targetValue = if (isPressed) 0.85f else 1f,
  animationSpec = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
  ),
)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Extension properties on DashboardThemeDefinition | Standalone objects in `:core:design` | Phase 5 migration | Tokens decoupled from theme -- good |
| Old navigation with OverlayNavHost in `:app` | New OverlayNavHost in `:feature:dashboard` | Phase 7 migration | Correct per CLAUDE.md but lost PreviewOverlay |
| FAB-based button bar | IconButton-based button bar | Phase 8 migration | Lost accent coloring and FAB aesthetic |
| SetupOverlays in `:core:widget-primitives` | WidgetStatusOverlay in `:feature:dashboard` | Phase 5 migration | Lost theme-awareness and per-type differentiation |
| SplashScreen theme in XML | installSplashScreen() without XML theme | Phase 1 setup | Splash works but shows system default colors |

**Deprecated/outdated:**
- None -- all libraries in use are current stable versions

## Open Questions

1. **`ic_logo_letterform` drawable availability**
   - What we know: Old codebase referenced `@drawable/ic_logo_letterform` for splash icon
   - What's unclear: Whether this drawable has been copied to the new codebase
   - Recommendation: Check for it; if missing, plan must include asset copy or creation

2. **Theme preview 60-second timeout**
   - What we know: ThemeCoordinator has `previewTheme` StateFlow, OverlayNavHost manages preview lifecycle
   - What's unclear: Whether a 60s timeout with auto-revert and toast is implemented
   - Recommendation: Verify in ThemeCoordinator; if missing, add as part of F4.6 plan

3. **Visual grid overlay during drag (F1.20)**
   - What we know: Grid snapping logic exists in WidgetGestureHandler + GridPlacementEngine
   - What's unclear: Whether a visual grid overlay (showing grid lines) renders during drag
   - Recommendation: Check DashboardGrid for grid line rendering during drag; likely missing and needs a plan

4. **Profile page transition visual quality**
   - What we know: ProfilePageTransition wraps DashboardLayer, uses horizontal swipe
   - What's unclear: Whether the transition animation matches old horizontal page model
   - Recommendation: Low risk -- profile paging is new functionality with no old-codebase counterpart to match exactly

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit5 + MockK + Truth (unit), Compose UI testing (semantics) |
| Config file | `android/feature/dashboard/build.gradle.kts` (testFixtures enabled) |
| Quick run command | `./gradlew :feature:dashboard:testDebugUnitTest --console=plain` |
| Full suite command | `./gradlew test --console=plain` |

### Existing Test Infrastructure
- `DashboardTestHarness` (testFixtures) -- coordinator integration testing DSL
- `EditModeCoordinatorTest` -- covers edit state, focus, drag/resize
- `DashboardGridTest` -- covers grid rendering
- `WidgetSlotTest` -- covers widget slot rendering
- `OverlayNavHostTest` + `OverlayNavHostRouteTest` -- covers overlay navigation

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| F1.8 | Focus overlay toolbar appears when widget focused in edit mode | unit (semantics) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.FocusOverlayToolbarTest" --console=plain` | Wave 0 |
| F1.9 | Bottom bar auto-hides after 3s, reveals on tap, edit mode forces visible | unit (coordinator + semantics) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.DashboardButtonBarAutoHideTest" --console=plain` | Wave 0 |
| F1.11 | Corner brackets render in edit mode with correct stroke pulse | unit (semantics) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.CornerBracketTest" --console=plain` | Wave 0 |
| F1.20 | Grid overlay visible during drag, haptic on snap | unit (state) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.GridSnapOverlayTest" --console=plain` | Wave 0 |
| F1.21 | Widget add/remove animations with correct specs | unit (animation state) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.DashboardGridTest" --console=plain` | Exists |
| F1.29 | Profile switching via swipe and bottom bar tap | unit (harness) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.ProfileCoordinatorTest" --console=plain` | Exists |
| F2.5 | Status overlays with themed icons and corner radius | unit (semantics) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.WidgetStatusOverlayTest" --console=plain` | Wave 0 |
| F2.18 | Focus interaction: tap-to-focus, tap-to-unfocus, action gating | unit (coordinator + semantics) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.EditModeCoordinatorTest" --console=plain` | Exists (needs expansion) |
| F3.14 | Setup failure overlays with tap-to-setup | unit (semantics) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.WidgetStatusOverlayTest" --console=plain` | Wave 0 |
| F4.6 | Theme preview timeout 60s with revert and toast | unit (harness/Turbine) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.ThemeCoordinatorTest" --console=plain` | Exists (needs expansion) |
| F11.7 | Permission flow: overlay tap -> setup wizard -> bind or system settings | unit (state flow) | `./gradlew :feature:dashboard:testDebugUnitTest --tests "*.WidgetSlotTest" --console=plain` | Exists (needs expansion) |

### Sampling Rate
- **Per task commit:** `./gradlew :feature:dashboard:testDebugUnitTest --console=plain`
- **Per wave merge:** `./gradlew test --console=plain`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `FocusOverlayToolbarTest.kt` -- covers F1.8, F2.18 visual: verify focus toolbar rendered via semantics
- [ ] `DashboardButtonBarAutoHideTest.kt` -- covers F1.9: verify auto-hide timer, reveal, edit-mode override
- [ ] `CornerBracketTest.kt` -- covers F1.11: verify bracket rendering in edit mode via semantics/test tags
- [ ] `WidgetStatusOverlayTest.kt` -- covers F2.5, F3.14: verify themed overlays, tap handlers, corner radius
- [ ] `GridSnapOverlayTest.kt` -- covers F1.20: verify grid overlay visibility during drag state

## Recommended Plan Decomposition

Based on deltas, dependencies, and context pressure management:

| Plan | Area | Key Files | Dependencies |
|------|------|-----------|--------------|
| 14-01 | Splash screen theme | `app/res/values/themes.xml`, `values-v31/themes.xml`, `AndroidManifest.xml` | None |
| 14-02 | Bottom bar auto-hide | `DashboardScreen.kt`, `DashboardButtonBar.kt` | None |
| 14-03 | Bottom bar visual parity (accent FAB style) | `DashboardButtonBar.kt` | 14-02 (uses same file) |
| 14-04 | Corner brackets (edit mode) | `DashboardGrid.kt` | None |
| 14-05 | Drag lift scale | `DashboardGrid.kt` | 14-04 (uses same graphicsLayer) |
| 14-06 | Focus overlay toolbar | `DashboardGrid.kt`, `WidgetSlot.kt`, `EditModeCoordinator.kt` | 14-04, 14-05 (graphicsLayer stack) |
| 14-07 | Widget status overlay parity | `WidgetStatusOverlay.kt`, `WidgetSlot.kt` | None |
| 14-08 | PreviewOverlay dashboard-peek | `OverlayNavHost.kt` + new `PreviewOverlay.kt` | None |
| 14-09 | Theme preview timeout | `ThemeCoordinator.kt` | None |
| 14-10 | Grid snap overlay + confirmation dialogs | `DashboardGrid.kt`, `OverlayNavHost.kt` | 14-04 |

**Wave structure suggestion:**
- Wave 1: Plans 14-01, 14-02, 14-04, 14-07, 14-08, 14-09 (no dependencies)
- Wave 2: Plans 14-03, 14-05, 14-06, 14-10 (depend on Wave 1)

## Sources

### Primary (HIGH confidence)
- Old source: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/DashboardAnimations.kt` -- animation specs
- Old source: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/DashboardThemeExtensions.kt` -- design tokens
- Old source: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/DashboardScreen.kt` -- auto-hide bar logic
- Old source: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt` -- wiggle, brackets, focus, lift
- Old source: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/ui/DashboardButtonBar.kt` -- FAB styling
- Old source: `../dqxn.old/android/app/src/main/java/app/dqxn/android/navigation/OverlayNavHost.kt` -- PreviewOverlay, confirmation dialogs
- Old source: `../dqxn.old/android/core/widget-primitives/src/main/java/app/dqxn/android/core/widgetprimitives/SetupOverlays.kt` -- themed overlays
- Old source: `../dqxn.old/android/app/src/main/res/values/themes.xml` + `values-v31/themes.xml` -- splash theme
- New source: `android/feature/dashboard/` -- all current implementations
- New source: `android/core/design/` -- ported design tokens
- `.planning/migration/replication-advisory.md` -- sections 1-4 cross-referenced

### Secondary (MEDIUM confidence)
- `.planning/oldcodebase/feature-dashboard.md` -- old codebase mapping index
- `.planning/oldcodebase/app-module.md` -- old app module mapping index

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in project, versions verified via build files
- Architecture: HIGH -- deltas identified by reading actual old and new source code, not inferred
- Pitfalls: HIGH -- derived from actual implementation differences and known Compose animation behaviors
- Test strategy: MEDIUM -- test infrastructure exists but specific test approaches for Canvas/animation validation via semantics need implementation-time verification

**Research date:** 2026-02-27
**Valid until:** 2026-03-27 (stable domain, no external dependency changes expected)
