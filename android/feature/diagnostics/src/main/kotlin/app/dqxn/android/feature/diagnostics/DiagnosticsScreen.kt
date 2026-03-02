package app.dqxn.android.feature.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
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

  OverlayScaffold(
    title = "Diagnostics",
    overlayType = OverlayType.Hub,
    onBack = onClose,
    modifier = modifier,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(top = DashboardSpacing.SpaceM)
          .testTag("diagnostics_screen"),
      verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SectionGap),
    ) {
      // Provider health — bounded height so LazyColumn gets finite constraints
      SectionCard(title = "Providers") {
        ProviderHealthDashboard(
          statuses = providerStatuses.values.toList().toImmutableList(),
          currentTimeMs = System.currentTimeMillis(),
          onProviderClick = { /* Detail navigation handled in future */ },
          modifier = Modifier.heightIn(max = 300.dp),
        )
      }

      // Session recording — bounded height so LazyColumn gets finite constraints
      SectionCard(title = "Session Recording") {
        SessionRecorderViewer(
          isRecording = isRecording,
          events = viewModel.getSessionSnapshot(),
          maxEvents = SessionRecorder.MAX_EVENTS,
          onToggleRecording = viewModel::toggleRecording,
          onClear = viewModel::clearSessionEvents,
          modifier = Modifier.heightIn(max = 400.dp),
        )
      }

      // Diagnostic snapshots — bounded height so LazyColumn gets finite constraints
      SectionCard(title = "Snapshots") {
        DiagnosticSnapshotViewer(
          snapshots = viewModel.snapshotCapture.recentSnapshots().toImmutableList(),
          modifier = Modifier.heightIn(max = 400.dp),
        )
      }

      // Observability metrics
      SectionCard(title = "Observability") {
        ObservabilityDashboard(
          metricsSnapshot = viewModel.metricsCollector.snapshot(),
        )
      }

      Spacer(modifier = Modifier.height(DashboardSpacing.SpaceL))
    }
  }
}

/** Card surface for each diagnostics section, matching pack browser card conventions. */
@Composable
private fun SectionCard(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val theme = LocalDashboardTheme.current
  val shape = RoundedCornerShape(CardSize.LARGE.cornerRadius)

  Column(modifier = modifier) {
    Text(
      text = title.uppercase(),
      style = DashboardTypography.sectionHeader,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      modifier = Modifier.padding(bottom = DashboardSpacing.SpaceXS),
    )
    Card(
      shape = shape,
      border = BorderStroke(1.dp, theme.widgetBorderColor.copy(alpha = 0.3f)),
      colors =
        CardDefaults.cardColors(
          containerColor = theme.widgetBorderColor.copy(alpha = 0.15f),
        ),
    ) {
      Column(modifier = Modifier.padding(DashboardSpacing.CardInternalPadding)) {
        content()
      }
    }
  }
}
