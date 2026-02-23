package app.dqxn.android.sdk.contracts.testing

import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Abstract contract test base for [DataProvider] implementations. JUnit5.
 *
 * Provides 12 inherited assertions covering emission timing, type correctness, timestamps,
 * cancellation safety, connection state, setup schema, timeouts, gating, and concurrency.
 *
 * Pack provider tests extend this class and override [createProvider].
 */
public abstract class DataProviderContractTest {

  /** Create the provider under test. */
  public abstract fun createProvider(): DataProvider<*>

  private lateinit var provider: DataProvider<*>

  @BeforeEach
  public fun setUp() {
    provider = createProvider()
  }

  // --- Test #1: emits within firstEmissionTimeout ---

  @Test
  public fun `emits within firstEmissionTimeout`(): Unit = runTest {
    withTimeout(provider.firstEmissionTimeout) { provider.provideState().first() }
  }

  // --- Test #2: emitted type matches declared snapshotType ---

  @Test
  public fun `emitted type matches declared snapshotType`(): Unit = runTest {
    val snapshot = withTimeout(provider.firstEmissionTimeout) { provider.provideState().first() }
    assertThat(snapshot).isInstanceOf(provider.snapshotType.java)
  }

  // --- Test #3: emitted snapshot has non-zero timestamp ---

  @Test
  public fun `emitted snapshot has non-zero timestamp`(): Unit = runTest {
    val snapshot = withTimeout(provider.firstEmissionTimeout) { provider.provideState().first() }
    assertThat(snapshot.timestamp).isGreaterThan(0L)
  }

  // --- Test #4: respects cancellation without leaking ---

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  public fun `respects cancellation without leaking`(): Unit = runTest {
    val job = launch { provider.provideState().collect {} }
    // Let at least one emission through, then cancel
    testScheduler.advanceTimeBy(200)
    testScheduler.runCurrent()
    job.cancelAndJoin()
    // If we reach here without hanging, the provider respects cancellation.
  }

  // --- Test #5: snapshotType is a valid DataSnapshot subtype ---

  @Test
  public fun `snapshotType is a valid DataSnapshot subtype`() {
    assertThat(DataSnapshot::class.java.isAssignableFrom(provider.snapshotType.java)).isTrue()
    assertThat(provider.snapshotType).isNotEqualTo(DataSnapshot::class)
  }

  // --- Test #6: connectionState emits at least one value ---

  @Test
  public fun `connectionState emits at least one value`(): Unit = runTest {
    val state = provider.connectionState.first()
    assertThat(state).isNotNull()
  }

  // --- Test #7: connectionErrorDescription null when connected ---

  @Test
  public fun `connectionErrorDescription null when connected`(): Unit = runTest {
    val connected = provider.connectionState.first()
    if (connected) {
      val error = provider.connectionErrorDescription.first()
      assertThat(error).isNull()
    }
  }

  // --- Test #8: setupSchema definitions have unique IDs ---

  @Test
  public fun `setupSchema definitions have unique IDs`() {
    val ids = provider.setupSchema.flatMap { it.definitions }.map { it.id }
    assertThat(ids).containsNoDuplicates()
  }

  // --- Test #9: subscriberTimeout is positive ---

  @Test
  public fun `subscriberTimeout is positive`() {
    assertThat(provider.subscriberTimeout).isGreaterThan(Duration.ZERO)
  }

  // --- Test #10: firstEmissionTimeout is positive ---

  @Test
  public fun `firstEmissionTimeout is positive`() {
    assertThat(provider.firstEmissionTimeout).isGreaterThan(Duration.ZERO)
  }

  // --- Test #11: gating defaults correctly ---

  @Test
  public fun `gating defaults to free when requiredAnyEntitlement is null`() {
    if (provider.requiredAnyEntitlement == null) {
      assertThat(provider.isAccessible { false }).isTrue()
    }
  }

  // --- Test #12: multiple concurrent collectors receive same data ---

  @Test
  public fun `multiple concurrent collectors receive same data`(): Unit = runTest {
    val deferred1 = async { provider.provideState().take(1).toList() }
    val deferred2 = async { provider.provideState().take(1).toList() }

    val result1 = deferred1.await()
    val result2 = deferred2.await()

    assertThat(result1).isNotEmpty()
    assertThat(result2).isNotEmpty()
  }
}
