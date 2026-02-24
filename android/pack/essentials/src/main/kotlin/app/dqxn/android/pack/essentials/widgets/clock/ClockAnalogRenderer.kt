package app.dqxn.android.pack.essentials.widgets.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import app.dqxn.android.pack.essentials.snapshots.TimeSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.widget.LocalWidgetData
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

@DashboardWidget(
  typeId = "essentials:clock-analog",
  displayName = "Clock (Analog)",
)
public class ClockAnalogRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:clock-analog"
  override val displayName: String = "Clock (Analog)"
  override val description: String = "Analog clock with hour, minute, and second hands"
  override val aspectRatio: Float? = 1f
  override val supportsTap: Boolean = false
  override val priority: Int = 99
  override val requiredAnyEntitlement: Set<String>? = null

  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> =
    setOf(TimeSnapshot::class)

  override val settingsSchema: List<SettingDefinition<*>> = listOf(
    SettingDefinition.BooleanSetting(
      key = "showTickMarks",
      label = "Show Tick Marks",
      description = "Display hour and minute tick marks around the clock face",
      default = true,
    ),
    SettingDefinition.TimezoneSetting(
      key = "timezoneId",
      label = "Timezone",
      description = "Override the system timezone",
    ),
  )

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(
      widthUnits = 10,
      heightUnits = 10,
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
    val handAngles by remember {
      derivedStateOf {
        val snapshot = widgetData.snapshot<TimeSnapshot>()
        snapshot?.let { extractAngles(it, settings) }
      }
    }

    val showTickMarks = settings["showTickMarks"] as? Boolean ?: true
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error

    Canvas(
      modifier = modifier
        .fillMaxSize()
        .aspectRatio(1f),
    ) {
      val radius = min(size.width, size.height) / 2f
      val center = Offset(size.width / 2f, size.height / 2f)

      if (showTickMarks) {
        drawTickMarks(center, radius, onSurface, onSurfaceVariant)
      }

      val angles = handAngles
      if (angles != null) {
        // Hour hand
        drawHand(
          center = center,
          angle = angles.hourAngle,
          length = radius * 0.5f,
          strokeWidth = radius * 0.06f,
          color = onSurface,
        )

        // Minute hand
        drawHand(
          center = center,
          angle = angles.minuteAngle,
          length = radius * 0.75f,
          strokeWidth = radius * 0.04f,
          color = onSurface,
        )

        // Second hand
        drawHand(
          center = center,
          angle = angles.secondAngle,
          length = radius * 0.85f,
          strokeWidth = radius * 0.02f,
          color = error,
        )

        // Center dot
        drawCircle(
          color = onSurface,
          radius = radius * 0.04f,
          center = center,
        )
      } else {
        // No data: draw empty face with tick marks only
        drawCircle(
          color = onSurfaceVariant.copy(alpha = 0.38f),
          radius = radius * 0.04f,
          center = center,
        )
      }
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val snapshot = data.snapshot<TimeSnapshot>() ?: return "Analog clock: no data"
    val zoneId = ZoneId.of(snapshot.zoneId)
    val zonedTime = Instant.ofEpochMilli(snapshot.epochMillis).atZone(zoneId)
    val hour = zonedTime.hour % 12
    val displayHour = if (hour == 0) 12 else hour
    val minute = zonedTime.minute
    return "Analog clock showing $displayHour:%02d".format(minute)
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  private data class HandAngles(
    val hourAngle: Float,
    val minuteAngle: Float,
    val secondAngle: Float,
  )

  private fun extractAngles(
    snapshot: TimeSnapshot,
    settings: ImmutableMap<String, Any>,
  ): HandAngles {
    val timezoneIdStr = settings["timezoneId"] as? String
    val zoneId = ClockDigitalRenderer.resolveZone(timezoneIdStr, snapshot.zoneId)
    val zonedTime = Instant.ofEpochMilli(snapshot.epochMillis).atZone(zoneId)
    val hour = zonedTime.hour
    val minute = zonedTime.minute
    val second = zonedTime.second
    return HandAngles(
      hourAngle = computeHourHandAngle(hour, minute, second),
      minuteAngle = computeMinuteHandAngle(minute, second),
      secondAngle = computeSecondHandAngle(second),
    )
  }

  private fun DrawScope.drawTickMarks(
    center: Offset,
    radius: Float,
    majorColor: Color,
    minorColor: Color,
  ) {
    for (i in 0 until 60) {
      val angle = i * 6f
      val isMajor = i % 5 == 0
      val innerRadius = if (isMajor) radius * 0.85f else radius * 0.9f
      val strokeWidth = if (isMajor) radius * 0.025f else radius * 0.012f
      val color = if (isMajor) majorColor else minorColor.copy(alpha = 0.5f)
      val angleRad = Math.toRadians((angle - 90).toDouble())

      drawLine(
        color = color,
        start = Offset(
          center.x + (innerRadius * cos(angleRad)).toFloat(),
          center.y + (innerRadius * sin(angleRad)).toFloat(),
        ),
        end = Offset(
          center.x + (radius * 0.95f * cos(angleRad)).toFloat(),
          center.y + (radius * 0.95f * sin(angleRad)).toFloat(),
        ),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
      )
    }
  }

  private fun DrawScope.drawHand(
    center: Offset,
    angle: Float,
    length: Float,
    strokeWidth: Float,
    color: Color,
  ) {
    val angleRad = Math.toRadians((angle - 90).toDouble())
    val end = Offset(
      center.x + (length * cos(angleRad)).toFloat(),
      center.y + (length * sin(angleRad)).toFloat(),
    )
    drawLine(
      color = color,
      start = center,
      end = end,
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
  }

  companion object {
    /** Hour hand angle: (hour % 12 + minute / 60f) * 30f degrees. */
    internal fun computeHourHandAngle(hour: Int, minute: Int, second: Int): Float =
      (hour % 12 + minute / 60f + second / 3600f) * 30f

    /** Minute hand angle: minute * 6f + second / 10f degrees. */
    internal fun computeMinuteHandAngle(minute: Int, second: Int): Float =
      minute * 6f + second / 10f

    /** Second hand angle: second * 6f degrees. */
    internal fun computeSecondHandAngle(second: Int): Float = second * 6f
  }
}
