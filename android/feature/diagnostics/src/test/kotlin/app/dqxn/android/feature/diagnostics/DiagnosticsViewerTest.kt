package app.dqxn.android.feature.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotDto
import app.dqxn.android.sdk.observability.metrics.MetricsSnapshot
import app.dqxn.android.sdk.observability.session.EventType
import app.dqxn.android.sdk.observability.session.SessionEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiagnosticsViewerTest {

  @get:Rule val composeTestRule = createComposeRule()

  // --- DiagnosticSnapshotViewer tests ---

  @Test
  fun `snapshot filter chips filter by type`() {
    val snapshots =
      listOf(
        snapshotDto(id = "1", triggerType = "WidgetCrash", description = "crash 1"),
        snapshotDto(id = "2", triggerType = "JankSpike", description = "jank 1"),
        snapshotDto(id = "3", triggerType = "WidgetCrash", description = "crash 2"),
      ).toImmutableList()

    composeTestRule.setContent {
      DiagnosticSnapshotViewer(snapshots = snapshots)
    }

    // Initially all 3 snapshots visible
    composeTestRule.onNodeWithTag("snapshot_row_0", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("snapshot_row_1", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("snapshot_row_2", useUnmergedTree = true).assertIsDisplayed()

    // Click CRASH filter
    composeTestRule.onNodeWithTag("snapshot_filter_CRASH").performClick()

    // Only crash rows visible (2 items filtered from 3)
    composeTestRule.onNodeWithTag("snapshot_row_0", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("snapshot_row_1", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("snapshot_row_2", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `snapshot viewer empty state`() {
    composeTestRule.setContent {
      DiagnosticSnapshotViewer(snapshots = persistentListOf())
    }

    composeTestRule.onNodeWithText("No snapshots").assertIsDisplayed()
  }

  // --- SessionRecorderViewer tests ---

  @Test
  fun `recording toggle shows red dot when recording`() {
    composeTestRule.setContent {
      SessionRecorderViewer(
        isRecording = true,
        events = persistentListOf(),
        maxEvents = 10_000,
        onToggleRecording = {},
        onClear = {},
      )
    }

    composeTestRule
      .onNodeWithTag("recording_indicator", useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Stop Recording", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `session viewer shows event count`() {
    val events =
      (0 until 50)
        .map { i ->
          SessionEvent(timestamp = i.toLong(), type = EventType.TAP, details = "event $i")
        }
        .toImmutableList()

    composeTestRule.setContent {
      SessionRecorderViewer(
        isRecording = false,
        events = events,
        maxEvents = 10_000,
        onToggleRecording = {},
        onClear = {},
      )
    }

    composeTestRule.onNodeWithText("50 / 10,000 events").assertIsDisplayed()
  }

  @Test
  fun `clear button fires callback`() {
    var clearCalled = false

    composeTestRule.setContent {
      SessionRecorderViewer(
        isRecording = false,
        events = persistentListOf(),
        maxEvents = 10_000,
        onToggleRecording = {},
        onClear = { clearCalled = true },
      )
    }

    composeTestRule.onNodeWithTag("clear_button").performClick()

    assertThat(clearCalled).isTrue()
  }

  // --- ObservabilityDashboard tests ---

  @Test
  fun `observability dashboard renders frame metrics`() {
    // Histogram: <8ms=80, <12ms=10, <16ms=5, <24ms=3, <33ms=1, >33ms=1 = 100 total
    val metrics =
      MetricsSnapshot(
        frameHistogram = persistentListOf(80L, 10L, 5L, 3L, 1L, 1L),
        totalFrameCount = 100L,
        widgetDrawTimes = persistentMapOf(),
        providerLatencies = persistentMapOf(),
        recompositionCounts = persistentMapOf(),
        captureTimestamp = System.currentTimeMillis(),
      )

    composeTestRule.setContent {
      ObservabilityDashboard(metricsSnapshot = metrics)
    }

    // P50 should be 4ms (80% in first bucket, p50 lands in bucket 0 midpoint=4)
    composeTestRule.onNodeWithTag("frame_time_p50").assertIsDisplayed()
    composeTestRule.onNodeWithTag("frame_time_p95").assertIsDisplayed()
    composeTestRule.onNodeWithTag("jank_percent").assertIsDisplayed()
  }

  // --- Helpers ---

  private fun snapshotDto(
    id: String,
    triggerType: String,
    description: String,
    timestamp: Long = System.currentTimeMillis(),
  ): DiagnosticSnapshotDto =
    DiagnosticSnapshotDto(
      id = id,
      timestamp = timestamp,
      triggerType = triggerType,
      triggerDescription = description,
    )
}
