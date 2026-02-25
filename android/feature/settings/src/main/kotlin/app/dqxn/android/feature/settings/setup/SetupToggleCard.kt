package app.dqxn.android.feature.settings.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.SemanticColors
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Binary toggle card for [SetupDefinition.SystemServiceToggle] and [SetupDefinition.SystemService].
 *
 * Shows a green checkmark when [satisfied], or an "Enable" button linking to system settings
 * when unsatisfied. Works for both toggle types -- the difference is in the evaluation logic
 * (SetupEvaluatorImpl), not the UI.
 */
@Composable
internal fun SetupToggleCard(
  label: String,
  description: String?,
  satisfied: Boolean,
  onEnableClick: () -> Unit,
) {
  val theme = LocalDashboardTheme.current

  Card(
    shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius),
    colors = CardDefaults.cardColors(
      containerColor = if (satisfied) {
        SemanticColors.Success.copy(alpha = 0.1f)
      } else {
        theme.widgetBorderColor.copy(alpha = 0.05f)
      },
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(DashboardSpacing.CardInternalPadding),
    ) {
      Icon(
        imageVector = if (satisfied) Icons.Filled.CheckCircle else Icons.Filled.ToggleOff,
        contentDescription = if (satisfied) "Enabled" else "Disabled",
        tint = if (satisfied) SemanticColors.Success else theme.secondaryTextColor,
        modifier = Modifier.size(24.dp),
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(start = DashboardSpacing.IconTextGap),
      ) {
        Text(
          text = label,
          style = DashboardTypography.itemTitle,
          color = theme.primaryTextColor,
        )
        description?.let { desc ->
          Text(
            text = desc,
            style = DashboardTypography.description,
            color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
            modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
          )
        }
      }

      if (satisfied) {
        Text(
          text = "Enabled",
          style = DashboardTypography.buttonLabel,
          color = SemanticColors.Success,
        )
      } else {
        Button(
          onClick = onEnableClick,
          colors = ButtonDefaults.buttonColors(
            containerColor = theme.accentColor,
            contentColor = Color.White,
          ),
          modifier = Modifier.defaultMinSize(minWidth = 76.dp, minHeight = 76.dp),
        ) {
          Text(text = "Enable", style = DashboardTypography.buttonLabel)
        }
      }
    }
  }
}
