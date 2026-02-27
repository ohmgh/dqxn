package app.dqxn.android.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Background battery soak test measuring drain when the app is backgrounded (NF37).
 *
 * NF37 requirement: near-zero background drain (< 1% per hour) without active BLE.
 *
 * Also verifies that sensor registrations are properly cleaned up when the app is backgrounded, via
 * `dumpsys sensorservice` inspection. This validates that `callbackFlow` `awaitClose` blocks
 * properly unregister sensors when the lifecycle stops collecting.
 *
 * Meaningful battery delta requires a physical device -- emulator battery is virtual. Per project
 * policy: connected device tests are automated tests, not manual tests.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackgroundBatterySoakTest {

  @get:Rule val hiltRule = HiltAndroidRule(this)

  private val client = AgenticTestClient()
  private lateinit var device: UiDevice

  @Before
  fun setup() {
    hiltRule.inject()
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  }

  /**
   * Soak test: launch app, background it, wait 15 minutes, measure battery delta. Asserts < 1%
   * hourly drain (NF37).
   */
  @Test
  fun backgroundBatterySoak() {
    // Wait for app ready
    client.assertReady()

    // Press HOME to background the app
    device.pressHome()

    // Give the system time to process the background transition
    Thread.sleep(BACKGROUND_SETTLE_MS)

    // Record initial battery level
    val initialLevel = readBatteryLevel()

    // Reset batterystats for clean measurement window
    device.executeShellCommand("dumpsys batterystats --reset")

    // Soak for 15 minutes in background
    Thread.sleep(BACKGROUND_SOAK_DURATION_MS)

    // Record final battery level
    val finalLevel = readBatteryLevel()

    // Calculate drain and extrapolate to 1 hour
    val drainPercent = initialLevel - finalLevel
    val hourlyDrain = drainPercent * 4 // 15 min -> 1 hour extrapolation

    assertWithMessage(
        "NF37: Background battery drain must be < ${MAX_HOURLY_DRAIN}%/hr. " +
          "Measured: initial=$initialLevel%, final=$finalLevel%, 15min drain=${drainPercent}%, " +
          "extrapolated hourly=${hourlyDrain}%. " +
          "NOTE: Emulator battery is virtual -- meaningful results require physical device."
      )
      .that(hourlyDrain)
      .isLessThan(MAX_HOURLY_DRAIN)
  }

  /**
   * Verifies that no active sensor registrations remain from the app when backgrounded.
   *
   * After pressing HOME and waiting for the lifecycle to stop, `dumpsys sensorservice` should show
   * no active sensor connections from the app's package. This validates that
   * `callbackFlow.awaitClose` properly unregisters all sensors.
   */
  @Test
  fun sensorUnregistrationOnBackground() {
    // Wait for app ready and add widgets that use sensors
    client.assertReady()

    val sensorWidgets =
      listOf(
        "essentials:compass",
        "essentials:speedometer",
        "essentials:ambient-light",
      )
    for (typeId in sensorWidgets) {
      client.send("add-widget", mapOf("typeId" to typeId))
    }

    // Wait for widgets to be active
    client.awaitCondition(
      command = "dump-health",
      jsonPath = "widgets",
      condition = { widgets -> widgets.size >= sensorWidgets.size },
      timeoutMs = 15_000L,
      pollIntervalMs = 1_000L,
    )

    // Background the app
    device.pressHome()

    // Wait for lifecycle to fully stop and sensors to unregister
    Thread.sleep(SENSOR_UNREGISTER_WAIT_MS)

    // Check dumpsys sensorservice for active registrations from our package
    val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    val sensorOutput = device.executeShellCommand("dumpsys sensorservice")

    // Parse for active connections from our package.
    // sensorservice output format includes "Connection " lines with package names.
    val activeConnections =
      sensorOutput.lines().filter { it.contains(packageName) && it.contains("active") }

    assertWithMessage(
        "No active sensor registrations should remain after backgrounding. " +
          "Found ${activeConnections.size} active connections from $packageName. " +
          "This indicates awaitClose is not properly unregistering sensors. " +
          "Lines: ${activeConnections.take(5).joinToString("; ")}"
      )
      .that(activeConnections)
      .isEmpty()
  }

  /** Reads the current battery level percentage from `dumpsys battery`. */
  private fun readBatteryLevel(): Int {
    val output = device.executeShellCommand("dumpsys battery")
    val levelLine = output.lines().firstOrNull { it.trim().startsWith("level:") }
    checkNotNull(levelLine) { "Could not find 'level:' in dumpsys battery output: $output" }
    val level = levelLine.trim().removePrefix("level:").trim().toIntOrNull()
    checkNotNull(level) { "Could not parse battery level from: $levelLine" }
    assertThat(level).isIn(0..100)
    return level
  }

  private companion object {
    const val BACKGROUND_SETTLE_MS: Long = 3_000L
    const val BACKGROUND_SOAK_DURATION_MS: Long = 15L * 60L * 1000L // 15 minutes
    const val SENSOR_UNREGISTER_WAIT_MS: Long = 5_000L
    const val MAX_HOURLY_DRAIN: Int = 1
  }
}
