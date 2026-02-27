package app.dqxn.android.feature.diagnostics

import app.cash.turbine.test
import app.dqxn.android.sdk.observability.session.EventType
import app.dqxn.android.sdk.observability.session.SessionEvent
import app.dqxn.android.sdk.observability.session.SessionEventEmitter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class SessionRecorderTest {

  private lateinit var recorder: SessionRecorder

  @BeforeEach
  fun setUp() {
    recorder = SessionRecorder()
  }

  @Test
  fun `record does nothing when not recording`() {
    repeat(5) { i -> recorder.record(event(timestamp = i.toLong(), type = EventType.TAP)) }

    assertThat(recorder.snapshot()).isEmpty()
  }

  @Test
  fun `record captures events when recording`() {
    recorder.startRecording()

    val events =
      listOf(
        event(timestamp = 1L, type = EventType.TAP),
        event(timestamp = 2L, type = EventType.MOVE),
        event(timestamp = 3L, type = EventType.RESIZE),
      )
    events.forEach { recorder.record(it) }

    val snapshot = recorder.snapshot()
    assertThat(snapshot).hasSize(3)
    assertThat(snapshot).containsExactlyElementsIn(events).inOrder()
  }

  @Test
  fun `ring buffer evicts oldest on overflow`() {
    recorder.startRecording()

    val totalEvents = SessionRecorder.MAX_EVENTS + 10
    repeat(totalEvents) { i ->
      recorder.record(event(timestamp = i.toLong(), type = EventType.TAP))
    }

    val snapshot = recorder.snapshot()
    assertThat(snapshot).hasSize(SessionRecorder.MAX_EVENTS)
    // Oldest 10 events (timestamps 0-9) should be evicted
    assertThat(snapshot.first().timestamp).isEqualTo(10L)
    assertThat(snapshot.last().timestamp).isEqualTo((totalEvents - 1).toLong())
  }

  @Test
  fun `snapshot returns immutable copy`() {
    recorder.startRecording()

    recorder.record(event(timestamp = 1L, type = EventType.TAP))
    recorder.record(event(timestamp = 2L, type = EventType.MOVE))
    val firstSnapshot = recorder.snapshot()

    // Record more events after snapshot
    recorder.record(event(timestamp = 3L, type = EventType.RESIZE))

    // First snapshot should be unchanged
    assertThat(firstSnapshot).hasSize(2)
    // New snapshot should have 3 events
    assertThat(recorder.snapshot()).hasSize(3)
  }

  @Test
  fun `stop recording prevents further capture`() {
    recorder.startRecording()
    recorder.record(event(timestamp = 1L, type = EventType.TAP))
    recorder.record(event(timestamp = 2L, type = EventType.MOVE))

    recorder.stopRecording()
    recorder.record(event(timestamp = 3L, type = EventType.RESIZE))
    recorder.record(event(timestamp = 4L, type = EventType.NAVIGATE))

    val snapshot = recorder.snapshot()
    assertThat(snapshot).hasSize(2)
    assertThat(snapshot.map { it.timestamp }).containsExactly(1L, 2L).inOrder()
  }

  @Test
  fun `clear removes all events`() {
    recorder.startRecording()
    recorder.record(event(timestamp = 1L, type = EventType.TAP))
    recorder.record(event(timestamp = 2L, type = EventType.MOVE))

    recorder.clear()

    assertThat(recorder.snapshot()).isEmpty()
    // isRecording should be unaffected
    assertThat(recorder.isRecording.value).isTrue()
  }

  @Test
  fun `isRecording flow reflects state`() = runTest {
    recorder.isRecording.test {
      assertThat(awaitItem()).isFalse()

      recorder.startRecording()
      assertThat(awaitItem()).isTrue()

      recorder.stopRecording()
      assertThat(awaitItem()).isFalse()
    }
  }

  @Test
  fun `implements SessionEventEmitter interface`() {
    assertThat(recorder).isInstanceOf(SessionEventEmitter::class.java)
  }

  private fun event(timestamp: Long, type: EventType, details: String = ""): SessionEvent =
    SessionEvent(timestamp = timestamp, type = type, details = details)
}
