package app.dqxn.android.pack.essentials.widgets.battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Battery icon drawn via Canvas. Shows a battery outline with fill level and optional charging
 * bolt.
 */
@Composable
internal fun BatteryIcon(
  level: Int?,
  isCharging: Boolean,
  size: Dp,
  modifier: Modifier = Modifier,
) {
  val fillColor =
    when {
      level == null -> MaterialTheme.colorScheme.onSurfaceVariant
      level <= 15 -> MaterialTheme.colorScheme.error
      level <= 30 -> Color(0xFFFF9800) // Amber warning
      else -> MaterialTheme.colorScheme.primary
    }
  val outlineColor = MaterialTheme.colorScheme.onSurface
  val chargingColor = MaterialTheme.colorScheme.tertiary

  Canvas(modifier = modifier.size(size)) {
    drawBatteryOutline(outlineColor)
    if (level != null) {
      drawBatteryFill(level, fillColor)
    }
    if (isCharging) {
      drawChargingBolt(chargingColor)
    }
  }
}

private fun DrawScope.drawBatteryOutline(color: Color) {
  val width = size.width
  val height = size.height
  // Battery body: centered, 60% width, 80% height
  val bodyWidth = width * 0.6f
  val bodyHeight = height * 0.75f
  val bodyLeft = (width - bodyWidth) / 2f
  val bodyTop = height * 0.15f

  drawRoundRect(
    color = color,
    topLeft = Offset(bodyLeft, bodyTop),
    size = Size(bodyWidth, bodyHeight),
    cornerRadius = CornerRadius(bodyWidth * 0.1f),
    style = Stroke(width = width * 0.04f),
  )

  // Battery cap (top nub)
  val capWidth = bodyWidth * 0.35f
  val capHeight = height * 0.08f
  val capLeft = (width - capWidth) / 2f
  drawRoundRect(
    color = color,
    topLeft = Offset(capLeft, bodyTop - capHeight * 0.6f),
    size = Size(capWidth, capHeight),
    cornerRadius = CornerRadius(capWidth * 0.2f),
  )
}

private fun DrawScope.drawBatteryFill(level: Int, color: Color) {
  val width = size.width
  val height = size.height
  val bodyWidth = width * 0.6f
  val bodyHeight = height * 0.75f
  val bodyLeft = (width - bodyWidth) / 2f
  val bodyTop = height * 0.15f

  val inset = width * 0.06f
  val fillableHeight = bodyHeight - inset * 2
  val fillHeight = fillableHeight * (level.coerceIn(0, 100) / 100f)

  drawRoundRect(
    color = color,
    topLeft = Offset(bodyLeft + inset, bodyTop + inset + fillableHeight - fillHeight),
    size = Size(bodyWidth - inset * 2, fillHeight),
    cornerRadius = CornerRadius(bodyWidth * 0.05f),
  )
}

private fun DrawScope.drawChargingBolt(color: Color) {
  val cx = size.width / 2f
  val cy = size.height / 2f
  val boltWidth = size.width * 0.2f
  val boltHeight = size.height * 0.35f

  val path =
    Path().apply {
      // Lightning bolt shape
      moveTo(cx + boltWidth * 0.1f, cy - boltHeight / 2f)
      lineTo(cx - boltWidth * 0.5f, cy + boltHeight * 0.05f)
      lineTo(cx, cy + boltHeight * 0.05f)
      lineTo(cx - boltWidth * 0.1f, cy + boltHeight / 2f)
      lineTo(cx + boltWidth * 0.5f, cy - boltHeight * 0.05f)
      lineTo(cx, cy - boltHeight * 0.05f)
      close()
    }
  drawPath(path, color)
}
