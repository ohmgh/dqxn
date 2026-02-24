package app.dqxn.android.pack.essentials.widgets.battery

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import app.dqxn.android.pack.essentials.snapshots.BatterySnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.InfoCardLayoutMode
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.settings.SizeOption
import app.dqxn.android.sdk.contracts.settings.infoCardSettingsSchema
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.layout.InfoCardLayout
import app.dqxn.android.sdk.ui.widget.LocalWidgetData
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

@DashboardWidget(typeId = "essentials:battery", displayName = "Battery")
public class BatteryRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:battery"
  override val displayName: String = "Battery"
  override val description: String = "Displays battery level, charging state, and optional temperature"
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = setOf(BatterySnapshot::class)
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 50
  override val requiredAnyEntitlement: Set<String>? = null

  override val settingsSchema: List<SettingDefinition<*>> =
    listOf(
      SettingDefinition.BooleanSetting(
        key = SETTING_SHOW_PERCENTAGE,
        label = "Show Percentage",
        description = "Display battery level as percentage",
        default = true,
      ),
      SettingDefinition.BooleanSetting(
        key = SETTING_SHOW_TEMPERATURE,
        label = "Show Temperature",
        description = "Display battery temperature",
        default = false,
      ),
      SettingDefinition.BooleanSetting(
        key = SETTING_CHARGING_INDICATOR,
        label = "Charging Indicator",
        description = "Show charging icon when plugged in",
        default = true,
      ),
    ) + infoCardSettingsSchema()

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(widthUnits = 6, heightUnits = 6, aspectRatio = null, settings = emptyMap())

  @Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  ) {
    val widgetData = LocalWidgetData.current
    val snapshot by remember { derivedStateOf { widgetData.snapshot<BatterySnapshot>() } }

    val layoutMode =
      remember(settings) {
        (settings["info_card_layout_mode"] as? InfoCardLayoutMode) ?: InfoCardLayoutMode.STANDARD
      }
    val sizeOption =
      remember(settings) { (settings["info_card_size"] as? SizeOption) ?: SizeOption.MEDIUM }
    val showPercentage =
      remember(settings) { (settings[SETTING_SHOW_PERCENTAGE] as? Boolean) ?: true }
    val showTemperature =
      remember(settings) { (settings[SETTING_SHOW_TEMPERATURE] as? Boolean) ?: false }
    val showCharging =
      remember(settings) { (settings[SETTING_CHARGING_INDICATOR] as? Boolean) ?: true }

    val currentSnapshot = snapshot
    val numberFormat = remember { NumberFormat.getInstance(Locale.getDefault()) }

    InfoCardLayout(
      modifier = modifier,
      layoutMode = layoutMode,
      iconSize = sizeOption,
      topTextSize = sizeOption,
      bottomTextSize = SizeOption.SMALL,
      icon = { size: Dp ->
        BatteryIcon(
          level = currentSnapshot?.level,
          isCharging = currentSnapshot?.isCharging == true && showCharging,
          size = size,
        )
      },
      topText = { textStyle: TextStyle ->
        val levelText =
          if (currentSnapshot != null && showPercentage) {
            "${numberFormat.format(currentSnapshot.level)}%"
          } else if (currentSnapshot != null) {
            numberFormat.format(currentSnapshot.level)
          } else {
            "--"
          }
        Text(
          text = levelText,
          style = textStyle,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
        )
      },
      bottomText = { textStyle: TextStyle ->
        val statusText = buildString {
          if (currentSnapshot != null) {
            if (currentSnapshot.isCharging && showCharging) {
              append("Charging")
            }
            if (showTemperature && currentSnapshot.temperature != null) {
              if (isNotEmpty()) append(" | ")
              append("${numberFormat.format(currentSnapshot.temperature)}\u00B0C")
            }
          }
        }
        if (statusText.isNotEmpty()) {
          Text(
            text = statusText,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      },
    )
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val snapshot = data.snapshot<BatterySnapshot>()
    if (snapshot == null) return "Battery: No data"
    val numberFormat = NumberFormat.getInstance(Locale.getDefault())
    return buildString {
      append("Battery: ${numberFormat.format(snapshot.level)}%")
      if (snapshot.isCharging) {
        append(", Charging")
      }
      if (snapshot.temperature != null) {
        append(", ${numberFormat.format(snapshot.temperature)}\u00B0C")
      }
    }
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  internal companion object {
    const val SETTING_SHOW_PERCENTAGE: String = "show_percentage"
    const val SETTING_SHOW_TEMPERATURE: String = "show_temperature"
    const val SETTING_CHARGING_INDICATOR: String = "charging_indicator"
  }
}
