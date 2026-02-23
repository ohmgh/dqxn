package app.dqxn.android.sdk.contracts.notification

import kotlinx.collections.immutable.ImmutableList

/**
 * In-app notification displayed by `NotificationCoordinator`.
 *
 * Two subtypes:
 * - [Toast] -- brief message, auto-dismiss
 * - [Banner] -- persistent with title, actions, dismissible flag
 */
public sealed interface InAppNotification {
  public val id: String
  public val priority: NotificationPriority
  public val timestamp: Long
  public val alertProfile: AlertProfile?

  public data class Toast(
    override val id: String,
    override val priority: NotificationPriority,
    override val timestamp: Long,
    override val alertProfile: AlertProfile? = null,
    val message: String,
    val durationMs: Long = 4_000L,
  ) : InAppNotification

  public data class Banner(
    override val id: String,
    override val priority: NotificationPriority,
    override val timestamp: Long,
    override val alertProfile: AlertProfile? = null,
    val title: String,
    val message: String,
    val actions: ImmutableList<NotificationAction>,
    val dismissible: Boolean,
  ) : InAppNotification
}
