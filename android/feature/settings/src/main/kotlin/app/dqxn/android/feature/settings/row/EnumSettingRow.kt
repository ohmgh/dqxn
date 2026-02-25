package app.dqxn.android.feature.settings.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Enum setting row with 3 render modes:
 *
 * 1. Preview cards grid (if `optionPreviews` were non-null -- not yet available in contracts).
 * 2. Chips via [FlowRow] (options <= 10).
 * 3. Dropdown via [ExposedDropdownMenuBox] (options > 10).
 *
 * Comparison uses `value == option || value?.toString() == option.name` per Pitfall 3
 * (type-prefixed serialization).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun <E : Enum<E>> EnumSettingRow(
  definition: SettingDefinition.EnumSetting<E>,
  currentValue: Any?,
  theme: DashboardThemeDefinition,
  onValueChanged: (String, Any?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
  ) {
    SettingLabel(
      label = definition.label,
      description = definition.description,
      theme = theme,
    )

    if (definition.options.size <= 10) {
      // Chip mode
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXS),
        verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXS),
        modifier = Modifier.padding(top = DashboardSpacing.SpaceXS),
      ) {
        definition.options.forEach { option ->
          val label = definition.optionLabels?.get(option) ?: option.name
          val isSelected =
            currentValue == option || currentValue?.toString() == option.name
          SelectionChip(
            text = label,
            isSelected = isSelected,
            onClick = { onValueChanged(definition.key, option) },
            theme = theme,
          )
        }
      }
    } else {
      // Dropdown mode
      var expanded by remember { mutableStateOf(false) }
      val selectedLabel =
        definition.options.firstOrNull { option ->
          currentValue == option || currentValue?.toString() == option.name
        }?.let { definition.optionLabels?.get(it) ?: it.name } ?: ""

      ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(top = DashboardSpacing.SpaceXS),
      ) {
        OutlinedTextField(
          value = selectedLabel,
          onValueChange = {},
          readOnly = true,
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedTextColor = theme.primaryTextColor,
              unfocusedTextColor = theme.primaryTextColor,
              focusedBorderColor = theme.accentColor,
              unfocusedBorderColor = theme.widgetBorderColor.copy(alpha = 0.3f),
            ),
          textStyle = DashboardTypography.itemTitle,
          modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
        ) {
          definition.options.forEach { option ->
            val label = definition.optionLabels?.get(option) ?: option.name
            DropdownMenuItem(
              text = {
                Text(
                  text = label,
                  style = DashboardTypography.itemTitle,
                  color = theme.primaryTextColor,
                )
              },
              onClick = {
                onValueChanged(definition.key, option)
                expanded = false
              },
            )
          }
        }
      }
    }
  }
}
