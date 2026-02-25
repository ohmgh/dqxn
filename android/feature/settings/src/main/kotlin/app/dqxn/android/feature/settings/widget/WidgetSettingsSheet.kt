package app.dqxn.android.feature.settings.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import app.dqxn.android.feature.settings.R
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.coroutines.launch

/**
 * 3-tab widget settings sheet wrapping [OverlayScaffold].
 *
 * Tabs:
 * - **Feature** (tab 0): Widget-specific settings via [FeatureSettingsContent].
 * - **Data Source** (tab 1): Provider selection via [DataProviderSettingsContent].
 * - **Info** (tab 2): Widget info, issues, disclaimers via [WidgetInfoContent].
 *
 * Uses [OverlayType.Preview] per replication advisory section 1. Dismissal via [onDismiss] callback
 * (not popBackStack).
 */
@Composable
public fun WidgetSettingsSheet(
  widgetTypeId: String,
  widgetRegistry: WidgetRegistry,
  dataProviderRegistry: DataProviderRegistry,
  providerSettingsStore: ProviderSettingsStore,
  entitlementManager: EntitlementManager,
  onDismiss: () -> Unit,
  onNavigate: (SettingNavigation) -> Unit,
  onNavigateToSetup: (String) -> Unit,
  onNavigateToPackBrowser: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val widgetSpec = widgetRegistry.findByTypeId(widgetTypeId)
  val theme = LocalDashboardTheme.current

  OverlayScaffold(
    title = widgetSpec?.displayName ?: widgetTypeId,
    overlayType = OverlayType.Preview,
    onClose = onDismiss,
    modifier = modifier.fillMaxSize(),
  ) {
    WidgetSettingsTabPager(
      widgetTypeId = widgetTypeId,
      widgetSpec = widgetSpec,
      dataProviderRegistry = dataProviderRegistry,
      providerSettingsStore = providerSettingsStore,
      entitlementManager = entitlementManager,
      theme = theme,
      onNavigate = onNavigate,
      onNavigateToSetup = onNavigateToSetup,
    )
  }
}

@Composable
private fun WidgetSettingsTabPager(
  widgetTypeId: String,
  widgetSpec: WidgetRenderer?,
  dataProviderRegistry: DataProviderRegistry,
  providerSettingsStore: ProviderSettingsStore,
  entitlementManager: EntitlementManager,
  theme: DashboardThemeDefinition,
  onNavigate: (SettingNavigation) -> Unit,
  onNavigateToSetup: (String) -> Unit,
) {
  val tabTitles =
    listOf(
      stringResource(R.string.widget_settings_tab_feature),
      stringResource(R.string.widget_settings_tab_data_source),
      stringResource(R.string.widget_settings_tab_info),
    )

  val pagerState = rememberPagerState(pageCount = { tabTitles.size })
  val scope = rememberCoroutineScope()

  Column {
    SecondaryTabRow(
      selectedTabIndex = pagerState.currentPage,
      containerColor = Color.Transparent,
      contentColor = theme.accentColor,
      modifier = Modifier.fillMaxWidth().testTag("widget_settings_tab_row"),
    ) {
      tabTitles.forEachIndexed { index, title ->
        Tab(
          selected = pagerState.currentPage == index,
          onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
          text = {
            Text(
              text = title,
              color =
                if (pagerState.currentPage == index) theme.accentColor
                else theme.secondaryTextColor,
            )
          },
          modifier = Modifier.testTag("widget_settings_tab_$index"),
        )
      }
    }

    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize().testTag("widget_settings_pager"),
    ) { page ->
      when (page) {
        TAB_FEATURE ->
          FeatureSettingsContent(
            widgetSpec = widgetSpec,
            providerSettingsStore = providerSettingsStore,
            entitlementManager = entitlementManager,
            theme = theme,
            onNavigate = onNavigate,
          )

        TAB_DATA_SOURCE ->
          DataProviderSettingsContent(
            widgetSpec = widgetSpec,
            dataProviderRegistry = dataProviderRegistry,
            theme = theme,
            onNavigateToSetup = onNavigateToSetup,
          )

        TAB_INFO ->
          WidgetInfoContent(
            widgetTypeId = widgetTypeId,
            widgetSpec = widgetSpec,
            theme = theme,
          )
      }
    }
  }
}

private const val TAB_FEATURE = 0
private const val TAB_DATA_SOURCE = 1
private const val TAB_INFO = 2
