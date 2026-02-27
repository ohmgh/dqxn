package app.dqxn.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap
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
    // "Gauge" is in the second pack group — scroll lazy grid to ensure it's composed
    composeTestRule
      .onNode(hasScrollAction())
      .performScrollToNode(hasText("Gauge"))
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

  // --- Live preview rendering via Render() ---

  @Test
  fun `widget preview calls Render composable`() {
    var renderCalled = false
    val widget =
      createTestWidget(
        typeId = "essentials:clock-digital",
        displayName = "Digital Clock",
        onRender = { renderCalled = true },
      )
    val registry = createWidgetRegistry(widget)

    setContent(registry, freeEntitlementManager)
    composeTestRule.waitForIdle()

    // Preview tag exists and Render() was invoked
    composeTestRule
      .onNodeWithTag("widget_preview_essentials:clock-digital", useUnmergedTree = true)
      .assertExists()
    assertThat(renderCalled).isTrue()
  }

  // --- Hardware icon badge tests ---

  @Test
  fun `GPS hardware icon shown for speed-compatible widget`() {
    val speedKClass = mockk<KClass<out DataSnapshot>>()
    every { speedKClass.simpleName } returns "SpeedSnapshot"
    val widget =
      createTestWidget(
        typeId = "essentials:speedometer",
        displayName = "Speedometer",
        compatibleSnapshots = setOf(speedKClass),
      )
    val registry = createWidgetRegistry(widget)

    setContent(registry, freeEntitlementManager)

    composeTestRule
      .onNodeWithTag("widget_hw_essentials:speedometer", useUnmergedTree = true)
      .assertExists()
  }

  @Test
  fun `no hardware icon for time-only widget`() {
    val timeKClass = mockk<KClass<out DataSnapshot>>()
    every { timeKClass.simpleName } returns "TimeSnapshot"
    val widget =
      createTestWidget(
        typeId = "essentials:clock-digital",
        displayName = "Digital Clock",
        compatibleSnapshots = setOf(timeKClass),
      )
    val registry = createWidgetRegistry(widget)

    setContent(registry, freeEntitlementManager)

    composeTestRule
      .onNodeWithTag("widget_hw_essentials:clock-digital", useUnmergedTree = true)
      .assertDoesNotExist()
  }

  @Test
  fun `GPS icon for solar-compatible widget`() {
    val solarKClass = mockk<KClass<out DataSnapshot>>()
    every { solarKClass.simpleName } returns "SolarSnapshot"
    val widget =
      createTestWidget(
        typeId = "essentials:solar",
        displayName = "Solar",
        compatibleSnapshots = setOf(solarKClass),
      )
    val registry = createWidgetRegistry(widget)

    setContent(registry, freeEntitlementManager)

    composeTestRule
      .onNodeWithTag("widget_hw_essentials:solar", useUnmergedTree = true)
      .assertExists()
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

  /**
   * Concrete [WidgetRenderer] compiled with the Compose compiler in this module's test source.
   *
   * MockK relaxed mocks cannot be used because [WidgetRenderer.Render] is `@Composable` but
   * `:sdk:contracts` is compiled without the Compose compiler -- MockK proxies get the
   * untransformed method signature, causing [NoSuchMethodError] when called from
   * Compose-compiled code.
   */
  /**
   * Concrete [WidgetRenderer] compiled with the Compose compiler in this module's test source.
   *
   * MockK relaxed mocks cannot be used because [WidgetRenderer.Render] is `@Composable` but
   * `:sdk:contracts` is compiled without the Compose compiler -- MockK proxies get the
   * untransformed method signature, causing [NoSuchMethodError] when called from
   * Compose-compiled code.
   */
  private fun createTestWidget(
    typeId: String,
    displayName: String,
    requiredEntitlements: Set<String>? = null,
    compatibleSnapshots: Set<KClass<out DataSnapshot>> = emptySet(),
    onRender: (() -> Unit)? = null,
  ): WidgetRenderer =
    object : WidgetRenderer {
      override val typeId: String = typeId
      override val displayName: String = displayName
      override val description: String = "A test widget"
      override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = compatibleSnapshots
      override val settingsSchema: List<SettingDefinition<*>> = emptyList()
      override val aspectRatio: Float? = null
      override val supportsTap: Boolean = false
      override val priority: Int = 0
      override val requiredAnyEntitlement: Set<String>? = requiredEntitlements

      @Composable
      override fun Render(
        isEditMode: Boolean,
        style: WidgetStyle,
        settings: ImmutableMap<String, Any>,
        modifier: Modifier,
      ) {
        onRender?.invoke()
      }

      override fun accessibilityDescription(data: WidgetData): String = ""
      override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false
      override fun getDefaults(context: WidgetContext): WidgetDefaults =
        WidgetDefaults(widthUnits = 2, heightUnits = 2, aspectRatio = null, settings = emptyMap())
    }

  private fun createWidgetRegistry(vararg widgets: WidgetRenderer): WidgetRegistry =
    object : WidgetRegistry {
      override fun getAll(): Set<WidgetRenderer> = widgets.toSet()
      override fun findByTypeId(typeId: String): WidgetRenderer? =
        widgets.firstOrNull { it.typeId == typeId }
      override fun getTypeIds(): Set<String> = widgets.map { it.typeId }.toSet()
    }
}
