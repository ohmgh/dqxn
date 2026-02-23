package app.dqxn.android.sdk.contracts.notification

/** Result of firing an [AlertProfile] via [AlertEmitter]. */
public enum class AlertResult {
  /** Sound/vibration played successfully. */
  PLAYED,

  /** Silenced by DND or system policy. */
  SILENCED,

  /** Audio focus denied by another app. */
  FOCUS_DENIED,

  /** Sound/vibration hardware unavailable. */
  UNAVAILABLE,
}
