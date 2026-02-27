package app.dqxn.android.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dqxn.android.data.device.ConnectionEvent
import app.dqxn.android.data.device.ConnectionEventStore
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import app.dqxn.android.sdk.observability.health.ProviderStatus
import app.dqxn.android.sdk.observability.health.ProviderStatusProvider
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import app.dqxn.android.sdk.observability.session.SessionEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel aggregating all observability data for the diagnostics feature.
 *
 * Exposes provider statuses, connection events, session recording state, and access to
 * [DiagnosticSnapshotCapture] and [MetricsCollector] for the viewer composables.
 */
@HiltViewModel
public class DiagnosticsViewModel
@Inject
constructor(
  private val providerStatusProvider: ProviderStatusProvider,
  private val connectionEventStore: ConnectionEventStore,
  public val snapshotCapture: DiagnosticSnapshotCapture,
  public val metricsCollector: MetricsCollector,
  private val sessionRecorder: SessionRecorder,
) : ViewModel() {

  /** Current status of all registered data providers. */
  public val providerStatuses: StateFlow<Map<String, ProviderStatus>> =
    providerStatusProvider
      .providerStatuses()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap(),
      )

  /** Rolling connection events from the ConnectionEventStore. */
  public val connectionEvents: StateFlow<ImmutableList<ConnectionEvent>> =
    connectionEventStore.events.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = persistentListOf(),
    )

  /** Whether session recording is currently active. */
  public val isRecording: StateFlow<Boolean> = sessionRecorder.isRecording

  /** Toggles session recording on/off. */
  public fun toggleRecording() {
    if (sessionRecorder.isRecording.value) {
      sessionRecorder.stopRecording()
    } else {
      sessionRecorder.startRecording()
    }
  }

  /** Returns an immutable snapshot of all recorded session events. */
  public fun getSessionSnapshot(): ImmutableList<SessionEvent> = sessionRecorder.snapshot()

  /** Clears all recorded session events. */
  public fun clearSessionEvents() {
    sessionRecorder.clear()
  }
}
