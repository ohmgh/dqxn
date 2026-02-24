package app.dqxn.android.feature.dashboard.test

import app.dqxn.android.core.thermal.RenderConfig
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.widget.WidgetData
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fake implementation of the binding interface for testing.
 *
 * Returns configurable [MutableStateFlow]s per widget for direct test control over what data
 * widgets receive.
 */
public class FakeWidgetDataBinder {

  /** Configurable data flows per widget instanceId. Tests can emit into these directly. */
  public val widgetDataFlows: ConcurrentHashMap<String, MutableStateFlow<WidgetData>> =
    ConcurrentHashMap()

  /**
   * Bind a widget and return its controllable data flow.
   */
  public fun bind(
    widget: DashboardWidgetInstance,
    compatibleSnapshots: Set<KClass<out DataSnapshot>>,
    renderConfig: StateFlow<RenderConfig>,
  ): Flow<WidgetData> {
    val flow = widgetDataFlows.getOrPut(widget.instanceId) { MutableStateFlow(WidgetData.Empty) }
    return flow
  }

  /** Emit data for a specific widget. Creates the flow if it doesn't exist. */
  public fun emit(widgetId: String, data: WidgetData) {
    widgetDataFlows.getOrPut(widgetId) { MutableStateFlow(WidgetData.Empty) }.value = data
  }

  /** Reset all flows. */
  public fun reset() {
    widgetDataFlows.clear()
  }
}
