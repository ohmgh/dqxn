package app.dqxn.android.sdk.observability.log

/** Standard log tags used across the DQXN platform. */
public object LogTags {
  public val DASHBOARD: LogTag = LogTag("dashboard")
  public val WIDGET: LogTag = LogTag("widget")
  public val PROVIDER: LogTag = LogTag("provider")
  public val THEME: LogTag = LogTag("theme")
  public val LAYOUT: LogTag = LogTag("layout")
  public val BINDING: LogTag = LogTag("binding")
  public val LIFECYCLE: LogTag = LogTag("lifecycle")
  public val AGENTIC: LogTag = LogTag("agentic")
  public val DIAGNOSTIC: LogTag = LogTag("diagnostic")
  public val THERMAL: LogTag = LogTag("thermal")
  public val ANALYTICS: LogTag = LogTag("analytics")
  public val SETUP: LogTag = LogTag("setup")
  public val PROFILE: LogTag = LogTag("profile")
  public val NOTIFICATION: LogTag = LogTag("notification")
}
