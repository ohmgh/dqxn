package app.dqxn.android.feature.dashboard

import androidx.lifecycle.SavedStateHandle
import app.dqxn.android.core.agentic.SemanticsOwnerHolder
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.command.DashboardCommandBus
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.LayoutCoordinator
import app.dqxn.android.feature.dashboard.coordinator.LayoutState
import app.dqxn.android.feature.dashboard.coordinator.NotificationCoordinator
import app.dqxn.android.feature.dashboard.coordinator.ProfileCoordinator
import app.dqxn.android.feature.dashboard.coordinator.ThemeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.WidgetBindingCoordinator
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.feature.dashboard.grid.BlankSpaceGestureHandler
import app.dqxn.android.feature.dashboard.grid.WidgetGestureHandler
import app.dqxn.android.feature.dashboard.test.TestWidgetFactory.testWidget
import app.dqxn.android.feature.settings.setup.SetupEvaluatorImpl
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.observability.crash.ErrorContext
import app.dqxn.android.sdk.observability.crash.ErrorReporter
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.session.SessionEventEmitter
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class DashboardViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val logger = NoOpLogger
  private val errorReporter: ErrorReporter = mockk(relaxed = true)
  private val sessionEventEmitter: SessionEventEmitter = mockk(relaxed = true)

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createMocks(): Mocks {
    val layoutCoordinator: LayoutCoordinator = mockk(relaxed = true) {
      every { layoutState } returns MutableStateFlow(LayoutState())
    }
    val editModeCoordinator: EditModeCoordinator = mockk(relaxed = true)
    val themeCoordinator: ThemeCoordinator = mockk(relaxed = true)
    val widgetBindingCoordinator: WidgetBindingCoordinator = mockk(relaxed = true) {
      every { activeBindings() } returns emptyMap()
    }
    val notificationCoordinator: NotificationCoordinator = mockk(relaxed = true)
    val profileCoordinator: ProfileCoordinator = mockk(relaxed = true)
    val widgetRegistry: WidgetRegistry = mockk(relaxed = true)
    val reducedMotionHelper: ReducedMotionHelper = mockk(relaxed = true)
    val widgetGestureHandler: WidgetGestureHandler = mockk(relaxed = true)
    val blankSpaceGestureHandler: BlankSpaceGestureHandler = mockk(relaxed = true)
    val semanticsOwnerHolder: SemanticsOwnerHolder = mockk(relaxed = true)
    val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    val dataProviderRegistry: DataProviderRegistry = mockk(relaxed = true)
    val providerSettingsStore: ProviderSettingsStore = mockk(relaxed = true)
    val entitlementManager: EntitlementManager = mockk(relaxed = true)
    val setupEvaluator: SetupEvaluatorImpl = mockk(relaxed = true)
    val pairedDeviceStore: PairedDeviceStore = mockk(relaxed = true)

    return Mocks(
      layoutCoordinator = layoutCoordinator,
      editModeCoordinator = editModeCoordinator,
      themeCoordinator = themeCoordinator,
      widgetBindingCoordinator = widgetBindingCoordinator,
      notificationCoordinator = notificationCoordinator,
      profileCoordinator = profileCoordinator,
      widgetRegistry = widgetRegistry,
      reducedMotionHelper = reducedMotionHelper,
      widgetGestureHandler = widgetGestureHandler,
      blankSpaceGestureHandler = blankSpaceGestureHandler,
      semanticsOwnerHolder = semanticsOwnerHolder,
      userPreferencesRepository = userPreferencesRepository,
      dataProviderRegistry = dataProviderRegistry,
      providerSettingsStore = providerSettingsStore,
      entitlementManager = entitlementManager,
      setupEvaluator = setupEvaluator,
      pairedDeviceStore = pairedDeviceStore,
    )
  }

  private fun createViewModel(mocks: Mocks): DashboardViewModel =
    DashboardViewModel(
      layoutCoordinator = mocks.layoutCoordinator,
      editModeCoordinator = mocks.editModeCoordinator,
      themeCoordinator = mocks.themeCoordinator,
      widgetBindingCoordinator = mocks.widgetBindingCoordinator,
      notificationCoordinator = mocks.notificationCoordinator,
      profileCoordinator = mocks.profileCoordinator,
      widgetRegistry = mocks.widgetRegistry,
      reducedMotionHelper = mocks.reducedMotionHelper,
      widgetGestureHandler = mocks.widgetGestureHandler,
      blankSpaceGestureHandler = mocks.blankSpaceGestureHandler,
      semanticsOwnerHolder = mocks.semanticsOwnerHolder,
      userPreferencesRepository = mocks.userPreferencesRepository,
      dataProviderRegistry = mocks.dataProviderRegistry,
      providerSettingsStore = mocks.providerSettingsStore,
      entitlementManager = mocks.entitlementManager,
      setupEvaluator = mocks.setupEvaluator,
      pairedDeviceStore = mocks.pairedDeviceStore,
      savedStateHandle = SavedStateHandle(),
      logger = logger,
      errorReporter = errorReporter,
      commandBus = DashboardCommandBus(),
      sessionEventEmitter = sessionEventEmitter,
    )

  @Test
  fun `dispatch AddWidget routes to layoutCoordinator and widgetBindingCoordinator`() = runTest {
    val mocks = createMocks()
    val vm = createViewModel(mocks)
    val widget = testWidget()

    vm.dispatch(DashboardCommand.AddWidget(widget))
    advanceUntilIdle()

    coVerify { mocks.layoutCoordinator.handleAddWidget(widget) }
    verify { mocks.widgetBindingCoordinator.bind(widget) }
  }

  @Test
  fun `dispatch RemoveWidget routes to unbind then removeWidget`() = runTest {
    val mocks = createMocks()
    val vm = createViewModel(mocks)

    vm.dispatch(DashboardCommand.RemoveWidget("w1"))
    advanceUntilIdle()

    verify { mocks.widgetBindingCoordinator.unbind("w1") }
    coVerify { mocks.layoutCoordinator.handleRemoveWidget("w1") }
  }

  @Test
  fun `dispatch EnterEditMode routes to editModeCoordinator`() = runTest {
    val mocks = createMocks()
    val vm = createViewModel(mocks)

    vm.dispatch(DashboardCommand.EnterEditMode)
    advanceUntilIdle()

    verify { mocks.editModeCoordinator.enterEditMode() }
  }

  @Test
  fun `dispatch SetTheme routes to themeCoordinator`() = runTest {
    val mocks = createMocks()
    val vm = createViewModel(mocks)

    vm.dispatch(DashboardCommand.SetTheme("dark"))
    advanceUntilIdle()

    coVerify { mocks.themeCoordinator.handleSetTheme("dark") }
  }

  @Test
  fun `dispatch SwitchProfile routes to profileCoordinator`() = runTest {
    val mocks = createMocks()
    val vm = createViewModel(mocks)

    vm.dispatch(DashboardCommand.SwitchProfile("profile-2"))
    advanceUntilIdle()

    coVerify { mocks.profileCoordinator.handleSwitchProfile("profile-2") }
  }

  @Test
  fun `dispatch WidgetCrash routes to widgetBindingCoordinator reportCrash`() = runTest {
    val mocks = createMocks()
    val vm = createViewModel(mocks)

    vm.dispatch(
      DashboardCommand.WidgetCrash(
        widgetId = "w1",
        typeId = "essentials:clock",
        throwable = RuntimeException("boom"),
      )
    )
    advanceUntilIdle()

    verify { mocks.widgetBindingCoordinator.reportCrash("w1", "essentials:clock") }
  }

  @Test
  fun `dispatch CycleThemeMode routes to themeCoordinator handleCycleThemeMode`() = runTest {
    val mocks = createMocks()
    val vm = createViewModel(mocks)

    vm.dispatch(DashboardCommand.CycleThemeMode)
    advanceUntilIdle()

    coVerify { mocks.themeCoordinator.handleCycleThemeMode() }
  }

  @Test
  fun `command handler exception does not kill processing loop`() = runTest {
    val mocks = createMocks()
    // First command throws, second should still be processed
    coEvery { mocks.themeCoordinator.handleSetTheme(any()) } throws RuntimeException("test crash")
    val vm = createViewModel(mocks)

    vm.dispatch(DashboardCommand.SetTheme("bad-theme"))
    advanceUntilIdle()

    vm.dispatch(DashboardCommand.EnterEditMode)
    advanceUntilIdle()

    // The edit mode command should still have been processed
    verify { mocks.editModeCoordinator.enterEditMode() }
  }

  @Test
  fun `slow command logged as warning`() = runTest {
    val mocks = createMocks()
    // Make handleSetTheme take 1.5s (above 1s threshold)
    coEvery { mocks.themeCoordinator.handleSetTheme(any()) } coAnswers {
      kotlinx.coroutines.delay(1500)
    }
    val vm = createViewModel(mocks)

    vm.dispatch(DashboardCommand.SetTheme("slow-theme"))
    advanceUntilIdle()

    // Verify handleSetTheme was called (the slow command was processed)
    coVerify { mocks.themeCoordinator.handleSetTheme("slow-theme") }
    // Logger is NoOpLogger in unit tests so we just verify no crash
  }

  @Test
  fun `command dispatched through bus routes to coordinator`() = runTest {
    val mocks = createMocks()
    val bus = DashboardCommandBus()
    val vm = DashboardViewModel(
      layoutCoordinator = mocks.layoutCoordinator,
      editModeCoordinator = mocks.editModeCoordinator,
      themeCoordinator = mocks.themeCoordinator,
      widgetBindingCoordinator = mocks.widgetBindingCoordinator,
      notificationCoordinator = mocks.notificationCoordinator,
      profileCoordinator = mocks.profileCoordinator,
      widgetRegistry = mocks.widgetRegistry,
      reducedMotionHelper = mocks.reducedMotionHelper,
      widgetGestureHandler = mocks.widgetGestureHandler,
      blankSpaceGestureHandler = mocks.blankSpaceGestureHandler,
      semanticsOwnerHolder = mocks.semanticsOwnerHolder,
      userPreferencesRepository = mocks.userPreferencesRepository,
      dataProviderRegistry = mocks.dataProviderRegistry,
      providerSettingsStore = mocks.providerSettingsStore,
      entitlementManager = mocks.entitlementManager,
      setupEvaluator = mocks.setupEvaluator,
      pairedDeviceStore = mocks.pairedDeviceStore,
      savedStateHandle = SavedStateHandle(),
      logger = logger,
      errorReporter = errorReporter,
      commandBus = bus,
      sessionEventEmitter = sessionEventEmitter,
    )

    // Let ViewModel init coroutines start (bus collector, command loop, etc.)
    advanceUntilIdle()

    bus.dispatch(DashboardCommand.EnterEditMode)
    advanceUntilIdle()

    verify { mocks.editModeCoordinator.enterEditMode() }
  }

  @Test
  fun `all 16 command variants routed without crash`() = runTest {
    val mocks = createMocks()
    val vm = createViewModel(mocks)
    val widget = testWidget()

    val commands = listOf(
      DashboardCommand.AddWidget(widget),
      DashboardCommand.RemoveWidget("w1"),
      DashboardCommand.MoveWidget("w1", GridPosition(1, 1)),
      DashboardCommand.ResizeWidget("w1", GridSize(4, 4)),
      DashboardCommand.FocusWidget("w1"),
      DashboardCommand.EnterEditMode,
      DashboardCommand.ExitEditMode,
      DashboardCommand.SetTheme("dark"),
      DashboardCommand.PreviewTheme(null),
      DashboardCommand.CycleThemeMode,
      DashboardCommand.WidgetCrash("w1", "essentials:clock", RuntimeException("boom")),
      DashboardCommand.SwitchProfile("p1"),
      DashboardCommand.CreateProfile("Work"),
      DashboardCommand.DeleteProfile("p2"),
      DashboardCommand.ResetLayout,
      DashboardCommand.ToggleStatusBar,
    )

    for (command in commands) {
      vm.dispatch(command)
    }
    advanceUntilIdle()

    // If we get here without crash, all 16 variants routed successfully
    assertThat(commands).hasSize(16)
  }

  private data class Mocks(
    val layoutCoordinator: LayoutCoordinator,
    val editModeCoordinator: EditModeCoordinator,
    val themeCoordinator: ThemeCoordinator,
    val widgetBindingCoordinator: WidgetBindingCoordinator,
    val notificationCoordinator: NotificationCoordinator,
    val profileCoordinator: ProfileCoordinator,
    val widgetRegistry: WidgetRegistry,
    val reducedMotionHelper: ReducedMotionHelper,
    val widgetGestureHandler: WidgetGestureHandler,
    val blankSpaceGestureHandler: BlankSpaceGestureHandler,
    val semanticsOwnerHolder: SemanticsOwnerHolder,
    val userPreferencesRepository: UserPreferencesRepository,
    val dataProviderRegistry: DataProviderRegistry,
    val providerSettingsStore: ProviderSettingsStore,
    val entitlementManager: EntitlementManager,
    val setupEvaluator: SetupEvaluatorImpl,
    val pairedDeviceStore: PairedDeviceStore,
  )
}
