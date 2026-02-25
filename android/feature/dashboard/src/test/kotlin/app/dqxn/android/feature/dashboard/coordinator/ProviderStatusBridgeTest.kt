package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class ProviderStatusBridgeTest {

  private val logger = NoOpLogger

  // -- Test snapshot type ---

  data class TestSnapshot(override val timestamp: Long = System.currentTimeMillis()) : DataSnapshot

  // -- Configurable test provider ---

  class TestProvider(
    override val sourceId: String = "test:provider",
    override val displayName: String = "Test Provider",
    val connectionFlow: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val errorFlow: MutableStateFlow<String?> = MutableStateFlow(null),
  ) : DataProvider<TestSnapshot> {
    override val description: String = "A test provider"
    override val dataType: String = DataTypes.SPEED
    override val priority: ProviderPriority = ProviderPriority.SIMULATED
    override val snapshotType: KClass<TestSnapshot> = TestSnapshot::class
    override val schema: DataSchema = DataSchema(emptyList(), 3000L)
    override val setupSchema: List<SetupPageDefinition> = emptyList()
    override val subscriberTimeout: Duration = 5.seconds
    override val firstEmissionTimeout: Duration = 5.seconds
    override val requiredAnyEntitlement: Set<String>? = null
    override val isAvailable: Boolean = true
    override val connectionState: Flow<Boolean> = connectionFlow
    override val connectionErrorDescription: Flow<String?> = errorFlow
    override fun provideState(): Flow<TestSnapshot> = flow { emit(TestSnapshot()) }
  }

  // -- Simple test registry ---

  class TestRegistry(private val providers: Set<DataProvider<*>>) : DataProviderRegistry {
    override fun getAll(): Set<DataProvider<*>> = providers
    override fun findByDataType(dataType: String): List<DataProvider<*>> =
      providers.filter { it.dataType == dataType }
    override fun getFiltered(entitlementCheck: (String) -> Boolean): Set<DataProvider<*>> =
      providers
  }

  @Test
  fun `providerStatuses emits empty map initially when no providers registered`() = runTest {
    val registry = TestRegistry(emptySet())
    val bridge = ProviderStatusBridge(registry, logger)

    bridge.providerStatuses().test {
      val statuses = awaitItem()
      assertThat(statuses).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `providerStatuses reflects bound provider with correct id and displayName`() = runTest {
    val provider = TestProvider(
      sourceId = "essentials:gps-speed",
      displayName = "GPS Speed",
    )
    val registry = TestRegistry(setOf(provider))
    val bridge = ProviderStatusBridge(registry, logger)

    bridge.providerStatuses().test {
      val statuses = awaitItem()
      assertThat(statuses).hasSize(1)
      assertThat(statuses).containsKey("essentials:gps-speed")

      val status = statuses["essentials:gps-speed"]!!
      assertThat(status.providerId).isEqualTo("essentials:gps-speed")
      assertThat(status.displayName).isEqualTo("GPS Speed")
      assertThat(status.isConnected).isTrue()
      assertThat(status.errorDescription).isNull()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `provider error updates status description`() = runTest {
    val provider = TestProvider(
      sourceId = "essentials:accelerometer",
      displayName = "Accelerometer",
    )
    val registry = TestRegistry(setOf(provider))
    val bridge = ProviderStatusBridge(registry, logger)

    bridge.providerStatuses().test {
      // Initial state: connected, no error
      val initial = awaitItem()
      assertThat(initial["essentials:accelerometer"]!!.errorDescription).isNull()

      // Emit error description
      provider.errorFlow.value = "Sensor unavailable"
      val updated = awaitItem()
      assertThat(updated["essentials:accelerometer"]!!.errorDescription)
        .isEqualTo("Sensor unavailable")

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `disconnected provider shows isConnected false`() = runTest {
    val provider = TestProvider(
      sourceId = "essentials:battery",
      displayName = "Battery",
    )
    val registry = TestRegistry(setOf(provider))
    val bridge = ProviderStatusBridge(registry, logger)

    bridge.providerStatuses().test {
      // Initial state: connected
      val initial = awaitItem()
      assertThat(initial["essentials:battery"]!!.isConnected).isTrue()

      // Disconnect provider
      provider.connectionFlow.value = false
      val disconnected = awaitItem()
      assertThat(disconnected["essentials:battery"]!!.isConnected).isFalse()

      cancelAndIgnoreRemainingEvents()
    }
  }
}
