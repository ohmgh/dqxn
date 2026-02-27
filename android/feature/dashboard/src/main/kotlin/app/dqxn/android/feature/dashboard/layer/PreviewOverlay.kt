package app.dqxn.android.feature.dashboard.layer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Overlay wrapper that leaves a transparent tap-to-dismiss zone at the top showing the dashboard.
 *
 * [previewFraction] controls how much of the screen height remains transparent at the top.
 * - 0.15f = 15% transparent, 85% content (Settings, ThemeSelector, ThemeStudio)
 * - 0.38f = 38% transparent, 62% content (WidgetSettings)
 *
 * Tap on the transparent zone calls [onDismiss]. Content is anchored to the bottom. Uses
 * `clickable(indication = null)` to consume taps without visual ripple.
 *
 * Per Pitfall 4 in research: uses `clickable` (tap only), NOT `pointerInput`, to avoid intercepting
 * drag gestures meant for dashboard widgets visible in the peek zone.
 */
@Composable
public fun PreviewOverlay(
  previewFraction: Float,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(modifier = modifier.fillMaxSize()) {
    // Transparent zone: tap to dismiss
    Box(
      modifier =
        Modifier.fillMaxSize()
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onDismiss,
          )
          .testTag("preview_dismiss_zone"),
    )

    // Content anchored to bottom, filling (1 - previewFraction) of screen height
    Box(
      modifier =
        Modifier.fillMaxHeight(1f - previewFraction)
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .testTag("preview_content"),
    ) {
      content()
    }
  }
}
