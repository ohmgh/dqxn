package app.dqxn.android.sdk.analytics

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Sealed hierarchy of analytics events tracked across DQXN. Each subtype provides a stable [name]
 * and typed [params] map.
 */
public sealed interface AnalyticsEvent {

  /** Event name used in analytics backend (e.g., Firebase). Stable, snake_case. */
  public val name: String

  /** Event parameters. All values are primitives (String, Int, Long, Float, Boolean). */
  public val params: ImmutableMap<String, Any>
    get() = persistentMapOf()

  // ---- Funnel events (F12.2) ----

  public data object AppLaunch : AnalyticsEvent {
    override val name: String = "app_launch"
  }

  public data object OnboardingComplete : AnalyticsEvent {
    override val name: String = "onboarding_complete"
  }

  public data object FirstWidgetAdded : AnalyticsEvent {
    override val name: String = "first_widget_added"
  }

  public data class FirstCustomization(val source: String) : AnalyticsEvent {
    override val name: String = "first_customization"
    override val params: ImmutableMap<String, Any> = persistentMapOf("source" to source)
  }

  // ---- Widget events ----

  public data class WidgetAdded(val typeId: String) : AnalyticsEvent {
    override val name: String = "widget_added"
    override val params: ImmutableMap<String, Any> = persistentMapOf("type_id" to typeId)
  }

  public data class WidgetRemoved(val typeId: String) : AnalyticsEvent {
    override val name: String = "widget_removed"
    override val params: ImmutableMap<String, Any> = persistentMapOf("type_id" to typeId)
  }

  public data class WidgetSettingsChanged(val typeId: String, val setting: String) :
    AnalyticsEvent {
    override val name: String = "widget_settings_changed"
    override val params: ImmutableMap<String, Any> =
      persistentMapOf("type_id" to typeId, "setting" to setting)
  }

  // ---- Theme events ----

  public data class ThemeChanged(val themeId: String, val isDark: Boolean) : AnalyticsEvent {
    override val name: String = "theme_changed"
    override val params: ImmutableMap<String, Any> =
      persistentMapOf("theme_id" to themeId, "is_dark" to isDark)
  }

  public data class ThemePreviewStarted(val themeId: String) : AnalyticsEvent {
    override val name: String = "theme_preview_started"
    override val params: ImmutableMap<String, Any> = persistentMapOf("theme_id" to themeId)
  }

  public data class ThemePreviewCommitted(val themeId: String) : AnalyticsEvent {
    override val name: String = "theme_preview_committed"
    override val params: ImmutableMap<String, Any> = persistentMapOf("theme_id" to themeId)
  }

  public data class ThemePreviewReverted(val themeId: String) : AnalyticsEvent {
    override val name: String = "theme_preview_reverted"
    override val params: ImmutableMap<String, Any> = persistentMapOf("theme_id" to themeId)
  }

  // ---- Upsell events (F12.6) ----

  public data class UpsellImpression(val trigger: String, val packId: String) : AnalyticsEvent {
    override val name: String = "upsell_impression"
    override val params: ImmutableMap<String, Any> =
      persistentMapOf("trigger_source" to trigger, "pack_id" to packId)
  }

  public data class UpsellConversion(val trigger: String, val packId: String) : AnalyticsEvent {
    override val name: String = "upsell_conversion"
    override val params: ImmutableMap<String, Any> =
      persistentMapOf("trigger_source" to trigger, "pack_id" to packId)
  }

  // ---- Engagement events (F12.3, F12.7) ----

  public data object SessionStart : AnalyticsEvent {
    override val name: String = "session_start"
  }

  public data class SessionEnd(
    val durationMs: Long,
    val widgetCount: Int,
    val editCount: Int,
    val jankPercent: Float,
    val peakThermalLevel: String,
    val widgetRenderFailures: Int,
    val providerErrors: Int,
  ) : AnalyticsEvent {
    override val name: String = "session_end"
    override val params: ImmutableMap<String, Any> =
      persistentMapOf(
        "duration_ms" to durationMs,
        "widget_count" to widgetCount,
        "edit_count" to editCount,
        "jank_percent" to jankPercent,
        "peak_thermal_level" to peakThermalLevel,
        "widget_render_failures" to widgetRenderFailures,
        "provider_errors" to providerErrors,
      )
  }

  // ---- Edit mode events ----

  public data object EditModeEntered : AnalyticsEvent {
    override val name: String = "edit_mode_entered"
  }

  public data class EditModeExited(
    val widgetsAdded: Int,
    val widgetsRemoved: Int,
    val widgetsMoved: Int,
    val widgetsResized: Int,
  ) : AnalyticsEvent {
    override val name: String = "edit_mode_exited"
    override val params: ImmutableMap<String, Any> =
      persistentMapOf(
        "widgets_added" to widgetsAdded,
        "widgets_removed" to widgetsRemoved,
        "widgets_moved" to widgetsMoved,
        "widgets_resized" to widgetsResized,
      )
  }

  // ---- Profile events ----

  public data object ProfileCreated : AnalyticsEvent {
    override val name: String = "profile_created"
  }

  public data class ProfileSwitched(val profileId: String) : AnalyticsEvent {
    override val name: String = "profile_switched"
    override val params: ImmutableMap<String, Any> = persistentMapOf("profile_id" to profileId)
  }

  public data object ProfileDeleted : AnalyticsEvent {
    override val name: String = "profile_deleted"
  }
}
