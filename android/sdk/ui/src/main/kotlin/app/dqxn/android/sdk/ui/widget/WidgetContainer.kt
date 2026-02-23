package app.dqxn.android.sdk.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Widget rendering container. Applies `graphicsLayer` for isolated RenderNode, border, rim padding,
 * and background style per the widget [style].
 *
 * Glow rendering (`RenderEffect.createBlurEffect()`) deferred to Phase 7. Error boundary deferred
 * to Phase 7 (`WidgetSlot`).
 */
@Composable
public fun WidgetContainer(
  style: WidgetStyle,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
  content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
  val theme = LocalDashboardTheme.current
  val cornerPercent = style.cornerRadiusPercent / 2
  val shape = remember(cornerPercent) { RoundedCornerShape(percent = cornerPercent) }

  BoxWithConstraints(
    modifier =
      modifier
        .graphicsLayer { alpha = style.opacity }
        .then(
          if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
          } else {
            Modifier
          }
        )
        .clip(shape)
        .then(
          if (style.showBorder) {
            Modifier.border(width = 2.dp, color = theme.widgetBorderColor, shape = shape)
          } else {
            Modifier
          }
        )
        .then(
          when (style.backgroundStyle) {
            BackgroundStyle.SOLID -> Modifier.background(theme.widgetBackgroundBrush, shape)
            BackgroundStyle.NONE -> Modifier
          }
        ),
  ) {
    val density = LocalDensity.current
    val rimPx =
      with(density) {
        val minDimensionPx = minOf(maxWidth.toPx(), maxHeight.toPx())
        (minDimensionPx * (style.rimSizePercent / 100f))
      }
    val rimDp = with(density) { rimPx.toDp() }

    BoxWithConstraints(
      modifier = Modifier.padding(rimDp),
      content = content,
    )
  }
}
