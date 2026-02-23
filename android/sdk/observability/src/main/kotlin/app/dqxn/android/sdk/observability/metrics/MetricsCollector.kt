package app.dqxn.android.sdk.observability.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/**
 * Lock-free metrics collector for frame histograms, per-widget draw times, per-provider latencies,
 * and recomposition counts.
 *
 * All recording methods use atomic operations and [ConcurrentHashMap.getOrPut] for late
 * registration. No synchronized blocks. Target overhead: <25ns for [recordFrame].
 *
 * Widget/provider registries will pre-populate in Phase 6/7 DI wiring. Until then, entries are
 * created lazily at recording sites.
 */
public class MetricsCollector {

  /** Histogram buckets: <8ms, <12ms, <16ms, <24ms, <33ms, >33ms. */
  private val frameHistogram: AtomicLongArray = AtomicLongArray(BUCKET_COUNT)
  private val totalFrameCount: AtomicLong = AtomicLong(0)

  /** Per-widget draw times keyed by typeId, ring buffer capacity 64. */
  private val widgetDrawTimes: ConcurrentHashMap<String, LongArrayRingBuffer> = ConcurrentHashMap()

  /** Per-provider latencies keyed by providerId. */
  private val providerLatencies: ConcurrentHashMap<String, LongArrayRingBuffer> =
    ConcurrentHashMap()

  /** Per-widget recomposition counts keyed by typeId. */
  private val recompositionCounts: ConcurrentHashMap<String, AtomicLong> = ConcurrentHashMap()

  /**
   * Records a frame duration into the appropriate histogram bucket and increments the total frame
   * count. Target: <25ns overhead (just two atomic increments).
   */
  public fun recordFrame(durationMs: Long) {
    val bucket =
      when {
        durationMs < 8 -> 0
        durationMs < 12 -> 1
        durationMs < 16 -> 2
        durationMs < 24 -> 3
        durationMs < 33 -> 4
        else -> 5
      }
    frameHistogram.incrementAndGet(bucket)
    totalFrameCount.incrementAndGet()
  }

  /** Records a widget draw duration in nanoseconds for the given [typeId]. */
  public fun recordWidgetDraw(typeId: String, durationNanos: Long) {
    val buffer = widgetDrawTimes.getOrPut(typeId) { LongArrayRingBuffer(RING_BUFFER_CAPACITY) }
    buffer.add(durationNanos)
  }

  /** Records a provider latency in milliseconds for the given [providerId]. */
  public fun recordProviderLatency(providerId: String, durationMs: Long) {
    val buffer =
      providerLatencies.getOrPut(providerId) { LongArrayRingBuffer(RING_BUFFER_CAPACITY) }
    buffer.add(durationMs)
  }

  /** Increments the recomposition counter for the given widget [typeId]. */
  public fun recordRecomposition(typeId: String) {
    val counter = recompositionCounts.getOrPut(typeId) { AtomicLong(0) }
    counter.incrementAndGet()
  }

  /** Takes a read-only snapshot of all current metrics. */
  public fun snapshot(): MetricsSnapshot {
    val histogram = LongArray(BUCKET_COUNT) { frameHistogram.get(it) }
    val drawTimes = widgetDrawTimes.mapValues { (_, buffer) -> buffer.toList() }
    val latencies = providerLatencies.mapValues { (_, buffer) -> buffer.toList() }
    val recomps = recompositionCounts.mapValues { (_, counter) -> counter.get() }

    return MetricsSnapshot(
      frameHistogram = histogram.toList().toImmutableList(),
      totalFrameCount = totalFrameCount.get(),
      widgetDrawTimes = drawTimes.toImmutableMap(),
      providerLatencies = latencies.toImmutableMap(),
      recompositionCounts = recomps.toImmutableMap(),
      captureTimestamp = System.currentTimeMillis(),
    )
  }

  private companion object {
    const val BUCKET_COUNT = 6
    const val RING_BUFFER_CAPACITY = 64
  }
}
