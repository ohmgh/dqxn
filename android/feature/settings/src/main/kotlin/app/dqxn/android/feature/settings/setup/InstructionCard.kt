package app.dqxn.android.feature.settings.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Setup instruction card with step number badge rendering and verification state display.
 *
 * Bridges to the instruction row pattern from Plan 05 (InstructionSettingRow), adding a card
 * wrapper with step badge. Step number badge renders as an accent-colored circle with white
 * number text.
 */
@Composable
internal fun InstructionCard(
  definition: SetupDefinition.Instruction,
) {
  val theme = LocalDashboardTheme.current

  Card(
    shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius),
    colors = CardDefaults.cardColors(
      containerColor = theme.widgetBorderColor.copy(alpha = 0.05f),
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 76.dp)
        .padding(DashboardSpacing.CardInternalPadding),
    ) {
      // Step number badge
      if (definition.stepNumber > 0) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(32.dp)
            .background(color = theme.accentColor, shape = CircleShape),
        ) {
          Text(
            text = definition.stepNumber.toString(),
            style = DashboardTypography.buttonLabel,
            color = Color.White,
          )
        }
      }

      // Label + description
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(
            start = if (definition.stepNumber > 0) DashboardSpacing.SpaceS else 0.dp,
          ),
      ) {
        Text(
          text = definition.label,
          style = DashboardTypography.itemTitle,
          color = theme.primaryTextColor,
        )
        definition.description?.let { desc ->
          Text(
            text = desc,
            style = DashboardTypography.description,
            color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
            modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
          )
        }

        // Verification status
        if (definition.verificationStrategy != null) {
          Text(
            text = if (definition.verificationOptional) {
              "Verification: optional"
            } else {
              "Verification: required"
            },
            style = DashboardTypography.caption,
            color = theme.primaryTextColor.copy(alpha = TextEmphasis.Disabled),
            modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
          )
        }

        // Alternative resolution
        definition.alternativeResolution?.let { alt ->
          Text(
            text = alt,
            style = DashboardTypography.caption,
            color = theme.accentColor.copy(alpha = TextEmphasis.Medium),
            modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
          )
        }
      }
    }
  }
}
