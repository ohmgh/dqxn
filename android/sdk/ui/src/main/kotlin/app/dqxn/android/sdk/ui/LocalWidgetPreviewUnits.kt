package app.dqxn.android.sdk.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * Target dimensions during a widget resize gesture, in grid units.
 *
 * Widgets read [LocalWidgetPreviewUnits] for content-aware relayout (e.g., Speedometer arc angle
 * adjustment, InfoCardLayout mode switching between STANDARD/COMPACT/WIDE).
 */
@Immutable
public data class PreviewGridSize(
  val widthUnits: Int,
  val heightUnits: Int,
)

/**
 * CompositionLocal providing target dimensions during resize gesture.
 *
 * Value is `null` when no resize is in progress.
 */
public val LocalWidgetPreviewUnits:
  androidx.compose.runtime.ProvidableCompositionLocal<PreviewGridSize?> =
  compositionLocalOf {
    null
  }
