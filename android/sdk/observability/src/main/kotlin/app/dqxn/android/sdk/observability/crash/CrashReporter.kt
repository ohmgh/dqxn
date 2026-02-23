package app.dqxn.android.sdk.observability.crash

/**
 * Interface for crash reporting (F12.1).
 * Implementation in `:core:firebase` (Phase 5) wraps Firebase Crashlytics.
 */
public interface CrashReporter {
  public fun log(message: String)
  public fun logException(e: Throwable)
  public fun setKey(key: String, value: String)
  public fun setUserId(id: String)
}

/** No-op implementation used when crash reporting is not configured. */
public object NoOpCrashReporter : CrashReporter {
  override fun log(message: String) {}
  override fun logException(e: Throwable) {}
  override fun setKey(key: String, value: String) {}
  override fun setUserId(id: String) {}
}
