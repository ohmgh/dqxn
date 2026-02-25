package app.dqxn.android.feature.settings.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.feature.settings.row.SettingRowDispatcher
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.contracts.setup.SetupResult
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Renders a single [SetupDefinition] by dispatching to the correct card composable.
 *
 * Implements three-layer visibility (same pattern as SettingRowDispatcher):
 * 1. [SetupDefinition.hidden] -- hard skip, no animation
 * 2. [SetupDefinition.visibleWhen] -- conditional visibility with animation
 * 3. Entitlement gating via [isAccessible]
 *
 * For [SetupDefinition.Setting], dispatches to [SettingRowDispatcher] (two-layer dispatch per
 * replication advisory section 7). The Setting wrapper's own [visibleWhen] is checked first,
 * then the inner definition's visibility is handled by the row dispatcher.
 */
@Composable
fun SetupDefinitionRenderer(
  definition: SetupDefinition,
  result: SetupResult?,
  currentSettings: Map<String, Any?>,
  entitlementManager: EntitlementManager,
  onValueChanged: (String, Any?) -> Unit,
  onPermissionRequest: (SetupDefinition.RuntimePermission) -> Unit,
  onSystemSettingsOpen: (String) -> Unit,
  onNavigate: ((SettingNavigation) -> Unit)? = null,
  scanStateMachine: DeviceScanStateMachine? = null,
  pairedDevices: List<ScanDevice> = emptyList(),
  onDeviceForget: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  // Layer 1: Hard skip
  if (definition.hidden) return

  // Layer 3: Entitlement gating
  if (!definition.isAccessible(entitlementManager::hasEntitlement)) return

  // Layer 2: Conditional visibility with animation
  val isVisible by remember(definition, currentSettings) {
    derivedStateOf {
      definition.visibleWhen?.invoke(currentSettings) != false
    }
  }

  AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier,
  ) {
    DefinitionContent(
      definition = definition,
      result = result,
      currentSettings = currentSettings,
      entitlementManager = entitlementManager,
      onValueChanged = onValueChanged,
      onPermissionRequest = onPermissionRequest,
      onSystemSettingsOpen = onSystemSettingsOpen,
      onNavigate = onNavigate,
      scanStateMachine = scanStateMachine,
      pairedDevices = pairedDevices,
      onDeviceForget = onDeviceForget,
    )
  }
}

@Composable
private fun DefinitionContent(
  definition: SetupDefinition,
  result: SetupResult?,
  currentSettings: Map<String, Any?>,
  entitlementManager: EntitlementManager,
  onValueChanged: (String, Any?) -> Unit,
  onPermissionRequest: (SetupDefinition.RuntimePermission) -> Unit,
  onSystemSettingsOpen: (String) -> Unit,
  onNavigate: ((SettingNavigation) -> Unit)?,
  scanStateMachine: DeviceScanStateMachine?,
  pairedDevices: List<ScanDevice>,
  onDeviceForget: (String) -> Unit,
) {
  when (definition) {
    is SetupDefinition.RuntimePermission -> SetupPermissionCard(
      definition = definition,
      satisfied = result?.satisfied == true,
      onPermissionRequest = { onPermissionRequest(definition) },
      onOpenSettings = {
        onSystemSettingsOpen(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
      },
    )

    is SetupDefinition.SystemServiceToggle -> SetupToggleCard(
      label = definition.label,
      description = definition.description,
      satisfied = result?.satisfied == true,
      onEnableClick = { onSystemSettingsOpen(definition.settingsAction) },
    )

    is SetupDefinition.SystemService -> SetupToggleCard(
      label = definition.label,
      description = definition.description,
      satisfied = result?.satisfied == true,
      onEnableClick = {
        onSystemSettingsOpen(android.provider.Settings.ACTION_SETTINGS)
      },
    )

    is SetupDefinition.DeviceScan -> DeviceScanCard(
      definition = definition,
      stateMachine = scanStateMachine,
      pairedDevices = pairedDevices,
      onDeviceForget = onDeviceForget,
    )

    is SetupDefinition.Instruction -> InstructionCard(
      definition = definition,
    )

    is SetupDefinition.Info -> InfoCard(
      definition = definition,
    )

    is SetupDefinition.Setting -> {
      // Two-layer dispatch: Setting wrapper -> SettingRowDispatcher
      // The Setting wrapper's visibleWhen is already checked above (layer 2).
      // The inner SettingDefinition's visibleWhen is handled by SettingRowDispatcher.
      val theme = LocalDashboardTheme.current
      val currentValue = currentSettings[definition.definition.key] ?: definition.definition.default
      SettingRowDispatcher(
        definition = definition.definition,
        currentValue = currentValue,
        currentSettings = currentSettings,
        entitlementManager = entitlementManager,
        theme = theme,
        onValueChanged = onValueChanged,
        onNavigate = onNavigate,
      )
    }
  }
}
