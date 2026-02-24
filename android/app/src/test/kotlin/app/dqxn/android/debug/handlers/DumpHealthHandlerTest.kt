package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.sdk.observability.health.WidgetHealthMonitor
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class DumpHealthHandlerTest {

  private val healthMonitor = WidgetHealthMonitor(
    logger = NoOpLogger,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
  )
  private val handler = DumpHealthHandler(healthMonitor)

  @Test
  fun `returns valid JSON when no widgets present`() = runTest {
    val result = handler.execute(
      CommandParams(traceId = "test-trace"),
      "test-cmd",
    )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)
    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json["widgetCount"]?.jsonPrimitive?.content).isEqualTo("0")
  }

  @Test
  fun `handler category is diagnostics`() {
    assertThat(handler.category).isEqualTo("diagnostics")
  }

  @Test
  fun `handler name is dump-health`() {
    assertThat(handler.name).isEqualTo("dump-health")
  }

  @Test
  fun `returns widget data when widgets are tracked`() = runTest {
    healthMonitor.reportData("widget-1", "essentials:clock")

    val result = handler.execute(
      CommandParams(traceId = "test-trace"),
      "test-cmd",
    )

    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json["widgetCount"]?.jsonPrimitive?.content).isEqualTo("1")
  }
}
