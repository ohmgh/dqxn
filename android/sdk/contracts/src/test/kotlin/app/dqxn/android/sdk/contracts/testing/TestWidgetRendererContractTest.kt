package app.dqxn.android.sdk.contracts.testing

import app.dqxn.android.sdk.contracts.provider.UnitSnapshot
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer

/**
 * Concrete contract test validating that [WidgetRendererContractTest] abstract base runs with
 * [TestWidgetRenderer]. All 14 inherited tests execute here — if any fail, the contract test
 * infrastructure itself is broken.
 */
public class TestWidgetRendererContractTest : WidgetRendererContractTest() {

  override fun createRenderer(): WidgetRenderer = TestWidgetRenderer()

  override fun createTestWidgetData(): WidgetData =
    testWidgetData(UnitSnapshot::class to UnitSnapshot(timestamp = System.currentTimeMillis()))
}
