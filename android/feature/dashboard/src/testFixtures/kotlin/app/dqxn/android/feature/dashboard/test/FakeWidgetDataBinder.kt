package app.dqxn.android.feature.dashboard.test

import app.dqxn.android.sdk.contracts.widget.WidgetData
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake binder for testing that provides configurable [MutableStateFlow]s per widget.
 *
 * Tests can emit data directly into per-widget flows without the real binding pipeline.
 * Uses widget instanceId as the key.
 */
public class FakeWidgetDataBinder {

  /** Configurable data flows per widget instanceId. Tests can emit into these directly. */
  public val widgetDataFlows: ConcurrentHashMap<String, MutableStateFlow<WidgetData>> =
    ConcurrentHashMap()

  /** Get the controllable data flow for a widget. Creates one if missing. */
  public fun widgetData(widgetId: String): Flow<WidgetData> =
    widgetDataFlows.getOrPut(widgetId) { MutableStateFlow(WidgetData.Empty) }

  /** Emit data for a specific widget. Creates the flow if it doesn't exist. */
  public fun emit(widgetId: String, data: WidgetData) {
    widgetDataFlows.getOrPut(widgetId) { MutableStateFlow(WidgetData.Empty) }.value = data
  }

  /** Reset all flows. */
  public fun reset() {
    widgetDataFlows.clear()
  }
}
