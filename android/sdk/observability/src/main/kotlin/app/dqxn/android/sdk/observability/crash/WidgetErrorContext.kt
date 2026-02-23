package app.dqxn.android.sdk.observability.crash

/**
 * Extended error context for widget crashes, carrying crash count and stack summary
 * for safe-mode evaluation (>3 crashes in 60s triggers safe mode).
 */
public data class WidgetErrorContext(
  val typeId: String,
  val widgetId: String,
  val crashCount: Int,
  val stackTraceSummary: String,
  val lastCrashTimestamp: Long,
)
