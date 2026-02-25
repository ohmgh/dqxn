package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/** Key lux values to display as dashed reference lines on the scale. */
private val KEY_LUX_VALUES: FloatArray = floatArrayOf(1f, 10f, 100f, 1000f, 10000f)

/**
 * Custom Canvas composable displaying a logarithmic lux meter for illuminance threshold.
 *
 * Uses [luxToPosition]/[positionToLux] from LuxMapping.kt for logarithmic scaling.
 * Supports tap and drag gestures to adjust the threshold.
 */
@Composable
public fun IlluminanceThresholdControl(
  threshold: Float,
  onThresholdChanged: (Float) -> Unit,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  val position = remember(threshold) { luxToPosition(threshold) }
  val displayLux = remember(threshold) { threshold.toInt().coerceIn(1, 10000) }

  Column(
    modifier = modifier.fillMaxWidth().testTag("illuminance_control"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // -- Current lux value above thumb --
    Text(
      text = "$displayLux lux",
      style = DashboardTypography.caption,
      color = theme.primaryTextColor,
      modifier = Modifier.padding(bottom = 4.dp).testTag("illuminance_lux_label"),
    )

    // -- Logarithmic scale canvas --
    Canvas(
      modifier =
        Modifier.fillMaxWidth()
          .height(48.dp)
          .testTag("illuminance_canvas")
          .pointerInput(Unit) {
            detectTapGestures { offset ->
              val newPosition = (offset.x / size.width).coerceIn(0f, 1f)
              onThresholdChanged(positionToLux(newPosition))
            }
          }
          .pointerInput(Unit) {
            detectDragGestures { change, _ ->
              change.consume()
              val newPosition = (change.position.x / size.width).coerceIn(0f, 1f)
              onThresholdChanged(positionToLux(newPosition))
            }
          },
    ) {
      drawLuxScale(
        position = position,
        accentColor = theme.accentColor,
        lineColor = theme.secondaryTextColor,
        textColor = theme.primaryTextColor,
      )
    }
  }
}

/** Draws the logarithmic lux scale with dashed reference lines and thumb indicator. */
private fun DrawScope.drawLuxScale(
  position: Float,
  accentColor: androidx.compose.ui.graphics.Color,
  lineColor: androidx.compose.ui.graphics.Color,
  textColor: androidx.compose.ui.graphics.Color,
) {
  val width = size.width
  val height = size.height
  val centerY = height / 2f
  val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

  // -- Track line --
  drawLine(
    color = lineColor.copy(alpha = 0.3f),
    start = Offset(0f, centerY),
    end = Offset(width, centerY),
    strokeWidth = 2f,
  )

  // -- Dashed reference lines at key lux values --
  for (lux in KEY_LUX_VALUES) {
    val x = luxToPosition(lux) * width
    drawLine(
      color = lineColor.copy(alpha = 0.2f),
      start = Offset(x, centerY - 12f),
      end = Offset(x, centerY + 12f),
      strokeWidth = 1f,
      pathEffect = dashEffect,
    )
  }

  // -- Active track (left of thumb) --
  val thumbX = position * width
  drawLine(
    color = accentColor,
    start = Offset(0f, centerY),
    end = Offset(thumbX, centerY),
    strokeWidth = 3f,
  )

  // -- Thumb circle --
  drawCircle(
    color = accentColor,
    radius = 10f,
    center = Offset(thumbX, centerY),
  )
}
