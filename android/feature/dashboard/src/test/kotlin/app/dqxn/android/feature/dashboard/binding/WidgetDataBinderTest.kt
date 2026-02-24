package app.dqxn.android.feature.dashboard.binding

import app.dqxn.android.core.thermal.FakeThermalManager
import app.dqxn.android.core.thermal.RenderConfig
import app.dqxn.android.core.thermal.ThermalLevel
import app.dqxn.android.feature.dashboard.test.TestWidgetFactory.testWidget
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataProviderInterceptor
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class WidgetDataBinderTest {

  private val logger = NoOpLogger
  private val thermalMonitor = FakeThermalManager()

  // -- Test snapshot types ---

  data class SpeedSnapshot(
    override val timestamp: Long = System.currentTimeMillis(),
    val speed: Double = 0.0,
  ) : DataSnapshot

  data class BatterySnapshot(
    override val timestamp: Long = System.currentTimeMillis(),
    val level: Int = 100,
  ) : DataSnapshot

  // -- Simple test providers ---

  class SimpleProvider<T : DataSnapshot>(
    private val dataFlow: Flow<T>,
    override val sourceId: String,
    override val displayName: String = "Test Provider",
    override val description: String = "A test provider",
    override val dataType: String = DataTypes.SPEED,
    override val priority: ProviderPriority = ProviderPriority.SIMULATED,
    override val snapshotType: KClass<T>,
    override val schema: DataSchema = DataSchema(emptyList(), 3000L),
    override val setupSchema: List<SetupPageDefinition> = emptyList(),
    override val subscriberTimeout: Duration = 5.seconds,
    override val firstEmissionTimeout: Duration = 5.seconds,
    override val requiredAnyEntitlement: Set<String>? = null,
    override val isAvailable: Boolean = true,
  ) : DataProvider<T> {
    override val connectionState: Flow<Boolean> = flowOf(true)
    override val connectionErrorDescription: Flow<String?> = flowOf(null)
    override fun provideState(): Flow<T> = dataFlow
  }

  // -- Fake registries ---

  class SimpleProviderRegistry(private val providers: Set<DataProvider<*>>) : DataProviderRegistry {
    override fun getAll(): Set<DataProvider<*>> = providers
    override fun findByDataType(dataType: String): List<DataProvider<*>> =
      providers.filter { it.dataType == dataType }
    override fun getFiltered(entitlementCheck: (String) -> Boolean): Set<DataProvider<*>> =
      providers
  }

  private fun createBinder(
    providers: Set<DataProvider<*>> = emptySet(),
    interceptors: Set<DataProviderInterceptor> = emptySet(),
  ): WidgetDataBinder {
    return WidgetDataBinder(
      providerRegistry = SimpleProviderRegistry(providers),
      interceptors = interceptors,
      thermalMonitor = thermalMonitor,
      logger = logger,
    )
  }

  @Test
  fun `bind with single provider emits WidgetData with correct slot`() = runTest {
    val speedFlow = MutableSharedFlow<SpeedSnapshot>()
    val provider = SimpleProvider(
      dataFlow = speedFlow,
      sourceId = "test:speed",
      snapshotType = SpeedSnapshot::class,
    )
    val binder = createBinder(setOf(provider))

    val widget = testWidget(typeId = "essentials:speed")
    val renderConfig = MutableStateFlow(RenderConfig.DEFAULT)
    val dataFlow = binder.bind(widget, setOf(SpeedSnapshot::class), renderConfig)

    dataFlow.test {
      // First emission is Empty from scan seed
      val seed = awaitItem()
      assertThat(seed).isEqualTo(app.dqxn.android.sdk.contracts.widget.WidgetData.Empty)

      speedFlow.emit(SpeedSnapshot(timestamp = 1000L, speed = 60.0))

      val latest = awaitItem()
      assertThat(latest.hasData()).isTrue()
      val speedData = latest.snapshot<SpeedSnapshot>()
      assertThat(speedData).isNotNull()
      assertThat(speedData!!.speed).isEqualTo(60.0)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `bind with multiple providers merges via scan`() = runTest {
    val speedFlow = MutableSharedFlow<SpeedSnapshot>()
    val batteryFlow = MutableSharedFlow<BatterySnapshot>()

    val speedProvider = SimpleProvider(
      dataFlow = speedFlow,
      sourceId = "test:speed",
      snapshotType = SpeedSnapshot::class,
      dataType = DataTypes.SPEED,
    )
    val batteryProvider = SimpleProvider(
      dataFlow = batteryFlow,
      sourceId = "test:battery",
      snapshotType = BatterySnapshot::class,
      dataType = DataTypes.BATTERY,
    )

    val binder = createBinder(setOf(speedProvider, batteryProvider))

    val widget = testWidget(typeId = "essentials:multi")
    val renderConfig = MutableStateFlow(RenderConfig.DEFAULT)
    val dataFlow = binder.bind(
      widget,
      setOf(SpeedSnapshot::class, BatterySnapshot::class),
      renderConfig,
    )

    dataFlow.test {
      // Scan seed
      val seed = awaitItem()
      assertThat(seed).isEqualTo(app.dqxn.android.sdk.contracts.widget.WidgetData.Empty)

      speedFlow.emit(SpeedSnapshot(timestamp = 100L, speed = 80.0))
      val afterSpeed = awaitItem()
      assertThat(afterSpeed.snapshot<SpeedSnapshot>()?.speed).isEqualTo(80.0)

      batteryFlow.emit(BatterySnapshot(timestamp = 200L, level = 75))
      val afterBattery = awaitItem()

      // Should have accumulated both slots
      assertThat(afterBattery.snapshot<SpeedSnapshot>()?.speed).isEqualTo(80.0)
      assertThat(afterBattery.snapshot<BatterySnapshot>()?.level).isEqualTo(75)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `merge+scan - one stuck provider does not block other slots`() = runTest {
    val speedFlow = MutableSharedFlow<SpeedSnapshot>()
    // Battery provider never emits (stalled)
    val batteryFlow = MutableSharedFlow<BatterySnapshot>()

    val speedProvider = SimpleProvider(
      dataFlow = speedFlow,
      sourceId = "test:speed",
      snapshotType = SpeedSnapshot::class,
      dataType = DataTypes.SPEED,
    )
    val batteryProvider = SimpleProvider(
      dataFlow = batteryFlow,
      sourceId = "test:battery-stall",
      snapshotType = BatterySnapshot::class,
      dataType = DataTypes.BATTERY,
    )

    val binder = createBinder(setOf(speedProvider, batteryProvider))

    val widget = testWidget(typeId = "essentials:partial")
    val renderConfig = MutableStateFlow(RenderConfig.DEFAULT)
    val dataFlow = binder.bind(
      widget,
      setOf(SpeedSnapshot::class, BatterySnapshot::class),
      renderConfig,
    )

    dataFlow.test {
      // Scan seed
      awaitItem()

      // Only speed emits; battery is stuck
      speedFlow.emit(SpeedSnapshot(timestamp = 100L, speed = 120.0))
      val latest = awaitItem()

      // Speed data should be available even though battery never emitted
      assertThat(latest.snapshot<SpeedSnapshot>()).isNotNull()
      assertThat(latest.snapshot<BatterySnapshot>()).isNull()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `provider fallback - user-selected unavailable falls back to next priority`() {
    val hardwareProvider = SimpleProvider(
      dataFlow = flowOf(SpeedSnapshot(timestamp = 1L, speed = 100.0)),
      sourceId = "hw:speed",
      snapshotType = SpeedSnapshot::class,
      priority = ProviderPriority.HARDWARE,
    )
    val simulatedProvider = SimpleProvider(
      dataFlow = flowOf(SpeedSnapshot(timestamp = 1L, speed = 50.0)),
      sourceId = "sim:speed",
      snapshotType = SpeedSnapshot::class,
      priority = ProviderPriority.SIMULATED,
    )

    val binder = createBinder(setOf(hardwareProvider, simulatedProvider))

    // User selected a nonexistent provider -> should fallback to HARDWARE first
    val resolved = binder.resolveProvider(SpeedSnapshot::class, "nonexistent:provider")
    assertThat(resolved).isNotNull()
    assertThat(resolved!!.sourceId).isEqualTo("hw:speed")
  }

  @Test
  fun `provider resolution priority order`() {
    val simulated = SimpleProvider(
      dataFlow = flowOf(SpeedSnapshot()),
      sourceId = "sim:speed",
      snapshotType = SpeedSnapshot::class,
      priority = ProviderPriority.SIMULATED,
    )
    val network = SimpleProvider(
      dataFlow = flowOf(SpeedSnapshot()),
      sourceId = "net:speed",
      snapshotType = SpeedSnapshot::class,
      priority = ProviderPriority.NETWORK,
    )
    val deviceSensor = SimpleProvider(
      dataFlow = flowOf(SpeedSnapshot()),
      sourceId = "sensor:speed",
      snapshotType = SpeedSnapshot::class,
      priority = ProviderPriority.DEVICE_SENSOR,
    )

    val binder = createBinder(setOf(simulated, network, deviceSensor))

    // Should resolve DEVICE_SENSOR (highest available priority)
    val resolved = binder.resolveProvider(SpeedSnapshot::class)
    assertThat(resolved!!.sourceId).isEqualTo("sensor:speed")
  }

  @Test
  fun `interceptor chain applied to provider flows`() = runTest {
    val speedFlow = MutableSharedFlow<SpeedSnapshot>()
    val provider = SimpleProvider(
      dataFlow = speedFlow,
      sourceId = "test:speed",
      snapshotType = SpeedSnapshot::class,
    )

    // Interceptor that doubles the speed
    val doublingInterceptor = object : DataProviderInterceptor {
      @Suppress("UNCHECKED_CAST")
      override fun <T : DataSnapshot> intercept(
        provider: DataProvider<T>,
        upstream: Flow<T>,
      ): Flow<T> =
        upstream.map { snapshot ->
          if (snapshot is SpeedSnapshot) {
            snapshot.copy(speed = snapshot.speed * 2) as T
          } else {
            snapshot
          }
        }
    }

    val binder = createBinder(setOf(provider), setOf(doublingInterceptor))
    val widget = testWidget(typeId = "essentials:speed")
    val renderConfig = MutableStateFlow(RenderConfig.DEFAULT)
    val dataFlow = binder.bind(widget, setOf(SpeedSnapshot::class), renderConfig)

    dataFlow.test {
      // Scan seed
      awaitItem()

      speedFlow.emit(SpeedSnapshot(timestamp = 100L, speed = 30.0))
      val latest = awaitItem()

      // Speed should be doubled by interceptor: 30 * 2 = 60
      assertThat(latest.snapshot<SpeedSnapshot>()!!.speed).isEqualTo(60.0)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `empty compatible snapshots emits Empty`() = runTest {
    val binder = createBinder()
    val widget = testWidget(typeId = "essentials:empty")
    val renderConfig = MutableStateFlow(RenderConfig.DEFAULT)

    val result = binder.bind(widget, emptySet(), renderConfig)
    val items = result.take(1).toList()

    assertThat(items).hasSize(1)
    assertThat(items[0]).isEqualTo(app.dqxn.android.sdk.contracts.widget.WidgetData.Empty)
  }

  @Test
  fun `thermal throttle reduces emission rate under DEGRADED config`() = runTest {
    var fakeTime = 0L
    val speedFlow = MutableSharedFlow<SpeedSnapshot>()
    val provider = SimpleProvider(
      dataFlow = speedFlow,
      sourceId = "test:speed",
      snapshotType = SpeedSnapshot::class,
    )
    val binder = WidgetDataBinder(
      providerRegistry = SimpleProviderRegistry(setOf(provider)),
      interceptors = emptySet(),
      thermalMonitor = thermalMonitor,
      logger = logger,
      timeProvider = { fakeTime },
    )

    // Set DEGRADED thermal level (30fps -> 33ms throttle interval)
    thermalMonitor.setLevel(ThermalLevel.DEGRADED)

    val widget = testWidget(typeId = "essentials:speed")
    val renderConfig = thermalMonitor.renderConfig
    val dataFlow = binder.bind(widget, setOf(SpeedSnapshot::class), renderConfig)

    dataFlow.test {
      // Scan seed (Empty)
      val seed = awaitItem()
      assertThat(seed).isEqualTo(app.dqxn.android.sdk.contracts.widget.WidgetData.Empty)

      // First emission always passes (lastEmitTime starts at 0, fakeTime=0, interval check: 0-0 >= 33 is false)
      // Actually: lastEmitTime=0, now=0, 0-0=0 >= 33 is false. Let's set fakeTime to something > 33.
      fakeTime = 100L
      speedFlow.emit(SpeedSnapshot(timestamp = 100L, speed = 1.0))
      val first = awaitItem()
      assertThat(first.snapshot<SpeedSnapshot>()!!.speed).isEqualTo(1.0)

      // Emit at fakeTime=120 (only 20ms later) -> should be throttled
      fakeTime = 120L
      speedFlow.emit(SpeedSnapshot(timestamp = 120L, speed = 2.0))

      // Emit at fakeTime=140 (40ms from first emission) -> should pass (100+33=133, 140 >= 133)
      fakeTime = 140L
      speedFlow.emit(SpeedSnapshot(timestamp = 140L, speed = 3.0))
      val second = awaitItem()
      // speed=2.0 was throttled, speed=3.0 passes
      assertThat(second.snapshot<SpeedSnapshot>()!!.speed).isEqualTo(3.0)

      cancelAndIgnoreRemainingEvents()
    }
  }
}
