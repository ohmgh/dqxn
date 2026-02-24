package app.dqxn.android.core.design.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTags
import app.dqxn.android.sdk.observability.log.warn
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.GradientSpec
import app.dqxn.android.sdk.ui.theme.GradientStop
import app.dqxn.android.sdk.ui.theme.GradientType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json

/**
 * Parses theme JSON files into [DashboardThemeDefinition] instances.
 *
 * Color strings are parsed from `#RRGGBB` and `#AARRGGBB` hex formats using a pure-Kotlin
 * implementation (no `android.graphics.Color` dependency, enabling unit testing without
 * Robolectric). Malformed JSON or unparseable colors return null (no crash) with a warning logged.
 */
@Singleton
public class ThemeJsonParser
@Inject
constructor(private val json: Json, private val logger: DqxnLogger) {

  /** Parses a single theme JSON string. Returns null on parse failure. */
  public fun parse(jsonString: String): DashboardThemeDefinition? =
    try {
      val schema = json.decodeFromString<ThemeFileSchema>(jsonString)
      schemaToDefinition(schema)
    } catch (e: Exception) {
      logger.warn(LogTags.THEME) { "Failed to parse theme JSON: ${e.message}" }
      null
    }

  /** Parses a JSON array of themes. Malformed entries are skipped. */
  public fun parseAll(jsonArrayString: String): List<DashboardThemeDefinition> =
    try {
      val schemas = json.decodeFromString<List<ThemeFileSchema>>(jsonArrayString)
      schemas.mapNotNull { schema ->
        try {
          schemaToDefinition(schema)
        } catch (e: Exception) {
          logger.warn(LogTags.THEME) { "Skipping malformed theme '${schema.id}': ${e.message}" }
          null
        }
      }
    } catch (e: Exception) {
      logger.warn(LogTags.THEME) { "Failed to parse theme JSON array: ${e.message}" }
      emptyList()
    }

  private fun schemaToDefinition(schema: ThemeFileSchema): DashboardThemeDefinition {
    val colors = schema.colors
    val bgGradientSpec = schema.backgroundGradient?.toGradientSpec()
    val widgetBgGradientSpec = schema.widgetBackgroundGradient?.toGradientSpec()

    val bgBrush =
      bgGradientSpec?.toBrush(DEFAULT_SIZE)
        ?: Brush.verticalGradient(
          listOf(parseHexColor(colors.background), parseHexColor(colors.surface))
        )
    val widgetBgBrush =
      widgetBgGradientSpec?.toBrush(DEFAULT_SIZE)
        ?: Brush.verticalGradient(
          listOf(parseHexColor(colors.surface), parseHexColor(colors.background))
        )

    return DashboardThemeDefinition(
      themeId = schema.id,
      displayName = schema.name,
      isDark = schema.isDark,
      primaryTextColor = parseHexColor(colors.primary),
      secondaryTextColor = parseHexColor(colors.secondary),
      accentColor = parseHexColor(colors.accent),
      widgetBorderColor = parseHexColor(colors.onSurface),
      backgroundBrush = bgBrush,
      widgetBackgroundBrush = widgetBgBrush,
      errorColor = colors.error?.let { parseHexColor(it) } ?: Color(0xFFEF5350),
      warningColor = colors.warning?.let { parseHexColor(it) } ?: Color(0xFFFFB74D),
      successColor = colors.success?.let { parseHexColor(it) } ?: Color(0xFF66BB6A),
      backgroundGradientSpec = bgGradientSpec,
      widgetBackgroundGradientSpec = widgetBgGradientSpec,
    )
  }

  private companion object {
    /** Default size for initial brush creation. Actual size resolved at draw time. */
    val DEFAULT_SIZE = androidx.compose.ui.geometry.Size(1080f, 1920f)
  }
}

/**
 * Parses a hex color string (#RRGGBB or #AARRGGBB) to a Compose [Color]. Pure Kotlin
 * implementation -- no android.graphics dependency, so it works in unit tests.
 */
internal fun parseHexColor(hex: String): Color {
  val stripped = hex.removePrefix("#")
  val colorLong =
    when (stripped.length) {
      6 -> {
        // #RRGGBB -> add full alpha
        val rgb = stripped.toLong(16)
        0xFF000000L or rgb
      }
      8 -> {
        // #AARRGGBB
        stripped.toLong(16)
      }
      else -> throw IllegalArgumentException("Invalid color format: $hex")
    }
  return Color(colorLong.toInt())
}

private fun ThemeGradientSchema.toGradientSpec(): GradientSpec {
  val gradientType =
    when (type.uppercase()) {
      "VERTICAL" -> GradientType.VERTICAL
      "HORIZONTAL" -> GradientType.HORIZONTAL
      "LINEAR" -> GradientType.LINEAR
      "RADIAL" -> GradientType.RADIAL
      "SWEEP" -> GradientType.SWEEP
      else -> GradientType.VERTICAL
    }
  val gradientStops =
    stops
      .map { stop ->
        GradientStop(
          color = parseHexColor(stop.color).value.toLong(),
          position = stop.position,
        )
      }
      .toImmutableList()
  return GradientSpec(type = gradientType, stops = gradientStops)
}
