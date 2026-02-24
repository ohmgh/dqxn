package app.dqxn.android.feature.dashboard.gesture

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Detects the system reduced motion setting by reading `animator_duration_scale`.
 *
 * Per NF39: when reduced motion is active, wiggle animation is disabled and spring animations are
 * replaced with snap/instant transitions. Glow effects remain unaffected.
 *
 * Note: haptics are not motion -- [DashboardHaptics] still vibrates when reduced motion is active
 * but may use lighter intensity variants.
 */
public class ReducedMotionHelper
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
) {

  /**
   * Whether the system has reduced motion enabled (animator_duration_scale == 0).
   *
   * Read on each access to reflect current system state without requiring a listener.
   */
  public val isReducedMotion: Boolean
    get() =
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      ) == 0f
}
