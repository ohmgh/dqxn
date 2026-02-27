package app.dqxn.android.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup timing benchmarks measuring cold and warm start performance.
 *
 * Cold startup gate: P50 < 1.5s (NF1). Warm startup: trend tracking for P50 regression detection.
 *
 * Run on a connected device: `./gradlew :benchmark:connectedBenchmarkAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
public class StartupBenchmark {

  @get:Rule public val rule: MacrobenchmarkRule = MacrobenchmarkRule()

  /**
   * Cold startup: process killed before each iteration. Measures full process init + Activity
   * creation + first frame.
   */
  @Test
  public fun coldStartup() {
    rule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(StartupTimingMetric()),
      iterations = 5,
      startupMode = StartupMode.COLD,
      setupBlock = { pressHome() },
      measureBlock = { startActivityAndWait() },
    )
  }

  /**
   * Warm startup: process alive, Activity recreated. Lower variance than cold; useful for P50 trend
   * tracking.
   */
  @Test
  public fun warmStartup() {
    rule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(StartupTimingMetric()),
      iterations = 3,
      startupMode = StartupMode.WARM,
      setupBlock = { pressHome() },
      measureBlock = { startActivityAndWait() },
    )
  }

  private companion object {
    const val PACKAGE_NAME = "app.dqxn.android"
  }
}
