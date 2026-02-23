package app.dqxn.android.sdk.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableList

/** Type of gradient for [GradientSpec]. */
public enum class GradientType {
  VERTICAL,
  HORIZONTAL,
  LINEAR,
  RADIAL,
  SWEEP,
}

/** A single color stop in a gradient. */
@Immutable
public data class GradientStop(
  val color: Long,
  val position: Float,
)

/**
 * Serializable gradient specification. Lives in `:sdk:ui` (not `:sdk:contracts`) because conversion
 * to Compose [Brush] requires Compose compiler for `Color` and `Size`.
 */
@Immutable
public data class GradientSpec(
  val type: GradientType,
  val stops: ImmutableList<GradientStop>,
) {
  /** Converts this spec to a Compose [Brush] for the given drawing [size]. */
  public fun toBrush(size: Size): Brush {
    val colorStops = stops.map { Pair(it.position, Color(it.color)) }.toTypedArray()
    return when (type) {
      GradientType.VERTICAL ->
        Brush.verticalGradient(
          colorStops = colorStops,
          startY = 0f,
          endY = size.height,
        )
      GradientType.HORIZONTAL ->
        Brush.horizontalGradient(
          colorStops = colorStops,
          startX = 0f,
          endX = size.width,
        )
      GradientType.LINEAR ->
        Brush.linearGradient(
          colorStops = colorStops,
          start = Offset.Zero,
          end = Offset(size.width, size.height),
        )
      GradientType.RADIAL ->
        Brush.radialGradient(
          colorStops = colorStops,
          center = Offset(size.width / 2f, size.height / 2f),
          radius = maxOf(size.width, size.height) / 2f,
        )
      GradientType.SWEEP ->
        Brush.sweepGradient(
          colorStops = colorStops,
          center = Offset(size.width / 2f, size.height / 2f),
        )
    }
  }
}
