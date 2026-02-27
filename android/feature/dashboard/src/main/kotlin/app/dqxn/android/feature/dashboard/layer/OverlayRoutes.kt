package app.dqxn.android.feature.dashboard.layer

import kotlinx.serialization.Serializable

/**
 * Type-safe route classes for the overlay navigation graph.
 *
 * Each route maps to a composable destination in [OverlayNavHost]. Using `@Serializable` with
 * Navigation Compose 2.8+ enables compile-time route safety via `composable<RouteType>` instead of
 * string-based `composable("route_string")`.
 *
 * Start destination is [EmptyRoute] (no overlay visible, Layer 0 shows through).
 */

/** Empty route -- no overlay. Dashboard Layer 0 is visible through the NavHost. */
@Serializable public data object EmptyRoute

/** Widget picker overlay -- grid of available widgets grouped by pack. */
@Serializable public data object WidgetPickerRoute

/** Main settings overlay -- Appearance, Behavior, Data & Privacy, Danger Zone sections. */
@Serializable public data object SettingsRoute

/**
 * Per-widget settings overlay -- 3-tab pager (Feature/Data Source/Info).
 *
 * Uses [ExitTransition.None] / [EnterTransition.None] per replication advisory section 2 (jankless
 * preview navigation): widget preview stays visible beneath child overlays.
 */
@Serializable public data class WidgetSettingsRoute(val widgetId: String)

/**
 * Provider setup wizard overlay -- paginated setup flow for a specific provider.
 *
 * [providerId] identifies which provider's setup schema to display.
 */
@Serializable public data class SetupRoute(val providerId: String)

/**
 * Auto-switch mode selector overlay -- preview-type transitions.
 *
 * Allows user to choose between LIGHT, DARK, SYSTEM, SOLAR_AUTO, ILLUMINANCE_AUTO modes.
 */
@Serializable public data object AutoSwitchModeRoute

/**
 * Theme selector overlay -- preview-type transitions.
 *
 * Caller-managed preview: Settings sets preview theme BEFORE navigating here. popEnter uses
 * fadeIn(150ms) NOT previewEnter per replication advisory section 4. [isDark] filters the theme
 * list to dark or light themes.
 */
@Serializable public data class ThemeSelectorRoute(val isDark: Boolean = false)

/**
 * Theme studio overlay -- preview-type transitions (sub-screen of ThemeSelector).
 *
 * [themeId] is the ID of the theme being edited. Null means "create new custom theme". When
 * navigated from clone action, [themeId] is the source theme's ID and ThemeStudio creates a copy
 * with a new custom ID.
 *
 * popEnter from ThemeStudioRoute back to ThemeSelector uses fadeIn(150ms) -- same pattern as
 * ThemeSelector popEnter to prevent double-slide.
 */
@Serializable public data class ThemeStudioRoute(val themeId: String? = null)

/**
 * Diagnostics overlay -- hub-type transitions.
 *
 * Aggregates provider health, session recording, diagnostic snapshots, and observability metrics.
 */
@Serializable public data object DiagnosticsRoute

/**
 * Onboarding overlay -- hub-type transitions.
 *
 * First-run flow: consent, disclaimer, theme selection, edit tour. Navigated to automatically when
 * [hasCompletedOnboarding] is false.
 */
@Serializable public data object OnboardingRoute
