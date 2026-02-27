package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.feature.settings.R
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.entitlement.Entitlements
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Auto-switch mode selector with entitlement gating.
 *
 * Displays all 5 [AutoSwitchMode] values as selectable cards with mode-specific icons and
 * descriptions. [AutoSwitchMode.SOLAR_AUTO] and [AutoSwitchMode.ILLUMINANCE_AUTO] show a premium
 * badge when the user lacks the [Entitlements.THEMES] entitlement.
 *
 * [IlluminanceThresholdControl] is always rendered (enabled only when [AutoSwitchMode.ILLUMINANCE_AUTO]
 * is selected) so users can pre-configure before switching mode.
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
  val modes: ImmutableList<AutoSwitchMode> = remember { AutoSwitchMode.entries.toImmutableList() }
  val hasThemes =
    remember(entitlementManager) { entitlementManager.hasEntitlement(Entitlements.THEMES) }

  Column(
    modifier = modifier.fillMaxWidth().selectableGroup().testTag("auto_switch_mode_content"),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS),
  ) {
    modes.forEach { mode ->
      val isGated = mode.isPremiumGated()
      val isAvailable = !isGated || hasThemes

      AutoSwitchOption(
        mode = mode,
        isSelected = mode == selectedMode,
        isAvailable = isAvailable,
        theme = theme,
        onClick = { onModeSelected(mode) },
      )
    }

    IlluminanceThresholdControl(
      threshold = illuminanceThreshold,
      onThresholdChanged = onIlluminanceThresholdChanged,
      theme = theme,
      enabled = selectedMode == AutoSwitchMode.ILLUMINANCE_AUTO,
      modifier = Modifier.padding(horizontal = DashboardSpacing.SpaceM),
    )
  }
}

/** Whether this mode requires premium entitlement. */
private fun AutoSwitchMode.isPremiumGated(): Boolean =
  this == AutoSwitchMode.SOLAR_AUTO || this == AutoSwitchMode.ILLUMINANCE_AUTO

@Composable
private fun AutoSwitchOption(
  mode: AutoSwitchMode,
  isSelected: Boolean,
  isAvailable: Boolean,
  theme: DashboardThemeDefinition,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(8.dp)
  val backgroundColor: Color
  val borderColor: Color
  val borderWidth: Dp

  when {
    !isAvailable -> {
      backgroundColor = theme.secondaryTextColor.copy(alpha = 0.03f)
      borderColor = theme.secondaryTextColor.copy(alpha = 0.1f)
      borderWidth = 1.dp
    }
    isSelected -> {
      backgroundColor = theme.accentColor.copy(alpha = 0.15f)
      borderColor = theme.accentColor
      borderWidth = 2.dp
    }
    else -> {
      backgroundColor = theme.secondaryTextColor.copy(alpha = 0.05f)
      borderColor = theme.secondaryTextColor.copy(alpha = 0.2f)
      borderWidth = 1.dp
    }
  }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .clip(shape)
        .border(borderWidth, borderColor, shape)
        .selectable(
          selected = isSelected,
          onClick = onClick,
          role = Role.RadioButton,
          enabled = isAvailable,
        )
        .background(backgroundColor)
        .padding(DashboardSpacing.SpaceS)
        .testTag("auto_switch_mode_${mode.name}"),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceS),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val iconTint =
        if (isAvailable) theme.accentColor
        else theme.secondaryTextColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_LOW)

      Icon(
        imageVector = mode.icon(),
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(24.dp).testTag("auto_switch_icon_${mode.name}"),
      )

      Column(modifier = Modifier.weight(1f)) {
        val textColor =
          if (isAvailable) theme.primaryTextColor
          else theme.primaryTextColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_LOW)
        val descColor =
          if (isAvailable) theme.secondaryTextColor
          else theme.secondaryTextColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_LOW)

        Text(
          text = mode.displayName(),
          style = DashboardTypography.itemTitle,
          color = textColor,
        )
        Text(
          text = mode.descriptionText(),
          style = DashboardTypography.description,
          color = descColor,
        )

        if (!isAvailable) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("premium_badge_${mode.name}"),
          ) {
            Icon(
              imageVector = Icons.Filled.Star,
              contentDescription = null,
              tint = theme.highlightColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_MEDIUM),
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = stringResource(R.string.auto_switch_themes_pack_required),
              style = DashboardTypography.caption,
              fontWeight = FontWeight.Bold,
              color = theme.highlightColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_MEDIUM),
            )
          }
        }
      }
    }
  }
}

/** Mode-specific icon. */
private fun AutoSwitchMode.icon(): ImageVector =
  when (this) {
    AutoSwitchMode.LIGHT -> Icons.Filled.LightMode
    AutoSwitchMode.DARK -> Icons.Filled.DarkMode
    AutoSwitchMode.SYSTEM -> Icons.Filled.PhoneAndroid
    AutoSwitchMode.SOLAR_AUTO -> Icons.Filled.WbTwilight
    AutoSwitchMode.ILLUMINANCE_AUTO -> Icons.Filled.Brightness6
  }

/** Human-readable display name via string resources. */
@Composable
private fun AutoSwitchMode.displayName(): String =
  when (this) {
    AutoSwitchMode.LIGHT -> stringResource(R.string.auto_switch_mode_light)
    AutoSwitchMode.DARK -> stringResource(R.string.auto_switch_mode_dark)
    AutoSwitchMode.SYSTEM -> stringResource(R.string.auto_switch_mode_system)
    AutoSwitchMode.SOLAR_AUTO -> stringResource(R.string.auto_switch_mode_solar)
    AutoSwitchMode.ILLUMINANCE_AUTO -> stringResource(R.string.auto_switch_mode_illuminance)
  }

/** Human-readable description via string resources. */
@Composable
private fun AutoSwitchMode.descriptionText(): String =
  when (this) {
    AutoSwitchMode.LIGHT -> stringResource(R.string.auto_switch_desc_light)
    AutoSwitchMode.DARK -> stringResource(R.string.auto_switch_desc_dark)
    AutoSwitchMode.SYSTEM -> stringResource(R.string.auto_switch_desc_system)
    AutoSwitchMode.SOLAR_AUTO -> stringResource(R.string.auto_switch_desc_solar)
    AutoSwitchMode.ILLUMINANCE_AUTO -> stringResource(R.string.auto_switch_desc_illuminance)
  }

