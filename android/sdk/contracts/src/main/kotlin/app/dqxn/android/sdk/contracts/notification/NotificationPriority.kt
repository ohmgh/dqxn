package app.dqxn.android.sdk.contracts.notification

/**
 * Priority levels for in-app notifications.
 *
 * Used for ordering, persistence, and auto-dismiss behavior. No driving-mode conditional behavior
 * at V1.
 */
public enum class NotificationPriority {
  CRITICAL,
  HIGH,
  NORMAL,
  LOW,
}
