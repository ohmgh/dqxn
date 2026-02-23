package app.dqxn.android.sdk.observability.metrics

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * Read-only snapshot of all collected metrics at a point in time. Used by F13.5 state dumps and
 * F13.6 debug overlays.
 */
@Immutable
public data class MetricsSnapshot(
  val frameHistogram: ImmutableList<Long>,
  val totalFrameCount: Long,
  val widgetDrawTimes: ImmutableMap<String, List<Long>>,
  val providerLatencies: ImmutableMap<String, List<Long>>,
  val recompositionCounts: ImmutableMap<String, Long>,
  val captureTimestamp: Long,
)
