---
phase: 14-ui-visual-interactive-parity
verified: 2026-02-27T00:00:00Z
status: gaps_found
score: 31/36 must-haves verified
re_verification: false
gaps:
  - truth: "onNavigateToThemeMode navigates to AutoSwitchModeRoute (not ThemeSelectorRoute)"
    status: failed
    reason: "OverlayNavHost line 164 still calls navController.navigate(ThemeSelectorRoute) for onNavigateToThemeMode — AutoSwitchModeRoute navigation was not wired"
    artifacts:
      - path: "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHost.kt"
        issue: "Line 160-165: onNavigateToThemeMode navigates to ThemeSelectorRoute, not AutoSwitchModeRoute"
    missing:
      - "Replace navController.navigate(ThemeSelectorRoute) with navController.navigate(AutoSwitchModeRoute) in onNavigateToThemeMode lambda"
      - "Add composable<AutoSwitchModeRoute> destination (AutoSwitchModeContent in OverlayScaffold) to OverlayNavHost NavHost block"

  - truth: "onNavigateToLightTheme previews + navigates to ThemeSelectorRoute(isDark=false)"
    status: failed
    reason: "onNavigateToLightTheme and onNavigateToDarkTheme callbacks are not passed from OverlayNavHost to MainSettings — MainSettings call site only passes 5 params (analyticsConsent, showStatusBar, keepScreenOn, navigation callbacks for packs/diagnostics/close)"
    artifacts:
      - path: "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHost.kt"
        issue: "Lines 152-172: MainSettings call site omits onNavigateToLightTheme, onNavigateToDarkTheme, lightThemeName, darkThemeName, packCount, themeCount, widgetCount, providerCount, autoSwitchModeDescription, versionName, onResetDash"
    missing:
      - "Wire onNavigateToLightTheme: preview lightTheme + navigate to ThemeSelectorRoute(isDark=false)"
      - "Wire onNavigateToDarkTheme: preview darkTheme + navigate to ThemeSelectorRoute(isDark=true)"
      - "Pass lightThemeName, darkThemeName from themeCoordinator.themeState"
      - "Pass packCount, themeCount, widgetCount, providerCount from widgetRegistry/dataProviderRegistry/allThemes"
      - "Pass autoSwitchModeDescription from themeState.autoSwitchMode"
      - "Wire onResetDash to DashboardCommand.ResetLayout (or equivalent)"

  - truth: "isDark from ThemeSelectorRoute passed to ThemeSelector in OverlayNavHost"
    status: failed
    reason: "ThemeSelectorRoute is now data class ThemeSelectorRoute(isDark: Boolean) but OverlayNavHost navigates to ThemeSelectorRoute (no isDark) and passes isDark = themeState.currentTheme.isDark (current active theme's mode) rather than from the route parameter. This means Light Theme and Dark Theme rows can't differentiate — both would pass the currentTheme's isDark to ThemeSelector."
    artifacts:
      - path: "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHost.kt"
        issue: "Line 164: navigate(ThemeSelectorRoute) uses ThemeSelectorRoute with isDark defaulting to false (data class default). Line 267: isDark = themeState.currentTheme.isDark (wrong — should read route.isDark from backStackEntry.toRoute<ThemeSelectorRoute>())"
    missing:
      - "Add backStackEntry parameter to ThemeSelectorRoute composable: composable<ThemeSelectorRoute> { backStackEntry -> val route = backStackEntry.toRoute<ThemeSelectorRoute>() }"
      - "Pass isDark = route.isDark to ThemeSelector instead of themeState.currentTheme.isDark"

  - truth: "F4.6 — Theme preview timeout: plan removed timeout claiming old codebase parity, but REQUIREMENTS.md F4.6 explicitly requires 60s timeout with toast 'Theme preview ended.'"
    status: failed
    reason: "REQUIREMENTS.md F4.6 states 'Preview times out after 60 seconds with toast Theme preview ended.' No 60s timeout exists anywhere in production source. Plans 05/06/08/14 explicitly removed PREVIEW_TIMEOUT_MS claiming old codebase didn't have it — but this contradicts the written requirement. This is a plan-vs-requirement conflict that must be resolved: either implement the timeout per F4.6 or formally amend the requirement."
    artifacts:
      - path: "android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeSelector.kt"
        issue: "PREVIEW_TIMEOUT_MS deleted by plan 14-06; no timeout replacement exists"
      - path: "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/ThemeCoordinator.kt"
        issue: "No 60s timeout with auto-revert and toast"
    missing:
      - "Resolve F4.6 conflict: either add 60s timeout in ThemeCoordinator (per requirement) or formally amend REQUIREMENTS.md to remove the timeout specification with documented rationale"

  - truth: "FocusOverlayToolbar uses dashboard design tokens (not MaterialTheme) — per plan 14-13 DesignTokenWiringTest contract"
    status: failed
    reason: "FocusOverlayToolbar.kt (created by plan 14-07) uses MaterialTheme.colorScheme.surfaceContainer and MaterialTheme.colorScheme.onSurface. The DesignTokenWiringTest in :feature:dashboard includes an allowlist of 6 files for MaterialTheme — but FocusOverlayToolbar.kt is NOT on the allowlist, so the DesignTokenWiringTest no MaterialTheme colorScheme test fails for it. Deferred-items.md confirms this is a known failure."
    artifacts:
      - path: "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/FocusOverlayToolbar.kt"
        issue: "Uses MaterialTheme.colorScheme.surfaceContainer and MaterialTheme.colorScheme.onSurface — not LocalDashboardTheme"
    missing:
      - "Either: add FocusOverlayToolbar.kt to :feature:dashboard DesignTokenWiringTest allowlist (with plan reference), OR migrate FocusOverlayToolbar to use LocalDashboardTheme.current.widgetBorderColor / primaryTextColor for ActionButton colors"
