package app.dqxn.android.app

import app.dqxn.android.core.thermal.ThermalLevel
import app.dqxn.android.core.thermal.ThermalMonitor
import app.dqxn.android.sdk.analytics.AnalyticsEvent
import app.dqxn.android.sdk.analytics.AnalyticsTracker
import app.dqxn.android.sdk.observability.health.WidgetHealthMonitor
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class SessionLifecycleTrackerTest {

  private val analyticsTracker = mockk<AnalyticsTracker>(relaxed = true)
  private val metricsCollector = MetricsCollector()
  private val healthMonitor = mockk<WidgetHealthMonitor>(relaxed = true).also {
    every { it.allStatuses() } returns emptyMap()
  }
  private val thermalLevelFlow = MutableStateFlow(ThermalLevel.NORMAL)
  private val thermalMonitor = mockk<ThermalMonitor>().also {
    every { it.thermalLevel } returns thermalLevelFlow
  }

  private var fakeTimeMs = 1_000_000L
  private lateinit var tracker: SessionLifecycleTracker

  @BeforeEach
  fun setUp() {
    fakeTimeMs = 1_000_000L
    tracker = SessionLifecycleTracker(
      analyticsTracker = analyticsTracker,
      metricsCollector = metricsCollector,
      healthMonitor = healthMonitor,
      thermalMonitor = thermalMonitor,
      clock = { fakeTimeMs },
    )
  }

  @Test
  fun `onSessionStart fires SessionStart event`() {
    tracker.onSessionStart(widgetCount = 5)

    val eventSlot = slot<AnalyticsEvent>()
    verify { analyticsTracker.track(capture(eventSlot)) }

    assertThat(eventSlot.captured).isEqualTo(AnalyticsEvent.SessionStart)
    assertThat(eventSlot.captured.name).isEqualTo("session_start")
  }

  @Test
  fun `onSessionEnd calculates duration correctly`() {
    tracker.onSessionStart(widgetCount = 3)
    fakeTimeMs += 60_000L // Advance 60 seconds
    tracker.onSessionEnd(editCount = 2)

    val events = mutableListOf<AnalyticsEvent>()
    verify { analyticsTracker.track(capture(events)) }

    // First event is SessionStart, second is SessionEnd
    val sessionEnd = events.last()
    assertThat(sessionEnd).isInstanceOf(AnalyticsEvent.SessionEnd::class.java)
    assertThat(sessionEnd.params["duration_ms"]).isEqualTo(60_000L)
    assertThat(sessionEnd.params["widget_count"]).isEqualTo(3)
    assertThat(sessionEnd.params["edit_count"]).isEqualTo(2)
  }

  @Test
  fun `onSessionEnd includes jank percent from metrics`() {
    // Record frames: 90 good (<16ms) + 10 jank (>16ms) = 10% jank
    repeat(90) { metricsCollector.recordFrame(10L) } // bucket 1 (<12ms)
    repeat(10) { metricsCollector.recordFrame(25L) } // bucket 3 (<33ms, >16ms = jank)

    tracker.onSessionStart(widgetCount = 1)
    tracker.onSessionEnd(editCount = 0)

    val events = mutableListOf<AnalyticsEvent>()
    verify { analyticsTracker.track(capture(events)) }

    val sessionEnd = events.last() as AnalyticsEvent.SessionEnd
    assertThat(sessionEnd.jankPercent).isEqualTo(10f)
  }

  @Test
  fun `onSessionEnd includes peak thermal level`() {
    thermalLevelFlow.value = ThermalLevel.NORMAL
    tracker.onSessionStart(widgetCount = 2)

    // Simulate thermal escalation during session
    tracker.recordThermalLevel(ThermalLevel.WARM)
    tracker.recordThermalLevel(ThermalLevel.DEGRADED)
    tracker.recordThermalLevel(ThermalLevel.WARM) // drops back but peak stays DEGRADED

    tracker.onSessionEnd(editCount = 0)

    val events = mutableListOf<AnalyticsEvent>()
    verify { analyticsTracker.track(capture(events)) }

    val sessionEnd = events.last() as AnalyticsEvent.SessionEnd
    assertThat(sessionEnd.peakThermalLevel).isEqualTo("DEGRADED")
  }

  @Test
  fun `onSessionEnd includes render failures and provider errors`() {
    every { healthMonitor.allStatuses() } returns mapOf(
      "widget-1" to WidgetHealthMonitor.WidgetHealthStatus(
        widgetId = "widget-1",
        typeId = "essentials:clock",
        status = WidgetHealthMonitor.Status.CRASHED,
      ),
      "widget-2" to WidgetHealthMonitor.WidgetHealthStatus(
        widgetId = "widget-2",
        typeId = "essentials:speed",
        status = WidgetHealthMonitor.Status.STALLED_RENDER,
      ),
      "widget-3" to WidgetHealthMonitor.WidgetHealthStatus(
        widgetId = "widget-3",
        typeId = "essentials:battery",
        status = WidgetHealthMonitor.Status.STALE_DATA,
      ),
      "widget-4" to WidgetHealthMonitor.WidgetHealthStatus(
        widgetId = "widget-4",
        typeId = "essentials:date",
        status = WidgetHealthMonitor.Status.ACTIVE,
      ),
    )

    tracker.onSessionStart(widgetCount = 4)
    tracker.onSessionEnd(editCount = 1)

    val events = mutableListOf<AnalyticsEvent>()
    verify { analyticsTracker.track(capture(events)) }

    val sessionEnd = events.last() as AnalyticsEvent.SessionEnd
    // CRASHED + STALLED_RENDER = 2 render failures
    assertThat(sessionEnd.widgetRenderFailures).isEqualTo(2)
    // STALE_DATA = 1 provider error
    assertThat(sessionEnd.providerErrors).isEqualTo(1)
  }

  @Test
  fun `session without start records zero duration`() {
    // Call onSessionEnd without prior onSessionStart
    tracker.onSessionEnd(editCount = 0)

    val eventSlot = slot<AnalyticsEvent>()
    verify { analyticsTracker.track(capture(eventSlot)) }

    val sessionEnd = eventSlot.captured as AnalyticsEvent.SessionEnd
    assertThat(sessionEnd.durationMs).isEqualTo(0L)
  }
}
