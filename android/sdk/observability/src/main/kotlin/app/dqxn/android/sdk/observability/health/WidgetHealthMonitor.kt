package app.dqxn.android.sdk.observability.health

import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.warn
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Monitors widget health by tracking data freshness and render liveness. Detects stale data (no
 * update in [stalenessThresholdMs]) and stalled renders on periodic [checkIntervalMs] checks.
 */
public class WidgetHealthMonitor(
  private val logger: DqxnLogger,
  private val scope: CoroutineScope,
  private val stalenessThresholdMs: Long = DEFAULT_STALENESS_THRESHOLD_MS,
  private val checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
  private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {

  private val widgetStatuses: ConcurrentHashMap<String, WidgetHealthStatus> = ConcurrentHashMap()

  init {
    scope.launch {
      while (true) {
        delay(checkIntervalMs)
        checkLiveness()
      }
    }
  }

  /** Reports that data was received for the given widget. Resets to ACTIVE status. */
  public fun reportData(widgetId: String, typeId: String) {
    val now = timeProvider()
    widgetStatuses.compute(widgetId) { _, existing ->
      (existing ?: WidgetHealthStatus(widgetId = widgetId, typeId = typeId)).copy(
        typeId = typeId,
        lastDataTimestamp = now,
        status = if (existing?.status == Status.CRASHED) Status.CRASHED else Status.ACTIVE,
      )
    }
  }

  /** Reports that a draw occurred for the given widget. */
  public fun reportDraw(widgetId: String, typeId: String) {
    val now = timeProvider()
    widgetStatuses.compute(widgetId) { _, existing ->
      (existing ?: WidgetHealthStatus(widgetId = widgetId, typeId = typeId)).copy(
        typeId = typeId,
        lastDrawTimestamp = now,
        status = if (existing?.status == Status.CRASHED) Status.CRASHED else Status.ACTIVE,
      )
    }
  }

  /** Reports that the widget has crashed. Sets status to CRASHED immediately. */
  public fun reportCrash(widgetId: String, typeId: String) {
    val now = timeProvider()
    widgetStatuses.compute(widgetId) { _, existing ->
      (existing ?: WidgetHealthStatus(widgetId = widgetId, typeId = typeId)).copy(
        typeId = typeId,
        status = Status.CRASHED,
        lastDataTimestamp = existing?.lastDataTimestamp ?: now,
        lastDrawTimestamp = existing?.lastDrawTimestamp ?: now,
      )
    }
  }

  /** Returns a snapshot of all tracked widget health statuses for F13.5 state dumps. */
  public fun allStatuses(): Map<String, WidgetHealthStatus> = HashMap(widgetStatuses)

  /** Exposed for testing -- triggers a liveness check immediately. */
  internal fun checkLiveness() {
    val now = timeProvider()
    widgetStatuses.forEach { (widgetId, status) ->
      if (status.status == Status.CRASHED || status.status == Status.SETUP_REQUIRED) return@forEach

      val newStatus =
        when {
          now - status.lastDataTimestamp > stalenessThresholdMs -> {
            logger.warn(TAG) { "Widget $widgetId stale data (${now - status.lastDataTimestamp}ms)" }
            Status.STALE_DATA
          }
          status.lastDrawTimestamp > 0 && now - status.lastDrawTimestamp > stalenessThresholdMs -> {
            logger.warn(TAG) {
              "Widget $widgetId stalled render (${now - status.lastDrawTimestamp}ms)"
            }
            Status.STALLED_RENDER
          }
          else -> Status.ACTIVE
        }

      if (newStatus != status.status) {
        widgetStatuses[widgetId] = status.copy(status = newStatus)
      }
    }
  }

  /** Health status of a single widget. */
  public data class WidgetHealthStatus(
    val widgetId: String,
    val typeId: String,
    val lastDataTimestamp: Long = 0L,
    val lastDrawTimestamp: Long = 0L,
    val status: Status = Status.ACTIVE,
  )

  /** Widget health status categories. */
  public enum class Status {
    ACTIVE,
    STALE_DATA,
    STALLED_RENDER,
    CRASHED,
    SETUP_REQUIRED,
  }

  private companion object {
    const val DEFAULT_STALENESS_THRESHOLD_MS = 10_000L
    const val DEFAULT_CHECK_INTERVAL_MS = 10_000L
    val TAG = LogTag("widget-health")
  }
}
