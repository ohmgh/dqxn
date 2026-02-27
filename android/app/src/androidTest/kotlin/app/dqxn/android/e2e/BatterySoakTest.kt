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
 * Screen-on battery soak test measuring drain with 12 active widgets over 30 minutes.
 *
 * NF11 requirement: < 5% battery drain per hour with 12 widgets active on screen.
 *
 * Uses `dumpsys battery` for level measurement and the agentic framework to add widgets. Meaningful
 * battery delta requires a physical device -- emulator battery is virtual and level won't change,
 * causing the assertion to trivially pass. CI should run on physical device when available.
 *
 * Per project policy: connected device tests are automated tests, not manual tests.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BatterySoakTest {

  @get:Rule val hiltRule = HiltAndroidRule(this)

  private val client = AgenticTestClient()
  private lateinit var device: UiDevice

  @Before
  fun setup() {
    hiltRule.inject()
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  }

  /**
   * Soak test: launch app with 12 widgets, run screen-on for 30 minutes, measure battery delta.
   * Asserts < 5% hourly drain (NF11).
   */
  @Test
  fun screenOnBatterySoak() {
    // Wait for app ready
    client.assertReady()

    // Add 12 widgets via agentic commands -- mix of essentials widget types
    val widgetTypes =
      listOf(
        "essentials:clock",
        "essentials:clock",
        "essentials:speedometer",
        "essentials:speedometer",
        "essentials:battery",
        "essentials:battery",
        "essentials:compass",
        "essentials:compass",
        "essentials:date-simple",
        "essentials:date-simple",
        "essentials:ambient-light",
        "essentials:ambient-light",
      )

    for (typeId in widgetTypes) {
      client.send("add-widget", mapOf("typeId" to typeId))
    }

    // Wait for all widgets to reach ACTIVE status via dump-health polling
    client.awaitCondition(
      command = "dump-health",
      jsonPath = "widgets",
      condition = { widgets -> widgets.size >= WIDGET_COUNT },
      timeoutMs = 30_000L,
      pollIntervalMs = 1_000L,
    )

    // Record initial battery level
    val initialLevel = readBatteryLevel()

    // Reset batterystats for clean measurement window
    device.executeShellCommand("dumpsys batterystats --reset")

    // Soak for 30 minutes with screen on
    Thread.sleep(SOAK_DURATION_MS)

    // Record final battery level
    val finalLevel = readBatteryLevel()

    // Calculate drain and extrapolate to 1 hour
    val drainPercent = initialLevel - finalLevel
    val hourlyDrain = drainPercent * 2 // 30 min -> 1 hour extrapolation

    assertWithMessage(
        "NF11: Screen-on battery drain with $WIDGET_COUNT widgets must be < ${MAX_HOURLY_DRAIN}%/hr. " +
          "Measured: initial=$initialLevel%, final=$finalLevel%, 30min drain=${drainPercent}%, " +
          "extrapolated hourly=${hourlyDrain}%. " +
          "NOTE: Emulator battery is virtual -- meaningful results require physical device."
      )
      .that(hourlyDrain)
      .isLessThan(MAX_HOURLY_DRAIN)
  }

  /**
   * Reads the current battery level percentage from `dumpsys battery`. Parses the "level: XX" line
   * from the output.
   */
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
    const val WIDGET_COUNT: Int = 12
    const val SOAK_DURATION_MS: Long = 30L * 60L * 1000L // 30 minutes
    const val MAX_HOURLY_DRAIN: Int = 5
  }
}
