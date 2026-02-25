package app.dqxn.android.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetPickerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testTheme =
    DashboardThemeDefinition(
      themeId = "test",
      displayName = "Test",
      primaryTextColor = Color.White,
      secondaryTextColor = Color.Gray,
      accentColor = Color.Cyan,
      widgetBorderColor = Color.Red,
      backgroundBrush = Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
      widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.DarkGray, Color.Black)),
    )

  private val freeEntitlementManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = true
      override fun getActiveEntitlements(): Set<String> = setOf("free")
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  private val noEntitlementManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = false
      override fun getActiveEntitlements(): Set<String> = emptySet()
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  // --- Grouping test ---

  @Test
  fun `widgets grouped under correct pack headers`() {
    val widget1 = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widget2 = createTestWidget("essentials:compass", "Compass")
    val widget3 = createTestWidget("plus:gauge", "Gauge")

    val registry = createWidgetRegistry(widget1, widget2, widget3)

    setContent(registry, freeEntitlementManager)

    // Pack headers use useUnmergedTree because they're inside scrollable Column
    composeTestRule.onNodeWithTag("pack_header_essentials", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("pack_header_plus", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithText("Digital Clock", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithText("Compass", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithText("Gauge", useUnmergedTree = true).assertExists()
  }

  // --- Live preview rendering test ---

  @Test
  fun `widget preview test tags render in picker`() {
    val widget = createTestWidget("essentials:clock-digital", "Digital Clock")
    val registry = createWidgetRegistry(widget)

    setContent(registry, freeEntitlementManager)

    // Preview tag is inside clickable card, so use unmerged tree
    composeTestRule
      .onNodeWithTag("widget_preview_essentials:clock-digital", useUnmergedTree = true)
      .assertExists()
  }

  // --- Entitlement badge test ---

  @Test
  fun `gated widget shows lock icon`() {
    val widget = createTestWidget("plus:gauge", "Gauge", requiredEntitlements = setOf("plus"))
    val registry = createWidgetRegistry(widget)

    setContent(registry, noEntitlementManager)

    // Lock icon is inside clickable card, use unmerged tree
    composeTestRule
      .onNodeWithTag("widget_lock_plus:gauge", useUnmergedTree = true)
      .assertExists()
  }

  @Test
  fun `free widget does not show lock icon`() {
    val widget = createTestWidget("essentials:clock-digital", "Digital Clock")
    val registry = createWidgetRegistry(widget)

    setContent(registry, freeEntitlementManager)

    composeTestRule
      .onNodeWithTag("widget_lock_essentials:clock-digital", useUnmergedTree = true)
      .assertDoesNotExist()
  }

  // --- Selection callback test ---

  @Test
  fun `tapping free widget fires onSelectWidget with typeId`() {
    val widget = createTestWidget("essentials:clock-digital", "Digital Clock")
    val registry = createWidgetRegistry(widget)
    var selectedTypeId: String? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetPicker(
          widgetRegistry = registry,
          entitlementManager = freeEntitlementManager,
          onSelectWidget = { selectedTypeId = it },
          onDismiss = {},
        )
      }
    }

    // Click on widget card -- use unmerged tree since card has clickable modifier
    composeTestRule
      .onNodeWithTag("widget_card_essentials:clock-digital", useUnmergedTree = true)
      .performClick()
    composeTestRule.waitForIdle()

    assertThat(selectedTypeId).isEqualTo("essentials:clock-digital")
  }

  // --- Gated widget blocks selection ---

  @Test
  fun `tapping gated widget does NOT fire onSelectWidget`() {
    val widget = createTestWidget("plus:gauge", "Gauge", requiredEntitlements = setOf("plus"))
    val registry = createWidgetRegistry(widget)
    var selectedTypeId: String? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetPicker(
          widgetRegistry = registry,
          entitlementManager = noEntitlementManager,
          onSelectWidget = { selectedTypeId = it },
          onDismiss = {},
        )
      }
    }

    composeTestRule
      .onNodeWithTag("widget_card_plus:gauge", useUnmergedTree = true)
      .performClick()
    composeTestRule.waitForIdle()

    assertThat(selectedTypeId).isNull()
  }

  // --- Entitlement revocation toast (F8.9) ---

  @Test
  fun `revocation toast fires when gated widget detected`() {
    val widget = createTestWidget("plus:gauge", "Gauge", requiredEntitlements = setOf("plus"))
    val registry = createWidgetRegistry(widget)
    var toastMessage: String? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetPicker(
          widgetRegistry = registry,
          entitlementManager = noEntitlementManager,
          onSelectWidget = {},
          onDismiss = {},
          onRevocationToast = { toastMessage = it },
        )
      }
    }

    composeTestRule.waitForIdle()

    assertThat(toastMessage).contains("no longer available")
  }

  @Test
  fun `no revocation toast when all widgets accessible`() {
    val widget = createTestWidget("essentials:clock-digital", "Digital Clock")
    val registry = createWidgetRegistry(widget)
    var toastMessage: String? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetPicker(
          widgetRegistry = registry,
          entitlementManager = freeEntitlementManager,
          onSelectWidget = {},
          onDismiss = {},
          onRevocationToast = { toastMessage = it },
        )
      }
    }

    composeTestRule.waitForIdle()

    assertThat(toastMessage).isNull()
  }

  // --- Helpers ---

  private fun setContent(
    widgetRegistry: WidgetRegistry,
    entitlementManager: EntitlementManager,
  ) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetPicker(
          widgetRegistry = widgetRegistry,
          entitlementManager = entitlementManager,
          onSelectWidget = {},
          onDismiss = {},
        )
      }
    }
  }

  private fun createTestWidget(
    typeId: String,
    displayName: String,
    requiredEntitlements: Set<String>? = null,
  ): WidgetRenderer {
    val renderer = mockk<WidgetRenderer>(relaxed = true)
    every { renderer.typeId } returns typeId
    every { renderer.displayName } returns displayName
    every { renderer.description } returns "A test widget"
    every { renderer.settingsSchema } returns emptyList()
    every { renderer.compatibleSnapshots } returns emptySet()
    every { renderer.requiredAnyEntitlement } returns requiredEntitlements
    every { renderer.aspectRatio } returns null
    every { renderer.supportsTap } returns false
    every { renderer.priority } returns 0
    return renderer
  }

  private fun createWidgetRegistry(vararg widgets: WidgetRenderer): WidgetRegistry =
    object : WidgetRegistry {
      override fun getAll(): Set<WidgetRenderer> = widgets.toSet()
      override fun findByTypeId(typeId: String): WidgetRenderer? =
        widgets.firstOrNull { it.typeId == typeId }
      override fun getTypeIds(): Set<String> = widgets.map { it.typeId }.toSet()
    }
}
