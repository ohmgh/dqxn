package app.dqxn.android.core.design.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset

/**
 * Unified animation specs for all dashboard transitions.
 *
 * Ported verbatim from old codebase `DashboardAnimations.kt`.
 *
 * Provides pre-composed enter/exit transitions for each overlay type:
 * - [sheetEnter]/[sheetExit] -- Bottom-up slide for button bars and sheets
 * - [hubEnter]/[hubExit] -- Scale + fade for fullscreen hub overlays
 * - [previewEnter]/[previewExit] -- Vertical slide for preview sheets
 * - [expandEnter]/[expandExit] -- Vertical expand for collapsible sections
 * - [dialogScrimEnter]/[dialogScrimExit] -- Fade for dialog route scrim
 * - [dialogEnter]/[dialogExit] -- Scale + fade for dialog card content
 * - PackBrowser source-dependent transitions
 *
 * Duration asymmetry pattern: enter is typically 200ms, exit 150ms.
 */
public object DashboardMotion {

  // Internal Spring Specs

  /** Standard spring -- subtle overshoot, moderate speed. Used by sheet, expand, dialog, packBrowser. */
  private val standardSpring = spring<Float>(dampingRatio = 0.65f, stiffness = 300f)

  private val standardSpringInt = spring<IntOffset>(dampingRatio = 0.65f, stiffness = 300f)

  /** Hub spring -- bouncier overshoot for premium feel. Used by hub overlays. */
  private val hubSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)

  /** Preview spring -- balanced slide with subtle overshoot. Used by preview sheets. */
  private val previewSpring = spring<IntOffset>(dampingRatio = 0.75f, stiffness = 380f)

  // Sheet Transitions (bottom-up slide)
  // Used by: DashboardButtonBar

  /** Sheet enter: slide up from bottom with spring + fade in. */
  public val sheetEnter: EnterTransition =
    slideInVertically(standardSpringInt) { it } + fadeIn(tween(200))

  /** Sheet exit: slide down to bottom + fade out. */
  public val sheetExit: ExitTransition =
    slideOutVertically(tween(200)) { it } + fadeOut(tween(150))

  // Hub Transitions (scale + fade)
  // Used by: ProviderSetup, PermissionRequest, WidgetPicker, TimezoneSelector,
  //          DateFormatSelector, AppSelector

  /** Hub enter: fade in with bouncy spring + scale from 85%. */
  public val hubEnter: EnterTransition =
    fadeIn(hubSpring) + scaleIn(initialScale = 0.85f, animationSpec = hubSpring)

  /** Hub exit: fade out + scale to 95%. */
  public val hubExit: ExitTransition =
    fadeOut(tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(150))

  // Preview Transitions (vertical slide with fade)
  // Used by: Settings, ThemeModeSelector, ThemeSelector, ThemeEditor

  /** Preview enter: slide up with balanced spring + fade in. */
  public val previewEnter: EnterTransition =
    slideInVertically(previewSpring) { it } + fadeIn(tween(200))

  /** Preview exit: slide down + fade out. */
  public val previewExit: ExitTransition =
    slideOutVertically(tween(200)) { it } + fadeOut(tween(150))

  // Expand Transitions (vertical expand/collapse)
  // Used by: FeatureSettingsContent

  /** Expand enter: expand vertically with spring + fade in. */
  public val expandEnter: EnterTransition =
    expandVertically(spring(dampingRatio = 0.65f, stiffness = 300f)) + fadeIn(tween(200))

  /** Expand exit: shrink vertically + fade out. */
  public val expandExit: ExitTransition = shrinkVertically(tween(200)) + fadeOut(tween(150))

  // Dialog Transitions
  // Route-level: scrim fade only (dialogScrimEnter/Exit)
  // Content-level: scale + fade for dialog card (dialogEnter/Exit)

  /** Dialog scrim enter: fade in only (used at route level). */
  public val dialogScrimEnter: EnterTransition = fadeIn(tween(200))

  /** Dialog scrim exit: fade out only (used at route level). */
  public val dialogScrimExit: ExitTransition = fadeOut(tween(150))

  /** Dialog content enter: scale from 85% with spring + fade in (used by ConfirmationDialog). */
  public val dialogEnter: EnterTransition =
    scaleIn(initialScale = 0.85f, animationSpec = standardSpring) + fadeIn(tween(200))

  /** Dialog content exit: scale to 90% + fade out (used by ConfirmationDialog). */
  public val dialogExit: ExitTransition =
    scaleOut(targetScale = 0.9f, animationSpec = tween(150)) + fadeOut(tween(150))

  // PackBrowser Source-Dependent Transitions
  //
  // PackBrowser animates differently depending on navigation origin:
  //   - From Settings: horizontal slide-over (feels like pushing a stack)
  //   - From WidgetInfo: crossfade (shared element card morph is primary)
  //   - Default: simple crossfade (fallback)

  /** PackBrowser enter from Settings -- horizontal slide-over with spring. */
  public val packBrowserEnterFromSettings: EnterTransition =
    slideInHorizontally(standardSpringInt) { it } + fadeIn(tween(200))

  /** PackBrowser enter from WidgetInfo -- minimal crossfade (shared element drives). */
  public val packBrowserEnterFromWidgetInfo: EnterTransition = fadeIn(tween(300))

  /** PackBrowser enter default -- simple crossfade fallback. */
  public val packBrowserEnterDefault: EnterTransition = fadeIn(tween(200))

  /** PackBrowser pop exit to Settings -- slide out to right. */
  public val packBrowserPopExitToSettings: ExitTransition =
    slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200))

  /** PackBrowser pop exit to WidgetInfo -- minimal crossfade (shared element drives). */
  public val packBrowserPopExitToWidgetInfo: ExitTransition = fadeOut(tween(300))

  /** PackBrowser pop exit default -- simple crossfade fallback. */
  public val packBrowserPopExitDefault: ExitTransition = fadeOut(tween(200))
}
