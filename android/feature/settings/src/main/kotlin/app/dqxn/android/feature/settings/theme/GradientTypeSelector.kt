package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.ui.theme.GradientType
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Gradient type selector using Material 3 [FilterChip] row.
 *
 * Displays all 5 [GradientType] values: VERTICAL, HORIZONTAL, LINEAR, RADIAL, SWEEP.
 */
@Composable
public fun GradientTypeSelector(
  selected: GradientType,
  onSelected: (GradientType) -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val types: ImmutableList<GradientType> =
    remember { GradientType.entries.toImmutableList() }

  LazyRow(
    modifier = modifier.fillMaxWidth().testTag("gradient_type_selector"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(types) { type ->
      val isSelected = type == selected
      FilterChip(
        selected = isSelected,
        onClick = { onSelected(type) },
        label = {
          Text(
            text = type.displayLabel(),
            style = DashboardTypography.caption,
          )
        },
        shape = RoundedCornerShape(CardSize.SMALL.cornerRadius),
        colors =
          FilterChipDefaults.filterChipColors(
            selectedContainerColor = theme.accentColor.copy(alpha = 0.2f),
            selectedLabelColor = theme.primaryTextColor,
            containerColor = theme.widgetBorderColor.copy(alpha = 0.1f),
            labelColor = theme.secondaryTextColor,
          ),
        border =
          FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            selectedBorderColor = theme.accentColor,
            borderColor = theme.widgetBorderColor,
          ),
        modifier = Modifier.testTag("gradient_type_${type.name}"),
      )
    }
  }
}

/** Human-readable display label for gradient type chips. */
private fun GradientType.displayLabel(): String =
  when (this) {
    GradientType.VERTICAL -> "Vertical"
    GradientType.HORIZONTAL -> "Horizontal"
    GradientType.LINEAR -> "Linear"
    GradientType.RADIAL -> "Radial"
    GradientType.SWEEP -> "Sweep"
  }
