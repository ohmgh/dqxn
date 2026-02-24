package app.dqxn.android.pack.essentials.widgets.speedlimit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot
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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

@DashboardWidget(typeId = "essentials:speedlimit-circle", displayName = "Speed Limit (Circle)")
public class SpeedLimitCircleRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:speedlimit-circle"
  override val displayName: String = "Speed Limit (Circle)"
  override val description: String = "European-style circular speed limit sign"
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> =
    setOf(SpeedLimitSnapshot::class)
  override val aspectRatio: Float = 1f
  override val supportsTap: Boolean = false
  override val priority: Int = 100
  override val requiredAnyEntitlement: Set<String>? = null

  override val settingsSchema: List<SettingDefinition<*>> =
    listOf(
      SettingDefinition.IntSetting(
        key = "borderSizePercent",
        label = "Ring Size",
        description = "Thickness of the red border ring as percentage of sign diameter",
        default = 10,
        min = 0,
        max = 100,
        presets = listOf(6, 10, 14),
      ),
      SettingDefinition.EnumSetting(
        key = "speedUnit",
        label = "Speed Unit",
        description = "Unit for speed limit display",
        default = SpeedUnit.AUTO,
        options = SpeedUnit.entries.toList(),
      ),
      SettingDefinition.EnumSetting(
        key = "digitColor",
        label = "Digit Color",
        description = "Color of speed digits (Auto uses blue for Japan)",
        default = DigitColor.AUTO,
        options = DigitColor.entries.toList(),
      ),
    )

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(widthUnits = 8, heightUnits = 8, aspectRatio = 1f, settings = emptyMap())

  @Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  ) {
    val widgetData = LocalWidgetData.current
    val snapshot by remember { derivedStateOf { widgetData.snapshot<SpeedLimitSnapshot>() } }

    val speedUnit =
      (settings["speedUnit"] as? SpeedUnit)
        ?: (settings["speedUnit"] as? String)?.let { name ->
          SpeedUnit.entries.find { it.name == name }
        }
        ?: SpeedUnit.AUTO
    val digitColor =
      (settings["digitColor"] as? DigitColor)
        ?: (settings["digitColor"] as? String)?.let { name ->
          DigitColor.entries.find { it.name == name }
        }
        ?: DigitColor.AUTO
    val borderSizePercent = settings["borderSizePercent"] as? Int ?: 10

    val useImperial = RegionDetector.resolveSpeedUnit(speedUnit)
    val useBlueDigits = RegionDetector.resolveUseBlueDigits(digitColor)

    val displayLimit =
      snapshot?.speedLimitKph?.let { kph ->
        if (useImperial) (kph * KPH_TO_MPH).roundToInt() else kph.roundToInt()
      }

    val textPaint =
      remember(useBlueDigits) {
        android.graphics.Paint().apply {
          color =
            if (useBlueDigits) android.graphics.Color.BLUE else android.graphics.Color.BLACK
          textAlign = android.graphics.Paint.Align.CENTER
          isAntiAlias = true
          isFakeBoldText = true
        }
      }

    Canvas(modifier = modifier.fillMaxSize()) {
      val centerX = size.width / 2f
      val centerY = size.height / 2f
      val radius = min(centerX, centerY) * 0.95f
      val borderWidth = radius * 2f * borderSizePercent / 100f

      drawCircle(color = Color.White, radius = radius, center = Offset(centerX, centerY))

      drawCircle(
        color = Color.Red,
        radius = radius - borderWidth / 2f,
        center = Offset(centerX, centerY),
        style = Stroke(width = borderWidth),
      )

      if (displayLimit != null) {
        drawIntoCanvas { canvas ->
          textPaint.textSize = radius * 0.9f
          val textY = centerY + textPaint.textSize / 3f
          canvas.nativeCanvas.drawText(displayLimit.toString(), centerX, textY, textPaint)
        }
      }
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val snapshot = data.snapshot<SpeedLimitSnapshot>()
    return if (snapshot != null) {
      val useImperial = RegionDetector.usesMph()
      val displayLimit =
        if (useImperial) (snapshot.speedLimitKph * KPH_TO_MPH).roundToInt()
        else snapshot.speedLimitKph.roundToInt()
      val unit = if (useImperial) "mph" else "km/h"
      "Speed limit: $displayLimit $unit"
    } else {
      "Speed limit: unknown"
    }
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  internal companion object {
    const val KPH_TO_MPH: Float = 0.621371f
  }
}
