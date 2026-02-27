package app.dqxn.android.pack.essentials.widgets.speedometer

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dqxn.android.pack.essentials.snapshots.AccelerationSnapshot
import app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot
import app.dqxn.android.pack.essentials.snapshots.SpeedSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.widget.LocalWidgetData
import java.text.NumberFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

/**
 * Multi-slot speedometer widget with circular gauge arc, 12-segment acceleration arc, auto-scaling
 * gauge max, and NF40-compliant speed limit warning (color + pulsing border + warning icon).
 *
 * Consumes 3 independent snapshot slots: [SpeedSnapshot], [AccelerationSnapshot],
 * [SpeedLimitSnapshot]. Each slot is read via separate `derivedStateOf` -- null snapshot means the
 * feature is simply not shown (no crash).
 */
@DashboardWidget(
  typeId = "essentials:speedometer",
  displayName = "Speedometer",
)
public class SpeedometerRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:speedometer"
  override val displayName: String = "Speedometer"
  override val description: String =
    "Circular speed gauge with acceleration arc and speed limit warning"
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> =
    setOf(SpeedSnapshot::class, AccelerationSnapshot::class, SpeedLimitSnapshot::class)
  override val aspectRatio: Float = 1f
  override val supportsTap: Boolean = false
  override val priority: Int = 100
  override val requiredAnyEntitlement: Set<String>? = null

  override val settingsSchema: List<SettingDefinition<*>> =
    listOf(
      SettingDefinition.BooleanSetting(
        key = "showSpeedArc",
        label = "Show speed arc",
        default = true,
        groupId = "arcs",
      ),
      SettingDefinition.BooleanSetting(
        key = "showAccelerationArc",
        label = "Show acceleration arc",
        default = true,
        groupId = "arcs",
      ),
      SettingDefinition.IntSetting(
        key = "arcStrokeDp",
        label = "Arc stroke width",
        default = 6,
        min = 2,
        max = 12,
      ),
      SettingDefinition.BooleanSetting(
        key = "showTickMarks",
        label = "Show tick marks",
        default = true,
      ),
      SettingDefinition.EnumSetting(
        key = "speedUnit",
        label = "Speed unit",
        default = SpeedUnit.AUTO,
        options = SpeedUnit.entries.toList(),
      ),
      SettingDefinition.IntSetting(
        key = "speedLimitOffsetKph",
        label = "Speed limit offset (km/h)",
        description = "Amber warning triggers when exceeding limit by this amount",
        default = 5,
        min = 0,
        max = 30,
      ),
      SettingDefinition.IntSetting(
        key = "speedLimitOffsetMph",
        label = "Speed limit offset (mph)",
        description = "Amber warning triggers when exceeding limit by this amount",
        default = 3,
        min = 0,
        max = 20,
      ),
      SettingDefinition.BooleanSetting(
        key = "showWarningBackground",
        label = "Show warning background",
        default = true,
      ),
      SettingDefinition.EnumSetting(
        key = "alertType",
        label = "Alert type",
        default = AlertType.VISUAL,
        options = AlertType.entries.toList(),
      ),
    )

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(
      widthUnits = 12,
      heightUnits = 12,
      aspectRatio = 1f,
      settings = emptyMap(),
    )

  @Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  ) {
    val widgetData = LocalWidgetData.current
    val speed by remember { derivedStateOf { widgetData.snapshot<SpeedSnapshot>() } }
    val acceleration by remember { derivedStateOf { widgetData.snapshot<AccelerationSnapshot>() } }
    val speedLimit by remember { derivedStateOf { widgetData.snapshot<SpeedLimitSnapshot>() } }

    val currentSpeedMps = speed?.speedMps ?: 0f
    val currentSpeedKph = currentSpeedMps * MPS_TO_KPH
    val gaugeMax = computeGaugeMax(currentSpeedKph)
    val arcAngle = computeArcAngle(currentSpeedKph, gaugeMax)

    val accelValue = acceleration?.acceleration ?: 0f
    val accelSegments = computeAccelerationSegments(accelValue, MAX_ACCELERATION)

    // NF40: Speed limit warning state
    val limitKph = speedLimit?.speedLimitKph
    val isAmber = limitKph != null && currentSpeedKph > limitKph + AMBER_OFFSET_KPH
    val isRed = limitKph != null && currentSpeedKph > limitKph + RED_OFFSET_KPH

    // Pulsing animation for NF40 warning
    val infiniteTransition = rememberInfiniteTransition(label = "speedLimitPulse")
    val pulseAlpha by
      infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "pulseAlpha",
      )

    Box(modifier = modifier.fillMaxSize()) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(size.width, size.height) / 2f * 0.85f
        val strokeWidthPx = 6.dp.toPx()

        // NF40: Warning background
        if (isAmber || isRed) {
          val bgColor =
            if (isRed) {
              Color.Red.copy(alpha = 0.15f)
            } else {
              COLOR_AMBER.copy(alpha = 0.15f)
            }
          drawCircle(
            color = bgColor,
            radius = radius * 1.05f,
            center = Offset(centerX, centerY),
          )
        }

        // NF40: Pulsing border
        if (isAmber || isRed) {
          val borderColor =
            if (isRed) {
              Color.Red.copy(alpha = pulseAlpha)
            } else {
              COLOR_AMBER.copy(alpha = pulseAlpha)
            }
          drawCircle(
            color = borderColor,
            radius = radius * 1.05f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3.dp.toPx()),
          )
        }

        // Background track arc (270 degrees, from 135 to 405)
        drawArc(
          color = Color.Gray.copy(alpha = 0.3f),
          startAngle = ARC_START_ANGLE,
          sweepAngle = ARC_SWEEP,
          useCenter = false,
          topLeft = Offset(centerX - radius, centerY - radius),
          size = Size(radius * 2, radius * 2),
          style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )

        // Speed arc
        if (arcAngle > 0f) {
          drawArc(
            color = COLOR_SPEED_ARC,
            startAngle = ARC_START_ANGLE,
            sweepAngle = arcAngle,
            useCenter = false,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
          )
        }

        // 12-segment acceleration arc
        if (accelSegments != 0) {
          val accelRadius = radius - strokeWidthPx * 2.5f
          val segmentSweep = ARC_SWEEP / ACCEL_SEGMENT_COUNT
          val segmentGap = 2f
          val fillCount = abs(accelSegments)
          val segmentColor = if (accelSegments > 0) COLOR_ACCEL_POSITIVE else COLOR_ACCEL_NEGATIVE

          for (i in 0 until fillCount) {
            val segStart = ARC_START_ANGLE + i * segmentSweep + segmentGap / 2
            val segSweep = segmentSweep - segmentGap
            drawArc(
              color = segmentColor,
              startAngle = segStart,
              sweepAngle = segSweep,
              useCenter = false,
              topLeft = Offset(centerX - accelRadius, centerY - accelRadius),
              size = Size(accelRadius * 2, accelRadius * 2),
              style = Stroke(width = strokeWidthPx * 0.6f, cap = StrokeCap.Butt),
            )
          }
        }

        // Tick marks
        val tickRadius = radius + strokeWidthPx
        val majorTickLen = strokeWidthPx
        val minorTickLen = strokeWidthPx * 0.5f

        for (i in 0..TICK_COUNT) {
          val angle = ARC_START_ANGLE + (ARC_SWEEP * i / TICK_COUNT)
          val isMajor = i % (TICK_COUNT / 6) == 0
          val tickLen = if (isMajor) majorTickLen else minorTickLen
          val radAngle = Math.toRadians(angle.toDouble())

          val startX = centerX + (tickRadius - tickLen) * cos(radAngle).toFloat()
          val startY = centerY + (tickRadius - tickLen) * sin(radAngle).toFloat()
          val endX = centerX + tickRadius * cos(radAngle).toFloat()
          val endY = centerY + tickRadius * sin(radAngle).toFloat()

          drawLine(
            color = if (isMajor) Color.White else Color.White.copy(alpha = 0.5f),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
          )
        }
      }

      // Speed text overlay
      val numberFormat = remember { NumberFormat.getInstance(Locale.getDefault()) }
      val displaySpeed = currentSpeedKph.roundToInt()
      val unitLabel = detectSpeedUnitLabel()

      Text(
        text = numberFormat.format(displaySpeed),
        color = Color.White,
        fontSize = 32.sp,
        modifier = Modifier.align(Alignment.Center),
      )

      Text(
        text = unitLabel,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 14.sp,
        modifier = Modifier.align(Alignment.Center).padding(top = 48.dp),
      )

      // NF40: Warning triangle icon (Canvas-drawn to avoid material-icons-extended dependency)
      if (isAmber || isRed) {
        val warningColor = if (isRed) Color.Red else COLOR_AMBER
        Canvas(
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).requiredSize(24.dp),
        ) {
          val trianglePath =
            Path().apply {
              moveTo(size.width / 2f, 0f)
              lineTo(size.width, size.height)
              lineTo(0f, size.height)
              close()
            }
          drawPath(trianglePath, color = warningColor)
          drawLine(
            color = Color.White,
            start = Offset(size.width / 2f, size.height * 0.3f),
            end = Offset(size.width / 2f, size.height * 0.6f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
          )
          drawCircle(
            color = Color.White,
            radius = 1.5.dp.toPx(),
            center = Offset(size.width / 2f, size.height * 0.75f),
          )
        }
      }
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val speed = data.snapshot<SpeedSnapshot>()
    val speedLimit = data.snapshot<SpeedLimitSnapshot>()

    if (speed == null) return "Speedometer: No data"

    val speedKph = speed.speedMps * MPS_TO_KPH
    val numberFormat = NumberFormat.getInstance(Locale.getDefault())
    val speedValue = numberFormat.format(speedKph.roundToInt())

    return buildString {
      append("Speed: $speedValue km/h")
      if (speedLimit != null) {
        val limitValue = numberFormat.format(speedLimit.speedLimitKph.roundToInt())
        append(", Limit: $limitValue km/h")
        if (speedKph > speedLimit.speedLimitKph) {
          append(", Over limit")
        }
      }
    }
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  internal companion object {
    const val ARC_START_ANGLE = 135f
    const val ARC_SWEEP = 270f
    const val TICK_COUNT = 54
    const val ACCEL_SEGMENT_COUNT = 12
    const val MAX_ACCELERATION = 10f
    const val MPS_TO_KPH = 3.6f
    const val MPS_TO_MPH = 2.23694f
    const val AMBER_OFFSET_KPH = 5f
    const val RED_OFFSET_KPH = 10f
    val COLOR_AMBER = Color(0xFFFF9800)
    val COLOR_SPEED_ARC = Color(0xFF4FC3F7)
    val COLOR_ACCEL_POSITIVE = Color(0xFF66BB6A)
    val COLOR_ACCEL_NEGATIVE = Color(0xFFEF5350)

    /**
     * Auto-scaling gauge maximum using stepped thresholds. Each threshold covers a speed range; the
     * next threshold kicks in when the current max is exceeded.
     */
    fun computeGaugeMax(speedKph: Float): Float =
      when {
        speedKph <= 30f -> 60f
        speedKph <= 60f -> 120f
        speedKph <= 120f -> 200f
        speedKph <= 200f -> 300f
        else -> 400f
      }

    /** Computes the arc sweep angle for the given speed and gauge maximum. [0, 270] degrees. */
    fun computeArcAngle(speedKph: Float, gaugeMax: Float): Float {
      if (gaugeMax <= 0f) return 0f
      return ((speedKph / gaugeMax) * ARC_SWEEP).coerceIn(0f, ARC_SWEEP)
    }

    /**
     * Computes the number of acceleration segments to fill (out of 12). Positive acceleration fills
     * positive segments; negative fills negative segments.
     */
    fun computeAccelerationSegments(acceleration: Float, maxAcceleration: Float): Int {
      if (maxAcceleration <= 0f) return 0
      val ratio = (acceleration / maxAcceleration).coerceIn(-1f, 1f)
      return (ratio * ACCEL_SEGMENT_COUNT).roundToInt()
    }
  }
}

/** Speed unit selection for the speedometer widget. */
public enum class SpeedUnit {
  AUTO,
  KPH,
  MPH,
}

/** Alert type for speed limit warnings. */
public enum class AlertType {
  VISUAL,
  HAPTIC,
  BOTH,
  NONE,
}

/**
 * Detects the appropriate speed unit label based on the device region. US, UK, Myanmar, and Liberia
 * use mph; all others use km/h.
 */
private fun detectSpeedUnitLabel(): String {
  val country =
    TimeZone.getDefault().id.let { tzId ->
      when {
        tzId.startsWith("America/") -> "US"
        tzId.startsWith("Europe/London") -> "GB"
        else -> Locale.getDefault().country
      }
    }
  return when (country.uppercase()) {
    "US",
    "GB",
    "MM",
    "LR" -> "mph"
    else -> "km/h"
  }
}
