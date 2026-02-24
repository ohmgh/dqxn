package app.dqxn.android.pack.essentials.widgets.date

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dqxn.android.pack.essentials.snapshots.TimeSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.DateFormatOption
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.widget.LocalWidgetData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

@DashboardWidget(
  typeId = "essentials:date-simple",
  displayName = "Date (Simple)",
)
internal class DateSimpleRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:date-simple"
  override val displayName: String = "Date (Simple)"
  override val description: String = "Single-line formatted date display"
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 95
  override val requiredAnyEntitlement: Set<String>? = null

  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> =
    setOf(TimeSnapshot::class)

  override val settingsSchema: List<SettingDefinition<*>> = listOf(
    SettingDefinition.DateFormatSetting(
      key = "dateFormat",
      label = "Date Format",
      description = "Choose how the date is displayed",
      default = DateFormatOption.MONTH_DAY_YEAR,
    ),
    SettingDefinition.TimezoneSetting(
      key = "timezoneId",
      label = "Timezone",
      description = "Override the system timezone",
    ),
  )

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(
      widthUnits = 8,
      heightUnits = 4,
      aspectRatio = null,
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
    val dateText by remember {
      derivedStateOf {
        val snapshot = widgetData.snapshot<TimeSnapshot>()
        snapshot?.let { formatDate(it, settings) }
      }
    }

    Column(
      modifier = modifier.fillMaxSize().padding(8.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = dateText ?: "--",
        style = MaterialTheme.typography.titleLarge,
        color = if (dateText != null) {
          MaterialTheme.colorScheme.onSurface
        } else {
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val snapshot = data.snapshot<TimeSnapshot>() ?: return "Date: no data"
    return formatDateFromSnapshot(snapshot, DateFormatOption.MONTH_DAY_YEAR)
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  private fun formatDate(snapshot: TimeSnapshot, settings: ImmutableMap<String, Any>): String {
    val formatOption = resolveFormatOption(settings)
    return formatDateFromSnapshot(snapshot, formatOption, settings["timezoneId"] as? String)
  }

  companion object {
    internal fun resolveFormatOption(settings: Map<String, Any>): DateFormatOption {
      val formatValue = settings["dateFormat"]
      return when (formatValue) {
        is DateFormatOption -> formatValue
        is String -> DateFormatOption.entries.find { it.name == formatValue }
          ?: DateFormatOption.MONTH_DAY_YEAR
        else -> DateFormatOption.MONTH_DAY_YEAR
      }
    }

    internal fun formatDateFromSnapshot(
      snapshot: TimeSnapshot,
      formatOption: DateFormatOption,
      timezoneIdStr: String? = null,
    ): String {
      val zoneId = if (!timezoneIdStr.isNullOrEmpty()) {
        ZoneId.of(timezoneIdStr)
      } else {
        ZoneId.of(snapshot.zoneId)
      }
      val zonedDate = Instant.ofEpochMilli(snapshot.epochMillis).atZone(zoneId)
      val formatter = DateTimeFormatter.ofPattern(formatOption.pattern, Locale.getDefault())
      return zonedDate.format(formatter)
    }
  }
}
