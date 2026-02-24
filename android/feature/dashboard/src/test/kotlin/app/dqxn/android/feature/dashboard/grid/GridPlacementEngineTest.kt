package app.dqxn.android.feature.dashboard.grid

import androidx.compose.ui.unit.IntRect
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.feature.dashboard.test.TestWidgetFactory.testWidget
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class GridPlacementEngineTest {

  private val engine = GridPlacementEngine(logger = NoOpLogger)

  @Test
  fun `findOptimalPosition on empty grid returns center-biased position`() {
    val widget = testWidget(widthUnits = 4, heightUnits = 4)
    val position = engine.findOptimalPosition(
      widget = widget,
      existingWidgets = persistentListOf(),
      viewportCols = 20,
      viewportRows = 12,
    )

    // Center of 20x12 grid is (10, 6). Widget is 4x4, so center of widget should be near center.
    // Manhattan distance sorting means position should be near center
    assertThat(position.col).isAtLeast(6)
    assertThat(position.col).isAtMost(12)
    assertThat(position.row).isAtLeast(2)
    assertThat(position.row).isAtMost(6)
  }

  @Test
  fun `findOptimalPosition avoids existing widget overlap`() {
    val existing = testWidget(col = 8, row = 4, widthUnits = 4, heightUnits = 4)
    val newWidget = testWidget(widthUnits = 4, heightUnits = 4)

    val position = engine.findOptimalPosition(
      widget = newWidget,
      existingWidgets = persistentListOf(existing),
      viewportCols = 20,
      viewportRows = 12,
    )

    // Ensure no overlap with existing widget at (8,4)-(12,8)
    val newRight = position.col + 4
    val newBottom = position.row + 4
    val overlaps = position.col < 12 && newRight > 8 && position.row < 8 && newBottom > 4
    assertThat(overlaps).isFalse()
  }

  @Test
  fun `findOptimalPosition respects no-straddle snap on configuration boundary`() {
    val boundary = ConfigurationBoundary(
      name = "fold",
      rect = IntRect(left = 0, top = 0, right = 10, bottom = 12),
    )
    val newWidget = testWidget(widthUnits = 4, heightUnits = 4)

    val position = engine.findOptimalPosition(
      widget = newWidget,
      existingWidgets = persistentListOf(),
      viewportCols = 20,
      viewportRows = 12,
      configBoundaries = listOf(boundary),
    )

    // Widget should be fully inside or fully outside the boundary
    val widgetRight = position.col + 4
    val widgetBottom = position.row + 4

    val fullyInside = position.col >= 0 && widgetRight <= 10 && position.row >= 0 && widgetBottom <= 12
    val fullyOutside = position.col >= 10 || widgetRight <= 0 || position.row >= 12 || widgetBottom <= 0

    assertThat(fullyInside || fullyOutside).isTrue()
  }

  @Test
  fun `snapToGrid snaps to nearest 2-unit boundary`() {
    // 48px / 16px grid unit = 3.0 raw units -> snaps to 2 (nearest even)
    val pos1 = engine.snapToGrid(pixelX = 48f, pixelY = 48f, gridUnitPx = 16f)
    assertThat(pos1.col % 2).isEqualTo(0)
    assertThat(pos1.row % 2).isEqualTo(0)

    // 80px / 16px = 5.0 raw units -> snaps to 6 (nearest even, (5+1)/2*2 = 6)
    val pos2 = engine.snapToGrid(pixelX = 80f, pixelY = 80f, gridUnitPx = 16f)
    assertThat(pos2.col % 2).isEqualTo(0)
    assertThat(pos2.row % 2).isEqualTo(0)
  }

  @Test
  fun `enforceNoStraddle snaps widget to nearest side of boundary`() {
    val boundary = ConfigurationBoundary(
      name = "fold",
      rect = IntRect(left = 0, top = 0, right = 10, bottom = 12),
    )

    // Widget at col=8 with width=4 straddles the right edge (8 < 10 < 12)
    val snapped = engine.enforceNoStraddle(
      position = GridPosition(col = 8, row = 0),
      size = GridSize(widthUnits = 4, heightUnits = 4),
      boundaries = listOf(boundary),
    )

    // Should snap to inside (col=6) or outside (col=10) -- whichever is closer
    val widgetRight = snapped.col + 4
    val fullyInside = snapped.col >= 0 && widgetRight <= 10
    val fullyOutside = snapped.col >= 10
    assertThat(fullyInside || fullyOutside).isTrue()
  }

  @Test
  fun `no valid position extends canvas downward`() {
    // Fill the entire 4x4 viewport with widgets
    val widgets = listOf(
      testWidget(col = 0, row = 0, widthUnits = 4, heightUnits = 4),
    ).toImmutableList()

    val newWidget = testWidget(widthUnits = 4, heightUnits = 4)
    val position = engine.findOptimalPosition(
      widget = newWidget,
      existingWidgets = widgets,
      viewportCols = 4,
      viewportRows = 4,
    )

    // Should extend below the viewport since it's full
    assertThat(position.row).isAtLeast(4)
  }

  @Test
  fun `configuration boundary intersection detection`() {
    val boundary = ConfigurationBoundary(
      name = "fold",
      rect = IntRect(left = 0, top = 0, right = 10, bottom = 12),
    )

    // A widget at col=9 with width=4 partially crosses the boundary at right=10
    val crossingWidget = testWidget(col = 9, row = 0, widthUnits = 4, heightUnits = 4)

    // findOptimalPosition should NOT place a widget at this position
    val existing = listOf(crossingWidget).toImmutableList()
    val newWidget = testWidget(widthUnits = 2, heightUnits = 2)

    // Place the new widget -- it should avoid the boundary straddle area
    val position = engine.findOptimalPosition(
      widget = newWidget,
      existingWidgets = persistentListOf(),
      viewportCols = 20,
      viewportRows = 12,
      configBoundaries = listOf(boundary),
    )

    // Result should be fully inside or fully outside the boundary
    val widgetRight = position.col + 2
    val widgetBottom = position.row + 2

    val fullyInside = position.col >= 0 && widgetRight <= 10 && position.row >= 0 && widgetBottom <= 12
    val fullyOutside = position.col >= 10 || widgetRight <= 0 || position.row >= 12 || widgetBottom <= 0

    assertThat(fullyInside || fullyOutside).isTrue()
  }
}
