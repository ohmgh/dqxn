package app.dqxn.android.feature.settings.widget

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.data.style.WidgetStyleStore
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.coroutines.launch

/**
 * 3-page widget settings sheet with titlebar-integrated pager icons.
 *
 * Pages:
 * - **Settings** (page 0): Feature + style settings via [SettingsPageContent].
 * - **Data Source** (page 1): Provider selection via [DataProviderSettingsContent].
 * - **Info** (page 2): Widget info, issues, disclaimers via [WidgetInfoContent].
 *
 * Navigation: Icon buttons (Settings, Extension, Info) in the title bar's actions slot.
 * Active icon = `accentColor`, inactive = `accentColor @ 0.4f`.
 *
 * Uses [OverlayType.Preview] per replication advisory section 1. Dismissal via [onDismiss] callback
 * (not popBackStack).
 */
@Composable
public fun WidgetSettingsSheet(
  widgetTypeId: String,
  widgetInstanceId: String,
  widgetRegistry: WidgetRegistry,
  dataProviderRegistry: DataProviderRegistry,
  providerSettingsStore: ProviderSettingsStore,
  widgetStyleStore: WidgetStyleStore,
  entitlementManager: EntitlementManager,
  onDismiss: () -> Unit,
  onNavigate: (SettingNavigation) -> Unit,
  onNavigateToSetup: (String) -> Unit,
  onNavigateToPackBrowser: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val widgetSpec = widgetRegistry.findByTypeId(widgetTypeId)
  val theme = LocalDashboardTheme.current

  val pagerState = rememberPagerState(initialPage = PAGE_SETTINGS, pageCount = { PAGE_COUNT })
  val scope = rememberCoroutineScope()

  OverlayScaffold(
    title = widgetSpec?.displayName ?: widgetTypeId,
    overlayType = OverlayType.Preview,
    onBack = onDismiss,
    modifier = modifier.fillMaxSize(),
    actions = {
      PagerIconActions(
        pagerState = pagerState,
        theme = theme,
        onPageSelected = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
      )
    },
  ) {
    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize().testTag("widget_settings_pager"),
    ) { page ->
      when (page) {
        PAGE_SETTINGS ->
          SettingsPageContent(
            widgetSpec = widgetSpec,
            widgetInstanceId = widgetInstanceId,
            providerSettingsStore = providerSettingsStore,
            widgetStyleStore = widgetStyleStore,
            entitlementManager = entitlementManager,
            theme = theme,
            onNavigate = onNavigate,
          )
        PAGE_DATA_SOURCE ->
          DataProviderSettingsContent(
            widgetSpec = widgetSpec,
            dataProviderRegistry = dataProviderRegistry,
            theme = theme,
            onNavigateToSetup = onNavigateToSetup,
          )
        PAGE_INFO ->
          WidgetInfoContent(
            widgetTypeId = widgetTypeId,
            widgetSpec = widgetSpec,
            theme = theme,
            onNavigateToPackBrowser = onNavigateToPackBrowser,
          )
      }
    }
  }
}

/**
 * Pager icon buttons placed in the title bar's actions slot.
 *
 * Active icon: `accentColor` (full opacity). Inactive: `accentColor @ 0.4f`.
 * Icons: Settings (page 0), Extension (page 1), Info (page 2).
 */
@Composable
private fun PagerIconActions(
  pagerState: PagerState,
  theme: DashboardThemeDefinition,
  onPageSelected: (Int) -> Unit,
) {
  val icons = listOf(
    Icons.Default.Settings to "Settings",
    Icons.Default.Extension to "Data Source",
    Icons.Default.Info to "Info",
  )

  icons.forEachIndexed { index, (icon, contentDesc) ->
    val isActive = pagerState.currentPage == index
    IconButton(
      onClick = { onPageSelected(index) },
      modifier = Modifier.testTag("widget_settings_icon_$index"),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = contentDesc,
        tint =
          if (isActive) theme.accentColor
          else theme.accentColor.copy(alpha = 0.4f),
        modifier = Modifier.size(24.dp),
      )
    }
  }
  Spacer(modifier = Modifier.width(4.dp))
}

private const val PAGE_SETTINGS = 0
private const val PAGE_DATA_SOURCE = 1
private const val PAGE_INFO = 2
private const val PAGE_COUNT = 3
