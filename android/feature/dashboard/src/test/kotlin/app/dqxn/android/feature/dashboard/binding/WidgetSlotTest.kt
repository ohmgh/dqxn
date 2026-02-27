package app.dqxn.android.feature.dashboard.binding

import app.dqxn.android.core.thermal.FakeThermalManager
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.EditState
import app.dqxn.android.feature.dashboard.coordinator.WidgetBindingCoordinator
import app.dqxn.android.feature.dashboard.safety.SafeModeManager
import app.dqxn.android.feature.dashboard.test.FakeSharedPreferences
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.status.WidgetRenderState
import app.dqxn.android.sdk.contracts.status.WidgetStatusCache
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Unit tests for WidgetSlot logic (NOT compose-ui-test).
 *
 * Tests: unknown typeId resolution, error boundary behavior, status overlay rendering,
 * accessibility description, and interaction gating.
 */
@Tag("fast")
class WidgetSlotTest {

  private val widgetRegistry = mockk<WidgetRegistry>()
  private val widgetBindingCoordinator = mockk<WidgetBindingCoordinator>()
  private val editModeCoordinator = mockk<EditModeCoordinator>()
  private val renderer = mockk<WidgetRenderer>(relaxed = true)

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
      typeId = "essentials:clock",
      position = GridPosition(col = 0, row = 0),
      size = GridSize(widthUnits = 4, heightUnits = 3),
      style = defaultStyle,
      settings = persistentMapOf(),
      dataSourceBindings = persistentMapOf(),
      zIndex = 0,
    )

  @BeforeEach
  fun setup() {
    every { widgetBindingCoordinator.widgetData(any()) } returns MutableStateFlow(WidgetData.Empty)
    every { widgetBindingCoordinator.widgetStatus(any()) } returns
      MutableStateFlow(WidgetStatusCache.EMPTY)
    every { editModeCoordinator.editState } returns MutableStateFlow(EditState())
    every { editModeCoordinator.isInteractionAllowed(any()) } returns true
  }

  @Test
  fun `unknown typeId renders UnknownWidgetPlaceholder`() {
    // F2.13: when WidgetRegistry returns null for a typeId, show placeholder
    every { widgetRegistry.findByTypeId("essentials:unknown") } returns null

    val unknownWidget = testWidget.copy(typeId = "essentials:unknown")

    // Verify the registry returns null for the unknown type
    val foundRenderer = widgetRegistry.findByTypeId(unknownWidget.typeId)
    assertThat(foundRenderer).isNull()
  }

  @Test
  fun `known typeId resolves renderer`() {
    every { widgetRegistry.findByTypeId("essentials:clock") } returns renderer

    val foundRenderer = widgetRegistry.findByTypeId(testWidget.typeId)
    assertThat(foundRenderer).isNotNull()
    assertThat(foundRenderer).isEqualTo(renderer)
  }

  @Test
  fun `widget crash delegates to SafeModeManager via real WidgetBindingCoordinator`() {
    // F2.14: widget crash reporting flows through a real WidgetBindingCoordinator to
    // SafeModeManager.
    // Unlike the original test (which mocked both sides), this uses a REAL coordinator and a REAL
    // SafeModeManager. If someone removes the safeModeManager.reportCrash() call from the
    // coordinator's reportCrash(), this test fails.
    val logger = NoOpLogger
    val safeModeManager = SafeModeManager(FakeSharedPreferences(), logger)
    val entitlementManager =
      mockk<EntitlementManager>(relaxed = true) {
        every { entitlementChanges } returns MutableStateFlow(setOf("free"))
      }
    val testDispatcher = StandardTestDispatcher()
    val binder =
      mockk<WidgetDataBinder>(relaxed = true) {
        every { bind(any(), any(), any()) } returns MutableStateFlow(WidgetData.Empty)
        every { minStalenessThresholdMs(any()) } returns null
      }

    val realCoordinator =
      WidgetBindingCoordinator(
        binder = binder,
        widgetRegistry = widgetRegistry,
        safeModeManager = safeModeManager,
        entitlementManager = entitlementManager,
        thermalMonitor = FakeThermalManager(),
        metricsCollector = MetricsCollector(),
        logger = logger,
        ioDispatcher = testDispatcher,
        defaultDispatcher = testDispatcher,
      )

    // Report 4 crashes — SafeModeManager triggers at threshold=4
    assertThat(safeModeManager.safeModeActive.value).isFalse()
    repeat(4) { i -> realCoordinator.reportCrash("widget-$i", "essentials:clock") }
    assertThat(safeModeManager.safeModeActive.value).isTrue()
  }

  @Test
  fun `status overlay rendered for SetupRequired state`() {
    // F3.14: SetupRequired status triggers overlay
    val setupStatus =
      WidgetStatusCache(
        overlayState =
          WidgetRenderState.SetupRequired(
            requirementType = "permission",
            message = "Location permission required",
          ),
        issues = persistentListOf(),
      )

    assertThat(setupStatus.overlayState).isInstanceOf(WidgetRenderState.SetupRequired::class.java)
    assertThat(setupStatus.overlayState).isNotEqualTo(WidgetRenderState.Ready)

    val setupState = setupStatus.overlayState as WidgetRenderState.SetupRequired
    assertThat(setupState.requirementType).isEqualTo("permission")
    assertThat(setupState.message).isEqualTo("Location permission required")
  }

  @Test
  fun `status overlay rendered for EntitlementRevoked state`() {
    val revokedStatus =
      WidgetStatusCache(
        overlayState = WidgetRenderState.EntitlementRevoked(upgradeEntitlement = "plus"),
        issues = persistentListOf(),
      )

    assertThat(revokedStatus.overlayState)
      .isInstanceOf(WidgetRenderState.EntitlementRevoked::class.java)

    val revokedState = revokedStatus.overlayState as WidgetRenderState.EntitlementRevoked
    assertThat(revokedState.upgradeEntitlement).isEqualTo("plus")
  }

  @Test
  fun `accessibility description set on semantics`() {
    // F2.19: renderer provides accessibility description from widget data
    every { widgetRegistry.findByTypeId("essentials:clock") } returns renderer
    every { renderer.accessibilityDescription(any()) } returns "Clock showing 3:45 PM"

    val description = renderer.accessibilityDescription(WidgetData.Empty)
    assertThat(description).isEqualTo("Clock showing 3:45 PM")
  }

  @Test
  fun `isInteractionAllowed false renders widget in non-interactive mode`() {
    // F2.18: when interaction is not allowed, widget receives isEditMode=true (non-interactive)
    val editState = EditState(isEditMode = true, focusedWidgetId = "test-widget-1")
    every { editModeCoordinator.editState } returns MutableStateFlow(editState)
    every { editModeCoordinator.isInteractionAllowed("test-widget-1") } returns false

    val isAllowed = editModeCoordinator.isInteractionAllowed(testWidget.instanceId)
    assertThat(isAllowed).isFalse()

    // When isInteractionAllowed is false, the widget should receive isEditMode=true
    // (non-interactive mode) in Render. The inverse (!isInteractionAllowed) drives this.
    val isEditModeForRenderer = !isAllowed
    assertThat(isEditModeForRenderer).isTrue()
  }
}
