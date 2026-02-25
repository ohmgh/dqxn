package app.dqxn.android.pack.themes

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.GradientSpec
import app.dqxn.android.sdk.ui.theme.GradientStop
import app.dqxn.android.sdk.ui.theme.GradientType
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// -- Pack-local JSON schema types --

@Serializable
internal data class ThemesColorSchema(
  val primary: String,
  val secondary: String,
  val accent: String,
  val highlight: String? = null,
  val background: String,
  val surface: String,
  val onSurface: String,
  val error: String? = null,
  val warning: String? = null,
  val success: String? = null,
)

@Serializable
internal data class ThemesGradientStopSchema(
  val color: String,
  val position: Float,
)

@Serializable
internal data class ThemesGradientSchema(
  val type: String,
  val stops: List<ThemesGradientStopSchema>,
)

@Serializable
internal data class ThemesDefaultsSchema(
  val backgroundStyle: String? = null,
  val glowEffect: Boolean? = null,
)

@Serializable
internal data class ThemesFileSchema(
  val id: String,
  val name: String,
  val isDark: Boolean,
  val colors: ThemesColorSchema,
  val backgroundGradient: ThemesGradientSchema? = null,
  val widgetBackgroundGradient: ThemesGradientSchema? = null,
  val defaults: ThemesDefaultsSchema? = null,
)

/**
 * Parses a hex color string (#RRGGBB or #AARRGGBB) to a Compose [Color].
 *
 * Pure Kotlin implementation -- no android.graphics dependency, works in unit tests without
 * Robolectric. Duplicated from `:core:design` because packs cannot depend on `:core:*`.
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

private val themeJson = Json { ignoreUnknownKeys = true }

/**
 * Parses a theme JSON string into a [DashboardThemeDefinition].
 *
 * Returns null on parse failure. Does NOT set [DashboardThemeDefinition.requiredAnyEntitlement] --
 * the provider applies entitlement gating post-parse.
 */
internal fun parseThemeJson(json: String): DashboardThemeDefinition? =
  try {
    val schema = themeJson.decodeFromString<ThemesFileSchema>(json)
    schemaToDefinition(schema)
  } catch (_: Exception) {
    null
  }

private val DEFAULT_SIZE = androidx.compose.ui.geometry.Size(1080f, 1920f)

private fun schemaToDefinition(schema: ThemesFileSchema): DashboardThemeDefinition {
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

  val backgroundStyle =
    when (schema.defaults?.backgroundStyle?.uppercase()) {
      "NONE" -> BackgroundStyle.NONE
      "SOLID" -> BackgroundStyle.SOLID
      else -> BackgroundStyle.SOLID
    }

  return DashboardThemeDefinition(
    themeId = "themes:${schema.id}",
    displayName = schema.name,
    isDark = schema.isDark,
    packId = "themes",
    primaryTextColor = parseHexColor(colors.primary),
    secondaryTextColor = parseHexColor(colors.secondary),
    accentColor = parseHexColor(colors.accent),
    highlightColor = colors.highlight?.let { parseHexColor(it) } ?: parseHexColor(colors.accent),
    widgetBorderColor = parseHexColor(colors.onSurface),
    backgroundBrush = bgBrush,
    widgetBackgroundBrush = widgetBgBrush,
    errorColor = colors.error?.let { parseHexColor(it) } ?: Color(0xFFEF5350.toInt()),
    warningColor = colors.warning?.let { parseHexColor(it) } ?: Color(0xFFFFB74D.toInt()),
    successColor = colors.success?.let { parseHexColor(it) } ?: Color(0xFF66BB6A.toInt()),
    defaultBackgroundStyle = backgroundStyle,
    defaultHasGlowEffect = schema.defaults?.glowEffect ?: false,
    backgroundGradientSpec = bgGradientSpec,
    widgetBackgroundGradientSpec = widgetBgGradientSpec,
  )
}

private fun ThemesGradientSchema.toGradientSpec(): GradientSpec {
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
