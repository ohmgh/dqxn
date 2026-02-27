package app.dqxn.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline profile generator covering critical user journeys.
 *
 * Generates AOT compilation hints for ART to optimize startup, dashboard rendering, edit mode
 * transitions, and widget picker overlay navigation.
 *
 * Run on a connected device: `./gradlew :baselineprofile:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
public class BaselineProfileGenerator {

  @get:Rule public val rule: BaselineProfileRule = BaselineProfileRule()

  /** Minimal startup critical path. ART compiles the startup trace. */
  @Test
  public fun startup() {
    rule.collect(packageName = PACKAGE_NAME) {
      pressHome()
      startActivityAndWait()
    }
  }

  /** Dashboard steady-state rendering path with active widgets and data binding. */
  @Test
  public fun dashboardInteraction() {
    rule.collect(packageName = PACKAGE_NAME) {
      startActivityAndWait()
      device.waitForIdle()
      // Allow data binding to complete so rendering paths are captured
      Thread.sleep(3_000)
    }
  }

  /** Edit mode enter/exit + gesture handling codepath. */
  @Test
  public fun editMode() {
    rule.collect(packageName = PACKAGE_NAME) {
      startActivityAndWait()
      device.waitForIdle()

      val editToggle = device.findObject(By.desc("Edit mode"))
      if (editToggle != null) {
        editToggle.click()
        device.waitForIdle()
        Thread.sleep(1_000)
        editToggle.click()
        device.waitForIdle()
      }
    }
  }

  /** Overlay navigation + widget picker rendering path. */
  @Test
  public fun widgetPicker() {
    rule.collect(packageName = PACKAGE_NAME) {
      startActivityAndWait()
      device.waitForIdle()

      // Enter edit mode first
      val editToggle = device.findObject(By.desc("Edit mode"))
      if (editToggle != null) {
        editToggle.click()
        device.waitForIdle()

        // Open widget picker
        val addWidget = device.findObject(By.desc("Add widget"))
        if (addWidget != null) {
          addWidget.click()
          device.waitForIdle()
          Thread.sleep(2_000)
        }
      }
    }
  }

  private companion object {
    const val PACKAGE_NAME = "app.dqxn.android"
  }
}
