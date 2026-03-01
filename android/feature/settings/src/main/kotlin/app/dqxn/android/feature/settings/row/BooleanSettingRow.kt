package app.dqxn.android.feature.settings.row

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Boolean setting row: label + description on the left, Switch on the right.
 *
 * Toggle fires [onValueChanged] with `(definition.key, !currentValue)`.
 */
@Composable
internal fun BooleanSettingRow(
  definition: SettingDefinition.BooleanSetting,
  currentValue: Boolean,
  theme: DashboardThemeDefinition,
  onValueChanged: (String, Any?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingLabel(
      label = definition.label,
      description = definition.description,
      theme = theme,
      modifier = Modifier.weight(1f),
    )
    Switch(
      checked = currentValue,
      onCheckedChange = { onValueChanged(definition.key, !currentValue) },
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = theme.accentColor,
          checkedTrackColor = theme.accentColor.copy(alpha = 0.5f),
          uncheckedThumbColor = theme.secondaryTextColor,
          uncheckedTrackColor = theme.secondaryTextColor.copy(alpha = 0.3f),
          uncheckedBorderColor = theme.secondaryTextColor.copy(alpha = 0.3f),
        ),
    )
  }
}
