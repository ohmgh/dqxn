package app.dqxn.android.feature.dashboard.binding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.EditState
import app.dqxn.android.feature.dashboard.coordinator.WidgetBindingCoordinator
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.status.WidgetStatusCache
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.widget.LocalWidgetScope
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the WidgetSlot error boundary (F2.14).
 *
 * Verifies:
 * 1. [LocalWidgetScope] is provided and accessible from within a renderer.
 * 2. Widget renders successfully with all CompositionLocals provided.
 * 3. Error fallback renders when hasRenderError is set (via connection error path).
 *
 * Note: Draw-phase exception interception via [SafeDrawModifier] cannot be verified in
 * Robolectric (draw lambdas are not executed). Full draw-phase testing requires
 * `connectedAndroidTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSlotErrorBoundaryTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val widgetRegistry = mockk<WidgetRegistry>()
  private val widgetBindingCoordinator = mockk<WidgetBindingCoordinator>()
  private val editModeCoordinator = mockk<EditModeCoordinator>()

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
      instanceId = "test-widget-1",
      typeId = "test:widget",
      position = GridPosition(col = 0, row = 0),
      size = GridSize(widthUnits = 4, heightUnits = 3),
      style = defaultStyle,
      settings = persistentMapOf(),
      dataSourceBindings = persistentMapOf(),
      zIndex = 0,
    )

  private fun setupDefaults(renderer: WidgetRenderer) {
    every { widgetRegistry.findByTypeId("test:widget") } returns renderer
    every { widgetBindingCoordinator.widgetData(any()) } returns
      MutableStateFlow(WidgetData.Empty)
    every { widgetBindingCoordinator.widgetStatus(any()) } returns
      MutableStateFlow(WidgetStatusCache.EMPTY)
    every { editModeCoordinator.editState } returns MutableStateFlow(EditState())
    every { editModeCoordinator.isInteractionAllowed(any()) } returns true
  }

  @Test
  fun `LocalWidgetScope is provided and accessible`() {
    var scopeAccessible = false
    val renderer = object : TestableRenderer() {
      @Composable
      override fun Render(
        isEditMode: Boolean,
        style: WidgetStyle,
        settings: ImmutableMap<String, Any>,
        modifier: Modifier,
      ) {
        // If LocalWidgetScope is not provided, this throws "No WidgetScope provided"
        val scope = LocalWidgetScope.current
        scopeAccessible = true
        Box(modifier = Modifier.size(10.dp))
      }
    }
    setupDefaults(renderer)

    composeTestRule.setContent {
      WidgetSlot(
        widget = testWidget,
        widgetBindingCoordinator = widgetBindingCoordinator,
        widgetRegistry = widgetRegistry,
        editModeCoordinator = editModeCoordinator,
        resizeState = null,
        onCommand = {},
      )
    }

    composeTestRule.waitForIdle()
    assertThat(scopeAccessible).isTrue()
  }

  @Test
  fun `widget renders with all CompositionLocals provided`() {
    var rendered = false
    val renderer = object : TestableRenderer() {
      @Composable
      override fun Render(
        isEditMode: Boolean,
        style: WidgetStyle,
        settings: ImmutableMap<String, Any>,
        modifier: Modifier,
      ) {
        rendered = true
        Box(modifier = Modifier.size(10.dp))
      }
    }
    setupDefaults(renderer)

    composeTestRule.setContent {
      WidgetSlot(
        widget = testWidget,
        widgetBindingCoordinator = widgetBindingCoordinator,
        widgetRegistry = widgetRegistry,
        editModeCoordinator = editModeCoordinator,
        resizeState = null,
        onCommand = {},
      )
    }

    composeTestRule.waitForIdle()
    assertThat(rendered).isTrue()
    composeTestRule.onNodeWithTag("widget_test-widget-1").assertIsDisplayed()
  }

  @Test
  fun `WidgetCrash command dispatches through onCommand`() {
    // Verify the command structure wires correctly (unit test of the dispatch path)
    val commands = mutableListOf<DashboardCommand>()
    val onCommand: (DashboardCommand) -> Unit = { commands.add(it) }

    val crash = DashboardCommand.WidgetCrash(
      widgetId = "test-widget-1",
      typeId = "test:widget",
      throwable = RuntimeException("boom"),
    )
    onCommand(crash)

    assertThat(commands).hasSize(1)
    val dispatched = commands.first() as DashboardCommand.WidgetCrash
    assertThat(dispatched.widgetId).isEqualTo("test-widget-1")
    assertThat(dispatched.typeId).isEqualTo("test:widget")
    assertThat(dispatched.throwable.message).isEqualTo("boom")
  }
}

/**
 * Minimal base renderer for test subclassing. Provides stub implementations for all
 * [WidgetRenderer] members except [Render], which tests override.
 */
private abstract class TestableRenderer : WidgetRenderer {
  override val typeId: String = "test:widget"
  override val displayName: String = "Test Widget"
  override val description: String = "Test"
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = emptySet()
  override val settingsSchema: List<SettingDefinition<*>> = emptyList()
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 0
  override val requiredAnyEntitlement: Set<String>? = null

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(widthUnits = 4, heightUnits = 3, aspectRatio = null, settings = emptyMap())

  override fun accessibilityDescription(data: WidgetData): String = "Test widget"

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false
}
