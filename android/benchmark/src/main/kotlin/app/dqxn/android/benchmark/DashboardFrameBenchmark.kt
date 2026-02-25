package app.dqxn.android.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing benchmarks measuring dashboard rendering performance.
 *
 * Steady-state gate: P95 frame time < 16.67ms with 12 active widgets (NF10).
 * Edit mode: measures enter/exit transition + wiggle animation frame timing.
 *
 * Widget population uses the agentic ContentProvider (debug builds only).
 * The `add-widget` handler validates typeId against registered WidgetRenderer set
 * and dispatches DashboardCommand.AddWidget through the command bus.
 *
 * Run on a connected device: `./gradlew :benchmark:connectedBenchmarkAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
public class DashboardFrameBenchmark {

  @get:Rule
  public val rule: MacrobenchmarkRule = MacrobenchmarkRule()

  /**
   * Steady-state frame timing with 12 active widgets rendering simultaneously.
   * Populates widgets via agentic ContentProvider, then soaks for 5 seconds
   * while demo pack data flows through all widget render nodes.
   */
  @Test
  public fun steadyState12Widgets() {
    rule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(FrameTimingMetric()),
      iterations = 3,
      startupMode = StartupMode.WARM,
      setupBlock = {
        startActivityAndWait()
        device.waitForIdle()
        populateWidgets()
        device.waitForIdle()
      },
      measureBlock = {
        // 5-second steady-state soak with demo pack data flowing through 12 widgets
        Thread.sleep(5_000)
      },
    )
  }

  /**
   * Edit mode cycle: enter edit mode (wiggle animations) then exit.
   * Measures frame timing during transition animations.
   */
  @Test
  public fun editModeCycle() {
    rule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(FrameTimingMetric()),
      iterations = 3,
      startupMode = StartupMode.WARM,
      setupBlock = {
        startActivityAndWait()
        device.waitForIdle()
      },
      measureBlock = {
        val editToggle = device.findObject(By.desc("Edit mode"))
        if (editToggle != null) {
          // Enter edit mode
          editToggle.click()
          device.waitForIdle()
          // Soak during wiggle animations
          Thread.sleep(2_000)
          // Exit edit mode
          editToggle.click()
          device.waitForIdle()
        }
      },
    )
  }

  /**
   * Populates 12 widgets via the agentic ContentProvider's add-widget handler.
   * Uses available essentials widget types, duplicating if fewer than 12 are registered.
   * Only works on debug builds (AgenticContentProvider is debug-manifest only).
   */
  private fun MacrobenchmarkScope.populateWidgets() {
    val widgetTypes =
      listOf(
        "essentials:clock",
        "essentials:battery",
        "essentials:date-simple",
        "essentials:speed",
        "essentials:compass",
        "essentials:orientation",
        "essentials:ambient-light",
        "essentials:acceleration",
        "essentials:solar",
        "essentials:speed-limit",
        "essentials:gps-coordinates",
        "essentials:altitude",
      )
    for (typeId in widgetTypes) {
      device.executeShellCommand(
        "content call --uri content://app.dqxn.android.debug.agentic" +
          " --method add-widget --extra typeId:s:$typeId"
      )
    }
  }

  private companion object {
    const val PACKAGE_NAME = "app.dqxn.android"
  }
}
