package app.dqxn.android.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.observability.metrics.MetricsSnapshot
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Simple text-based observability dashboard showing frame time metrics and jank percentage.
 *
 * Displays P50, P95, P99 frame times derived from the [MetricsSnapshot] histogram and a jank
 * percentage indicator (frames > 16ms).
 *
 * @param metricsSnapshot The current metrics snapshot, or null if no data available.
 */
@Composable
public fun ObservabilityDashboard(
  metricsSnapshot: MetricsSnapshot?,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val dividerColor = theme.widgetBorderColor.copy(alpha = 0.3f)

  Column(
    modifier = modifier.fillMaxSize().testTag("observability_dashboard").padding(16.dp),
  ) {
    Text(
      text = "Observability Metrics",
      style = DashboardTypography.title,
      color = theme.primaryTextColor,
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (metricsSnapshot == null || metricsSnapshot.totalFrameCount == 0L) {
      Text(
        text = "No metrics data available",
        style = DashboardTypography.description,
        color = theme.secondaryTextColor,
      )
      return
    }

    val frameMetrics = computeFrameMetrics(metricsSnapshot)

    // Frame time percentiles
    Text(
      text = "Frame Times",
      style = DashboardTypography.itemTitle,
      color = theme.primaryTextColor,
    )
    Spacer(modifier = Modifier.height(8.dp))

    MetricRow(
      label = "P50",
      value = "${frameMetrics.p50}ms",
      testTag = "frame_time_p50",
    )
    MetricRow(
      label = "P95",
      value = "${frameMetrics.p95}ms",
      testTag = "frame_time_p95",
    )
    MetricRow(
      label = "P99",
      value = "${frameMetrics.p99}ms",
      testTag = "frame_time_p99",
    )

    HorizontalDivider(
      color = dividerColor,
      modifier = Modifier.padding(vertical = 12.dp),
    )

    // Jank percentage
    MetricRow(
      label = "Jank",
      value = "%.1f%%".format(frameMetrics.jankPercent),
      testTag = "jank_percent",
    )

    HorizontalDivider(
      color = dividerColor,
      modifier = Modifier.padding(vertical = 12.dp),
    )

    // Memory usage
    val runtime = Runtime.getRuntime()
    val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
    MetricRow(
      label = "Memory",
      value = "${usedMemoryMb}MB / ${totalMemoryMb}MB",
      testTag = "memory_usage",
    )

    HorizontalDivider(
      color = dividerColor,
      modifier = Modifier.padding(vertical = 12.dp),
    )

    // Total frame count
    Text(
      text = "Total frames: ${metricsSnapshot.totalFrameCount}",
      style = DashboardTypography.caption,
      color = theme.secondaryTextColor,
    )
  }
}

@Composable
private fun MetricRow(
  label: String,
  value: String,
  testTag: String,
) {
  val theme = LocalDashboardTheme.current

  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(testTag),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = DashboardTypography.description,
      color = theme.primaryTextColor,
    )
    Text(
      text = value,
      style = DashboardTypography.description,
      color = theme.accentColor,
    )
  }
}

/**
 * Frame metrics computed from a [MetricsSnapshot] histogram.
 *
 * Histogram buckets: <8ms, <12ms, <16ms, <24ms, <33ms, >33ms Bucket midpoints used for percentile
 * estimation: 4, 10, 14, 20, 28, 40
 */
@Stable
internal data class FrameMetrics(
  val p50: Int,
  val p95: Int,
  val p99: Int,
  val jankPercent: Double,
)

/** Bucket midpoints in ms for percentile estimation. */
private val BUCKET_MIDPOINTS = intArrayOf(4, 10, 14, 20, 28, 40)

@Stable
internal fun computeFrameMetrics(snapshot: MetricsSnapshot): FrameMetrics {
  val histogram = snapshot.frameHistogram
  val total = snapshot.totalFrameCount

  if (total == 0L) {
    return FrameMetrics(p50 = 0, p95 = 0, p99 = 0, jankPercent = 0.0)
  }

  val p50 = percentileFromHistogram(histogram.map { it }, total, 0.50)
  val p95 = percentileFromHistogram(histogram.map { it }, total, 0.95)
  val p99 = percentileFromHistogram(histogram.map { it }, total, 0.99)

  // Jank = frames > 16ms (buckets 3, 4, 5: <24ms, <33ms, >33ms)
  val jankFrames =
    if (histogram.size >= 6) {
      histogram[3] + histogram[4] + histogram[5]
    } else {
      0L
    }
  val jankPercent = (jankFrames.toDouble() / total) * 100.0

  return FrameMetrics(p50 = p50, p95 = p95, p99 = p99, jankPercent = jankPercent)
}

private fun percentileFromHistogram(
  buckets: List<Long>,
  total: Long,
  percentile: Double,
): Int {
  val targetCount = (total * percentile).toLong()
  var cumulative = 0L
  for (i in buckets.indices) {
    cumulative += buckets[i]
    if (cumulative >= targetCount) {
      return if (i < BUCKET_MIDPOINTS.size) BUCKET_MIDPOINTS[i] else BUCKET_MIDPOINTS.last()
    }
  }
  return BUCKET_MIDPOINTS.last()
}
