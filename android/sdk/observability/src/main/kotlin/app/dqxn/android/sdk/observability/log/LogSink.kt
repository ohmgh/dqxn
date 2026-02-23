package app.dqxn.android.sdk.observability.log

/** Destination for log entries. Implementations must be thread-safe. */
public fun interface LogSink {
  public fun write(entry: LogEntry)
}
