package app.dqxn.android.sdk.observability.diagnostic

import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.RingBufferSink
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import app.dqxn.android.sdk.observability.trace.DqxnTracer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.collections.immutable.toImmutableList

/**
 * Captures diagnostic snapshots on anomaly detection. Guards against concurrent captures via
 * [AtomicBoolean] CAS. Delegates file persistence to [DiagnosticFileWriter] with pool-based
 * rotation.
 *
 * Three rotation pools based on trigger type:
 * - **crash**: [AnomalyTrigger.WidgetCrash], [AnomalyTrigger.AnrDetected],
 *   [AnomalyTrigger.DataStoreCorruption]
 * - **thermal**: [AnomalyTrigger.ThermalEscalation]
 * - **perf**: [AnomalyTrigger.JankSpike], [AnomalyTrigger.ProviderTimeout],
 *   [AnomalyTrigger.EscalatedStaleness], [AnomalyTrigger.BindingStalled]
 */
public class DiagnosticSnapshotCapture(
  private val logger: DqxnLogger,
  private val metricsCollector: MetricsCollector,
  private val tracer: DqxnTracer,
  private val logRingBuffer: RingBufferSink,
  private val fileWriter: DiagnosticFileWriter,
) {

  private val capturing: AtomicBoolean = AtomicBoolean(false)

  /**
   * Captures a diagnostic snapshot for the given [trigger]. Returns null if a concurrent capture is
   * already in progress or if storage pressure is detected.
   */
  public fun capture(
    trigger: AnomalyTrigger,
    agenticTraceId: String? = null
  ): DiagnosticSnapshot? {
    if (!capturing.compareAndSet(false, true)) {
      logger.warn(TAG) { "Concurrent diagnostic capture dropped for trigger: $trigger" }
      return null
    }

    try {
      if (fileWriter.checkStoragePressure()) {
        logger.warn(TAG) { "Storage pressure detected, skipping capture for: $trigger" }
        return null
      }

      val snapshot =
        DiagnosticSnapshot(
          id = UUID.randomUUID().toString(),
          timestamp = System.currentTimeMillis(),
          trigger = trigger,
          agenticTraceId = agenticTraceId,
          metricsSnapshot = metricsCollector.snapshot(),
          activeSpans =
            tracer
              .activeSpans()
              .map { "${it.name} [${it.traceId}/${it.spanId}]" }
              .toImmutableList(),
          logTail =
            logRingBuffer
              .toList()
              .map { entry -> "${entry.level}/${entry.tag.value}: ${entry.message}" }
              .toImmutableList(),
        )

      val pool = poolForTrigger(trigger)
      fileWriter.write(snapshot, pool)

      logger.info(TAG) {
        "Diagnostic snapshot captured: ${snapshot.id} pool=$pool trigger=$trigger"
      }

      return snapshot
    } finally {
      capturing.set(false)
    }
  }

  /** Returns recent snapshot DTOs from the given pool (or all pools if null). */
  public fun recentSnapshots(pool: String? = null): List<DiagnosticSnapshotDto> {
    return fileWriter.read(pool)
  }

  private fun poolForTrigger(trigger: AnomalyTrigger): String =
    when (trigger) {
      is AnomalyTrigger.WidgetCrash -> DiagnosticFileWriter.POOL_CRASH
      is AnomalyTrigger.AnrDetected -> DiagnosticFileWriter.POOL_CRASH
      is AnomalyTrigger.DataStoreCorruption -> DiagnosticFileWriter.POOL_CRASH
      is AnomalyTrigger.ThermalEscalation -> DiagnosticFileWriter.POOL_THERMAL
      is AnomalyTrigger.JankSpike -> DiagnosticFileWriter.POOL_PERF
      is AnomalyTrigger.ProviderTimeout -> DiagnosticFileWriter.POOL_PERF
      is AnomalyTrigger.EscalatedStaleness -> DiagnosticFileWriter.POOL_PERF
      is AnomalyTrigger.BindingStalled -> DiagnosticFileWriter.POOL_PERF
    }

  private companion object {
    val TAG = LogTag("diagnostic-capture")
  }
}
