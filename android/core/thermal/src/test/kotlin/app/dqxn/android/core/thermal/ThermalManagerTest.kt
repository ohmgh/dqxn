package app.dqxn.android.core.thermal

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThermalManagerTest {

  // -------------------------------------------------------------------------
  // ThermalLevel mapping from PowerManager status constants
  // -------------------------------------------------------------------------

  @Test
  fun `mapThermalStatus maps NONE to NORMAL`() {
    assertThat(ThermalManager.mapThermalStatus(android.os.PowerManager.THERMAL_STATUS_NONE))
      .isEqualTo(ThermalLevel.NORMAL)
  }

  @Test
  fun `mapThermalStatus maps LIGHT to NORMAL`() {
    assertThat(ThermalManager.mapThermalStatus(android.os.PowerManager.THERMAL_STATUS_LIGHT))
      .isEqualTo(ThermalLevel.NORMAL)
  }

  @Test
  fun `mapThermalStatus maps MODERATE to WARM`() {
    assertThat(ThermalManager.mapThermalStatus(android.os.PowerManager.THERMAL_STATUS_MODERATE))
      .isEqualTo(ThermalLevel.WARM)
  }

  @Test
  fun `mapThermalStatus maps SEVERE to DEGRADED`() {
    assertThat(ThermalManager.mapThermalStatus(android.os.PowerManager.THERMAL_STATUS_SEVERE))
      .isEqualTo(ThermalLevel.DEGRADED)
  }

  @Test
  fun `mapThermalStatus maps EMERGENCY to CRITICAL`() {
    assertThat(ThermalManager.mapThermalStatus(android.os.PowerManager.THERMAL_STATUS_EMERGENCY))
      .isEqualTo(ThermalLevel.CRITICAL)
  }

  @Test
  fun `mapThermalStatus maps SHUTDOWN to CRITICAL`() {
    assertThat(ThermalManager.mapThermalStatus(android.os.PowerManager.THERMAL_STATUS_SHUTDOWN))
      .isEqualTo(ThermalLevel.CRITICAL)
  }

  @Test
  fun `mapThermalStatus maps unknown status to CRITICAL`() {
    assertThat(ThermalManager.mapThermalStatus(999)).isEqualTo(ThermalLevel.CRITICAL)
  }

  // -------------------------------------------------------------------------
  // RenderConfig derivation from ThermalLevel
  // -------------------------------------------------------------------------

  @Test
  fun `NORMAL produces 60fps with glow enabled`() {
    val config = ThermalLevel.NORMAL.toRenderConfig()
    assertThat(config.targetFps).isEqualTo(60f)
    assertThat(config.glowEnabled).isTrue()
    assertThat(config.useGradientFallback).isFalse()
  }

  @Test
  fun `WARM produces 45fps with glow enabled`() {
    val config = ThermalLevel.WARM.toRenderConfig()
    assertThat(config.targetFps).isEqualTo(45f)
    assertThat(config.glowEnabled).isTrue()
    assertThat(config.useGradientFallback).isFalse()
  }

  @Test
  fun `DEGRADED produces 30fps with gradient fallback`() {
    val config = ThermalLevel.DEGRADED.toRenderConfig()
    assertThat(config.targetFps).isEqualTo(30f)
    assertThat(config.glowEnabled).isFalse()
    assertThat(config.useGradientFallback).isTrue()
  }

  @Test
  fun `CRITICAL produces 24fps with gradient fallback`() {
    val config = ThermalLevel.CRITICAL.toRenderConfig()
    assertThat(config.targetFps).isEqualTo(24f)
    assertThat(config.glowEnabled).isFalse()
    assertThat(config.useGradientFallback).isTrue()
  }

  @Test
  fun `RenderConfig DEFAULT matches NORMAL level`() {
    assertThat(RenderConfig.DEFAULT).isEqualTo(ThermalLevel.NORMAL.toRenderConfig())
  }

  // -------------------------------------------------------------------------
  // FakeThermalManager transitions with Turbine
  // -------------------------------------------------------------------------

  @Test
  fun `FakeThermalManager default state is NORMAL`() = runTest {
    val fake = FakeThermalManager()
    assertThat(fake.thermalLevel.value).isEqualTo(ThermalLevel.NORMAL)
    assertThat(fake.renderConfig.value).isEqualTo(RenderConfig.DEFAULT)
  }

  @Test
  fun `FakeThermalManager transitions update thermalLevel flow`() = runTest {
    val fake = FakeThermalManager()

    fake.thermalLevel.test {
      assertThat(awaitItem()).isEqualTo(ThermalLevel.NORMAL)

      fake.setLevel(ThermalLevel.WARM)
      assertThat(awaitItem()).isEqualTo(ThermalLevel.WARM)

      fake.setLevel(ThermalLevel.DEGRADED)
      assertThat(awaitItem()).isEqualTo(ThermalLevel.DEGRADED)

      fake.setLevel(ThermalLevel.CRITICAL)
      assertThat(awaitItem()).isEqualTo(ThermalLevel.CRITICAL)

      fake.setLevel(ThermalLevel.NORMAL)
      assertThat(awaitItem()).isEqualTo(ThermalLevel.NORMAL)
    }
  }

  @Test
  fun `FakeThermalManager renderConfig updates synchronously without scope`() {
    val fake = FakeThermalManager()

    fake.setLevel(ThermalLevel.DEGRADED)
    assertThat(fake.renderConfig.value.targetFps).isEqualTo(30f)
    assertThat(fake.renderConfig.value.glowEnabled).isFalse()

    fake.setLevel(ThermalLevel.CRITICAL)
    assertThat(fake.renderConfig.value.targetFps).isEqualTo(24f)
  }

  @Test
  fun `FakeThermalManager renderConfig updates with scope via flow derivation`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val testScope = TestScope(testDispatcher)
    val fake = FakeThermalManager(scope = testScope.backgroundScope)

    fake.renderConfig.test {
      assertThat(awaitItem()).isEqualTo(RenderConfig.DEFAULT)

      fake.setLevel(ThermalLevel.WARM)
      testScope.testScheduler.advanceUntilIdle()
      assertThat(awaitItem().targetFps).isEqualTo(45f)

      fake.setLevel(ThermalLevel.CRITICAL)
      testScope.testScheduler.advanceUntilIdle()
      assertThat(awaitItem().targetFps).isEqualTo(24f)
    }
  }

  // -------------------------------------------------------------------------
  // ThermalLevel enum coverage
  // -------------------------------------------------------------------------

  @Test
  fun `all ThermalLevel values produce distinct RenderConfig`() {
    val configs = ThermalLevel.entries.map { it.toRenderConfig() }
    assertThat(configs).hasSize(4)
    // FPS values must be strictly decreasing
    val fpsList = configs.map { it.targetFps }
    assertThat(fpsList).containsExactly(60f, 45f, 30f, 24f).inOrder()
  }
}
