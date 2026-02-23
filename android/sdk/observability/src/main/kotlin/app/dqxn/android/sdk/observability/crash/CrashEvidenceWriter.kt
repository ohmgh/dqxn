package app.dqxn.android.sdk.observability.crash

import android.content.SharedPreferences

/**
 * [Thread.UncaughtExceptionHandler] that persists crash evidence to [SharedPreferences]
 * synchronously via [SharedPreferences.Editor.commit] (NOT apply) so the data survives
 * the process termination that follows.
 *
 * Used by safe mode to detect repeated widget crashes on next launch.
 */
public class CrashEvidenceWriter(
  private val prefs: SharedPreferences,
) : Thread.UncaughtExceptionHandler {

  private val delegate: Thread.UncaughtExceptionHandler? =
    Thread.getDefaultUncaughtExceptionHandler()

  override fun uncaughtException(t: Thread, e: Throwable) {
    try {
      prefs
        .edit()
        .putString(KEY_TYPE_ID, extractWidgetTypeId(e))
        .putString(KEY_EXCEPTION, "${e::class.simpleName}: ${e.message}")
        .putString(KEY_STACK_TOP5, e.stackTrace.take(5).joinToString("\n"))
        .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        .commit() // MUST be commit(), NOT apply() -- process is dying
    } catch (_: Exception) {
      // Must never interfere with crash handling.
    } finally {
      delegate?.uncaughtException(t, e)
    }
  }

  /**
   * Reads the last crash evidence, returning null if none exists.
   * Used on launch by safe-mode recovery.
   */
  public fun readLastCrash(): CrashEvidence? {
    val exception = prefs.getString(KEY_EXCEPTION, null) ?: return null
    return CrashEvidence(
      typeId = prefs.getString(KEY_TYPE_ID, null),
      exception = exception,
      stackTop5 = prefs.getString(KEY_STACK_TOP5, "") ?: "",
      timestamp = prefs.getLong(KEY_TIMESTAMP, 0L),
    )
  }

  /** Clears stored crash evidence after successful recovery. */
  public fun clearEvidence() {
    prefs
      .edit()
      .remove(KEY_TYPE_ID)
      .remove(KEY_EXCEPTION)
      .remove(KEY_STACK_TOP5)
      .remove(KEY_TIMESTAMP)
      .apply()
  }

  public companion object {
    internal const val KEY_TYPE_ID = "last_crash_type_id"
    internal const val KEY_EXCEPTION = "last_crash_exception"
    internal const val KEY_STACK_TOP5 = "last_crash_stack_top5"
    internal const val KEY_TIMESTAMP = "last_crash_timestamp"

    /** Pattern to extract widget typeId from exception messages. */
    private val WIDGET_TYPE_ID_PATTERN: Regex =
      Regex("\\[([a-z][a-z0-9]*:[a-z][a-z0-9-]*)\\]")

    /**
     * Walk the exception cause chain looking for a widget typeId pattern in messages.
     */
    internal fun extractWidgetTypeId(e: Throwable): String? {
      var current: Throwable? = e
      while (current != null) {
        val message = current.message ?: ""
        val match = WIDGET_TYPE_ID_PATTERN.find(message)
        if (match != null) {
          return match.groupValues[1]
        }
        current = current.cause
      }
      return null
    }
  }
}

/** Crash evidence persisted across process death for safe-mode recovery. */
public data class CrashEvidence(
  val typeId: String?,
  val exception: String,
  val stackTop5: String,
  val timestamp: Long,
)
