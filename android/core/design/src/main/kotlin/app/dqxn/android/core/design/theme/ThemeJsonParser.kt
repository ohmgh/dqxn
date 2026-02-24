package app.dqxn.android.core.design.theme

import android.graphics.Color as AndroidColor
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
 * Color strings are parsed via [android.graphics.Color.parseColor] which supports
 * `#RRGGBB` and `#AARRGGBB` hex formats. Malformed JSON or unparseable colors
 * return null (no crash) with a warning logged.
 */
@Singleton
public class ThemeJsonParser @Inject constructor(private val json: Json, private val logger: DqxnLogger) {

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

    val primaryColor = parseColor(colors.primary)
    val bgBrush = bgGradientSpec?.toBrush(DEFAULT_SIZE) ?: Brush.verticalGradient(
      listOf(parseColor(colors.background), parseColor(colors.surface))
    )
    val widgetBgBrush = widgetBgGradientSpec?.toBrush(DEFAULT_SIZE) ?: Brush.verticalGradient(
      listOf(parseColor(colors.surface), parseColor(colors.background))
    )

    return DashboardThemeDefinition(
      themeId = schema.id,
      displayName = schema.name,
      isDark = schema.isDark,
      primaryTextColor = primaryColor,
      secondaryTextColor = parseColor(colors.secondary),
      accentColor = parseColor(colors.accent),
      widgetBorderColor = parseColor(colors.onSurface),
      backgroundBrush = bgBrush,
      widgetBackgroundBrush = widgetBgBrush,
      errorColor = colors.error?.let { parseColor(it) } ?: Color(0xFFEF5350),
      warningColor = colors.warning?.let { parseColor(it) } ?: Color(0xFFFFB74D),
      successColor = colors.success?.let { parseColor(it) } ?: Color(0xFF66BB6A),
      backgroundGradientSpec = bgGradientSpec,
      widgetBackgroundGradientSpec = widgetBgGradientSpec,
    )
  }

  private companion object {
    /** Default size for initial brush creation. Actual size resolved at draw time. */
    val DEFAULT_SIZE = androidx.compose.ui.geometry.Size(1080f, 1920f)
  }
}

private fun parseColor(hex: String): Color = Color(AndroidColor.parseColor(hex))

private fun ThemeGradientSchema.toGradientSpec(): GradientSpec {
  val gradientType = when (type.uppercase()) {
    "VERTICAL" -> GradientType.VERTICAL
    "HORIZONTAL" -> GradientType.HORIZONTAL
    "LINEAR" -> GradientType.LINEAR
    "RADIAL" -> GradientType.RADIAL
    "SWEEP" -> GradientType.SWEEP
    else -> GradientType.VERTICAL
  }
  val gradientStops = stops.map { stop ->
    GradientStop(
      color = parseColor(stop.color).value.toLong(),
      position = stop.position,
    )
  }.toImmutableList()
  return GradientSpec(type = gradientType, stops = gradientStops)
}
