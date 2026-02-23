package app.dqxn.android.sdk.observability.diagnostic

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.observability.metrics.MetricsSnapshot
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Immutable diagnostic snapshot captured on anomaly detection. Contains metrics state, active trace
 * spans, and recent log tail at the time of capture.
 */
@Immutable
public data class DiagnosticSnapshot(
  val id: String,
  val timestamp: Long,
  val trigger: AnomalyTrigger,
  val agenticTraceId: String? = null,
  val metricsSnapshot: MetricsSnapshot? = null,
  val activeSpans: ImmutableList<String> = persistentListOf(),
  val logTail: ImmutableList<String> = persistentListOf(),
)
