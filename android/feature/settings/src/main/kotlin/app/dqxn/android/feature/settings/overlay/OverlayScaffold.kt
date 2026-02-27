package app.dqxn.android.feature.settings.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.motion.DashboardMotion
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Overlay surface type that determines corner shape.
 * - [Hub]: Full-screen overlay (no rounded corners).
 * - [Preview]: Top-rounded overlay (preview sheets from bottom).
 * - [Confirmation]: All-rounded overlay (centered dialogs/confirmations).
 */
public enum class OverlayType {
  Hub,
  Preview,
  Confirmation,
}

/** Maximum content width per overlay type. */
internal fun OverlayType.maxWidthDp(): Dp = when (this) {
  OverlayType.Hub -> 480.dp
  OverlayType.Preview -> 520.dp
  OverlayType.Confirmation -> 400.dp
}

/**
 * Shared container composable for all settings overlay surfaces.
 *
 * Shape is derived from [overlayType] using [CardSize.LARGE.cornerRadius] (16dp):
 * - [OverlayType.Hub]: No rounded corners (0dp all sides).
 * - [OverlayType.Preview]: Top corners rounded (16dp top, 0dp bottom).
 * - [OverlayType.Confirmation]: All corners rounded (16dp all sides).
 *
 * Width constraints:
 * - All overlay types constrained via [OverlayType.maxWidthDp] regardless of screen size.
 * - [OverlayType.Hub]: 480dp. [OverlayType.Preview]: 520dp. [OverlayType.Confirmation]: 400dp.
 *
 * Sheet edge visibility: Preview and Confirmation types get a 1dp border and a semi-transparent
 * darkening overlay to visually separate from the dashboard canvas behind.
 *
 * Back button meets 76dp minimum touch target (F10.4).
 */
@Composable
public fun OverlayScaffold(
  title: String,
  overlayType: OverlayType,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  visible: Boolean = true,
  actions: @Composable RowScope.() -> Unit = {},
  content: @Composable () -> Unit,
) {
  val theme = LocalDashboardTheme.current
  val cornerRadius = CardSize.LARGE.cornerRadius
  val shape = overlayType.toShape(cornerRadius)

  AnimatedVisibility(
    visible = visible,
    enter = DashboardMotion.sheetEnter,
    exit = DashboardMotion.sheetExit,
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = when (overlayType) {
        OverlayType.Hub -> Alignment.Center
        OverlayType.Preview -> Alignment.BottomCenter
        OverlayType.Confirmation -> Alignment.Center
      },
    ) {
      val contentModifier = Modifier
        .widthIn(max = overlayType.maxWidthDp())
        .then(when (overlayType) {
          OverlayType.Hub -> Modifier.fillMaxHeight()
          else -> Modifier
        })
        .then(modifier)

      // Edge visibility: border on Preview/Confirmation types
      val borderModifier = when (overlayType) {
        OverlayType.Preview, OverlayType.Confirmation ->
          Modifier.border(1.dp, theme.widgetBorderColor.copy(alpha = 0.3f), shape)
        else -> Modifier
      }

      Column(
        modifier =
          contentModifier
            .testTag("overlay_scaffold_${overlayType.name.lowercase()}")
            .then(borderModifier)
            .clip(shape)
            .background(theme.backgroundBrush, shape)
            // Edge visibility: semi-transparent darkening overlay for Preview/Confirmation
            .then(
              when (overlayType) {
                OverlayType.Preview, OverlayType.Confirmation ->
                  Modifier.background(Color.Black.copy(alpha = 0.12f))
                else -> Modifier
              }
            )
            .padding(DashboardSpacing.ScreenEdgePadding),
      ) {
        OverlayTitleBar(title = title, onBack = onBack, actions = actions)
        content()
      }
    }
  }
}

/**
 * Maps [OverlayType] to a [RoundedCornerShape] using the given [cornerRadius].
 *
 * Uses [CardSize.LARGE.cornerRadius] (16dp) -- NOT inline dp values per replication advisory fix.
 */
private fun OverlayType.toShape(cornerRadius: androidx.compose.ui.unit.Dp): RoundedCornerShape =
  when (this) {
    OverlayType.Hub -> RoundedCornerShape(0.dp)
    OverlayType.Preview ->
      RoundedCornerShape(
        topStart = cornerRadius,
        topEnd = cornerRadius,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
      )
    OverlayType.Confirmation -> RoundedCornerShape(cornerRadius)
  }
