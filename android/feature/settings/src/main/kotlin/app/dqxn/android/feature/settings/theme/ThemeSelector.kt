package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** Maximum number of custom themes allowed. */
internal const val MAX_CUSTOM_THEMES: Int = 12

/**
 * Theme browser composable with 2-page HorizontalPager, free-first ordering, preview lifecycle,
 * and clone capability.
 *
 * Key behaviors (from replication advisory section 3):
 * - **3-column grid**: [GridCells.Fixed] with 3 columns, 4 rows visible.
 * - **2-page pager**: Page 0 = built-in themes (Native), Page 1 = custom themes.
 * - **Gradient backgrounds**: Theme cards show [DashboardThemeDefinition.backgroundBrush].
 * - **Color-dot swatches**: 4 x 8dp circles (primaryTextColor, secondaryTextColor, accentColor,
 *   highlightColor).
 * - **Star icon**: Premium themes show [Icons.Filled.Star] overlay (not lock).
 * - **Selection border**: Uses [DashboardThemeDefinition.highlightColor] (not accentColor).
 * - **Aspect ratio 2f**: Theme card swatch uses 2:1 aspect ratio.
 * - **Free-first ordering**: Themes sorted as free -> custom -> premium (F4.13).
 * - **Preview-regardless-of-entitlement**: All themes previewable on tap; gate at apply (F4.9).
 * - **Clone built-in to custom**: Long-press on built-in triggers [onCloneToCustom] (F4.12).
 * - **Dual cleanup**: [DisposableEffect] ensures preview is cleared on disposal.
 * - **isDark filtering**: Only themes matching [isDark] parameter are displayed.
 *
 * Callback-based API -- ThemeSelector does NOT call ThemeCoordinator directly.
 */
@Composable
public fun ThemeSelector(
  allThemes: ImmutableList<DashboardThemeDefinition>,
  isDark: Boolean,
  previewTheme: DashboardThemeDefinition?,
  customThemeCount: Int,
  entitlementManager: EntitlementManager,
  onPreviewTheme: (DashboardThemeDefinition) -> Unit,
  onApplyTheme: (String) -> Unit,
  onClearPreview: () -> Unit,
  onCloneToCustom: (DashboardThemeDefinition) -> Unit,
  onOpenStudio: (DashboardThemeDefinition) -> Unit,
  onDeleteCustom: (String) -> Unit,
  onCreateNewTheme: () -> Unit,
  onShowToast: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current

  // -- Filter by isDark, then sort: free first, then custom, then premium (F4.13) --
  val filteredThemes = remember(allThemes, isDark) {
    allThemes.filter { it.isDark == isDark }
  }
  val sortedThemes = remember(filteredThemes) { sortThemes(filteredThemes.toImmutableList()) }

  // -- Split into built-in vs custom for 2-page pager --
  val builtInThemes = remember(sortedThemes) {
    sortedThemes.filter { !it.themeId.startsWith("custom_") }
  }
  val customThemes = remember(sortedThemes) {
    sortedThemes.filter { it.themeId.startsWith("custom_") }
  }

  // -- Pager state and coroutine scope --
  val pagerState = rememberPagerState(initialPage = 0) { 2 }
  val scope = rememberCoroutineScope()

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
    title = if (isDark) "Dark Themes" else "Light Themes",
    overlayType = OverlayType.Preview,
    onClose = onClose,
    modifier = modifier.testTag("theme_selector"),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // -- Title bar page icons: Palette (page 0) + Add (page 1) --
      ThemePageIcons(
        currentPage = pagerState.currentPage,
        onNavigateToPage = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
        accentColor = theme.accentColor,
        inactiveColor = theme.secondaryTextColor,
      )

      // -- 2-page HorizontalPager --
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().testTag("theme_pager"),
      ) { page ->
        when (page) {
          0 -> ThemeGrid(
            themes = builtInThemes,
            previewTheme = previewTheme,
            entitlementManager = entitlementManager,
            customThemeCount = customThemeCount,
            onTap = onPreviewTheme,
            onLongPress = { themeItem ->
              if (customThemeCount < MAX_CUSTOM_THEMES) onCloneToCustom(themeItem)
              else onShowToast("Maximum $MAX_CUSTOM_THEMES custom themes reached")
            },
            onApply = { themeItem, hasAccess ->
              if (hasAccess) onApplyTheme(themeItem.themeId)
              else onShowToast("Upgrade required to apply this theme")
            },
            accentColor = theme.accentColor,
            textColor = theme.primaryTextColor,
            highlightColor = theme.highlightColor,
            modifier = Modifier.testTag("theme_page_builtin"),
          )
          1 -> ThemeGrid(
            themes = customThemes,
            previewTheme = previewTheme,
            entitlementManager = entitlementManager,
            customThemeCount = customThemeCount,
            onTap = onPreviewTheme,
            onLongPress = { /* no clone for custom themes */ },
            onApply = { themeItem, _ -> onApplyTheme(themeItem.themeId) },
            accentColor = theme.accentColor,
            textColor = theme.primaryTextColor,
            highlightColor = theme.highlightColor,
            isCustomPage = true,
            onEdit = onOpenStudio,
            onDelete = { themeItem -> onDeleteCustom(themeItem.themeId) },
            onCreateNew = onCreateNewTheme,
            modifier = Modifier.testTag("theme_page_custom"),
          )
        }
      }
    }
  }
}

/**
 * Title bar page indicator icons: Palette (page 0) + Add (page 1).
 *
 * Active page icon uses [accentColor]; inactive uses [inactiveColor].
 */
