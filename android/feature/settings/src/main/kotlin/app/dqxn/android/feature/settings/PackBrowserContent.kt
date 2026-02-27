package app.dqxn.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList

/**
 * Pack list accessible from Settings.
 *
 * Shows all packs with: icon, name, description, stats (themes/widgets/providers), entitlement
 * status, and tag chips for each widget/theme/provider. Uses [OverlayScaffold] with
 * [OverlayType.Hub].
 */
@Composable
public fun PackBrowserContent(
  widgetRegistry: WidgetRegistry,
  dataProviderRegistry: DataProviderRegistry,
  allThemes: ImmutableList<DashboardThemeDefinition>,
  entitlementManager: EntitlementManager,
  onSelectPack: (String) -> Unit,
  onDismiss: () -> Unit,
  onSimulateFreeUser: ((Boolean) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val allWidgets = remember(widgetRegistry) { widgetRegistry.getAll() }
  val allProviders = remember(dataProviderRegistry) { dataProviderRegistry.getAll() }

  val packInfos: List<PackInfo> =
    remember(allWidgets, allProviders, allThemes) {
      // Collect all unique pack IDs from widgets, providers, and themes
      val widgetsByPack = allWidgets.groupBy { it.typeId.substringBefore(':') }
      val providersByPack = allProviders.groupBy { it.sourceId.substringBefore(':') }
      val themesByPack = allThemes.filter { it.packId != null }.groupBy { it.packId!! }

      val allPackIds =
        (widgetsByPack.keys + providersByPack.keys + themesByPack.keys)
          .distinct()
          .sortedWith(compareBy<String> { packPriority(it) }.thenBy { it })

      allPackIds.map { packId ->
        val widgets = widgetsByPack[packId].orEmpty()
        val providers = providersByPack[packId].orEmpty()
        val themes = themesByPack[packId].orEmpty()

        val hasEntitlementReqs = widgets.any { !it.requiredAnyEntitlement.isNullOrEmpty() } ||
          providers.any { !it.requiredAnyEntitlement.isNullOrEmpty() } ||
          themes.any { !it.requiredAnyEntitlement.isNullOrEmpty() }

        val allAccessible = widgets.all { it.isAccessible(entitlementManager::hasEntitlement) } &&
          providers.all { it.isAccessible(entitlementManager::hasEntitlement) } &&
          themes.all { it.isAccessible(entitlementManager::hasEntitlement) }

        val status = when {
          !hasEntitlementReqs -> PackStatus.Free
          allAccessible -> PackStatus.Owned
          else -> PackStatus.Available
        }

        PackInfo(
          packId = packId,
          displayName = packId.replaceFirstChar { it.uppercase() },
          description = getPackDescription(packId),
          widgetNames = widgets.map { it.displayName },
          themeNames = themes.map { it.displayName },
          providerNames = providers.map { it.displayName },
          status = status,
        )
      }
    }

  OverlayScaffold(
    title = stringResource(R.string.pack_browser_title),
    overlayType = OverlayType.Hub,
    onBack = onDismiss,
    modifier = modifier,
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

      // Debug: Simulate Free User toggle
      if (onSimulateFreeUser != null) {
        SimulateFreeUserCard(
          isFreeUser = !entitlementManager.hasEntitlement("themes") &&
            !entitlementManager.hasEntitlement("plus"),
          onToggle = onSimulateFreeUser,
          theme = theme,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackCard(
  packInfo: PackInfo,
  theme: DashboardThemeDefinition,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(CardSize.LARGE.cornerRadius)

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onSelect)
        .testTag("pack_card_${packInfo.packId}"),
    shape = shape,
    border = androidx.compose.foundation.BorderStroke(1.dp, theme.widgetBorderColor.copy(alpha = 0.3f)),
    colors = CardDefaults.cardColors(
      containerColor = theme.widgetBorderColor.copy(alpha = 0.15f),
    ),
  ) {
    Column(modifier = Modifier.padding(DashboardSpacing.CardInternalPadding)) {
      // Header row: icon + name + description
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = getPackIcon(packInfo.packId),
          contentDescription = null,
          tint = theme.accentColor,
          modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.size(DashboardSpacing.SpaceS))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = packInfo.displayName,
            style = DashboardTypography.itemTitle,
            color = theme.primaryTextColor,
          )
          Text(
            text = packInfo.description,
            style = DashboardTypography.description,
            color = theme.secondaryTextColor,
          )
        }
      }

      Spacer(modifier = Modifier.height(DashboardSpacing.ItemGap))

      // Stats row: counts + status label
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.Widgets,
          contentDescription = null,
          tint = theme.secondaryTextColor,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.size(DashboardSpacing.SpaceXXS))
        Text(
          text = buildStatsText(packInfo),
          style = DashboardTypography.caption,
          color = theme.secondaryTextColor,
          modifier = Modifier.weight(1f),
        )
        PackStatusLabel(status = packInfo.status, theme = theme)
      }

      // Widget tags
      if (packInfo.widgetNames.isNotEmpty()) {
        Spacer(modifier = Modifier.height(DashboardSpacing.InGroupGap))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.InGroupGap),
          verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS),
        ) {
          packInfo.widgetNames.forEach { name ->
            TagChip(
              text = name,
              backgroundColor = theme.accentColor.copy(alpha = 0.15f),
              textColor = theme.accentColor,
            )
          }
        }
      }

      // Theme tags
      if (packInfo.themeNames.isNotEmpty()) {
        Spacer(modifier = Modifier.height(DashboardSpacing.InGroupGap))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.InGroupGap),
          verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS),
        ) {
          packInfo.themeNames.forEach { name ->
            TagChip(
              text = name,
              backgroundColor = theme.highlightColor.copy(alpha = 0.15f),
              textColor = theme.highlightColor,
            )
          }
        }
      }

      // Provider tags
      if (packInfo.providerNames.isNotEmpty()) {
        Spacer(modifier = Modifier.height(DashboardSpacing.InGroupGap))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.InGroupGap),
          verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS),
        ) {
          packInfo.providerNames.forEach { name ->
            TagChip(
              text = name,
              backgroundColor = theme.secondaryTextColor.copy(alpha = 0.15f),
              textColor = theme.secondaryTextColor,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TagChip(
  text: String,
  backgroundColor: androidx.compose.ui.graphics.Color,
  textColor: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = DashboardTypography.caption,
    color = textColor,
    modifier =
      modifier
        .clip(RoundedCornerShape(6.dp))
        .background(backgroundColor)
        .padding(horizontal = 8.dp, vertical = 4.dp),
  )
}

@Composable
private fun PackStatusLabel(
  status: PackStatus,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  val (text, color) = when (status) {
    PackStatus.Free -> "FREE" to theme.secondaryTextColor
    PackStatus.Owned -> "PURCHASED" to theme.accentColor
    PackStatus.Available -> "GET" to theme.highlightColor
  }

  Text(
    text = text,
    style = DashboardTypography.buttonLabel,
    color = color,
    modifier = modifier.testTag("pack_status_${status.name.lowercase()}"),
  )
}

private fun buildStatsText(packInfo: PackInfo): String {
  val parts = mutableListOf<String>()
  if (packInfo.themeNames.isNotEmpty()) parts.add("${packInfo.themeNames.size} themes")
  if (packInfo.widgetNames.isNotEmpty()) parts.add("${packInfo.widgetNames.size} widgets")
  if (packInfo.providerNames.isNotEmpty()) parts.add("${packInfo.providerNames.size} providers")
  return parts.joinToString(" \u2022 ")
}

private fun getPackIcon(packId: String): ImageVector = when (packId) {
  "essentials" -> Icons.Default.Widgets
  "plus" -> Icons.Default.Star
  "themes" -> Icons.Default.Palette
  "demo" -> Icons.Default.BugReport
  else -> Icons.Default.Dashboard
}

private fun packPriority(packId: String): Int = when (packId) {
  "essentials" -> 0
  "plus" -> 1
  "themes" -> 2
  "demo" -> 3
  else -> 4
}

private fun getPackDescription(packId: String): String = when (packId) {
  "essentials" -> "Core widgets and data providers"
  "plus" -> "Premium widgets and features"
  "themes" -> "Additional theme packs"
  "demo" -> "Demo and development tools"
  else -> "Dashboard pack"
}

@Composable
private fun SimulateFreeUserCard(
  isFreeUser: Boolean,
  onToggle: (Boolean) -> Unit,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(CardSize.LARGE.cornerRadius)

  Card(
    modifier = modifier.fillMaxWidth().testTag("simulate_free_user_card"),
    shape = shape,
    border = androidx.compose.foundation.BorderStroke(1.dp, theme.widgetBorderColor.copy(alpha = 0.3f)),
    colors = CardDefaults.cardColors(
      containerColor = theme.widgetBorderColor.copy(alpha = 0.15f),
    ),
  ) {
    Row(
      modifier = Modifier.padding(DashboardSpacing.CardInternalPadding).fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Default.BugReport,
        contentDescription = null,
        tint = theme.accentColor,
        modifier = Modifier.size(32.dp),
      )
      Spacer(modifier = Modifier.size(DashboardSpacing.SpaceS))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Simulate Free User",
          style = DashboardTypography.itemTitle,
          color = theme.primaryTextColor,
        )
        Text(
          text = "Revokes themes, plus entitlements",
          style = DashboardTypography.description,
          color = theme.secondaryTextColor,
        )
      }
      Switch(
        checked = isFreeUser,
        onCheckedChange = onToggle,
        colors = SwitchDefaults.colors(
          checkedThumbColor = theme.accentColor,
          checkedTrackColor = theme.accentColor.copy(alpha = 0.3f),
          uncheckedThumbColor = theme.secondaryTextColor,
          uncheckedTrackColor = theme.secondaryTextColor.copy(alpha = 0.1f),
        ),
      )
    }
  }
}

private data class PackInfo(
  val packId: String,
  val displayName: String,
  val description: String,
  val widgetNames: List<String>,
  val themeNames: List<String>,
  val providerNames: List<String>,
  val status: PackStatus,
)

private enum class PackStatus { Free, Owned, Available }
