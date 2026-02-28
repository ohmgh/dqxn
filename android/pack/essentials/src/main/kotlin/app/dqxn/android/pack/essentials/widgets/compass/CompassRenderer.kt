package app.dqxn.android.pack.essentials.widgets.compass

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
 * Uses `drawWithCache` for size-dependent geometry (tick positions, Paint objects) and defers
 * data-dependent reads (bearing, pitch, roll) to the draw phase via `State<T>.value`.
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

    // Keep as State<T> (no `by`) — reads deferred to draw phase
    val orientationState: State<OrientationSnapshot?> = remember {
      derivedStateOf { widgetData.snapshot<OrientationSnapshot>() }
    }

    val showTickMarks = settings["showTickMarks"] as? Boolean ?: true
    val showCardinalLabels = settings["showCardinalLabels"] as? Boolean ?: true
    val showTiltIndicators = settings["showTiltIndicators"] as? Boolean ?: false

    val needlePath = remember { Path() }

    Spacer(
      modifier =
        modifier.fillMaxSize().drawWithCache {
          val centerX = size.width / 2f
          val centerY = size.height / 2f
          val radius = min(centerX, centerY) * 0.9f

          // Pre-compute tick mark geometry (only on size change)
          data class TickGeom(
            val startX: Float,
            val startY: Float,
            val endX: Float,
            val endY: Float,
            val isMajor: Boolean,
          )

          val ticks =
            if (showTickMarks) {
              buildList {
                for (deg in 0 until 360 step 10) {
                  val isMajor = deg % 30 == 0
                  val tickLength = if (isMajor) radius * 0.1f else radius * 0.05f
                  val tickWidth = if (isMajor) 2f else 1f
                  val angleRad = (deg - 90f) * PI.toFloat() / 180f
                  val startRadius = radius - tickLength
                  val cosA = cos(angleRad)
                  val sinA = sin(angleRad)
                  add(
                    TickGeom(
                      startX = centerX + startRadius * cosA,
                      startY = centerY + startRadius * sinA,
                      endX = centerX + radius * cosA,
                      endY = centerY + radius * sinA,
                      isMajor = isMajor,
                    ),
                  )
                }
              }
            } else {
              emptyList()
            }

          // Pre-compute cardinal label positions
          data class CardinalLabelGeom(val label: String, val x: Float, val y: Float, val isNorth: Boolean)

          val labelRadius = radius * 0.75f
          val cardinalLabels =
            if (showCardinalLabels) {
              CARDINAL_LABELS.map { (label, deg) ->
                val angleRad = (deg - 90f) * PI.toFloat() / 180f
                CardinalLabelGeom(
                  label = label,
                  x = centerX + labelRadius * cos(angleRad),
                  y = centerY + labelRadius * sin(angleRad),
                  isNorth = label == "N",
                )
              }
            } else {
              emptyList()
            }

          // Create native Paint objects here (cached, recreated only on size change when textSize
          // depends on radius)
          val labelPaint =
            if (showCardinalLabels) {
              android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = radius * 0.15f
                isAntiAlias = true
                isFakeBoldText = true
              }
            } else {
              null
            }
          val nLabelPaint =
            if (showCardinalLabels && labelPaint != null) {
              android.graphics.Paint(labelPaint).apply {
                color = android.graphics.Color.RED
              }
            } else {
              null
            }

          // Needle geometry
          val needleLength = radius * 0.6f
          val needleWidth = radius * 0.06f

          // Tilt indicator geometry
          val indicatorRadius = radius * 0.3f

          onDrawBehind {
            // Read orientation in draw phase — only triggers draw invalidation, not recomposition
            val orientation = orientationState.value
            val bearing = orientation?.bearing ?: 0f
            val pitch = orientation?.pitch ?: 0f
            val roll = orientation?.roll ?: 0f

            // Circle outline
            drawCircle(
              color = CIRCLE_OUTLINE_COLOR,
              radius = radius,
              center = Offset(centerX, centerY),
              style = Stroke(width = 2f),
            )

            // Rotating dial
            rotate(degrees = -bearing, pivot = Offset(centerX, centerY)) {
              // Tick marks
              for (tick in ticks) {
                drawLine(
                  color = Color.White,
                  start = Offset(tick.startX, tick.startY),
                  end = Offset(tick.endX, tick.endY),
                  strokeWidth = if (tick.isMajor) 2f else 1f,
                )
              }

              // Cardinal labels
              if (labelPaint != null && nLabelPaint != null) {
                drawIntoCanvas { canvas ->
                  for (cl in cardinalLabels) {
                    canvas.nativeCanvas.drawText(
                      cl.label,
                      cl.x,
                      cl.y + labelPaint.textSize / 3f,
                      if (cl.isNorth) nLabelPaint else labelPaint,
                    )
                  }
                }
              }
            }

            // Stationary needle
            drawNeedle(needlePath, centerX, centerY, needleLength, needleWidth)

            // Tilt indicators
            if (showTiltIndicators) {
              val pitchOffset = (pitch / 90f) * indicatorRadius
              drawLine(
                color = TILT_PITCH_COLOR,
                start = Offset(centerX - indicatorRadius, centerY + pitchOffset),
                end = Offset(centerX + indicatorRadius, centerY + pitchOffset),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
              )
              val rollOffset = (roll / 90f) * indicatorRadius
              drawLine(
                color = TILT_ROLL_COLOR,
                start = Offset(centerX + rollOffset, centerY - indicatorRadius),
                end = Offset(centerX + rollOffset, centerY + indicatorRadius),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
              )
            }
          }
        },
    )
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
    // Pre-computed color copies
    private val CIRCLE_OUTLINE_COLOR = Color.White.copy(alpha = 0.3f)
    internal val NEEDLE_SOUTH_COLOR = Color.White.copy(alpha = 0.7f)
    private val TILT_PITCH_COLOR = Color.Cyan.copy(alpha = 0.6f)
    private val TILT_ROLL_COLOR = Color.Yellow.copy(alpha = 0.6f)

    private val CARDINAL_LABELS = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)

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

/** Draw compass needle (north red, south white, center dot). Reuses [path] to avoid allocation. */
private fun DrawScope.drawNeedle(
  path: Path,
  centerX: Float,
  centerY: Float,
  needleLength: Float,
  needleWidth: Float,
) {
  // North (up) — red
  path.reset()
  path.moveTo(centerX, centerY - needleLength)
  path.lineTo(centerX - needleWidth, centerY)
  path.lineTo(centerX + needleWidth, centerY)
  path.close()
  drawPath(path, color = Color.Red)

  // South (down) — white translucent
  path.reset()
  path.moveTo(centerX, centerY + needleLength)
  path.lineTo(centerX - needleWidth, centerY)
  path.lineTo(centerX + needleWidth, centerY)
  path.close()
  drawPath(path, color = CompassRenderer.NEEDLE_SOUTH_COLOR)

  // Center dot
  drawCircle(color = Color.White, radius = needleWidth, center = Offset(centerX, centerY))
}