@Composable
private fun ThemePageIcons(
  currentPage: Int,
  onNavigateToPage: (Int) -> Unit,
  accentColor: Color,
  inactiveColor: Color,
) {
  Row(
    modifier = Modifier.fillMaxWidth().testTag("theme_page_icons"),
    horizontalArrangement = Arrangement.Center,
  ) {
    IconButton(onClick = { onNavigateToPage(0) }) {
      Icon(
        imageVector = Icons.Filled.Palette,
        contentDescription = "Built-in themes",
        tint = if (currentPage == 0) accentColor else inactiveColor,
        modifier = Modifier.testTag("theme_page_icon_builtin"),
      )
    }
    IconButton(onClick = { onNavigateToPage(1) }) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = "Custom themes",
        tint = if (currentPage == 1) accentColor else inactiveColor,
        modifier = Modifier.testTag("theme_page_icon_custom"),
      )
    }
  }
}

/**
 * Shared grid composable for both built-in and custom theme pages.
 *
 * 3 columns with 4 rows visible. Optional create button on custom page.
 */
@Composable
private fun ThemeGrid(
  themes: List<DashboardThemeDefinition>,
  previewTheme: DashboardThemeDefinition?,
  entitlementManager: EntitlementManager,
  customThemeCount: Int,
  onTap: (DashboardThemeDefinition) -> Unit,
  onLongPress: (DashboardThemeDefinition) -> Unit,
  onApply: (DashboardThemeDefinition, Boolean) -> Unit,
  accentColor: Color,
  textColor: Color,
  highlightColor: Color,
  modifier: Modifier = Modifier,
  isCustomPage: Boolean = false,
  onEdit: ((DashboardThemeDefinition) -> Unit)? = null,
  onDelete: ((DashboardThemeDefinition) -> Unit)? = null,
  onCreateNew: (() -> Unit)? = null,
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    contentPadding = PaddingValues(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.fillMaxSize(),
  ) {
    items(themes, key = { it.themeId }) { themeItem ->
      val isSelected = previewTheme?.themeId == themeItem.themeId
      val isGated = !themeItem.requiredAnyEntitlement.isNullOrEmpty()
      val hasAccess =
        !isGated || themeItem.requiredAnyEntitlement!!.any { entitlementManager.hasEntitlement(it) }

      ThemeCard(
        theme = themeItem,
        isSelected = isSelected,
        isGated = isGated,
        isCustom = isCustomPage,
        hasAccess = hasAccess,
        onTap = { onTap(themeItem) },
        onLongPress = { onLongPress(themeItem) },
        onApply = { onApply(themeItem, hasAccess) },
        onEdit = if (isCustomPage && onEdit != null) { { onEdit(themeItem) } } else null,
        onDelete = if (isCustomPage && onDelete != null) { { onDelete(themeItem) } } else null,
        textColor = textColor,
        highlightColor = highlightColor,
      )
    }

    if (isCustomPage && onCreateNew != null) {
      item {
        CreateThemeButton(accentColor = accentColor, onClick = onCreateNew)
      }
    }
  }
}

/**
 * Individual theme card in the grid.
 *
 * Shows gradient background via [DashboardThemeDefinition.backgroundBrush], 4 color-dot swatches,
 * and star icon overlay for gated themes. Selection border uses [highlightColor].
 * Edit/delete actions for custom themes.
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
  onEdit: (() -> Unit)?,
  onDelete: (() -> Unit)?,
  textColor: Color,
  highlightColor: Color,
) {
  val borderColor = if (isSelected) highlightColor else Color.Transparent
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
    // Gradient background swatch
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(2f)
          .clip(cornerShape)
          .background(theme.backgroundBrush)
          .testTag("theme_swatch_${theme.themeId}"),
    ) {
      // Star icon overlay for premium themes (NOT lock)
      if (isGated) {
        Icon(
          imageVector = Icons.Filled.Star,
          contentDescription = "Premium",
          tint = Color.White.copy(alpha = 0.8f),
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(4.dp)
              .size(14.dp)
              .testTag("theme_star_${theme.themeId}"),
        )
      }
    }

    // Theme name
    Text(
      text = theme.displayName,
      style = DashboardTypography.caption,
      color = textColor,
      maxLines = 1,
      modifier = Modifier.testTag("theme_name_${theme.themeId}"),
    )

    // 4 color-dot swatches (8dp circles)
    Row(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.padding(top = 2.dp).testTag("theme_dots_${theme.themeId}"),
    ) {
      ColorDot(color = theme.primaryTextColor)
      ColorDot(color = theme.secondaryTextColor)
      ColorDot(color = theme.accentColor)
      ColorDot(color = theme.highlightColor)
    }

    // Edit/delete row for custom themes
    if (isCustom && onEdit != null && onDelete != null) {
      Row(
        modifier = Modifier.testTag("theme_custom_actions_${theme.themeId}"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "Edit",
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
          )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "Delete",
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

/** 8dp colored circle for theme color swatch display. */
@Composable
private fun ColorDot(color: Color, modifier: Modifier = Modifier) {
  Box(modifier = modifier.size(8.dp).clip(CircleShape).background(color))
}

/** Create new theme button for the custom themes page. */
@Composable
private fun CreateThemeButton(
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cornerShape = RoundedCornerShape(CardSize.SMALL.cornerRadius)
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .aspectRatio(2f)
        .clip(cornerShape)
        .border(2.dp, accentColor.copy(alpha = 0.3f), cornerShape)
        .clickable(onClick = onClick)
        .testTag("create_theme_button"),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.Add,
      contentDescription = "Create theme",
      tint = accentColor,
      modifier = Modifier.size(32.dp),
    )
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
