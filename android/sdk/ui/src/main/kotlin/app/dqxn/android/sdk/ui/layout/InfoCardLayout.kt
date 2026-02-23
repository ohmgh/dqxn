package app.dqxn.android.sdk.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.contracts.settings.InfoCardLayoutMode
import app.dqxn.android.sdk.contracts.settings.SizeOption
import app.dqxn.android.sdk.contracts.settings.toMultiplier

/**
 * Deterministic weighted layout for info card widgets.
 *
 * Distributes available space among icon, top text, and bottom text slots using [SizeOption]
 * multipliers. The normalization target is [NORMALIZATION_TARGET] (80% of available height) to
 * provide a 20% safety buffer for font leading and descenders.
 *
 * Three layout modes:
 * - [InfoCardLayoutMode.STANDARD]: Vertical stack -- icon on top, title below, body at bottom.
 * - [InfoCardLayoutMode.COMPACT]: Icon + title inline on one row, body text below.
 * - [InfoCardLayoutMode.WIDE]: Icon left column, text right column (2-column).
 */
@Composable
public fun InfoCardLayout(
  modifier: Modifier = Modifier,
  layoutMode: InfoCardLayoutMode = InfoCardLayoutMode.STANDARD,
  iconSize: SizeOption = SizeOption.MEDIUM,
  topTextSize: SizeOption = SizeOption.MEDIUM,
  bottomTextSize: SizeOption = SizeOption.MEDIUM,
  icon: (@Composable (Dp) -> Unit)? = null,
  topText: (@Composable (TextStyle) -> Unit)? = null,
  bottomText: (@Composable (TextStyle) -> Unit)? = null,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val availableHeightPx = with(density) { maxHeight.toPx() }
    val availableWidthPx = with(density) { maxWidth.toPx() }
    val spacerHeightPx = with(density) { SPACER_HEIGHT.toPx() }

    val allocation =
      remember(iconSize, topTextSize, bottomTextSize, availableHeightPx, spacerHeightPx) {
        computeAllocation(
          iconMultiplier = iconSize.toMultiplier(),
          topTextMultiplier = topTextSize.toMultiplier(),
          bottomTextMultiplier = bottomTextSize.toMultiplier(),
          availableHeightPx = availableHeightPx,
          spacerHeightPx = spacerHeightPx,
        )
      }

    val iconDp = with(density) { allocation.iconPx.toDp() }
    val topTextStyle =
      remember(allocation.topTextPx) {
        getTightTextStyle(with(density) { allocation.topTextPx.toSp() })
      }
    val bottomTextStyle =
      remember(allocation.bottomTextPx) {
        getTightTextStyle(with(density) { allocation.bottomTextPx.toSp() })
      }

    when (layoutMode) {
      InfoCardLayoutMode.STANDARD ->
        StandardLayout(
          iconDp = iconDp,
          icon = icon,
          topTextStyle = topTextStyle,
          topText = topText,
          bottomTextStyle = bottomTextStyle,
          bottomText = bottomText,
        )
      InfoCardLayoutMode.COMPACT ->
        CompactLayout(
          iconDp = iconDp,
          icon = icon,
          topTextStyle = topTextStyle,
          topText = topText,
          bottomTextStyle = bottomTextStyle,
          bottomText = bottomText,
        )
      InfoCardLayoutMode.WIDE ->
        WideLayout(
          iconDp = iconDp,
          icon = icon,
          topTextStyle = topTextStyle,
          topText = topText,
          bottomTextStyle = bottomTextStyle,
          bottomText = bottomText,
          availableWidthPx = availableWidthPx,
          density = density,
        )
    }
  }
}

// --- Layout Modes ---

@Composable
private fun StandardLayout(
  iconDp: Dp,
  icon: (@Composable (Dp) -> Unit)?,
  topTextStyle: TextStyle,
  topText: (@Composable (TextStyle) -> Unit)?,
  bottomTextStyle: TextStyle,
  bottomText: (@Composable (TextStyle) -> Unit)?,
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    if (icon != null && iconDp > 0.dp) {
      Box(modifier = Modifier.size(iconDp), contentAlignment = Alignment.Center) { icon(iconDp) }
      Spacer(modifier = Modifier.height(SPACER_HEIGHT))
    }
    if (topText != null) {
      topText(topTextStyle)
      Spacer(modifier = Modifier.height(SPACER_HEIGHT))
    }
    if (bottomText != null) {
      bottomText(bottomTextStyle)
    }
  }
}

