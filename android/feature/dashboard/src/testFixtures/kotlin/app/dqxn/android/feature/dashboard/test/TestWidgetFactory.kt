package app.dqxn.android.feature.dashboard.test

import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import java.util.UUID
import kotlinx.collections.immutable.persistentMapOf

/** Factory for creating [DashboardWidgetInstance] instances in tests. */
public object TestWidgetFactory {

  /** Create a test widget with sensible defaults. Position and size are customizable. */
  public fun testWidget(
    typeId: String = "essentials:clock",
    instanceId: String = UUID.randomUUID().toString(),
    col: Int = 0,
    row: Int = 0,
    widthUnits: Int = 4,
    heightUnits: Int = 4,
    zIndex: Int = 0,
  ): DashboardWidgetInstance =
    DashboardWidgetInstance(
      instanceId = instanceId,
      typeId = typeId,
      position = GridPosition(col = col, row = row),
      size = GridSize(widthUnits = widthUnits, heightUnits = heightUnits),
      style =
        WidgetStyle(
          backgroundStyle = BackgroundStyle.SOLID,
          opacity = 1.0f,
          showBorder = false,
          hasGlowEffect = false,
          cornerRadiusPercent = 10,
          rimSizePercent = 0,
          zLayer = zIndex,
        ),
      settings = persistentMapOf(),
      dataSourceBindings = persistentMapOf(),
      zIndex = zIndex,
    )
}
