package app.dqxn.android.data.style

import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import kotlinx.coroutines.flow.Flow

/** Per-widget container style persistence. Returns [WidgetStyle.Default] for missing widgets. */
public interface WidgetStyleStore {

  /** Observe the style for a given widget instance. Emits [WidgetStyle.Default] if not set. */
  public fun getStyle(instanceId: String): Flow<WidgetStyle>

  /** Persist a style override for the given widget instance. */
  public suspend fun setStyle(instanceId: String, style: WidgetStyle)

  /** Remove the style override, reverting to [WidgetStyle.Default]. */
  public suspend fun removeStyle(instanceId: String)

  /** Remove all style overrides. Used by "Delete All Data" (F14.4). */
  public suspend fun clearAll()
}
