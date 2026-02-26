package app.dqxn.android.feature.dashboard.coordinator

import androidx.compose.runtime.Immutable
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.feature.dashboard.gesture.DashboardHaptics
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.feature.dashboard.grid.DragUpdate
import app.dqxn.android.feature.dashboard.grid.GridPlacementEngine
import app.dqxn.android.feature.dashboard.grid.ResizeHandle
import app.dqxn.android.feature.dashboard.grid.ResizeUpdate
import app.dqxn.android.sdk.contracts.widget.WidgetSpec
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.debug
import app.dqxn.android.sdk.observability.log.info
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Edit mode state: edit/focus flag, focused widget, and status bar visibility.
 *
 * Continuous gesture flows ([DragUpdate], [ResizeUpdate]) are separate StateFlows because they
 * update at different frequencies than discrete edit state.
 */
@Immutable
public data class EditState(
  val isEditMode: Boolean = false,
  val focusedWidgetId: String? = null,
  val showStatusBar: Boolean = false,
)

/**
 * Animation state for a widget being added or removed. Read by DashboardGrid composable (Plan 06)
 * to apply fadeIn+scaleIn / fadeOut+scaleOut spring animations.
 */
@Immutable
public data class WidgetAnimationState(
  val widgetId: String,
  val isAdding: Boolean,
  val isRemoving: Boolean,
)

/**
 * Coordinator for edit mode, widget focus, drag/resize gesture state, and widget animations.
 *
 * Owns discrete [EditState] via [StateFlow] and continuous gesture flows ([dragState],
 * [resizeState]) as separate nullable StateFlows. All drag/resize data uses pixel offsets for
 * `graphicsLayer` — grid snapping happens only on gesture completion.
 */
