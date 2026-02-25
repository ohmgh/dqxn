package app.dqxn.android.feature.settings.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.feature.settings.R
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.feature.settings.row.SettingRowDispatcher
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch

/**
 * Widget feature settings tab.
 *
 * Loads current settings from [ProviderSettingsStore.getAllSettings] via
 * [collectAsStateWithLifecycle] (Layer 1 -- overlay lifecycle). Renders [WidgetRenderer.settingsSchema]
 * items through [SettingRowDispatcher]. Value changes write through to
 * [ProviderSettingsStore.setSetting] immediately.
 */
@Composable
internal fun FeatureSettingsContent(
  widgetSpec: WidgetRenderer?,
  providerSettingsStore: ProviderSettingsStore,
  entitlementManager: EntitlementManager,
  theme: DashboardThemeDefinition,
  onNavigate: (SettingNavigation) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (widgetSpec == null || widgetSpec.settingsSchema.isEmpty()) {
    EmptySettingsPlaceholder(theme = theme, modifier = modifier)
    return
  }

  val packId = widgetSpec.typeId.substringBefore(':')
  val providerId = widgetSpec.typeId.substringAfter(':')

  val currentSettings by
    providerSettingsStore.getAllSettings(packId, providerId).collectAsStateWithLifecycle(
      initialValue = kotlinx.collections.immutable.persistentMapOf(),
    )

  val scope = rememberCoroutineScope()

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(vertical = DashboardSpacing.ItemGap)
        .testTag("feature_settings_content"),
  ) {
    widgetSpec.settingsSchema.forEach { definition ->
      SettingRowDispatcher(
        definition = definition,
        currentValue = currentSettings[definition.key],
        currentSettings = currentSettings,
        entitlementManager = entitlementManager,
        theme = theme,
        onValueChanged = { key, value ->
          scope.launch {
            providerSettingsStore.setSetting(packId, providerId, key, value)
          }
        },
        onNavigate = onNavigate,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun EmptySettingsPlaceholder(
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize().testTag("feature_settings_empty"),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = stringResource(R.string.widget_settings_no_settings),
      style = DashboardTypography.description,
      color = theme.secondaryTextColor,
    )
  }
}
