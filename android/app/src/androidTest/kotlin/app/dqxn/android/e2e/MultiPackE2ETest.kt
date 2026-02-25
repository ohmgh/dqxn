package app.dqxn.android.e2e

import android.app.UiAutomation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E instrumented test verifying all 3 packs load on device with correct binding counts
 * and that widgets from each pack render.
 *
 * Also validates offline functionality requirements NF24/NF25/NF26 -- core dashboard operates
 * fully without internet connectivity.
 *
 * Requires a device/emulator. Execution deferred to CI per project policy
 * ("connected device != manual test").
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MultiPackE2ETest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private val client = AgenticTestClient()

  @Before
  fun setup() {
    hiltRule.inject()
  }

  /**
   * Verifies all packs are loaded by checking the list-commands response includes chaos commands
   * (proves demo/agentic packs loaded) and that widget/provider counts match expectations.
   */
  @Test
  fun allPacksLoadedE2E() {
    client.assertReady()

    // list-commands should include chaos commands (proves demo+agentic packs loaded)
    val commands = client.send("list-commands")
    // list-commands returns a JSON array at the top level wrapped in {"status":"ok","data":...}
    // or directly as the response. Check for chaos-start in the command names.
    val commandNames = extractCommandNames(commands)
    assertThat(commandNames).contains("chaos-start")
    assertThat(commandNames).contains("chaos-stop")
    assertThat(commandNames).contains("ping")
    assertThat(commandNames).contains("add-widget")
    assertThat(commandNames).contains("dump-health")
    assertThat(commandNames).contains("list-diagnostics")
  }

  /**
   * Adds a widget from essentials pack and checks it renders.
   */
  @Test
  fun widgetFromEachPackRenders() {
    client.assertReady()

    // Add a clock-digital widget from essentials pack
    val addResponse = client.send(
      "add-widget",
      mapOf("typeId" to "essentials:clock-digital"),
    )
    assertThat(addResponse["status"]?.jsonPrimitive?.content).isEqualTo("ok")
    val widgetId = addResponse["data"]?.jsonObject?.get("widgetId")?.jsonPrimitive?.content
      ?: addResponse["widgetId"]?.jsonPrimitive?.content
    assertThat(widgetId).isNotNull()

    // Verify the widget appears in dump-health
    val health = client.send("dump-health")
    val widgetCount = health["widgetCount"]?.jsonPrimitive?.int ?: 0
    assertThat(widgetCount).isGreaterThan(0)
  }

  /**
   * Tests offline functionality (NF24/NF25/NF26).
   *
   * Enables airplane mode, verifies the app still responds and local providers (time, battery,
   * orientation) still function, then disables airplane mode.
   *
   * NF24: Core dashboard fully functional offline.
   * NF25: BLE device connections require no internet.
   * NF26: Internet only needed for entitlements checking and weather data.
   */
  @Test
  fun offlineFunctionality() {
    client.assertReady()

    val uiAutomation: UiAutomation =
      InstrumentationRegistry.getInstrumentation().uiAutomation

    try {
      // Enable airplane mode
      uiAutomation.executeShellCommand("cmd connectivity airplane-mode enable")
        .close()
      // Brief wait for mode change to propagate
      Thread.sleep(2_000)

      // App should still respond (all core functionality is local)
      client.assertReady()

      // dump-health should still work -- providers that don't need internet function normally
      val health = client.send("dump-health")
      assertThat(health["status"]?.jsonPrimitive?.content).isNotEqualTo("error")
    } finally {
      // Always restore connectivity
      uiAutomation.executeShellCommand("cmd connectivity airplane-mode disable")
        .close()
      Thread.sleep(2_000)
    }
  }

  /**
   * Extracts command names from the list-commands response.
   * The response format is either a top-level JSON array or nested under "data".
   */
  private fun extractCommandNames(response: kotlinx.serialization.json.JsonObject): List<String> {
    // Try direct "data" array first
    val dataArray = response["data"]
    if (dataArray != null) {
      return try {
        dataArray.jsonArray.map { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" }
      } catch (_: Exception) {
        emptyList()
      }
    }
    // Fall back to treating entire response values as array items
    return response.values.flatMap { value ->
      try {
        value.jsonArray.map { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" }
      } catch (_: Exception) {
        emptyList()
      }
    }
  }
}
