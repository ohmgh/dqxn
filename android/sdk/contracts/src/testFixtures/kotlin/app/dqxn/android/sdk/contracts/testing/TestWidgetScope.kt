package app.dqxn.android.sdk.contracts.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope

/** Test-only [CoroutineScope] wrapping [TestScope] for widget-scoped coroutine testing. */
public class TestWidgetScope(
  public val testScope: TestScope = TestScope(),
) : CoroutineScope by testScope
