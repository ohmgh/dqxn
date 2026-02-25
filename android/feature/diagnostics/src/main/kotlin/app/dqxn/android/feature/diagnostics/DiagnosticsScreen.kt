package app.dqxn.android.feature.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.collections.immutable.toImmutableList

/**
 * Top-level diagnostics screen composable, composed inside [OverlayNavHost] for the
 * [DiagnosticsRoute].
 *
 * Aggregates all 5 diagnostic viewer composables from [DiagnosticsViewModel]:
 * 1. Provider health dashboard
 * 2. Session recorder viewer
 * 3. Diagnostic snapshot viewer
 * 4. Observability dashboard (frame metrics)
 *
 * Provider detail screen is navigated to inline via tap on a provider row (handled internally).
 */
@Composable
public fun DiagnosticsScreen(
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
  val providerStatuses by viewModel.providerStatuses.collectAsState()
  val isRecording by viewModel.isRecording.collectAsState()

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .testTag("diagnostics_screen"),
  ) {
    Text(
      text = "Diagnostics",
      style = MaterialTheme.typography.headlineMedium,
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Provider health
    ProviderHealthDashboard(
      statuses = providerStatuses.values.toList().toImmutableList(),
      currentTimeMs = System.currentTimeMillis(),
      onProviderClick = { /* Detail navigation handled in future */ },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    // Session recording
    SessionRecorderViewer(
      isRecording = isRecording,
      events = viewModel.getSessionSnapshot(),
      maxEvents = SessionRecorder.MAX_EVENTS,
      onToggleRecording = viewModel::toggleRecording,
      onClear = viewModel::clearSessionEvents,
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    // Diagnostic snapshots
    DiagnosticSnapshotViewer(
      snapshots = viewModel.snapshotCapture.recentSnapshots().toImmutableList(),
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    // Observability metrics
    ObservabilityDashboard(
      metricsSnapshot = viewModel.metricsCollector.snapshot(),
    )

    Spacer(modifier = Modifier.height(24.dp))
  }
}
