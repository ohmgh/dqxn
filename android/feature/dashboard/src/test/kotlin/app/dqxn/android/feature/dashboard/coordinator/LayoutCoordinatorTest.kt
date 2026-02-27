package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.data.preset.PresetLoader
import app.dqxn.android.feature.dashboard.grid.ConfigurationBoundaryDetector
import app.dqxn.android.feature.dashboard.grid.GridPlacementEngine
import app.dqxn.android.feature.dashboard.test.FakeLayoutRepository
import app.dqxn.android.feature.dashboard.test.TestWidgetFactory.testWidget
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class LayoutCoordinatorTest {

  private val logger = NoOpLogger

  private val presetLoader: PresetLoader = mockk {
    every { loadPreset(any()) } returns
      listOf(
        testWidget(typeId = "essentials:clock", col = 0, row = 0),
        testWidget(typeId = "essentials:battery", col = 4, row = 0),
      )
  }

  @Test
  fun `initialize loads widgets from repository`() = runTest {
    val fakeRepo = FakeLayoutRepository()
    val coordinator = createCoordinator(fakeRepo, StandardTestDispatcher(testScheduler))

    val w1 = testWidget(typeId = "essentials:clock", col = 0, row = 0)
    val w2 = testWidget(typeId = "essentials:battery", col = 4, row = 0)
    val w3 = testWidget(typeId = "essentials:compass", col = 8, row = 0)
    fakeRepo.setWidgets(listOf(w1, w2, w3))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val state = coordinator.layoutState.value
    assertThat(state.isLoading).isFalse()
    assertThat(state.widgets).hasSize(3)
    assertThat(state.widgets.map { it.typeId })
      .containsExactly("essentials:clock", "essentials:battery", "essentials:compass")

    initJob.cancel()
  }

  @Test
  fun `handleAddWidget places widget via GridPlacementEngine`() = runTest {
    val fakeRepo = FakeLayoutRepository()
    val coordinator = createCoordinator(fakeRepo, StandardTestDispatcher(testScheduler))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val newWidget = testWidget(typeId = "essentials:speed", col = 99, row = 99)
    coordinator.handleAddWidget(newWidget)
    testScheduler.runCurrent()

    val state = coordinator.layoutState.value
    assertThat(state.widgets).hasSize(1)
    val placed = state.widgets.first()
    assertThat(placed.typeId).isEqualTo("essentials:speed")
    assertThat(placed.position.col).isAtMost(20)
    assertThat(placed.position.row).isAtMost(12)

    initJob.cancel()
  }

  @Test
  fun `handleRemoveWidget removes from state`() = runTest {
    val fakeRepo = FakeLayoutRepository()
    val coordinator = createCoordinator(fakeRepo, StandardTestDispatcher(testScheduler))
    val widget = testWidget(instanceId = "remove-me", typeId = "essentials:clock")
    fakeRepo.setWidgets(listOf(widget))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()
    assertThat(coordinator.layoutState.value.widgets).hasSize(1)

    coordinator.handleRemoveWidget("remove-me")
    testScheduler.runCurrent()

    assertThat(coordinator.layoutState.value.widgets).isEmpty()

    initJob.cancel()
  }

  @Test
  fun `handleMoveWidget updates position`() = runTest {
    val fakeRepo = FakeLayoutRepository()
    val coordinator = createCoordinator(fakeRepo, StandardTestDispatcher(testScheduler))
    val widget = testWidget(instanceId = "movable", col = 0, row = 0)
    fakeRepo.setWidgets(listOf(widget))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val newPos = GridPosition(col = 5, row = 3)
    coordinator.handleMoveWidget("movable", newPos)
    testScheduler.runCurrent()

    val moved = coordinator.layoutState.value.widgets.first()
    assertThat(moved.position).isEqualTo(newPos)

    initJob.cancel()
  }

  @Test
  fun `handleResizeWidget updates size and optional position`() = runTest {
    val fakeRepo = FakeLayoutRepository()
    val coordinator = createCoordinator(fakeRepo, StandardTestDispatcher(testScheduler))
    val widget =
      testWidget(instanceId = "resizable", col = 2, row = 2, widthUnits = 4, heightUnits = 4)
    fakeRepo.setWidgets(listOf(widget))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val newSize = GridSize(widthUnits = 6, heightUnits = 6)
    val newPos = GridPosition(col = 0, row = 0)
    coordinator.handleResizeWidget("resizable", newSize, newPos)
    testScheduler.runCurrent()

    val resized = coordinator.layoutState.value.widgets.first()
    assertThat(resized.size).isEqualTo(newSize)
    assertThat(resized.position).isEqualTo(newPos)

    initJob.cancel()
  }

  @Test
  fun `handleResizeWidget keeps position when null`() = runTest {
    val fakeRepo = FakeLayoutRepository()
    val coordinator = createCoordinator(fakeRepo, StandardTestDispatcher(testScheduler))
    val widget =
      testWidget(instanceId = "br-resize", col = 2, row = 2, widthUnits = 4, heightUnits = 4)
    fakeRepo.setWidgets(listOf(widget))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val newSize = GridSize(widthUnits = 6, heightUnits = 6)
    coordinator.handleResizeWidget("br-resize", newSize, null)
    testScheduler.runCurrent()

    val resized = coordinator.layoutState.value.widgets.first()
    assertThat(resized.size).isEqualTo(newSize)
    assertThat(resized.position).isEqualTo(GridPosition(col = 2, row = 2))

    initJob.cancel()
  }

  @Test
  fun `handleResetLayout reloads from PresetLoader`() = runTest {
    val fakeRepo = FakeLayoutRepository()
    val coordinator = createCoordinator(fakeRepo, StandardTestDispatcher(testScheduler))
    val widget = testWidget(instanceId = "old-widget", typeId = "essentials:old")
    fakeRepo.setWidgets(listOf(widget))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()
    assertThat(coordinator.layoutState.value.widgets).hasSize(1)

    coordinator.handleResetLayout()
    testScheduler.runCurrent()

    val state = coordinator.layoutState.value
    assertThat(state.widgets).hasSize(2)
    assertThat(state.widgets.map { it.typeId })
      .containsExactly("essentials:clock", "essentials:battery")

    initJob.cancel()
  }

  @Test
  fun `visibleWidgets filters by viewport`() = runTest {
    val fakeRepo = FakeLayoutRepository()
    val coordinator = createCoordinator(fakeRepo, StandardTestDispatcher(testScheduler))
    val inside =
      testWidget(instanceId = "inside", col = 0, row = 0, widthUnits = 4, heightUnits = 4)
    val outside =
      testWidget(instanceId = "outside", col = 100, row = 100, widthUnits = 4, heightUnits = 4)
    fakeRepo.setWidgets(listOf(inside, outside))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val visible = coordinator.visibleWidgets(viewportCols = 20, viewportRows = 12)
    assertThat(visible).hasSize(1)
    assertThat(visible.first().instanceId).isEqualTo("inside")

    initJob.cancel()
  }

  @Test
  fun `viewport culling returns zero for fully off-screen widgets`() = runTest {
    val fakeRepo = FakeLayoutRepository()
    val coordinator = createCoordinator(fakeRepo, StandardTestDispatcher(testScheduler))
    val farAway =
      testWidget(instanceId = "far", col = 100, row = 100, widthUnits = 4, heightUnits = 4)
    fakeRepo.setWidgets(listOf(farAway))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val visible = coordinator.visibleWidgets(viewportCols = 20, viewportRows = 12)
    assertThat(visible).isEmpty()

    initJob.cancel()
  }

  private fun createCoordinator(
    fakeRepo: FakeLayoutRepository,
    ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(),
  ): LayoutCoordinator =
    LayoutCoordinator(
      layoutRepository = fakeRepo,
      presetLoader = presetLoader,
      gridPlacementEngine = GridPlacementEngine(logger = logger),
      configurationBoundaryDetector =
        ConfigurationBoundaryDetector(
          windowInfoTracker = mockk(relaxed = true),
          logger = logger,
        ),
      ioDispatcher = ioDispatcher,
      logger = logger,
    )
}
