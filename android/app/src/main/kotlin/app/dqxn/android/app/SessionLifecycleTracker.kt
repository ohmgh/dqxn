package app.dqxn.android.app

import app.dqxn.android.core.thermal.ThermalLevel
import app.dqxn.android.core.thermal.ThermalMonitor
import app.dqxn.android.sdk.analytics.AnalyticsEvent
import app.dqxn.android.sdk.analytics.AnalyticsTracker
import app.dqxn.android.sdk.observability.health.WidgetHealthMonitor
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Tracks app session lifecycle and fires [AnalyticsEvent.SessionStart] / [AnalyticsEvent.SessionEnd]
 * with quality metrics (F12.7): jank%, peak thermal level, render failures, provider errors.
 *
 * All events gated on consent via [AnalyticsTracker.isEnabled] internally.
 */
@Singleton
public class SessionLifecycleTracker @Inject constructor(
  private val analyticsTracker: AnalyticsTracker,
  private val metricsCollector: MetricsCollector,
  private val healthMonitor: WidgetHealthMonitor,
  private val thermalMonitor: ThermalMonitor,
  private val clock: () -> Long = System::currentTimeMillis,
) {

  private var sessionStartTimeMs: Long = 0L
  private var lastWidgetCount: Int = 0
  private var peakThermalLevel: ThermalLevel = ThermalLevel.NORMAL

  /**
   * Called when a session begins. Records start time and fires [AnalyticsEvent.SessionStart].
   *
   * @param widgetCount Number of widgets currently on the dashboard.
   */
  public fun onSessionStart(widgetCount: Int) {
    sessionStartTimeMs = clock()
    lastWidgetCount = widgetCount
    peakThermalLevel = thermalMonitor.thermalLevel.value
    analyticsTracker.track(AnalyticsEvent.SessionStart)
  }

  /**
   * Updates peak thermal level. Call from a collector observing [ThermalMonitor.thermalLevel].
   */
  public fun recordThermalLevel(level: ThermalLevel) {
    if (level.ordinal > peakThermalLevel.ordinal) {
      peakThermalLevel = level
    }
  }

  /**
   * Called when a session ends. Collects quality metrics and fires [AnalyticsEvent.SessionEnd].
   *
   * @param editCount Number of edit-mode actions during the session.
   */
  public fun onSessionEnd(editCount: Int) {
    val durationMs = if (sessionStartTimeMs > 0L) {
      clock() - sessionStartTimeMs
    } else {
      0L
    }

    val metricsSnapshot = metricsCollector.snapshot()
    val jankPercent = computeJankPercent(metricsSnapshot)

    val statuses = healthMonitor.allStatuses()
    val renderFailures = statuses.values.count {
      it.status == WidgetHealthMonitor.Status.CRASHED ||
        it.status == WidgetHealthMonitor.Status.STALLED_RENDER
    }
    val providerErrors = statuses.values.count {
      it.status == WidgetHealthMonitor.Status.STALE_DATA
    }

    analyticsTracker.track(
      AnalyticsEvent.SessionEnd(
        durationMs = durationMs,
        widgetCount = lastWidgetCount,
        editCount = editCount,
        jankPercent = jankPercent,
        peakThermalLevel = peakThermalLevel.name,
        widgetRenderFailures = renderFailures,
        providerErrors = providerErrors,
      )
    )

    // Reset for next session
    sessionStartTimeMs = 0L
    peakThermalLevel = ThermalLevel.NORMAL
  }

  /**
   * Computes jank percentage from frame histogram.
   * Jank = frames > 16ms (buckets 3, 4, 5) / total * 100.
   */
  private fun computeJankPercent(
    snapshot: app.dqxn.android.sdk.observability.metrics.MetricsSnapshot,
  ): Float {
    val total = snapshot.totalFrameCount
    if (total == 0L) return 0f
    val histogram = snapshot.frameHistogram
    val jankFrames = if (histogram.size >= 6) {
      histogram[3] + histogram[4] + histogram[5]
    } else {
      0L
    }
    return (jankFrames.toFloat() / total) * 100f
  }
}
