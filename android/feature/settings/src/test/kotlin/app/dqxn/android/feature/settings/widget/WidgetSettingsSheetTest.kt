package app.dqxn.android.feature.settings.widget

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.setup.InfoStyle
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetSettingsSheetTest {

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

  // --- Tab navigation tests ---

  @Test
  fun `renders 3 tabs with correct titles`() {
    val widgetSpec = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    composeTestRule.onNodeWithText("Feature").assertIsDisplayed()
    composeTestRule.onNodeWithText("Data Source").assertIsDisplayed()
    composeTestRule.onNodeWithText("Info").assertIsDisplayed()
  }

  @Test
  fun `tab row renders with correct test tag`() {
    val widgetSpec = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    composeTestRule.onNodeWithTag("widget_settings_tab_row").assertIsDisplayed()
  }

  @Test
  fun `clicking Data Source tab switches to tab 1`() {
    val widgetSpec = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Click Data Source tab
    composeTestRule.onNodeWithText("Data Source").performClick()
    composeTestRule.waitForIdle()

    // Tab 1 should be selected -- verify via the tab_1 test tag
    composeTestRule.onNodeWithTag("widget_settings_tab_1").assertIsDisplayed()
  }

  @Test
  fun `clicking Info tab switches to tab 2`() {
    val widgetSpec = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Click Info tab
    composeTestRule.onNodeWithText("Info").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("widget_settings_tab_2").assertIsDisplayed()
  }

  // --- Schema rendering test ---

  @Test
  fun `Feature tab renders BooleanSetting from widget schema`() {
    val widgetSpec =
      createTestWidget(
        typeId = "essentials:compass",
        displayName = "Compass",
        settingsSchema =
          listOf(
            SettingDefinition.BooleanSetting(
              key = "show_degrees",
              label = "Show Degrees",
              default = true,
            ),
          ),
      )
    val widgetRegistry = createWidgetRegistry(widgetSpec)
    val providerSettingsStore = createProviderSettingsStore()

    setContent(widgetRegistry, providerSettingsStore = providerSettingsStore)

    // Feature tab is tab 0 (default), so BooleanSetting should render
    composeTestRule.onNodeWithText("Show Degrees").assertIsDisplayed()
  }

  @Test
  fun `Feature tab renders EnumSetting from widget schema`() {
    val widgetSpec =
      createTestWidget(
        typeId = "essentials:compass",
        displayName = "Compass",
        settingsSchema =
          listOf(
            SettingDefinition.EnumSetting(
              key = "style",
              label = "Style",
              default = TestDisplayStyle.MODERN,
              options = TestDisplayStyle.entries.toList(),
            ),
          ),
      )
    val widgetRegistry = createWidgetRegistry(widgetSpec)
    val providerSettingsStore = createProviderSettingsStore()

    setContent(widgetRegistry, providerSettingsStore = providerSettingsStore)

    composeTestRule.onNodeWithText("Style").assertIsDisplayed()
  }

  // --- Provider listing test ---

  @Test
  fun `Data Source tab shows providers when navigated`() {
    val widgetSpec =
      createTestWidget(
        typeId = "essentials:speedometer",
        displayName = "Speedometer",
        compatibleSnapshots = setOf(SpeedSnapshot::class),
      )
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    val provider1 = createTestProvider("essentials:gps-speed", "GPS Speed", "Speed")
    val provider2 = createTestProvider("essentials:obd-speed", "OBD Speed", "Speed")
    val dataProviderRegistry =
      object : DataProviderRegistry {
        override fun getAll(): Set<DataProvider<*>> = setOf(provider1, provider2)
        override fun findByDataType(dataType: String): List<DataProvider<*>> =
          if (dataType == "Speed") listOf(provider1, provider2)
          else emptyList()
        override fun getFiltered(entitlementCheck: (String) -> Boolean): Set<DataProvider<*>> =
          setOf(provider1, provider2)
      }

    setContent(widgetRegistry, dataProviderRegistry = dataProviderRegistry)

    // Navigate to Data Source tab
    composeTestRule.onNodeWithText("Data Source").performClick()
    composeTestRule.waitForIdle()

    // Both providers should render
    composeTestRule.onNodeWithText("GPS Speed").assertIsDisplayed()
    composeTestRule.onNodeWithText("OBD Speed").assertIsDisplayed()
  }

  // --- Speed disclaimer test ---

  @Test
  fun `Info tab shows speed disclaimer for speedometer widget`() {
    val widgetSpec = createTestWidget("essentials:speedometer", "Speedometer")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Navigate to Info tab
    composeTestRule.onNodeWithText("Info").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("widget_info_speed_disclaimer").assertIsDisplayed()
    composeTestRule.onNodeWithText("Speed readings are estimates", substring = true).assertIsDisplayed()
  }

  @Test
  fun `Info tab does not show speed disclaimer for non-speed widget`() {
    val widgetSpec = createTestWidget("essentials:compass", "Compass")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Navigate to Info tab
    composeTestRule.onNodeWithText("Info").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("widget_info_speed_disclaimer").assertDoesNotExist()
  }

  // --- Helpers ---

  /** Test snapshot for provider matching in DataProviderSettingsContent. */
  private data class SpeedSnapshot(override val timestamp: Long = 0L) : DataSnapshot

  private enum class TestDisplayStyle { MODERN, CLASSIC }

  private fun setContent(
    widgetRegistry: WidgetRegistry,
    dataProviderRegistry: DataProviderRegistry = emptyDataProviderRegistry(),
    providerSettingsStore: ProviderSettingsStore = createProviderSettingsStore(),
    widgetTypeId: String = widgetRegistry.getAll().first().typeId,
  ) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetSettingsSheet(
          widgetTypeId = widgetTypeId,
          widgetRegistry = widgetRegistry,
          dataProviderRegistry = dataProviderRegistry,
          providerSettingsStore = providerSettingsStore,
          entitlementManager = freeEntitlementManager,
          onDismiss = {},
          onNavigate = {},
          onNavigateToSetup = {},
          onNavigateToPackBrowser = {},
        )
      }
    }
  }

  private fun createTestWidget(
    typeId: String,
    displayName: String,
    settingsSchema: List<SettingDefinition<*>> = emptyList(),
    description: String = "A test widget",
    compatibleSnapshots: Set<KClass<out DataSnapshot>> = emptySet(),
  ): WidgetRenderer {
    val renderer = mockk<WidgetRenderer>(relaxed = true)
    every { renderer.typeId } returns typeId
    every { renderer.displayName } returns displayName
    every { renderer.description } returns description
    every { renderer.settingsSchema } returns settingsSchema
    every { renderer.compatibleSnapshots } returns compatibleSnapshots
    every { renderer.requiredAnyEntitlement } returns null
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

  private fun emptyDataProviderRegistry(): DataProviderRegistry =
    object : DataProviderRegistry {
      override fun getAll(): Set<DataProvider<*>> = emptySet()
      override fun findByDataType(dataType: String): List<DataProvider<*>> = emptyList()
      override fun getFiltered(entitlementCheck: (String) -> Boolean): Set<DataProvider<*>> =
        emptySet()
    }

  private fun createProviderSettingsStore(): ProviderSettingsStore =
    object : ProviderSettingsStore {
      override fun getSetting(
        packId: String,
        providerId: String,
        key: String,
      ): Flow<Any?> = flowOf(null)

      override suspend fun setSetting(
        packId: String,
        providerId: String,
        key: String,
        value: Any?,
      ) {}

      override fun getAllSettings(
        packId: String,
        providerId: String,
      ): Flow<ImmutableMap<String, Any?>> = flowOf(persistentMapOf())

      override suspend fun clearSettings(packId: String, providerId: String) {}

      override fun getAllProviderSettings(): Flow<Map<String, Map<String, String>>> =
        flowOf(emptyMap())

      override suspend fun clearAll() {}
    }

  private fun createTestProvider(
    sourceId: String,
    displayName: String,
    dataType: String,
    snapshotType: KClass<out DataSnapshot> = SpeedSnapshot::class,
  ): DataProvider<*> {
    val provider = mockk<DataProvider<DataSnapshot>>(relaxed = true)
    every { provider.sourceId } returns sourceId
    every { provider.displayName } returns displayName
    every { provider.dataType } returns dataType
    @Suppress("UNCHECKED_CAST")
    every { provider.snapshotType } returns (snapshotType as KClass<DataSnapshot>)
    every { provider.priority } returns ProviderPriority.DEVICE_SENSOR
    every { provider.isAvailable } returns true
    every { provider.setupSchema } returns emptyList()
    every { provider.requiredAnyEntitlement } returns null
    return provider
  }
}
