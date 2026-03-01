package app.dqxn.android.feature.settings.row

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.contracts.setup.InstructionAction
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import java.util.TimeZone

/**
 * Label + optional description row used as the left side of all setting rows.
 *
 * Minimum height 76dp per F10.4 touch target requirement.
 */
@Composable
internal fun SettingLabel(
  label: String,
  description: String?,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier.defaultMinSize(minHeight = 76.dp).padding(vertical = DashboardSpacing.SpaceXS),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = label,
      style = DashboardTypography.itemTitle,
      color = theme.primaryTextColor,
    )
    if (description != null) {
      Text(
        text = description,
        style = DashboardTypography.description,
        color = theme.secondaryTextColor.copy(alpha = TextEmphasis.Medium),
        modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
      )
    }
  }
}

/**
 * Material 3 FilterChip for enum/preset selection.
 *
 * Uses [CardSize.SMALL.cornerRadius] and accent color when selected. Minimum 76dp height.
 */
@Composable
internal fun SelectionChip(
  text: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  FilterChip(
    selected = isSelected,
    onClick = onClick,
    label = {
      Text(
        text = text,
        style = DashboardTypography.buttonLabel,
        color = if (isSelected) theme.accentColor else theme.secondaryTextColor,
      )
    },
    shape = RoundedCornerShape(CardSize.SMALL.cornerRadius),
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = theme.accentColor.copy(alpha = 0.2f),
        selectedLabelColor = theme.accentColor,
        containerColor = theme.secondaryTextColor.copy(alpha = 0.1f),
        labelColor = theme.secondaryTextColor,
      ),
    border =
      FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = isSelected,
        borderColor = theme.secondaryTextColor.copy(alpha = 0.3f),
        selectedBorderColor = theme.accentColor,
        selectedBorderWidth = 2.dp,
        borderWidth = 1.dp,
      ),
    modifier = modifier.defaultMinSize(minHeight = 76.dp),
  )
}

/**
 * Card with border highlight when selected, used for preview-based enum options.
 *
 * Uses [CardSize.MEDIUM.cornerRadius].
 */
@Composable
internal fun PreviewSelectionCard(
  isSelected: Boolean,
  onClick: () -> Unit,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val borderColor =
    if (isSelected) theme.accentColor else theme.widgetBorderColor.copy(alpha = 0.3f)
  val borderWidth = if (isSelected) 2.dp else 1.dp
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius),
    border = BorderStroke(borderWidth, borderColor),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isSelected) theme.accentColor.copy(alpha = 0.1f) else Color.Transparent,
      ),
    modifier = modifier.defaultMinSize(minHeight = 76.dp),
  ) {
    content()
  }
}

/**
 * Formats a timezone zone ID as a GMT offset string (e.g., "GMT+08:00").
 *
 * Returns the raw [zoneId] if parsing fails.
 */
internal fun formatGmtOffset(zoneId: String): String {
  return try {
    val tz = TimeZone.getTimeZone(zoneId)
    val offsetMs = tz.rawOffset
    val hours = offsetMs / 3_600_000
    val minutes = Math.abs((offsetMs % 3_600_000) / 60_000)
    if (minutes == 0) {
      "GMT${if (hours >= 0) "+" else ""}$hours"
    } else {
      "GMT${if (hours >= 0) "+" else ""}$hours:${minutes.toString().padStart(2, '0')}"
    }
  } catch (_: Exception) {
    zoneId
  }
}

/**
 * Executes an [InstructionAction] by launching the appropriate Android intent.
 * - [InstructionAction.OpenUrl]: Opens the URL via ACTION_VIEW.
 * - [InstructionAction.LaunchApp]: Launches the app via package manager launch intent.
 */
internal fun executeInstructionAction(context: Context, action: InstructionAction) {
  when (action) {
    is InstructionAction.OpenUrl -> {
      val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
    }
    is InstructionAction.LaunchApp -> {
      val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
      if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
      }
    }
    is InstructionAction.OpenSystemSettings -> {
      val intent = Intent(action.settingsAction).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      context.startActivity(intent)
    }
  }
}
