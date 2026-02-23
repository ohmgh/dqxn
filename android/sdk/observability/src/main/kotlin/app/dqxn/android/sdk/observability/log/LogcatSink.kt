package app.dqxn.android.sdk.observability.log

import android.util.Log

/**
 * Routes log entries to [android.util.Log] using the appropriate level methods. Active in debug
 * builds only -- returns early if [isDebugBuild] is false.
 */
public class LogcatSink(private val isDebugBuild: Boolean) : LogSink {

  override fun write(entry: LogEntry) {
    if (!isDebugBuild) return

    val tag = entry.tag.value
    val message =
      if (entry.throwable != null) {
        "${entry.message}\n${entry.throwable.stackTraceToString()}"
      } else {
        entry.message
      }

    when (entry.level) {
      LogLevel.VERBOSE -> Log.v(tag, message)
      LogLevel.DEBUG -> Log.d(tag, message)
      LogLevel.INFO -> Log.i(tag, message)
      LogLevel.WARN -> Log.w(tag, message)
      LogLevel.ERROR -> Log.e(tag, message)
    }
  }
}
