package app.dqxn.android.data.layout

import app.dqxn.android.data.proto.DashboardStoreProto
import app.dqxn.android.data.proto.ProfileCanvasProto
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import kotlinx.collections.immutable.persistentMapOf

/**
 * Hardcoded fallback layout used when the DataStore is empty or corrupted. Does not depend on any
 * file I/O, JSON, or asset loading -- it is a code-level constant.
 */
public object FallbackLayout {

  /** Default clock widget placed at center of a typical canvas region. */
  public val FALLBACK_WIDGET: DashboardWidgetInstance =
    DashboardWidgetInstance(
      instanceId = "fallback-clock",
      typeId = "essentials:clock",
      position = GridPosition(col = 10, row = 5),
      size = GridSize(widthUnits = 10, heightUnits = 9),
      style = WidgetStyle.Default,
      settings = persistentMapOf(),
      dataSourceBindings = persistentMapOf(),
      zIndex = 0,
    )

  private const val DEFAULT_PROFILE_ID = "default"
  private const val DEFAULT_PROFILE_NAME = "Home"

  /** Create a fallback profile proto containing only [FALLBACK_WIDGET]. */
  public fun createFallbackProfile(): ProfileCanvasProto =
    ProfileCanvasProto.newBuilder()
      .setProfileId(DEFAULT_PROFILE_ID)
      .setDisplayName(DEFAULT_PROFILE_NAME)
      .setSortOrder(0)
      .addWidgets(FALLBACK_WIDGET.toProto())
      .build()

  /** Create a complete fallback store with one profile and one widget. */
  public fun createFallbackStore(): DashboardStoreProto =
    DashboardStoreProto.newBuilder()
      .setSchemaVersion(LayoutMigration.CURRENT_VERSION)
      .addProfiles(createFallbackProfile())
      .setActiveProfileId(DEFAULT_PROFILE_ID)
      .build()
}
