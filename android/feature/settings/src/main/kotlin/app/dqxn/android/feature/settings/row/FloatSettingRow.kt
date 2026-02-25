package app.dqxn.android.feature.settings.row

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Float setting row with discrete value selection.
 *
 * Uses +/- buttons with step increments. Explicitly NOT a slider (sliders conflict with pager
 * swipe per anti-pattern).
 */
@Composable
internal fun FloatSettingRow(
  definition: SettingDefinition.FloatSetting,
  currentValue: Float,
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

    val step = definition.step ?: 0.1f
    IconButton(
      onClick = {
        val newValue = (currentValue - step).coerceAtLeast(definition.min)
        onValueChanged(definition.key, newValue)
      },
      modifier = Modifier.size(48.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Remove,
        contentDescription = "Decrease",
        tint = theme.primaryTextColor,
      )
    }

    Text(
      text = "%.1f".format(currentValue),
      style = DashboardTypography.itemTitle,
      color = theme.accentColor,
      modifier = Modifier.padding(horizontal = DashboardSpacing.SpaceXS),
    )

    IconButton(
      onClick = {
        val newValue = (currentValue + step).coerceAtMost(definition.max)
        onValueChanged(definition.key, newValue)
      },
      modifier = Modifier.size(48.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = "Increase",
        tint = theme.primaryTextColor,
      )
    }
  }
}
