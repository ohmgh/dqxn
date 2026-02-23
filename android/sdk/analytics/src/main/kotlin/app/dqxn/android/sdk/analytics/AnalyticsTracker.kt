package app.dqxn.android.sdk.analytics

/**
 * Analytics contract interface. Implementation provided by `:core:firebase` in Phase 5.
 *
 * [isEnabled] gates all event tracking. When disabled, [track] and [setUserProperty] are no-ops.
 * Callers are responsible for checking [isEnabled] before constructing expensive event parameters.
 */
public interface AnalyticsTracker {

  /** Returns whether analytics tracking is currently enabled. */
  public fun isEnabled(): Boolean

  /** Enables or disables analytics tracking. */
  public fun setEnabled(enabled: Boolean)

  /** Tracks the given analytics event. No-op when [isEnabled] returns false. */
  public fun track(event: AnalyticsEvent)

  /** Sets a user property for analytics segmentation. */
  public fun setUserProperty(key: String, value: String)
}

/** No-op analytics tracker. [isEnabled] always returns false, all methods are no-ops. */
public object NoOpAnalyticsTracker : AnalyticsTracker {

  override fun isEnabled(): Boolean = false

  override fun setEnabled(enabled: Boolean) {
    // Intentionally empty -- no-op tracker cannot be enabled.
  }

  override fun track(event: AnalyticsEvent) {
    // Intentionally empty.
  }

  override fun setUserProperty(key: String, value: String) {
    // Intentionally empty.
  }
}
