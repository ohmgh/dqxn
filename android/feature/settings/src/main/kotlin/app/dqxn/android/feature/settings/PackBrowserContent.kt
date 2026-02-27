package app.dqxn.android.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Pack list accessible from Settings.
 *
 * Shows all packs with: name, widget count, entitlement status. Each pack card navigable to
 * filtered widget list. Uses [OverlayScaffold] with [OverlayType.Hub].
 */
@Composable
public fun PackBrowserContent(
  widgetRegistry: WidgetRegistry,
  entitlementManager: EntitlementManager,
  onSelectPack: (String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val allWidgets = remember(widgetRegistry) { widgetRegistry.getAll() }

  val packInfos: List<PackInfo> =
    remember(allWidgets) {
      allWidgets
        .groupBy { it.typeId.substringBefore(':') }
        .map { (packId, widgets) ->
          PackInfo(
            packId = packId,
            displayName = packId.replaceFirstChar { it.uppercase() },
            widgetCount = widgets.size,
            isFullyAccessible = widgets.all { it.isAccessible(entitlementManager::hasEntitlement) },
          )
        }
        .sortedBy { it.packId }
    }

  OverlayScaffold(
    title = stringResource(R.string.pack_browser_title),
    overlayType = OverlayType.Hub,
    onClose = onDismiss,
    modifier = modifier.fillMaxSize(),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("pack_browser_list"),
      verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
    ) {
      packInfos.forEach { packInfo ->
        PackCard(
          packInfo = packInfo,
          theme = theme,
          onSelect = { onSelectPack(packInfo.packId) },
        )
      }
    }
  }
}

/**
 * Pack card showing name, widget count, and entitlement status.
 *
 * Minimum touch target 76dp (F10.4).
 */
@Composable
private fun PackCard(
  packInfo: PackInfo,
  theme: DashboardThemeDefinition,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius)

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .sizeIn(minHeight = 76.dp)
        .clip(shape)
        .border(1.dp, theme.widgetBorderColor.copy(alpha = 0.3f), shape)
        .clickable(onClick = onSelect)
        .padding(DashboardSpacing.CardInternalPadding)
        .testTag("pack_card_${packInfo.packId}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = packInfo.displayName,
        style = DashboardTypography.itemTitle,
        color = theme.primaryTextColor,
      )
      Text(
        text = stringResource(R.string.pack_browser_widgets_count, packInfo.widgetCount),
        style = DashboardTypography.caption,
        color = theme.secondaryTextColor,
      )
    }

    Text(
      text =
        if (packInfo.isFullyAccessible) stringResource(R.string.pack_browser_accessible)
        else stringResource(R.string.pack_browser_locked),
      style = DashboardTypography.caption,
      color = if (packInfo.isFullyAccessible) theme.successColor else theme.secondaryTextColor,
      modifier = Modifier.testTag("pack_status_${packInfo.packId}"),
    )
  }
}

/** Internal data class for pack summary info. */
private data class PackInfo(
  val packId: String,
  val displayName: String,
  val widgetCount: Int,
  val isFullyAccessible: Boolean,
)
