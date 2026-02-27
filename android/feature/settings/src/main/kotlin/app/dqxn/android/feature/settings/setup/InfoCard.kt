package app.dqxn.android.feature.settings.setup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.SemanticColors
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.contracts.setup.InfoStyle
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Info card for setup pages with [SemanticColors] styling.
 *
 * Bridges to the info row pattern from Plan 04 (InfoSettingRow), adapted for [SetupDefinition.Info]
 * subtypes. Maps [InfoStyle] to semantic colors:
 * - [InfoStyle.INFO] -> [SemanticColors.Info]
 * - [InfoStyle.WARNING] -> [SemanticColors.Warning]
 * - [InfoStyle.SUCCESS] -> [SemanticColors.Success]
 * - [InfoStyle.ERROR] -> [SemanticColors.Error]
 */
@Composable
internal fun InfoCard(
  definition: SetupDefinition.Info,
) {
  val theme = LocalDashboardTheme.current
  val (tintColor, icon) = definition.style.toColorAndIcon()

  Card(
    shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius),
    colors =
      CardDefaults.cardColors(
        containerColor = tintColor.copy(alpha = 0.1f),
      ),
    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(DashboardSpacing.CardInternalPadding),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = definition.style.name,
        tint = tintColor,
        modifier = Modifier.size(24.dp),
      )

      Text(
        text = definition.label,
        style = DashboardTypography.itemTitle,
        color = theme.primaryTextColor,
        modifier = Modifier.weight(1f).padding(start = DashboardSpacing.IconTextGap),
      )
    }

    definition.description?.let { desc ->
      Text(
        text = desc,
        style = DashboardTypography.description,
        color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
        modifier =
          Modifier.padding(
            start = DashboardSpacing.CardInternalPadding + 24.dp + DashboardSpacing.IconTextGap,
            end = DashboardSpacing.CardInternalPadding,
            bottom = DashboardSpacing.CardInternalPadding,
          ),
      )
    }
  }
}

private fun InfoStyle.toColorAndIcon(): Pair<Color, ImageVector> =
  when (this) {
    InfoStyle.INFO -> SemanticColors.Info to Icons.Filled.Info
    InfoStyle.WARNING -> SemanticColors.Warning to Icons.Filled.Warning
    InfoStyle.SUCCESS -> SemanticColors.Success to Icons.Filled.CheckCircle
    InfoStyle.ERROR -> SemanticColors.Error to Icons.Filled.Error
  }
