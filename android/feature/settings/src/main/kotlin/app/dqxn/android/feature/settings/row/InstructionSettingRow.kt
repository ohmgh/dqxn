package app.dqxn.android.feature.settings.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Instruction setting row with step number badge, description, and optional action button.
 *
 * Per Pitfall 7 (dual execution): [executeInstructionAction] fires locally AND calls [onNavigate]
 * with [SettingNavigation.OnInstructionAction] so the parent can track verification.
 *
 * Step badge renders as accent color circle with white number text.
 */
@Composable
internal fun InstructionSettingRow(
  definition: SettingDefinition.InstructionSetting,
  theme: DashboardThemeDefinition,
  onNavigate: ((SettingNavigation) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  Row(
    modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Step badge
    if (definition.stepNumber > 0) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(32.dp).background(color = theme.accentColor, shape = CircleShape),
      ) {
        Text(
          text = definition.stepNumber.toString(),
          style = DashboardTypography.buttonLabel,
          color = Color.White,
        )
      }
    }

    Column(
      modifier =
        Modifier.weight(1f)
          .padding(
            start = if (definition.stepNumber > 0) DashboardSpacing.SpaceS else 0.dp,
          ),
    ) {
      Text(
        text = definition.label,
        style = DashboardTypography.itemTitle,
        color = theme.primaryTextColor,
      )
      if (definition.description != null) {
        Text(
          text = definition.description!!,
          style = DashboardTypography.description,
          color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
          modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
        )
      }
    }

    // Action button (dual execution)
    if (definition.action != null) {
      Button(
        onClick = {
          // Local execution
          executeInstructionAction(context, definition.action!!)
          // Navigation callback for verification tracking
          onNavigate?.invoke(
            SettingNavigation.OnInstructionAction(
              settingKey = definition.key,
              action = definition.action!!,
            )
          )
        },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = theme.accentColor,
            contentColor = Color.White,
          ),
        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
      ) {
        Text(text = "Open", style = DashboardTypography.buttonLabel)
      }
    }
  }
}
