package app.dqxn.android.core.agentic.chaos

import app.cash.turbine.test
import app.dqxn.android.sdk.contracts.fault.ProviderFault
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class ChaosProviderInterceptorTest {

  private data class TestSnapshot(override val timestamp: Long, val value: Int) : DataSnapshot

  private class FakeProvider(
    override val sourceId: String = "test-provider",
  ) : DataProvider<TestSnapshot> {
    override val snapshotType: KClass<TestSnapshot> = TestSnapshot::class
    override val displayName: String = "Test"
    override val description: String = "Test provider"
    override val dataType: String = "test"
    override val priority: ProviderPriority = ProviderPriority.SIMULATED
    override val schema: DataSchema = DataSchema(emptyList(), stalenessThresholdMs = 5000L)
    override val setupSchema: List<SetupPageDefinition> = emptyList()
    override val subscriberTimeout: Duration = 10.seconds
    override val firstEmissionTimeout: Duration = 5.seconds
    override val isAvailable: Boolean = true
    override val connectionState: Flow<Boolean> = MutableStateFlow(true)
    override val connectionErrorDescription: Flow<String?> = MutableStateFlow(null)
    override val requiredAnyEntitlement: Set<String>? = null

    override fun provideState(): Flow<TestSnapshot> = flow {
      repeat(10) { i ->
        emit(TestSnapshot(timestamp = System.nanoTime(), value = i))
        delay(100)
      }
    }
  }

  private val interceptor = ChaosProviderInterceptor()
  private val provider = FakeProvider()

  @Test
  fun `intercept returns upstream unchanged when no fault active`() = runTest {
    val upstream = flow {
      emit(TestSnapshot(timestamp = 1L, value = 42))
      emit(TestSnapshot(timestamp = 2L, value = 43))
    }

    interceptor.intercept(provider, upstream).test {
      assertThat(awaitItem().value).isEqualTo(42)
      assertThat(awaitItem().value).isEqualTo(43)
      awaitComplete()
    }
  }

  @Test
  fun `injectFault Kill terminates flow`() = runTest {
    interceptor.injectFault("test-provider", ProviderFault.Kill)

    val upstream = flow {
      emit(TestSnapshot(timestamp = 1L, value = 1))
      emit(TestSnapshot(timestamp = 2L, value = 2))
    }

    interceptor.intercept(provider, upstream).test {
      awaitComplete()
    }
  }

  @Test
  fun `injectFault Delay adds latency to emissions`() = runTest {
    interceptor.injectFault("test-provider", ProviderFault.Delay(delayMs = 500))

    val upstream = flow { emit(TestSnapshot(timestamp = 1L, value = 1)) }

    val collected = mutableListOf<TestSnapshot>()
    val job = backgroundScope.launch {
      interceptor.intercept(provider, upstream).collect { collected.add(it) }
    }

    runCurrent()
    assertThat(collected).isEmpty()

    advanceTimeBy(501)
    runCurrent()
    assertThat(collected).hasSize(1)
    assertThat(collected[0].value).isEqualTo(1)

    job.cancel()
  }

  @Test
  fun `injectFault Error throws on collection`() = runTest {
    val exception = RuntimeException("chaos error")
    interceptor.injectFault("test-provider", ProviderFault.Error(exception))

    val upstream = flow { emit(TestSnapshot(timestamp = 1L, value = 1)) }

    interceptor.intercept(provider, upstream).test {
      val error = awaitError()
      assertThat(error).isInstanceOf(RuntimeException::class.java)
      assertThat(error).hasMessageThat().isEqualTo("chaos error")
    }
  }

  @Test
  fun `injectFault ErrorOnNext throws on first emission`() = runTest {
    val exception = RuntimeException("first emission error")
    interceptor.injectFault("test-provider", ProviderFault.ErrorOnNext(exception))

    val upstream = flow {
      emit(TestSnapshot(timestamp = 1L, value = 1))
      emit(TestSnapshot(timestamp = 2L, value = 2))
    }

    interceptor.intercept(provider, upstream).test {
      val error = awaitError()
      assertThat(error).hasMessageThat().isEqualTo("first emission error")
    }
  }

  @Test
  fun `injectFault Stall never emits`() = runTest {
    interceptor.injectFault("test-provider", ProviderFault.Stall)

    val upstream = flow { emit(TestSnapshot(timestamp = 1L, value = 1)) }

    val collected = mutableListOf<TestSnapshot>()
    val job = backgroundScope.launch {
      interceptor.intercept(provider, upstream).collect { collected.add(it) }
    }

    advanceTimeBy(5000)
    runCurrent()
    assertThat(collected).isEmpty()

    job.cancel()
  }

  @Test
  fun `injectFault Flap alternates emission windows`() = runTest {
    interceptor.injectFault(
      "test-provider",
      ProviderFault.Flap(onMillis = 1000, offMillis = 1000),
    )

    val upstream = flow {
      emit(TestSnapshot(timestamp = 1L, value = 1)) // at t=0 (on window)
      delay(500)
      emit(TestSnapshot(timestamp = 2L, value = 2)) // at t=500 (still on)
      delay(600)
      emit(TestSnapshot(timestamp = 3L, value = 3)) // at t=1100 (off window)
      delay(1000)
      emit(TestSnapshot(timestamp = 4L, value = 4)) // at t=2100 (on window again)
    }

    val collected = mutableListOf<TestSnapshot>()
    val job = backgroundScope.launch {
      interceptor.intercept(provider, upstream).collect { collected.add(it) }
    }

    // At t=0, first emission during on window
    runCurrent()
    assertThat(collected.map { it.value }).containsExactly(1)

    // At t=500, second emission still in on window
    advanceTimeBy(500)
    runCurrent()
    assertThat(collected.map { it.value }).containsExactly(1, 2)

    // At t=1100, third emission in off window -- should be dropped
    advanceTimeBy(600)
    runCurrent()
    assertThat(collected.map { it.value }).containsExactly(1, 2)

    // At t=2100, fourth emission back in on window
    advanceTimeBy(1000)
    runCurrent()
    assertThat(collected.map { it.value }).containsExactly(1, 2, 4)

    job.cancel()
  }

  @Test
  fun `injectFault Corrupt transforms snapshot data`() = runTest {
    interceptor.injectFault(
      "test-provider",
      ProviderFault.Corrupt { snapshot ->
        val original = snapshot as TestSnapshot
        original.copy(value = original.value * 100)
      },
    )

    val upstream = flow {
      emit(TestSnapshot(timestamp = 1L, value = 5))
      emit(TestSnapshot(timestamp = 2L, value = 3))
    }

    val result = interceptor.intercept(provider, upstream).toList()
    assertThat(result.map { it.value }).containsExactly(500, 300).inOrder()
  }

  @Test
  fun `clearFault removes fault for specific provider`() = runTest {
    interceptor.injectFault("test-provider", ProviderFault.Kill)
    interceptor.clearFault("test-provider")

    val upstream = flow { emit(TestSnapshot(timestamp = 1L, value = 42)) }

    interceptor.intercept(provider, upstream).test {
      assertThat(awaitItem().value).isEqualTo(42)
      awaitComplete()
    }
  }

  @Test
  fun `clearAll removes all active faults`() = runTest {
    interceptor.injectFault("provider-a", ProviderFault.Kill)
    interceptor.injectFault("provider-b", ProviderFault.Stall)
    interceptor.clearAll()

    assertThat(interceptor.getActiveFaults()).isEmpty()
  }

  @Test
  fun `getActiveFaults returns current fault map`() {
    val fault1 = ProviderFault.Kill
    val fault2 = ProviderFault.Stall
    interceptor.injectFault("provider-a", fault1)
    interceptor.injectFault("provider-b", fault2)

    val faults = interceptor.getActiveFaults()
    assertThat(faults).hasSize(2)
    assertThat(faults["provider-a"]).isEqualTo(ProviderFault.Kill)
    assertThat(faults["provider-b"]).isEqualTo(ProviderFault.Stall)
  }
}
