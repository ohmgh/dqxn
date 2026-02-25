package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.core.agentic.getString
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.command.DashboardCommandBus
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import java.util.UUID
import javax.inject.Inject
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Agentic command handler for adding a widget to the dashboard. Validates the requested typeId
 * against the registered widget set and constructs a [DashboardWidgetInstance] with default
 * positioning.
 *
 * The handler returns the generated widget instance ID on success, enabling downstream agentic
 * commands (move, resize, remove) to reference the new widget. The actual dashboard placement
 * is performed by the command channel consumer in [DashboardViewModel].
 */
@AgenticCommand(
  name = "add-widget",
  description = "Adds a widget to the dashboard by typeId, returns generated widgetId",
  category = "layout",
)
internal class AddWidgetHandler
@Inject
constructor(
  private val widgets: Set<@JvmSuppressWildcards WidgetRenderer>,
  private val commandBus: DashboardCommandBus,
) : CommandHandler {

  override val name: String = "add-widget"
  override val description: String =
    "Adds a widget to the dashboard by typeId, returns generated widgetId"
  override val category: String = "layout"
  override val aliases: List<String> = listOf("add")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val typeId = params.getString("typeId")
      ?: return CommandResult.Error(
        message = "Missing required param: typeId",
        code = "MISSING_PARAM",
      )

    val renderer = widgets.find { it.typeId == typeId }
      ?: return CommandResult.Error(
        message = "Unknown typeId: $typeId",
        code = "UNKNOWN_TYPE",
      )

    val widgetId = "widget-${UUID.randomUUID()}"

    // Construct widget instance with renderer defaults
    val defaults = renderer.getDefaults(WidgetContext.DEFAULT)
    val instance = DashboardWidgetInstance(
      instanceId = widgetId,
      typeId = renderer.typeId,
      position = GridPosition(col = 0, row = 0),
      size = GridSize(widthUnits = defaults.widthUnits, heightUnits = defaults.heightUnits),
      style = WidgetStyle.Default,
      settings = persistentMapOf(),
      dataSourceBindings = persistentMapOf(),
      zIndex = 0,
    )

    // Dispatch to dashboard via singleton command bus
    commandBus.dispatch(
      DashboardCommand.AddWidget(widget = instance, traceId = commandId),
    )

    val json = buildJsonObject {
      put("status", "ok")
      put("typeId", renderer.typeId)
      put("widgetId", widgetId)
      put("displayName", renderer.displayName)
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> = mapOf(
    "typeId" to "Widget type ID to add (e.g., essentials:clock)",
  )
}
