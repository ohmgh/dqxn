package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * Color swatch circles for selecting which theme property to edit.
 *
 * Selected swatch receives an accent border. Each swatch shows the current color
 * from the provided [theme] definition.
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

  LazyRow(
    modifier = modifier.testTag("theme_studio_swatch_row"),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(types) { swatchType ->
      val isSelected = swatchType == selected
      val swatchColor = swatchType.resolveColor(theme)
      val borderColor = if (isSelected) theme.accentColor else Color.Transparent

      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(swatchColor, CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .clickable { onSelected(swatchType) }
            .semantics { contentDescription = swatchType.displayName }
            .testTag("swatch_${swatchType.name}"),
      )
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
