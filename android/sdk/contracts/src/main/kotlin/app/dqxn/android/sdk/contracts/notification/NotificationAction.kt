package app.dqxn.android.sdk.contracts.notification

import androidx.compose.runtime.Immutable

/** An action button on a [InAppNotification.Banner]. */
@Immutable
public data class NotificationAction(
  val label: String,
  val actionId: String,
)
