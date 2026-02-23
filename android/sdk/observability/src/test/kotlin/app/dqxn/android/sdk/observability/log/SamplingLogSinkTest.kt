package app.dqxn.android.sdk.observability.log

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Test

class SamplingLogSinkTest {

  private class CountingSink : LogSink {
    val count = AtomicInteger(0)

    override fun write(entry: LogEntry) {
      count.incrementAndGet()
    }
  }

  private val tagA = LogTag("tagA")
  private val tagB = LogTag("tagB")

  private fun makeEntry(tag: LogTag, timestamp: Long = 1_000_000L): LogEntry =
    LogEntry(
      timestamp = timestamp,
      level = LogLevel.INFO,
      tag = tag,
      message = "test",
      sessionId = "session",
    )

  @Test
  fun `unlimited tag passes all entries`() {
    val delegate = CountingSink()
    val sink = SamplingLogSink(delegate = delegate)

    repeat(10) { sink.write(makeEntry(tagA)) }

    assertThat(delegate.count.get()).isEqualTo(10)
  }

  @Test
  fun `rate limited tag drops excess`() {
    val delegate = CountingSink()
    val sink =
      SamplingLogSink(
        delegate = delegate,
        rateLimitPerSecond = mapOf(tagA to 1),
      )

    // All entries at same timestamp -- only first should pass
    repeat(5) { sink.write(makeEntry(tagA, timestamp = 1_000_000_000L)) }

    assertThat(delegate.count.get()).isEqualTo(1)
  }

  @Test
  fun `different tags have independent limits`() {
    val delegate = CountingSink()
    val sink =
      SamplingLogSink(
        delegate = delegate,
        rateLimitPerSecond = mapOf(tagA to 1),
        // tagB not in map -- unlimited
      )

    sink.write(makeEntry(tagA, timestamp = 1_000_000_000L))
    sink.write(makeEntry(tagA, timestamp = 1_000_000_000L)) // Should be dropped
    sink.write(makeEntry(tagB, timestamp = 1_000_000_000L))
    sink.write(makeEntry(tagB, timestamp = 1_000_000_000L))

    // tagA: 1 pass + 1 drop = 1; tagB: 2 pass = 2; total = 3
    assertThat(delegate.count.get()).isEqualTo(3)
  }
}
