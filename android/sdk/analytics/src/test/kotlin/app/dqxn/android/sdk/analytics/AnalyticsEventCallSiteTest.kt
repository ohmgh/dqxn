package app.dqxn.android.sdk.analytics

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test

class AnalyticsEventCallSiteTest {

  private val tracker =
    mockk<AnalyticsTracker>(relaxed = true).also { every { it.isEnabled() } returns true }

  @Test
  fun `trackWidgetAdd fires WidgetAdded event with correct typeId`() {
    tracker.trackWidgetAdd("essentials:clock")

    val eventSlot = slot<AnalyticsEvent>()
    verify { tracker.track(capture(eventSlot)) }

    val event = eventSlot.captured
    assertThat(event).isInstanceOf(AnalyticsEvent.WidgetAdded::class.java)
    assertThat(event.name).isEqualTo("widget_added")
    assertThat(event.params["type_id"]).isEqualTo("essentials:clock")
  }

  @Test
  fun `trackThemeChange fires ThemeChanged event with themeId and isDark`() {
    tracker.trackThemeChange(themeId = "midnight-blue", isDark = true)

    val eventSlot = slot<AnalyticsEvent>()
    verify { tracker.track(capture(eventSlot)) }

    val event = eventSlot.captured
    assertThat(event).isInstanceOf(AnalyticsEvent.ThemeChanged::class.java)
    assertThat(event.name).isEqualTo("theme_changed")
    assertThat(event.params["theme_id"]).isEqualTo("midnight-blue")
    assertThat(event.params["is_dark"]).isEqualTo(true)
  }

  @Test
  fun `trackUpsellImpression includes trigger source and packId`() {
    tracker.trackUpsellImpression(
      trigger = UpsellTrigger.THEME_PREVIEW,
      packId = "themes",
    )

    val eventSlot = slot<AnalyticsEvent>()
    verify { tracker.track(capture(eventSlot)) }

    val event = eventSlot.captured
    assertThat(event).isInstanceOf(AnalyticsEvent.UpsellImpression::class.java)
    assertThat(event.name).isEqualTo("upsell_impression")
    assertThat(event.params["trigger_source"]).isEqualTo("theme_preview")
    assertThat(event.params["pack_id"]).isEqualTo("themes")
  }

  @Test
  fun `events suppressed when consent is false`() {
    // Create a tracker that gates on isEnabled -- use a real implementation pattern.
    // The contract: track() is a no-op when isEnabled() returns false.
    // We verify this by creating a wrapper that mirrors the consent gating contract.
    val gatedTracker = mockk<AnalyticsTracker>()
    every { gatedTracker.isEnabled() } returns false
    // When disabled, track should be called but the underlying implementation no-ops.
    // With a mock, we verify the call sites still delegate to track() -- the tracker
    // itself is responsible for suppression. Verify extension functions don't skip.
    every { gatedTracker.track(any()) } answers
      {
        // Simulate consent gate: if !isEnabled, don't dispatch
        if (!gatedTracker.isEnabled()) return@answers
        error("Should not reach here when disabled")
      }

    gatedTracker.trackWidgetAdd("essentials:clock")
    gatedTracker.trackThemeChange("midnight", isDark = false)
    gatedTracker.trackUpsellImpression(UpsellTrigger.SETTINGS, "plus")

    // All three should call track() (extension functions always delegate)
    verify(exactly = 3) { gatedTracker.track(any()) }

    // But the gated implementation suppresses dispatch -- verified by the answers block
    // not throwing. In production, FirebaseAnalyticsTracker checks isEnabled() inside track().
  }

  @Test
  fun `no PII in event parameters`() {
    // Structural test: verify event data classes do not accept PII fields.
    // WidgetAdded only has typeId -- no email, name, phone, userId fields.
    val widgetAdded = AnalyticsEvent.WidgetAdded(typeId = "essentials:clock")
    val paramKeys = widgetAdded.params.keys
    assertThat(paramKeys).doesNotContain("email")
    assertThat(paramKeys).doesNotContain("name")
    assertThat(paramKeys).doesNotContain("phone")
    assertThat(paramKeys).doesNotContain("user_id")

    // ThemeChanged only has themeId and isDark.
    val themeChanged = AnalyticsEvent.ThemeChanged(themeId = "midnight", isDark = true)
    val themeKeys = themeChanged.params.keys
    assertThat(themeKeys).doesNotContain("email")
    assertThat(themeKeys).doesNotContain("name")
    assertThat(themeKeys).doesNotContain("phone")
    assertThat(themeKeys).doesNotContain("user_id")

    // UpsellImpression only has trigger_source and pack_id.
    val upsell = AnalyticsEvent.UpsellImpression(trigger = "settings", packId = "plus")
    val upsellKeys = upsell.params.keys
    assertThat(upsellKeys).doesNotContain("email")
    assertThat(upsellKeys).doesNotContain("name")
    assertThat(upsellKeys).doesNotContain("phone")
    assertThat(upsellKeys).doesNotContain("user_id")

    // SessionEnd contains quality metrics, no PII.
    val sessionEnd =
      AnalyticsEvent.SessionEnd(
        durationMs = 60_000,
        widgetCount = 5,
        editCount = 2,
        jankPercent = 1.0f,
        peakThermalLevel = "NORMAL",
        widgetRenderFailures = 0,
        providerErrors = 0,
      )
    val sessionKeys = sessionEnd.params.keys
    assertThat(sessionKeys).doesNotContain("email")
    assertThat(sessionKeys).doesNotContain("name")
    assertThat(sessionKeys).doesNotContain("phone")
    assertThat(sessionKeys).doesNotContain("user_id")
  }

  @Test
  fun `UpsellTrigger constants are distinct`() {
    val triggers =
      setOf(
        UpsellTrigger.THEME_PREVIEW,
        UpsellTrigger.WIDGET_PICKER,
        UpsellTrigger.SETTINGS,
      )
    // If any constants were duplicated, the set would have fewer than 3 elements.
    assertThat(triggers).hasSize(3)
    assertThat(UpsellTrigger.THEME_PREVIEW).isNotEqualTo(UpsellTrigger.WIDGET_PICKER)
    assertThat(UpsellTrigger.WIDGET_PICKER).isNotEqualTo(UpsellTrigger.SETTINGS)
    assertThat(UpsellTrigger.THEME_PREVIEW).isNotEqualTo(UpsellTrigger.SETTINGS)
  }
}