public class EditModeCoordinator
@Inject
constructor(
  private val layoutCoordinator: LayoutCoordinator,
  private val gridPlacementEngine: GridPlacementEngine,
  private val haptics: DashboardHaptics,
  private val reducedMotionHelper: ReducedMotionHelper,
  private val userPreferencesRepository: UserPreferencesRepository,
  private val logger: DqxnLogger,
) {

  private lateinit var scope: CoroutineScope

  /**
   * Initialize with a ViewModel-scoped coroutine scope for persistence operations (endDrag,
   * endResize). Must be called once from ViewModel init.
   */
  public fun initialize(scope: CoroutineScope) {
    this.scope = scope
    scope.launch {
      userPreferencesRepository.showStatusBar.collect { visible ->
        _editState.update { it.copy(showStatusBar = visible) }
      }
    }
    logger.info(TAG) { "EditModeCoordinator initialized" }
  }

  private val _editState: MutableStateFlow<EditState> = MutableStateFlow(EditState())

  /** Discrete edit mode state. */
  public val editState: StateFlow<EditState> = _editState.asStateFlow()

  // -- Continuous gesture flows --

  private val _dragState: MutableStateFlow<DragUpdate?> = MutableStateFlow(null)

  /** Current drag gesture state. Null when no drag is in progress. */
  public val dragState: StateFlow<DragUpdate?> = _dragState.asStateFlow()

  private val _resizeState: MutableStateFlow<ResizeUpdate?> = MutableStateFlow(null)

  /** Current resize gesture state. Null when no resize is in progress. */
  public val resizeState: StateFlow<ResizeUpdate?> = _resizeState.asStateFlow()

  // -- Widget animation tracking --

  private val _animatingWidgets: MutableStateFlow<Set<WidgetAnimationState>> =
    MutableStateFlow(emptySet())

  /** Widgets currently animating in or out. */
  public val animatingWidgets: StateFlow<Set<WidgetAnimationState>> =
    _animatingWidgets.asStateFlow()

  // -- Drag bookkeeping --

  private var dragStartWidgetId: String? = null
  private var dragStartCol: Int = 0
  private var dragStartRow: Int = 0

  // -- Resize bookkeeping --

  private var resizeWidgetId: String? = null
  private var resizeHandle: ResizeHandle = ResizeHandle.BOTTOM_RIGHT
  private var resizeOriginalSize: GridSize = GridSize(2, 2)
  private var resizeOriginalPosition: GridPosition = GridPosition(0, 0)
  private var resizeWidgetSpec: WidgetSpec? = null

  // -- Edit mode --

  /** Enter edit mode. Clears any focused widget and fires haptic feedback. */
  public fun enterEditMode() {
    _editState.update { it.copy(isEditMode = true, focusedWidgetId = null) }
    haptics.editModeEnter()
    logger.info(TAG) { "Edit mode entered" }
  }

  /** Exit edit mode. Clears focus and fires haptic feedback. */
  public fun exitEditMode() {
    _editState.update { it.copy(isEditMode = false, focusedWidgetId = null) }
    haptics.editModeExit()
    logger.info(TAG) { "Edit mode exited" }
  }

  /**
   * Focus a widget by [widgetId], or clear focus with null. Fires haptic feedback when focusing
   * (non-null).
   */
  public fun focusWidget(widgetId: String?) {
    _editState.update { it.copy(focusedWidgetId = widgetId) }
    if (widgetId != null) {
      haptics.widgetFocus()
    }
    logger.debug(TAG) { "Focus: $widgetId" }
  }

  // -- Drag --

  /**
   * Begin a drag gesture for [widgetId] from the widget's current grid position. The [startCol]
   * and [startRow] are the widget's grid coordinates at drag start — used to compute final snap
   * position.
   */
  public fun startDrag(widgetId: String, startCol: Int, startRow: Int) {
    dragStartWidgetId = widgetId
    dragStartCol = startCol
    dragStartRow = startRow
    _dragState.value =
      DragUpdate(widgetId = widgetId, currentOffsetX = 0f, currentOffsetY = 0f, isDragging = true)
    haptics.dragStart()
    logger.debug(TAG) { "Drag start: $widgetId at ($startCol, $startRow)" }
  }

  /**
   * Update the drag offset in pixels. These values are applied directly to `graphicsLayer {
   * translationX / translationY }` — NOT layout position.
   */
  public fun updateDrag(offsetX: Float, offsetY: Float) {
    val currentId = dragStartWidgetId ?: return
    _dragState.value =
      DragUpdate(widgetId = currentId, currentOffsetX = offsetX, currentOffsetY = offsetY, isDragging = true)
  }

  /**
   * End the drag gesture. Snaps to the nearest grid position via [GridPlacementEngine.snapToGrid],
   * enforces no-straddle via [GridPlacementEngine.enforceNoStraddle], and commits the final
   * position via [LayoutCoordinator.handleMoveWidget].
   *
   * Non-suspend: persistence is launched on the ViewModel scope provided via [initialize]. This
   * allows callers in restricted suspension scopes (e.g., [AwaitPointerEventScope]) to call
   * directly.
   *
   * @param gridUnitPx The pixel size of one grid unit (for snap calculation).
   * @return The committed [GridPosition], or null if no drag was active.
   */
  public fun endDrag(gridUnitPx: Float): GridPosition? {
    val widgetId = dragStartWidgetId ?: return null
    val drag = _dragState.value ?: return null

    // Convert pixel offset to absolute pixel position for snap
    val absolutePixelX = dragStartCol * gridUnitPx + drag.currentOffsetX
    val absolutePixelY = dragStartRow * gridUnitPx + drag.currentOffsetY

    // Snap to grid
    var snappedPosition = gridPlacementEngine.snapToGrid(absolutePixelX, absolutePixelY, gridUnitPx)

    // Find the widget's size for no-straddle enforcement
    val widget = layoutCoordinator.layoutState.value.widgets.find { it.instanceId == widgetId }
    if (widget != null) {
      val boundaries = layoutCoordinator.configurationBoundaries.value
      snappedPosition = gridPlacementEngine.enforceNoStraddle(snappedPosition, widget.size, boundaries)
    }

    // Commit position asynchronously via ViewModel scope
    val finalPosition = snappedPosition
    scope.launch { layoutCoordinator.handleMoveWidget(widgetId, finalPosition) }

    // Clear drag state
    _dragState.value = null
    dragStartWidgetId = null

    haptics.snapToGrid()
    logger.info(TAG) { "Drag end: $widgetId -> $snappedPosition" }

    return snappedPosition
  }

  // -- Resize --

  /**
   * Begin a resize gesture for [widgetId] from the given [handle]. Captures the widget's current
   * size and position for delta computation.
   *
   * @param widgetSpec Optional widget spec for aspect ratio enforcement. If the spec declares an
   *   [WidgetSpec.aspectRatio], resize will maintain it.
   */
  public fun startResize(
    widgetId: String,
    handle: ResizeHandle,
    currentSize: GridSize,
    currentPosition: GridPosition,
    widgetSpec: WidgetSpec? = null,
  ) {
    resizeWidgetId = widgetId
    resizeHandle = handle
    resizeOriginalSize = currentSize
    resizeOriginalPosition = currentPosition
    resizeWidgetSpec = widgetSpec

    _resizeState.value =
      ResizeUpdate(
        widgetId = widgetId,
        handle = handle,
        targetSize = currentSize,
        targetPosition = null,
        isResizing = true,
      )

    haptics.resizeStart()
    logger.debug(TAG) { "Resize start: $widgetId handle=$handle size=$currentSize" }
  }

  /**
   * Update the resize by [deltaWidthUnits] and [deltaHeightUnits] grid units relative to the
   * original size at resize start.
   *
   * Position compensation per replication advisory section 6:
   * - [ResizeHandle.TOP_LEFT]: gridX -= deltaWidth, gridY -= deltaHeight
   * - [ResizeHandle.TOP_RIGHT]: gridY -= deltaHeight
   * - [ResizeHandle.BOTTOM_LEFT]: gridX -= deltaWidth
   * - [ResizeHandle.BOTTOM_RIGHT]: no position change
   *
   * Enforces [MIN_WIDGET_UNITS] minimum and aspect ratio from [WidgetSpec] if declared.
   */
  public fun updateResize(deltaWidthUnits: Int, deltaHeightUnits: Int) {
    val widgetId = resizeWidgetId ?: return

    var newWidth = (resizeOriginalSize.widthUnits + deltaWidthUnits).coerceAtLeast(MIN_WIDGET_UNITS)
    var newHeight = (resizeOriginalSize.heightUnits + deltaHeightUnits).coerceAtLeast(MIN_WIDGET_UNITS)

    // Enforce aspect ratio if declared (F2.16)
    val aspectRatio = resizeWidgetSpec?.aspectRatio
    if (aspectRatio != null && aspectRatio > 0f) {
      // Use the dominant dimension change to drive the constrained dimension
      val widthDelta = kotlin.math.abs(deltaWidthUnits)
      val heightDelta = kotlin.math.abs(deltaHeightUnits)

      if (widthDelta >= heightDelta) {
        // Width is dominant — compute height from width
        newHeight = (newWidth / aspectRatio).toInt().coerceAtLeast(MIN_WIDGET_UNITS)
      } else {
        // Height is dominant — compute width from height
        newWidth = (newHeight * aspectRatio).toInt().coerceAtLeast(MIN_WIDGET_UNITS)
      }
    }

    val actualDeltaWidth = newWidth - resizeOriginalSize.widthUnits
    val actualDeltaHeight = newHeight - resizeOriginalSize.heightUnits

    // Position compensation for non-BottomRight handles
    val targetPosition = when (resizeHandle) {
      ResizeHandle.TOP_LEFT -> GridPosition(
        col = resizeOriginalPosition.col - actualDeltaWidth,
        row = resizeOriginalPosition.row - actualDeltaHeight,
      )
      ResizeHandle.TOP_RIGHT -> GridPosition(
        col = resizeOriginalPosition.col,
        row = resizeOriginalPosition.row - actualDeltaHeight,
      )
      ResizeHandle.BOTTOM_LEFT -> GridPosition(
        col = resizeOriginalPosition.col - actualDeltaWidth,
        row = resizeOriginalPosition.row,
      )
      ResizeHandle.BOTTOM_RIGHT -> null
    }

    _resizeState.value = ResizeUpdate(
      widgetId = widgetId,
      handle = resizeHandle,
      targetSize = GridSize(newWidth, newHeight),
      targetPosition = targetPosition,
      isResizing = true,
    )
  }

  /**
   * End the resize gesture. Commits the final size and position via
   * [LayoutCoordinator.handleResizeWidget] and clears resize state.
   *
   * Non-suspend: persistence is launched on the ViewModel scope provided via [initialize].
   */
  public fun endResize() {
    val widgetId = resizeWidgetId ?: return
    val resize = _resizeState.value ?: return

    scope.launch {
      layoutCoordinator.handleResizeWidget(widgetId, resize.targetSize, resize.targetPosition)
    }

    _resizeState.value = null
    resizeWidgetId = null
    resizeWidgetSpec = null

    logger.info(TAG) { "Resize end: $widgetId -> ${resize.targetSize}" }
  }

  // -- Status bar --

  /** Toggle the status bar visibility. Called by DashboardViewModel on ToggleStatusBar command. */
  public fun toggleStatusBar() {
    val nextValue = !_editState.value.showStatusBar
    _editState.update { it.copy(showStatusBar = nextValue) }
    if (::scope.isInitialized) {
      scope.launch {
        userPreferencesRepository.setShowStatusBar(nextValue)
      }
    }
    logger.debug(TAG) { "Status bar toggled: $nextValue" }
  }

  // -- Widget animations (F1.21) --

  /**
   * Track a widget being added for fadeIn + scaleIn spring animation. The animation composable in
   * Plan 06 reads [animatingWidgets] and removes the entry when animation completes.
   */
  public fun handleWidgetAdded(widgetId: String) {
    _animatingWidgets.update { current ->
      current + WidgetAnimationState(widgetId = widgetId, isAdding = true, isRemoving = false)
    }
    logger.debug(TAG) { "Widget add animation: $widgetId" }
  }

  /**
   * Track a widget being removed for fadeOut + scaleOut animation. The animation composable in Plan
   * 06 reads [animatingWidgets] and removes the entry when animation completes.
   */
  public fun handleWidgetRemoved(widgetId: String) {
    _animatingWidgets.update { current ->
      current + WidgetAnimationState(widgetId = widgetId, isAdding = false, isRemoving = true)
    }
    logger.debug(TAG) { "Widget remove animation: $widgetId" }
  }

  /** Remove animation tracking for a widget after its animation has completed. */
  public fun clearWidgetAnimation(widgetId: String) {
    _animatingWidgets.update { current -> current.filter { it.widgetId != widgetId }.toSet() }
  }

  // -- Interaction gating (F2.18) --

  /**
   * Whether interactive widget actions (e.g., Shortcuts tap) are allowed for [widgetId]. Returns
   * true only if NOT in edit mode AND the widget is NOT focused. Focused widget shows overlay
   * toolbar instead of passing through taps.
   */
  public fun isInteractionAllowed(widgetId: String): Boolean {
    val state = _editState.value
    return !state.isEditMode && state.focusedWidgetId != widgetId
  }

  public companion object {
    private val TAG = LogTag("EditMode")

    /** Minimum widget size in grid units for resize operations. */
    public const val MIN_WIDGET_UNITS: Int = 2
  }
}
