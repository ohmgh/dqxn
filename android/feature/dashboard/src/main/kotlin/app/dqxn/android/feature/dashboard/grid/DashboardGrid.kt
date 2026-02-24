package app.dqxn.android.feature.dashboard.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.feature.dashboard.binding.WidgetSlot
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.EditState
import app.dqxn.android.feature.dashboard.coordinator.WidgetAnimationState
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.ui.widget.GridConstants
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

/**
 * Dashboard grid composable rendering widgets via custom [Layout] + MeasurePolicy.
 *
 * NOT LazyLayout per CLAUDE.md. Uses explicit viewport culling (NF7): widgets fully outside the
 * viewport are filtered before composition, yielding zero render cost. Each widget is wrapped in
 * [graphicsLayer] for isolated RenderNode (NF1).
 *
 * Edit mode visuals: wiggle animation (+-0.5 degrees, 150ms) and bracket pulse (3-6dp, 800ms).
 * Both disabled when [ReducedMotionHelper.isReducedMotion] is true (NF39).
 *
 * Widget add/remove animations: fadeIn+scaleIn spring on add, fadeOut+scaleOut on remove (F1.21).
 */
@Composable
public fun DashboardGrid(
  widgets: ImmutableList<DashboardWidgetInstance>,
  viewportCols: Int,
  viewportRows: Int,
  editState: EditState,
  dragState: DragUpdate?,
  resizeState: ResizeUpdate?,
  configurationBoundaries: ImmutableList<ConfigurationBoundary>,
  widgetBindingCoordinator: app.dqxn.android.feature.dashboard.coordinator.WidgetBindingCoordinator,
  widgetRegistry: WidgetRegistry,
  editModeCoordinator: EditModeCoordinator,
  reducedMotionHelper: ReducedMotionHelper,
  widgetGestureHandler: WidgetGestureHandler,
  blankSpaceGestureHandler: BlankSpaceGestureHandler,
  onCommand: (app.dqxn.android.feature.dashboard.command.DashboardCommand) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val gridUnitPx = with(density) { GridConstants.GRID_UNIT_SIZE.toPx() }

  // NF7: viewport culling -- filter to visible widgets only
  val visibleWidgets = remember(widgets, viewportCols, viewportRows) {
    widgets.filter { widget ->
      widget.position.col < viewportCols &&
        widget.position.row < viewportRows &&
        widget.position.col + widget.size.widthUnits > 0 &&
        widget.position.row + widget.size.heightUnits > 0
    }
  }

  // Animation tracking
  val animatingWidgets by editModeCoordinator.animatingWidgets.collectAsState()

  // Edit mode wiggle animation
  val isEditMode = editState.isEditMode
  val isReducedMotion = reducedMotionHelper.isReducedMotion

  val blankSpaceModifier = with(blankSpaceGestureHandler) {
    Modifier.blankSpaceGestures()
  }

  Layout(
    content = {
      for (widget in visibleWidgets) {
        key(widget.instanceId) {
          val animState = animatingWidgets.find { it.widgetId == widget.instanceId }
          val isVisible = animState?.isRemoving != true

          AnimatedVisibility(
            visible = isVisible,
            enter = if (isReducedMotion) {
              fadeIn(snap()) + scaleIn(initialScale = 0.8f, animationSpec = snap())
            } else {
              fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(
                  initialScale = 0.8f,
                  animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                )
            },
            exit = if (isReducedMotion) {
              fadeOut(snap()) + scaleOut(targetScale = 0.8f, animationSpec = snap())
            } else {
              fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleOut(
                  targetScale = 0.8f,
                  animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                )
            },
          ) {
            // Per-widget graphicsLayer for isolated RenderNode (NF1)
            val widgetDragState = if (dragState?.widgetId == widget.instanceId) dragState else null

            // Wiggle rotation for edit mode (F1.11)
            val wiggleRotation = if (isEditMode && !isReducedMotion) {
              val infiniteTransition = rememberInfiniteTransition(label = "wiggle_${widget.instanceId}")
              val rotation by infiniteTransition.animateFloat(
                initialValue = -0.5f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(
                  animation = tween(durationMillis = 150),
                  repeatMode = RepeatMode.Reverse,
                ),
                label = "wiggle_rotation_${widget.instanceId}",
              )
              rotation
            } else {
              0f
            }

            // Bracket pulse for edit mode
            val bracketScale = if (isEditMode && !isReducedMotion) {
              val infiniteTransition = rememberInfiniteTransition(label = "bracket_${widget.instanceId}")
              val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                  animation = tween(durationMillis = 800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                  repeatMode = RepeatMode.Reverse,
                ),
                label = "bracket_scale_${widget.instanceId}",
              )
              scale
            } else {
              1f
            }

            // Apply gesture handler
            val widgetSpec = widgetRegistry.findByTypeId(widget.typeId)
            val gestureModifier = if (widgetSpec != null) {
              with(widgetGestureHandler) {
                Modifier.widgetGestures(
                  widgetId = widget.instanceId,
                  widgetSpec = widgetSpec,
                  currentPosition = widget.position,
                  currentSize = widget.size,
                  gridUnitPx = gridUnitPx,
                )
              }
            } else {
              Modifier
            }

            WidgetSlot(
              widget = widget,
              widgetBindingCoordinator = widgetBindingCoordinator,
              widgetRegistry = widgetRegistry,
              editModeCoordinator = editModeCoordinator,
              resizeState = resizeState,
              onCommand = onCommand,
              modifier = gestureModifier
                .graphicsLayer {
                  // Drag offset via graphicsLayer (NOT Modifier.offset)
                  if (widgetDragState != null) {
                    translationX = widgetDragState.currentOffsetX
                    translationY = widgetDragState.currentOffsetY
                  }
                  // Edit mode wiggle
                  rotationZ = wiggleRotation
                  // Bracket pulse
                  scaleX = bracketScale
                  scaleY = bracketScale
                },
            )
          }
        }
      }
    },
    modifier = modifier
      .semantics {}
      .testTag("dashboard_grid")
      .then(blankSpaceModifier),
  ) { measurables, constraints ->
    // Custom MeasurePolicy: each widget measured with constraints from widget.size * GRID_UNIT_SIZE
    val placeables = measurables.mapIndexed { index, measurable ->
      val widget = if (index < visibleWidgets.size) visibleWidgets[index] else return@Layout layout(0, 0) {}
      val widthPx = (widget.size.widthUnits * gridUnitPx).roundToInt()
      val heightPx = (widget.size.heightUnits * gridUnitPx).roundToInt()
      measurable.measure(
        Constraints.fixed(
          width = widthPx.coerceAtLeast(0),
          height = heightPx.coerceAtLeast(0),
        ),
      )
    }

    val layoutWidth = (viewportCols * gridUnitPx).roundToInt().coerceAtLeast(constraints.minWidth)
    val layoutHeight = (viewportRows * gridUnitPx).roundToInt().coerceAtLeast(constraints.minHeight)

    layout(layoutWidth, layoutHeight) {
      placeables.forEachIndexed { index, placeable ->
        if (index < visibleWidgets.size) {
          val widget = visibleWidgets[index]
          val x = (widget.position.col * gridUnitPx).roundToInt()
          val y = (widget.position.row * gridUnitPx).roundToInt()
          placeable.placeRelative(
            x = x,
            y = y,
            zIndex = widget.zIndex.toFloat(),
          )
        }
      }
    }
  }
}

/** Grid unit size constant exposed for test access. */
public object DashboardGridConstants {
  /** Size of one grid unit in dp. Same as [GridConstants.GRID_UNIT_SIZE]. */
  public val GRID_UNIT_SIZE_DP: androidx.compose.ui.unit.Dp = GridConstants.GRID_UNIT_SIZE
}
