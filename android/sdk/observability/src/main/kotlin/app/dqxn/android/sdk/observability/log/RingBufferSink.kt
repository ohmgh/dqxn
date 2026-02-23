package app.dqxn.android.sdk.observability.log

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * Lock-free ring buffer storing [LogEntry] objects using [AtomicReferenceArray]. NOT the same as
 * [app.dqxn.android.sdk.observability.metrics.LongArrayRingBuffer] (this stores objects, not
 * primitives).
 *
 * Thread-safe for concurrent writes. [toList] returns entries ordered oldest-to-newest.
 */
public class RingBufferSink(private val capacity: Int) : LogSink {

  private val buffer: AtomicReferenceArray<LogEntry?> = AtomicReferenceArray(capacity)
  private val writeIndex: AtomicInteger = AtomicInteger(0)

  override fun write(entry: LogEntry) {
    val index = writeIndex.getAndIncrement()
    buffer.set(index % capacity, entry)
  }

  /** Returns entries ordered from oldest to newest. */
  public fun toList(): List<LogEntry> {
    val currentWrite = writeIndex.get()
    val count = minOf(currentWrite, capacity)
    if (count == 0) return emptyList()

    val result = ArrayList<LogEntry>(count)
    val start = if (currentWrite <= capacity) 0 else currentWrite - capacity
    for (i in start until currentWrite) {
      val entry = buffer.get(i % capacity)
      if (entry != null) {
        result.add(entry)
      }
    }
    return result
  }

  /** Number of entries currently stored. */
  public fun count(): Int = minOf(writeIndex.get(), capacity)
}
