package app.dqxn.android.sdk.observability.crash

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Decorator around [ErrorReporter] that deduplicates reports within a cooldown period (NF36). Uses
 * exception class + context as dedup key. Prevents report flooding.
 */
public class DeduplicatingErrorReporter(
  private val delegate: ErrorReporter,
  private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
  private val clock: () -> Long = System::currentTimeMillis,
) : ErrorReporter {

  private val lastReported: ConcurrentHashMap<String, AtomicLong> = ConcurrentHashMap()

  override fun reportNonFatal(e: Throwable, context: ErrorContext) {
    val key = "${e::class.simpleName}:${context}"
    if (shouldReport(key)) {
      delegate.reportNonFatal(e, context)
    }
  }

  override fun reportWidgetCrash(
    typeId: String,
    widgetId: String,
    context: WidgetErrorContext,
  ) {
    val key = "widget:${typeId}:${widgetId}"
    if (shouldReport(key)) {
      delegate.reportWidgetCrash(typeId, widgetId, context)
    }
  }

  private fun shouldReport(key: String): Boolean {
    val now = clock()
    val lastTime = lastReported.computeIfAbsent(key) { AtomicLong(NEVER_REPORTED) }

    while (true) {
      val last = lastTime.get()
      // First report (sentinel) always passes; subsequent reports check cooldown
      if (last != NEVER_REPORTED && now - last < cooldownMillis) {
        return false
      }
      if (lastTime.compareAndSet(last, now)) {
        return true
      }
      // CAS failed, retry
    }
  }

  private companion object {
    const val DEFAULT_COOLDOWN_MILLIS = 60_000L
    const val NEVER_REPORTED = Long.MIN_VALUE
  }
}
