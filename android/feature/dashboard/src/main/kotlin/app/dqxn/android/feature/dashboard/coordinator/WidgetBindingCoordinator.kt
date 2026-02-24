package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.core.thermal.ThermalMonitor
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.feature.dashboard.binding.WidgetDataBinder
import app.dqxn.android.feature.dashboard.safety.SafeModeManager
import app.dqxn.android.sdk.common.di.DefaultDispatcher
import app.dqxn.android.sdk.common.di.IoDispatcher
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.status.WidgetRenderState
import app.dqxn.android.sdk.contracts.status.WidgetStatusCache
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.debug
import app.dqxn.android.sdk.observability.log.error
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.persistentListOf

/**
 * Manages per-widget data binding lifecycle with [SupervisorJob] isolation.
 *
 * Each widget binding runs under an independent child job of the [bindingSupervisor]. One widget
 * crash does NOT cancel sibling bindings (NF19). Failed bindings retry with exponential backoff
 * (1s, 2s, 4s) up to [MAX_RETRIES] attempts (NF16). Crashes are reported to [SafeModeManager]
 * for cross-widget counting.
 *
 * Entitlement changes are observed reactively (NF18): when entitlements change, all widget statuses
 * are re-evaluated.
 */
public class WidgetBindingCoordinator
@Inject
constructor(
  private val binder: WidgetDataBinder,
  private val widgetRegistry: WidgetRegistry,
  private val safeModeManager: SafeModeManager,
  private val entitlementManager: EntitlementManager,
  private val thermalMonitor: ThermalMonitor,
  private val metricsCollector: MetricsCollector,
  private val logger: DqxnLogger,
  @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
  @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

  /** SupervisorJob parent for ALL binding jobs. One crash does NOT cancel siblings (NF19). */
  private val bindingSupervisor: Job = SupervisorJob()

  /** Per-widget binding jobs keyed by widget instanceId. */
  private val bindings: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

  /** Per-widget data flows keyed by widget instanceId. */
  private val _widgetData: ConcurrentHashMap<String, MutableStateFlow<WidgetData>> =
    ConcurrentHashMap()

  /** Per-widget status flows keyed by widget instanceId. */
  private val _widgetStatuses: ConcurrentHashMap<String, MutableStateFlow<WidgetStatusCache>> =
    ConcurrentHashMap()

  /** Per-widget error counts for retry logic. */
  private val errorCounts: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

  /** Binding scope: parent scope + SupervisorJob. Set via [initialize]. */
  private lateinit var bindingScope: CoroutineScope

  /** Widget instances currently bound (for rebinding on resume). */
  private val boundWidgets: ConcurrentHashMap<String, DashboardWidgetInstance> = ConcurrentHashMap()

  /**
   * Initialize the coordinator by setting up the binding scope and observing entitlement changes.
   * Must be called once from ViewModel init.
   */
  public fun initialize(scope: CoroutineScope) {
    bindingScope = CoroutineScope(scope.coroutineContext + bindingSupervisor + defaultDispatcher)

    // Observe entitlement changes and re-evaluate all widget statuses (NF18)
    entitlementManager.entitlementChanges
      .onEach { activeEntitlements ->
        logger.info(TAG) { "Entitlements changed: $activeEntitlements" }
        reevaluateEntitlements(activeEntitlements)
      }
      .launchIn(bindingScope)

    logger.info(TAG) { "WidgetBindingCoordinator initialized" }
  }

  /**
   * Bind a widget to its data providers. Cancels any existing binding for this widget, creates
   * a new job under [bindingScope] with [CoroutineExceptionHandler] for crash isolation.
   *
   * Resets the error count for a fresh start. For retries after errors, use [startBinding] directly.
   */
  public fun bind(widget: DashboardWidgetInstance) {
    // Reset error count for fresh (non-retry) binding
    errorCounts[widget.instanceId] = 0
    startBinding(widget)
  }

  /**
   * Internal binding entry point shared by [bind] and retry logic. Does NOT reset error count
   * so retries correctly track cumulative failures.
   */
  private fun startBinding(widget: DashboardWidgetInstance) {
    // Cancel existing binding if present
    unbindInternal(widget.instanceId)

    // Store widget for rebinding on resume
    boundWidgets[widget.instanceId] = widget

    // Ensure data and status flows exist
    val dataFlow = _widgetData.getOrPut(widget.instanceId) { MutableStateFlow(WidgetData.Empty) }
    val statusFlow =
      _widgetStatuses.getOrPut(widget.instanceId) { MutableStateFlow(WidgetStatusCache.EMPTY) }

    // Resolve compatible snapshots from the widget registry
    val renderer = widgetRegistry.findByTypeId(widget.typeId)
    if (renderer == null) {
      logger.warn(TAG) { "No renderer found for typeId '${widget.typeId}', setting ProviderMissing" }
      statusFlow.value =
        WidgetStatusCache(
          overlayState = WidgetRenderState.ProviderMissing,
          issues = persistentListOf(),
        )
      return
    }

    val compatibleSnapshots = renderer.compatibleSnapshots

    // Create the exception handler for this widget's binding job
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      handleBindingError(widget, throwable)
    }

    val job =
      bindingScope.launch(exceptionHandler) {
        val providerFlow = binder.bind(widget, compatibleSnapshots, thermalMonitor.renderConfig)

        providerFlow.collect { widgetData ->
          dataFlow.value = widgetData
          // Successful emission resets error count
          errorCounts[widget.instanceId] = 0
          // Update status to Ready on successful data
          if (widgetData.hasData()) {
            statusFlow.value = WidgetStatusCache.EMPTY // Ready state
          }
        }
      }

    bindings[widget.instanceId] = job
    logger.debug(TAG) { "Binding started for widget ${widget.instanceId} (${widget.typeId})" }
  }

  /** Unbind a widget: cancels job and removes from tracking maps. */
  public fun unbind(widgetId: String) {
    unbindInternal(widgetId)
    boundWidgets.remove(widgetId)
    _widgetData.remove(widgetId)
    _widgetStatuses.remove(widgetId)
    errorCounts.remove(widgetId)
    logger.debug(TAG) { "Widget unbound and cleaned up: $widgetId" }
  }

  /** Rebind a widget with a new provider type ID. */
  public fun rebind(widgetId: String, newProviderTypeId: String) {
    val widget = boundWidgets[widgetId] ?: return
    val updatedWidget =
      widget.copy(
        dataSourceBindings =
          widget.dataSourceBindings.let { bindings ->
            val mutable = bindings.toMutableMap()
            mutable["_override"] = newProviderTypeId
            kotlinx.collections.immutable.persistentMapOf(*mutable.entries.map { it.key to it.value }.toTypedArray())
          }
      )
    bind(updatedWidget)
    logger.info(TAG) { "Widget $widgetId rebound to provider $newProviderTypeId" }
  }

  /**
   * Returns a per-widget data flow. Creates an empty flow if the widget hasn't been bound yet.
   */
  public fun widgetData(widgetId: String): StateFlow<WidgetData> =
    _widgetData.getOrPut(widgetId) { MutableStateFlow(WidgetData.Empty) }.asStateFlow()

  /** Returns a per-widget status flow. */
  public fun widgetStatus(widgetId: String): StateFlow<WidgetStatusCache> =
    _widgetStatuses
      .getOrPut(widgetId) { MutableStateFlow(WidgetStatusCache.EMPTY) }
      .asStateFlow()

  /** Snapshot of all widget statuses for diagnostics. */
  public fun allStatuses(): Map<String, WidgetStatusCache> =
    _widgetStatuses.mapValues { it.value.value }

  /**
   * Pause all bindings (F1.14). Cancels all active jobs for CPU-heavy overlays.
   */
  public fun pauseAll() {
    for ((widgetId, job) in bindings) {
      job.cancel()
      logger.debug(TAG) { "Paused binding for $widgetId" }
    }
    bindings.clear()
    logger.info(TAG) { "All ${boundWidgets.size} bindings paused" }
  }

  /**
   * Resume all bindings (F1.14). Rebinds all previously bound widgets.
   */
  public fun resumeAll() {
    val widgets = boundWidgets.values.toList()
    for (widget in widgets) {
      bind(widget)
    }
    logger.info(TAG) { "Resumed ${widgets.size} bindings" }
  }

  /**
   * Report a widget crash. Delegates to [SafeModeManager] for cross-widget counting.
   * Called by DashboardViewModel on WidgetCrash command.
   */
  public fun reportCrash(widgetId: String, typeId: String) {
    safeModeManager.reportCrash(widgetId, typeId)
    logger.warn(TAG) { "Widget crash reported: $widgetId ($typeId)" }
  }

  /**
   * Cancel the binding supervisor and all child jobs.
   * Called from ViewModel.onCleared() to prevent leaks.
   */
  public fun destroy() {
    bindingSupervisor.cancel()
    bindings.clear()
    boundWidgets.clear()
    logger.info(TAG) { "WidgetBindingCoordinator destroyed" }
  }

  /** For test introspection: returns the current active binding jobs. */
  public fun activeBindings(): Map<String, Job> = bindings.toMap()

  /** Cancel binding without removing from boundWidgets (for internal pause/rebind). */
  private fun unbindInternal(widgetId: String) {
    bindings.remove(widgetId)?.cancel()
  }

  /**
   * Handle a binding error with exponential backoff retry (NF16).
   * Backoff: 1s, 2s, 4s. Max 3 attempts.
   */
  private fun handleBindingError(widget: DashboardWidgetInstance, throwable: Throwable) {
    val widgetId = widget.instanceId
    val count = (errorCounts[widgetId] ?: 0) + 1
    errorCounts[widgetId] = count

    // Report crash to SafeModeManager for cross-widget counting
    safeModeManager.reportCrash(widgetId, widget.typeId)

    if (count > MAX_RETRIES) {
      // Retries exhausted
      logger.error(TAG) {
        "Widget $widgetId (${widget.typeId}) retries exhausted after $MAX_RETRIES attempts: " +
          "${throwable.message}"
      }
      _widgetStatuses.getOrPut(widgetId) { MutableStateFlow(WidgetStatusCache.EMPTY) }.value =
        WidgetStatusCache(
          overlayState =
            WidgetRenderState.ConnectionError(
              message = "Provider error: retries exhausted"
            ),
          issues = persistentListOf(),
        )
      return
    }

    val backoffMs = BACKOFF_BASE_MS * (1 shl (count - 1)) // 1s, 2s, 4s
    logger.warn(TAG) {
      "Widget $widgetId (${widget.typeId}) error #$count/$MAX_RETRIES, " +
        "retrying in ${backoffMs}ms: ${throwable.message}"
    }

    // Schedule retry with backoff — uses startBinding to preserve error count
    bindingScope.launch {
      delay(backoffMs)
      if (boundWidgets.containsKey(widgetId)) {
        startBinding(widget)
      }
    }
  }

  /**
   * Re-evaluate entitlement status for all bound widgets (NF18).
   * Widgets requiring entitlements the user no longer holds get EntitlementRevoked status.
   */
  private fun reevaluateEntitlements(activeEntitlements: Set<String>) {
    for ((widgetId, widget) in boundWidgets) {
      val renderer = widgetRegistry.findByTypeId(widget.typeId) ?: continue
      val required = renderer.requiredAnyEntitlement

      if (!required.isNullOrEmpty() && required.none { activeEntitlements.contains(it) }) {
        _widgetStatuses.getOrPut(widgetId) { MutableStateFlow(WidgetStatusCache.EMPTY) }.value =
          WidgetStatusCache(
            overlayState =
              WidgetRenderState.EntitlementRevoked(
                upgradeEntitlement = required.first()
              ),
            issues = persistentListOf(),
          )
        logger.info(TAG) {
          "Widget $widgetId (${widget.typeId}) entitlement revoked: requires $required"
        }
      } else {
        // Restore to ready if previously revoked
        val currentStatus = _widgetStatuses[widgetId]?.value
        if (currentStatus?.overlayState is WidgetRenderState.EntitlementRevoked) {
          _widgetStatuses[widgetId]?.value = WidgetStatusCache.EMPTY
          logger.info(TAG) { "Widget $widgetId (${widget.typeId}) entitlement restored" }
        }
      }
    }
  }

  internal companion object {
    val TAG: LogTag = LogTag("BindingCoord")
    internal const val MAX_RETRIES: Int = 3
    internal const val BACKOFF_BASE_MS: Long = 1000L
  }
}
