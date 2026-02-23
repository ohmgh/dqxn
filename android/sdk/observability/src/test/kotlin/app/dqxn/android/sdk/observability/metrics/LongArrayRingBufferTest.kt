package app.dqxn.android.sdk.observability.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LongArrayRingBufferTest {

  @Test
  fun `add and retrieve within capacity`() {
    val buffer = LongArrayRingBuffer(10)
    for (i in 1L..5L) buffer.add(i)

    assertThat(buffer.toList()).containsExactly(1L, 2L, 3L, 4L, 5L).inOrder()
    assertThat(buffer.count()).isEqualTo(5)
  }

  @Test
  fun `wraps around correctly`() {
    val capacity = 5
    val buffer = LongArrayRingBuffer(capacity)

    // Add capacity + 3 values (8 total: 1..8)
    for (i in 1L..8L) buffer.add(i)

    // Should contain last 5: [4, 5, 6, 7, 8]
    assertThat(buffer.toList()).containsExactly(4L, 5L, 6L, 7L, 8L).inOrder()
    assertThat(buffer.count()).isEqualTo(capacity)
  }

  @Test
  fun `percentile calculation`() {
    val buffer = LongArrayRingBuffer(100)
    // Add values 1..100
    for (i in 1L..100L) buffer.add(i)

    assertThat(buffer.percentile(0.5)).isEqualTo(50L)  // P50 (median)
    assertThat(buffer.percentile(0.95)).isEqualTo(95L) // P95
    assertThat(buffer.percentile(0.99)).isEqualTo(99L) // P99
    assertThat(buffer.percentile(0.0)).isEqualTo(1L)   // min
    assertThat(buffer.percentile(1.0)).isEqualTo(100L) // max
  }

  @Test
  fun `average calculation`() {
    val buffer = LongArrayRingBuffer(10)
    buffer.add(10L)
    buffer.add(20L)
    buffer.add(30L)

    assertThat(buffer.average()).isEqualTo(20.0)
  }

  @Test
  fun `empty buffer returns empty list`() {
    val buffer = LongArrayRingBuffer(10)

    assertThat(buffer.toList()).isEmpty()
    assertThat(buffer.count()).isEqualTo(0)
    assertThat(buffer.average()).isEqualTo(0.0)
  }

  @Test
  fun `single element`() {
    val buffer = LongArrayRingBuffer(10)
    buffer.add(42L)

    assertThat(buffer.count()).isEqualTo(1)
    assertThat(buffer.toList()).containsExactly(42L)
    assertThat(buffer.average()).isEqualTo(42.0)
    assertThat(buffer.percentile(0.5)).isEqualTo(42L)
  }
}