---

# Phase 14: UI Visual and Interactive Parity — Verification Report

**Phase Goal:** Restore UI visual and interactive parity with the old codebase after migration. Fix broken typography, spacing, animations, graphical assets, OverlayNavHost sheets, theme/widget preview states, bottom bar, and splash screen — adapted to the new architecture.

**Verified:** 2026-02-27
**Status:** gaps_found
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Splash screen shows dark background (#0f172a) with letterform icon | VERIFIED | `values/themes.xml` has Theme.App.Starting with windowSplashScreenBackground=#0f172a; `values-v31/themes.xml` has android:windowSplashScreenBackground; manifest references @style/Theme.App.Starting |
| 2 | Post-splash transitions to Theme.Dqxn.NoActionBar seamlessly | VERIFIED | postSplashScreenTheme=@style/Theme.Dqxn.NoActionBar present in both theme files |
| 3 | Bottom bar auto-hides after 3s inactivity, tap reveals, edit mode forces visible, drag/resize hides | VERIFIED | DashboardScreen.kt has isBarVisible, lastInteractionTime, LaunchedEffect(isBarVisible, editState.isEditMode), edit mode LaunchedEffect, drag/resize LaunchedEffect |
| 4 | Settings button is accent-colored FAB with luminance-based content color | VERIFIED | DashboardButtonBar.kt imports FloatingActionButton, luminance; uses accentColor + luminance-based content color |
| 5 | Edit mode shows Canvas-drawn corner brackets with pulsing stroke width 3-6dp 800ms | VERIFIED | DashboardGrid.kt has bracketStrokeWidth, 8x drawLine calls for 4 corners; no scaleX = bracketScale |
| 6 | Visual grid overlay visible during drag (drawBehind) | VERIFIED | DashboardGrid.kt imports drawBehind, conditional gridOverlayModifier with drawLine grid lines |
| 7 | Widget add/remove animations use spring StiffnessMediumLow | VERIFIED | DashboardGrid.kt has AnimatedVisibility + scaleIn/scaleOut + StiffnessMediumLow confirmed |
| 8 | Status overlays use theme accent color, RoundedCornerShape clip, per-type icon sizes | VERIFIED | WidgetStatusOverlay.kt has accentColor from LocalDashboardTheme, RoundedCornerShape(cornerRadiusDp.dp), per-type when block |
| 9 | SetupRequired and EntitlementRevoked overlays are tappable (route to OpenWidgetSettings) | VERIFIED | WidgetSlot.kt passes onSetupTap and onEntitlementTap both firing DashboardCommand.OpenWidgetSettings |
| 10 | Disconnected overlay uses corner positioning (top-end), 20dp icon | VERIFIED | WidgetStatusOverlay.kt has Alignment.TopEnd + Modifier.size(20.dp) for Disconnected case |
| 11 | PreviewOverlay composable with configurable previewFraction and tap-to-dismiss zone | VERIFIED | PreviewOverlay.kt has previewFraction param, preview_dismiss_zone testTag, fillMaxHeight(1-previewFraction) content |
| 12 | Settings/WidgetSettings/ThemeSelector/ThemeStudio routes wrapped in PreviewOverlay | VERIFIED | OverlayNavHost.kt has 4 PreviewOverlay calls: 0.15f, 0.38f, 0.15f, 0.15f respectively |
| 13 | ThemeSelector uses GridCells.Fixed(3), 2-page HorizontalPager built-in/custom | VERIFIED | ThemeSelector.kt has HorizontalPager, GridCells.Fixed(3), 2 pages (builtInThemes/customThemes) |
| 14 | Theme cards show gradient background, 4 color-dot swatches, star icon for premium, 2f aspect ratio | VERIFIED | ThemeSelector.kt uses backgroundBrush, Row of 4 ColorDot, Icons.Filled.Star, aspectRatio(2f) |
| 15 | Selection border uses highlightColor, no PREVIEW_TIMEOUT_MS | VERIFIED | ThemeSelector.kt: borderColor = if (isSelected) highlightColor; no PREVIEW_TIMEOUT_MS |
| 16 | ThemeSelector accepts isDark, onCreateNewTheme params | VERIFIED | ThemeSelector.kt function signature has isDark: Boolean and onCreateNewTheme: () -> Unit |
| 17 | FocusOverlayToolbar renders above focused widget with delete/settings buttons, press-scale animation | VERIFIED | FocusOverlayToolbar.kt exists with onDelete/onSettings, animateFloatAsState 0.85f spring; DashboardGrid.kt renders it for focusedWidgetId |
| 18 | Tap-to-focus/unfocus wired in DashboardGrid, settingsAlpha dims non-focused widgets | VERIFIED | DashboardGrid.kt has settingsAlpha tween(300), isFocused check, clickable for focus toggle |
| 19 | MainSettings restructured to About Banner → Dash Packs → Themes → Status Bar → Reset Dash → Advanced | VERIFIED | MainSettings.kt has AboutAppBanner, SettingsItemRow for DashPacks/ThemeMode/LightTheme/DarkTheme, ToggleRow for Status Bar, ResetDashRow |
| 20 | About App banner: tagline "Life is a dash. Make it beautiful.", attribution "-- The Dashing Dachshund" | VERIFIED | MainSettings.kt has AboutAppBanner composable with string resources about_app_tagline, about_app_attribution |
| 21 | Settings items have 40dp rounded-square icon box, accentColor.copy(alpha=0.1f) background | VERIFIED | MainSettings.kt SettingsItemRow has Modifier.size(40.dp) + accentColor.copy(alpha = 0.1f) |
| 22 | AutoSwitchModeRoute exists in OverlayRoutes | VERIFIED | OverlayRoutes.kt has @Serializable data object AutoSwitchModeRoute |
| 23 | ThemeSelectorRoute changed to data class with isDark param | VERIFIED | OverlayRoutes.kt: data class ThemeSelectorRoute(val isDark: Boolean = false) |
| 24 | onNavigateToThemeMode navigates to AutoSwitchModeRoute | FAILED | OverlayNavHost line 164 still navigates to ThemeSelectorRoute |
| 25 | onNavigateToLightTheme/onNavigateToDarkTheme wired from OverlayNavHost to MainSettings | FAILED | These callbacks are NOT passed from OverlayNavHost to MainSettings |
| 26 | isDark from ThemeSelectorRoute passed to ThemeSelector | FAILED | ThemeSelectorRoute composable does not use backStackEntry.toRoute<ThemeSelectorRoute>() — isDark = themeState.currentTheme.isDark (hardcoded from current theme) |
| 27 | WidgetPicker uses LazyVerticalStaggeredGrid Adaptive(120.dp), wide widgets span FullLine, no FlowRow | VERIFIED | WidgetPicker.kt has LazyVerticalStaggeredGrid, StaggeredGridCells.Adaptive(minSize = 120.dp), StaggeredGridItemSpan.FullLine for wide widgets |
| 28 | OverlayScaffold applies adaptive width constraints on medium+ screens | VERIFIED | OverlayScaffold.kt has widthIn, maxWidthDp(), Hub=480.dp, Preview=520.dp, isCompact check with COMPACT_MAX_WIDTH=600.dp |
| 29 | DesignTokenWiringTest exists in all 3 feature modules scanning for MaterialTheme usage | VERIFIED | All 3 DesignTokenWiringTest files exist with source-scanning assertions and allowlists |
| 30 | ThemeStudio title is editable BasicTextField | VERIFIED | ThemeStudio.kt has BasicTextField for displayName editing, testTag "editable_title" |
| 31 | ThemeStudio has Undo button (reset to initial) and Delete button (hidden for new themes) | VERIFIED | ThemeStudio.kt has undo_button (clickable stateHolder.reset()) and delete_button (shown only when existingTheme != null) |
| 32 | ThemeSwatchRow uses 48dp container with 36dp inner circle, highlightColor selection border | VERIFIED | ThemeSwatchRow.kt has Modifier.size(48.dp), Modifier.size(36.dp), borderColor = if (isSelected) theme.highlightColor |
| 33 | Gradient editing wires GradientTypeSelector + GradientStopRow for BACKGROUND/WIDGET_BACKGROUND | VERIFIED | ThemeStudio.kt has GradientTypeSelector + GradientStopRow for both SwatchType.BACKGROUND and SwatchType.WIDGET_BACKGROUND |
| 34 | ThemeStudioStateHolder has displayName, gradient state, reset(), buildCustomTheme uses displayName | VERIFIED | ThemeStudioStateHolder.kt has var displayName, backgroundGradientType, backgroundStops, widgetBackgroundGradientType, widgetBackgroundStops, savedDisplayName, isDirty includes gradient comparisons, fun reset() |
| 35 | Profile switching via swipe and bottom bar tap preserved (F1.29) | VERIFIED | ProfilePageTransition.kt exists; DashboardButtonBar.kt has onProfileClick, profile_{id} testTags |
| 36 | F4.6 — 60s preview timeout with toast "Theme preview ended." | FAILED | No timeout exists in ThemeCoordinator or anywhere in production source; plans deliberately removed it citing old codebase parity, contradicting REQUIREMENTS.md F4.6 |

**Score:** 31/36 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `android/app/src/main/res/values/themes.xml` | Theme.App.Starting with splash bg | VERIFIED | Contains windowSplashScreenBackground=#0f172a, postSplashScreenTheme, ic_logo_letterform |
| `android/app/src/main/res/values-v31/themes.xml` | API 31+ native splash attributes | VERIFIED | Contains android:windowSplashScreenBackground, android:windowSplashScreenAnimatedIcon |
| `android/app/src/main/AndroidManifest.xml` | Theme.App.Starting on activity | VERIFIED | android:theme="@style/Theme.App.Starting" confirmed |
| `android/app/src/test/kotlin/app/dqxn/android/SplashThemeTest.kt` | Splash theme validation test | VERIFIED | File exists with 3 test methods |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt` | Auto-hide timer state | VERIFIED | isBarVisible, lastInteractionTime, LaunchedEffects |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/ui/DashboardButtonBar.kt` | FAB-style accent settings button | VERIFIED | FloatingActionButton, luminance, accentColor |
| `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/ui/DashboardButtonBarAutoHideTest.kt` | Auto-hide timer tests | VERIFIED | Exists (pre-existing compilation issue: isDefault param — see deferred-items.md) |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt` | Canvas brackets + grid overlay + drag lift | VERIFIED | bracketStrokeWidth, 8x drawLine, drawBehind, liftScale |
| `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/grid/CornerBracketTest.kt` | Corner bracket tests | VERIFIED | Exists (pre-existing Robolectric classloader failure — see deferred-items.md) |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/ui/WidgetStatusOverlay.kt` | Themed per-type overlays | VERIFIED | accentColor, RoundedCornerShape, per-type when block |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/binding/WidgetSlot.kt` | OpenWidgetSettings tap handlers | VERIFIED | onSetupTap + onEntitlementTap both fire OpenWidgetSettings |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/command/DashboardCommand.kt` | OpenWidgetSettings command | VERIFIED | data class OpenWidgetSettings exists |
| `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/ui/WidgetStatusOverlayTest.kt` | Status overlay tests | VERIFIED | Exists |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/PreviewOverlay.kt` | PreviewOverlay composable | VERIFIED | previewFraction, onDismiss, preview_dismiss_zone, preview_content |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHost.kt` | Routes wrapped in PreviewOverlay | VERIFIED (partial) | 4 PreviewOverlay wraps correct; AutoSwitchModeRoute destination MISSING; MainSettings new params not passed |
| `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/layer/PreviewOverlayTest.kt` | PreviewOverlay tests | VERIFIED | Exists |
| `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeSelector.kt` | 3-col 2-page pager theme selector | VERIFIED | HorizontalPager, GridCells.Fixed(3), aspectRatio(2f), highlightColor, star icon, no timeout |
| `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/theme/ThemeSelectorTest.kt` | ThemeSelector tests | VERIFIED | Exists (updated for new params) |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/FocusOverlayToolbar.kt` | Focus toolbar composable | VERIFIED (partial) | Exists with onDelete/onSettings/press-scale; uses MaterialTheme.colorScheme (design token gap) |
| `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/grid/FocusOverlayToolbarTest.kt` | Focus toolbar tests | VERIFIED | Exists |
| `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/Phase14RegressionTest.kt` | Phase 14 regression gate | VERIFIED | Exists with 10 source-scanning assertions |
| `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/main/MainSettings.kt` | Restructured settings UI | VERIFIED | AboutAppBanner, SettingsItemRow, ResetDashRow — params exist but routing not wired |
| `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayRoutes.kt` | AutoSwitchModeRoute, ThemeSelectorRoute(isDark) | VERIFIED | Both present |
| `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/main/MainSettingsTest.kt` | MainSettings tests | VERIFIED | Exists |
| `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/WidgetPicker.kt` | LazyVerticalStaggeredGrid picker | VERIFIED | Adaptive grid, FullLine spanning, no FlowRow, no verticalScroll |
| `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/WidgetPickerLayoutTest.kt` | Widget picker layout tests | VERIFIED | Exists |
| `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/overlay/OverlayScaffold.kt` | Adaptive width constraints | VERIFIED | widthIn, maxWidthDp, Hub=480, Preview=520, Confirmation=400 |
| `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/overlay/OverlayScaffoldAdaptiveTest.kt` | Adaptive overlay tests | VERIFIED | Exists |
| `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/DesignTokenWiringTest.kt` | Design token test settings | VERIFIED | Exists with allowlist |
| `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/DesignTokenWiringTest.kt` | Design token test dashboard | VERIFIED (partial) | Exists; FocusOverlayToolbar.kt uses MaterialTheme but is NOT on the allowlist — test fails |
| `android/feature/onboarding/src/test/kotlin/app/dqxn/android/feature/onboarding/DesignTokenWiringTest.kt` | Design token test onboarding | VERIFIED | Exists |
| `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeStudio.kt` | Theme Studio editable title, undo/delete, gradient wiring | VERIFIED | BasicTextField, undo_button, delete_button, GradientTypeSelector, GradientStopRow |
| `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeStudioStateHolder.kt` | State holder with displayName, gradient, reset() | VERIFIED | var displayName, backgroundGradientType, backgroundStops, fun reset() |
| `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeSwatchRow.kt` | 48dp container, 36dp inner, highlightColor | VERIFIED | size(48.dp), size(36.dp), theme.highlightColor border |
| `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/theme/ThemeStudioTest.kt` | ThemeStudio tests | VERIFIED | Exists |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| AndroidManifest.xml | themes.xml | android:theme="@style/Theme.App.Starting" | VERIFIED | Confirmed |
| DashboardScreen.kt | DashboardButtonBar.kt | isBarVisible state + onInteraction callback | VERIFIED | isBarVisible passed, lastInteractionTime reset in onInteraction |
| DashboardGrid.kt | EditState.isEditMode | Conditional bracket rendering | VERIFIED | isEditMode gates bracketStrokeWidth > 0 and Canvas rendering |
| DashboardGrid.kt | FocusOverlayToolbar.kt | Rendered when editState.focusedWidgetId matches widget | VERIFIED | FocusOverlayToolbar rendered when isEditMode && focusedWidgetId != null |
| FocusOverlayToolbar.kt | DashboardCommand.RemoveWidget | Delete button fires command (note: RemoveWidget not DeleteWidget) | VERIFIED | Fires DashboardCommand.RemoveWidget(focusedWidget.instanceId) |
| WidgetStatusOverlay.kt | LocalDashboardTheme | theme.accentColor for icon tint | VERIFIED | accentColor = theme.accentColor from LocalDashboardTheme.current |
| WidgetSlot.kt | WidgetStatusOverlay.kt | onSetupTap, onEntitlementTap callbacks | VERIFIED | Both callbacks fire OpenWidgetSettings command |
| OverlayNavHost.kt | PreviewOverlay.kt | PreviewOverlay wrapping route content | VERIFIED | 4 routes wrapped |
| OverlayNavHost.kt | AutoSwitchModeContent | AutoSwitchModeRoute composable destination | FAILED | composable<AutoSwitchModeRoute> destination does NOT exist in NavHost block |
| MainSettings.kt | OverlayNavHost.kt | onNavigateToLightTheme, onNavigateToDarkTheme callbacks | FAILED | Callbacks exist in MainSettings signature but not passed from OverlayNavHost |
| ThemeSelectorRoute | ThemeSelector.isDark | isDark from route parameter | FAILED | ThemeSelectorRoute not read via toRoute<ThemeSelectorRoute>() — isDark taken from themeState.currentTheme.isDark |
| FocusOverlayToolbar.kt | LocalDashboardTheme | Design token for button colors | FAILED | Uses MaterialTheme.colorScheme instead of LocalDashboardTheme tokens |

---

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| F1.8 | 14-07 | Widget focus overlay toolbar (delete/settings, no translate/scale) | VERIFIED | FocusOverlayToolbar.kt exists, wired in DashboardGrid, settingsAlpha dims non-focused widgets |
| F1.9 | 14-02 | Auto-hide bottom bar (3s, tap reveal, edit mode forces visible, drag hides, FAB settings) | VERIFIED | DashboardScreen.kt has timer logic; DashboardButtonBar.kt has FloatingActionButton |
| F1.11 | 14-03 | Edit mode corner brackets (Canvas drawLine stroke width pulse, NOT scale) | VERIFIED | bracketStrokeWidth animation, 8x drawLine; no scaleX = bracketScale |
| F1.20 | 14-03 | Grid snap overlay during drag (visual grid lines) | VERIFIED | drawBehind modifier with grid lines when dragState != null |
| F1.21 | 14-08 | Widget add/remove animations (spring StiffnessMediumLow) | VERIFIED | AnimatedVisibility + scaleIn/scaleOut + StiffnessMediumLow confirmed in DashboardGrid.kt |
| F1.29 | 14-08 | Profile switching via swipe and bottom bar tap | VERIFIED | ProfilePageTransition.kt exists; DashboardButtonBar.kt has onProfileClick + profile_{id} testTags |
| F2.5 | 14-04 | WidgetStatusCache overlays with theming | VERIFIED | WidgetStatusOverlay.kt fully reworked: accent color, per-type sizes, RoundedCornerShape |
| F2.18 | 14-07 | Focus interaction model (toolbar, tap-to-unfocus, interactive gates) | VERIFIED | FocusOverlayToolbar + tap-to-focus/unfocus in DashboardGrid + EditModeCoordinator.isInteractionAllowed |
| F3.14 | 14-04 | Provider setup failure UX (SetupRequired overlay tappable) | VERIFIED | WidgetSlot.kt: onSetupTap fires OpenWidgetSettings command |
| F4.6 | 14-05, 14-06, 14-08 | Theme preview: live preview, reverts on cancel, 60s timeout with toast | PARTIAL | PreviewOverlay (dashboard peek) and preview reverts are implemented. 60s timeout with toast explicitly deleted by plans, contradicting F4.6. |
| F11.7 | 14-04 | Permission flow: SetupRequired overlay tap opens setup wizard | VERIFIED | WidgetSlot.kt: onSetupTap fires OpenWidgetSettings; DashboardCommand.OpenWidgetSettings exists |

---

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `FocusOverlayToolbar.kt` | `MaterialTheme.colorScheme.surfaceContainer`, `MaterialTheme.colorScheme.onSurface` | WARNING | DesignTokenWiringTest no-MaterialTheme-colorScheme assertion fails for this file (confirmed in deferred-items.md as known failure #6) |
| `OverlayNavHost.kt` | Missing `composable<AutoSwitchModeRoute>` NavHost destination — route defined but navigation to it would silently do nothing | BLOCKER | Navigating to AutoSwitchModeRoute (once wired) will fail at runtime with no destination found |
| `OverlayNavHost.kt` | `navController.navigate(ThemeSelectorRoute)` in onNavigateToThemeMode should be `navigate(AutoSwitchModeRoute)` | BLOCKER | Theme Mode settings row navigates to wrong destination |
| `OverlayNavHost.kt` | New MainSettings params (lightThemeName, darkThemeName, packCount, versionName, etc.) all use default empty/zero values | WARNING | About App banner shows no version; Dash Packs shows "0 packs"; Light/Dark Theme rows show no theme names |
| `Phase14RegressionTest.kt` | `F1_20 grid overlay renders during drag` — test asserts `content.contains("drawBehind")` which passes even with the correct implementation | INFO | Test is semantically correct but imprecise — any `drawBehind` call satisfies it |

---

### Pre-Existing Failures (Documented in deferred-items.md)

These failures predate Phase 14 and are out of scope:

1. **DashboardButtonBarAutoHideTest.kt** — Missing `isDefault` param in ProfileInfo constructor (pre-existing)
2. **CornerBracketTest.kt** — Robolectric ClassCastException at DashboardGrid.kt:501 (pre-existing KSP/classloader issue)
3. **WidgetPickerTest.kt** — Widget text node not found assertion (pre-existing)
4. **pack:plus:testDebugUnitTest** — 0 tests discovered (pre-existing JUnit5 config issue)
5. **SetupEvaluatorImpl KSP resolution failure** — Blocks `:feature:dashboard:kspDebugKotlin` (pre-existing)

---

### Human Verification Required

#### 1. AutoSwitchModeRoute Runtime Behavior

**Test:** Navigate to Settings, tap "Theme Mode" row.
**Expected per requirement:** Should open a mode selector (Light/Dark/System/Solar/Illuminance).
**Current behavior:** Navigates to ThemeSelectorRoute (theme browser) with a preview active — wrong screen.
**Why human:** Cannot verify runtime navigation behavior from static analysis.

#### 2. Light Theme / Dark Theme Row Behavior

**Test:** Navigate to Settings, tap "Light Theme" row, then "Dark Theme" row.
**Expected:** Each should open ThemeSelector filtered to light or dark themes respectively (isDark = false / true).
**Current behavior:** Neither callback is wired in OverlayNavHost; tapping either will call the default no-op lambda.
**Why human:** Cannot verify runtime tap response from static analysis.

#### 3. Visual Appearance of Splash Screen

**Test:** Cold start the app.
**Expected:** Dark (#0f172a) background, DQXN letterform icon centered, smooth transition to app.
**Why human:** Visual appearance requires device/emulator execution.

#### 4. F4.6 Preview Timeout Policy Decision

**Test:** Open ThemeSelector, select a theme preview, wait 60 seconds.
**Expected per REQUIREMENTS.md F4.6:** Preview reverts automatically with toast "Theme preview ended."
**Current behavior:** Preview stays active indefinitely. Plans claim old codebase had no timeout.
**Why human:** This is a product decision about whether REQUIREMENTS.md F4.6 is authoritative or whether the plan research overrides it. Needs product owner resolution.

---

## Gaps Summary

Five gaps block full goal achievement:

**Gap 1 — AutoSwitchModeRoute not wired (blocker):** `OverlayNavHost.kt` has `AutoSwitchModeRoute` defined in OverlayRoutes and referenced in transition patterns, but has no `composable<AutoSwitchModeRoute>` NavHost destination. Navigating to it would silently fail. The `onNavigateToThemeMode` callback still points to `ThemeSelectorRoute` instead.

**Gap 2 — MainSettings new callbacks not passed from OverlayNavHost (blocker):** `MainSettings.kt` has `onNavigateToLightTheme`, `onNavigateToDarkTheme`, and 8 data params (lightThemeName, darkThemeName, packCount, themeCount, widgetCount, providerCount, autoSwitchModeDescription, versionName, onResetDash). OverlayNavHost's MainSettings call site omits all of them, leaving the About banner versionless, Dash Packs showing "0 packs", and Light/Dark Theme rows as unresponsive no-ops.

**Gap 3 — ThemeSelectorRoute isDark not read from route (functional gap):** The ThemeSelectorRoute composable uses `isDark = themeState.currentTheme.isDark` (current active theme's mode) instead of `route.isDark` from the backStackEntry. This means both the light and dark theme selectors would show the same filtered set based on the current active theme — defeating the purpose of parameterized routing.

**Gap 4 — F4.6 timeout conflict (requirement gap):** REQUIREMENTS.md F4.6 requires a 60s preview timeout with toast. Plans removed the existing timeout citing old codebase parity research. This is a documented conflict that requires explicit product resolution — either implement the timeout or amend the requirement.

**Gap 5 — FocusOverlayToolbar design token gap (warning):** FocusOverlayToolbar.kt uses `MaterialTheme.colorScheme` instead of `LocalDashboardTheme` tokens. This causes the DesignTokenWiringTest in `:feature:dashboard` to fail (confirmed in deferred-items.md as item #6).

---

*Verified: 2026-02-27*
*Verifier: Claude (gsd-verifier)*
