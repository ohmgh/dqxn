package app.dqxn.android.pack.essentials.widgets.ambientlight

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import app.dqxn.android.pack.essentials.snapshots.AmbientLightSnapshot
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

@DashboardWidget(typeId = "essentials:ambient-light", displayName = "Ambient Light")
public class AmbientLightRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:ambient-light"
  override val displayName: String = "Ambient Light"
  override val description: String = "Displays ambient light level in lux with category"
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> =
    setOf(AmbientLightSnapshot::class)
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 60
  override val requiredAnyEntitlement: Set<String>? = null

  override val settingsSchema: List<SettingDefinition<*>> = infoCardSettingsSchema()

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(widthUnits = 8, heightUnits = 8, aspectRatio = null, settings = emptyMap())

  @Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  ) {
    val widgetData = LocalWidgetData.current
    val snapshot by remember { derivedStateOf { widgetData.snapshot<AmbientLightSnapshot>() } }

    val layoutMode =
      remember(settings) {
        (settings["info_card_layout_mode"] as? InfoCardLayoutMode) ?: InfoCardLayoutMode.STANDARD
      }
    val sizeOption =
      remember(settings) { (settings["info_card_size"] as? SizeOption) ?: SizeOption.MEDIUM }

    val currentSnapshot = snapshot
    val numberFormat = remember { NumberFormat.getInstance(Locale.getDefault()) }

    InfoCardLayout(
      modifier = modifier,
      layoutMode = layoutMode,
      iconSize = sizeOption,
      topTextSize = sizeOption,
      bottomTextSize = SizeOption.SMALL,
      icon = { size: Dp ->
        LightBulbIcon(
          category = currentSnapshot?.category,
          size = size,
        )
      },
      topText = { textStyle: TextStyle ->
        val luxText =
          if (currentSnapshot != null) {
            "${numberFormat.format(currentSnapshot.lux)} lux"
          } else {
            "-- lux"
          }
        Text(
          text = luxText,
          style = textStyle,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
        )
      },
      bottomText = { textStyle: TextStyle ->
        val categoryText = currentSnapshot?.category?.let { formatCategory(it) } ?: "Unknown"
        Text(
          text = categoryText,
          style = textStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      },
    )
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val snapshot = data.snapshot<AmbientLightSnapshot>()
    if (snapshot == null) return "Ambient Light: No data"
    val numberFormat = NumberFormat.getInstance(Locale.getDefault())
    return "Ambient Light: ${numberFormat.format(snapshot.lux)} lux, ${formatCategory(snapshot.category)}"
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  internal companion object {
    internal fun formatCategory(category: String): String =
      when (category.uppercase()) {
        "DARK" -> "Dark"
        "DIM" -> "Dim"
        "NORMAL" -> "Normal"
        "BRIGHT" -> "Bright"
        "VERY_BRIGHT" -> "Very Bright"
        else -> category.lowercase().replaceFirstChar { it.uppercase() }
      }
  }
}
