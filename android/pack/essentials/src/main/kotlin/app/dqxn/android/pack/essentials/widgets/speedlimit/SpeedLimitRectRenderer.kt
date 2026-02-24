package app.dqxn.android.pack.essentials.widgets.speedlimit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

@DashboardWidget(typeId = "essentials:speedlimit-rect", displayName = "Speed Limit (Rectangle)")
internal class SpeedLimitRectRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:speedlimit-rect"
  override val displayName: String = "Speed Limit (Rectangle)"
  override val description: String = "US MUTCD-style rectangular speed limit sign"
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> =
    setOf(SpeedLimitSnapshot::class)
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 99
  override val requiredAnyEntitlement: Set<String>? = null

  override val settingsSchema: List<SettingDefinition<*>> =
    listOf(
      SettingDefinition.IntSetting(
        key = "borderSizePercent",
        label = "Border Size",
        description = "Thickness of the border as percentage of sign width",
        default = 5,
        min = 0,
        max = 100,
        presets = listOf(3, 5, 8),
      ),
      SettingDefinition.EnumSetting(
        key = "speedUnit",
        label = "Speed Unit",
        description = "Unit for speed limit display",
        default = SpeedUnit.AUTO,
        options = SpeedUnit.entries.toList(),
      ),
    )

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(widthUnits = 6, heightUnits = 8, aspectRatio = null, settings = emptyMap())

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
    val borderSizePercent = settings["borderSizePercent"] as? Int ?: 5

    val useImperial = RegionDetector.resolveSpeedUnit(speedUnit)

    val displayLimit =
      snapshot?.speedLimitKph?.let { kph ->
        if (useImperial) (kph * SpeedLimitCircleRenderer.KPH_TO_MPH).roundToInt()
        else kph.roundToInt()
      }

    val headerPaint = remember {
      android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
      }
    }
    val numberPaint = remember {
      android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
      }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
      val signWidth = size.width * 0.95f
      val signHeight = size.height * 0.95f
      val left = (size.width - signWidth) / 2f
      val top = (size.height - signHeight) / 2f
      val borderWidth = min(signWidth, signHeight) * borderSizePercent / 100f
      val cornerRadius = min(signWidth, signHeight) * 0.05f

      drawRoundRect(
        color = Color.White,
        topLeft = Offset(left, top),
        size = Size(signWidth, signHeight),
        cornerRadius = CornerRadius(cornerRadius),
      )

      drawRoundRect(
        color = Color.Black,
        topLeft = Offset(left + borderWidth / 2f, top + borderWidth / 2f),
        size = Size(signWidth - borderWidth, signHeight - borderWidth),
        cornerRadius = CornerRadius(cornerRadius),
        style = Stroke(width = borderWidth),
      )

      val centerX = size.width / 2f

      drawIntoCanvas { canvas ->
        headerPaint.textSize = signHeight * 0.12f
        val speedY = top + signHeight * 0.25f
        canvas.nativeCanvas.drawText("SPEED", centerX, speedY, headerPaint)

        val limitY = speedY + headerPaint.textSize * 1.2f
        canvas.nativeCanvas.drawText("LIMIT", centerX, limitY, headerPaint)

        if (displayLimit != null) {
          numberPaint.textSize = signHeight * 0.35f
          val numberY = top + signHeight * 0.78f
          canvas.nativeCanvas.drawText(displayLimit.toString(), centerX, numberY, numberPaint)
        }
      }
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val snapshot = data.snapshot<SpeedLimitSnapshot>()
    return if (snapshot != null) {
      val useImperial = RegionDetector.usesMph()
      val displayLimit =
        if (useImperial) (snapshot.speedLimitKph * SpeedLimitCircleRenderer.KPH_TO_MPH).roundToInt()
        else snapshot.speedLimitKph.roundToInt()
      val unit = if (useImperial) "mph" else "km/h"
      "Speed limit: $displayLimit $unit"
    } else {
      "Speed limit: unknown"
    }
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false
}
