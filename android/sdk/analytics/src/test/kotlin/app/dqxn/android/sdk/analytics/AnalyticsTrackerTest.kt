package app.dqxn.android.sdk.analytics

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test

class AnalyticsTrackerTest {

  @Test
  fun `NoOpAnalyticsTracker isEnabled returns false`() {
    assertThat(NoOpAnalyticsTracker.isEnabled()).isFalse()
  }

  @Test
  fun `NoOpAnalyticsTracker track does not throw`() {
    // Should not throw on any event
    NoOpAnalyticsTracker.track(AnalyticsEvent.AppLaunch)
    NoOpAnalyticsTracker.track(AnalyticsEvent.WidgetAdded("essentials:clock"))
    NoOpAnalyticsTracker.track(
      AnalyticsEvent.SessionEnd(
        durationMs = 1000,
        widgetCount = 5,
        editCount = 2,
        jankPercent = 1.5f,
        peakThermalLevel = "NOMINAL",
        widgetRenderFailures = 0,
        providerErrors = 0,
      )
    )
  }

  @Test
  fun `WidgetAdded has correct name and params`() {
    val event = AnalyticsEvent.WidgetAdded("essentials:clock")

    assertThat(event.name).isEqualTo("widget_added")
    assertThat(event.params["type_id"]).isEqualTo("essentials:clock")
  }

  @Test
  fun `SessionEnd includes session quality metrics`() {
    val event =
      AnalyticsEvent.SessionEnd(
        durationMs = 60_000,
        widgetCount = 8,
        editCount = 3,
        jankPercent = 2.5f,
        peakThermalLevel = "DEGRADED",
        widgetRenderFailures = 1,
        providerErrors = 2,
      )

    assertThat(event.name).isEqualTo("session_end")
    assertThat(event.params).containsEntry("jank_percent", 2.5f)
    assertThat(event.params).containsEntry("peak_thermal_level", "DEGRADED")
    assertThat(event.params).containsEntry("widget_render_failures", 1)
    assertThat(event.params).containsEntry("provider_errors", 2)
    assertThat(event.params).containsEntry("duration_ms", 60_000L)
    assertThat(event.params).containsEntry("widget_count", 8)
    assertThat(event.params).containsEntry("edit_count", 3)
  }

  @Test
  fun `UpsellImpression includes trigger_source`() {
    val event = AnalyticsEvent.UpsellImpression(trigger = "widget_limit", packId = "plus")

    assertThat(event.name).isEqualTo("upsell_impression")
    assertThat(event.params["trigger_source"]).isEqualTo("widget_limit")
    assertThat(event.params["pack_id"]).isEqualTo("plus")
  }

  @Test
  fun `PackAnalytics prepends packId to event params`() {
    val delegate = mockk<AnalyticsTracker>(relaxed = true)
    val packAnalytics = PackAnalytics(packId = "essentials", delegate = delegate)

    packAnalytics.track(AnalyticsEvent.WidgetAdded("essentials:clock"))

    val eventSlot = slot<AnalyticsEvent>()
    verify { delegate.track(capture(eventSlot)) }

    val captured = eventSlot.captured
    assertThat(captured.name).isEqualTo("widget_added")
    assertThat(captured.params["pack_id"]).isEqualTo("essentials")
    assertThat(captured.params["type_id"]).isEqualTo("essentials:clock")
  }

  @Test
  fun `all event subclasses have non-empty name`() {
    val events =
      listOf(
        AnalyticsEvent.AppLaunch,
        AnalyticsEvent.OnboardingComplete,
        AnalyticsEvent.FirstWidgetAdded,
        AnalyticsEvent.FirstCustomization("theme"),
        AnalyticsEvent.WidgetAdded("t"),
        AnalyticsEvent.WidgetRemoved("t"),
        AnalyticsEvent.WidgetSettingsChanged("t", "s"),
        AnalyticsEvent.ThemeChanged("t", true),
        AnalyticsEvent.ThemePreviewStarted("t"),
        AnalyticsEvent.ThemePreviewCommitted("t"),
        AnalyticsEvent.ThemePreviewReverted("t"),
        AnalyticsEvent.UpsellImpression("trigger", "pack"),
        AnalyticsEvent.UpsellConversion("trigger", "pack"),
        AnalyticsEvent.SessionStart,
        AnalyticsEvent.SessionEnd(0, 0, 0, 0f, "NOMINAL", 0, 0),
        AnalyticsEvent.EditModeEntered,
        AnalyticsEvent.EditModeExited(0, 0, 0, 0),
        AnalyticsEvent.ProfileCreated,
        AnalyticsEvent.ProfileSwitched("p"),
        AnalyticsEvent.ProfileDeleted,
      )

    events.forEach { event -> assertThat(event.name).isNotEmpty() }
  }
}
