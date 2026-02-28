package app.dqxn.android.pack.essentials.widgets.ambientlight

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

  Spacer(
    modifier =
      modifier.size(size).drawWithCache {
        val width = this.size.width
        val height = this.size.height
        val cx = width / 2f

        // Bulb geometry
        val bulbRadius = width * 0.35f
        val bulbCenterY = height * 0.35f

        // Base geometry
        val baseWidth = width * 0.3f
        val baseHeight = height * 0.2f
        val baseLeft = (width - baseWidth) / 2f
        val baseTop = bulbCenterY + bulbRadius * 0.7f
        val outlineStrokeWidth = width * 0.03f
        val threadStrokeWidth = width * 0.02f
        val threadCount = 3
        val threadSpacing = baseHeight / (threadCount + 1)

        // Ray geometry (8 rays, static endpoints keyed on size)
        val innerRadius = bulbRadius * 1.3f
        val outerRadius = bulbRadius * 1.7f
        val rayStrokeWidth = width * 0.025f

        data class RayEndpoints(val startX: Float, val startY: Float, val endX: Float, val endY: Float)

        val rays = buildList {
          val rayCount = 8
          for (i in 0 until rayCount) {
            val angle = (i * 360f / rayCount) - 90f
            val rad = Math.toRadians(angle.toDouble())
            add(
              RayEndpoints(
                startX = cx + innerRadius * Math.cos(rad).toFloat(),
                startY = bulbCenterY + innerRadius * Math.sin(rad).toFloat(),
                endX = cx + outerRadius * Math.cos(rad).toFloat(),
                endY = bulbCenterY + outerRadius * Math.sin(rad).toFloat(),
              ),
            )
          }
        }

        // Cached alpha-modified color for rays
        val rayColor = tintColor.copy(alpha = 0.6f)

        onDrawBehind {
          // Bulb globe
          drawCircle(
            color = tintColor,
            radius = bulbRadius,
            center = Offset(cx, bulbCenterY),
          )
          drawCircle(
            color = outlineColor,
            radius = bulbRadius,
            center = Offset(cx, bulbCenterY),
            style = Stroke(width = outlineStrokeWidth),
          )

          // Bulb base
          drawRect(
            color = outlineColor,
            topLeft = Offset(baseLeft, baseTop),
            size = Size(baseWidth, baseHeight),
            style = Stroke(width = outlineStrokeWidth),
          )

          // Thread lines
          for (i in 1..threadCount) {
            val y = baseTop + threadSpacing * i
            drawLine(
              color = outlineColor,
              start = Offset(baseLeft, y),
              end = Offset(baseLeft + baseWidth, y),
              strokeWidth = threadStrokeWidth,
            )
          }

          // Rays from cached geometry
          for (ray in rays) {
            drawLine(
              color = rayColor,
              start = Offset(ray.startX, ray.startY),
              end = Offset(ray.endX, ray.endY),
              strokeWidth = rayStrokeWidth,
            )
          }
        }
      },
  )
}
