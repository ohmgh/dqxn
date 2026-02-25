package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.core.agentic.chaos.ChaosEngine
import app.dqxn.android.core.agentic.chaos.ChaosProviderInterceptor
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class ChaosStartHandlerTest {

  private data class StubSnapshot(override val timestamp: Long) : DataSnapshot

  private class StubProvider(override val sourceId: String) : DataProvider<StubSnapshot> {
    override val snapshotType: KClass<StubSnapshot> = StubSnapshot::class
    override val displayName: String = "Stub"
    override val description: String = "Stub"
    override val dataType: String = "stub"
    override val priority: ProviderPriority = ProviderPriority.SIMULATED
    override val schema: DataSchema = DataSchema(emptyList(), stalenessThresholdMs = 5000L)
    override val setupSchema: List<SetupPageDefinition> = emptyList()
    override val subscriberTimeout: Duration = 10.seconds
    override val firstEmissionTimeout: Duration = 5.seconds
    override val isAvailable: Boolean = true
    override val connectionState: Flow<Boolean> = MutableStateFlow(true)
    override val connectionErrorDescription: Flow<String?> = MutableStateFlow(null)
    override val requiredAnyEntitlement: Set<String>? = null
    override fun provideState(): Flow<StubSnapshot> = emptyFlow()
  }

  private val interceptor = ChaosProviderInterceptor()
  private val engine = ChaosEngine(interceptor)
  private val providers: Set<DataProvider<*>> = setOf(
    StubProvider("provider-a"),
    StubProvider("provider-b"),
  )
  private val handler = ChaosStartHandler(engine, providers)

  @AfterEach
  fun tearDown() {
    if (engine.isActive()) engine.stop()
  }

  @Test
  fun `execute with default params starts combined profile`() = runTest {
    val result = handler.execute(
      CommandParams(raw = emptyMap(), traceId = "test-trace"),
      "test-cmd",
    )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)
    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json["profile"]?.jsonPrimitive?.content).isEqualTo("combined")
  }

  @Test
  fun `execute with explicit seed and profile`() = runTest {
    val result = handler.execute(
      CommandParams(raw = mapOf("seed" to "42", "profile" to "provider-stress"), traceId = "t1"),
      "cmd-1",
    )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)
    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json["seed"]?.jsonPrimitive?.content).isEqualTo("42")
    assertThat(json["profile"]?.jsonPrimitive?.content).isEqualTo("provider-stress")
  }

  @Test
  fun `execute returns session info`() = runTest {
    val result = handler.execute(
      CommandParams(raw = mapOf("seed" to "42", "profile" to "provider-stress"), traceId = "t1"),
      "cmd-1",
    )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)
    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json["sessionId"]?.jsonPrimitive?.content).isNotEmpty()
    assertThat(json["providerCount"]?.jsonPrimitive?.content).isEqualTo("2")
  }

  @Test
  fun `execute while session active returns error`() = runTest {
    handler.execute(
      CommandParams(raw = mapOf("seed" to "1"), traceId = "t1"),
      "cmd-1",
    )

    val result = handler.execute(
      CommandParams(raw = mapOf("seed" to "2"), traceId = "t2"),
      "cmd-2",
    )

    assertThat(result).isInstanceOf(CommandResult.Error::class.java)
    assertThat((result as CommandResult.Error).code).isEqualTo("ALREADY_ACTIVE")
  }

  @Test
  fun `handler name and category are correct`() {
    assertThat(handler.name).isEqualTo("chaos-start")
    assertThat(handler.category).isEqualTo("chaos")
  }
}
