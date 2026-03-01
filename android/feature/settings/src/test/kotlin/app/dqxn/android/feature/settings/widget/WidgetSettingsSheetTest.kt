package app.dqxn.android.feature.settings.widget

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dqxn.android.data.style.WidgetStyleStore
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KClass
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

  // --- Pager icon navigation tests ---

  @Test
  fun `renders pager icons in title bar`() {
    val widgetSpec = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    composeTestRule.onNodeWithTag("widget_settings_icon_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("widget_settings_icon_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("widget_settings_icon_2").assertIsDisplayed()
  }

  @Test
  fun `pager renders with correct test tag`() {
    val widgetSpec = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    composeTestRule.onNodeWithTag("widget_settings_pager").assertIsDisplayed()
  }

  @Test
  fun `clicking Data Source icon switches to page 1`() {
    val widgetSpec =
      createTestWidget(
        typeId = "essentials:speedometer",
        displayName = "Speedometer",
        compatibleSnapshots = setOf(SpeedSnapshot::class),
      )
    val widgetRegistry = createWidgetRegistry(widgetSpec)
    val provider = createTestProvider("essentials:gps-speed", "GPS Speed", "Speed")
    val dataProviderRegistry =
      object : DataProviderRegistry {
        override fun getAll(): Set<DataProvider<*>> = setOf(provider)
        override fun findByDataType(dataType: String): List<DataProvider<*>> =
          if (dataType == "Speed") listOf(provider) else emptyList()
        override fun getFiltered(entitlementCheck: (String) -> Boolean): Set<DataProvider<*>> =
          setOf(provider)
      }

    setContent(widgetRegistry, dataProviderRegistry = dataProviderRegistry)

    // Click Data Source icon (index 1)
    composeTestRule.onNodeWithTag("widget_settings_icon_1").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("data_provider_settings_content").assertIsDisplayed()
  }

  @Test
  fun `clicking Info icon switches to page 2`() {
    val widgetSpec = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Click Info icon (index 2)
    composeTestRule.onNodeWithTag("widget_settings_icon_2").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("widget_info_content").assertIsDisplayed()
  }

  // --- Schema rendering test ---

  @Test
  fun `Settings page renders BooleanSetting from widget schema`() {
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

    // Settings page is page 0 (default), so BooleanSetting should render
    composeTestRule.onNodeWithText("Show Degrees").assertIsDisplayed()
  }

  @Test
  fun `Settings page renders style settings`() {
    val widgetSpec = createTestWidget("essentials:compass", "Compass")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Style settings should appear on the same page
    composeTestRule.onNodeWithText("Surface").assertIsDisplayed()
    composeTestRule.onNodeWithText("Glow Effect").assertIsDisplayed()
    composeTestRule.onNodeWithText("Outline").assertIsDisplayed()
  }

  @Test
  fun `Settings page renders EnumSetting from widget schema`() {
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
  fun `Data Source page shows providers when navigated`() {
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

    // Navigate to Data Source page via icon
    composeTestRule.onNodeWithTag("widget_settings_icon_1").performClick()
    composeTestRule.waitForIdle()

    // Both providers should render
    composeTestRule.onNodeWithText("GPS Speed").assertIsDisplayed()
    composeTestRule.onNodeWithText("OBD Speed").assertIsDisplayed()
  }

  // --- Info page tests ---

  @Test
  fun `Info page shows speed disclaimer for speedometer widget`() {
    val widgetSpec = createTestWidget("essentials:speedometer", "Speedometer")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Navigate to Info page via icon
    composeTestRule.onNodeWithTag("widget_settings_icon_2").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("widget_info_speed_disclaimer").assertIsDisplayed()
    composeTestRule.onNodeWithText("Speed readings are estimates", substring = true).assertIsDisplayed()
  }

  @Test
  fun `Info page does not show speed disclaimer for non-speed widget`() {
    val widgetSpec = createTestWidget("essentials:compass", "Compass")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Navigate to Info page via icon
    composeTestRule.onNodeWithTag("widget_settings_icon_2").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("widget_info_speed_disclaimer").assertDoesNotExist()
  }

  @Test
  fun `Info page shows pack card`() {
    val widgetSpec = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Navigate to Info page
    composeTestRule.onNodeWithTag("widget_settings_icon_2").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("widget_info_pack_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Essentials").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tap for pack info").assertIsDisplayed()
  }

  @Test
  fun `Info page shows All Systems Go card`() {
    val widgetSpec = createTestWidget("essentials:clock-digital", "Digital Clock")
    val widgetRegistry = createWidgetRegistry(widgetSpec)

    setContent(widgetRegistry)

    // Navigate to Info page
    composeTestRule.onNodeWithTag("widget_settings_icon_2").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("widget_info_all_systems_go").assertIsDisplayed()
    composeTestRule.onNodeWithText("All Systems Go").assertIsDisplayed()
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
          widgetInstanceId = "test-instance-1",
          widgetRegistry = widgetRegistry,
          dataProviderRegistry = dataProviderRegistry,
          providerSettingsStore = providerSettingsStore,
          widgetStyleStore = createWidgetStyleStore(),
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

  private fun createWidgetStyleStore(): WidgetStyleStore =
    object : WidgetStyleStore {
      override fun getStyle(instanceId: String): Flow<WidgetStyle> =
        flowOf(WidgetStyle.Default)
      override suspend fun setStyle(instanceId: String, style: WidgetStyle) {}
      override suspend fun removeStyle(instanceId: String) {}
      override fun getAllStyles(): Flow<Map<String, WidgetStyle>> = flowOf(emptyMap())
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
