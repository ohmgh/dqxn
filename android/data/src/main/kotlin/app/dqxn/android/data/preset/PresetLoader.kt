package app.dqxn.android.data.preset

import android.content.Context
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.FallbackLayout
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.error
import app.dqxn.android.sdk.observability.log.info
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.json.Json

/**
 * Loads region-aware preset layouts from bundled JSON assets. Falls back to `default.json` when no
 * region-specific preset exists, and to [FallbackLayout.FALLBACK_WIDGET] when all JSON loading
 * fails (APK corruption).
 */
@Singleton
public class PresetLoader
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
  private val logger: DqxnLogger,
) {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  /**
   * Load a preset for the given region. Returns a list of [DashboardWidgetInstance] with generated
   * UUIDs. Filters out GPS-dependent widgets per F11.5.
   */
  public fun loadPreset(
    region: String = FallbackRegionDetector.detectRegion(),
  ): List<DashboardWidgetInstance> {
    // Try region-specific preset first, then default
    val manifest =
      loadManifestFromAssets("presets/$region.json")
        ?: loadManifestFromAssets("presets/default.json")

    if (manifest == null) {
      logger.error(TAG) { "Failed to load any preset JSON, returning fallback widget" }
      return listOf(FallbackLayout.FALLBACK_WIDGET)
    }

    logger.info(TAG) { "Loaded preset '${manifest.name}' with ${manifest.widgets.size} widgets" }

    return manifest.widgets
      .map { widget -> remapTypeId(widget) }
      .filter { widget -> !isGpsDependentWidget(widget.typeId) }
      .map { widget -> widget.toDashboardWidgetInstance() }
  }

  /**
   * Parse a [PresetManifest] from the given JSON string. Exposed for testing without requiring
   * Android context.
   */
  internal fun parsePreset(jsonString: String): PresetManifest? =
    try {
      json.decodeFromString(PresetManifest.serializer(), jsonString)
    } catch (e: Exception) {
      logger.error(TAG) { "Failed to parse preset JSON: ${e.message}" }
      null
    }

  private fun loadManifestFromAssets(path: String): PresetManifest? =
    try {
      val jsonString = context.assets.open(path).bufferedReader().use { it.readText() }
      parsePreset(jsonString)
    } catch (_: Exception) {
      null
    }

  private companion object {
    val TAG = LogTag("PresetLoader")

    /** GPS-dependent widget type suffixes per F11.5 -- excluded from first-launch presets. */
    val GPS_WIDGET_PATTERNS = setOf("speedometer", "compass", "gforce", "altimeter", "solar")

    fun isGpsDependentWidget(typeId: String): Boolean {
      val widgetName = typeId.substringAfter(":")
      return GPS_WIDGET_PATTERNS.any { pattern -> widgetName.contains(pattern, ignoreCase = true) }
    }

    /** Remap legacy `free:*` typeIds to `essentials:*` (research pitfall #6). */
    fun remapTypeId(widget: PresetWidget): PresetWidget {
      val typeId = widget.typeId
      return if (typeId.startsWith("free:")) {
        widget.copy(typeId = "essentials:" + typeId.removePrefix("free:"))
      } else {
        widget
      }
    }

    fun PresetWidget.toDashboardWidgetInstance(): DashboardWidgetInstance {
      val bgStyle =
        runCatching { BackgroundStyle.valueOf(style.backgroundStyle) }
          .getOrDefault(BackgroundStyle.SOLID)
      return DashboardWidgetInstance(
        instanceId = UUID.randomUUID().toString(),
        typeId = typeId,
        position = GridPosition(col = gridX, row = gridY),
        size = GridSize(widthUnits = widthUnits, heightUnits = heightUnits),
        style =
          WidgetStyle(
            backgroundStyle = bgStyle,
            opacity = style.opacity,
            showBorder = style.showBorder,
            hasGlowEffect = style.hasGlowEffect,
            cornerRadiusPercent = style.cornerRadiusPercent,
            rimSizePercent = style.rimSizePercent,
            zLayer = 0,
          ),
        settings = persistentMapOf(),
        dataSourceBindings = persistentMapOf(),
        zIndex = 0,
      )
    }
  }
}
