package app.dqxn.android.feature.settings.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.feature.settings.R
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/** Speed-related widget typeId prefixes for NF-D1 speed disclaimer. */
private val SPEED_TYPE_IDS =
  setOf(
    "essentials:speedometer",
    "essentials:speed-limit-circle",
    "essentials:speed-limit-rect",
  )

/**
 * Widget info + issues tab.
 *
 * Displays: widget type name, pack name, description, compatible data types list. For speed-related
 * widgets, includes NF-D1 speed disclaimer from string resource. Issues section from
 * [WidgetStatusCache] with resolution actions.
 */
@Composable
internal fun WidgetInfoContent(
  widgetTypeId: String,
  widgetSpec: WidgetRenderer?,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(vertical = DashboardSpacing.ItemGap)
        .testTag("widget_info_content"),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
  ) {
    // Widget type name
    InfoRow(
      label = stringResource(R.string.widget_info_type),
      value = widgetSpec?.displayName ?: widgetTypeId,
      theme = theme,
    )

    // Pack name
    val packId = widgetTypeId.substringBefore(':')
    InfoRow(
      label = stringResource(R.string.widget_info_pack),
      value = packId.replaceFirstChar { it.uppercase() },
      theme = theme,
    )

    // Description
    widgetSpec
      ?.description
      ?.takeIf { it.isNotBlank() }
      ?.let { description ->
        Text(
          text = description,
          style = DashboardTypography.description,
          color = theme.secondaryTextColor,
          modifier = Modifier.fillMaxWidth().testTag("widget_info_description"),
        )
      }

    // Compatible data types
    widgetSpec
      ?.compatibleSnapshots
      ?.takeIf { it.isNotEmpty() }
      ?.let { snapshots ->
        HorizontalDivider(
          color = theme.widgetBorderColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_DISABLED),
        )
        Text(
          text = stringResource(R.string.widget_info_compatible_data),
          style = DashboardTypography.itemTitle,
          color = theme.primaryTextColor,
        )
        snapshots.forEach { snapshotType ->
          val dataTypeName = snapshotType.simpleName?.removeSuffix("Snapshot") ?: "Unknown"
          Text(
            text = dataTypeName,
            style = DashboardTypography.description,
            color = theme.secondaryTextColor,
            modifier = Modifier.testTag("widget_info_data_type_$dataTypeName"),
          )
        }
      }

    // NF-D1 speed disclaimer for speed-related widgets
    if (widgetTypeId in SPEED_TYPE_IDS) {
      HorizontalDivider(
        color = theme.widgetBorderColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_DISABLED),
      )
      Text(
        text = stringResource(R.string.widget_info_speed_disclaimer),
        style = DashboardTypography.caption,
        color = theme.warningColor,
        modifier = Modifier.fillMaxWidth().testTag("widget_info_speed_disclaimer"),
      )
    }
  }
}

@Composable
private fun InfoRow(
  label: String,
  value: String,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = DashboardTypography.caption,
      color = theme.secondaryTextColor,
    )
    Text(
      text = value,
      style = DashboardTypography.description,
      color = theme.primaryTextColor,
    )
  }
}
