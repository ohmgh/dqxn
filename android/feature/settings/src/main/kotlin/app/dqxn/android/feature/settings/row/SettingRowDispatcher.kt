package app.dqxn.android.feature.settings.row

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.dqxn.android.core.design.motion.DashboardMotion
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Central dispatch composable that routes [SettingDefinition] subtypes to their matching row
 * composables.
 *
 * Three-layer visibility gating:
 * 1. `hidden` -- hard skip, no animation.
 * 2. `visibleWhen` -- evaluated as `!= false` (null = always visible, critical for avoiding
 *    flickering on initial empty map load per Pitfall 1).
 * 3. Entitlement check via [EntitlementManager].
 *
 * 12-branch `when` dispatch: first 6 types implemented (Boolean, Enum, Int, Float, String, Info),
 * remaining 6 (Instruction, AppPicker, DateFormat, Timezone, SoundPicker, Uri) fall through to
 * [SettingLabel] as a fallback.
 */
@Composable
public fun SettingRowDispatcher(
  definition: SettingDefinition<*>,
  currentValue: Any?,
  currentSettings: Map<String, Any?>,
  entitlementManager: EntitlementManager,
  theme: DashboardThemeDefinition,
  onValueChanged: (String, Any?) -> Unit,
  onNavigate: ((SettingNavigation) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  // Layer 1: hard skip
  if (definition.hidden) return

  // Layer 2: conditional visibility (null = always visible per Pitfall 1)
  val isVisible = definition.visibleWhen?.invoke(currentSettings) != false

  // Layer 3: entitlement gating
  val hasEntitlement = definition.isAccessible(entitlementManager::hasEntitlement)

  AnimatedVisibility(
    visible = isVisible && hasEntitlement,
    enter = DashboardMotion.expandEnter,
    exit = DashboardMotion.expandExit,
  ) {
    @Suppress("UNCHECKED_CAST")
    when (definition) {
      is SettingDefinition.BooleanSetting ->
        BooleanSettingRow(
          definition = definition,
          currentValue = currentValue as? Boolean ?: definition.default,
          theme = theme,
          onValueChanged = onValueChanged,
          modifier = modifier,
        )

      is SettingDefinition.EnumSetting<*> ->
        EnumSettingRow(
          definition = definition as SettingDefinition.EnumSetting<Nothing>,
          currentValue = currentValue,
          theme = theme,
          onValueChanged = onValueChanged,
          modifier = modifier,
        )

      is SettingDefinition.IntSetting ->
        IntSettingRow(
          definition = definition,
          currentValue = currentValue as? Int ?: definition.default,
          currentSettings = currentSettings,
          theme = theme,
          onValueChanged = onValueChanged,
          modifier = modifier,
        )

      is SettingDefinition.FloatSetting ->
        FloatSettingRow(
          definition = definition,
          currentValue = currentValue as? Float ?: definition.default,
          theme = theme,
          onValueChanged = onValueChanged,
          modifier = modifier,
        )

      is SettingDefinition.StringSetting ->
        StringSettingRow(
          definition = definition,
          currentValue = currentValue as? String ?: definition.default,
          theme = theme,
          onValueChanged = onValueChanged,
          modifier = modifier,
        )

      is SettingDefinition.InfoSetting ->
        InfoSettingRow(
          definition = definition,
          theme = theme,
          modifier = modifier,
        )

      // Not-yet-implemented row types -- fallback to label display
      else ->
        SettingLabel(
          label = definition.label,
          description = definition.description,
          theme = theme,
          modifier = modifier,
        )
    }
  }
}
