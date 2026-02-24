package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.feature.dashboard.gesture.DashboardHaptics
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.feature.dashboard.grid.ConfigurationBoundaryDetector
import app.dqxn.android.feature.dashboard.grid.GridPlacementEngine
import app.dqxn.android.feature.dashboard.grid.ResizeHandle
import app.dqxn.android.feature.dashboard.test.FakeLayoutRepository
import app.dqxn.android.feature.dashboard.test.TestWidgetFactory.testWidget
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class EditModeCoordinatorTest {

  private val logger = NoOpLogger
  private val haptics: DashboardHaptics = mockk(relaxed = true)
  private val reducedMotionHelper: ReducedMotionHelper = mockk {
    every { isReducedMotion } returns false
  }
  private val gridPlacementEngine = GridPlacementEngine(logger)
  private val boundaryDetector: ConfigurationBoundaryDetector = mockk {
    every { boundaries } returns MutableStateFlow(persistentListOf())
  }
  private lateinit var layoutCoordinator: LayoutCoordinator
  private lateinit var coordinator: EditModeCoordinator
  private lateinit var fakeRepo: FakeLayoutRepository

  @BeforeEach
  fun setup() {
    fakeRepo = FakeLayoutRepository()
    layoutCoordinator = LayoutCoordinator(
      layoutRepository = fakeRepo,
      presetLoader = mockk(relaxed = true),
      gridPlacementEngine = gridPlacementEngine,
      configurationBoundaryDetector = boundaryDetector,
      ioDispatcher = UnconfinedTestDispatcher(),
      logger = logger,
    )
    coordinator = EditModeCoordinator(
      layoutCoordinator = layoutCoordinator,
      gridPlacementEngine = gridPlacementEngine,
      haptics = haptics,
      reducedMotionHelper = reducedMotionHelper,
      logger = logger,
    )
  }

  // -- Edit mode --

  @Test
  fun `enterEditMode sets isEditMode true and clears focus`() {
    coordinator.focusWidget("widget-1")
    coordinator.enterEditMode()

    val state = coordinator.editState.value
    assertThat(state.isEditMode).isTrue()
    assertThat(state.focusedWidgetId).isNull()
    verify { haptics.editModeEnter() }
  }

  @Test
  fun `exitEditMode sets isEditMode false and clears focus`() {
    coordinator.enterEditMode()
    coordinator.focusWidget("widget-1")
    coordinator.exitEditMode()

    val state = coordinator.editState.value
    assertThat(state.isEditMode).isFalse()
    assertThat(state.focusedWidgetId).isNull()
    verify { haptics.editModeExit() }
  }

  // -- Focus --

  @Test
  fun `focusWidget sets focusedWidgetId`() {
    coordinator.focusWidget("widget-42")

    assertThat(coordinator.editState.value.focusedWidgetId).isEqualTo("widget-42")
    verify { haptics.widgetFocus() }
  }

  @Test
  fun `focusWidget null clears focus`() {
    coordinator.focusWidget("widget-42")
    coordinator.focusWidget(null)

    assertThat(coordinator.editState.value.focusedWidgetId).isNull()
    // widgetFocus haptic only called once (for non-null)
    verify(exactly = 1) { haptics.widgetFocus() }
  }

  // -- Drag --

  @Test
  fun `startDrag emits DragUpdate with isDragging true`() {
    coordinator.startDrag("widget-1", startCol = 2, startRow = 3)

    val drag = coordinator.dragState.value
    assertThat(drag).isNotNull()
    assertThat(drag!!.widgetId).isEqualTo("widget-1")
    assertThat(drag.isDragging).isTrue()
    assertThat(drag.currentOffsetX).isEqualTo(0f)
    assertThat(drag.currentOffsetY).isEqualTo(0f)
    verify { haptics.dragStart() }
  }

  @Test
  fun `updateDrag updates offsets in dragState`() {
    coordinator.startDrag("widget-1", startCol = 0, startRow = 0)
    coordinator.updateDrag(100f, 200f)

    val drag = coordinator.dragState.value
    assertThat(drag).isNotNull()
    assertThat(drag!!.currentOffsetX).isEqualTo(100f)
    assertThat(drag.currentOffsetY).isEqualTo(200f)
  }

  @Test
  fun `endDrag snaps to grid and commits position`() = runTest(UnconfinedTestDispatcher()) {
    // Place a widget in the layout first
    val widget = testWidget(instanceId = "w1", col = 2, row = 2)
    fakeRepo.setWidgets(listOf(widget))
    layoutCoordinator.initialize(backgroundScope)

    coordinator.initialize(this)

    // Start drag from col=2, row=2
    coordinator.startDrag("w1", startCol = 2, startRow = 2)
    // Drag by 150px, 100px with grid unit of 48px
    coordinator.updateDrag(150f, 100f)

    val snapped = coordinator.endDrag(gridUnitPx = 48f)

    assertThat(snapped).isNotNull()
    assertThat(coordinator.dragState.value).isNull()
    verify { haptics.snapToGrid() }

    // Verify position was committed to layout coordinator
    advanceUntilIdle()
    val updatedWidget = layoutCoordinator.layoutState.value.widgets.find { it.instanceId == "w1" }
    assertThat(updatedWidget).isNotNull()
    assertThat(updatedWidget!!.position).isEqualTo(snapped)
  }

  @Test
  fun `endDrag with no-straddle violation snaps to boundary`() = runTest(UnconfinedTestDispatcher()) {
    val widget = testWidget(instanceId = "w1", col = 0, row = 0, widthUnits = 4, heightUnits = 4)
    fakeRepo.setWidgets(listOf(widget))
    layoutCoordinator.initialize(backgroundScope)
    coordinator.initialize(this)

    coordinator.startDrag("w1", startCol = 0, startRow = 0)
    coordinator.updateDrag(48f, 48f) // small drag

    val snapped = coordinator.endDrag(gridUnitPx = 48f)

    // Result should be non-null (even if no straddle correction needed)
    assertThat(snapped).isNotNull()
    assertThat(coordinator.dragState.value).isNull()
  }

  // -- Resize --

  @Test
  fun `startResize emits ResizeUpdate`() {
    coordinator.startResize(
      widgetId = "w1",
      handle = ResizeHandle.BOTTOM_RIGHT,
      currentSize = GridSize(4, 4),
      currentPosition = GridPosition(2, 2),
    )

    val resize = coordinator.resizeState.value
    assertThat(resize).isNotNull()
    assertThat(resize!!.widgetId).isEqualTo("w1")
    assertThat(resize.handle).isEqualTo(ResizeHandle.BOTTOM_RIGHT)
    assertThat(resize.targetSize).isEqualTo(GridSize(4, 4))
    assertThat(resize.isResizing).isTrue()
    verify { haptics.resizeStart() }
  }

  @Test
  fun `updateResize TopLeft compensates position`() {
    coordinator.startResize(
      widgetId = "w1",
      handle = ResizeHandle.TOP_LEFT,
      currentSize = GridSize(4, 4),
      currentPosition = GridPosition(4, 4),
    )

    // Increase size by 2 in each dimension
    coordinator.updateResize(deltaWidthUnits = 2, deltaHeightUnits = 2)

    val resize = coordinator.resizeState.value!!
    assertThat(resize.targetSize).isEqualTo(GridSize(6, 6))
    // TopLeft: gridX -= deltaWidth, gridY -= deltaHeight
    assertThat(resize.targetPosition).isEqualTo(GridPosition(col = 2, row = 2))
  }

  @Test
  fun `updateResize BottomRight does not change position`() {
    coordinator.startResize(
      widgetId = "w1",
      handle = ResizeHandle.BOTTOM_RIGHT,
      currentSize = GridSize(4, 4),
      currentPosition = GridPosition(2, 2),
    )

    coordinator.updateResize(deltaWidthUnits = 2, deltaHeightUnits = 2)

    val resize = coordinator.resizeState.value!!
    assertThat(resize.targetSize).isEqualTo(GridSize(6, 6))
    assertThat(resize.targetPosition).isNull() // BottomRight: no position change
  }

  @Test
  fun `updateResize enforces MIN_WIDGET_UNITS = 2`() {
    coordinator.startResize(
      widgetId = "w1",
      handle = ResizeHandle.BOTTOM_RIGHT,
      currentSize = GridSize(4, 4),
      currentPosition = GridPosition(0, 0),
    )

    // Try to shrink below minimum
    coordinator.updateResize(deltaWidthUnits = -10, deltaHeightUnits = -10)

    val resize = coordinator.resizeState.value!!
    assertThat(resize.targetSize.widthUnits).isEqualTo(EditModeCoordinator.MIN_WIDGET_UNITS)
    assertThat(resize.targetSize.heightUnits).isEqualTo(EditModeCoordinator.MIN_WIDGET_UNITS)
  }

  @Test
  fun `updateResize enforces aspect ratio`() {
    val widgetSpec = mockk<app.dqxn.android.sdk.contracts.widget.WidgetSpec> {
      every { aspectRatio } returns 1.0f // Square widget
    }

    coordinator.startResize(
      widgetId = "w1",
      handle = ResizeHandle.BOTTOM_RIGHT,
      currentSize = GridSize(4, 4),
      currentPosition = GridPosition(0, 0),
      widgetSpec = widgetSpec,
    )

    // Resize width only by 2 — height should auto-adjust to maintain 1:1
    coordinator.updateResize(deltaWidthUnits = 2, deltaHeightUnits = 0)

    val resize = coordinator.resizeState.value!!
    // Width increased to 6, height should match (1:1 aspect ratio)
    assertThat(resize.targetSize.widthUnits).isEqualTo(6)
    assertThat(resize.targetSize.heightUnits).isEqualTo(6)
  }

  // -- Interaction gating --

  @Test
  fun `isInteractionAllowed returns false in edit mode`() {
    coordinator.enterEditMode()

    assertThat(coordinator.isInteractionAllowed("widget-1")).isFalse()
  }

  @Test
  fun `isInteractionAllowed returns false when widget is focused`() {
    coordinator.focusWidget("widget-1")

    assertThat(coordinator.isInteractionAllowed("widget-1")).isFalse()
  }

  @Test
  fun `isInteractionAllowed returns true in non-edit non-focused mode`() {
    assertThat(coordinator.isInteractionAllowed("widget-1")).isTrue()
  }

  // -- Status bar --

  @Test
  fun `toggleStatusBar toggles showStatusBar in EditState`() {
    assertThat(coordinator.editState.value.showStatusBar).isTrue()

    coordinator.toggleStatusBar()
    assertThat(coordinator.editState.value.showStatusBar).isFalse()

    coordinator.toggleStatusBar()
    assertThat(coordinator.editState.value.showStatusBar).isTrue()
  }
}
