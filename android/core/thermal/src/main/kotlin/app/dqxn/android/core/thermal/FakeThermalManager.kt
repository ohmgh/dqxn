package app.dqxn.android.core.thermal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Test and chaos-injection implementation of [ThermalMonitor]. Exposes a [MutableStateFlow] that
 * tests can drive directly.
 *
 * Usage:
 * ```kotlin
 * val fake = FakeThermalManager()
 * fake.setLevel(ThermalLevel.DEGRADED)
 * // renderConfig automatically derives from thermalLevel
 * ```
 */
public class FakeThermalManager(scope: CoroutineScope? = null) : ThermalMonitor {

  private val _thermalLevel: MutableStateFlow<ThermalLevel> = MutableStateFlow(ThermalLevel.NORMAL)

  override val thermalLevel: StateFlow<ThermalLevel> = _thermalLevel

  private val _renderConfig: MutableStateFlow<RenderConfig> = MutableStateFlow(RenderConfig.DEFAULT)

  override val renderConfig: StateFlow<RenderConfig> =
    if (scope != null) {
      _thermalLevel
        .map { it.toRenderConfig() }
        .stateIn(scope, SharingStarted.Eagerly, RenderConfig.DEFAULT)
    } else {
      // Synchronous fallback for simple test scenarios without a scope.
      // Backed by a manually-updated MutableStateFlow.
      _renderConfig
    }

  /** Set the thermal level. When no scope is provided, also updates renderConfig synchronously. */
  public fun setLevel(level: ThermalLevel) {
    _thermalLevel.value = level
    _renderConfig.value = level.toRenderConfig()
  }
}
