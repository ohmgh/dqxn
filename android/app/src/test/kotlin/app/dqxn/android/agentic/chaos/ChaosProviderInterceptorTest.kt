package app.dqxn.android.agentic.chaos

import app.cash.turbine.test
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import com.google.common.truth.Truth.assertThat
import dev.agentic.android.chaos.Fault
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
  fun `inject Kill terminates flow`() = runTest {
    interceptor.inject("test-provider", Fault.Kill)

    val upstream = flow {
      emit(TestSnapshot(timestamp = 1L, value = 1))
      emit(TestSnapshot(timestamp = 2L, value = 2))
    }

    interceptor.intercept(provider, upstream).test {
      awaitComplete()
    }
  }

  @Test
  fun `inject Delay adds latency to emissions`() = runTest {
    interceptor.inject("test-provider", Fault.Delay(delayMs = 500))

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
  fun `inject Error throws on collection`() = runTest {
    val exception = RuntimeException("chaos error")
    interceptor.inject("test-provider", Fault.Error(exception))

    val upstream = flow { emit(TestSnapshot(timestamp = 1L, value = 1)) }

    interceptor.intercept(provider, upstream).test {
      val error = awaitError()
      assertThat(error).isInstanceOf(RuntimeException::class.java)
      assertThat(error).hasMessageThat().isEqualTo("chaos error")
    }
  }

  @Test
  fun `inject ErrorOnNext throws on first emission`() = runTest {
    val exception = RuntimeException("first emission error")
    interceptor.inject("test-provider", Fault.ErrorOnNext(exception))

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
  fun `inject Stall never emits`() = runTest {
    interceptor.inject("test-provider", Fault.Stall)

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
  fun `inject Flap alternates emission windows`() = runTest {
    interceptor.inject(
      "test-provider",
      Fault.Flap(onMillis = 1000, offMillis = 1000),
    )

    val upstream = flow {
      emit(TestSnapshot(timestamp = 1L, value = 1))
      delay(500)
      emit(TestSnapshot(timestamp = 2L, value = 2))
      delay(600)
      emit(TestSnapshot(timestamp = 3L, value = 3))
      delay(1000)
      emit(TestSnapshot(timestamp = 4L, value = 4))
    }

    val collected = mutableListOf<TestSnapshot>()
    val job = backgroundScope.launch {
      interceptor.intercept(provider, upstream).collect { collected.add(it) }
    }

    runCurrent()
    assertThat(collected.map { it.value }).containsExactly(1)

    advanceTimeBy(500)
    runCurrent()
    assertThat(collected.map { it.value }).containsExactly(1, 2)

    advanceTimeBy(600)
    runCurrent()
    assertThat(collected.map { it.value }).containsExactly(1, 2)

    advanceTimeBy(1000)
    runCurrent()
    assertThat(collected.map { it.value }).containsExactly(1, 2, 4)

    job.cancel()
  }

  @Test
  fun `inject Corrupt transforms snapshot data`() = runTest {
    interceptor.inject(
      "test-provider",
      Fault.Corrupt { snapshot ->
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
  fun `clear removes fault for specific provider`() = runTest {
    interceptor.inject("test-provider", Fault.Kill)
    interceptor.clear("test-provider")

    val upstream = flow { emit(TestSnapshot(timestamp = 1L, value = 42)) }

    interceptor.intercept(provider, upstream).test {
      assertThat(awaitItem().value).isEqualTo(42)
      awaitComplete()
    }
  }

  @Test
  fun `clearAll removes all active faults`() = runTest {
    interceptor.inject("provider-a", Fault.Kill)
    interceptor.inject("provider-b", Fault.Stall)
    interceptor.clearAll()

    assertThat(interceptor.activeFaults()).isEmpty()
  }

  @Test
  fun `activeFaults returns current fault map`() {
    interceptor.inject("provider-a", Fault.Kill)
    interceptor.inject("provider-b", Fault.Stall)

    val faults = interceptor.activeFaults()
    assertThat(faults).hasSize(2)
    assertThat(faults["provider-a"]).isEqualTo(Fault.Kill)
    assertThat(faults["provider-b"]).isEqualTo(Fault.Stall)
  }
}
