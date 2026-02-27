package app.dqxn.android.pack.essentials.widgets.date

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
  typeId = "essentials:date-grid",
  displayName = "Date (Grid)",
)
public class DateGridRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:date-grid"
  override val displayName: String = "Date (Grid)"
  override val description: String = "Grid layout showing day, month, day number, and year"
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 93
  override val requiredAnyEntitlement: Set<String>? = null

  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = setOf(TimeSnapshot::class)

  override val settingsSchema: List<SettingDefinition<*>> =
    listOf(
      SettingDefinition.TimezoneSetting(
        key = "timezoneId",
        label = "Timezone",
        description = "Override the system timezone",
      ),
    )

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(
      widthUnits = 10,
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
    val gridParts by remember {
      derivedStateOf {
        val snapshot = widgetData.snapshot<TimeSnapshot>()
        snapshot?.let { extractGridParts(it, settings) }
      }
    }

    Row(
      modifier = modifier.fillMaxSize().padding(8.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val parts = gridParts
      if (parts != null) {
        // Left column: large day number
        Text(
          text = parts.dayNumber,
          style = MaterialTheme.typography.displayLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(end = 12.dp),
        )
        // Right column: stacked day name, month, year
        Column(
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = parts.dayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = parts.monthName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = parts.year,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        Text(
          text = "--",
          style = MaterialTheme.typography.displayLarge,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
      }
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val snapshot = data.snapshot<TimeSnapshot>() ?: return "Date: no data"
    return DateSimpleRenderer.formatDateFromSnapshot(snapshot, DateFormatOption.MONTH_DAY_YEAR)
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  private data class GridParts(
    val dayName: String,
    val monthName: String,
    val dayNumber: String,
    val year: String,
  )

  private fun extractGridParts(
    snapshot: TimeSnapshot,
    settings: ImmutableMap<String, Any>,
  ): GridParts {
    val timezoneIdStr = settings["timezoneId"] as? String
    val zoneId =
      if (!timezoneIdStr.isNullOrEmpty()) {
        ZoneId.of(timezoneIdStr)
      } else {
        ZoneId.of(snapshot.zoneId)
      }
    val zonedDate = Instant.ofEpochMilli(snapshot.epochMillis).atZone(zoneId)
    val locale = Locale.getDefault()

    return GridParts(
      dayName = zonedDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
      monthName = zonedDate.month.getDisplayName(TextStyle.FULL, locale),
      dayNumber = zonedDate.dayOfMonth.toString(),
      year = zonedDate.year.toString(),
    )
  }
}