@Composable
private fun CompactLayout(
  iconDp: Dp,
  icon: (@Composable (Dp) -> Unit)?,
  topTextStyle: TextStyle,
  topText: (@Composable (TextStyle) -> Unit)?,
  bottomTextStyle: TextStyle,
  bottomText: (@Composable (TextStyle) -> Unit)?,
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (icon != null && iconDp > 0.dp) {
        Box(modifier = Modifier.size(iconDp), contentAlignment = Alignment.Center) { icon(iconDp) }
        Spacer(modifier = Modifier.width(SPACER_HEIGHT))
      }
      if (topText != null) {
        topText(topTextStyle)
      }
    }
    if (bottomText != null) {
      Spacer(modifier = Modifier.height(SPACER_HEIGHT))
      bottomText(bottomTextStyle)
    }
  }
}

@Composable
private fun WideLayout(
  iconDp: Dp,
  icon: (@Composable (Dp) -> Unit)?,
  topTextStyle: TextStyle,
  topText: (@Composable (TextStyle) -> Unit)?,
  bottomTextStyle: TextStyle,
  bottomText: (@Composable (TextStyle) -> Unit)?,
  availableWidthPx: Float,
  density: androidx.compose.ui.unit.Density,
) {
  Row(
    modifier = Modifier.fillMaxSize(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null && iconDp > 0.dp) {
      Box(modifier = Modifier.size(iconDp), contentAlignment = Alignment.Center) { icon(iconDp) }
      Spacer(modifier = Modifier.width(SPACER_HEIGHT))
    }
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.Center,
    ) {
      if (topText != null) {
        topText(topTextStyle)
        Spacer(modifier = Modifier.height(SPACER_HEIGHT))
      }
      if (bottomText != null) {
        bottomText(bottomTextStyle)
      }
    }
  }
}

// --- Allocation Algorithm ---

/** Normalization target: allocate 80% of available height to content elements. */
internal const val NORMALIZATION_TARGET: Float = 0.8f

/** Height of spacers between content elements. */
private val SPACER_HEIGHT = 4.dp

/**
 * Computed space allocation for each content slot, in pixels.
 *
 * Public for testability -- the normalization math is the core of InfoCardLayout.
 */
public data class SpaceAllocation(
  val iconPx: Float,
  val topTextPx: Float,
  val bottomTextPx: Float,
)

/**
 * Computes space allocation via weighted normalization.
 * 1. Subtract fixed spacer space from available height.
 * 2. Multiply remaining space by [NORMALIZATION_TARGET] (80%).
 * 3. Sum raw multiplier weights for non-zero elements.
 * 4. Distribute proportionally.
 */
internal fun computeAllocation(
  iconMultiplier: Float,
  topTextMultiplier: Float,
  bottomTextMultiplier: Float,
  availableHeightPx: Float,
  spacerHeightPx: Float,
): SpaceAllocation {
  // Count active spacers (between non-zero adjacent elements)
  val elements = listOf(iconMultiplier, topTextMultiplier, bottomTextMultiplier)
  val nonZeroCount = elements.count { it > 0f }
  val spacerCount = maxOf(0, nonZeroCount - 1)

  val heightAfterSpacers = availableHeightPx - (spacerCount * spacerHeightPx)
  val allocatableHeight = heightAfterSpacers * NORMALIZATION_TARGET

  val totalWeight = iconMultiplier + topTextMultiplier + bottomTextMultiplier

  if (totalWeight <= 0f || allocatableHeight <= 0f) {
    return SpaceAllocation(0f, 0f, 0f)
  }

  return SpaceAllocation(
    iconPx = allocatableHeight * (iconMultiplier / totalWeight),
    topTextPx = allocatableHeight * (topTextMultiplier / totalWeight),
    bottomTextPx = allocatableHeight * (bottomTextMultiplier / totalWeight),
  )
}

/**
 * Creates a [TextStyle] with tight font metrics -- eliminates padding above/below text for precise
 * space utilization.
 */
internal fun getTightTextStyle(fontSize: TextUnit): TextStyle =
  TextStyle(
    fontSize = fontSize,
    lineHeight = fontSize,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle =
      LineHeightStyle(
        trim = LineHeightStyle.Trim.Both,
        alignment = LineHeightStyle.Alignment.Center,
      ),
  )
