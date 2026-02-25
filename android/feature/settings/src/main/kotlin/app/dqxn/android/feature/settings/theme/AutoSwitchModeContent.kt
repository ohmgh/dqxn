package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Entitlement ID required for premium auto-switch modes. */
private const val PREMIUM_ENTITLEMENT: String = "plus"

/**
 * Auto-switch mode selector with entitlement gating.
 *
 * Displays all 5 [AutoSwitchMode] values as selectable rows with radio buttons.
 * [AutoSwitchMode.SOLAR_AUTO] and [AutoSwitchMode.ILLUMINANCE_AUTO] show lock icons
 * when the user lacks the [PREMIUM_ENTITLEMENT].
 *
 * When [AutoSwitchMode.ILLUMINANCE_AUTO] is selected, shows [IlluminanceThresholdControl].
 */
@Composable
public fun AutoSwitchModeContent(
  selectedMode: AutoSwitchMode,
  illuminanceThreshold: Float,
  entitlementManager: EntitlementManager,
  onModeSelected: (AutoSwitchMode) -> Unit,
  onIlluminanceThresholdChanged: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val modes: ImmutableList<AutoSwitchMode> =
    remember { AutoSwitchMode.entries.toImmutableList() }
  val hasPremium = remember(entitlementManager) {
    entitlementManager.hasEntitlement(PREMIUM_ENTITLEMENT)
  }

  Column(
    modifier = modifier.fillMaxWidth().testTag("auto_switch_mode_content"),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
  ) {
    modes.forEach { mode ->
      val isGated = mode.isPremiumGated()
      val showLock = isGated && !hasPremium

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onModeSelected(mode) }
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .testTag("auto_switch_mode_${mode.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        RadioButton(
          selected = mode == selectedMode,
          onClick = { onModeSelected(mode) },
          colors =
            RadioButtonDefaults.colors(
              selectedColor = theme.accentColor,
              unselectedColor = theme.secondaryTextColor,
            ),
        )

        Text(
          text = mode.displayLabel(),
          style = DashboardTypography.itemTitle,
          color = theme.primaryTextColor,
          modifier = Modifier.weight(1f),
        )

        if (showLock) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Premium required",
            tint = theme.secondaryTextColor,
            modifier =
              Modifier.size(20.dp)
                .testTag("lock_icon_${mode.name}"),
          )
        }
      }
    }

    // -- Illuminance threshold control (shown when ILLUMINANCE_AUTO selected) --
    if (selectedMode == AutoSwitchMode.ILLUMINANCE_AUTO) {
      IlluminanceThresholdControl(
        threshold = illuminanceThreshold,
        onThresholdChanged = onIlluminanceThresholdChanged,
        theme = theme,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    }
  }
}

/** Whether this mode requires premium entitlement. */
private fun AutoSwitchMode.isPremiumGated(): Boolean =
  this == AutoSwitchMode.SOLAR_AUTO || this == AutoSwitchMode.ILLUMINANCE_AUTO

/** Human-readable display label for auto-switch modes. */
private fun AutoSwitchMode.displayLabel(): String =
  when (this) {
    AutoSwitchMode.LIGHT -> "Light"
    AutoSwitchMode.DARK -> "Dark"
    AutoSwitchMode.SYSTEM -> "System"
    AutoSwitchMode.SOLAR_AUTO -> "Solar Auto"
    AutoSwitchMode.ILLUMINANCE_AUTO -> "Illuminance Auto"
  }
