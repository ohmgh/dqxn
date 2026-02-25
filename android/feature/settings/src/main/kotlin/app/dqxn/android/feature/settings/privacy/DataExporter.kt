package app.dqxn.android.feature.settings.privacy

import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.data.style.WidgetStyleStore
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Exports all user data as human-readable JSON for GDPR Article 15 / PDPA compliance (NF-P5).
 * Collects from all 5 data repositories and maps to serializable data classes.
 */
public class DataExporter
@Inject
constructor(
  private val layoutRepository: LayoutRepository,
  private val userPreferencesRepository: UserPreferencesRepository,
  private val providerSettingsStore: ProviderSettingsStore,
  private val pairedDeviceStore: PairedDeviceStore,
  private val widgetStyleStore: WidgetStyleStore,
) {

  private val json = Json { prettyPrint = true }

  /**
   * Collects all user data from all repositories and returns a JSON string. Each repository is
   * read once via `.first()` to capture a consistent snapshot.
   */
  public suspend fun exportToJson(): String {
    val profileSummaries = layoutRepository.profiles.first()
    val activeProfileId = layoutRepository.activeProfileId.first()

    val profiles =
      profileSummaries.map { summary ->
        val widgets = layoutRepository.getProfileWidgets(summary.profileId).first()
        ProfileExport(
          profileId = summary.profileId,
          displayName = summary.displayName,
          widgets =
            widgets.map { w ->
              WidgetExport(
                instanceId = w.instanceId,
                typeId = w.typeId,
                positionX = w.position.col,
                positionY = w.position.row,
                width = w.size.widthUnits,
                height = w.size.heightUnits,
              )
            },
        )
      }

    val autoSwitchMode = userPreferencesRepository.autoSwitchMode.first()
    val lightThemeId = userPreferencesRepository.lightThemeId.first()
    val darkThemeId = userPreferencesRepository.darkThemeId.first()
    val illuminanceThreshold = userPreferencesRepository.illuminanceThreshold.first()
    val keepScreenOn = userPreferencesRepository.keepScreenOn.first()
    val orientationLock = userPreferencesRepository.orientationLock.first()
    val showStatusBar = userPreferencesRepository.showStatusBar.first()
    val analyticsConsent = userPreferencesRepository.analyticsConsent.first()

    val providerSettings = providerSettingsStore.getAllProviderSettings().first()

    val pairedDevices =
      pairedDeviceStore.devices.first().map { device ->
        PairedDeviceExport(name = device.displayName, macAddress = device.macAddress)
      }

    val allStyles = widgetStyleStore.getAllStyles().first()
    val widgetStyles =
      allStyles.mapValues { (_, style) ->
        mapOf(
          "backgroundStyle" to style.backgroundStyle.name,
          "opacity" to style.opacity.toString(),
          "showBorder" to style.showBorder.toString(),
          "hasGlowEffect" to style.hasGlowEffect.toString(),
          "cornerRadiusPercent" to style.cornerRadiusPercent.toString(),
          "rimSizePercent" to style.rimSizePercent.toString(),
        )
      }

    val export =
      DataExport(
        exportTimestamp = System.currentTimeMillis(),
        appVersion = "1.0.0",
        layout = LayoutExport(profiles = profiles, activeProfileId = activeProfileId),
        preferences =
          PreferencesExport(
            autoSwitchMode = autoSwitchMode.name,
            lightThemeId = lightThemeId,
            darkThemeId = darkThemeId,
            illuminanceThreshold = illuminanceThreshold,
            keepScreenOn = keepScreenOn,
            orientationLock = orientationLock,
            showStatusBar = showStatusBar,
            analyticsConsent = analyticsConsent,
          ),
        providerSettings = providerSettings,
        pairedDevices = pairedDevices,
        widgetStyles = widgetStyles,
      )

    return json.encodeToString(DataExport.serializer(), export)
  }
}

@Serializable
public data class DataExport(
  val exportTimestamp: Long,
  val appVersion: String,
  val layout: LayoutExport,
  val preferences: PreferencesExport,
  val providerSettings: Map<String, Map<String, String>>,
  val pairedDevices: List<PairedDeviceExport>,
  val widgetStyles: Map<String, Map<String, String>>,
)

@Serializable
public data class LayoutExport(
  val profiles: List<ProfileExport>,
  val activeProfileId: String,
)

@Serializable
public data class ProfileExport(
  val profileId: String,
  val displayName: String,
  val widgets: List<WidgetExport>,
)

@Serializable
public data class WidgetExport(
  val instanceId: String,
  val typeId: String,
  val positionX: Int,
  val positionY: Int,
  val width: Int,
  val height: Int,
)

@Serializable
public data class PreferencesExport(
  val autoSwitchMode: String,
  val lightThemeId: String,
  val darkThemeId: String,
  val illuminanceThreshold: Float,
  val keepScreenOn: Boolean,
  val orientationLock: String,
  val showStatusBar: Boolean,
  val analyticsConsent: Boolean,
)

@Serializable
public data class PairedDeviceExport(
  val name: String,
  val macAddress: String,
)
