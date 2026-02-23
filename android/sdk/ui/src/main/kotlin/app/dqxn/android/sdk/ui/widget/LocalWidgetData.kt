package app.dqxn.android.sdk.ui.widget

import androidx.compose.runtime.compositionLocalOf
import app.dqxn.android.sdk.contracts.widget.WidgetData

/**
 * CompositionLocal for per-widget data injection.
 *
 * Provided by `WidgetSlot` (Phase 7) wrapping each widget's `Render()` call. Widgets read via
 * `LocalWidgetData.current` + `derivedStateOf` to defer snapshot reads to the draw phase.
 */
public val LocalWidgetData: androidx.compose.runtime.ProvidableCompositionLocal<WidgetData> =
  compositionLocalOf {
    error("No WidgetData provided")
  }
