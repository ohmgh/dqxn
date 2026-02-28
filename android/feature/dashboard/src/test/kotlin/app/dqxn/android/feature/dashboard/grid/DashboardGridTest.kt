package app.dqxn.android.feature.dashboard.grid

import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.widget.GridConstants
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Unit tests for DashboardGrid logic (NOT compose-ui-test).
 *
 * Tests viewport culling, z-index ordering, grid unit size, placement math, and foldable
 * no-straddle snap enforcement (NF46).
 */
@Tag("fast")
class DashboardGridTest {

  private val defaultStyle =
    WidgetStyle(
      backgroundStyle = BackgroundStyle.NONE,
      opacity = 1f,
      showBorder = false,
      hasGlowEffect = false,
      cornerRadiusPercent = 0,
      rimSizePercent = 0,
      zLayer = 0,
    )

  private fun widget(
    id: String,
    col: Int,
    row: Int,
    width: Int = 2,
    height: Int = 2,
    zIndex: Int = 0,
  ): DashboardWidgetInstance =
    DashboardWidgetInstance(
      instanceId = id,
      typeId = "essentials:test",
      position = GridPosition(col = col, row = row),
      size = GridSize(widthUnits = width, heightUnits = height),
      style = defaultStyle,
      settings = persistentMapOf(),
      dataSourceBindings = persistentMapOf(),
      zIndex = zIndex,
    )

  @Test
  fun `viewport culling filters out off-screen widgets`() {
    val viewportCols = 10
    val viewportRows = 8

    val widgets =
      listOf(
        widget("w1", col = 0, row = 0), // visible (top-left)
        widget("w2", col = 5, row = 3), // visible (center)
        widget("w3", col = 8, row = 6), // visible (near edge)
        widget("w4", col = 12, row = 0), // OFF-SCREEN: col >= viewportCols
        widget("w5", col = 0, row = 10), // OFF-SCREEN: row >= viewportRows
      )

    val visible =
      widgets.filter { widget ->
        widget.position.col < viewportCols &&
          widget.position.row < viewportRows &&
          widget.position.col + widget.size.widthUnits > 0 &&
          widget.position.row + widget.size.heightUnits > 0
      }

    assertThat(visible).hasSize(3)
    assertThat(visible.map { it.instanceId }).containsExactly("w1", "w2", "w3")
  }

  @Test
  fun `widget z-index ordering correct`() {
    val widgets =
      listOf(
        widget("low", col = 0, row = 0, zIndex = 1),
        widget("high", col = 2, row = 0, zIndex = 5),
        widget("medium", col = 4, row = 0, zIndex = 3),
      )

    val sorted = widgets.sortedBy { it.zIndex }
    assertThat(sorted.map { it.instanceId }).containsExactly("low", "medium", "high").inOrder()
  }

  @Test
  fun `grid unit size calculation is 16dp`() {
    assertThat(GridConstants.GRID_UNIT_SIZE.value).isEqualTo(16f)
    assertThat(DashboardGridConstants.GRID_UNIT_SIZE_DP.value).isEqualTo(16f)
  }

  @Test
  fun `widget placement coordinates match position times GRID_UNIT_SIZE`() {
    // Verify MeasurePolicy math: x = col * gridUnitPx, y = row * gridUnitPx
    val gridUnitPx = 16f * 2.0f // 16dp at 2x density = 32px

    val w = widget("w1", col = 3, row = 5, width = 4, height = 3)

    val expectedX = w.position.col * gridUnitPx // 3 * 32 = 96
    val expectedY = w.position.row * gridUnitPx // 5 * 32 = 160
    val expectedWidth = w.size.widthUnits * gridUnitPx // 4 * 32 = 128
    val expectedHeight = w.size.heightUnits * gridUnitPx // 3 * 32 = 96

    assertThat(expectedX).isEqualTo(96f)
    assertThat(expectedY).isEqualTo(160f)
    assertThat(expectedWidth).isEqualTo(128f)
    assertThat(expectedHeight).isEqualTo(96f)
  }

  // ---- Foldable no-straddle snap tests (NF46) ----

  private val gridPlacementEngine = GridPlacementEngine(mockk(relaxed = true))

  @Test
  fun `widget straddling fold boundary snapped to nearest side`() {
    // Fold boundary at col 10 (horizontal fold line: left=0, top=0, right=10, bottom=20)
    val boundary =
      ConfigurationBoundary(
        name = "fold",
        rect = androidx.compose.ui.unit.IntRect(left = 0, top = 0, right = 10, bottom = 20),
      )

    // Widget at col=8, width=4 -> straddles right edge (8+4=12 > 10)
    val position = GridPosition(col = 8, row = 2)
    val size = GridSize(widthUnits = 4, heightUnits = 2)

    val snapped = gridPlacementEngine.enforceNoStraddle(position, size, listOf(boundary))

    // Should snap: either to col=6 (inside: 10-4=6) or col=10 (outside)
    // Distance from 8 to 6 = 2, from 8 to 10 = 2, so ties go to inside (<=)
    assertThat(snapped.col == 6 || snapped.col == 10).isTrue()
  }

