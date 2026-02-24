package app.dqxn.android.feature.dashboard.grid

import androidx.compose.runtime.Immutable
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize

/**
 * Continuous resize gesture state for [MutableStateFlow] (latest-value-wins).
 *
 * [targetPosition] is non-null for non-[ResizeHandle.BOTTOM_RIGHT] handles where the widget origin
 * must shift to keep the opposite corner fixed. Null flow value means no resize is in progress.
 */
@Immutable
public data class ResizeUpdate(
  val widgetId: String,
  val handle: ResizeHandle,
  val targetSize: GridSize,
  val targetPosition: GridPosition?,
  val isResizing: Boolean,
)

/** Corner handle used for widget resize gestures. */
public enum class ResizeHandle {
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
}
