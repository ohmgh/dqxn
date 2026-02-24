package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.core.thermal.FakeThermalManager
import app.dqxn.android.feature.dashboard.binding.DataProviderRegistryImpl
import app.dqxn.android.feature.dashboard.binding.WidgetDataBinder
import app.dqxn.android.feature.dashboard.binding.WidgetRegistryImpl
import app.dqxn.android.feature.dashboard.safety.SafeModeManager
import app.dqxn.android.feature.dashboard.test.FakeSharedPreferences
import app.dqxn.android.feature.dashboard.test.TestWidgetFactory.testWidget
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import app.dqxn.android.sdk.contracts.status.WidgetRenderState
import app.dqxn.android.sdk.contracts.testing.TestWidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class WidgetBindingCoordinatorTest {

  private val logger = NoOpLogger
  private val metricsCollector = MetricsCollector()
  private val thermalMonitor = FakeThermalManager()

  // -- Test snapshot type ---

  data class TestSnapshot(override val timestamp: Long = System.currentTimeMillis()) : DataSnapshot

  // -- Crashing provider for isolation tests ---

  class CrashingProvider(
    override val sourceId: String = "test:crashing",
    override val snapshotType: KClass<TestSnapshot> = TestSnapshot::class,
  ) : DataProvider<TestSnapshot> {
    override val displayName: String = "Crashing Provider"
    override val description: String = "Crashes immediately"
    override val dataType: String = DataTypes.SPEED
    override val priority: ProviderPriority = ProviderPriority.SIMULATED
    override val schema: DataSchema = DataSchema(emptyList(), 3000L)
    override val setupSchema: List<SetupPageDefinition> = emptyList()
    override val subscriberTimeout: Duration = 5.seconds
    override val firstEmissionTimeout: Duration = 5.seconds
    override val requiredAnyEntitlement: Set<String>? = null
    override val isAvailable: Boolean = true
    override val connectionState: Flow<Boolean> = flowOf(true)
    override val connectionErrorDescription: Flow<String?> = flowOf(null)
    override fun provideState(): Flow<TestSnapshot> = flow {
      throw RuntimeException("Provider crashed")
    }
  }

  // -- Fake entitlement manager ---

  class FakeEntitlementManager : EntitlementManager {
    private val _entitlements = MutableStateFlow<Set<String>>(setOf("free"))
    override fun hasEntitlement(id: String): Boolean = _entitlements.value.contains(id)
    override fun getActiveEntitlements(): Set<String> = _entitlements.value
    override val entitlementChanges: Flow<Set<String>> = _entitlements

    fun setEntitlements(entitlements: Set<String>) {
      _entitlements.value = entitlements
    }
  }

  // -- Continuous provider that emits on demand ---

  class ContinuousProvider(
    val emissionFlow: MutableSharedFlow<TestSnapshot> = MutableSharedFlow(),
    override val sourceId: String = "test:continuous",
  ) : DataProvider<TestSnapshot> {
    override val displayName: String = "Continuous Provider"
    override val description: String = "Emits on demand"
    override val dataType: String = DataTypes.SPEED
    override val priority: ProviderPriority = ProviderPriority.SIMULATED
    override val snapshotType: KClass<TestSnapshot> = TestSnapshot::class
    override val schema: DataSchema = DataSchema(emptyList(), 3000L)
    override val setupSchema: List<SetupPageDefinition> = emptyList()
    override val subscriberTimeout: Duration = 5.seconds
    override val firstEmissionTimeout: Duration = 5.seconds
    override val requiredAnyEntitlement: Set<String>? = null
    override val isAvailable: Boolean = true
    override val connectionState: Flow<Boolean> = flowOf(true)
    override val connectionErrorDescription: Flow<String?> = flowOf(null)
    override fun provideState(): Flow<TestSnapshot> = emissionFlow
  }

  /**
   * Helper to create a coordinator within a [TestScope], using the scope's scheduler for the
   * dispatchers. This avoids "different schedulers" errors.
   */
  private fun TestScope.createCoordinator(
    widgetRegistry: WidgetRegistry,
    providerRegistry: DataProviderRegistry,
    entitlementManager: FakeEntitlementManager = FakeEntitlementManager(),
    safeModeManager: SafeModeManager = SafeModeManager(FakeSharedPreferences(), logger),
    dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    binder: WidgetDataBinder? = null,
    timeProvider: (() -> Long)? = null,
  ): WidgetBindingCoordinator {
    val actualBinder = binder ?: WidgetDataBinder(
      providerRegistry = providerRegistry,
      interceptors = emptySet(),
      thermalMonitor = thermalMonitor,
      logger = logger,
    )
    return WidgetBindingCoordinator(
      binder = actualBinder,
      widgetRegistry = widgetRegistry,
      safeModeManager = safeModeManager,
      entitlementManager = entitlementManager,
      thermalMonitor = thermalMonitor,
      metricsCollector = metricsCollector,
      logger = logger,
      ioDispatcher = dispatcher,
      defaultDispatcher = dispatcher,
      timeProvider = timeProvider ?: { System.currentTimeMillis() },
    )
  }

  @Test
  fun `bind creates job and emits widget data`() = runTest {
    val provider = ContinuousProvider()
    val renderer = TestWidgetRenderer(
      typeId = "essentials:clock",
      compatibleSnapshots = setOf(TestSnapshot::class),
    )
    val widgetRegistry = WidgetRegistryImpl(setOf(renderer), logger)
    val providerRegistry = DataProviderRegistryImpl(setOf(provider), FakeEntitlementManager(), logger)
    val coordinator = createCoordinator(widgetRegistry, providerRegistry)

    val initJob = Job(coroutineContext[Job])
    val initScope = this + initJob
    coordinator.initialize(initScope)
    testScheduler.runCurrent()

    val widget = testWidget(typeId = "essentials:clock")
    coordinator.bind(widget)
    testScheduler.runCurrent()

    provider.emissionFlow.emit(TestSnapshot(timestamp = 1000L))
    testScheduler.runCurrent()

    val data = coordinator.widgetData(widget.instanceId).value
    assertThat(data.hasData()).isTrue()
    assertThat(coordinator.activeBindings()).containsKey(widget.instanceId)

    coordinator.destroy()
    initJob.cancel()
  }

  @Test
  fun `unbind cancels job and removes data flow`() = runTest {
    val provider = ContinuousProvider()
    val renderer = TestWidgetRenderer(
      typeId = "essentials:clock",
      compatibleSnapshots = setOf(TestSnapshot::class),
    )
    val widgetRegistry = WidgetRegistryImpl(setOf(renderer), logger)
    val providerRegistry = DataProviderRegistryImpl(setOf(provider), FakeEntitlementManager(), logger)
    val coordinator = createCoordinator(widgetRegistry, providerRegistry)

    val initJob = Job(coroutineContext[Job])
    val initScope = this + initJob
    coordinator.initialize(initScope)
    testScheduler.runCurrent()

    val widget = testWidget(typeId = "essentials:clock")
    coordinator.bind(widget)
    testScheduler.runCurrent()

    assertThat(coordinator.activeBindings()).containsKey(widget.instanceId)

    coordinator.unbind(widget.instanceId)

    assertThat(coordinator.activeBindings()).doesNotContainKey(widget.instanceId)

    coordinator.destroy()
    initJob.cancel()
  }

  @Test
  fun `SupervisorJob isolation - one provider crash does not cancel sibling bindings`() =
    runTest {
      val goodProvider = ContinuousProvider(sourceId = "test:good")
      val crashProvider = CrashingProvider(sourceId = "test:crash")

      val goodRenderer = TestWidgetRenderer(
        typeId = "essentials:clock",
        compatibleSnapshots = setOf(TestSnapshot::class),
      )
      val crashRenderer = TestWidgetRenderer(
        typeId = "essentials:crash",
        compatibleSnapshots = setOf(TestSnapshot::class),
      )

      val widgetRegistry = WidgetRegistryImpl(setOf(goodRenderer, crashRenderer), logger)
      val providerRegistry = DataProviderRegistryImpl(
        setOf(goodProvider, crashProvider),
        FakeEntitlementManager(),
        logger,
      )
      val coordinator = createCoordinator(widgetRegistry, providerRegistry)

      val initJob = Job(coroutineContext[Job])
      val initScope = this + initJob
      coordinator.initialize(initScope)
      testScheduler.runCurrent()

      val goodWidget = testWidget(typeId = "essentials:clock", instanceId = "good-widget")
      val crashWidget = testWidget(typeId = "essentials:crash", instanceId = "crash-widget")

      coordinator.bind(goodWidget)
      coordinator.bind(crashWidget)
      testScheduler.runCurrent()

      // Good widget should still have an active binding despite crash widget failing
      val goodBinding = coordinator.activeBindings()["good-widget"]
      assertThat(goodBinding).isNotNull()
      assertThat(goodBinding!!.isActive).isTrue()

      coordinator.destroy()
      initJob.cancel()
    }

  @Test
  fun `widget crash reported to SafeModeManager`() = runTest {
    val safeModeManager = SafeModeManager(FakeSharedPreferences(), logger)
    val widgetRegistry = WidgetRegistryImpl(emptySet(), logger)
    val providerRegistry = DataProviderRegistryImpl(emptySet(), FakeEntitlementManager(), logger)
    val coordinator = createCoordinator(
      widgetRegistry, providerRegistry, safeModeManager = safeModeManager,
    )

    val initJob = Job(coroutineContext[Job])
    val initScope = this + initJob
    coordinator.initialize(initScope)
    testScheduler.runCurrent()

    // Report crashes directly (bypassing binding retry complexity).
    // SafeModeManager triggers at threshold=4 crashes within the window.
    repeat(4) {
      coordinator.reportCrash("crash-$it", "essentials:crash")
    }

    assertThat(safeModeManager.safeModeActive.value).isTrue()

    initJob.cancel()
    coordinator.destroy()
  }

  @Test
  fun `pauseAll cancels all bindings, resumeAll rebinds`() =
    runTest {
      val provider = ContinuousProvider()
      val renderer = TestWidgetRenderer(
        typeId = "essentials:clock",
        compatibleSnapshots = setOf(TestSnapshot::class),
      )
      val widgetRegistry = WidgetRegistryImpl(setOf(renderer), logger)
      val providerRegistry = DataProviderRegistryImpl(setOf(provider), FakeEntitlementManager(), logger)
      val coordinator = createCoordinator(widgetRegistry, providerRegistry)

      val initJob = Job(coroutineContext[Job])
      val initScope = this + initJob
      coordinator.initialize(initScope)
      testScheduler.runCurrent()

      val widget1 = testWidget(typeId = "essentials:clock", instanceId = "w1")
      val widget2 = testWidget(typeId = "essentials:clock", instanceId = "w2")
      coordinator.bind(widget1)
      coordinator.bind(widget2)
      testScheduler.runCurrent()

      assertThat(coordinator.activeBindings()).hasSize(2)

      coordinator.pauseAll()
      assertThat(coordinator.activeBindings()).isEmpty()

      coordinator.resumeAll()
      testScheduler.runCurrent()
      assertThat(coordinator.activeBindings()).hasSize(2)

      coordinator.destroy()
      initJob.cancel()
    }

  @Test
  fun `entitlement revocation updates widget status`() = runTest {
    val provider = ContinuousProvider()
    val renderer = TestWidgetRenderer(
      typeId = "essentials:premium",
      compatibleSnapshots = setOf(TestSnapshot::class),
      requiredAnyEntitlement = setOf("plus"),
    )
    val entitlementManager = FakeEntitlementManager()
    entitlementManager.setEntitlements(setOf("free", "plus"))

    val widgetRegistry = WidgetRegistryImpl(setOf(renderer), logger)
    val providerRegistry = DataProviderRegistryImpl(setOf(provider), entitlementManager, logger)
    val coordinator = createCoordinator(widgetRegistry, providerRegistry, entitlementManager)

    val initJob = Job(coroutineContext[Job])
    val initScope = this + initJob
    coordinator.initialize(initScope)
    testScheduler.runCurrent()

    val widget = testWidget(typeId = "essentials:premium", instanceId = "premium-widget")
    coordinator.bind(widget)
    testScheduler.runCurrent()

    // Revoke "plus" entitlement
    entitlementManager.setEntitlements(setOf("free"))
    testScheduler.runCurrent()

    val status = coordinator.widgetStatus("premium-widget").value
    assertThat(status.overlayState).isInstanceOf(WidgetRenderState.EntitlementRevoked::class.java)

    coordinator.destroy()
    initJob.cancel()
  }

  @Test
  fun `status priority - ProviderMissing when no renderer found`() =
    runTest {
      val widgetRegistry = WidgetRegistryImpl(emptySet(), logger)
      val providerRegistry = DataProviderRegistryImpl(emptySet(), FakeEntitlementManager(), logger)
      val coordinator = createCoordinator(widgetRegistry, providerRegistry)

      val initJob = Job(coroutineContext[Job])
      val initScope = this + initJob
      coordinator.initialize(initScope)
      testScheduler.runCurrent()

      val widget = testWidget(typeId = "essentials:nonexistent", instanceId = "missing-widget")
      coordinator.bind(widget)
      testScheduler.runCurrent()

      val status = coordinator.widgetStatus("missing-widget").value
      assertThat(status.overlayState).isEqualTo(WidgetRenderState.ProviderMissing)

      coordinator.destroy()
      initJob.cancel()
    }

  // -- Exponential backoff retry tests (NF16) ---
  //
  // These use a mocked WidgetDataBinder to isolate the coordinator's retry logic from the
  // binder's merge+scan+flatMapLatest flow pipeline. The real pipeline's internal channelFlow
  // dispatches interact badly with StandardTestDispatcher + CoroutineExceptionHandler + SupervisorJob,
  // causing test hangs. Mocking the binder returns a simple flow { throw } that crashes
  // synchronously, testing ONLY the coordinator's handleBindingError → delay → startBinding cycle.

  /**
   * Creates a mock [WidgetDataBinder] whose [bind] returns a flow controlled by [flowFactory].
   * Each call to bind() invokes [flowFactory] to get the flow, allowing per-call behavior.
   * [minStalenessThresholdMs] returns null to disable the staleness watchdog in retry tests.
   */
  private fun createMockBinder(flowFactory: () -> Flow<WidgetData>): WidgetDataBinder {
    val binder = mockk<WidgetDataBinder>()
    every { binder.bind(any(), any(), any()) } answers { flowFactory() }
    every { binder.minStalenessThresholdMs(any()) } returns null
    return binder
  }

  @Test
  fun `exponential backoff retry timing 1s 2s 4s`() = runTest {
    var callCount = 0
    val binder = createMockBinder {
      flow {
        callCount++
        if (callCount <= 3) {
          throw RuntimeException("Crash #$callCount")
        }
        emit(WidgetData.Empty.withSlot(TestSnapshot::class, TestSnapshot(timestamp = 1000L)))
        awaitCancellation()
      }
    }
    val renderer = TestWidgetRenderer(
      typeId = "essentials:clock",
      compatibleSnapshots = setOf(TestSnapshot::class),
    )
    val widgetRegistry = WidgetRegistryImpl(setOf(renderer), logger)
    val providerRegistry = DataProviderRegistryImpl(emptySet(), FakeEntitlementManager(), logger)
    val coordinator = createCoordinator(widgetRegistry, providerRegistry, binder = binder)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)

    val widget = testWidget(typeId = "essentials:clock")
    coordinator.bind(widget)
    testScheduler.runCurrent() // crash #1

    assertThat(callCount).isEqualTo(1)

    // First retry after 1s backoff
    testScheduler.advanceTimeBy(1001)
    testScheduler.runCurrent()
    assertThat(callCount).isEqualTo(2) // crash #2

    // Second retry after 2s backoff
    testScheduler.advanceTimeBy(2001)
    testScheduler.runCurrent()
    assertThat(callCount).isEqualTo(3) // crash #3

    // Third retry after 4s backoff — call #4 succeeds
    testScheduler.advanceTimeBy(4001)
    testScheduler.runCurrent()
    assertThat(callCount).isEqualTo(4)

    coordinator.destroy()
    initJob.cancel()
  }

  @Test
  fun `max 3 retries then ConnectionError status`() = runTest {
    var callCount = 0
    val binder = createMockBinder {
      flow {
        callCount++
        throw RuntimeException("Crash #$callCount")
      }
    }
    val renderer = TestWidgetRenderer(
      typeId = "essentials:clock",
      compatibleSnapshots = setOf(TestSnapshot::class),
    )
    val widgetRegistry = WidgetRegistryImpl(setOf(renderer), logger)
    val providerRegistry = DataProviderRegistryImpl(emptySet(), FakeEntitlementManager(), logger)
    val coordinator = createCoordinator(widgetRegistry, providerRegistry, binder = binder)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)

    val widget = testWidget(typeId = "essentials:clock")
    coordinator.bind(widget)
    testScheduler.runCurrent() // crash #1

    testScheduler.advanceTimeBy(1001)
    testScheduler.runCurrent() // crash #2

    testScheduler.advanceTimeBy(2001)
    testScheduler.runCurrent() // crash #3

    testScheduler.advanceTimeBy(4001)
    testScheduler.runCurrent() // crash #4 → count exceeds MAX_RETRIES → ConnectionError

    assertThat(callCount).isEqualTo(4)
    val status = coordinator.widgetStatus(widget.instanceId).value
    assertThat(status.overlayState).isInstanceOf(WidgetRenderState.ConnectionError::class.java)

    coordinator.destroy()
    initJob.cancel()
  }

  @Test
  fun `successful emission resets error count`() = runTest {
    var callCount = 0
    val binder = createMockBinder {
      flow {
        callCount++
        if (callCount <= 1) {
          throw RuntimeException("Crash #$callCount")
        }
        emit(WidgetData.Empty.withSlot(TestSnapshot::class, TestSnapshot(timestamp = 1000L)))
        awaitCancellation()
      }
    }
    val renderer = TestWidgetRenderer(
      typeId = "essentials:clock",
      compatibleSnapshots = setOf(TestSnapshot::class),
    )
    val widgetRegistry = WidgetRegistryImpl(setOf(renderer), logger)
    val providerRegistry = DataProviderRegistryImpl(emptySet(), FakeEntitlementManager(), logger)
    val coordinator = createCoordinator(widgetRegistry, providerRegistry, binder = binder)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)

    val widget = testWidget(typeId = "essentials:clock")
    coordinator.bind(widget)
    testScheduler.runCurrent() // crash #1

    // Retry after 1s backoff — call #2 succeeds
    testScheduler.advanceTimeBy(1001)
    testScheduler.runCurrent()

    val data = coordinator.widgetData(widget.instanceId).value
    assertThat(data.hasData()).isTrue()
    assertThat(coordinator.activeBindings()).containsKey(widget.instanceId)

    coordinator.destroy()
    initJob.cancel()
  }

  // -- Data staleness detection test (F3.11) ---

  @Test
  fun `data staleness marks widget DataStale when no emission within threshold`() = runTest {
    var fakeTime = 0L
    val provider = ContinuousProvider()
    // Schema with 5s staleness threshold
    val renderer = TestWidgetRenderer(
      typeId = "essentials:clock",
      compatibleSnapshots = setOf(TestSnapshot::class),
    )
    val widgetRegistry = WidgetRegistryImpl(setOf(renderer), logger)
    val providerRegistry =
      DataProviderRegistryImpl(setOf(provider), FakeEntitlementManager(), logger)

    // The provider has schema.stalenessThresholdMs=3000L by default in ContinuousProvider.
    // The coordinator calls binder.minStalenessThresholdMs() which returns the provider's threshold.
    val coordinator = createCoordinator(
      widgetRegistry,
      providerRegistry,
      timeProvider = { fakeTime },
    )

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val widget = testWidget(typeId = "essentials:clock")
    coordinator.bind(widget)
    testScheduler.runCurrent()

    // Emit data — this records lastEmissionTimestamp via timeProvider
    fakeTime = 1000L
    provider.emissionFlow.emit(TestSnapshot(timestamp = 1000L))
    testScheduler.runCurrent()

    // Data received — status should be Ready (EMPTY)
    val statusAfterEmission = coordinator.widgetStatus(widget.instanceId).value
    assertThat(statusAfterEmission.overlayState).isNotInstanceOf(WidgetRenderState.DataStale::class.java)

    // Advance virtual time past the staleness threshold (3000ms from ContinuousProvider schema).
    // The watchdog delay is 3000ms. After the delay, it checks: timeProvider() - lastTs > 3000.
    // Set fakeTime to 5000 so 5000 - 1000 = 4000 > 3000.
    fakeTime = 5000L
    testScheduler.advanceTimeBy(3001)
    testScheduler.runCurrent()

    // Status should now be DataStale
    val statusAfterStale = coordinator.widgetStatus(widget.instanceId).value
    assertThat(statusAfterStale.overlayState).isEqualTo(WidgetRenderState.DataStale)

    coordinator.destroy()
    initJob.cancel()
  }

  @Test
  fun `staleness watchdog resets when new data arrives before threshold`() = runTest {
    var fakeTime = 0L
    val provider = ContinuousProvider()
    val renderer = TestWidgetRenderer(
      typeId = "essentials:clock",
      compatibleSnapshots = setOf(TestSnapshot::class),
    )
    val widgetRegistry = WidgetRegistryImpl(setOf(renderer), logger)
    val providerRegistry =
      DataProviderRegistryImpl(setOf(provider), FakeEntitlementManager(), logger)

    val coordinator = createCoordinator(
      widgetRegistry,
      providerRegistry,
      timeProvider = { fakeTime },
    )

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val widget = testWidget(typeId = "essentials:clock")
    coordinator.bind(widget)
    testScheduler.runCurrent()

    // First emission at time=1000
    fakeTime = 1000L
    provider.emissionFlow.emit(TestSnapshot(timestamp = 1000L))
    testScheduler.runCurrent()

    // Advance 2s (still within 3s threshold)
    fakeTime = 3000L
    testScheduler.advanceTimeBy(2000)
    testScheduler.runCurrent()

    // Emit again before staleness — updates lastEmissionTimestamp
    fakeTime = 3000L
    provider.emissionFlow.emit(TestSnapshot(timestamp = 3000L))
    testScheduler.runCurrent()

    // Now advance another 3001ms (total 5001ms from bind, but only 3001ms from last emission)
    // The watchdog fires at 3000ms intervals. After this advance, it checks:
    // timeProvider()=3500 - lastTs=3000 = 500 < 3000 → NOT stale
    fakeTime = 3500L
    testScheduler.advanceTimeBy(1001)
    testScheduler.runCurrent()

    val status = coordinator.widgetStatus(widget.instanceId).value
    assertThat(status.overlayState).isNotInstanceOf(WidgetRenderState.DataStale::class.java)

    coordinator.destroy()
    initJob.cancel()
  }
}
