package app.dqxn.android.sdk.observability.log

/**
 * Log severity levels. Ordinal comparison is used for level filtering: a log entry passes if
 * `entry.level.ordinal >= minimumLevel.ordinal`.
 */
public enum class LogLevel {
  VERBOSE,
  DEBUG,
  INFO,
  WARN,
  ERROR,
}
