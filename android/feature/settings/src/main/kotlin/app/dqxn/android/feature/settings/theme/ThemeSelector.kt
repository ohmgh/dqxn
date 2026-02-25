package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

/** Preview timeout duration in milliseconds (60 seconds). */
internal const val PREVIEW_TIMEOUT_MS: Long = 60_000L

/** Maximum number of custom themes allowed. */
internal const val MAX_CUSTOM_THEMES: Int = 12

/**
 * Theme browser composable with free-first ordering, preview lifecycle, and clone capability.
 *
 * Key behaviors (from replication advisory section 3):
 * - **Free-first ordering**: Themes sorted as free -> custom -> premium (F4.13).
 * - **Preview lifecycle**: 60s timeout auto-reverts via [LaunchedEffect] keyed on themeId (F4.6).
 * - **Preview-regardless-of-entitlement**: All themes previewable on tap; gate at apply (F4.9).
 * - **Clone built-in to custom**: Long-press on built-in triggers [onCloneToCustom] (F4.12).
 * - **Dual cleanup**: [DisposableEffect] ensures preview is cleared on disposal.
 *
 * Callback-based API -- ThemeSelector does NOT call ThemeCoordinator directly.
 */
@Composable
public fun ThemeSelector(
  allThemes: ImmutableList<DashboardThemeDefinition>,
  previewTheme: DashboardThemeDefinition?,
  customThemeCount: Int,
  entitlementManager: EntitlementManager,
  onPreviewTheme: (DashboardThemeDefinition) -> Unit,
  onApplyTheme: (String) -> Unit,
  onClearPreview: () -> Unit,
  onCloneToCustom: (DashboardThemeDefinition) -> Unit,
  onOpenStudio: (DashboardThemeDefinition) -> Unit,
  onDeleteCustom: (String) -> Unit,
  onShowToast: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current

  // -- Sort themes: free first, then custom, then premium (F4.13) --
  val sortedThemes = remember(allThemes) { sortThemes(allThemes) }

  // -- Preview timeout: 60s auto-revert keyed on themeId (F4.6) --
  LaunchedEffect(previewTheme?.themeId) {
    if (previewTheme != null) {
      delay(PREVIEW_TIMEOUT_MS)
      onClearPreview()
      onShowToast("Preview timed out")
    }
  }

  // -- Entitlement revocation: clear preview if previewing a revoked theme (F4.10) --
  val currentEntitlements by entitlementManager.entitlementChanges.collectAsState(
    initial = entitlementManager.getActiveEntitlements()
  )
  LaunchedEffect(currentEntitlements, previewTheme) {
    if (previewTheme != null) {
      val required = previewTheme.requiredAnyEntitlement
      if (!required.isNullOrEmpty() && required.none { currentEntitlements.contains(it) }) {
        onClearPreview()
        onShowToast("Theme no longer available")
      }
    }
  }

  // -- Dual cleanup: clear preview on disposal (replication advisory section 3) --
  DisposableEffect(Unit) { onDispose { onClearPreview() } }

  OverlayScaffold(
    title = "Themes",
    overlayType = OverlayType.Preview,
    onClose = onClose,
    modifier = modifier.testTag("theme_selector"),
  ) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      contentPadding = PaddingValues(vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      items(sortedThemes, key = { it.themeId }) { themeItem ->
        val isSelected = previewTheme?.themeId == themeItem.themeId
        val isCustom = themeItem.themeId.startsWith("custom_")
        val isGated = !themeItem.requiredAnyEntitlement.isNullOrEmpty()
        val hasAccess =
          !isGated || themeItem.requiredAnyEntitlement!!.any { entitlementManager.hasEntitlement(it) }

        ThemeCard(
          theme = themeItem,
          isSelected = isSelected,
          isGated = isGated,
          isCustom = isCustom,
          hasAccess = hasAccess,
          onTap = {
            // Preview-regardless-of-entitlement (F4.9): all themes previewable
            onPreviewTheme(themeItem)
          },
          onLongPress = {
            if (!isCustom) {
              // Clone built-in to custom (F4.12)
              if (customThemeCount < MAX_CUSTOM_THEMES) {
                onCloneToCustom(themeItem)
              } else {
                onShowToast("Maximum $MAX_CUSTOM_THEMES custom themes reached")
              }
            }
          },
          onApply = {
            if (hasAccess) {
              onApplyTheme(themeItem.themeId)
            } else {
              onShowToast("Upgrade required to apply this theme")
            }
          },
          onEdit = { onOpenStudio(themeItem) },
          onDelete = { onDeleteCustom(themeItem.themeId) },
          accentColor = theme.accentColor,
          textColor = theme.primaryTextColor,
          secondaryTextColor = theme.secondaryTextColor,
        )
      }
    }
  }
}

