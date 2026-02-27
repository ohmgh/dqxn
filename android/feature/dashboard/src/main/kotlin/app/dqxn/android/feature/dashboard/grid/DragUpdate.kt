package app.dqxn.android.feature.dashboard.grid

import androidx.compose.runtime.Immutable

/**
 * Continuous drag gesture state for [MutableStateFlow] (latest-value-wins).
 *
 * Offsets are in pixels for direct use with `graphicsLayer { translationX / translationY }`. Null
 * flow value means no drag is in progress.
 */
@Immutable
public data class DragUpdate(
  val widgetId: String,
  val currentOffsetX: Float,
  val currentOffsetY: Float,
  val isDragging: Boolean,
)
