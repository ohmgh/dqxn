package app.dqxn.android.core.thermal

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over thermal monitoring. [ThermalManager] is the production implementation;
 * [FakeThermalManager] provides controllable flows for testing and chaos injection.
 */
public interface ThermalMonitor {

  /** Current thermal level derived from system thermal status and headroom analysis. */
  public val thermalLevel: StateFlow<ThermalLevel>

  /** Render configuration derived from [thermalLevel]. */
  public val renderConfig: StateFlow<RenderConfig>
}
