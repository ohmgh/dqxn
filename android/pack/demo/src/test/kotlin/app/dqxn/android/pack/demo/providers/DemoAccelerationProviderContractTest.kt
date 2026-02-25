package app.dqxn.android.pack.demo.providers

import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest

class DemoAccelerationProviderContractTest : DataProviderContractTest() {

  override fun createProvider(): DataProvider<*> = DemoAccelerationProvider()
}
