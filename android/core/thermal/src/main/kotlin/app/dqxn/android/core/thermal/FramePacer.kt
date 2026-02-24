package app.dqxn.android.core.thermal

import android.os.Build
import android.view.Window
import android.view.WindowManager
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.debug
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls the display refresh rate to match the target FPS from [RenderConfig].
 *
 * On API 34+, sets [WindowManager.LayoutParams.preferredRefreshRate] on the window. On API 31-33,
 * the same approach is used as a best-effort hint -- the system may or may not honor it.
 *
 * Phase 7 integrates this with the dashboard layer's Compose rendering pipeline. Phase 5 provides
 * the utility and tests.
 */
@Singleton
public class FramePacer @Inject constructor(private val logger: DqxnLogger) {

  private var lastAppliedFps: Float = 0f

  /**
   * Sets the preferred refresh rate on [window] to [targetFps]. No-ops if the rate has not changed
   * since the last call.
   */
  public fun applyFrameRate(window: Window, targetFps: Float) {
    if (targetFps == lastAppliedFps) return

    val params: WindowManager.LayoutParams = window.attributes
    params.preferredRefreshRate = targetFps
    window.attributes = params
    lastAppliedFps = targetFps

    logger.debug(TAG) { "Frame rate set: ${targetFps}fps (API ${Build.VERSION.SDK_INT})" }
  }

  /**
   * Resets the preferred refresh rate to 0 (system default). Call when the dashboard is
   * backgrounded or destroyed.
   */
  public fun reset(window: Window) {
    if (lastAppliedFps == 0f) return

    val params: WindowManager.LayoutParams = window.attributes
    params.preferredRefreshRate = 0f
    window.attributes = params
    lastAppliedFps = 0f

    logger.debug(TAG) { "Frame rate reset to system default" }
  }

  internal companion object {
    val TAG: LogTag = LogTag("FramePacer")
  }
}
