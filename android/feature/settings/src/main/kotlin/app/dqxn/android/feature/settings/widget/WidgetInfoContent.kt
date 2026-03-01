package app.dqxn.android.feature.settings.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
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
 * Widget info tab with pack card and status section.
 *
 * Layout (old codebase pattern):
 * - Description text (if present)
 * - Pack card (bordered, accent icon, tappable → pack browser)
 * - "STATUS" section header
 * - "All Systems Go" card (accent-bordered) or per-issue cards
 * - NF-D1 speed disclaimer (speed widgets only)
 */
@Composable
internal fun WidgetInfoContent(
  widgetTypeId: String,
  widgetSpec: WidgetRenderer?,
  theme: DashboardThemeDefinition,
  onNavigateToPackBrowser: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val packId = widgetTypeId.substringBefore(':')

  LazyColumn(
    modifier =
      modifier
        .fillMaxSize()
        .testTag("widget_info_content"),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(
      vertical = DashboardSpacing.ItemGap,
    ),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
  ) {
    // Description
    widgetSpec
      ?.description
      ?.takeIf { it.isNotBlank() }
      ?.let { description ->
        item(key = "description") {
          Text(
            text = description,
            style = DashboardTypography.description,
            color = theme.secondaryTextColor,
            modifier = Modifier.fillMaxWidth().testTag("widget_info_description"),
          )
          Spacer(modifier = Modifier.height(DashboardSpacing.SpaceXS))
        }
      }

    // Pack card
    item(key = "pack_card") {
      PackCard(
        packId = packId,
        theme = theme,
        onClick = { onNavigateToPackBrowser(packId) },
      )
    }

    // Section gap before STATUS
    item(key = "status_spacer") {
      Spacer(modifier = Modifier.height(DashboardSpacing.SpaceL))
    }

    // STATUS header
    item(key = "status_header") {
      Text(
        text = stringResource(R.string.widget_info_status_header),
        style = DashboardTypography.sectionHeader,
        color = theme.secondaryTextColor,
      )
    }

    // TODO: Wire actual widget issues from WidgetStatusCache. For now, show "All Systems Go".
    item(key = "status_card") {
      AllSystemsGoCard(theme = theme)
    }

    // NF-D1 speed disclaimer for speed-related widgets
    if (widgetTypeId in SPEED_TYPE_IDS) {
      item(key = "speed_disclaimer") {
        Text(
          text = stringResource(R.string.widget_info_speed_disclaimer),
          style = DashboardTypography.caption,
          color = theme.warningColor,
          modifier = Modifier.fillMaxWidth().testTag("widget_info_speed_disclaimer"),
        )
      }
    }

    item(key = "bottom_spacer") {
      Spacer(
        modifier =
          Modifier
            .height(24.dp)
            .then(Modifier),
      )
    }
  }
}

/**
 * Pack card with icon, pack name, and "Tap for pack info" subtitle.
 *
 * Matches old codebase: `widgetBorderColor @ 0.15f` background, `widgetBorderColor @ 0.3f` border,
 * `CardSize.MEDIUM` corners, accent-colored icon.
 */
@Composable
private fun PackCard(
  packId: String,
  theme: DashboardThemeDefinition,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius)

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(shape)
        .border(1.dp, theme.widgetBorderColor.copy(alpha = 0.3f), shape)
        .background(theme.widgetBorderColor.copy(alpha = 0.15f))
        .clickable(onClick = onClick)
        .padding(DashboardSpacing.CardInternalPadding)
        .testTag("widget_info_pack_card"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.IconTextGap),
  ) {
    Icon(
      imageVector = Icons.Default.Inventory2,
      contentDescription = null,
      tint = theme.accentColor,
      modifier = Modifier.size(32.dp),
    )
    Column {
      Text(
        text = packId.replaceFirstChar { it.uppercase() },
        style = DashboardTypography.itemTitle,
        color = theme.primaryTextColor,
      )
      Text(
        text = stringResource(R.string.widget_info_tap_pack_info),
        style = DashboardTypography.description,
        color = theme.secondaryTextColor.copy(alpha = TextEmphasis.Medium),
      )
    }
  }
}

/**
 * "All Systems Go" status card with checkmark icon.
 *
 * Matches old codebase: `accentColor @ 0.1f` background, `accentColor @ 0.3f` border,
 * 28dp check circle icon.
 */
@Composable
private fun AllSystemsGoCard(
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius)

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(shape)
        .border(1.dp, theme.accentColor.copy(alpha = 0.3f), shape)
        .background(theme.accentColor.copy(alpha = 0.1f))
        .padding(DashboardSpacing.CardInternalPadding)
        .testTag("widget_info_all_systems_go"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.IconTextGap),
  ) {
    Icon(
      imageVector = Icons.Default.CheckCircle,
      contentDescription = null,
      tint = theme.accentColor,
      modifier = Modifier.size(28.dp),
    )
    Column {
      Text(
        text = stringResource(R.string.widget_info_all_systems_go),
        style = DashboardTypography.itemTitle,
        color = theme.primaryTextColor,
      )
      Text(
        text = stringResource(R.string.widget_info_running_smoothly),
        style = DashboardTypography.description,
        color = theme.secondaryTextColor.copy(alpha = TextEmphasis.Medium),
      )
    }
  }
}
