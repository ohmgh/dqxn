package app.dqxn.android.pack.essentials.widgets.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import app.dqxn.android.pack.essentials.snapshots.OrientationSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.widget.LocalWidgetData
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

/**
 * Compass widget renderer.
 *
 * Displays device heading with a rotating dial and stationary needle. The dial (tick marks,
 * cardinal labels) rotates by `-bearing` degrees so the current heading always points to the top.
 * Optionally shows tilt indicators for pitch and roll.
 *
 * Canvas-based with remembered draw objects for zero per-frame allocation.
 */
@DashboardWidget(typeId = "essentials:compass", displayName = "Compass")
public class CompassRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:compass"
  override val displayName: String = "Compass"
  override val description: String = "Compass showing device heading and cardinal directions"
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> =
    setOf(OrientationSnapshot::class)
  override val aspectRatio: Float = 1f
  override val supportsTap: Boolean = false
  override val priority: Int = 100
  override val requiredAnyEntitlement: Set<String>? = null

  override val settingsSchema: List<SettingDefinition<*>> =
    listOf(
      SettingDefinition.BooleanSetting(
        key = "showTickMarks",
        label = "Tick Marks",
        description = "Show degree markings around the dial edge",
        default = true,
      ),
      SettingDefinition.BooleanSetting(
        key = "showCardinalLabels",
        label = "Cardinal Labels",
        description = "Show N, S, E, W labels on the compass face",
        default = true,
      ),
      SettingDefinition.BooleanSetting(
        key = "showTiltIndicators",
        label = "Tilt Indicators",
        description = "Show pitch and roll indicator lines",
        default = false,
      ),
    )

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(widthUnits = 10, heightUnits = 10, aspectRatio = 1f, settings = emptyMap())

  @Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  ) {
    val widgetData = LocalWidgetData.current
    val orientation by remember { derivedStateOf { widgetData.snapshot<OrientationSnapshot>() } }
    val bearing = orientation?.bearing ?: 0f
    val pitch = orientation?.pitch ?: 0f
    val roll = orientation?.roll ?: 0f

    val showTickMarks = settings["showTickMarks"] as? Boolean ?: true
    val showCardinalLabels = settings["showCardinalLabels"] as? Boolean ?: true
    val showTiltIndicators = settings["showTiltIndicators"] as? Boolean ?: false

    val needlePath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
      val centerX = size.width / 2f
      val centerY = size.height / 2f
      val radius = min(centerX, centerY) * 0.9f

      drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2f),
      )

      rotate(degrees = -bearing, pivot = Offset(centerX, centerY)) {
        if (showTickMarks) {
          drawTickMarks(centerX, centerY, radius)
        }
        if (showCardinalLabels) {
          drawCardinalLabels(centerX, centerY, radius)
        }
      }

      drawNeedle(needlePath, centerX, centerY, radius)

      if (showTiltIndicators) {
        drawTiltIndicators(centerX, centerY, radius, pitch, roll)
      }
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val orientation = data.snapshot<OrientationSnapshot>()
    return if (orientation != null) {
      val degrees = orientation.bearing.roundToInt()
      val cardinal = getCardinalDirection(orientation.bearing)
      "Compass: heading $degrees degrees, $cardinal"
    } else {
      "Compass: no heading data"
    }
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  companion object {
    internal fun getCardinalDirection(bearing: Float): String {
      val normalized = ((bearing % 360f) + 360f) % 360f
      return when {
        normalized < 22.5f || normalized >= 337.5f -> "N"
        normalized < 67.5f -> "NE"
        normalized < 112.5f -> "E"
        normalized < 157.5f -> "SE"
        normalized < 202.5f -> "S"
        normalized < 247.5f -> "SW"
        normalized < 292.5f -> "W"
        else -> "NW"
      }
    }
  }
}

private fun DrawScope.drawTickMarks(centerX: Float, centerY: Float, radius: Float) {
  for (deg in 0 until 360 step 10) {
    val isMajor = deg % 30 == 0
    val tickLength = if (isMajor) radius * 0.1f else radius * 0.05f
    val tickWidth = if (isMajor) 2f else 1f
    val angleRad = (deg - 90f) * PI.toFloat() / 180f
    val startRadius = radius - tickLength
    drawLine(
      color = Color.White,
      start = Offset(centerX + startRadius * cos(angleRad), centerY + startRadius * sin(angleRad)),
      end = Offset(centerX + radius * cos(angleRad), centerY + radius * sin(angleRad)),
      strokeWidth = tickWidth,
    )
  }
}

private fun DrawScope.drawCardinalLabels(centerX: Float, centerY: Float, radius: Float) {
  val labels = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
  val labelRadius = radius * 0.75f
  drawIntoCanvas { canvas ->
    val paint =
      android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = radius * 0.15f
        isAntiAlias = true
        isFakeBoldText = true
      }
    val nPaint = android.graphics.Paint(paint).apply { color = android.graphics.Color.RED }
    for ((label, deg) in labels) {
      val angleRad = (deg - 90f) * PI.toFloat() / 180f
      val x = centerX + labelRadius * cos(angleRad)
      val y = centerY + labelRadius * sin(angleRad)
      canvas.nativeCanvas.drawText(
        label,
        x,
        y + paint.textSize / 3f,
        if (label == "N") nPaint else paint
      )
    }
  }
}

private fun DrawScope.drawNeedle(path: Path, centerX: Float, centerY: Float, radius: Float) {
  val needleLength = radius * 0.6f
  val needleWidth = radius * 0.06f
  path.reset()
  path.moveTo(centerX, centerY - needleLength)
  path.lineTo(centerX - needleWidth, centerY)
  path.lineTo(centerX + needleWidth, centerY)
  path.close()
  drawPath(path, color = Color.Red)
  path.reset()
  path.moveTo(centerX, centerY + needleLength)
  path.lineTo(centerX - needleWidth, centerY)
  path.lineTo(centerX + needleWidth, centerY)
  path.close()
  drawPath(path, color = Color.White.copy(alpha = 0.7f))
  drawCircle(color = Color.White, radius = needleWidth, center = Offset(centerX, centerY))
}

private fun DrawScope.drawTiltIndicators(
  centerX: Float,
  centerY: Float,
  radius: Float,
  pitch: Float,
  roll: Float,
) {
  val indicatorRadius = radius * 0.3f
  val pitchOffset = (pitch / 90f) * indicatorRadius
  drawLine(
    color = Color.Cyan.copy(alpha = 0.6f),
    start = Offset(centerX - indicatorRadius, centerY + pitchOffset),
    end = Offset(centerX + indicatorRadius, centerY + pitchOffset),
    strokeWidth = 2f,
    cap = StrokeCap.Round,
  )
  val rollOffset = (roll / 90f) * indicatorRadius
  drawLine(
    color = Color.Yellow.copy(alpha = 0.6f),
    start = Offset(centerX + rollOffset, centerY - indicatorRadius),
    end = Offset(centerX + rollOffset, centerY + indicatorRadius),
    strokeWidth = 2f,
    cap = StrokeCap.Round,
  )
}
