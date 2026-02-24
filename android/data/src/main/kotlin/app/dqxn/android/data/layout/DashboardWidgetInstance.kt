package app.dqxn.android.data.layout

import app.dqxn.android.data.proto.SavedWidgetProto
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

/**
 * Domain representation of a placed widget on the dashboard canvas. Maps to/from
 * [SavedWidgetProto] for DataStore persistence.
 */
public data class DashboardWidgetInstance(
  val instanceId: String,
  val typeId: String,
  val position: GridPosition,
  val size: GridSize,
  val style: WidgetStyle,
  val settings: ImmutableMap<String, String>,
  val dataSourceBindings: ImmutableMap<String, String>,
  val zIndex: Int,
) {

  /** Convert to proto representation for DataStore persistence. */
  public fun toProto(): SavedWidgetProto =
    SavedWidgetProto.newBuilder()
      .setId(instanceId)
      .setType(typeId)
      .setGridX(position.col)
      .setGridY(position.row)
      .setWidthUnits(size.widthUnits)
      .setHeightUnits(size.heightUnits)
      .setBackgroundStyle(style.backgroundStyle.name)
      .setOpacity(style.opacity)
      .setShowBorder(style.showBorder)
      .setHasGlowEffect(style.hasGlowEffect)
      .setCornerRadiusPercent(style.cornerRadiusPercent)
      .setRimSizePercent(style.rimSizePercent)
      .putAllSettings(settings)
      .setZIndex(zIndex)
      .build()

  public companion object {
    /** Reconstruct a [DashboardWidgetInstance] from its proto form. */
    public fun fromProto(proto: SavedWidgetProto): DashboardWidgetInstance =
      DashboardWidgetInstance(
        instanceId = proto.id,
        typeId = proto.type,
        position = GridPosition(col = proto.gridX, row = proto.gridY),
        size = GridSize(widthUnits = proto.widthUnits, heightUnits = proto.heightUnits),
        style =
          WidgetStyle(
            backgroundStyle =
              runCatching { BackgroundStyle.valueOf(proto.backgroundStyle) }
                .getOrDefault(BackgroundStyle.NONE),
            opacity = proto.opacity.takeIf { it > 0f } ?: 1.0f,
            showBorder = proto.showBorder,
            hasGlowEffect = proto.hasGlowEffect,
            cornerRadiusPercent = proto.cornerRadiusPercent,
            rimSizePercent = proto.rimSizePercent,
            zLayer = proto.zIndex,
          ),
        settings = proto.settingsMap.toImmutableMap(),
        dataSourceBindings =
          proto.selectedDataSourceIdsList
            .mapIndexed { index, sourceId -> index.toString() to sourceId }
            .toMap()
            .toImmutableMap(),
        zIndex = proto.zIndex,
      )
  }
}
