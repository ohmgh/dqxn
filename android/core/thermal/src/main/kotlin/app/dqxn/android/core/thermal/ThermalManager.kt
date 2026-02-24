package app.dqxn.android.core.thermal

import android.content.Context
import android.os.PowerManager
import app.dqxn.android.sdk.common.di.ApplicationScope
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Monitors device thermal state via [PowerManager.addThermalStatusListener] and
 * [PowerManager.getThermalHeadroom]. Emits [ThermalLevel] transitions and derives [RenderConfig]
 * for downstream consumers.
 *
 * Call [start] once from Application.onCreate to begin monitoring. Preemptive warm detection: if
 * thermal headroom is below [HEADROOM_WARM_THRESHOLD] but status reports NONE/LIGHT, the level is
 * elevated to [ThermalLevel.WARM] proactively.
 */
@Singleton
public class ThermalManager
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val logger: DqxnLogger,
  @ApplicationScope private val scope: CoroutineScope,
) : ThermalMonitor {

  private val _thermalLevel: MutableStateFlow<ThermalLevel> = MutableStateFlow(ThermalLevel.NORMAL)

  override val thermalLevel: StateFlow<ThermalLevel> = _thermalLevel.asStateFlow()

  override val renderConfig: StateFlow<RenderConfig> =
    _thermalLevel
      .map { level -> level.toRenderConfig() }
      .stateIn(scope, SharingStarted.Eagerly, RenderConfig.DEFAULT)

  /**
   * Registers the thermal status listener and begins monitoring. Must be called once from
   * Application.onCreate or a similarly early lifecycle point.
   */
  public fun start() {
    val pm = context.getSystemService(PowerManager::class.java) ?: return
    pm.addThermalStatusListener(context.mainExecutor) { status ->
      val mapped = mapThermalStatus(status)
      val level = applyHeadroomOverride(pm, mapped)
      if (_thermalLevel.value != level) {
        logger.info(TAG) { "Thermal transition: ${_thermalLevel.value} -> $level (status=$status)" }
        _thermalLevel.value = level
      }
    }
    logger.info(TAG) { "Thermal monitoring started" }
  }

  private fun applyHeadroomOverride(pm: PowerManager, statusLevel: ThermalLevel): ThermalLevel {
    if (statusLevel != ThermalLevel.NORMAL) return statusLevel
    val headroom = pm.getThermalHeadroom(HEADROOM_FORECAST_SECONDS)
    if (headroom > 0f && headroom < HEADROOM_WARM_THRESHOLD) {
      logger.warn(TAG) { "Preemptive WARM: headroom=$headroom < $HEADROOM_WARM_THRESHOLD" }
      return ThermalLevel.WARM
    }
    return statusLevel
  }

  internal companion object {
    val TAG: LogTag = LogTag("Thermal")

    /** Forecast window in seconds for getThermalHeadroom. */
    const val HEADROOM_FORECAST_SECONDS: Int = 10

    /** Headroom threshold below which we preemptively elevate to WARM. */
    const val HEADROOM_WARM_THRESHOLD: Float = 0.3f

    fun mapThermalStatus(status: Int): ThermalLevel =
      when (status) {
        PowerManager.THERMAL_STATUS_NONE,
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.NORMAL
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.WARM
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.DEGRADED
        // EMERGENCY, SHUTDOWN, or any unknown future status
        else -> ThermalLevel.CRITICAL
      }
  }
}

/** Maps a [ThermalLevel] to its corresponding [RenderConfig]. */
internal fun ThermalLevel.toRenderConfig(): RenderConfig =
  when (this) {
    ThermalLevel.NORMAL ->
      RenderConfig(targetFps = 60f, glowEnabled = true, useGradientFallback = false)
    ThermalLevel.WARM ->
      RenderConfig(targetFps = 45f, glowEnabled = true, useGradientFallback = false)
    ThermalLevel.DEGRADED ->
      RenderConfig(targetFps = 30f, glowEnabled = false, useGradientFallback = true)
    ThermalLevel.CRITICAL ->
      RenderConfig(targetFps = 24f, glowEnabled = false, useGradientFallback = true)
  }
