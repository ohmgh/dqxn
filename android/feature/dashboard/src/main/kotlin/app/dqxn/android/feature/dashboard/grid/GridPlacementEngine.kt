package app.dqxn.android.feature.dashboard.grid

import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.debug
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList

/**
 * Finds optimal grid positions for new widgets, enforcing no-overlap and no-straddle snap (F1.27).
 *
 * Scanning strategy: row-by-row, column-by-column, preferring positions closest to the viewport
 * center. Falls back to extending the canvas downward if no valid position exists.
 */
public class GridPlacementEngine
@Inject
constructor(
  private val logger: DqxnLogger,
) {

  /**
   * Find the optimal position for a widget given existing placements, viewport dimensions, and
   * configuration boundaries.
   *
   * @param widget The widget to place (uses [DashboardWidgetInstance.size] for dimensions).
   * @param existingWidgets Currently placed widgets to avoid overlap with.
   * @param viewportCols Number of grid columns visible in the viewport.
   * @param viewportRows Number of grid rows visible in the viewport.
   * @param configBoundaries Configuration boundaries for no-straddle enforcement.
   * @return The best [GridPosition] for placement.
   */
  public fun findOptimalPosition(
    widget: DashboardWidgetInstance,
    existingWidgets: ImmutableList<DashboardWidgetInstance>,
    viewportCols: Int,
    viewportRows: Int,
    configBoundaries: List<ConfigurationBoundary> = emptyList(),
  ): GridPosition {
    val widgetSize = widget.size
    val centerCol = viewportCols / 2
    val centerRow = viewportRows / 2

    // Generate candidate positions sorted by distance from center (center-biased)
    val candidates =
      buildList {
          for (row in 0 until viewportRows) {
            for (col in 0 until viewportCols) {
              if (
                col + widgetSize.widthUnits <= viewportCols &&
                  row + widgetSize.heightUnits <= viewportRows
              ) {
                add(GridPosition(col, row))
              }
            }
          }
        }
        .sortedBy { pos ->
          val dc = abs(pos.col + widgetSize.widthUnits / 2 - centerCol)
          val dr = abs(pos.row + widgetSize.heightUnits / 2 - centerRow)
          dc + dr // Manhattan distance from center
        }

    for (candidate in candidates) {
      if (
        !hasOverlap(candidate, widgetSize, existingWidgets) &&
          !straddlesBoundary(candidate, widgetSize, configBoundaries)
      ) {
        logger.debug(TAG) { "Optimal position found: ($candidate)" }
        return candidate
      }
    }

    // No valid position within viewport -- extend canvas downward
    val maxRow =
      existingWidgets.maxOfOrNull { it.position.row + it.size.heightUnits } ?: viewportRows
    val extendedPosition = GridPosition(col = 0, row = maxRow)
    logger.debug(TAG) { "No viewport position available, extending canvas to $extendedPosition" }
    return extendedPosition
  }

  /**
   * Snap pixel coordinates to the nearest 2-unit grid boundary using midpoint snap.
   *
   * Per replication advisory section 6: `roundToInt(pixelPos / gridUnitPx)` with 2-unit rounding.
   */
  public fun snapToGrid(pixelX: Float, pixelY: Float, gridUnitPx: Float): GridPosition {
    val rawCol = (pixelX / gridUnitPx).roundToInt()
    val rawRow = (pixelY / gridUnitPx).roundToInt()
    // Snap to nearest 2-unit boundary
    val snappedCol = ((rawCol + 1) / SNAP_UNIT) * SNAP_UNIT
    val snappedRow = ((rawRow + 1) / SNAP_UNIT) * SNAP_UNIT
    return GridPosition(col = snappedCol, row = snappedRow)
  }

  /**
   * If a widget's bounding box crosses a configuration boundary, snap it to the nearest side
   * (inside or outside, whichever is closer).
   */
  public fun enforceNoStraddle(
    position: GridPosition,
    size: GridSize,
    boundaries: List<ConfigurationBoundary>,
  ): GridPosition {
    var result = position

    for (boundary in boundaries) {
      val bRect = boundary.rect

      val widgetLeft = result.col
      val widgetRight = result.col + size.widthUnits
      val widgetTop = result.row
      val widgetBottom = result.row + size.heightUnits

      // Check horizontal straddle: widget crosses left or right edge of boundary
      if (widgetTop < bRect.bottom && widgetBottom > bRect.top) {
        // Widget vertically overlaps with boundary region
        if (widgetLeft < bRect.right && widgetRight > bRect.right) {
          // Widget straddles the right edge
          val snapInside = bRect.right - size.widthUnits
          val snapOutside = bRect.right
          result =
            if (abs(widgetLeft - snapInside) <= abs(widgetLeft - snapOutside)) {
              result.copy(col = snapInside)
            } else {
              result.copy(col = snapOutside)
            }
        }
        if (widgetLeft < bRect.left && widgetRight > bRect.left) {
          // Widget straddles the left edge
          val snapInside = bRect.left
          val snapOutside = bRect.left - size.widthUnits
          result =
            if (abs(widgetLeft - snapInside) <= abs(widgetLeft - snapOutside)) {
              result.copy(col = snapInside)
            } else {
              result.copy(col = snapOutside)
            }
        }
      }

      // Check vertical straddle: widget crosses top or bottom edge of boundary
      if (widgetLeft < bRect.right && widgetRight > bRect.left) {
        // Widget horizontally overlaps with boundary region
        if (widgetTop < bRect.bottom && widgetBottom > bRect.bottom) {
          // Widget straddles the bottom edge
          val snapInside = bRect.bottom - size.heightUnits
          val snapOutside = bRect.bottom
          result =
            if (abs(widgetTop - snapInside) <= abs(widgetTop - snapOutside)) {
              result.copy(row = snapInside)
            } else {
              result.copy(row = snapOutside)
            }
        }
        if (widgetTop < bRect.top && widgetBottom > bRect.top) {
          // Widget straddles the top edge
          val snapInside = bRect.top
          val snapOutside = bRect.top - size.heightUnits
          result =
            if (abs(widgetTop - snapInside) <= abs(widgetTop - snapOutside)) {
              result.copy(row = snapInside)
            } else {
              result.copy(row = snapOutside)
            }
        }
      }
    }

    return result
  }

  /** Check if placing a widget at [position] with [size] overlaps any existing widget. */
  private fun hasOverlap(
    position: GridPosition,
    size: GridSize,
    existingWidgets: ImmutableList<DashboardWidgetInstance>,
  ): Boolean {
    val newLeft = position.col
    val newRight = position.col + size.widthUnits
    val newTop = position.row
    val newBottom = position.row + size.heightUnits

    return existingWidgets.any { existing ->
      val exLeft = existing.position.col
      val exRight = existing.position.col + existing.size.widthUnits
      val exTop = existing.position.row
      val exBottom = existing.position.row + existing.size.heightUnits

      newLeft < exRight && newRight > exLeft && newTop < exBottom && newBottom > exTop
    }
  }

  /**
   * Check if a widget bounding box straddles any configuration boundary. A widget "straddles" if it
   * partially overlaps a boundary rect -- fully inside or fully outside is fine.
   */
  private fun straddlesBoundary(
    position: GridPosition,
    size: GridSize,
    boundaries: List<ConfigurationBoundary>,
  ): Boolean {
    val wLeft = position.col
    val wRight = position.col + size.widthUnits
    val wTop = position.row
    val wBottom = position.row + size.heightUnits

    return boundaries.any { boundary ->
      val bRect = boundary.rect
      val overlapLeft = maxOf(wLeft, bRect.left)
      val overlapRight = minOf(wRight, bRect.right)
      val overlapTop = maxOf(wTop, bRect.top)
      val overlapBottom = minOf(wBottom, bRect.bottom)

      val hasOverlap = overlapLeft < overlapRight && overlapTop < overlapBottom

      if (!hasOverlap) return@any false

      // Straddle = partial overlap (not fully inside the boundary rect)
      val fullyInside =
        wLeft >= bRect.left && wRight <= bRect.right && wTop >= bRect.top && wBottom <= bRect.bottom

      !fullyInside
    }
  }

  private companion object {
    val TAG = LogTag("GridPlacement")
    const val SNAP_UNIT: Int = 2
  }
}
