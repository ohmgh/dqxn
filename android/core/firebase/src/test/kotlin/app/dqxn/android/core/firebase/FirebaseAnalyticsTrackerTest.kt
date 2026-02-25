package app.dqxn.android.core.firebase

import android.os.Bundle
import app.dqxn.android.sdk.analytics.AnalyticsEvent
import com.google.common.truth.Truth.assertThat
import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FirebaseAnalyticsTrackerTest {

  private lateinit var firebaseAnalytics: FirebaseAnalytics
  private lateinit var tracker: FirebaseAnalyticsTracker

  @BeforeEach
  fun setUp() {
    firebaseAnalytics = mockk(relaxed = true)
    tracker = FirebaseAnalyticsTracker(firebaseAnalytics)
  }

  @Test
  fun `isEnabled returns false by default`() {
    assertThat(tracker.isEnabled()).isFalse()
  }

  @Test
  fun `constructor disables firebase collection`() {
    verify { firebaseAnalytics.setAnalyticsCollectionEnabled(false) }
  }

  @Test
  fun `track is no-op at default state before any setEnabled call`() {
    tracker.track(AnalyticsEvent.AppLaunch)
    verify(exactly = 0) { firebaseAnalytics.logEvent(any(), any()) }
  }

  @Test
  fun `setEnabled false disables tracking`() {
    every { firebaseAnalytics.setAnalyticsCollectionEnabled(any()) } just runs

    tracker.setEnabled(false)

    assertThat(tracker.isEnabled()).isFalse()
    verify { firebaseAnalytics.setAnalyticsCollectionEnabled(false) }
  }

  @Test
  fun `setEnabled true enables tracking`() {
    every { firebaseAnalytics.setAnalyticsCollectionEnabled(any()) } just runs

    tracker.setEnabled(false)
    tracker.setEnabled(true)

    assertThat(tracker.isEnabled()).isTrue()
    verify { firebaseAnalytics.setAnalyticsCollectionEnabled(true) }
  }

  @Test
  fun `track delegates AppLaunch event to firebase`() {
    tracker.setEnabled(true)
    tracker.track(AnalyticsEvent.AppLaunch)

    val nameSlot = slot<String>()
    verify { firebaseAnalytics.logEvent(capture(nameSlot), any()) }
    assertThat(nameSlot.captured).isEqualTo("app_launch")
  }

  @Test
  fun `track delegates WidgetAdded event with correct name`() {
    tracker.setEnabled(true)
    val event = AnalyticsEvent.WidgetAdded(typeId = "essentials:clock")

    tracker.track(event)

    val nameSlot = slot<String>()
    verify { firebaseAnalytics.logEvent(capture(nameSlot), any<Bundle>()) }
    assertThat(nameSlot.captured).isEqualTo("widget_added")
  }

  @Test
  fun `track passes non-null bundle for events with params`() {
    tracker.setEnabled(true)
    val event = AnalyticsEvent.WidgetAdded(typeId = "essentials:clock")

    tracker.track(event)

    // Bundle contents are stubs in unit tests (isReturnDefaultValues = true),
    // so we verify a bundle was passed (not null) and the correct event name.
    val bundleSlot = slot<Bundle>()
    verify { firebaseAnalytics.logEvent("widget_added", capture(bundleSlot)) }
    assertThat(bundleSlot.captured).isNotNull()
  }

  @Test
  fun `track is no-op when disabled`() {
    every { firebaseAnalytics.setAnalyticsCollectionEnabled(any()) } just runs

    tracker.setEnabled(false)
    tracker.track(AnalyticsEvent.AppLaunch)

    verify(exactly = 0) { firebaseAnalytics.logEvent(any(), any()) }
  }

  @Test
  fun `setUserProperty delegates to firebase`() {
    tracker.setEnabled(true)
    tracker.setUserProperty("theme", "dark")

    verify { firebaseAnalytics.setUserProperty("theme", "dark") }
  }

  @Test
  fun `setUserProperty is no-op when disabled`() {
    every { firebaseAnalytics.setAnalyticsCollectionEnabled(any()) } just runs

    tracker.setEnabled(false)
    tracker.setUserProperty("theme", "dark")

    verify(exactly = 0) { firebaseAnalytics.setUserProperty(any(), any()) }
  }

  @Test
  fun `track delegates SessionEnd event with correct name`() {
    tracker.setEnabled(true)
    val event =
      AnalyticsEvent.SessionEnd(
        durationMs = 60_000L,
        widgetCount = 5,
        editCount = 2,
        jankPercent = 1.5f,
        peakThermalLevel = "NORMAL",
        widgetRenderFailures = 0,
        providerErrors = 1,
      )

    tracker.track(event)

    verify { firebaseAnalytics.logEvent("session_end", any<Bundle>()) }
  }

  @Test
  fun `track delegates OnboardingComplete data object`() {
    tracker.setEnabled(true)
    tracker.track(AnalyticsEvent.OnboardingComplete)

    verify { firebaseAnalytics.logEvent("onboarding_complete", any()) }
  }

  @Test
  fun `track delegates ThemeChanged event`() {
    tracker.setEnabled(true)
    val event = AnalyticsEvent.ThemeChanged(themeId = "midnight", isDark = true)

    tracker.track(event)

    verify { firebaseAnalytics.logEvent("theme_changed", any<Bundle>()) }
  }

  @Test
  fun `resetAnalyticsData delegates to firebase`() {
    tracker.resetAnalyticsData()

    verify { firebaseAnalytics.resetAnalyticsData() }
  }
}
