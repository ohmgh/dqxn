package app.dqxn.android.sdk.observability.metrics

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MetricsCollectorTest {

  @Test
  fun `recordFrame buckets correctly`() {
    val collector = MetricsCollector()

    collector.recordFrame(5) // <8ms -> bucket 0
    collector.recordFrame(10) // <12ms -> bucket 1
    collector.recordFrame(15) // <16ms -> bucket 2
    collector.recordFrame(20) // <24ms -> bucket 3
    collector.recordFrame(30) // <33ms -> bucket 4
    collector.recordFrame(50) // >=33ms -> bucket 5

    val snapshot = collector.snapshot()
    assertThat(snapshot.frameHistogram).containsExactly(1L, 1L, 1L, 1L, 1L, 1L).inOrder()
    assertThat(snapshot.totalFrameCount).isEqualTo(6)
  }

  @Test
  fun `recordFrame concurrent writes`() = runTest {
    val collector = MetricsCollector()
    val jobs = (1..100).map { launch { repeat(100) { collector.recordFrame(10) } } }
    jobs.forEach { it.join() }

    assertThat(collector.snapshot().totalFrameCount).isEqualTo(10_000)
  }

  @Test
  fun `recordWidgetDraw stores in ring buffer`() {
    val collector = MetricsCollector()

    // Record 70 draw times (ring buffer capacity is 64)
    repeat(70) { i -> collector.recordWidgetDraw("essentials:clock", i.toLong()) }

    val snapshot = collector.snapshot()
    val drawTimes = snapshot.widgetDrawTimes["essentials:clock"]
    assertThat(drawTimes).isNotNull()
    assertThat(drawTimes!!.size).isEqualTo(64) // Capped at ring buffer capacity
  }

  @Test
  fun `recordProviderLatency late registration`() {
    val collector = MetricsCollector()

    // Record latency for provider not pre-populated
    collector.recordProviderLatency("essentials:gps-speed", 42)

    val snapshot = collector.snapshot()
    assertThat(snapshot.providerLatencies).containsKey("essentials:gps-speed")
    assertThat(snapshot.providerLatencies["essentials:gps-speed"]).containsExactly(42L)
  }

  @Test
  fun `snapshot returns immutable copy`() {
    val collector = MetricsCollector()
    collector.recordFrame(5)
    collector.recordFrame(10)

    val snapshot1 = collector.snapshot()
    assertThat(snapshot1.totalFrameCount).isEqualTo(2)

    // Record more frames after snapshot
    collector.recordFrame(20)
    collector.recordFrame(30)

    // Original snapshot should be unchanged
    assertThat(snapshot1.totalFrameCount).isEqualTo(2)
    assertThat(collector.snapshot().totalFrameCount).isEqualTo(4)
  }

  @Test
  fun `recordRecomposition increments counter`() {
    val collector = MetricsCollector()

    repeat(5) { collector.recordRecomposition("essentials:clock") }

    val snapshot = collector.snapshot()
    assertThat(snapshot.recompositionCounts["essentials:clock"]).isEqualTo(5)
  }
}