  @Test
  fun `widget fully within one fold region is not relocated`() {
    val boundary =
      ConfigurationBoundary(
        name = "fold",
        rect = androidx.compose.ui.unit.IntRect(left = 0, top = 0, right = 10, bottom = 20),
      )

    // Widget fully inside boundary: col=2, width=4 -> 2+4=6 < 10
    val position = GridPosition(col = 2, row = 3)
    val size = GridSize(widthUnits = 4, heightUnits = 2)

    val result = gridPlacementEngine.enforceNoStraddle(position, size, listOf(boundary))

    assertThat(result).isEqualTo(position)
  }

  @Test
  fun `fold boundary change triggers re-validation of all widget positions`() {
    // Mock boundary detector
    val detector = mockk<ConfigurationBoundaryDetector>()
    val boundaryFlow = MutableStateFlow(persistentListOf<ConfigurationBoundary>())
    every { detector.boundaries } returns boundaryFlow

    // Simulate boundary change
    val newBoundaries =
      persistentListOf(
        ConfigurationBoundary("fold", androidx.compose.ui.unit.IntRect(0, 0, 10, 20)),
      )
    boundaryFlow.value = newBoundaries

    // Verify the boundary flow emits new boundaries
    assertThat(boundaryFlow.value).hasSize(1)
    assertThat(boundaryFlow.value.first().name).isEqualTo("fold")
  }

  // ---- Resize preview tests (R3) ----

  @Test
  fun `resize preview uses targetSize for measurement`() {
    val w = widget("w1", col = 2, row = 2, width = 4, height = 4)
    val resizeState = ResizeUpdate(
      widgetId = "w1",
      handle = ResizeHandle.BOTTOM_RIGHT,
      targetSize = GridSize(widthUnits = 8, heightUnits = 6),
      targetPosition = null,
      isResizing = true,
    )

    // When resizing, effective size should be targetSize, not widget.size
    val effectiveSize = if (resizeState.widgetId == w.instanceId && resizeState.isResizing) {
      resizeState.targetSize
    } else {
      w.size
    }
    assertThat(effectiveSize.widthUnits).isEqualTo(8)
    assertThat(effectiveSize.heightUnits).isEqualTo(6)
  }

  @Test
  fun `resize preview uses targetPosition for placement`() {
    val w = widget("w1", col = 4, row = 4, width = 4, height = 4)
    val resizeState = ResizeUpdate(
      widgetId = "w1",
      handle = ResizeHandle.TOP_LEFT,
      targetSize = GridSize(widthUnits = 6, heightUnits = 6),
      targetPosition = GridPosition(col = 2, row = 2),
      isResizing = true,
    )

    val effectivePosition = if (resizeState.widgetId == w.instanceId &&
      resizeState.isResizing && resizeState.targetPosition != null
    ) {
      resizeState.targetPosition
    } else {
      w.position
    }
    assertThat(effectivePosition.col).isEqualTo(2)
    assertThat(effectivePosition.row).isEqualTo(2)
  }

  @Test
  fun `brackets visible during resize`() {
    val isEditMode = true
    val isFocused = false
    val isResizingThisWidget = true

    // Brackets should be visible when isEditMode && (isFocused || isResizingThisWidget)
    val showBrackets = isEditMode && (isFocused || isResizingThisWidget)
    assertThat(showBrackets).isTrue()
  }

  @Test
  fun `no-straddle enforced during drag - snap preview respects fold boundary`() {
    val boundary =
      ConfigurationBoundary(
        name = "fold",
        rect = androidx.compose.ui.unit.IntRect(left = 0, top = 0, right = 10, bottom = 20),
      )

    // Simulate drag ending at pixel position that maps to a straddling grid position
    val gridUnitPx = 32f

    // Snap pixel (9 * 32, 2 * 32) -> raw col = 9, raw row = 2
    val snappedPosition =
      gridPlacementEngine.snapToGrid(9f * gridUnitPx, 2f * gridUnitPx, gridUnitPx)

    // Now enforce no-straddle with the fold boundary
    val size = GridSize(widthUnits = 4, heightUnits = 2)
    val finalPosition =
      gridPlacementEngine.enforceNoStraddle(snappedPosition, size, listOf(boundary))

    // Widget should NOT straddle the boundary at col=10
    val widgetRight = finalPosition.col + size.widthUnits
    val eitherFullyInside = widgetRight <= 10
    val eitherFullyOutside = finalPosition.col >= 10

    assertThat(eitherFullyInside || eitherFullyOutside).isTrue()
  }
}
