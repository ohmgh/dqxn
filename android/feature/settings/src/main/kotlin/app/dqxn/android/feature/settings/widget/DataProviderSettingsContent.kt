package app.dqxn.android.feature.settings.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.feature.settings.R
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Provider selection state for visual differentiation.
 */
private enum class ProviderSelectionState {
  /** Provider requires setup before it can be used. */
  DISABLED,

  /** Provider is currently active for this widget. */
  SELECTED,

  /** Provider is available but not currently active. */
  UNSELECTED,
}

/**
 * Data source selection tab with three-state provider cards.
 *
 * Shows available providers via [DataProviderRegistry.getAll]. Visual states per old codebase:
 * - Disabled: `secondary @ 0.03f` bg, `secondary @ 0.1f` border, content alpha 0.4
 * - Selected: `accent @ 0.15f` bg, `accent` 2dp border
 * - Unselected: `secondary @ 0.05f` bg, `secondary @ 0.2f` 1dp border
 *
 * Provider cards show: connection status dot, name, data type, priority badge. Providers with
 * setup requirements show "Setup Required" badge; tap navigates via [onNavigateToSetup].
 */
@Composable
internal fun DataProviderSettingsContent(
  widgetSpec: WidgetRenderer?,
  dataProviderRegistry: DataProviderRegistry,
  theme: DashboardThemeDefinition,
  onNavigateToSetup: (String) -> Unit,
  selectedSourceIds: Set<String> = emptySet(),
  onProviderSelected: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  if (widgetSpec == null) {
    EmptyProviderPlaceholder(theme = theme, modifier = modifier)
    return
  }

  val allProviders =
    remember(widgetSpec) {
      val compatible = widgetSpec.compatibleSnapshots
      dataProviderRegistry.getAll()
        .filter { it.snapshotType in compatible }
        .sortedBy { it.priority.ordinal }
    }

  if (allProviders.isEmpty()) {
    EmptyProviderPlaceholder(theme = theme, modifier = modifier)
    return
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(vertical = DashboardSpacing.ItemGap)
        .testTag("data_provider_settings_content"),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
  ) {
    Text(
      text = stringResource(R.string.widget_info_select_data_source),
      style = DashboardTypography.description,
      color = theme.secondaryTextColor,
    )

    allProviders.forEach { provider ->
      val hasSetup = provider.setupSchema.isNotEmpty()
      val requiresSetup = hasSetup && !provider.isAvailable
      val isSelected = provider.sourceId in selectedSourceIds

      val state = when {
        requiresSetup -> ProviderSelectionState.DISABLED
        isSelected -> ProviderSelectionState.SELECTED
        else -> ProviderSelectionState.UNSELECTED
      }

      ProviderCard(
        provider = provider,
        state = state,
        theme = theme,
        onClick = {
          if (requiresSetup) {
            onNavigateToSetup(provider.sourceId)
          } else {
            onProviderSelected?.invoke(provider.sourceId)
          }
        },
      )
    }
  }
}

@Composable
private fun ProviderCard(
  provider: DataProvider<*>,
  state: ProviderSelectionState,
  theme: DashboardThemeDefinition,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius)

  val backgroundColor = when (state) {
    ProviderSelectionState.DISABLED -> theme.secondaryTextColor.copy(alpha = 0.03f)
    ProviderSelectionState.SELECTED -> theme.accentColor.copy(alpha = 0.15f)
    ProviderSelectionState.UNSELECTED -> theme.secondaryTextColor.copy(alpha = 0.05f)
  }

  val borderColor = when (state) {
    ProviderSelectionState.DISABLED -> theme.secondaryTextColor.copy(alpha = 0.1f)
    ProviderSelectionState.SELECTED -> theme.accentColor
    ProviderSelectionState.UNSELECTED -> theme.secondaryTextColor.copy(alpha = 0.2f)
  }

  val borderWidth = when (state) {
    ProviderSelectionState.SELECTED -> 2.dp
    else -> 1.dp
  }

  val contentAlpha = when (state) {
    ProviderSelectionState.DISABLED -> 0.4f
    else -> 1.0f
  }

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .sizeIn(minHeight = 76.dp)
        .clip(shape)
        .background(backgroundColor, shape)
        .border(borderWidth, borderColor, shape)
        .clickable(onClick = onClick)
        .padding(DashboardSpacing.CardInternalPadding)
        .testTag("provider_card_${provider.sourceId}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
  ) {
    // Connection status dot
    ConnectionStatusDot(isAvailable = provider.isAvailable, theme = theme)

    Column(
      modifier = Modifier.weight(1f).alpha(contentAlpha),
    ) {
      Text(
        text = provider.displayName,
        style = DashboardTypography.itemTitle,
        color = theme.primaryTextColor,
      )
      Text(
        text = provider.dataType,
        style = DashboardTypography.caption,
        color = theme.secondaryTextColor,
      )
    }

    // Priority badge
    PriorityBadge(
      priority = provider.priority,
      theme = theme,
      modifier = Modifier.alpha(contentAlpha),
    )

    // Setup required badge
    if (provider.setupSchema.isNotEmpty() && !provider.isAvailable) {
      Text(
        text = stringResource(R.string.provider_setup_required),
        style = DashboardTypography.caption,
        color = theme.warningColor,
        modifier = Modifier.testTag("provider_setup_badge_${provider.sourceId}"),
      )
    }
  }
}

@Composable
private fun ConnectionStatusDot(
  isAvailable: Boolean,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  val color =
    if (isAvailable) theme.successColor
    else theme.secondaryTextColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_LOW)
  Box(
    modifier =
      modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color)
        .testTag(if (isAvailable) "status_connected" else "status_disconnected"),
  )
}

@Composable
private fun PriorityBadge(
  priority: ProviderPriority,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  val label =
    when (priority) {
      ProviderPriority.HARDWARE -> "HW"
      ProviderPriority.DEVICE_SENSOR -> "Sensor"
      ProviderPriority.NETWORK -> "Net"
      ProviderPriority.SIMULATED -> "Sim"
    }
  Text(
    text = label,
    style = DashboardTypography.caption,
    color = theme.accentColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_MEDIUM),
    modifier = modifier.testTag("priority_badge_${priority.name}"),
  )
}

@Composable
private fun EmptyProviderPlaceholder(
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize().testTag("data_provider_settings_empty"),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = stringResource(R.string.provider_no_providers),
      style = DashboardTypography.description,
      color = theme.secondaryTextColor,
    )
  }
}
