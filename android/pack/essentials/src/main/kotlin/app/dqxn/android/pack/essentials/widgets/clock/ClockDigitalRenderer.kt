package app.dqxn.android.pack.essentials.widgets.clock

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
  typeId = "essentials:clock",
  displayName = "Clock (Digital)",
)
public class ClockDigitalRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:clock"
  override val displayName: String = "Clock (Digital)"
  override val description: String = "Digital clock with configurable seconds and 24-hour format"
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 100
  override val requiredAnyEntitlement: Set<String>? = null

  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = setOf(TimeSnapshot::class)

  override val settingsSchema: List<SettingDefinition<*>> =
    listOf(
      SettingDefinition.BooleanSetting(
        key = "showSeconds",
        label = "Show Seconds",
        description = "Display seconds alongside hours and minutes",
        default = false,
      ),
      SettingDefinition.BooleanSetting(
        key = "use24HourFormat",
        label = "24-Hour Format",
        description = "Use 24-hour time format instead of 12-hour AM/PM",
        default = true,
      ),
      SettingDefinition.BooleanSetting(
        key = "showLeadingZero",
        label = "Show Leading Zero",
        description = "Display leading zero for single-digit hours",
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
      heightUnits = 9,
      aspectRatio = null,
      settings = emptyMap(),
    )

  /**
   * Returns defaults with width adjusted for seconds display. When showSeconds is enabled, width
   * increases by 2 units.
   */
  internal fun getDefaultsWithSettings(
    context: WidgetContext,
    settings: Map<String, Any?>,
  ): WidgetDefaults {
    val showSeconds = settings["showSeconds"] as? Boolean ?: false
    val baseWidth = 10
    val width = if (showSeconds) baseWidth + 2 else baseWidth
    return WidgetDefaults(
      widthUnits = width,
      heightUnits = 9,
      aspectRatio = null,
      settings = emptyMap(),
    )
  }

  @Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  ) {
    val widgetData = LocalWidgetData.current
    val timeState by remember {
      derivedStateOf {
        val snapshot = widgetData.snapshot<TimeSnapshot>()
        snapshot?.let { extractTime(it, settings) }
      }
    }

    Column(
      modifier = modifier.fillMaxSize().padding(8.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val time = timeState
      if (time != null) {
        Row(
          verticalAlignment = Alignment.Bottom,
        ) {
          Text(
            text = time.mainTime,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (time.suffix.isNotEmpty()) {
            Text(
              text = time.suffix,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
          }
        }
      } else {
        Text(
          text = "--:--",
          style = MaterialTheme.typography.displayLarge,
          fontWeight = FontWeight.Light,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
      }
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val snapshot = data.snapshot<TimeSnapshot>() ?: return "Digital clock: no data"
    return formatAccessibilityTime(snapshot, use24Hour = true, showSeconds = false)
  }

  /** Returns a formatted accessibility description for the given snapshot and settings. */
  internal fun accessibilityDescriptionWithSettings(
    data: WidgetData,
    settings: Map<String, Any?>,
  ): String {
    val snapshot = data.snapshot<TimeSnapshot>() ?: return "Digital clock: no data"
    val use24Hour = settings["use24HourFormat"] as? Boolean ?: true
    val showSeconds = settings["showSeconds"] as? Boolean ?: false
    return formatAccessibilityTime(snapshot, use24Hour, showSeconds)
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  private data class TimeDisplay(val mainTime: String, val suffix: String)

  private fun extractTime(
    snapshot: TimeSnapshot,
    settings: ImmutableMap<String, Any>
  ): TimeDisplay {
    val use24Hour = settings["use24HourFormat"] as? Boolean ?: true
    val showSeconds = settings["showSeconds"] as? Boolean ?: false
    val showLeadingZero = settings["showLeadingZero"] as? Boolean ?: true
    val timezoneIdStr = settings["timezoneId"] as? String

    val zoneId = resolveZone(timezoneIdStr, snapshot.zoneId)
    val zonedTime = Instant.ofEpochMilli(snapshot.epochMillis).atZone(zoneId)

    val hour =
      if (use24Hour) zonedTime.hour
      else {
        val h = zonedTime.hour % 12
        if (h == 0) 12 else h
      }
    val minute = zonedTime.minute
    val second = zonedTime.second

    val hourStr = if (showLeadingZero) "%02d".format(hour) else "$hour"
    val mainTime =
      if (showSeconds) {
        "$hourStr:%02d:%02d".format(minute, second)
      } else {
        "$hourStr:%02d".format(minute)
      }

    val suffix =
      if (!use24Hour) {
        if (zonedTime.hour < 12) "AM" else "PM"
      } else {
        ""
      }

    return TimeDisplay(mainTime, suffix)
  }

  companion object {
    internal fun resolveZone(settingsZoneId: String?, snapshotZoneId: String): ZoneId =
      if (!settingsZoneId.isNullOrEmpty()) {
        ZoneId.of(settingsZoneId)
      } else {
        ZoneId.of(snapshotZoneId)
      }

    internal fun formatAccessibilityTime(
      snapshot: TimeSnapshot,
      use24Hour: Boolean,
      showSeconds: Boolean,
    ): String {
      val zoneId = ZoneId.of(snapshot.zoneId)
      val zonedTime = Instant.ofEpochMilli(snapshot.epochMillis).atZone(zoneId)
      val pattern =
        when {
          use24Hour && showSeconds -> "HH:mm:ss"
          use24Hour -> "HH:mm"
          showSeconds -> "h:mm:ss a"
          else -> "h:mm a"
        }
      val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
      return zonedTime.format(formatter)
    }
  }
}
