package app.dqxn.android.pack.demo.providers

import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest

class DemoSpeedProviderContractTest : DataProviderContractTest() {

  override fun createProvider(): DataProvider<*> = DemoSpeedProvider()
}
