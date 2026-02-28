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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.feature.dashboard.binding.WidgetSlot
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.EditState
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import app.dqxn.android.sdk.ui.widget.GridConstants
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList

/** Pre-computed grid overlay line color (avoids per-frame Color.copy allocation). */
private val GRID_LINE_COLOR = Color.White.copy(alpha = 0.15f)

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
  onTapBlankSpace: () -> Unit,
  editingWidgetId: String? = null,
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

  // Container dimensions for preview centering (computed from viewport grid dims)
  val containerWidthPx = viewportCols * gridUnitPx
  val containerHeightPx = viewportRows * gridUnitPx
  val isSettingsOpen = editingWidgetId != null

  val currentOnTapBlankSpace by rememberUpdatedState(onTapBlankSpace)
  val blankSpaceModifier = with(blankSpaceGestureHandler) {
    Modifier.blankSpaceGestures(onTapBlankSpace = { currentOnTapBlankSpace() })
  }

  // Visual grid overlay during drag (F1.20)
  val gridOverlayModifier =
    if (dragState != null) {
      Modifier.drawBehind {
        val gridPx = gridUnitPx
        val cols = (size.width / gridPx).toInt()
        val rows = (size.height / gridPx).toInt()
        val lineColor = GRID_LINE_COLOR
        for (col in 0..cols) {
          val x = col * gridPx
          drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
        for (row in 0..rows) {
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
            val isActivelyManipulated = dragState?.widgetId == widget.instanceId ||
              resizeState?.widgetId == widget.instanceId
            val isFocused = editState.focusedWidgetId == widget.instanceId
            val isResizingThisWidget = resizeState?.widgetId == widget.instanceId &&
              resizeState.isResizing

            // Wiggle rotation for edit mode (F1.11) -- suppressed during active drag/resize or settings open
            val wiggleRotation =
              if (isEditMode && !isReducedMotion && !isActivelyManipulated && !isSettingsOpen) {
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

            // Bracket stroke pulse for edit mode (F1.11) -- on focused or resizing widget
            val showBrackets = isFocused || isResizingThisWidget
            val bracketStrokeWidth =
              if (isEditMode && showBrackets && !isReducedMotion) {
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
              } else if (isEditMode && showBrackets) {
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

            // Preview state per widget
            val isBeingEdited = editingWidgetId == widget.instanceId

            // settingsAlpha: fade out non-edited widgets when settings open, dim non-focused in edit mode
            val settingsAlpha by
              animateFloatAsState(
                targetValue = when {
                  isSettingsOpen && !isBeingEdited -> 0f
                  isSettingsOpen && isBeingEdited -> 1f
                  editState.focusedWidgetId != null &&
                    editState.focusedWidgetId != widget.instanceId -> 0.5f
                  else -> 1f
                },
                animationSpec = if (isBeingEdited) snap() else tween(300),
                label = "settings_alpha_${widget.instanceId}",
              )

            // Preview centering: translate edited widget to center of peek zone
            val previewFraction = 0.38f
            val peekHeightPx = containerHeightPx * previewFraction
            val peekCenterY = peekHeightPx / 2f

            val focusTranslationX by animateFloatAsState(
              targetValue = if (isBeingEdited) {
                val widgetW = widget.size.widthUnits * gridUnitPx
                (containerWidthPx - widgetW) / 2f - (widget.position.col * gridUnitPx)
              } else {
                0f
              },
              animationSpec = spring(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioNoBouncy,
              ),
              label = "focus_tx_${widget.instanceId}",
            )

            val focusTranslationY by animateFloatAsState(
              targetValue = if (isBeingEdited) {
                val widgetH = widget.size.heightUnits * gridUnitPx
                val originalCenterY = (widget.position.row * gridUnitPx) + (widgetH / 2f)
                peekCenterY - originalCenterY
              } else {
                0f
              },
              animationSpec = spring(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioNoBouncy,
              ),
              label = "focus_ty_${widget.instanceId}",
            )

            // Scale down if widget exceeds peek zone
            val focusScale by animateFloatAsState(
              targetValue = if (isBeingEdited) {
                val widgetW = widget.size.widthUnits * gridUnitPx
                val widgetH = widget.size.heightUnits * gridUnitPx
                if (widgetW > containerWidthPx || widgetH > peekHeightPx) {
                  minOf(containerWidthPx / widgetW, peekHeightPx / widgetH)
                } else {
                  1f
                }
              } else {
                1f
              },
              animationSpec = spring(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioNoBouncy,
              ),
              label = "focus_scale_${widget.instanceId}",
            )

            // Apply gesture handler
            val renderer = widgetRegistry.findByTypeId(widget.typeId)
            val onWidgetTap: (() -> Unit)? = if (renderer != null && renderer.supportsTap) {
              @Suppress("UNCHECKED_CAST")
              val anySettings = widget.settings as kotlinx.collections.immutable.ImmutableMap<String, Any>
              { renderer.onTap(widget.instanceId, anySettings) }
            } else {
              null
            }
            val gestureModifier =
              if (renderer != null) {
                with(widgetGestureHandler) {
                  Modifier.widgetGestures(
                    widgetId = widget.instanceId,
                    widgetSpec = renderer,
                    currentPosition = widget.position,
                    currentSize = widget.size,
                    gridUnitPx = gridUnitPx,
                    viewportCols = viewportCols,
                    viewportRows = viewportRows,
                    onWidgetTap = onWidgetTap,
                  )
                }
              } else {
                Modifier
              }

            // Edit mode overlay gate — matches old codebase derivedStateOf:
            // all edit visuals (brackets, toolbar, handles) hidden when settings open
            val showEditModeUI = isEditMode && !isSettingsOpen

            // Outer Box: gesture modifier here so brackets/handles are CHILDREN, not siblings.
            // Old codebase: gesture on outer Box, content in inner Offscreen Box, overlay as siblings.
            // Pointer pass ordering (handles Main, body Final) only works in parent-child hierarchy.
            Box(
              modifier = gestureModifier.graphicsLayer {
                // Drag offset via graphicsLayer (NOT Modifier.offset)
                if (widgetDragState != null) {
                  translationX = widgetDragState.currentOffsetX
                  translationY = widgetDragState.currentOffsetY
                }
                // Preview centering offset
                translationX += focusTranslationX
                translationY += focusTranslationY
                // Edit mode wiggle
                rotationZ = wiggleRotation
                // Drag lift scale * preview scale
                scaleX = liftScale * focusScale
                scaleY = liftScale * focusScale
                // Dim non-focused widgets (F1.8) / fade out when settings open
                alpha = settingsAlpha
                // Don't clip — allows toolbar/brackets to render outside widget bounds
                clip = false
              },
            ) {
              // Widget content in separate Offscreen layer to prevent transparency artifacts.
              // Edit controls are OUTSIDE this layer so they render above widget bounds.
              Box(
                modifier = Modifier
                  .matchParentSize()
                  .graphicsLayer {
                    compositingStrategy =
                      androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                  },
              ) {
                WidgetSlot(
                  widget = widget,
                  widgetBindingCoordinator = widgetBindingCoordinator,
                  widgetRegistry = widgetRegistry,
                  editModeCoordinator = editModeCoordinator,
                  resizeState = resizeState,
                  onCommand = onCommand,
                )
              }

              // All edit mode overlay elements gated by showEditModeUI (old codebase pattern)
              if (showEditModeUI) {
                // Corner brackets (F1.11) -- on focused or resizing widget
                if ((isFocused || isResizingThisWidget) && bracketStrokeWidth > 0f) {
                  val bracketColor = LocalDashboardTheme.current.accentColor

                  Canvas(
                    modifier = Modifier
                      .matchParentSize()
                      .testTag("bracket_${widget.instanceId}"),
                  ) {
                    val strokePx = bracketStrokeWidth.dp.toPx()
                    val bracketLengthPx = 32.dp.toPx()
                    val cornerRadiusPx = bracketLengthPx / 4f // 8dp arc
                    val legLength = bracketLengthPx / 2f - cornerRadiusPx // 8dp straight
                    val arcDiameter = cornerRadiusPx * 2f
                    val color = bracketColor
                    val cap = StrokeCap.Round

                    // Top-left corner: arc + legs
                    drawArc(
                      color = color,
                      startAngle = 180f,
                      sweepAngle = 90f,
                      useCenter = false,
                      topLeft = Offset(0f, 0f),
                      size = Size(arcDiameter, arcDiameter),
                      style = Stroke(width = strokePx, cap = cap),
                    )
                    drawLine(color, Offset(cornerRadiusPx, 0f), Offset(cornerRadiusPx + legLength, 0f), strokePx, cap)
                    drawLine(color, Offset(0f, cornerRadiusPx), Offset(0f, cornerRadiusPx + legLength), strokePx, cap)

                    // Top-right corner: arc + legs
                    drawArc(
                      color = color,
                      startAngle = 270f,
                      sweepAngle = 90f,
                      useCenter = false,
                      topLeft = Offset(size.width - arcDiameter, 0f),
                      size = Size(arcDiameter, arcDiameter),
                      style = Stroke(width = strokePx, cap = cap),
                    )
                    drawLine(color, Offset(size.width - cornerRadiusPx - legLength, 0f), Offset(size.width - cornerRadiusPx, 0f), strokePx, cap)
                    drawLine(color, Offset(size.width, cornerRadiusPx), Offset(size.width, cornerRadiusPx + legLength), strokePx, cap)

                    // Bottom-left corner: arc + legs
                    drawArc(
                      color = color,
                      startAngle = 90f,
                      sweepAngle = 90f,
                      useCenter = false,
                      topLeft = Offset(0f, size.height - arcDiameter),
                      size = Size(arcDiameter, arcDiameter),
                      style = Stroke(width = strokePx, cap = cap),
                    )
                    drawLine(color, Offset(cornerRadiusPx, size.height), Offset(cornerRadiusPx + legLength, size.height), strokePx, cap)
                    drawLine(color, Offset(0f, size.height - cornerRadiusPx - legLength), Offset(0f, size.height - cornerRadiusPx), strokePx, cap)

                    // Bottom-right corner: arc + legs
                    drawArc(
                      color = color,
                      startAngle = 0f,
                      sweepAngle = 90f,
                      useCenter = false,
                      topLeft = Offset(size.width - arcDiameter, size.height - arcDiameter),
                      size = Size(arcDiameter, arcDiameter),
                      style = Stroke(width = strokePx, cap = cap),
                    )
                    drawLine(color, Offset(size.width - cornerRadiusPx - legLength, size.height), Offset(size.width - cornerRadiusPx, size.height), strokePx, cap)
                    drawLine(color, Offset(size.width, size.height - cornerRadiusPx - legLength), Offset(size.width, size.height - cornerRadiusPx), strokePx, cap)
                  }
                }

                // Resize handles at 32dp — only when focused or resizing (old codebase pattern)
                if ((isFocused || isResizingThisWidget) && renderer != null) {
                  for (handle in ResizeHandle.entries) {
                    Box(
                      modifier = Modifier
                        .size(32.dp)
                        .align(handle.toAlignment())
                        .pointerInput(widget.instanceId, handle) {
                          awaitEachGesture {
                            val down = awaitFirstDown(
                              requireUnconsumed = false,
                              pass = PointerEventPass.Main,
                            )
                            down.consume()
                            editModeCoordinator.startResize(
                              widget.instanceId, handle, widget.size, widget.position, renderer,
                            )
                            awaitResizeEvents(handle, gridUnitPx, editModeCoordinator)
                          }
                        }
                        .testTag("resize_handle_${handle.name}_${widget.instanceId}"),
                    )
                  }
                }

                // Focus toolbar (F1.8) — inside widget Box, positioned above/below via unbounded overflow.
                // Old codebase: AnimatedVisibility inside widget Box, wrapContentSize(unbounded = true).
                if (isFocused && !isResizingThisWidget) {
                  FocusOverlayToolbar(
                    widgetId = widget.instanceId,
                    onDelete = { onCommand(DashboardCommand.RemoveWidget(widget.instanceId)) },
                    onSettings = {
                      onCommand(DashboardCommand.OpenWidgetSettings(widget.instanceId))
                    },
                    modifier = Modifier
                      .align(Alignment.TopCenter)
                      .wrapContentSize(unbounded = true)
                      .offset(y = (-48).dp) // 40dp button + 8dp gap
                      .zIndex(10f),
                  )
                }
              }
            }
          }
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
    // Derive viewport from actual layout constraints and update coordinator
    val derivedCols = (constraints.maxWidth / gridUnitPx).toInt()
    val derivedRows = (constraints.maxHeight / gridUnitPx).toInt()
    editModeCoordinator.updateViewport(derivedCols, derivedRows)

    // Custom MeasurePolicy: each widget measured with constraints from widget.size * GRID_UNIT_SIZE
    val placeables =
      measurables.mapIndexed { index, measurable ->
        if (index < visibleWidgets.size) {
          val widget = visibleWidgets[index]
          // Use resize preview size when this widget is being resized
          val effectiveSize = if (resizeState?.widgetId == widget.instanceId &&
            resizeState.isResizing
          ) {
            resizeState.targetSize
          } else {
            widget.size
          }
          val widthPx = (effectiveSize.widthUnits * gridUnitPx).roundToInt()
          val heightPx = (effectiveSize.heightUnits * gridUnitPx).roundToInt()
          measurable.measure(
            Constraints.fixed(
              width = widthPx.coerceAtLeast(0),
              height = heightPx.coerceAtLeast(0),
            ),
          )
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
          // Use resize preview position when this widget is being resized
          val effectivePosition = if (resizeState?.widgetId == widget.instanceId &&
            resizeState.isResizing && resizeState.targetPosition != null
          ) {
            resizeState.targetPosition
          } else {
            widget.position
          }
          val x = (effectivePosition.col * gridUnitPx).roundToInt()
          val y = (effectivePosition.row * gridUnitPx).roundToInt()
          // Elevate focused/dragging/resizing/editing widget above all others
          val isManipulated = dragState?.widgetId == widget.instanceId ||
            resizeState?.widgetId == widget.instanceId ||
            editState.focusedWidgetId == widget.instanceId
          val isBeingEdited = editingWidgetId == widget.instanceId
          val zIndex = when {
            isBeingEdited -> 1000f
            isManipulated -> Float.MAX_VALUE - 1f
            else -> widget.zIndex.toFloat()
          }
          placeable.placeRelative(
            x = x,
            y = y,
            zIndex = zIndex,
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

/** Map [ResizeHandle] to [Alignment] for Box placement within a parent. */
private fun ResizeHandle.toAlignment(): Alignment = when (this) {
  ResizeHandle.TOP_LEFT -> Alignment.TopStart
  ResizeHandle.TOP_RIGHT -> Alignment.TopEnd
  ResizeHandle.BOTTOM_LEFT -> Alignment.BottomStart
  ResizeHandle.BOTTOM_RIGHT -> Alignment.BottomEnd
}

/**
 * Resize gesture tracking from a corner handle Box node. Accumulates pointer deltas, inverts
 * for left/top handles, and updates the coordinator each frame.
 */
private suspend fun AwaitPointerEventScope.awaitResizeEvents(
  handle: ResizeHandle,
  gridUnitPx: Float,
  coordinator: EditModeCoordinator,
) {
  var accX = 0f
  var accY = 0f
  while (true) {
    val event = awaitPointerEvent(PointerEventPass.Initial)
    val change = event.changes.firstOrNull() ?: break
    if (change.changedToUp()) {
      coordinator.endResize()
      break
    }
    val pos = change.positionChange()
    if (pos != Offset.Zero) {
      change.consume()
      val dx = if (handle == ResizeHandle.TOP_LEFT || handle == ResizeHandle.BOTTOM_LEFT) -pos.x else pos.x
      val dy = if (handle == ResizeHandle.TOP_LEFT || handle == ResizeHandle.TOP_RIGHT) -pos.y else pos.y
      accX += dx
      accY += dy
      coordinator.updateResize((accX / gridUnitPx).toInt(), (accY / gridUnitPx).toInt())
    }
  }
}
