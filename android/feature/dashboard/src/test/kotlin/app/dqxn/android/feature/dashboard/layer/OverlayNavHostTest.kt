package app.dqxn.android.feature.dashboard.layer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.rememberNavController
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.feature.settings.main.MainSettingsViewModel
import app.dqxn.android.feature.settings.setup.SetupEvaluatorImpl
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric Compose tests for [OverlayNavHost] route rendering and back navigation.
 *
 * Tests verify:
 * - Empty route renders (no overlay content visible)
 * - Settings route renders overlay scaffold
 * - WidgetPicker route renders picker content
 * - Back navigation from Settings returns to EmptyRoute
 * - WidgetSettings back-stack preservation when Setup pushed on top
 */
@RunWith(RobolectricTestRunner::class)
class OverlayNavHostTest {

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

  private val mockWidgetRegistry = mockk<WidgetRegistry>(relaxed = true).also {
    every { it.getAll() } returns emptySet()
    every { it.findByTypeId(any()) } returns null
  }

  private val mockDataProviderRegistry = mockk<DataProviderRegistry>(relaxed = true).also {
    every { it.getAll() } returns emptySet()
  }

  private val mockProviderSettingsStore = mockk<ProviderSettingsStore>(relaxed = true)

  private val mockEntitlementManager = mockk<EntitlementManager>(relaxed = true).also {
    every { it.hasEntitlement(any()) } returns true
  }

  private val mockSetupEvaluator = mockk<SetupEvaluatorImpl>(relaxed = true)

  private val mockPairedDeviceStore = mockk<PairedDeviceStore>(relaxed = true)

  private val mockMainSettingsViewModel = mockk<MainSettingsViewModel>(relaxed = true).also {
    every { it.analyticsConsent } returns MutableStateFlow(false)
    every { it.showStatusBar } returns MutableStateFlow(false)
    every { it.keepScreenOn } returns MutableStateFlow(true)
  }

  // --- Empty route renders ---

