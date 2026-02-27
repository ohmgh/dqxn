package app.dqxn.android.pack.essentials.widgets.ambientlight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** Light bulb icon drawn via Canvas. Tint varies by ambient light category. */
@Composable
internal fun LightBulbIcon(
  category: String?,
  size: Dp,
  modifier: Modifier = Modifier,
) {
  val tintColor =
    when (category?.uppercase()) {
      "BRIGHT",
      "VERY_BRIGHT" -> Color(0xFFFFC107) // Amber/yellow for bright
      "NORMAL" -> MaterialTheme.colorScheme.primary
      "DIM" -> MaterialTheme.colorScheme.onSurfaceVariant
      "DARK" -> MaterialTheme.colorScheme.outline
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
  val outlineColor = MaterialTheme.colorScheme.onSurface

  Canvas(modifier = modifier.size(size)) { drawLightBulb(tintColor, outlineColor) }
}

private fun DrawScope.drawLightBulb(fillColor: Color, outlineColor: Color) {
  val width = size.width
  val height = size.height
  val cx = width / 2f

  // Bulb globe (upper ~65% of height)
  val bulbRadius = width * 0.35f
  val bulbCenterY = height * 0.35f
  drawCircle(
    color = fillColor,
    radius = bulbRadius,
    center = Offset(cx, bulbCenterY),
  )
  drawCircle(
    color = outlineColor,
    radius = bulbRadius,
    center = Offset(cx, bulbCenterY),
    style = Stroke(width = width * 0.03f),
  )

  // Bulb base (screw threads) — small rectangle below globe
  val baseWidth = width * 0.3f
  val baseHeight = height * 0.2f
  val baseLeft = (width - baseWidth) / 2f
  val baseTop = bulbCenterY + bulbRadius * 0.7f

  drawRect(
    color = outlineColor,
    topLeft = Offset(baseLeft, baseTop),
    size = Size(baseWidth, baseHeight),
    style = Stroke(width = width * 0.03f),
  )

  // Thread lines
  val threadCount = 3
  val threadSpacing = baseHeight / (threadCount + 1)
  for (i in 1..threadCount) {
    val y = baseTop + threadSpacing * i
    drawLine(
      color = outlineColor,
      start = Offset(baseLeft, y),
      end = Offset(baseLeft + baseWidth, y),
      strokeWidth = width * 0.02f,
    )
  }

  // Rays emanating from bulb (when lit)
  val rayCount = 8
  val innerRadius = bulbRadius * 1.3f
  val outerRadius = bulbRadius * 1.7f
  for (i in 0 until rayCount) {
    val angle = (i * 360f / rayCount) - 90f
    val rad = Math.toRadians(angle.toDouble())
    val startX = cx + innerRadius * Math.cos(rad).toFloat()
    val startY = bulbCenterY + innerRadius * Math.sin(rad).toFloat()
    val endX = cx + outerRadius * Math.cos(rad).toFloat()
    val endY = bulbCenterY + outerRadius * Math.sin(rad).toFloat()
    drawLine(
      color = fillColor.copy(alpha = 0.6f),
      start = Offset(startX, startY),
      end = Offset(endX, endY),
      strokeWidth = width * 0.025f,
    )
  }
}
