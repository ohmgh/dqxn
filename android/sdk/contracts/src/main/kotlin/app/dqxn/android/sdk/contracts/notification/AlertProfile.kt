package app.dqxn.android.sdk.contracts.notification

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * Alert configuration for a notification.
 * - F9.2: [AlertMode] for per-alert sound/vibration selection.
 * - F9.3: [ttsMessage] for text-to-speech announcements.
 * - F9.4: [soundUri] for custom notification sounds.
 */
@Immutable
public data class AlertProfile(
  val mode: AlertMode,
  val soundUri: String? = null,
  val ttsMessage: String? = null,
  val vibrationPattern: ImmutableList<Long>? = null,
)

/** Alert delivery mode. */
public enum class AlertMode {
  SILENT,
  VIBRATE,
  SOUND,
}
