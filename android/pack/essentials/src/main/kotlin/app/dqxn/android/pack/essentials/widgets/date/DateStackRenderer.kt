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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

@DashboardWidget(
  typeId = "essentials:date-stack",
  displayName = "Date (Stack)",
)
internal class DateStackRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:date-stack"
  override val displayName: String = "Date (Stack)"
  override val description: String = "Vertically stacked day-of-week, month, and day number"
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 94
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
      heightUnits = 6,
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
    val dateParts by remember {
      derivedStateOf {
        val snapshot = widgetData.snapshot<TimeSnapshot>()
        snapshot?.let { extractDateParts(it, settings) }
      }
    }

    Column(
      modifier = modifier.fillMaxSize().padding(8.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val parts = dateParts
      if (parts != null) {
        Text(
          text = parts.dayOfWeek,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Text(
          text = parts.dayNumber,
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        )
        Text(
          text = parts.month,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center,
        )
      } else {
        Text(
          text = "--",
          style = MaterialTheme.typography.displayMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          textAlign = TextAlign.Center,
        )
      }
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val snapshot = data.snapshot<TimeSnapshot>() ?: return "Date: no data"
    return DateSimpleRenderer.formatDateFromSnapshot(snapshot, DateFormatOption.MONTH_DAY_YEAR)
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  private data class DateParts(
    val dayOfWeek: String,
    val dayNumber: String,
    val month: String,
  )

  private fun extractDateParts(
    snapshot: TimeSnapshot,
    settings: ImmutableMap<String, Any>,
  ): DateParts {
    val timezoneIdStr = settings["timezoneId"] as? String
    val zoneId = if (!timezoneIdStr.isNullOrEmpty()) {
      ZoneId.of(timezoneIdStr)
    } else {
      ZoneId.of(snapshot.zoneId)
    }
    val zonedDate = Instant.ofEpochMilli(snapshot.epochMillis).atZone(zoneId)
    val locale = Locale.getDefault()

    return DateParts(
      dayOfWeek = zonedDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale).uppercase(locale),
      dayNumber = zonedDate.dayOfMonth.toString(),
      month = zonedDate.month.getDisplayName(TextStyle.FULL, locale).uppercase(locale),
    )
  }
}
