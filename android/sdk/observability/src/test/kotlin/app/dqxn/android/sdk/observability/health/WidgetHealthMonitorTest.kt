package app.dqxn.android.sdk.observability.health

import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetHealthMonitorTest {

  @Test
  fun `reportData updates lastDataTimestamp and sets ACTIVE`() = runTest {
    var fakeTime = 1000L
    val monitor =
      WidgetHealthMonitor(
        logger = NoOpLogger,
        scope = backgroundScope,
        timeProvider = { fakeTime },
      )

    monitor.reportData("widget-1", "essentials:clock")

    val statuses = monitor.allStatuses()
    assertThat(statuses["widget-1"]?.status).isEqualTo(WidgetHealthMonitor.Status.ACTIVE)
    assertThat(statuses["widget-1"]?.lastDataTimestamp).isEqualTo(1000L)
  }

  @Test
  fun `stale data detected after threshold`() = runTest {
    var fakeTime = 1000L
    val monitor =
      WidgetHealthMonitor(
        logger = NoOpLogger,
        scope = backgroundScope,
        stalenessThresholdMs = 10_000,
        timeProvider = { fakeTime },
      )

    monitor.reportData("widget-1", "essentials:clock")

    // Advance past staleness threshold
    fakeTime = 12_000L
    monitor.checkLiveness()

    assertThat(monitor.allStatuses()["widget-1"]?.status)
      .isEqualTo(WidgetHealthMonitor.Status.STALE_DATA)
  }

  @Test
  fun `reportDraw resets stalled render`() = runTest {
    var fakeTime = 1000L
    val monitor =
      WidgetHealthMonitor(
        logger = NoOpLogger,
        scope = backgroundScope,
        timeProvider = { fakeTime },
      )

    monitor.reportDraw("widget-1", "essentials:clock")

    val statuses = monitor.allStatuses()
    assertThat(statuses["widget-1"]?.status).isEqualTo(WidgetHealthMonitor.Status.ACTIVE)
    assertThat(statuses["widget-1"]?.lastDrawTimestamp).isEqualTo(1000L)
  }

  @Test
  fun `stalled render detected`() = runTest {
    var fakeTime = 1000L
    val monitor =
      WidgetHealthMonitor(
        logger = NoOpLogger,
        scope = backgroundScope,
        stalenessThresholdMs = 10_000,
        timeProvider = { fakeTime },
      )

    // Report both data and draw at t=1000
    monitor.reportData("widget-1", "essentials:clock")
    monitor.reportDraw("widget-1", "essentials:clock")

    // Advance: data still fresh but draw stale
    fakeTime = 5_000L
    monitor.reportData("widget-1", "essentials:clock") // Keep data fresh

    fakeTime = 12_000L // Now past draw staleness (last draw at 1000)
    monitor.checkLiveness()

    assertThat(monitor.allStatuses()["widget-1"]?.status)
      .isEqualTo(WidgetHealthMonitor.Status.STALLED_RENDER)
  }

  @Test
  fun `reportCrash sets CRASHED status`() = runTest {
    val monitor =
      WidgetHealthMonitor(
        logger = NoOpLogger,
        scope = backgroundScope,
      )

    monitor.reportData("widget-1", "essentials:clock")
    monitor.reportCrash("widget-1", "essentials:clock")

    assertThat(monitor.allStatuses()["widget-1"]?.status)
      .isEqualTo(WidgetHealthMonitor.Status.CRASHED)
  }

  @Test
  fun `allStatuses returns all tracked widgets`() = runTest {
    val monitor =
      WidgetHealthMonitor(
        logger = NoOpLogger,
        scope = backgroundScope,
      )

    monitor.reportData("widget-1", "essentials:clock")
    monitor.reportData("widget-2", "essentials:speedometer")
    monitor.reportData("widget-3", "essentials:compass")

    assertThat(monitor.allStatuses()).hasSize(3)
  }
}
