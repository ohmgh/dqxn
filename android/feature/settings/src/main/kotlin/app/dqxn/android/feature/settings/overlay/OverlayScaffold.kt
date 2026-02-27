package app.dqxn.android.feature.settings.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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

/** Compact screen width threshold. Below this, no width constraint is applied. */
private val COMPACT_MAX_WIDTH: Dp = 600.dp

/** Maximum content width per overlay type on medium+ screens. */
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
 * On medium+ screens (>= 600dp), overlay width is constrained:
 * - [OverlayType.Hub]: max 480dp, centered.
 * - [OverlayType.Preview]: max 520dp, bottom-anchored.
 * - [OverlayType.Confirmation]: max 400dp, centered.
 *
 * On compact screens (< 600dp), no width constraint is applied.
 *
 * Background color from [LocalDashboardTheme]. Internal padding from
 * [DashboardSpacing.ScreenEdgePadding]. Animation via [DashboardMotion.sheetEnter] /
 * [DashboardMotion.sheetExit].
 *
 * Close button meets 76dp minimum touch target (F10.4).
 */
@Composable
public fun OverlayScaffold(
  title: String,
  overlayType: OverlayType,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  visible: Boolean = true,
  content: @Composable () -> Unit,
) {
  val theme = LocalDashboardTheme.current
  val cornerRadius = CardSize.LARGE.cornerRadius
  val shape = overlayType.toShape(cornerRadius)

  val screenWidth = LocalConfiguration.current.screenWidthDp.dp
  val isCompact = screenWidth < COMPACT_MAX_WIDTH

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
      val contentModifier = if (isCompact) {
        modifier
      } else {
        val baseModifier = modifier.widthIn(max = overlayType.maxWidthDp())
        if (overlayType == OverlayType.Hub) {
          baseModifier.fillMaxHeight()
        } else {
          baseModifier
        }
      }

      Column(
        modifier =
          contentModifier
            .testTag("overlay_scaffold_${overlayType.name.lowercase()}")
            .clip(shape)
            .background(theme.backgroundBrush, shape)
            .padding(DashboardSpacing.ScreenEdgePadding),
      ) {
        OverlayTitleBar(title = title, onClose = onClose)
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
