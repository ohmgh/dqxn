package app.dqxn.android.feature.settings.privacy

import app.dqxn.android.data.device.PairedDevice
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.layout.ProfileSummary
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.data.style.WidgetStyleStore
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class DataExporterTest {

  private val json = Json { ignoreUnknownKeys = true }

  private val testWidget =
    DashboardWidgetInstance(
      instanceId = "widget-1",
      typeId = "essentials:clock",
      position = GridPosition(col = 2, row = 3),
      size = GridSize(widthUnits = 4, heightUnits = 2),
      style = WidgetStyle.Default,
      settings = persistentMapOf("timezone" to "UTC"),
      dataSourceBindings = persistentMapOf("0" to "essentials:time"),
      zIndex = 0,
    )

  private fun createMocks(
    profiles: List<ProfileSummary> = listOf(
      ProfileSummary(profileId = "profile-1", displayName = "Main", sortOrder = 0),
    ),
    activeProfileId: String = "profile-1",
    widgets: List<DashboardWidgetInstance> = listOf(testWidget),
    autoSwitchMode: AutoSwitchMode = AutoSwitchMode.SYSTEM,
    lightThemeId: String = "minimalist",
    darkThemeId: String = "slate",
    illuminanceThreshold: Float = 100f,
    keepScreenOn: Boolean = true,
    orientationLock: String = "auto",
    showStatusBar: Boolean = false,
    analyticsConsent: Boolean = false,
    providerSettings: Map<String, Map<String, String>> = mapOf(
      "essentials:gps-speed" to mapOf("unit" to "kmh"),
    ),
    pairedDevices: List<PairedDevice> = listOf(
      PairedDevice(
        definitionId = "obd-adapter",
        displayName = "OBD-II Adapter",
        macAddress = "AA:BB:CC:DD:EE:FF",
        lastConnected = 1000L,
        associationId = 1,
      ),
    ),
    widgetStyles: Map<String, WidgetStyle> = mapOf(
      "widget-1" to WidgetStyle(
        backgroundStyle = BackgroundStyle.SOLID,
        opacity = 0.8f,
        showBorder = true,
        hasGlowEffect = false,
        cornerRadiusPercent = 20,
        rimSizePercent = 5,
        zLayer = 0,
      ),
    ),
  ): DataExporter {
    val layoutRepository = mockk<LayoutRepository> {
      every { this@mockk.profiles } returns flowOf(profiles.let { persistentListOf(*it.toTypedArray()) })
      every { this@mockk.activeProfileId } returns flowOf(activeProfileId)
      for (profile in profiles) {
        val profileWidgets = if (profile.profileId == activeProfileId) widgets else emptyList()
        every { getProfileWidgets(profile.profileId) } returns
          flowOf(persistentListOf(*profileWidgets.toTypedArray()))
      }
    }

    val userPreferencesRepository = mockk<UserPreferencesRepository> {
      every { this@mockk.autoSwitchMode } returns flowOf(autoSwitchMode)
      every { this@mockk.lightThemeId } returns flowOf(lightThemeId)
      every { this@mockk.darkThemeId } returns flowOf(darkThemeId)
      every { this@mockk.illuminanceThreshold } returns flowOf(illuminanceThreshold)
      every { this@mockk.keepScreenOn } returns flowOf(keepScreenOn)
      every { this@mockk.orientationLock } returns flowOf(orientationLock)
      every { this@mockk.showStatusBar } returns flowOf(showStatusBar)
      every { this@mockk.analyticsConsent } returns flowOf(analyticsConsent)
    }

    val providerSettingsStore = mockk<ProviderSettingsStore> {
      every { getAllProviderSettings() } returns flowOf(providerSettings)
    }

    val pairedDeviceStore = mockk<PairedDeviceStore> {
      every { devices } returns flowOf(persistentListOf(*pairedDevices.toTypedArray()))
    }

    val widgetStyleStore = mockk<WidgetStyleStore> {
      every { getAllStyles() } returns flowOf(widgetStyles)
    }

    return DataExporter(
      layoutRepository = layoutRepository,
      userPreferencesRepository = userPreferencesRepository,
      providerSettingsStore = providerSettingsStore,
      pairedDeviceStore = pairedDeviceStore,
      widgetStyleStore = widgetStyleStore,
    )
  }

  @Test
  fun `exportToJson produces valid JSON that round-trips`() = runTest {
    val exporter = createMocks()

    val jsonString = exporter.exportToJson()
    val parsed = json.decodeFromString(DataExport.serializer(), jsonString)

    assertThat(parsed.layout.profiles).hasSize(1)
    assertThat(parsed.layout.activeProfileId).isEqualTo("profile-1")
    assertThat(parsed.layout.profiles[0].profileId).isEqualTo("profile-1")
    assertThat(parsed.layout.profiles[0].displayName).isEqualTo("Main")
    assertThat(parsed.layout.profiles[0].widgets).hasSize(1)

    val widget = parsed.layout.profiles[0].widgets[0]
    assertThat(widget.instanceId).isEqualTo("widget-1")
    assertThat(widget.typeId).isEqualTo("essentials:clock")
    assertThat(widget.positionX).isEqualTo(2)
    assertThat(widget.positionY).isEqualTo(3)
    assertThat(widget.width).isEqualTo(4)
    assertThat(widget.height).isEqualTo(2)
  }

  @Test
  fun `exportToJson includes all preferences`() = runTest {
    val exporter =
      createMocks(
        autoSwitchMode = AutoSwitchMode.ILLUMINANCE_AUTO,
        lightThemeId = "ocean",
        darkThemeId = "midnight",
        illuminanceThreshold = 250f,
        keepScreenOn = false,
        orientationLock = "landscape",
        showStatusBar = true,
        analyticsConsent = true,
      )

    val jsonString = exporter.exportToJson()
    val parsed = json.decodeFromString(DataExport.serializer(), jsonString)

    assertThat(parsed.preferences.autoSwitchMode).isEqualTo("ILLUMINANCE_AUTO")
    assertThat(parsed.preferences.lightThemeId).isEqualTo("ocean")
    assertThat(parsed.preferences.darkThemeId).isEqualTo("midnight")
    assertThat(parsed.preferences.illuminanceThreshold).isEqualTo(250f)
    assertThat(parsed.preferences.keepScreenOn).isFalse()
    assertThat(parsed.preferences.orientationLock).isEqualTo("landscape")
    assertThat(parsed.preferences.showStatusBar).isTrue()
    assertThat(parsed.preferences.analyticsConsent).isTrue()
  }

  @Test
  fun `exportToJson includes paired devices`() = runTest {
    val exporter = createMocks()

    val jsonString = exporter.exportToJson()
    val parsed = json.decodeFromString(DataExport.serializer(), jsonString)

    assertThat(parsed.pairedDevices).hasSize(1)
    assertThat(parsed.pairedDevices[0].name).isEqualTo("OBD-II Adapter")
    assertThat(parsed.pairedDevices[0].macAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
  }

  @Test
  fun `exportToJson includes provider settings`() = runTest {
    val exporter = createMocks()

    val jsonString = exporter.exportToJson()
    val parsed = json.decodeFromString(DataExport.serializer(), jsonString)

    assertThat(parsed.providerSettings).containsKey("essentials:gps-speed")
    assertThat(parsed.providerSettings["essentials:gps-speed"]).containsEntry("unit", "kmh")
  }

  @Test
  fun `exportToJson includes widget styles`() = runTest {
    val exporter = createMocks()

    val jsonString = exporter.exportToJson()
    val parsed = json.decodeFromString(DataExport.serializer(), jsonString)

    assertThat(parsed.widgetStyles).containsKey("widget-1")
    assertThat(parsed.widgetStyles["widget-1"]).containsEntry("backgroundStyle", "SOLID")
    assertThat(parsed.widgetStyles["widget-1"]).containsEntry("opacity", "0.8")
    assertThat(parsed.widgetStyles["widget-1"]).containsEntry("showBorder", "true")
  }

  @Test
  fun `exportToJson with empty state produces valid JSON`() = runTest {
    val exporter =
      createMocks(
        widgets = emptyList(),
        pairedDevices = emptyList(),
        providerSettings = emptyMap(),
        widgetStyles = emptyMap(),
      )

    val jsonString = exporter.exportToJson()
    val parsed = json.decodeFromString(DataExport.serializer(), jsonString)

    assertThat(parsed.layout.profiles[0].widgets).isEmpty()
    assertThat(parsed.pairedDevices).isEmpty()
    assertThat(parsed.providerSettings).isEmpty()
    assertThat(parsed.widgetStyles).isEmpty()
  }

  @Test
  fun `exportToJson round-trip preserves all values`() = runTest {
    val exporter = createMocks()

    val jsonString = exporter.exportToJson()
    val parsed = json.decodeFromString(DataExport.serializer(), jsonString)

    // Re-serialize and re-parse to verify full round-trip stability
    val reEncoded = json.encodeToString(DataExport.serializer(), parsed)
    val reParsed = json.decodeFromString(DataExport.serializer(), reEncoded)

    assertThat(reParsed.layout).isEqualTo(parsed.layout)
    assertThat(reParsed.preferences).isEqualTo(parsed.preferences)
    assertThat(reParsed.providerSettings).isEqualTo(parsed.providerSettings)
    assertThat(reParsed.pairedDevices).isEqualTo(parsed.pairedDevices)
    assertThat(reParsed.widgetStyles).isEqualTo(parsed.widgetStyles)
  }

  @Test
  fun `exportToJson includes export timestamp and app version`() = runTest {
    val exporter = createMocks()

    val jsonString = exporter.exportToJson()
    val parsed = json.decodeFromString(DataExport.serializer(), jsonString)

    assertThat(parsed.exportTimestamp).isGreaterThan(0L)
    assertThat(parsed.appVersion).isEqualTo("1.0.0")
  }
}
