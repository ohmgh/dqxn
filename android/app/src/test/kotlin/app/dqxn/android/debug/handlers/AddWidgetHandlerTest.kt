package app.dqxn.android.debug.handlers

import app.cash.turbine.test
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.command.DashboardCommandBus
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class AddWidgetHandlerTest {

  private val fakeWidgets: Set<WidgetRenderer> = setOf(
    StubRenderer(typeId = "essentials:clock", displayName = "Clock"),
    StubRenderer(typeId = "essentials:speedometer", displayName = "Speedometer"),
  )

  private val commandBus = DashboardCommandBus()
  private val handler = AddWidgetHandler(fakeWidgets, commandBus)

  @Test
  fun `handler name is add-widget`() {
    assertThat(handler.name).isEqualTo("add-widget")
  }

  @Test
  fun `handler category is layout`() {
    assertThat(handler.category).isEqualTo("layout")
  }

  @Test
  fun `successful add returns ok status with typeId and widgetId`() = runTest {
    val result = handler.execute(
      CommandParams(raw = mapOf("typeId" to "essentials:clock"), traceId = "test-trace"),
      "test-cmd",
    )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)
    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("ok")
    assertThat(json["typeId"]?.jsonPrimitive?.content).isEqualTo("essentials:clock")
    assertThat(json["widgetId"]?.jsonPrimitive?.content).isNotEmpty()
    assertThat(json["displayName"]?.jsonPrimitive?.content).isEqualTo("Clock")
  }

  @Test
  fun `unknown typeId returns error with UNKNOWN_TYPE code`() = runTest {
    val result = handler.execute(
      CommandParams(raw = mapOf("typeId" to "nonexistent:widget"), traceId = "test-trace"),
      "test-cmd",
    )

    assertThat(result).isInstanceOf(CommandResult.Error::class.java)
    val error = result as CommandResult.Error
    assertThat(error.code).isEqualTo("UNKNOWN_TYPE")
    assertThat(error.message).contains("nonexistent:widget")
  }

  @Test
  fun `missing typeId returns error with MISSING_PARAM code`() = runTest {
    val result = handler.execute(
      CommandParams(raw = emptyMap(), traceId = "test-trace"),
      "test-cmd",
    )

    assertThat(result).isInstanceOf(CommandResult.Error::class.java)
    val error = result as CommandResult.Error
    assertThat(error.code).isEqualTo("MISSING_PARAM")
  }

  @Test
  fun `widgetId is unique per invocation`() = runTest {
    val params = CommandParams(raw = mapOf("typeId" to "essentials:clock"), traceId = "t1")

    val result1 = handler.execute(params, "cmd-1") as CommandResult.Success
    val result2 = handler.execute(params, "cmd-2") as CommandResult.Success

    val id1 = Json.parseToJsonElement(result1.data).jsonObject["widgetId"]?.jsonPrimitive?.content
    val id2 = Json.parseToJsonElement(result2.data).jsonObject["widgetId"]?.jsonPrimitive?.content
    assertThat(id1).isNotEqualTo(id2)
  }

  @Test
  fun `paramsSchema documents typeId parameter`() {
    val schema = handler.paramsSchema()
    assertThat(schema).containsKey("typeId")
  }

  @Test
  fun `successful add dispatches AddWidget command to bus`() = runTest {
    commandBus.commands.test {
      handler.execute(
        CommandParams(raw = mapOf("typeId" to "essentials:clock"), traceId = "trace-1"),
        "cmd-1",
      )

      val command = awaitItem()
      assertThat(command).isInstanceOf(DashboardCommand.AddWidget::class.java)
      val addWidget = command as DashboardCommand.AddWidget
      assertThat(addWidget.widget.typeId).isEqualTo("essentials:clock")
      assertThat(addWidget.widget.instanceId).startsWith("widget-")
      assertThat(addWidget.widget.position).isEqualTo(GridPosition(col = 0, row = 0))
      assertThat(addWidget.widget.size).isEqualTo(GridSize(widthUnits = 4, heightUnits = 4))
      assertThat(addWidget.widget.style).isEqualTo(WidgetStyle.Default)
      assertThat(addWidget.traceId).isEqualTo("cmd-1")
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `unknown typeId does not dispatch to bus`() = runTest {
    commandBus.commands.test {
      handler.execute(
        CommandParams(raw = mapOf("typeId" to "nonexistent:widget"), traceId = "trace-1"),
        "cmd-1",
      )
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `missing typeId does not dispatch to bus`() = runTest {
    commandBus.commands.test {
      handler.execute(
        CommandParams(raw = emptyMap(), traceId = "trace-1"),
        "cmd-1",
      )
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }
}

/**
 * Minimal stub renderer for handler tests. Only [typeId] and [displayName] are relevant;
 * all other fields use sensible defaults.
 */
private class StubRenderer(
  override val typeId: String,
  override val displayName: String,
) : WidgetRenderer {
  override val description: String = ""
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = emptySet()
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 0
  override val requiredAnyEntitlement: Set<String>? = null
  override val settingsSchema: List<SettingDefinition<*>> = emptyList()

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(widthUnits = 4, heightUnits = 4, aspectRatio = null, settings = emptyMap())

  @androidx.compose.runtime.Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: androidx.compose.ui.Modifier,
  ) = Unit

  override fun accessibilityDescription(data: WidgetData): String = ""
  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false
}
