package app.dqxn.android.feature.dashboard.safety

import android.content.SharedPreferences
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages safe mode detection based on widget crash frequency.
 *
 * Safe mode triggers when [CRASH_THRESHOLD] or more crashes (across ALL widgets, not per-widget)
 * occur within a [WINDOW_MS] rolling window. Uses [SharedPreferences] with [commit] for
 * process-death safety -- same pattern as CrashRecovery in `:app`.
 *
 * This class owns its own crash timestamps rather than depending on CrashRecovery from `:app`,
 * because `:feature:dashboard` cannot depend on `:app`. The crash counting is widget-specific
 * (tracks widgetId and typeId) while CrashRecovery tracks process-level crashes.
 */
public class SafeModeManager
@Inject
constructor(
  private val prefs: SharedPreferences,
  private val logger: DqxnLogger,
  /** Clock source for timestamps. Defaults to [System.currentTimeMillis]. Inject a fake for tests. */
  public val clock: () -> Long = { System.currentTimeMillis() },
) {

  private val _safeModeActive: MutableStateFlow<Boolean> = MutableStateFlow(false)

  /** Whether safe mode is currently active. Observed by NotificationCoordinator for CRITICAL banner. */
  public val safeModeActive: StateFlow<Boolean> = _safeModeActive.asStateFlow()

  init {
    // Check persisted state on construction
    _safeModeActive.value = checkSafeMode()
  }

  /**
   * Report a widget crash. Records the timestamp and checks if the rolling window threshold is
   * exceeded. Cross-widget counting: 4 different widgets each crashing once = safe mode triggered.
   */
  public fun reportCrash(widgetId: String, typeId: String) {
    val now = clock()
    val timestamps = readTimestamps().filter { now - it < WINDOW_MS }.plus(now)

    prefs
      .edit()
      .putString(KEY_CRASH_TIMESTAMPS, timestamps.joinToString(","))
      .commit() // commit(), NOT apply() -- must survive process death

    val isSafe = timestamps.size >= CRASH_THRESHOLD
    _safeModeActive.value = isSafe

    if (isSafe) {
      logger.warn(TAG) {
        "Safe mode activated: ${timestamps.size} crashes in ${WINDOW_MS / 1000}s " +
          "(last: widget=$widgetId, type=$typeId)"
      }
    } else {
      logger.info(TAG) {
        "Widget crash recorded: widget=$widgetId, type=$typeId " +
          "(${timestamps.size}/$CRASH_THRESHOLD in window)"
      }
    }
  }

  /** Clear crash history and deactivate safe mode. */
  public fun resetSafeMode() {
    prefs.edit().remove(KEY_CRASH_TIMESTAMPS).commit()
    _safeModeActive.value = false
    logger.info(TAG) { "Safe mode reset" }
  }

  private fun checkSafeMode(): Boolean {
    val now = clock()
    return readTimestamps().count { now - it < WINDOW_MS } >= CRASH_THRESHOLD
  }

  private fun readTimestamps(): List<Long> =
    prefs
      .getString(KEY_CRASH_TIMESTAMPS, null)
      ?.split(",")
      ?.mapNotNull { it.toLongOrNull() }
      ?: emptyList()

  internal companion object {
    val TAG: LogTag = LogTag("SafeMode")
    internal const val KEY_CRASH_TIMESTAMPS: String = "widget_crash_timestamps"
    internal const val WINDOW_MS: Long = 60_000L
    internal const val CRASH_THRESHOLD: Int = 4
  }
}
