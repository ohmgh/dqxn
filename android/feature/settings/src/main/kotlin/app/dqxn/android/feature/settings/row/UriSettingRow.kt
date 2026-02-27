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
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Generic URI display row with "Select" navigation action.
 *
 * Shows current URI or "Not set". Taps fire [onNavigate] for the URI sub-picker. This is the
 * catch-all type for URI-based settings.
 */
@Composable
internal fun UriSettingRow(
  definition: SettingDefinition.UriSetting,
  currentValue: String?,
  theme: DashboardThemeDefinition,
  onNavigate: ((SettingNavigation) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 76.dp)
        .clickable { /* onNavigate for URI sub-picker -- no dedicated SettingNavigation event yet */},
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingLabel(
      label = definition.label,
      description = definition.description,
      theme = theme,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = currentValue ?: "Not set",
      style = DashboardTypography.description,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      modifier = Modifier.padding(end = 4.dp),
    )
    Icon(
      imageVector = Icons.Filled.ChevronRight,
      contentDescription = "Select",
      tint = theme.secondaryTextColor,
    )
  }
}
