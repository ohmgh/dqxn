package app.dqxn.android.feature.settings.widget

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
 * Data source selection tab.
 *
 * Shows available providers via [DataProviderRegistry.findByDataType]. Current provider highlighted
 * with accent color. Provider cards show: name, data type, connection status indicator, priority
 * badge. Providers with setup requirements show "Setup Required" badge; tap navigates via
 * [onNavigateToSetup].
 */
@Composable
internal fun DataProviderSettingsContent(
  widgetSpec: WidgetRenderer?,
  dataProviderRegistry: DataProviderRegistry,
  theme: DashboardThemeDefinition,
  onNavigateToSetup: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (widgetSpec == null) {
    EmptyProviderPlaceholder(theme = theme, modifier = modifier)
    return
  }

  val dataTypes =
    remember(widgetSpec) {
      widgetSpec.compatibleSnapshots.map { it.simpleName ?: "Unknown" }
    }

  val allProviders =
    remember(widgetSpec) {
      widgetSpec.compatibleSnapshots
        .flatMap { snapshotType ->
          val dataType = snapshotType.simpleName?.removeSuffix("Snapshot") ?: ""
          dataProviderRegistry.findByDataType(dataType)
        }
        .distinctBy { it.sourceId }
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
    allProviders.forEach { provider ->
      ProviderCard(
        provider = provider,
        theme = theme,
        onNavigateToSetup = onNavigateToSetup,
      )
    }
  }
}

@Composable
private fun ProviderCard(
  provider: DataProvider<*>,
  theme: DashboardThemeDefinition,
  onNavigateToSetup: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasSetup = provider.setupSchema.isNotEmpty()
  val shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius)

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .sizeIn(minHeight = 76.dp)
        .clip(shape)
        .border(1.dp, theme.widgetBorderColor.copy(alpha = 0.3f), shape)
        .clickable {
          if (hasSetup) {
            onNavigateToSetup(provider.sourceId)
          }
        }
        .padding(DashboardSpacing.ItemGap)
        .testTag("provider_card_${provider.sourceId}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
  ) {
    // Connection status dot
    ConnectionStatusDot(isAvailable = provider.isAvailable, theme = theme)

    Column(modifier = Modifier.weight(1f)) {
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
    PriorityBadge(priority = provider.priority, theme = theme)

    // Setup required badge
    if (hasSetup) {
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
        .then(
          Modifier.border(8.dp, color, CircleShape),
        )
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
