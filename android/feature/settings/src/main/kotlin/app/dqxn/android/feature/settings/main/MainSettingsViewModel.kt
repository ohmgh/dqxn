package app.dqxn.android.feature.settings.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dqxn.android.data.device.ConnectionEventStore
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.data.style.WidgetStyleStore
import app.dqxn.android.sdk.analytics.AnalyticsTracker
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the main settings screen.
 *
 * Exposes user preference flows for UI binding and provides actions for analytics consent toggling
 * and Delete All Data (F14.4, GDPR compliance).
 */
@HiltViewModel
public class MainSettingsViewModel
@Inject
constructor(
  private val userPreferencesRepository: UserPreferencesRepository,
  private val providerSettingsStore: ProviderSettingsStore,
  private val layoutRepository: LayoutRepository,
  private val pairedDeviceStore: PairedDeviceStore,
  private val widgetStyleStore: WidgetStyleStore,
  private val connectionEventStore: ConnectionEventStore,
  private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {

  /** Analytics consent state. Defaults to `false` (opt-in per PDPA/GDPR, F12.5). */
  public val analyticsConsent: StateFlow<Boolean> =
    userPreferencesRepository.analyticsConsent.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = false,
    )

  /** Whether to show the system status bar. */
  public val showStatusBar: StateFlow<Boolean> =
    userPreferencesRepository.showStatusBar.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = false,
    )

  /** Orientation lock preference ("landscape", "portrait", or "auto"). */
  public val orientationLock: StateFlow<String> =
    userPreferencesRepository.orientationLock.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = "auto",
    )

  /** Whether to keep the screen on while dashboard is visible. */
  public val keepScreenOn: StateFlow<Boolean> =
    userPreferencesRepository.keepScreenOn.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = true,
    )

  /**
   * Toggle analytics consent.
   *
   * When enabling: set preference first, then enable tracker. When disabling: disable tracker
   * first, then persist preference. Order matters to prevent data collection between state changes.
   */
  public fun setAnalyticsConsent(enabled: Boolean) {
    viewModelScope.launch {
      if (enabled) {
        userPreferencesRepository.setAnalyticsConsent(true)
        analyticsTracker.setEnabled(true)
      } else {
        analyticsTracker.setEnabled(false)
        userPreferencesRepository.setAnalyticsConsent(false)
      }
    }
  }

  /** Toggle status bar visibility. */
  public fun setShowStatusBar(visible: Boolean) {
    viewModelScope.launch { userPreferencesRepository.setShowStatusBar(visible) }
  }

  /** Update orientation lock preference. */
  public fun setOrientationLock(lock: String) {
    viewModelScope.launch { userPreferencesRepository.setOrientationLock(lock) }
  }

  /** Toggle keep screen on. */
  public fun setKeepScreenOn(enabled: Boolean) {
    viewModelScope.launch { userPreferencesRepository.setKeepScreenOn(enabled) }
  }

  /**
   * Delete all user data across all stores. Clears all 6 data stores/repositories and disables
   * analytics. Used by "Delete All Data" (F14.4) for GDPR compliance.
   */
  public fun deleteAllData() {
    viewModelScope.launch {
      userPreferencesRepository.clearAll()
      providerSettingsStore.clearAll()
      layoutRepository.clearAll()
      pairedDeviceStore.clearAll()
      widgetStyleStore.clearAll()
      connectionEventStore.clear()
      analyticsTracker.setEnabled(false)
    }
  }
}
