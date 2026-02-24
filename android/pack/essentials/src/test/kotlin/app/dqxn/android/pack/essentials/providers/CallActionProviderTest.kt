package app.dqxn.android.pack.essentials.providers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.dqxn.android.sdk.contracts.provider.ActionableProvider
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.UnitSnapshot
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetAction
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CallActionProviderTest : DataProviderContractTest() {

  private val mockIntent = mockk<Intent>(relaxed = true)
  private val packageManager =
    mockk<PackageManager>(relaxed = true) {
      every { getLaunchIntentForPackage("com.example.app") } returns mockIntent
      every { getLaunchIntentForPackage("com.nonexistent.app") } returns null
    }
  private val context =
    mockk<Context>(relaxed = true) {
      every { getPackageManager() } returns packageManager
    }

  override fun createProvider(): DataProvider<*> = CallActionProvider(context)

  // --- CallAction-specific: ActionableProvider contract ---

  @Test
  fun `implements ActionableProvider`() {
    val provider = CallActionProvider(context)
    assertThat(provider).isInstanceOf(ActionableProvider::class.java)
  }

  @Test
  fun `provideState emits UnitSnapshot`() = runTest {
    val provider = CallActionProvider(context)
    val snapshot = provider.provideState().first()
    assertThat(snapshot).isInstanceOf(UnitSnapshot::class.java)
    assertThat(snapshot.timestamp).isGreaterThan(0L)
  }

  @Test
  fun `onAction with valid package launches activity`() {
    val provider = CallActionProvider(context)
    val action = WidgetAction.Custom(
      actionId = "launch",
      params = mapOf("packageName" to "com.example.app"),
    )

    provider.onAction(action)

    val intentSlot = slot<Intent>()
    verify { context.startActivity(capture(intentSlot)) }
    verify { mockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
  }

  @Test
  fun `onAction with unknown package does not crash`() {
    val provider = CallActionProvider(context)
    val action = WidgetAction.Custom(
      actionId = "launch",
      params = mapOf("packageName" to "com.nonexistent.app"),
    )

    // Should not throw
    provider.onAction(action)

    // No startActivity call since getLaunchIntentForPackage returned null
    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  fun `onAction with Tap action does nothing`() {
    val provider = CallActionProvider(context)
    val action = WidgetAction.Tap(widgetId = "widget-123")

    provider.onAction(action)

    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  fun `onAction with missing packageName param does nothing`() {
    val provider = CallActionProvider(context)
    val action = WidgetAction.Custom(
      actionId = "launch",
      params = emptyMap(),
    )

    provider.onAction(action)

    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  fun `connectionState is always true`() = runTest {
    val provider = CallActionProvider(context)
    assertThat(provider.connectionState.first()).isTrue()
  }
}
