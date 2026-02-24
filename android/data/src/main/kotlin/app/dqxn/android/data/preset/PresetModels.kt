package app.dqxn.android.data.preset

import kotlinx.serialization.Serializable

/** Top-level structure for a preset JSON file. */
@Serializable
public data class PresetManifest(
  val name: String,
  val description: String,
  val schemaVersion: Int,
  val widgets: List<PresetWidget>,
)

/** Widget placement within a preset layout. */
@Serializable
public data class PresetWidget(
  val typeId: String,
  val gridX: Int,
  val gridY: Int,
  val widthUnits: Int,
  val heightUnits: Int,
  val style: PresetWidgetStyle = PresetWidgetStyle(),
  val settings: Map<String, String> = emptyMap(),
)

/** Visual style overrides for a preset widget. */
@Serializable
public data class PresetWidgetStyle(
  val backgroundStyle: String = "SOLID",
  val opacity: Float = 1.0f,
  val showBorder: Boolean = false,
  val hasGlowEffect: Boolean = false,
  val cornerRadiusPercent: Int = 0,
  val rimSizePercent: Int = 0,
)
