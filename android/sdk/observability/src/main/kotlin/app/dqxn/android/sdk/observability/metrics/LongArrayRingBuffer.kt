package app.dqxn.android.sdk.observability.metrics

import java.util.concurrent.atomic.AtomicInteger

/**
 * Lock-free primitive ring buffer storing [Long] values without boxing.
 * Uses modular arithmetic for wrap-around.
 *
 * Designed for high-frequency metrics (per-widget draw times, frame durations).
 * NOT the same as [app.dqxn.android.sdk.observability.log.RingBufferSink]
 * (this stores primitives, not objects).
 */
public class LongArrayRingBuffer(private val capacity: Int) {

  private val buffer: LongArray = LongArray(capacity)
  private val writeIndex: AtomicInteger = AtomicInteger(0)

  /** Adds a value to the ring buffer. Thread-safe. */
  public fun add(value: Long) {
    val index = writeIndex.getAndIncrement()
    buffer[index % capacity] = value
  }

  /** Returns values ordered from oldest to newest. */
  public fun toList(): List<Long> {
    val currentWrite = writeIndex.get()
    val count = minOf(currentWrite, capacity)
    if (count == 0) return emptyList()

    val result = ArrayList<Long>(count)
    val start = if (currentWrite <= capacity) 0 else currentWrite - capacity
    for (i in start until currentWrite) {
      result.add(buffer[i % capacity])
    }
    return result
  }

  /** Number of values currently stored. */
  public fun count(): Int = minOf(writeIndex.get(), capacity)

  /** Calculates the average of stored values. Returns 0.0 for empty buffer. */
  public fun average(): Double {
    val values = toList()
    if (values.isEmpty()) return 0.0
    return values.sum().toDouble() / values.size
  }

  /**
   * Calculates the given percentile (0.0 to 1.0) of stored values.
   * Uses nearest-rank method. Returns 0 for empty buffer.
   */
  public fun percentile(p: Double): Long {
    require(p in 0.0..1.0) { "Percentile must be between 0.0 and 1.0, got $p" }

    val values = toList()
    if (values.isEmpty()) return 0L

    val sorted = values.sorted()
    val rank = (p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
    return sorted[rank]
  }
}
