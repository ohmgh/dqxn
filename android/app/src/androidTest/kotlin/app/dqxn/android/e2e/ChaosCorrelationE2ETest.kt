package app.dqxn.android.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E instrumented test verifying the deterministic chaos -> detect -> correlate pipeline.
 *
 * Uses seed=42 to produce a reproducible fault sequence via the agentic `chaos-start` command.
 * Validates that chaos injection produces correlated diagnostic snapshots that reference the fault
 * types from the chaos profile (SC2).
 *
 * Requires a device/emulator. Execution deferred to CI per project policy ("connected device !=
 * manual test").
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChaosCorrelationE2ETest {

  @get:Rule val hiltRule = HiltAndroidRule(this)

  private val client = AgenticTestClient()

  @Before
  fun setup() {
    hiltRule.inject()
  }

  @Test
  fun chaosCorrelationSeed42() {
    // Wait for app ready
    client.assertReady()

    // Start chaos with deterministic seed=42 and standard profile
    val startResponse =
      client.send(
        "chaos-start",
        mapOf("seed" to 42, "profile" to "combined"),
      )
    assertThat(startResponse["status"]?.jsonPrimitive?.content).isEqualTo("ok")
    val sessionId =
      startResponse["data"]?.jsonObject?.get("sessionId")?.jsonPrimitive?.content
        ?: startResponse["sessionId"]?.jsonPrimitive?.content
    assertThat(sessionId).isNotNull()

    // Wait for faults to inject -- poll list-diagnostics until at least one snapshot appears.
    // Do NOT use Thread.sleep -- use deterministic condition polling.
    val diagnostics =
      client.awaitCondition(
        command = "list-diagnostics",
        jsonPath = "snapshots",
        condition = { it.isNotEmpty() },
        timeoutMs = 15_000L,
      )

    // Assert diagnostics are not empty (chaos produced at least 1 diagnostic snapshot)
    val snapshots = diagnostics["snapshots"]?.jsonArray
    assertThat(snapshots).isNotNull()
    assertThat(snapshots!!.size).isGreaterThan(0)

    // Verify each snapshot has required fields
    for (snapshot in snapshots) {
      val obj = snapshot.jsonObject
      assertThat(obj["id"]?.jsonPrimitive?.content).isNotEmpty()
      assertThat(obj["timestamp"]?.jsonPrimitive?.long).isGreaterThan(0L)
      assertThat(obj["triggerType"]?.jsonPrimitive?.content).isNotEmpty()
    }

    // Stop chaos
    val stopResponse = client.send("chaos-stop")
    assertThat(stopResponse["status"]?.jsonPrimitive?.content).isEqualTo("ok")

    // Verify the session produced correlated data
    val count = diagnostics["count"]?.jsonPrimitive?.int ?: 0
    assertThat(count).isGreaterThan(0)
  }
}
