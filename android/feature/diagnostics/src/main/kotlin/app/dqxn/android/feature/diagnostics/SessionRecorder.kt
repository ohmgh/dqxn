package app.dqxn.android.feature.diagnostics

import app.dqxn.android.sdk.observability.session.SessionEvent
import app.dqxn.android.sdk.observability.session.SessionEventEmitter
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ring-buffer session event recorder implementing [SessionEventEmitter].
 *
 * Events are only captured when [isRecording] is true. The buffer holds at most
 * [MAX_EVENTS] entries; overflow evicts the oldest events first.
 *
 * Thread-safe: all buffer mutations are synchronized. [snapshot] returns an
 * immutable copy that does not block recording.
 */
@Singleton
public class SessionRecorder @Inject constructor() : SessionEventEmitter {

  public companion object {
    public const val MAX_EVENTS: Int = 10_000
  }

  private val buffer = ArrayDeque<SessionEvent>(MAX_EVENTS)
  private val lock = Any()

  private val _isRecording = MutableStateFlow(false)
  public val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  override fun record(event: SessionEvent) {
    if (!_isRecording.value) return
    synchronized(lock) {
      if (buffer.size >= MAX_EVENTS) buffer.removeFirst()
      buffer.addLast(event)
    }
  }

  /** Start recording session events. */
  public fun startRecording() {
    _isRecording.value = true
  }

  /** Stop recording session events. Events already captured are retained. */
  public fun stopRecording() {
    _isRecording.value = false
  }

  /** Return an immutable snapshot of all recorded events without blocking recording. */
  public fun snapshot(): ImmutableList<SessionEvent> = synchronized(lock) {
    buffer.toImmutableList()
  }

  /** Clear all recorded events. Does not affect recording state. */
  public fun clear(): Unit = synchronized(lock) {
    buffer.clear()
  }
}
