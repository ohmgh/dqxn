package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.feature.settings.R
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/** Edge padding in pixels for indicator line calculations. */
private const val EDGE_PAD_PX: Float = 4f

/**
 * Custom illuminance threshold control with a dark→light gradient bar and flanking moon/sun icons.
 *
 * Uses [luxToPosition]/[positionToLux] from LuxMapping.kt for logarithmic scaling. Supports tap and
 * drag gestures to adjust the threshold. Optionally shows a current-lux indicator.
 *
 * @param threshold Current threshold in lux.
 * @param onThresholdChanged Callback when threshold changes.
 * @param theme Active dashboard theme.
 * @param enabled Whether the control is interactive. When false, alpha is reduced and gestures ignored.
 * @param currentLux Optional current ambient lux reading. When non-null, a solid indicator line is drawn.
 * @param modifier Modifier for the container.
 */
@Composable
public fun IlluminanceThresholdControl(
  threshold: Float,
  onThresholdChanged: (Float) -> Unit,
  theme: DashboardThemeDefinition,
  enabled: Boolean = true,
  currentLux: Float? = null,
  modifier: Modifier = Modifier,
) {
  val position = remember(threshold) { luxToPosition(threshold) }
  val displayLux = remember(threshold) { threshold.toInt().coerceIn(1, MAX_LUX.toInt()) }
  val alpha = if (enabled) 1f else DashboardThemeDefinition.EMPHASIS_LOW

  val darkColor = remember(theme.secondaryTextColor, alpha) {
    adjustLightness(theme.secondaryTextColor, 0.15f).copy(alpha = alpha)
  }
  val lightColor = remember(theme.secondaryTextColor, alpha) {
    adjustLightness(theme.secondaryTextColor, 0.75f).copy(alpha = alpha)
  }
  val thresholdLineColor = remember(theme.highlightColor, alpha) {
    theme.highlightColor.copy(alpha = alpha)
  }
  val currentLuxLineColor = remember(theme.accentColor, alpha) {
    theme.accentColor.copy(alpha = alpha)
  }

  Column(
    modifier = modifier.fillMaxWidth().testTag("illuminance_control"),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXS),
  ) {
    Text(
      text = stringResource(R.string.illuminance_switch_dark_below, displayLux),
      style = DashboardTypography.caption,
      color = theme.primaryTextColor.copy(alpha = alpha),
      modifier = Modifier.testTag("illuminance_label"),
    )

    Row(
      horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXS),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Filled.DarkMode,
        contentDescription = stringResource(R.string.illuminance_content_desc_dark),
        tint = theme.secondaryTextColor.copy(alpha = alpha),
        modifier = Modifier.size(20.dp).testTag("illuminance_moon"),
      )

      Box(
        modifier =
          Modifier.weight(1f)
            .height(32.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .testTag("illuminance_canvas"),
      ) {
        Spacer(
          modifier =
            Modifier.matchParentSize()
              .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                  val fraction =
                    ((offset.x - EDGE_PAD_PX) / (size.width - 2 * EDGE_PAD_PX)).coerceIn(0f, 1f)
                  onThresholdChanged(positionToLux(fraction))
                }
              }
              .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                  change.consume()
                  val fraction =
                    ((change.position.x - EDGE_PAD_PX) / (size.width - 2 * EDGE_PAD_PX))
                      .coerceIn(0f, 1f)
                  onThresholdChanged(positionToLux(fraction))
                }
              }
              .drawWithCache {
                val w = size.width
                val h = size.height
                val usableWidth = w - 2 * EDGE_PAD_PX
                // Cached: gradient brush and dash path effect (recreated only on size change)
                val gradientBrush = Brush.horizontalGradient(listOf(darkColor, lightColor))
                val lineStrokeWidth = 2.dp.toPx()
                val dashEffect =
                  PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)

                onDrawBehind {
                  // 1. Dark→light gradient background
                  drawRect(brush = gradientBrush)

                  // 2. Current lux solid vertical line
                  if (currentLux != null) {
                    val currentPos = luxToPosition(currentLux)
                    val cx = EDGE_PAD_PX + currentPos * usableWidth
                    drawLine(
                      color = currentLuxLineColor,
                      start = Offset(cx, 0f),
                      end = Offset(cx, h),
                      strokeWidth = lineStrokeWidth,
                    )
                  }

                  // 3. Threshold dashed vertical line
                  val tx = EDGE_PAD_PX + position * usableWidth
                  drawLine(
                    color = thresholdLineColor,
                    start = Offset(tx, 0f),
                    end = Offset(tx, h),
                    strokeWidth = lineStrokeWidth,
                    pathEffect = dashEffect,
                  )
                }
              },
        )
      }

      Icon(
        imageVector = Icons.Filled.LightMode,
        contentDescription = stringResource(R.string.illuminance_content_desc_bright),
        tint = theme.secondaryTextColor.copy(alpha = alpha),
        modifier = Modifier.size(20.dp).testTag("illuminance_sun"),
      )
    }
  }
}
