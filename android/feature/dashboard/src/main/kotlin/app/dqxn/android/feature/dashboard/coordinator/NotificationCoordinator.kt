package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.feature.dashboard.binding.StorageMonitor
import app.dqxn.android.feature.dashboard.safety.SafeModeManager
import app.dqxn.android.sdk.contracts.notification.AlertEmitter
import app.dqxn.android.sdk.contracts.notification.AlertMode
import app.dqxn.android.sdk.contracts.notification.AlertProfile
import app.dqxn.android.sdk.contracts.notification.InAppNotification
import app.dqxn.android.sdk.contracts.notification.NotificationPriority
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.debug
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates in-app notification display: banners (persistent) and toasts (brief).
 *
 * Banners are condition-keyed (NOT UUID-keyed) to prevent flicker on re-derivation per pitfall 4 in
 * research. Each banner has a stable [String] key derived from its condition source. When the
 * coordinator is re-created (process death), it re-observes singleton state flows which re-emit
 * current values. [StateFlow] already provides distinct-until-changed semantics via structural
 * equality, preventing duplicate banner show/dismiss cycles.
 *
 * Notification priority ordering: CRITICAL > HIGH > NORMAL > LOW. Banners are sorted by priority in
 * [activeBanners].
 */
public class NotificationCoordinator
@Inject
constructor(
  private val safeModeManager: SafeModeManager,
  private val storageMonitor: StorageMonitor,
  private val alertEmitter: AlertEmitter,
  private val logger: DqxnLogger,
) {

  private val _activeBanners: MutableStateFlow<ImmutableList<InAppNotification.Banner>> =
    MutableStateFlow(persistentListOf())

  /**
   * Active banners sorted by priority (CRITICAL first). Derived from condition state flows.
   * Re-derivation on recreation: all singleton flows re-emit current values, so banners are
   * reconstructed immediately.
   */
  public val activeBanners: StateFlow<ImmutableList<InAppNotification.Banner>> =
    _activeBanners.asStateFlow()

  /**
   * Toast channel: single-consumer, exactly-once delivery. Buffered to prevent loss when consumer
   * is temporarily suspended.
   */
  public val toasts: Channel<InAppNotification.Toast> = Channel(capacity = Channel.BUFFERED)

  /** Coordinator scope saved from [initialize] for launching async side-effects. */
  private lateinit var coordinatorScope: CoroutineScope

  /** Internal mutable banner map keyed by condition ID. Sorted on every mutation. */
  private val bannerMap: MutableMap<String, InAppNotification.Banner> = mutableMapOf()

  /**
   * Initialize the coordinator by observing singleton state flows. Each observer uses
   * [distinctUntilChanged] to prevent banner flicker on rapid state changes.
   *
   * Must be called once from ViewModel init with the ViewModel's coroutine scope.
   */
  public fun initialize(scope: CoroutineScope) {
    coordinatorScope = scope

    // Observe safe mode state (CRITICAL banner)
    // StateFlow already guarantees distinctUntilChanged semantics via structural equality.
    scope.launch {
      safeModeManager.safeModeActive.collect { isActive ->
        if (isActive) {
          showBanner(
            id = BANNER_SAFE_MODE,
            priority = NotificationPriority.CRITICAL,
            title = "Safe Mode",
            message = "App recovered from repeated crashes. Reset to fix.",
            dismissible = false,
            alertProfile = AlertProfile(mode = AlertMode.VIBRATE),
          )
        } else {
          dismissBanner(BANNER_SAFE_MODE)
        }
      }
    }

    // Observe low storage state (HIGH banner, NF41)
    // StateFlow already guarantees distinctUntilChanged semantics.
    scope.launch {
      storageMonitor.isLow.collect { isLow ->
        if (isLow) {
          showBanner(
            id = BANNER_LOW_STORAGE,
            priority = NotificationPriority.HIGH,
            title = "Low Storage",
            message = "Storage is running low. Free up space to save changes.",
            dismissible = true,
          )
        } else {
          dismissBanner(BANNER_LOW_STORAGE)
        }
      }
    }

    logger.info(TAG) { "NotificationCoordinator initialized" }
  }

  /**
   * Show or update a banner by condition [id]. If a banner with this ID already exists, it is
   * replaced (condition-keyed update, no duplication). Banners are re-sorted by priority.
   *
   * If [alertProfile] is non-null, delegates to [alertEmitter] for audio/vibration (F9.2, F9.3,
   * F9.4).
   */
  public fun showBanner(
    id: String,
    priority: NotificationPriority,
    title: String,
    message: String,
    dismissible: Boolean = true,
    actions: ImmutableList<app.dqxn.android.sdk.contracts.notification.NotificationAction> =
      persistentListOf(),
    alertProfile: AlertProfile? = null,
  ) {
    val banner =
      InAppNotification.Banner(
        id = id,
        priority = priority,
        timestamp = System.currentTimeMillis(),
        alertProfile = alertProfile,
        title = title,
        message = message,
        actions = actions,
        dismissible = dismissible,
      )

    synchronized(bannerMap) {
      bannerMap[id] = banner
      refreshBannerState()
    }

    // Fire alert side-effect (F9.2, F9.3, F9.4) when an alert profile is configured.
    // alertEmitter.fire() is a suspend function, so we launch on the coordinator scope.
    if (alertProfile != null && ::coordinatorScope.isInitialized) {
      coordinatorScope.launch { alertEmitter.fire(alertProfile) }
    }

    logger.debug(TAG) { "Banner shown: $id ($priority)" }
  }

  /** Dismiss a banner by condition [id]. No-op if the banner doesn't exist. */
  public fun dismissBanner(id: String) {
    synchronized(bannerMap) {
      if (bannerMap.remove(id) != null) {
        refreshBannerState()
        logger.debug(TAG) { "Banner dismissed: $id" }
      }
    }
  }

  /** Show a toast notification. Sends to [toasts] channel for exactly-once consumption. */
  public fun showToast(toast: InAppNotification.Toast) {
    toasts.trySend(toast)
    logger.debug(TAG) { "Toast queued: ${toast.id}" }
  }

  /**
   * Emit connection status notification (F9.1). Shows a NORMAL priority banner for device
   * disconnection, dismisses on reconnection.
   *
   * @param deviceName Human-readable device name for the banner message.
   * @param connected True if the device is now connected, false if disconnected.
   */
  public fun emitConnectionStatus(deviceName: String, connected: Boolean) {
    val bannerId = "$BANNER_CONNECTION_PREFIX$deviceName"
    if (connected) {
      dismissBanner(bannerId)
      logger.info(TAG) { "Device connected: $deviceName" }
    } else {
      showBanner(
        id = bannerId,
        priority = NotificationPriority.NORMAL,
        title = "Disconnected",
        message = "$deviceName disconnected.",
        dismissible = true,
      )
      logger.warn(TAG) { "Device disconnected: $deviceName" }
    }
  }

  /**
   * Report a layout save failure (NF42). Called by LayoutCoordinator or ViewModel when persistence
   * fails. Shows a HIGH priority banner until explicitly dismissed.
   */
  public fun reportLayoutSaveFailure() {
    showBanner(
      id = BANNER_LAYOUT_SAVE_FAILED,
      priority = NotificationPriority.HIGH,
      title = "Save Failed",
      message = "Unable to save layout. Free up storage space.",
      dismissible = true,
    )
  }

  /** Clear the layout save failure banner. Called when a subsequent save succeeds. */
  public fun clearLayoutSaveFailure() {
    dismissBanner(BANNER_LAYOUT_SAVE_FAILED)
  }

  /**
   * Sort banners by priority and update the state flow. Must be called with [bannerMap] lock held.
   */
  private fun refreshBannerState() {
    _activeBanners.value = bannerMap.values.sortedBy { it.priority.ordinal }.toImmutableList()
  }

  internal companion object {
    val TAG: LogTag = LogTag("NotifCoord")

    internal const val BANNER_SAFE_MODE: String = "safe_mode"
    internal const val BANNER_LOW_STORAGE: String = "low_storage"
    internal const val BANNER_LAYOUT_SAVE_FAILED: String = "layout_save_failed"
    internal const val BANNER_CONNECTION_PREFIX: String = "connection_"
  }
}
