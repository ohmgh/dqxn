package app.dqxn.android.feature.settings.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * Sheet edge visibility: Preview and Confirmation types get a 1dp border to visually separate
 * from the dashboard canvas behind.
 *
 * Back button uses M3 IconButton (48dp touch target, matching old codebase).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun OverlayScaffold(
  title: String,
  overlayType: OverlayType,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
  content: @Composable () -> Unit,
) {
  val theme = LocalDashboardTheme.current
  val cornerRadius = CardSize.LARGE.cornerRadius
  val shape = overlayType.toShape(cornerRadius)

  // No AnimatedVisibility wrapper — NavHost route transitions handle all enter/exit animations.
  // A scaffold-level AnimatedVisibility would conflict with NavHost transitions (double-animation
  // on enter, no exit animation since visible was never toggled to false).
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = when (overlayType) {
      OverlayType.Hub -> Alignment.Center
      OverlayType.Preview -> Alignment.BottomCenter
      OverlayType.Confirmation -> Alignment.Center
    },
  ) {
    // Dismiss zone: tap outside content to close (effective on wider screens for Hub)
    if (overlayType == OverlayType.Hub) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onBack,
          ),
      )
    }
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
          .then(
            when (overlayType) {
              OverlayType.Hub -> {
                val insets = WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
                Modifier.padding(
                  top = insets.calculateTopPadding(),
                  bottom = insets.calculateBottomPadding() + 16.dp,
                )
              }
              OverlayType.Preview -> {
                val insets = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
                Modifier.padding(bottom = insets.calculateBottomPadding() + 16.dp)
              }
              OverlayType.Confirmation -> Modifier
            }
          ),
    ) {
      OverlayTitleBar(title = title, onBack = onBack, actions = actions)
      Box(modifier = Modifier.padding(horizontal = DashboardSpacing.ScreenEdgePadding)) {
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
