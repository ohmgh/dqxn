package app.dqxn.android.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Full user journey E2E test exercising the entire dashboard lifecycle: launch -> dashboard load ->
 * widget health -> add widget -> verify rendering -> theme state -> data providers -> settings
 * presence -> observability metrics.
 *
 * Uses [AgenticTestClient] from Plan 13-05 to send agentic commands and assert responses via the
 * response-file protocol.
 *
 * Requires a device/emulator. Execution deferred to CI per project policy ("connected device !=
 * manual test").
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FullJourneyE2ETest {

  @get:Rule val hiltRule = HiltAndroidRule(this)

  private val client = AgenticTestClient()

  @Before
  fun setup() {
    hiltRule.inject()
  }

  /**
   * Full 11-step user journey covering the complete dashboard lifecycle.
   *
   * Steps:
   * 1. Launch: App responds to ping
   * 2. Dashboard loads: dump-layout returns profile structure
   * 3. Widget health baseline: dump-health returns current state
   * 4. Add widget: add essentials:clock-digital widget
   * 5. Widget health verification: poll dump-health until widget is ACTIVE
   * 6. Widget semantics: query-semantics finds widget node by test tag
   * 7. Theme state: list-themes returns registered theme providers
   * 8. Data providers: list-providers confirms data layer is active
   * 9. Agentic surface: list-commands returns all available commands
   * 10. Settings presence: query-semantics finds settings_button test tag
   * 11. Observability: get-metrics returns performance data
   */
  @Test
  fun fullJourneyE2E() {
    // Step 1 - Launch: Wait for app readiness via ping
    client.assertReady()

    // Step 2 - Dashboard loads: dump-layout returns profile structure
    val layout = client.send("dump-layout")
    assertThat(layout["status"]?.jsonPrimitive?.content).isNotEqualTo("error")
    // Layout response has profiles key (even if empty on fresh install)
    assertThat(layout.containsKey("profiles") || layout.containsKey("data")).isTrue()

    // Step 3 - Widget health baseline: dump-health works (may be empty on fresh install)
    val healthBaseline = client.send("dump-health")
    val baselineCount = healthBaseline["widgetCount"]?.jsonPrimitive?.int ?: 0
    // Baseline count is valid (>= 0)
    assertThat(baselineCount).isAtLeast(0)

    // Step 4 - Add widget: add an essentials:clock-digital widget
    val addResponse =
      client.send(
        "add-widget",
        mapOf("typeId" to "essentials:clock-digital"),
      )
    assertThat(addResponse["status"]?.jsonPrimitive?.content).isEqualTo("ok")
    val widgetId =
      addResponse["data"]?.jsonObject?.get("widgetId")?.jsonPrimitive?.content
        ?: addResponse["widgetId"]?.jsonPrimitive?.content
    assertThat(widgetId).isNotNull()

    // Step 5 - Widget health: poll dump-health until widget count increases
    val healthAfterAdd =
      client.awaitCondition(
        command = "dump-health",
        jsonPath = "widgets",
        condition = { widgets ->
          widgets.any { widget ->
            val obj = widget.jsonObject
            obj["typeId"]?.jsonPrimitive?.content == "essentials:clock-digital"
          }
        },
        timeoutMs = 5_000L,
      )
    val countAfterAdd = healthAfterAdd["widgetCount"]?.jsonPrimitive?.int ?: 0
    assertThat(countAfterAdd).isGreaterThan(baselineCount)

    // Step 6 - Widget semantics: verify widget has a semantics node
    // Widget test tags follow pattern: widget_{instanceId}
    client.assertWidgetRendered("essentials:clock-digital", timeoutMs = 5_000L)

    // Step 7 - Theme state: list-themes returns registered theme providers
    val themes = client.send("list-themes")
    val providerCount = themes["providerCount"]?.jsonPrimitive?.int ?: 0
    // At least one theme provider should be registered (essentials + themes packs)
    assertThat(providerCount).isGreaterThan(0)
    val themesList = themes["themes"]?.jsonArray
    assertThat(themesList).isNotNull()
    assertThat(themesList!!.size).isGreaterThan(0)
    // Verify each theme has required fields
    val firstTheme = themesList[0].jsonObject
    assertThat(firstTheme["themeId"]?.jsonPrimitive?.content).isNotEmpty()
    assertThat(firstTheme["displayName"]?.jsonPrimitive?.content).isNotEmpty()

    // Step 8 - Data providers: list-providers confirms data layer
    val providers = client.send("list-providers")
    // Response should not be an error
    assertThat(providers["status"]?.jsonPrimitive?.content).isNotEqualTo("error")

    // Step 9 - Agentic surface: list-commands returns all registered commands
    val commands = client.send("list-commands")
    val commandData = commands["data"]?.jsonArray
    assertThat(commandData).isNotNull()
    val commandNames = commandData!!.map { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" }
    // Core commands must be present
    assertThat(commandNames).contains("ping")
    assertThat(commandNames).contains("add-widget")
    assertThat(commandNames).contains("dump-health")
    assertThat(commandNames).contains("dump-layout")
    assertThat(commandNames).contains("list-themes")
    assertThat(commandNames).contains("dump-semantics")

    // Step 10 - Settings: verify settings_button exists in semantics tree
    val settingsQuery =
      client.send(
        "query-semantics",
        mapOf("testTag" to "settings_button"),
      )
    val settingsMatchCount = settingsQuery["matchCount"]?.jsonPrimitive?.int ?: 0
    // Settings button should be in the semantics tree
    assertThat(settingsMatchCount).isGreaterThan(0)

    // Step 11 - Observability: get-metrics returns performance snapshot
    val metrics = client.send("get-metrics")
    assertThat(metrics["status"]?.jsonPrimitive?.content).isNotEqualTo("error")
  }
}
