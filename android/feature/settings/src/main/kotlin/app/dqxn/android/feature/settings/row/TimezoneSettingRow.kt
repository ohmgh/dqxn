package app.dqxn.android.feature.settings.row

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import java.util.TimeZone

/**
 * Timezone setting row that displays city name + GMT offset.
 *
 * Three display states:
 * 1. `null` -> system timezone with "System Default" subtitle.
 * 2. `"SYSTEM"` -> system timezone display without subtitle (avoids duplicate per advisory).
 * 3. else -> parse zone ID, extract city from last "/" segment, format GMT offset.
 *
 * Taps fire [SettingNavigation.ToTimezonePicker].
 */
@Composable
internal fun TimezoneSettingRow(
  definition: SettingDefinition.TimezoneSetting,
  currentValue: String?,
  theme: DashboardThemeDefinition,
  onNavigate: ((SettingNavigation) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val (displayName, subtitle) = resolveTimezoneDisplay(currentValue)

  Row(
    modifier =
      modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp).clickable {
        onNavigate?.invoke(
          SettingNavigation.ToTimezonePicker(
            settingKey = definition.key,
            currentValue = currentValue,
          )
        )
      },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingLabel(
      label = definition.label,
      description = definition.description,
      theme = theme,
      modifier = Modifier.weight(1f),
    )

    Column(
      horizontalAlignment = Alignment.End,
      modifier = Modifier.padding(end = 4.dp),
    ) {
      Text(
        text = displayName,
        style = DashboardTypography.description,
        color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = DashboardTypography.caption,
          color = theme.secondaryTextColor.copy(alpha = TextEmphasis.Medium),
          modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
        )
      }
    }

    Icon(
      imageVector = Icons.Filled.ChevronRight,
      contentDescription = "Select timezone",
      tint = theme.secondaryTextColor,
    )
  }
}

/**
 * Resolves timezone display text.
 *
 * @return Pair of (display name, optional subtitle). Subtitle is non-null only for the `null`
 *   (system default) state.
 */
private fun resolveTimezoneDisplay(value: String?): Pair<String, String?> {
  val systemZone = TimeZone.getDefault()
  val systemCity = extractCityName(systemZone.id)
  val systemOffset = formatGmtOffset(systemZone.id)

  return when {
    // State 1: null -> system timezone with "System Default" subtitle
    value == null -> "$systemCity ($systemOffset)" to "System Default"

    // State 2: "SYSTEM" -> system timezone without subtitle
    value == "SYSTEM" -> "$systemCity ($systemOffset)" to null

    // State 3: specific zone ID
    else -> {
      val city = extractCityName(value)
      val offset = formatGmtOffset(value)
      "$city ($offset)" to null
    }
  }
}

/** Extracts the city name from a zone ID (e.g., "America/New_York" -> "New York"). */
private fun extractCityName(zoneId: String): String {
  val lastSegment = zoneId.substringAfterLast('/')
  return lastSegment.replace('_', ' ')
}
