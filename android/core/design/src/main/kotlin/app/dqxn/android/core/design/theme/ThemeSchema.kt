package app.dqxn.android.core.design.theme

import kotlinx.serialization.Serializable

/**
 * JSON schema types for theme file parsing. These are intermediate representations
 * deserialized from theme JSON files, then converted to [DashboardThemeDefinition]
 * by [ThemeJsonParser].
 */

/** Color tokens in a theme JSON file. */
@Serializable
public data class ThemeColorSchema(
  val primary: String,
  val secondary: String,
  val accent: String,
  val background: String,
  val surface: String,
  val onSurface: String,
  val error: String? = null,
  val warning: String? = null,
  val success: String? = null,
)

/** Gradient specification in a theme JSON file. */
@Serializable
public data class ThemeGradientSchema(
  val type: String,
  val stops: List<GradientStopSchema>,
)

/** A single color stop in a gradient. */
@Serializable
public data class GradientStopSchema(
  val color: String,
  val position: Float,
)

/** Top-level theme file schema. */
@Serializable
public data class ThemeFileSchema(
  val id: String,
  val name: String,
  val isDark: Boolean,
  val colors: ThemeColorSchema,
  val backgroundGradient: ThemeGradientSchema? = null,
  val widgetBackgroundGradient: ThemeGradientSchema? = null,
)
