package app.dqxn.android.sdk.observability.log

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-tag rate limiting sink. Drops entries exceeding [rateLimitPerSecond] for their tag.
 * Tags not in the rate limit map pass through unlimited.
 *
 * Uses [ConcurrentHashMap] with [AtomicLong] tracking last-allowed timestamp per tag
 * for lock-free rate enforcement.
 */
public class SamplingLogSink(
  private val delegate: LogSink,
  private val rateLimitPerSecond: Map<LogTag, Int> = emptyMap(),
) : LogSink {

  // Key: tag value, Value: last allowed timestamp in nanos
  private val lastAllowed: ConcurrentHashMap<String, AtomicLong> = ConcurrentHashMap()

  override fun write(entry: LogEntry) {
    val limit = rateLimitPerSecond[entry.tag] ?: run {
      delegate.write(entry)
      return
    }

    if (limit <= 0) return // Explicitly blocked

    val intervalNanos = 1_000_000_000L / limit
    val tagKey = entry.tag.value
    val lastTimestamp = lastAllowed.computeIfAbsent(tagKey) { AtomicLong(0L) }

    while (true) {
      val last = lastTimestamp.get()
      val now = entry.timestamp
      if (now - last < intervalNanos) {
        // Within rate limit window -- drop
        return
      }
      if (lastTimestamp.compareAndSet(last, now)) {
        delegate.write(entry)
        return
      }
      // CAS failed, retry
    }
  }
}
