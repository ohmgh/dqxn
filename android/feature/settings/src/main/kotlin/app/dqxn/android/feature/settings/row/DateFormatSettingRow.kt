package app.dqxn.android.feature.settings.row

import androidx.compose.foundation.clickable
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
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.sdk.contracts.settings.DateFormatOption
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Date format setting row that shows a live date preview formatted with the current
 * [DateFormatOption].
 *
 * Taps fire [SettingNavigation.ToDateFormatPicker] with the current value. Uses `java.time` APIs.
 */
@Composable
internal fun DateFormatSettingRow(
  definition: SettingDefinition.DateFormatSetting,
  currentValue: DateFormatOption,
  theme: DashboardThemeDefinition,
  onNavigate: ((SettingNavigation) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val formattedDate = formatCurrentDate(currentValue)

  Row(
    modifier =
      modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp).clickable {
        onNavigate?.invoke(
          SettingNavigation.ToDateFormatPicker(
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
    Text(
      text = formattedDate,
      style = DashboardTypography.description,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      modifier = Modifier.padding(end = 4.dp),
    )
    Icon(
      imageVector = Icons.Filled.ChevronRight,
      contentDescription = "Select date format",
      tint = theme.secondaryTextColor,
    )
  }
}

/** Formats today's date using the given [DateFormatOption] pattern via [DateTimeFormatter]. */
private fun formatCurrentDate(option: DateFormatOption): String =
  try {
    LocalDate.now().format(DateTimeFormatter.ofPattern(option.pattern))
  } catch (_: Exception) {
    option.pattern
  }
