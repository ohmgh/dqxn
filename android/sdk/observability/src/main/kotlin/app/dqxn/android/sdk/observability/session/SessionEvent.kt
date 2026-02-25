package app.dqxn.android.sdk.observability.session

import androidx.compose.runtime.Immutable

/**
 * A single session event captured during user interaction with the dashboard.
 *
 * Events are recorded via [SessionEventEmitter] and stored in a ring buffer
 * by the SessionRecorder implementation in `:feature:diagnostics`.
 */
@Immutable
public data class SessionEvent(
  val timestamp: Long,
  val type: EventType,
  val details: String = "",
)

/** Types of session events that can be recorded for diagnostics replay. */
public enum class EventType {
  TAP,
  MOVE,
  RESIZE,
  NAVIGATE,
  EDIT_MODE_ENTER,
  EDIT_MODE_EXIT,
  THEME_CHANGE,
  WIDGET_ADD,
  WIDGET_REMOVE,
}
