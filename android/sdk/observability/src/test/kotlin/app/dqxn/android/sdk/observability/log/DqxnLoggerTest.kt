package app.dqxn.android.sdk.observability.log

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Test

class DqxnLoggerTest {

  private val tag = LogTag("test")

  /** Counting sink that records how many times write() was invoked. */
  private class CountingSink : LogSink {
    val count = AtomicInteger(0)
    var lastEntry: LogEntry? = null

    override fun write(entry: LogEntry) {
      count.incrementAndGet()
      lastEntry = entry
    }
  }

  @Test
  fun `disabled path does not invoke sink`() {
    val sink = CountingSink()
    val logger =
      DqxnLoggerImpl(
        sinks = listOf(sink),
        minimumLevel = LogLevel.ERROR,
        sessionId = "test-session",
      )

    // DEBUG is below ERROR minimum -- lambda should NOT execute
    logger.debug(tag) { "this should not be called" }

    assertThat(sink.count.get()).isEqualTo(0)
  }

  @Test
  fun `enabled path dispatches to all sinks`() {
    val sink1 = CountingSink()
    val sink2 = CountingSink()
    val logger =
      DqxnLoggerImpl(
        sinks = listOf(sink1, sink2),
        minimumLevel = LogLevel.DEBUG,
        sessionId = "test-session",
      )

    logger.info(tag) { "hello" }

    assertThat(sink1.count.get()).isEqualTo(1)
    assertThat(sink2.count.get()).isEqualTo(1)
  }

  @Test
  fun `log entry contains correct fields`() {
    val sink = CountingSink()
    val logger =
      DqxnLoggerImpl(
        sinks = listOf(sink),
        minimumLevel = LogLevel.DEBUG,
        sessionId = "my-session",
      )

    logger.info(tag) { "test message" }

    val entry = sink.lastEntry!!
    assertThat(entry.timestamp).isAtLeast(0L)
    assertThat(entry.level).isEqualTo(LogLevel.INFO)
    assertThat(entry.tag).isEqualTo(tag)
    assertThat(entry.message).isEqualTo("test message")
    assertThat(entry.sessionId).isEqualTo("my-session")
  }

  @Test
  fun `error with throwable captures throwable`() {
    val sink = CountingSink()
    val logger =
      DqxnLoggerImpl(
        sinks = listOf(sink),
        minimumLevel = LogLevel.DEBUG,
        sessionId = "test-session",
      )
    val exception = RuntimeException("boom")

    logger.error(tag, exception) { "error happened" }

    val entry = sink.lastEntry!!
    assertThat(entry.throwable).isSameInstanceAs(exception)
    assertThat(entry.level).isEqualTo(LogLevel.ERROR)
  }

  @Test
  fun `NoOpLogger always returns false from isEnabled`() {
    assertThat(NoOpLogger.isEnabled(LogLevel.ERROR, tag)).isFalse()
    assertThat(NoOpLogger.isEnabled(LogLevel.VERBOSE, tag)).isFalse()
  }

  @Test
  fun `fields overload passes fields to entry`() {
    val sink = CountingSink()
    val logger =
      DqxnLoggerImpl(
        sinks = listOf(sink),
        minimumLevel = LogLevel.DEBUG,
        sessionId = "test-session",
      )

    logger.info(tag, { persistentMapOf("key" to "value") }) { "with fields" }

    val entry = sink.lastEntry!!
    assertThat(entry.fields).containsEntry("key", "value")
  }
}
