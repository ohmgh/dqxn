package app.dqxn.android.sdk.contracts.testing

import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.UnitSnapshot
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Concrete contract test validating that [DataProviderContractTest] abstract base runs with
 * [TestDataProvider]. All 12 inherited tests execute here — if any fail, the contract test
 * infrastructure itself is broken.
 */
public class TestDataProviderContractTest : DataProviderContractTest() {

  override fun createProvider(): DataProvider<*> {
    val testFlow = flow {
      while (coroutineContext.isActive) {
        emit(UnitSnapshot(timestamp = System.currentTimeMillis()))
        kotlinx.coroutines.delay(100)
      }
    }
    return TestDataProvider(
      snapshotType = UnitSnapshot::class,
      baseFlow = testFlow,
    )
  }
}
