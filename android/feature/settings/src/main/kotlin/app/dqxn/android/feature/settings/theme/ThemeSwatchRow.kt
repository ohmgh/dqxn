package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Theme property selector for the 7 editable color slots in [DashboardThemeDefinition].
 *
 * Each swatch type maps to one color token on the theme definition.
 */
public enum class SwatchType(public val displayName: String) {
  PRIMARY_TEXT("Primary Text"),
  SECONDARY_TEXT("Secondary Text"),
  ACCENT("Accent"),
  HIGHLIGHT("Highlight"),
  WIDGET_BORDER("Widget Border"),
  BACKGROUND("Background"),
  WIDGET_BACKGROUND("Widget Background"),
}

/**
 * Color swatch containers for selecting which theme property to edit.
 *
 * Each swatch is a 48dp rounded-square container (8dp corner radius) with a 36dp inner color
 * circle. Selected swatch receives a [highlightColor][DashboardThemeDefinition.highlightColor]
 * border.
 *
 * Uses [Row] with horizontal scroll instead of [LazyRow] so all 7 swatches are always composed
 * (required for semantics test tag assertions).
 */
@Composable
public fun ThemeSwatchRow(
  selected: SwatchType,
  onSelected: (SwatchType) -> Unit,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  val types: ImmutableList<SwatchType> =
    remember { SwatchType.entries.toImmutableList() }

  Row(
    modifier =
      modifier
        .horizontalScroll(rememberScrollState())
        .testTag("theme_studio_swatch_row"),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    types.forEach { swatchType ->
      val isSelected = swatchType == selected
      val swatchColor = swatchType.resolveColor(theme)
      val borderColor = if (isSelected) theme.highlightColor else Color.Transparent
      val containerShape = RoundedCornerShape(8.dp)

      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(containerShape)
            .background(
              color =
                if (isSelected) theme.highlightColor.copy(alpha = 0.15f)
                else Color.Transparent,
              shape = containerShape,
            )
            .border(2.dp, borderColor, containerShape)
            .clickable { onSelected(swatchType) }
            .semantics { contentDescription = swatchType.displayName }
            .testTag("swatch_${swatchType.name}"),
        contentAlignment = Alignment.Center,
      ) {
        // 36dp inner color circle
        Box(
          modifier =
            Modifier.size(36.dp)
              .clip(CircleShape)
              .background(swatchColor, CircleShape),
        )
      }
    }
  }
}

/** Resolves the current color for this [SwatchType] from the given theme. */
internal fun SwatchType.resolveColor(theme: DashboardThemeDefinition): Color =
  when (this) {
    SwatchType.PRIMARY_TEXT -> theme.primaryTextColor
    SwatchType.SECONDARY_TEXT -> theme.secondaryTextColor
    SwatchType.ACCENT -> theme.accentColor
    SwatchType.HIGHLIGHT -> theme.highlightColor
    SwatchType.WIDGET_BORDER -> theme.widgetBorderColor
    // For brush-based properties, use a fallback since Brush is not a single Color.
    // The actual gradient editing is in GradientStopRow; swatch shows a representative color.
    SwatchType.BACKGROUND -> Color.DarkGray
    SwatchType.WIDGET_BACKGROUND -> Color.Gray
  }
