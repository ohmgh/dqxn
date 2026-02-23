package app.dqxn.android.sdk.observability.log

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Test

class RingBufferSinkTest {

  private fun makeEntry(id: Int): LogEntry = LogEntry(
    timestamp = id.toLong(),
    level = LogLevel.INFO,
    tag = LogTag("test"),
    message = "message-$id",
    sessionId = "session",
  )

  @Test
  fun `stores entries up to capacity`() {
    val capacity = 5
    val sink = RingBufferSink(capacity)

    // Write capacity + 5 entries
    repeat(capacity + 5) { sink.write(makeEntry(it)) }

    assertThat(sink.toList()).hasSize(capacity)
  }

  @Test
  fun `oldest entries evicted first`() {
    val capacity = 5
    val sink = RingBufferSink(capacity)

    // Write capacity + 1 entries (0..5)
    repeat(capacity + 1) { sink.write(makeEntry(it)) }

    val entries = sink.toList()
    // Entry 0 should be gone, entries 1-5 should remain
    assertThat(entries.first().message).isEqualTo("message-1")
    assertThat(entries.last().message).isEqualTo("message-5")
  }

  @Test
  fun `empty buffer returns empty list`() {
    val sink = RingBufferSink(10)
    assertThat(sink.toList()).isEmpty()
    assertThat(sink.count()).isEqualTo(0)
  }
}
