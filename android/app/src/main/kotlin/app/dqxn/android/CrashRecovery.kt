package app.dqxn.android

import android.content.SharedPreferences

/**
 * Crash timestamp tracking for safe-mode detection.
 *
 * Uses [SharedPreferences] (not DataStore) because crash recording must be synchronous --
 * [recordCrash] is called from an [Thread.UncaughtExceptionHandler] where the process is about to
 * die. [SharedPreferences.Editor.commit] is used instead of apply() to guarantee persistence before
 * process termination.
 *
 * Safe mode triggers when [THRESHOLD] or more crashes occur within [WINDOW_MS] (60 seconds).
 */
public class CrashRecovery(
  private val prefs: SharedPreferences,
) {

  /** Records the current timestamp as a crash event. Uses [commit] to survive process death. */
  public fun recordCrash() {
    val now = System.currentTimeMillis()
    val timestamps = readTimestamps().filter { now - it < WINDOW_MS }.plus(now)
    prefs
      .edit()
      .putString(KEY_TIMESTAMPS, timestamps.joinToString(","))
      .commit() // commit(), NOT apply() -- must survive process death
  }

  /** Returns true if [THRESHOLD] or more crashes occurred within the last [WINDOW_MS]. */
  public fun isInSafeMode(): Boolean {
    val now = System.currentTimeMillis()
    return readTimestamps().count { now - it < WINDOW_MS } >= THRESHOLD
  }

  /** Clears all crash history. Used after successful recovery. */
  public fun clearCrashHistory() {
    prefs.edit().remove(KEY_TIMESTAMPS).apply()
  }

  private fun readTimestamps(): List<Long> =
    prefs.getString(KEY_TIMESTAMPS, null)?.split(",")?.mapNotNull { it.toLongOrNull() }
      ?: emptyList()

  internal companion object {
    internal const val KEY_TIMESTAMPS: String = "crash_timestamps"
    internal const val WINDOW_MS: Long = 60_000L
    internal const val THRESHOLD: Int = 4
  }
}
