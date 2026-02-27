package app.dqxn.android.feature.dashboard.gesture

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class DashboardHapticsTest {

  private val mockVibrator =
    mockk<Vibrator>(relaxed = true) { every { hasVibrator() } returns true }

  private val mockVibratorManager =
    mockk<VibratorManager> { every { defaultVibrator } returns mockVibrator }

  private val mockContext =
    mockk<Context>(relaxed = true) {
      every { getSystemService(Context.VIBRATOR_MANAGER_SERVICE) } returns mockVibratorManager
      @Suppress("DEPRECATION")
      every { getSystemService(Context.VIBRATOR_SERVICE) } returns mockVibrator
    }

  private val mockReducedMotionHelper =
    mockk<ReducedMotionHelper> { every { isReducedMotion } returns false }

  private val stubEffect = mockk<VibrationEffect>()

  private lateinit var haptics: DashboardHaptics

  @BeforeEach
  fun setUp() {
    mockkStatic(VibrationEffect::class)
    every { VibrationEffect.createPredefined(any()) } returns stubEffect

    haptics = DashboardHaptics(context = mockContext, reducedMotionHelper = mockReducedMotionHelper)
  }

  @AfterEach
  fun tearDown() {
    unmockkStatic(VibrationEffect::class)
  }

  @Nested
  @DisplayName("haptic methods")
  inner class HapticMethods {

    @Test
    fun `editModeEnter invokes vibrator with HEAVY_CLICK`() {
      haptics.editModeEnter()

      verify { VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK) }
      verify { mockVibrator.vibrate(stubEffect) }
    }

    @Test
    fun `editModeExit invokes vibrator`() {
      haptics.editModeExit()

      verify { VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK) }
      verify { mockVibrator.vibrate(stubEffect) }
    }

    @Test
    fun `snapToGrid invokes vibrator`() {
      haptics.snapToGrid()

      verify { VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) }
      verify { mockVibrator.vibrate(stubEffect) }
    }

    @Test
    fun `boundaryHit invokes vibrator with distinct effect`() {
      haptics.boundaryHit()

      verify { VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK) }
      verify { mockVibrator.vibrate(stubEffect) }
    }

    @Test
    fun `all 8 methods callable without crash`() {
      haptics.editModeEnter()
      haptics.editModeExit()
      haptics.dragStart()
      haptics.snapToGrid()
      haptics.boundaryHit()
      haptics.resizeStart()
      haptics.widgetFocus()
      haptics.buttonPress()

      verify(exactly = 8) { mockVibrator.vibrate(stubEffect) }
    }
  }

  @Nested
  @DisplayName("reduced motion")
  inner class ReducedMotion {

    @Test
    fun `editModeEnter uses lighter effect when reduced motion active`() {
      every { mockReducedMotionHelper.isReducedMotion } returns true

      haptics.editModeEnter()

      // Reduced motion: uses EFFECT_CLICK instead of EFFECT_HEAVY_CLICK
      verify { VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK) }
      verify { mockVibrator.vibrate(stubEffect) }
    }
  }

  @Nested
  @DisplayName("no vibrator")
  inner class NoVibrator {

    @Test
    fun `methods do not crash when hasVibrator returns false`() {
      every { mockVibrator.hasVibrator() } returns false

      haptics.editModeEnter()
      haptics.editModeExit()
      haptics.dragStart()
      haptics.snapToGrid()
      haptics.boundaryHit()
      haptics.resizeStart()
      haptics.widgetFocus()
      haptics.buttonPress()

      verify(exactly = 0) { mockVibrator.vibrate(any<VibrationEffect>()) }
    }
  }
}