/**
 * Individual theme card in the grid.
 *
 * Shows lock icon for gated themes. Edit/delete actions for custom themes.
 */
@Composable
private fun ThemeCard(
  theme: DashboardThemeDefinition,
  isSelected: Boolean,
  isGated: Boolean,
  isCustom: Boolean,
  hasAccess: Boolean,
  onTap: () -> Unit,
  onLongPress: () -> Unit,
  onApply: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  accentColor: Color,
  textColor: Color,
  secondaryTextColor: Color,
) {
  val borderColor = if (isSelected) accentColor else Color.Transparent
  val cornerShape = RoundedCornerShape(CardSize.SMALL.cornerRadius)

  Column(
    modifier =
      Modifier.testTag("theme_card_${theme.themeId}")
        .clip(cornerShape)
        .border(2.dp, borderColor, cornerShape)
        .combinedClickable(
          onClick = onTap,
          onLongClick = onLongPress,
          onDoubleClick = onApply,
        )
        .padding(4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Color preview swatch
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(1.5f)
          .clip(cornerShape)
          .testTag("theme_swatch_${theme.themeId}"),
    ) {
      // Lock icon overlay for gated themes
      if (isGated) {
        Icon(
          imageVector = Icons.Filled.Lock,
          contentDescription = "Locked",
          tint = Color.White.copy(alpha = 0.8f),
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(4.dp)
              .size(16.dp)
              .testTag("theme_lock_${theme.themeId}"),
        )
      }
    }

    Text(
      text = theme.displayName,
      style = DashboardTypography.caption,
      color = textColor,
      maxLines = 1,
      modifier = Modifier.testTag("theme_name_${theme.themeId}"),
    )

    // Edit/delete row for custom themes
    if (isCustom) {
      Box(modifier = Modifier.testTag("theme_custom_actions_${theme.themeId}")) {
        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "Edit theme",
            tint = secondaryTextColor,
            modifier = Modifier.size(16.dp),
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.align(Alignment.CenterEnd).size(24.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "Delete theme",
            tint = secondaryTextColor,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

/**
 * Sorts themes into free-first ordering: free -> custom -> premium (F4.13).
 *
 * - Free: no [requiredAnyEntitlement] and not a custom theme.
 * - Custom: [themeId] starts with "custom_".
 * - Premium: has [requiredAnyEntitlement] and not custom.
 */
internal fun sortThemes(
  themes: ImmutableList<DashboardThemeDefinition>
): List<DashboardThemeDefinition> {
  val free = mutableListOf<DashboardThemeDefinition>()
  val custom = mutableListOf<DashboardThemeDefinition>()
  val premium = mutableListOf<DashboardThemeDefinition>()

  for (theme in themes) {
    when {
      theme.themeId.startsWith("custom_") -> custom.add(theme)
      theme.requiredAnyEntitlement.isNullOrEmpty() -> free.add(theme)
      else -> premium.add(theme)
    }
  }

  return free + custom + premium
}
