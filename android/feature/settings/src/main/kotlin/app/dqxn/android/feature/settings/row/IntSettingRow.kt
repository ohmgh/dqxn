package app.dqxn.android.feature.settings.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Integer setting row with preset selection chips.
 *
 * If [SettingDefinition.IntSetting.getEffectivePresets] returns presets, renders as [SelectionChip]
 * row. Otherwise renders as [SettingLabel] with current value display.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntSettingRow(
  definition: SettingDefinition.IntSetting,
  currentValue: Int,
  currentSettings: Map<String, Any?>,
  theme: DashboardThemeDefinition,
  onValueChanged: (String, Any?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val presets = definition.getEffectivePresets(currentSettings)

  Column(modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp)) {
    if (presets != null) {
      SettingLabel(label = definition.label, description = definition.description, theme = theme)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXS),
        verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXS),
        modifier = Modifier.padding(top = DashboardSpacing.SpaceXS),
      ) {
        presets.forEach { preset ->
          SelectionChip(
            text = preset.toString(),
            isSelected = currentValue == preset,
            onClick = { onValueChanged(definition.key, preset) },
            theme = theme,
          )
        }
      }
    } else {
      Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SettingLabel(
          label = definition.label,
          description = definition.description,
          theme = theme,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = currentValue.toString(),
          style = DashboardTypography.itemTitle,
          color = theme.accentColor,
        )
      }
    }
  }
}
