package app.dqxn.android.sdk.ui.widget

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

/**
 * Static CompositionLocal providing the supervised `WidgetCoroutineScope` for per-widget coroutine
 * isolation.
 *
 * Each widget gets its own scope with `SupervisorJob` so a crash in one widget does not cancel
 * siblings. Uses `CoroutineExceptionHandler` to report to `widgetStatus`, never propagate.
 *
 * Static because the scope identity is tied to the widget lifecycle, not recomposition.
 */
public val LocalWidgetScope: androidx.compose.runtime.ProvidableCompositionLocal<CoroutineScope> =
  staticCompositionLocalOf { error("No WidgetScope provided") }
