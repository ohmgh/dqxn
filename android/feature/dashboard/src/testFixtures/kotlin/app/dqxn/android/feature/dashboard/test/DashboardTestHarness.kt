package app.dqxn.android.feature.dashboard.test

import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.preset.PresetLoader
import app.dqxn.android.feature.dashboard.coordinator.LayoutCoordinator
import app.dqxn.android.feature.dashboard.coordinator.LayoutState
import app.dqxn.android.feature.dashboard.grid.ConfigurationBoundaryDetector
import app.dqxn.android.feature.dashboard.grid.GridPlacementEngine
import app.dqxn.android.feature.dashboard.safety.SafeModeManager
import app.dqxn.android.sdk.observability.log.NoOpLogger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * DSL entry point for coordinator-level testing with real implementations.
 *
 * Uses [StandardTestDispatcher] (never UnconfinedTestDispatcher per CLAUDE.md).
 * [FakeLayoutRepository] replaces the real DataStore-backed implementation.
 *
 * ```kotlin
 * dashboardTest {
 *   val widget = testWidget("essentials:clock")
 *   layoutCoordinator.handleAddWidget(widget)
 *   assertThat(layoutState().widgets).hasSize(1)
 * }
 * ```
 */
public fun dashboardTest(
  block: suspend DashboardTestScope.() -> Unit,
): Unit = runTest {
  val harness = DashboardTestHarness(testScope = this)
  harness.initialize()
  DashboardTestScope(harness).block()
}

/**
 * Test scope exposing coordinator state and convenience methods for dashboard tests.
 */
public class DashboardTestScope(
  private val harness: DashboardTestHarness,
) {
  public val layoutCoordinator: LayoutCoordinator get() = harness.layoutCoordinator
  public val safeModeManager: SafeModeManager get() = harness.safeModeManager
  public val fakeLayoutRepository: FakeLayoutRepository get() = harness.fakeLayoutRepository

  public fun layoutState(): LayoutState = harness.layoutCoordinator.layoutState.value
  public fun safeMode(): Boolean = harness.safeModeManager.safeModeActive.value
}

/**
 * Coordinator test harness that creates real coordinator instances backed by fakes.
 *
 * All coordinators use [StandardTestDispatcher] for deterministic coroutine execution. The
 * [FakeLayoutRepository] replaces DataStore-backed persistence with in-memory storage.
 */
public class DashboardTestHarness(
  private val testScope: TestScope,
) {

  private val testDispatcher = testScope.testScheduler.let { StandardTestDispatcher(it) }
  private val logger = NoOpLogger

  public val fakeLayoutRepository: FakeLayoutRepository = FakeLayoutRepository()

  private val presetLoader: PresetLoader = mockk {
    every { loadPreset(any()) } returns emptyList()
  }

  public val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(logger = logger)

  private val configurationBoundaryDetector: ConfigurationBoundaryDetector =
    ConfigurationBoundaryDetector(
      windowInfoTracker = mockk(relaxed = true),
      logger = logger,
    )

  public val layoutCoordinator: LayoutCoordinator =
    LayoutCoordinator(
      layoutRepository = fakeLayoutRepository,
      presetLoader = presetLoader,
      gridPlacementEngine = gridPlacementEngine,
      configurationBoundaryDetector = configurationBoundaryDetector,
      ioDispatcher = testDispatcher,
      logger = logger,
    )

  private val fakePrefs: FakeSharedPreferences = FakeSharedPreferences()

  public val safeModeManager: SafeModeManager =
    SafeModeManager(
      prefs = fakePrefs,
      logger = logger,
    )

  /** Initialize coordinators. Must be called before accessing state. */
  public fun initialize() {
    layoutCoordinator.initialize(testScope)
  }
}
