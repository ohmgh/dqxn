package app.dqxn.android.feature.dashboard.grid

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.EditState
import app.dqxn.android.feature.dashboard.coordinator.WidgetBindingCoordinator
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for corner brackets in edit mode (F1.11) and dashboard grid tag presence.
 *
 * Uses Robolectric + compose-ui-test to verify bracket Canvas test tags appear in edit mode and are
 * absent otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CornerBracketTest {

  @get:Rule val composeTestRule = createComposeRule()

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

  private val testWidget =
    DashboardWidgetInstance(
      instanceId = "w1",
      typeId = "essentials:clock-digital",
      position = GridPosition(col = 0, row = 0),
      size = GridSize(widthUnits = 4, heightUnits = 4),
      style = defaultStyle,
      settings = persistentMapOf(),
      dataSourceBindings = persistentMapOf(),
      zIndex = 0,
    )

  private fun createMocks(
    isEditMode: Boolean
  ): Triple<EditModeCoordinator, WidgetBindingCoordinator, WidgetRegistry> {
    val editModeCoordinator = mockk<EditModeCoordinator>(relaxed = true)
    every { editModeCoordinator.editState } returns
      MutableStateFlow(EditState(isEditMode = isEditMode))
    every { editModeCoordinator.animatingWidgets } returns MutableStateFlow(emptySet())

    val widgetBindingCoordinator = mockk<WidgetBindingCoordinator>(relaxed = true)
    val widgetRegistry = mockk<WidgetRegistry>(relaxed = true)
    return Triple(editModeCoordinator, widgetBindingCoordinator, widgetRegistry)
  }

  @Test
  fun `bracket test tags exist in edit mode`() {
    val (editModeCoordinator, widgetBindingCoordinator, widgetRegistry) = createMocks(true)
    val reducedMotionHelper = mockk<ReducedMotionHelper>(relaxed = true)
    every { reducedMotionHelper.isReducedMotion } returns false
    val widgetGestureHandler = mockk<WidgetGestureHandler>(relaxed = true)
    val blankSpaceGestureHandler = mockk<BlankSpaceGestureHandler>(relaxed = true)

    composeTestRule.setContent {
      DashboardGrid(
        widgets = persistentListOf(testWidget),
        viewportCols = 20,
        viewportRows = 12,
        editState = EditState(isEditMode = true),
        dragState = null,
        resizeState = null,
        configurationBoundaries = persistentListOf(),
        widgetBindingCoordinator = widgetBindingCoordinator,
        widgetRegistry = widgetRegistry,
        editModeCoordinator = editModeCoordinator,
        reducedMotionHelper = reducedMotionHelper,
        widgetGestureHandler = widgetGestureHandler,
        blankSpaceGestureHandler = blankSpaceGestureHandler,
        onCommand = {},
      )
    }

    composeTestRule.onNodeWithTag("bracket_w1", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `brackets do not render outside edit mode`() {
    val (editModeCoordinator, widgetBindingCoordinator, widgetRegistry) = createMocks(false)
    val reducedMotionHelper = mockk<ReducedMotionHelper>(relaxed = true)
    every { reducedMotionHelper.isReducedMotion } returns false
    val widgetGestureHandler = mockk<WidgetGestureHandler>(relaxed = true)
    val blankSpaceGestureHandler = mockk<BlankSpaceGestureHandler>(relaxed = true)

    composeTestRule.setContent {
      DashboardGrid(
        widgets = persistentListOf(testWidget),
        viewportCols = 20,
        viewportRows = 12,
        editState = EditState(isEditMode = false),
        dragState = null,
        resizeState = null,
        configurationBoundaries = persistentListOf(),
        widgetBindingCoordinator = widgetBindingCoordinator,
        widgetRegistry = widgetRegistry,
        editModeCoordinator = editModeCoordinator,
        reducedMotionHelper = reducedMotionHelper,
        widgetGestureHandler = widgetGestureHandler,
        blankSpaceGestureHandler = blankSpaceGestureHandler,
        onCommand = {},
      )
    }

    composeTestRule.onNodeWithTag("bracket_w1").assertDoesNotExist()
  }

  @Test
  fun `dashboard grid tag exists`() {
    val (editModeCoordinator, widgetBindingCoordinator, widgetRegistry) = createMocks(false)
    val reducedMotionHelper = mockk<ReducedMotionHelper>(relaxed = true)
    every { reducedMotionHelper.isReducedMotion } returns false
    val widgetGestureHandler = mockk<WidgetGestureHandler>(relaxed = true)
    val blankSpaceGestureHandler = mockk<BlankSpaceGestureHandler>(relaxed = true)

    composeTestRule.setContent {
      DashboardGrid(
        widgets = persistentListOf(),
        viewportCols = 20,
        viewportRows = 12,
        editState = EditState(),
        dragState = null,
        resizeState = null,
        configurationBoundaries = persistentListOf(),
        widgetBindingCoordinator = widgetBindingCoordinator,
        widgetRegistry = widgetRegistry,
        editModeCoordinator = editModeCoordinator,
        reducedMotionHelper = reducedMotionHelper,
        widgetGestureHandler = widgetGestureHandler,
        blankSpaceGestureHandler = blankSpaceGestureHandler,
        onCommand = {},
      )
    }

    composeTestRule.onNodeWithTag("dashboard_grid").assertExists()
  }
}
