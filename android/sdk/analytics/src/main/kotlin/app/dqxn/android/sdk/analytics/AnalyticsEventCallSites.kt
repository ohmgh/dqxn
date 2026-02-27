package app.dqxn.android.sdk.analytics

/**
 * Extension helpers for firing analytics events at interaction call sites.
 *
 * All calls go through [AnalyticsTracker.track] which gates on consent internally via
 * [AnalyticsTracker.isEnabled]. No additional consent checks needed at call sites.
 */

// ---- F12.2: Key funnel events ----

/** Tracks a widget-added event with the given [typeId]. */
public fun AnalyticsTracker.trackWidgetAdd(typeId: String) {
  track(AnalyticsEvent.WidgetAdded(typeId = typeId))
}

/** Tracks a theme-changed event with the given [themeId] and [isDark] flag. */
public fun AnalyticsTracker.trackThemeChange(themeId: String, isDark: Boolean) {
  track(AnalyticsEvent.ThemeChanged(themeId = themeId, isDark = isDark))
}

// ---- F12.6: Upsell with trigger_source ----

/**
 * Tracks an upsell impression event. [trigger] identifies what surface triggered the upsell (see
 * [UpsellTrigger] constants). [packId] identifies the pack being upsold.
 */
public fun AnalyticsTracker.trackUpsellImpression(trigger: String, packId: String) {
  track(AnalyticsEvent.UpsellImpression(trigger = trigger, packId = packId))
}

/**
 * Well-known trigger sources for upsell impressions (F12.6). New trigger sources should be added
 * here as constants rather than inlined as strings.
 */
public object UpsellTrigger {
  public const val THEME_PREVIEW: String = "theme_preview"
  public const val WIDGET_PICKER: String = "widget_picker"
  public const val SETTINGS: String = "settings"
}
