package app.dqxn.android.sdk.contracts.testing

import app.dqxn.android.sdk.contracts.widget.WidgetRenderer

/**
 * Concrete jqwik property test validating that [WidgetRendererPropertyTest] abstract base runs with
 * [TestWidgetRenderer].
 */
public class TestWidgetRendererPropertyTest : WidgetRendererPropertyTest() {

  override fun createRenderer(): WidgetRenderer = TestWidgetRenderer()
}
