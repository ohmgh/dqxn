package app.dqxn.android.sdk.observability.log

/**
 * Decorator wrapping any [LogSink] in try/catch so observability never crashes the app.
 * Every sink in the pipeline should be wrapped with this.
 */
public class SafeLogSink(private val delegate: LogSink) : LogSink {
  override fun write(entry: LogEntry) {
    try {
      delegate.write(entry)
    } catch (_: Exception) {
      // Observability must never crash the host app.
    }
  }
}
