package app.dqxn.android.feature.dashboard.binding

import app.dqxn.android.core.thermal.RenderConfig
import app.dqxn.android.core.thermal.ThermalMonitor
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataProviderInterceptor
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.debug
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan

/**
 * IoC data binding engine that connects data providers to widgets via merge+scan.
 *
 * For each widget, resolves providers for each compatible snapshot type, applies the interceptor
 * chain, throttles by thermal config, merges all provider flows, and scans into [WidgetData] with
 * per-type slots.
 *
 * Provider resolution priority (F3.10): user-selected > HARDWARE > DEVICE_SENSOR > NETWORK > SIMULATED.
 * Fallback: if the assigned provider is unavailable, falls back to the next available by priority.
 */
public class WidgetDataBinder
@Inject
constructor(
  private val providerRegistry: DataProviderRegistry,
  private val interceptors: Set<@JvmSuppressWildcards DataProviderInterceptor>,
  private val thermalMonitor: ThermalMonitor,
  private val logger: DqxnLogger,
) {

  /**
   * Bind a widget to its data providers and return a flow of [WidgetData].
   *
   * @param widget The widget instance to bind.
   * @param renderConfig Current render config (thermal-aware).
   * @param widgetRegistry Used to look up WidgetRenderer for compatible snapshot types.
   * @param compatibleSnapshots The snapshot types this widget can render.
   * @return Flow of [WidgetData] accumulated from all bound providers.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  public fun bind(
    widget: DashboardWidgetInstance,
    compatibleSnapshots: Set<KClass<out DataSnapshot>>,
    renderConfig: StateFlow<RenderConfig>,
  ): Flow<WidgetData> {
    if (compatibleSnapshots.isEmpty()) {
      logger.debug(TAG) { "Widget ${widget.typeId} has no compatible snapshots, emitting empty" }
      return flow { emit(WidgetData.Empty) }
    }

    // For each snapshot type, resolve a provider and create a typed flow
    val providerFlows: List<Flow<Pair<KClass<out DataSnapshot>, DataSnapshot>>> =
      compatibleSnapshots.mapNotNull { snapshotType ->
        val userSelectedId = widget.dataSourceBindings[snapshotType.simpleName ?: ""]
        val provider = resolveProvider(snapshotType, userSelectedId)

        if (provider == null) {
          logger.warn(TAG) {
            "No provider found for snapshot type ${snapshotType.simpleName} " +
              "on widget ${widget.typeId}"
          }
          return@mapNotNull null
        }

        logger.info(TAG) {
          "Binding ${widget.typeId} slot ${snapshotType.simpleName} -> ${provider.sourceId}"
        }

        // Get the raw provider flow, apply interceptors, then throttle.
        // Unchecked cast: provider is DataProvider<*> from resolveProvider, but we know it
        // produces DataSnapshot subtypes. The interceptor chain is type-safe internally.
        @Suppress("UNCHECKED_CAST")
        val typedProvider = provider as DataProvider<DataSnapshot>
        val rawFlow = typedProvider.provideState()
        val interceptedFlow = applyInterceptors(typedProvider, rawFlow)

        // Throttle by thermal-aware render config
        renderConfig
          .flatMapLatest { config ->
            val intervalMs = (1000f / config.targetFps).toLong().coerceAtLeast(16L)
            throttle(interceptedFlow, intervalMs)
          }
          .map { snapshot -> snapshotType to snapshot }
      }

    if (providerFlows.isEmpty()) {
      return flow { emit(WidgetData.Empty) }
    }

    // Merge all typed flows and scan-accumulate into WidgetData
    return providerFlows
      .merge()
      .scan(WidgetData.Empty) { accumulated, (type, snapshot) ->
        accumulated.withSlot(type, snapshot)
      }
      .onEach { data ->
        logger.debug(TAG) { "Widget ${widget.typeId} data updated: ${data.snapshots.size} slots" }
      }
  }

  /**
   * Resolve a provider for the given snapshot type using priority ordering (F3.10).
   *
   * Priority: user-selected > HARDWARE > DEVICE_SENSOR > NETWORK > SIMULATED.
   * If user-selected is unavailable, falls back to next by priority.
   */
  public fun resolveProvider(
    snapshotType: KClass<out DataSnapshot>,
    userSelectedProviderId: String? = null,
  ): DataProvider<*>? {
    val allProviders = providerRegistry.getAll()

    // Filter providers compatible with this snapshot type
    val compatible =
      allProviders.filter { provider -> provider.snapshotType == snapshotType }

    if (compatible.isEmpty()) return null

    // If user selected a specific provider, try that first
    if (userSelectedProviderId != null) {
      val userSelected = compatible.find { it.sourceId == userSelectedProviderId && it.isAvailable }
      if (userSelected != null) return userSelected

      logger.info(TAG) {
        "User-selected provider $userSelectedProviderId unavailable for " +
          "${snapshotType.simpleName}, falling back to priority order"
      }
    }

    // Fall back to priority ordering: HARDWARE > DEVICE_SENSOR > NETWORK > SIMULATED
    val priorityOrder =
      listOf(
        ProviderPriority.HARDWARE,
        ProviderPriority.DEVICE_SENSOR,
        ProviderPriority.NETWORK,
        ProviderPriority.SIMULATED,
      )

    for (priority in priorityOrder) {
      val candidate = compatible.find { it.priority == priority && it.isAvailable }
      if (candidate != null) return candidate
    }

    // Last resort: any compatible provider regardless of availability
    return compatible.firstOrNull()
  }

  private fun <T : DataSnapshot> applyInterceptors(
    provider: DataProvider<T>,
    upstream: Flow<T>,
  ): Flow<T> {
    var current = upstream
    for (interceptor in interceptors) {
      current = interceptor.intercept(provider, current)
    }
    return current
  }

  private fun <T> throttle(upstream: Flow<T>, intervalMs: Long): Flow<T> = flow {
    var lastEmitTime = 0L
    upstream.collect { value ->
      val now = System.currentTimeMillis()
      if (now - lastEmitTime >= intervalMs) {
        lastEmitTime = now
        emit(value)
      }
    }
  }

  internal companion object {
    val TAG: LogTag = LogTag("WidgetBinder")
  }
}
