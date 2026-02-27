package app.dqxn.android.feature.dashboard.binding

import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class DataProviderRegistryImplTest {

  private val logger = NoOpLogger

  // Minimal test snapshot
  data class TestSnapshot(override val timestamp: Long = 0L) : DataSnapshot

  // Minimal provider for testing
  class TestProvider(
    override val sourceId: String,
    override val dataType: String,
    override val priority: ProviderPriority = ProviderPriority.SIMULATED,
    override val requiredAnyEntitlement: Set<String>? = null,
    override val isAvailable: Boolean = true,
  ) : DataProvider<TestSnapshot> {
    override val displayName: String = sourceId
    override val description: String = "Test"
    override val snapshotType: KClass<TestSnapshot> = TestSnapshot::class
    override val schema: DataSchema = DataSchema(emptyList(), 3000L)
    override val setupSchema: List<SetupPageDefinition> = emptyList()
    override val subscriberTimeout: Duration = 5.seconds
    override val firstEmissionTimeout: Duration = 5.seconds
    override val connectionState: Flow<Boolean> = flowOf(true)
    override val connectionErrorDescription: Flow<String?> = flowOf(null)

    override fun provideState(): Flow<TestSnapshot> = emptyFlow()
  }

  private fun fakeEntitlementManager(
    entitlements: Set<String> = setOf("free"),
  ): EntitlementManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = id in entitlements

      override fun getActiveEntitlements(): Set<String> = entitlements

      override val entitlementChanges: Flow<Set<String>> = MutableStateFlow(entitlements)
    }

  @Test
  fun `getAll returns all providers`() {
    val providers =
      setOf(
        TestProvider("speed", DataTypes.SPEED),
        TestProvider("battery", DataTypes.BATTERY),
      )
    val registry = DataProviderRegistryImpl(providers, fakeEntitlementManager(), logger)

    assertThat(registry.getAll()).hasSize(2)
  }

  @Test
  fun `findByDataType returns matching providers sorted by priority`() {
    val hardware = TestProvider("hw-speed", DataTypes.SPEED, ProviderPriority.HARDWARE)
    val simulated = TestProvider("sim-speed", DataTypes.SPEED, ProviderPriority.SIMULATED)
    val battery = TestProvider("battery", DataTypes.BATTERY)

    val registry =
      DataProviderRegistryImpl(
        setOf(simulated, hardware, battery),
        fakeEntitlementManager(),
        logger,
      )

    val speedProviders = registry.findByDataType(DataTypes.SPEED)
    assertThat(speedProviders).hasSize(2)
    // Sorted by priority ordinal: HARDWARE(0) < SIMULATED(3)
    assertThat(speedProviders[0].sourceId).isEqualTo("hw-speed")
    assertThat(speedProviders[1].sourceId).isEqualTo("sim-speed")
  }

  @Test
  fun `findByDataType returns empty for unknown type`() {
    val registry =
      DataProviderRegistryImpl(
        setOf(TestProvider("speed", DataTypes.SPEED)),
        fakeEntitlementManager(),
        logger,
      )

    assertThat(registry.findByDataType("NONEXISTENT")).isEmpty()
  }

  @Test
  fun `getFiltered excludes providers requiring missing entitlements`() {
    val freeProvider = TestProvider("free-speed", DataTypes.SPEED)
    val plusProvider =
      TestProvider(
        "plus-speed",
        DataTypes.SPEED,
        requiredAnyEntitlement = setOf("plus"),
      )

    val registry =
      DataProviderRegistryImpl(
        setOf(freeProvider, plusProvider),
        fakeEntitlementManager(setOf("free")),
        logger,
      )

    val filtered = registry.getFiltered { it == "free" }
    assertThat(filtered).hasSize(1)
    assertThat(filtered.first().sourceId).isEqualTo("free-speed")
  }

  @Test
  fun `availableProviders uses entitlementManager for filtering`() {
    val freeProvider = TestProvider("free-speed", DataTypes.SPEED)
    val plusProvider =
      TestProvider(
        "plus-speed",
        DataTypes.SPEED,
        requiredAnyEntitlement = setOf("plus"),
      )

    val registry =
      DataProviderRegistryImpl(
        setOf(freeProvider, plusProvider),
        fakeEntitlementManager(setOf("free", "plus")),
        logger,
      )

    val available = registry.availableProviders()
    assertThat(available).hasSize(2)
  }

  @Test
  fun `empty provider set produces empty registry`() {
    val registry =
      DataProviderRegistryImpl(
        emptySet(),
        fakeEntitlementManager(),
        logger,
      )

    assertThat(registry.getAll()).isEmpty()
    assertThat(registry.findByDataType(DataTypes.SPEED)).isEmpty()
    assertThat(registry.availableProviders()).isEmpty()
  }
}
