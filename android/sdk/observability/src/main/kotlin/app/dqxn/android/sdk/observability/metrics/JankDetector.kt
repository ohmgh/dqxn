package app.dqxn.android.sdk.observability.metrics

import app.dqxn.android.sdk.observability.diagnostic.AnomalyTrigger
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.warn

/**
 * Tracks consecutive janky frames (>16ms) and triggers diagnostic captures at exponential
 * thresholds: 5, 20, and 100 consecutive janky frames.
 *
 * Not folded into [MetricsCollector] -- distinct class with its own responsibility (anomaly
 * detection vs. metric recording).
 */
public class JankDetector(
  private val diagnosticCapture: DiagnosticSnapshotCapture,
  private val logger: DqxnLogger,
) {

  private var consecutiveJankyFrames: Int = 0

  /**
   * Called after each frame render. If [durationMs] > 16ms, increments the consecutive jank counter
   * and checks thresholds. If <= 16ms, resets the counter.
   */
  public fun onFrameRendered(durationMs: Long) {
    if (durationMs > JANK_THRESHOLD_MS) {
      consecutiveJankyFrames++
      checkThresholds()
    } else {
      consecutiveJankyFrames = 0
    }
  }

  private fun checkThresholds() {
    val count = consecutiveJankyFrames
    if (count in CAPTURE_THRESHOLDS) {
      logger.warn(TAG) { "Jank spike detected: $count consecutive janky frames" }
      diagnosticCapture.capture(AnomalyTrigger.JankSpike(count))
    }
  }

  private companion object {
    const val JANK_THRESHOLD_MS = 16L
    val CAPTURE_THRESHOLDS = setOf(5, 20, 100)
    val TAG = LogTag("jank-detector")
  }
}
