package app.dqxn.android.feature.dashboard.safety

import app.dqxn.android.feature.dashboard.test.FakeSharedPreferences
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class SafeModeManagerTest {

  private lateinit var fakePrefs: FakeSharedPreferences
  private lateinit var manager: SafeModeManager
  private var currentTime = 1_000_000L
  private val testClock: () -> Long = { currentTime }

  @BeforeEach
  fun setUp() {
    fakePrefs = FakeSharedPreferences()
    manager = SafeModeManager(
      prefs = fakePrefs,
      logger = NoOpLogger,
      clock = testClock,
    )
  }

  @Test
  fun `no crashes means safe mode inactive`() {
    assertThat(manager.safeModeActive.value).isFalse()
  }

  @Test
  fun `3 crashes in 60s does not trigger safe mode`() {
    manager.reportCrash("widget-1", "essentials:clock")
    currentTime += 1_000
    manager.reportCrash("widget-2", "essentials:battery")
    currentTime += 1_000
    manager.reportCrash("widget-3", "essentials:compass")

    assertThat(manager.safeModeActive.value).isFalse()
  }

  @Test
  fun `4 crashes in 60s triggers safe mode`() {
    manager.reportCrash("widget-1", "essentials:clock")
    currentTime += 1_000
    manager.reportCrash("widget-2", "essentials:battery")
    currentTime += 1_000
    manager.reportCrash("widget-3", "essentials:compass")
    currentTime += 1_000
    manager.reportCrash("widget-4", "essentials:speed")

    assertThat(manager.safeModeActive.value).isTrue()
  }

  @Test
  fun `4 crashes from 4 different widgets triggers safe mode`() {
    // Cross-widget counting: each widget crashes once
    manager.reportCrash("w1", "essentials:clock")
    manager.reportCrash("w2", "essentials:battery")
    manager.reportCrash("w3", "essentials:compass")
    manager.reportCrash("w4", "essentials:speed")

    assertThat(manager.safeModeActive.value).isTrue()
  }

  @Test
  fun `crashes older than 60s are expired`() {
    manager.reportCrash("w1", "essentials:clock")
    currentTime += 20_000
    manager.reportCrash("w2", "essentials:battery")
    currentTime += 20_000
    manager.reportCrash("w3", "essentials:compass")

    // First crash is now 40s old -- still in window
    assertThat(manager.safeModeActive.value).isFalse()

    // Advance past 60s from the first crash
    currentTime += 25_000 // 65s from first crash
    manager.reportCrash("w4", "essentials:speed")

    // First crash expired (65s old), only 3 remain in window: w2 (45s), w3 (25s), w4 (0s)
    assertThat(manager.safeModeActive.value).isFalse()
  }

  @Test
  fun `resetSafeMode clears state`() {
    // Trigger safe mode
    manager.reportCrash("w1", "t1")
    manager.reportCrash("w2", "t2")
    manager.reportCrash("w3", "t3")
    manager.reportCrash("w4", "t4")
    assertThat(manager.safeModeActive.value).isTrue()

    manager.resetSafeMode()
    assertThat(manager.safeModeActive.value).isFalse()
  }

  @Test
  fun `safe mode survives process death via SharedPreferences`() {
    // Record 4 crashes
    manager.reportCrash("w1", "t1")
    manager.reportCrash("w2", "t2")
    manager.reportCrash("w3", "t3")
    manager.reportCrash("w4", "t4")

    // Simulate process death by creating a new SafeModeManager with the same prefs and clock
    val restoredManager = SafeModeManager(
      prefs = fakePrefs,
      logger = NoOpLogger,
      clock = testClock,
    )

    // Should still be in safe mode after "restart"
    assertThat(restoredManager.safeModeActive.value).isTrue()
  }
}
