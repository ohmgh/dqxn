package app.dqxn.android.feature.dashboard.session

import androidx.lifecycle.SavedStateHandle
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.feature.dashboard.DashboardViewModel
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.command.DashboardCommandBus
import app.dqxn.android.feature.dashboard.coordinator.LayoutCoordinator
import app.dqxn.android.feature.dashboard.coordinator.LayoutState
import app.dqxn.android.feature.dashboard.coordinator.WidgetBindingCoordinator
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.session.EventType
import app.dqxn.android.sdk.observability.session.SessionEvent
import app.dqxn.android.sdk.observability.session.SessionEventEmitter
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

/**
 * Verifies that DashboardViewModel records session events via [SessionEventEmitter] when dashboard
 * interactions (tap, move, resize, navigation) occur through the command channel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class SessionEventEmissionTest {

  private val testDispatcher = StandardTestDispatcher()
  private val logger = NoOpLogger
  private val sessionEventEmitter: SessionEventEmitter = mockk(relaxed = true)

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): DashboardViewModel {
    val layoutCoordinator: LayoutCoordinator =
      mockk(relaxed = true) { every { layoutState } returns MutableStateFlow(LayoutState()) }
    val widgetBindingCoordinator: WidgetBindingCoordinator =
      mockk(relaxed = true) { every { activeBindings() } returns emptyMap() }

    return DashboardViewModel(
      layoutCoordinator = layoutCoordinator,
      editModeCoordinator = mockk(relaxed = true),
      themeCoordinator = mockk(relaxed = true),
      widgetBindingCoordinator = widgetBindingCoordinator,
      notificationCoordinator = mockk(relaxed = true),
      profileCoordinator = mockk(relaxed = true),
      widgetRegistry = mockk(relaxed = true),
      reducedMotionHelper = mockk(relaxed = true),
      widgetGestureHandler = mockk(relaxed = true),
      blankSpaceGestureHandler = mockk(relaxed = true),
      semanticsOwnerHolder = mockk(relaxed = true),
      userPreferencesRepository = mockk(relaxed = true),
      dataProviderRegistry = mockk(relaxed = true),
      providerSettingsStore = mockk(relaxed = true),
      entitlementManager = mockk(relaxed = true),
      setupEvaluator = mockk(relaxed = true),
      pairedDeviceStore = mockk(relaxed = true),
      builtInThemes = mockk(relaxed = true),
      savedStateHandle = SavedStateHandle(),
      logger = logger,
      errorReporter = mockk(relaxed = true),
      commandBus = DashboardCommandBus(),
      sessionEventEmitter = sessionEventEmitter,
    )
  }

  @Test
  fun `widget tap emits TAP event with widgetId`() = runTest {
    val vm = createViewModel()

    vm.dispatch(DashboardCommand.FocusWidget("widget-123"))
    advanceUntilIdle()

    val eventSlot = slot<SessionEvent>()
    verify { sessionEventEmitter.record(capture(eventSlot)) }
    assertThat(eventSlot.captured.type).isEqualTo(EventType.TAP)
    assertThat(eventSlot.captured.details).contains("widget-123")
  }

  @Test
  fun `widget move emits MOVE event with widgetId and coordinates`() = runTest {
    val vm = createViewModel()

    vm.dispatch(DashboardCommand.MoveWidget("widget-456", GridPosition(3, 5)))
    advanceUntilIdle()

    val eventSlot = slot<SessionEvent>()
    verify { sessionEventEmitter.record(capture(eventSlot)) }
    assertThat(eventSlot.captured.type).isEqualTo(EventType.MOVE)
    assertThat(eventSlot.captured.details).contains("widget-456")
    assertThat(eventSlot.captured.details).contains("3")
    assertThat(eventSlot.captured.details).contains("5")
  }

  @Test
  fun `widget resize emits RESIZE event with widgetId and dimensions`() = runTest {
    val vm = createViewModel()

    vm.dispatch(DashboardCommand.ResizeWidget("widget-789", GridSize(4, 6)))
    advanceUntilIdle()

    val eventSlot = slot<SessionEvent>()
    verify { sessionEventEmitter.record(capture(eventSlot)) }
    assertThat(eventSlot.captured.type).isEqualTo(EventType.RESIZE)
    assertThat(eventSlot.captured.details).contains("widget-789")
    assertThat(eventSlot.captured.details).contains("4x6")
  }

  @Test
  fun `navigation emits NAVIGATE event with route name`() = runTest {
    val vm = createViewModel()

    vm.dispatch(DashboardCommand.SwitchProfile("home-profile"))
    advanceUntilIdle()

    val eventSlot = slot<SessionEvent>()
    verify { sessionEventEmitter.record(capture(eventSlot)) }
    assertThat(eventSlot.captured.type).isEqualTo(EventType.NAVIGATE)
    assertThat(eventSlot.captured.details).contains("home-profile")
  }
}
