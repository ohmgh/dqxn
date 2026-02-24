package app.dqxn.android.debug.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import app.dqxn.android.sdk.observability.metrics.MetricsSnapshot

/**
 * Debug overlay showing frame timing statistics. Positioned at top-right.
 *
 * Displays FPS counter (derived from frame histogram), frame time percentiles (P50/P95/P99),
 * and jank frame count (>33ms bucket). At Phase 6, [metricsCollector] will have empty data --
 * overlay shows zeros until the dashboard shell starts recording frames in Phase 7.
 *
 * Uses [derivedStateOf] to defer reads and [graphicsLayer] for render isolation.
 */
@Composable
internal fun FrameStatsOverlay(
  metricsCollector: MetricsCollector,
  modifier: Modifier = Modifier,
) {
  val snapshot: MetricsSnapshot by remember {
    derivedStateOf { metricsCollector.snapshot() }
  }

  val fpsEstimate: String by remember {
    derivedStateOf {
      val total = snapshot.totalFrameCount
      if (total == 0L) "-- fps" else estimateFps(snapshot)
    }
  }

  val jankCount: Long by remember {
    derivedStateOf {
      val histogram = snapshot.frameHistogram
      if (histogram.isEmpty()) 0L else histogram.last() // >33ms bucket
    }
  }

  Column(
    modifier =
      modifier
        .graphicsLayer()
        .background(OverlayBackground)
        .padding(8.dp),
    horizontalAlignment = Alignment.End,
  ) {
    OverlayText(text = fpsEstimate)

    val total = snapshot.totalFrameCount
    if (total > 0L) {
      val percentiles = remember(snapshot.frameHistogram) {
        computePercentiles(snapshot.frameHistogram, total)
      }
      OverlayText(text = "P50: ${percentiles.p50}ms  P95: ${percentiles.p95}ms  P99: ${percentiles.p99}ms")
      OverlayText(text = "Jank: $jankCount")
      OverlayText(text = "Frames: $total")
    } else {
      OverlayText(text = "No frame data")
    }
  }
}

/**
 * Estimates FPS from the frame histogram. Uses weighted midpoints of each bucket to
 * compute average frame time, then converts to FPS.
 */
private fun estimateFps(snapshot: MetricsSnapshot): String {
  val histogram = snapshot.frameHistogram
  if (histogram.size < BUCKET_COUNT) return "-- fps"

  // Bucket midpoints in ms: <8, <12, <16, <24, <33, >33
  val midpoints = longArrayOf(4, 10, 14, 20, 28, 40)
  var totalMs = 0L
  var totalFrames = 0L
  for (i in 0 until minOf(histogram.size, BUCKET_COUNT)) {
    totalMs += histogram[i] * midpoints[i]
    totalFrames += histogram[i]
  }
  if (totalFrames == 0L) return "-- fps"
  val avgMs = totalMs.toDouble() / totalFrames
  if (avgMs <= 0.0) return "-- fps"
  val fps = (1000.0 / avgMs).toInt().coerceAtMost(MAX_DISPLAY_FPS)
  return "$fps fps"
}

private data class Percentiles(val p50: Long, val p95: Long, val p99: Long)

/**
 * Computes P50/P95/P99 frame time percentiles from the histogram.
 * Uses bucket upper bounds as the representative value for each bucket.
 */
private fun computePercentiles(
  histogram: List<Long>,
  totalFrames: Long,
): Percentiles {
  if (histogram.size < BUCKET_COUNT || totalFrames == 0L) {
    return Percentiles(0, 0, 0)
  }
  // Bucket upper bounds in ms
  val bounds = longArrayOf(8, 12, 16, 24, 33, 50)

  fun percentile(fraction: Double): Long {
    val target = (totalFrames * fraction).toLong()
    var cumulative = 0L
    for (i in 0 until minOf(histogram.size, BUCKET_COUNT)) {
      cumulative += histogram[i]
      if (cumulative >= target) return bounds[i]
    }
    return bounds.last()
  }

  return Percentiles(
    p50 = percentile(0.50),
    p95 = percentile(0.95),
    p99 = percentile(0.99),
  )
}

private const val BUCKET_COUNT = 6
private const val MAX_DISPLAY_FPS = 120

@Composable
private fun OverlayText(text: String) {
  androidx.compose.material3.Text(
    text = text,
    style = TextStyle(
      color = OverlayTextColor,
      fontSize = 12.sp,
      fontFamily = FontFamily.Monospace,
    ),
  )
}

private val OverlayBackground = Color(0xCC1A1A1A)
private val OverlayTextColor = Color(0xFFCCCCCC)
