package app.dqxn.android.feature.dashboard.coordinator

import androidx.compose.runtime.Immutable
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.preset.PresetLoader
import app.dqxn.android.feature.dashboard.grid.ConfigurationBoundary
import app.dqxn.android.feature.dashboard.grid.ConfigurationBoundaryDetector
import app.dqxn.android.feature.dashboard.grid.GridPlacementEngine
import app.dqxn.android.sdk.common.di.IoDispatcher
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.debug
import app.dqxn.android.sdk.observability.log.info
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Structural layout state: widget list, loading indicator, and active profile.
 *
 * Each update creates a new instance with [ImmutableList] — Compose stability is guaranteed via
 * [@Immutable] annotation and kotlinx immutable collections.
 */
@Immutable
public data class LayoutState(
  val widgets: ImmutableList<DashboardWidgetInstance> = persistentListOf(),
  val isLoading: Boolean = true,
  val activeProfileId: String = "",
)

/**
 * Coordinator for canvas positioning, viewport culling, widget CRUD, and grid placement.
 *
 * Owns its own [StateFlow] slice per the decomposed state architecture. All disk I/O runs on the
 * [ioDispatcher]. Layout persistence is handled by [LayoutRepository] which already debounces at
 * 500ms (Phase 5).
 */
public class LayoutCoordinator
@Inject
constructor(
  private val layoutRepository: LayoutRepository,
  private val presetLoader: PresetLoader,
  private val gridPlacementEngine: GridPlacementEngine,
  private val configurationBoundaryDetector: ConfigurationBoundaryDetector,
  @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
  private val logger: DqxnLogger,
) {

  private val _layoutState: MutableStateFlow<LayoutState> = MutableStateFlow(LayoutState())

  /** The current layout state. Collected by DashboardScreen and DashboardGrid. */
  public val layoutState: StateFlow<LayoutState> = _layoutState.asStateFlow()

  /**
   * Configuration boundaries exposed for DashboardGrid (no-straddle enforcement) and DashboardLayer
   * (edit-mode boundary visualization). Delegates to [ConfigurationBoundaryDetector.boundaries].
   */
  public val configurationBoundaries: StateFlow<ImmutableList<ConfigurationBoundary>>
    get() = configurationBoundaryDetector.boundaries

  /**
   * Initialize the coordinator by observing the layout repository for widget changes. Must be
   * called once from ViewModel init with the ViewModel's coroutine scope.
   */
  public fun initialize(scope: CoroutineScope) {
    scope.launch {
      withContext(ioDispatcher) {
        layoutRepository.getActiveProfileWidgets().collect { widgets ->
          _layoutState.update { state ->
            state.copy(widgets = widgets, isLoading = false)
          }
          logger.debug(TAG) { "Layout loaded: ${widgets.size} widgets" }
        }
      }
    }

    scope.launch {
      layoutRepository.activeProfileId.collect { profileId ->
        _layoutState.update { it.copy(activeProfileId = profileId) }
      }
    }

    logger.info(TAG) { "LayoutCoordinator initialized" }
  }

  /**
   * Add a widget to the layout. Uses [GridPlacementEngine] to find an optimal position respecting
   * no-straddle snap and existing widget overlap.
   */
  public suspend fun handleAddWidget(
    widget: DashboardWidgetInstance,
    viewportCols: Int = DEFAULT_VIEWPORT_COLS,
    viewportRows: Int = DEFAULT_VIEWPORT_ROWS,
  ) {
    val current = _layoutState.value.widgets
    val boundaries = configurationBoundaries.value

    val optimalPosition =
      gridPlacementEngine.findOptimalPosition(
        widget = widget,
        existingWidgets = current,
        viewportCols = viewportCols,
        viewportRows = viewportRows,
        configBoundaries = boundaries,
      )

    val positioned = widget.copy(position = optimalPosition)
    _layoutState.update { state ->
      state.copy(widgets = (state.widgets + positioned).toImmutableList())
    }

    withContext(ioDispatcher) { layoutRepository.addWidget(positioned) }

    logger.info(TAG) { "Widget added: ${widget.typeId} at $optimalPosition" }
  }

  /** Remove a widget from the layout by instance ID. */
  public suspend fun handleRemoveWidget(widgetId: String) {
    _layoutState.update { state ->
      state.copy(widgets = state.widgets.filter { it.instanceId != widgetId }.toImmutableList())
    }

    withContext(ioDispatcher) { layoutRepository.removeWidget(widgetId) }

    logger.info(TAG) { "Widget removed: $widgetId" }
  }

  /** Move a widget to a new grid position. */
  public suspend fun handleMoveWidget(widgetId: String, position: GridPosition) {
    _layoutState.update { state ->
      state.copy(
        widgets =
          state.widgets
            .map { if (it.instanceId == widgetId) it.copy(position = position) else it }
            .toImmutableList()
      )
    }

    withContext(ioDispatcher) { layoutRepository.updateWidgetPosition(widgetId, position) }

    logger.debug(TAG) { "Widget moved: $widgetId -> $position" }
  }

  /**
   * Resize a widget to a new size, optionally adjusting position (for non-BottomRight resize
   * handles where the origin shifts).
   */
  public suspend fun handleResizeWidget(
    widgetId: String,
    size: GridSize,
    position: GridPosition?,
  ) {
    _layoutState.update { state ->
      state.copy(
        widgets =
          state.widgets
            .map { widget ->
              if (widget.instanceId == widgetId) {
                widget.copy(
                  size = size,
                  position = position ?: widget.position,
                )
              } else {
                widget
              }
            }
            .toImmutableList()
      )
    }

    withContext(ioDispatcher) {
      val current =
        _layoutState.value.widgets.find { it.instanceId == widgetId } ?: return@withContext
      layoutRepository.updateWidget(current)
    }

    logger.debug(TAG) { "Widget resized: $widgetId -> $size" }
  }

  /** Reset the layout by reloading from the preset loader. */
  public suspend fun handleResetLayout() {
    val presetWidgets = withContext(ioDispatcher) { presetLoader.loadPreset() }

    _layoutState.update { state ->
      state.copy(widgets = presetWidgets.toImmutableList())
    }

    // Persist each preset widget
    withContext(ioDispatcher) {
      // Clear existing layout by removing all, then adding preset widgets
      val currentWidgets = _layoutState.value.widgets
      for (widget in currentWidgets) {
        layoutRepository.removeWidget(widget.instanceId)
      }
      for (widget in presetWidgets) {
        layoutRepository.addWidget(widget)
      }
    }

    logger.info(TAG) { "Layout reset to preset (${presetWidgets.size} widgets)" }
  }

  /**
   * Filter widgets visible within the given viewport dimensions. Widgets fully outside the viewport
   * are excluded per NF7 viewport culling.
   */
  public fun visibleWidgets(
    viewportCols: Int,
    viewportRows: Int,
  ): ImmutableList<DashboardWidgetInstance> =
    _layoutState.value.widgets
      .filter { widget ->
        widget.position.col < viewportCols &&
          widget.position.row < viewportRows &&
          widget.position.col + widget.size.widthUnits > 0 &&
          widget.position.row + widget.size.heightUnits > 0
      }
      .toImmutableList()

  private companion object {
    val TAG = LogTag("LayoutCoord")
    const val DEFAULT_VIEWPORT_COLS: Int = 20
    const val DEFAULT_VIEWPORT_ROWS: Int = 12
  }
}
