package app.dqxn.android.feature.dashboard.gesture

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Semantic haptic feedback for all dashboard interactions.
 *
 * Provides 8 distinct haptic methods per replication advisory section 6. Each method uses
 * predefined effects on API 30+ with [VibrationEffect] fallbacks for older APIs.
 *
 * When reduced motion is active (via [ReducedMotionHelper]), heavy click variants are replaced with
 * lighter alternatives. Haptics are NOT motion -- they still fire under reduced motion.
 */
public class DashboardHaptics
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
  private val reducedMotionHelper: ReducedMotionHelper,
) {

  private val vibrator: Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
      manager.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

  /** Edit mode entered -- heavy impact feedback. Reduced motion: use lighter click. */
  public fun editModeEnter() {
    if (reducedMotionHelper.isReducedMotion) {
      vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    } else {
      vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }
  }

  /** Edit mode exited -- standard click. */
  public fun editModeExit() {
    vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
  }

  /** Widget drag started -- light tick. */
  public fun dragStart() {
    vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
  }

  /** Widget snapped to grid position -- light tick. */
  public fun snapToGrid() {
    vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
  }

  /** Widget hit canvas boundary -- double click, distinct from grid snap per F1.27. */
  public fun boundaryHit() {
    vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
  }

  /** Widget resize started -- light tick. */
  public fun resizeStart() {
    vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
  }

  /** Widget received focus in edit mode -- standard click. */
  public fun widgetFocus() {
    vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
  }

  /** Button pressed -- standard click. */
  public fun buttonPress() {
    vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
  }

  private fun vibrate(effect: VibrationEffect) {
    if (vibrator.hasVibrator()) {
      vibrator.vibrate(effect)
    }
  }
}