  @Test
  fun `empty route renders no overlay content`() {
    var navController: androidx.navigation.NavHostController? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        navController = rememberNavController()
        OverlayNavHost(
          navController = navController!!,
          widgetRegistry = mockWidgetRegistry,
          dataProviderRegistry = mockDataProviderRegistry,
          providerSettingsStore = mockProviderSettingsStore,
          entitlementManager = mockEntitlementManager,
          setupEvaluator = mockSetupEvaluator,
          pairedDeviceStore = mockPairedDeviceStore,
          mainSettingsViewModel = mockMainSettingsViewModel,
          onCommand = {},
        )
      }
    }

    // EmptyRoute is start destination -- no overlay scaffolds should be visible
    composeTestRule.onNodeWithTag("overlay_scaffold_hub").assertDoesNotExist()
    composeTestRule.onNodeWithTag("overlay_scaffold_preview").assertDoesNotExist()
  }

  // --- Settings route renders ---

  @Test
  fun `settings route renders overlay content`() {
    var navController: androidx.navigation.NavHostController? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        navController = rememberNavController()
        OverlayNavHost(
          navController = navController!!,
          widgetRegistry = mockWidgetRegistry,
          dataProviderRegistry = mockDataProviderRegistry,
          providerSettingsStore = mockProviderSettingsStore,
          entitlementManager = mockEntitlementManager,
          setupEvaluator = mockSetupEvaluator,
          pairedDeviceStore = mockPairedDeviceStore,
          mainSettingsViewModel = mockMainSettingsViewModel,
          onCommand = {},
        )
      }
    }

    composeTestRule.runOnUiThread {
      navController!!.navigate(SettingsRoute)
    }

    composeTestRule.waitForIdle()

    // Settings uses OverlayType.Hub -- verify scaffold appears
    composeTestRule.onNodeWithTag("overlay_scaffold_hub").assertExists()
  }

  // --- WidgetPicker route renders ---

  @Test
  fun `widget picker route renders overlay content`() {
    var navController: androidx.navigation.NavHostController? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        navController = rememberNavController()
        OverlayNavHost(
          navController = navController!!,
          widgetRegistry = mockWidgetRegistry,
          dataProviderRegistry = mockDataProviderRegistry,
          providerSettingsStore = mockProviderSettingsStore,
          entitlementManager = mockEntitlementManager,
          setupEvaluator = mockSetupEvaluator,
          pairedDeviceStore = mockPairedDeviceStore,
          mainSettingsViewModel = mockMainSettingsViewModel,
          onCommand = {},
        )
      }
    }

    composeTestRule.runOnUiThread {
      navController!!.navigate(WidgetPickerRoute)
    }

    composeTestRule.waitForIdle()

    // WidgetPicker uses OverlayType.Hub -- verify scaffold appears
    composeTestRule.onNodeWithTag("overlay_scaffold_hub").assertExists()
  }

  // --- Back from Settings ---

  @Test
  fun `back from settings returns to empty route`() {
    var navController: androidx.navigation.NavHostController? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        navController = rememberNavController()
        OverlayNavHost(
          navController = navController!!,
          widgetRegistry = mockWidgetRegistry,
          dataProviderRegistry = mockDataProviderRegistry,
          providerSettingsStore = mockProviderSettingsStore,
          entitlementManager = mockEntitlementManager,
          setupEvaluator = mockSetupEvaluator,
          pairedDeviceStore = mockPairedDeviceStore,
          mainSettingsViewModel = mockMainSettingsViewModel,
          onCommand = {},
        )
      }
    }

    // Navigate to Settings
    composeTestRule.runOnUiThread {
      navController!!.navigate(SettingsRoute)
    }
    composeTestRule.waitForIdle()

    // Verify settings overlay present
    composeTestRule.onNodeWithTag("overlay_scaffold_hub").assertExists()

    // Navigate back
    composeTestRule.runOnUiThread {
      navController!!.popBackStack()
    }
    composeTestRule.waitForIdle()

    // Verify back at EmptyRoute -- no overlay
    composeTestRule.onNodeWithTag("overlay_scaffold_hub").assertDoesNotExist()
    composeTestRule.onNodeWithTag("overlay_scaffold_preview").assertDoesNotExist()
  }

  // --- WidgetSettings back-stack preservation ---

  @Test
  fun `widget settings preserved in back stack when setup pushed on top`() {
    val testRenderer = mockk<WidgetRenderer>(relaxed = true).also {
      every { it.typeId } returns "essentials:clock"
      every { it.displayName } returns "Clock"
      every { it.description } returns "Digital clock"
      every { it.settingsSchema } returns emptyList()
    }
    every { mockWidgetRegistry.findByTypeId("essentials:clock") } returns testRenderer

    var navController: androidx.navigation.NavHostController? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        navController = rememberNavController()
        OverlayNavHost(
          navController = navController!!,
          widgetRegistry = mockWidgetRegistry,
          dataProviderRegistry = mockDataProviderRegistry,
          providerSettingsStore = mockProviderSettingsStore,
          entitlementManager = mockEntitlementManager,
          setupEvaluator = mockSetupEvaluator,
          pairedDeviceStore = mockPairedDeviceStore,
          mainSettingsViewModel = mockMainSettingsViewModel,
          onCommand = {},
        )
      }
    }

    // Navigate to WidgetSettings
    composeTestRule.runOnUiThread {
      navController!!.navigate(WidgetSettingsRoute(widgetId = "essentials:clock"))
    }
    composeTestRule.waitForIdle()

    // WidgetSettings renders with Preview overlay type
    composeTestRule.onNodeWithTag("overlay_scaffold_preview").assertExists()

    // Push Setup on top -- WidgetSettings should stay in back stack
    composeTestRule.runOnUiThread {
      navController!!.navigate(SetupRoute(providerId = "essentials:gps-speed"))
    }
    composeTestRule.waitForIdle()

    // Verify WidgetSettings is still in the back stack
    val backStack = navController!!.currentBackStack.value
    val hasWidgetSettings = backStack.any { entry ->
      entry.destination.route?.contains(WidgetSettingsRoute::class.qualifiedName!!) == true
    }
    assertThat(hasWidgetSettings).isTrue()

    // Pop Setup -- should return to WidgetSettings
    composeTestRule.runOnUiThread {
      navController!!.popBackStack()
    }
    composeTestRule.waitForIdle()

    // WidgetSettings still visible
    composeTestRule.onNodeWithTag("overlay_scaffold_preview").assertExists()
  }
}
