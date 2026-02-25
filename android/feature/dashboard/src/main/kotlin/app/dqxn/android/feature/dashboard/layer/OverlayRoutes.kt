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
@Serializable
public data object EmptyRoute

/** Widget picker overlay -- grid of available widgets grouped by pack. */
@Serializable
public data object WidgetPickerRoute

/** Main settings overlay -- Appearance, Behavior, Data & Privacy, Danger Zone sections. */
@Serializable
public data object SettingsRoute

/**
 * Per-widget settings overlay -- 3-tab pager (Feature/Data Source/Info).
 *
 * Uses [ExitTransition.None] / [EnterTransition.None] per replication advisory section 2
 * (jankless preview navigation): widget preview stays visible beneath child overlays.
 */
@Serializable
public data class WidgetSettingsRoute(val widgetId: String)

/**
 * Provider setup wizard overlay -- paginated setup flow for a specific provider.
 *
 * [providerId] identifies which provider's setup schema to display.
 */
@Serializable
public data class SetupRoute(val providerId: String)
