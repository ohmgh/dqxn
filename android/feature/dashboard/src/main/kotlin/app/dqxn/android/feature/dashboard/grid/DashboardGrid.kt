package app.dqxn.android.feature.dashboard.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.feature.dashboard.binding.WidgetSlot
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.EditState
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.ui.widget.GridConstants
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList

/**
 * Dashboard grid composable rendering widgets via custom [Layout] + MeasurePolicy.
 *
 * NOT LazyLayout per CLAUDE.md. Uses explicit viewport culling (NF7): widgets fully outside the
 * viewport are filtered before composition, yielding zero render cost. Each widget is wrapped in
 * [graphicsLayer] for isolated RenderNode (NF1).
 *
 * Edit mode visuals: wiggle animation (+-0.5 degrees, 150ms) and Canvas bracket stroke pulse
 * (3-6dp, 800ms). Both disabled when [ReducedMotionHelper.isReducedMotion] is true (NF39).
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
  val visibleWidgets =
    remember(widgets, viewportCols, viewportRows) {
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

  val blankSpaceModifier = with(blankSpaceGestureHandler) { Modifier.blankSpaceGestures() }

  // Visual grid overlay during drag (F1.20)
  val gridOverlayModifier =
    if (dragState != null) {
      Modifier.drawBehind {
        val gridPx = gridUnitPx
        val cols = (size.width / gridPx).toInt()
        val rows = (size.height / gridPx).toInt()
        val lineColor = Color.White.copy(alpha = 0.15f)
        for (col in 0..cols step 2) {
          val x = col * gridPx
          drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
        for (row in 0..rows step 2) {
          val y = row * gridPx
          drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
      }
    } else {
      Modifier
    }

  Layout(
    content = {
      for (widget in visibleWidgets) {
        key(widget.instanceId) {
          val animState = animatingWidgets.find { it.widgetId == widget.instanceId }
          val isVisible = animState?.isRemoving != true

          AnimatedVisibility(
            visible = isVisible,
            enter =
              if (isReducedMotion) {
                fadeIn(snap()) + scaleIn(initialScale = 0.8f, animationSpec = snap())
              } else {
                fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                  scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                  )
              },
            exit =
              if (isReducedMotion) {
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
            val wiggleRotation =
              if (isEditMode && !isReducedMotion) {
                val infiniteTransition =
                  rememberInfiniteTransition(label = "wiggle_${widget.instanceId}")
                val rotation by
                  infiniteTransition.animateFloat(
                    initialValue = -0.5f,
                    targetValue = 0.5f,
                    animationSpec =
                      infiniteRepeatable(
                        animation = tween(durationMillis = 150),
                        repeatMode = RepeatMode.Reverse,
                      ),
                    label = "wiggle_rotation_${widget.instanceId}",
                  )
                rotation
              } else {
                0f
              }

            // Bracket stroke pulse for edit mode (F1.11) -- Canvas-drawn, NOT scale
            val bracketStrokeWidth =
              if (isEditMode && !isReducedMotion) {
                val infiniteTransition =
                  rememberInfiniteTransition(label = "bracket_${widget.instanceId}")
                val strokeWidth by
                  infiniteTransition.animateFloat(
                    initialValue = 3f,
                    targetValue = 6f,
                    animationSpec =
                      infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                      ),
                    label = "bracket_stroke_${widget.instanceId}",
                  )
                strokeWidth
              } else if (isEditMode) {
                4f // Static midpoint for reduced motion
              } else {
                0f // Not in edit mode
              }

            // Drag lift scale (1.03f spring on drag)
            val isDragging = widgetDragState?.isDragging == true
            val liftScale by
              animateFloatAsState(
                targetValue = if (isDragging) 1.03f else 1f,
                animationSpec =
                  if (isReducedMotion) {
                    snap()
                  } else {
                    spring(
                      dampingRatio = Spring.DampingRatioMediumBouncy,
                      stiffness = Spring.StiffnessMedium,
                    )
                  },
                label = "lift_scale_${widget.instanceId}",
              )

            // settingsAlpha: dim non-focused widgets when a widget is focused (F1.8)
            val settingsAlpha by
              animateFloatAsState(
                targetValue =
                  if (editState.focusedWidgetId != null &&
                    editState.focusedWidgetId != widget.instanceId
                  ) {
                    0.5f
                  } else {
                    1f
                  },
                animationSpec = tween(300),
                label = "settings_alpha_${widget.instanceId}",
              )

            // Focus tap handler: in edit mode, tap toggles focus (F2.18)
            val isFocused = editState.focusedWidgetId == widget.instanceId
            val focusClickModifier =
              if (isEditMode) {
                Modifier.clickable(
                  indication = null,
                  interactionSource = remember { MutableInteractionSource() },
                  onClick = {
                    if (isFocused) {
                      editModeCoordinator.focusWidget(null) // Unfocus
                    } else {
                      editModeCoordinator.focusWidget(widget.instanceId) // Focus
                    }
                  },
                )
              } else {
                Modifier
              }

            // Apply gesture handler
            val widgetSpec = widgetRegistry.findByTypeId(widget.typeId)
            val gestureModifier =
              if (widgetSpec != null) {
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

            Box(modifier = focusClickModifier) {
              WidgetSlot(
                widget = widget,
                widgetBindingCoordinator = widgetBindingCoordinator,
                widgetRegistry = widgetRegistry,
                editModeCoordinator = editModeCoordinator,
                resizeState = resizeState,
                onCommand = onCommand,
                modifier =
                  gestureModifier.graphicsLayer {
                    // Drag offset via graphicsLayer (NOT Modifier.offset)
                    if (widgetDragState != null) {
                      translationX = widgetDragState.currentOffsetX
                      translationY = widgetDragState.currentOffsetY
                    }
                    // Edit mode wiggle
                    rotationZ = wiggleRotation
                    // Drag lift scale
                    scaleX = liftScale
                    scaleY = liftScale
                    // Dim non-focused widgets (F1.8)
                    alpha = settingsAlpha
                  },
              )

              // Corner brackets for edit mode (F1.11)
              if (isEditMode && bracketStrokeWidth > 0f) {
                Canvas(
                  modifier =
                    Modifier.matchParentSize()
                      .graphicsLayer {
                        // Apply same drag offset so brackets follow widget
                        if (widgetDragState != null) {
                          translationX = widgetDragState.currentOffsetX
                          translationY = widgetDragState.currentOffsetY
                        }
                        scaleX = liftScale
                        scaleY = liftScale
                      }
                      .testTag("bracket_${widget.instanceId}"),
                ) {
                  val strokePx = bracketStrokeWidth.dp.toPx()
                  val bracketLength = 16.dp.toPx()
                  val color = Color.White

                  // Top-left corner
                  drawLine(
                    color,
                    Offset(0f, strokePx / 2),
                    Offset(bracketLength, strokePx / 2),
                    strokePx,
                  )
                  drawLine(
                    color,
                    Offset(strokePx / 2, 0f),
                    Offset(strokePx / 2, bracketLength),
                    strokePx,
                  )

                  // Top-right corner
                  drawLine(
                    color,
                    Offset(size.width - bracketLength, strokePx / 2),
                    Offset(size.width, strokePx / 2),
                    strokePx,
                  )
                  drawLine(
                    color,
                    Offset(size.width - strokePx / 2, 0f),
                    Offset(size.width - strokePx / 2, bracketLength),
                    strokePx,
                  )

                  // Bottom-left corner
                  drawLine(
                    color,
                    Offset(0f, size.height - strokePx / 2),
                    Offset(bracketLength, size.height - strokePx / 2),
                    strokePx,
                  )
                  drawLine(
                    color,
                    Offset(strokePx / 2, size.height - bracketLength),
                    Offset(strokePx / 2, size.height),
                    strokePx,
                  )

                  // Bottom-right corner
                  drawLine(
                    color,
                    Offset(size.width - bracketLength, size.height - strokePx / 2),
                    Offset(size.width, size.height - strokePx / 2),
                    strokePx,
                  )
                  drawLine(
                    color,
                    Offset(size.width - strokePx / 2, size.height - bracketLength),
                    Offset(size.width - strokePx / 2, size.height),
                    strokePx,
                  )
                }
              }
            }
          }
        }
      }

      // Focus overlay toolbar (F1.8) -- rendered after all widgets = highest z-index
      if (isEditMode && editState.focusedWidgetId != null) {
        val focusedWidget = visibleWidgets.find { it.instanceId == editState.focusedWidgetId }
        if (focusedWidget != null) {
          FocusOverlayToolbar(
            widgetId = focusedWidget.instanceId,
            onDelete = { onCommand(DashboardCommand.RemoveWidget(focusedWidget.instanceId)) },
            onSettings = {
              onCommand(DashboardCommand.OpenWidgetSettings(focusedWidget.instanceId))
            },
          )
        }
      }
    },
    modifier =
      modifier
        .semantics {}
        .testTag("dashboard_grid")
        .then(blankSpaceModifier)
        .then(gridOverlayModifier),
  ) { measurables, constraints ->
    // Pre-compute toolbar gap outside of placement lambda
    val toolbarGapPx = 8.dp.roundToPx()

    // Custom MeasurePolicy: each widget measured with constraints from widget.size * GRID_UNIT_SIZE
    // If focus toolbar is present, it's the last measurable (index == visibleWidgets.size)
    val hasFocusToolbar = isEditMode && editState.focusedWidgetId != null &&
      visibleWidgets.any { it.instanceId == editState.focusedWidgetId }

    val placeables =
      measurables.mapIndexed { index, measurable ->
        if (index < visibleWidgets.size) {
          val widget = visibleWidgets[index]
          val widthPx = (widget.size.widthUnits * gridUnitPx).roundToInt()
          val heightPx = (widget.size.heightUnits * gridUnitPx).roundToInt()
          measurable.measure(
            Constraints.fixed(
              width = widthPx.coerceAtLeast(0),
              height = heightPx.coerceAtLeast(0),
            ),
          )
        } else if (hasFocusToolbar && index == visibleWidgets.size) {
          // Focus toolbar: measure with wrap content
          measurable.measure(Constraints())
        } else {
          return@Layout layout(0, 0) {}
        }
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

      // Place focus toolbar above the focused widget at highest z-index
      if (hasFocusToolbar && placeables.size > visibleWidgets.size) {
        val focusedWidget = visibleWidgets.find { it.instanceId == editState.focusedWidgetId }
        if (focusedWidget != null) {
          val toolbarPlaceable = placeables[visibleWidgets.size]
          val widgetX = (focusedWidget.position.col * gridUnitPx).roundToInt()
          val widgetY = (focusedWidget.position.row * gridUnitPx).roundToInt()
          // Center toolbar above widget, offset by toolbar height + 8dp gap
          val widgetWidthPx = (focusedWidget.size.widthUnits * gridUnitPx).roundToInt()
          val toolbarX = widgetX + (widgetWidthPx - toolbarPlaceable.width) / 2
          val toolbarY = widgetY - toolbarPlaceable.height - toolbarGapPx
          toolbarPlaceable.placeRelative(
            x = toolbarX.coerceAtLeast(0),
            y = toolbarY.coerceAtLeast(0),
            zIndex = Float.MAX_VALUE, // Above all widgets
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
