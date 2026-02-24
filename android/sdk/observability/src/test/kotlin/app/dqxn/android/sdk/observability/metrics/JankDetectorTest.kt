package app.dqxn.android.sdk.observability.metrics

import app.dqxn.android.sdk.observability.diagnostic.AnomalyTrigger
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test

class JankDetectorTest {

  private val capturedTriggers = mutableListOf<AnomalyTrigger>()

  private val mockCapture: DiagnosticSnapshotCapture =
    mockk<DiagnosticSnapshotCapture>(relaxed = true).also {
      val triggerSlot = slot<AnomalyTrigger>()
      every { it.capture(capture(triggerSlot), any()) } answers
        {
          capturedTriggers.add(triggerSlot.captured)
          null
        }
    }

  private val detector = JankDetector(mockCapture, NoOpLogger)

  @Test
  fun `non-janky frame resets counter`() {
    repeat(4) { detector.onFrameRendered(20) }
    detector.onFrameRendered(10)

    assertThat(capturedTriggers).isEmpty()
  }

  @Test
  fun `5th consecutive janky frame triggers capture`() {
    repeat(5) { detector.onFrameRendered(20) }

    assertThat(capturedTriggers).containsExactly(AnomalyTrigger.JankSpike(5))
  }

  @Test
  fun `4th consecutive janky frame does NOT trigger`() {
    repeat(4) { detector.onFrameRendered(20) }

    assertThat(capturedTriggers).isEmpty()
  }

  @Test
  fun `20th consecutive janky frame triggers capture`() {
    repeat(20) { detector.onFrameRendered(20) }

    assertThat(capturedTriggers)
      .containsExactly(AnomalyTrigger.JankSpike(5), AnomalyTrigger.JankSpike(20))
      .inOrder()
  }

  @Test
  fun `100th consecutive janky frame triggers capture`() {
    repeat(100) { detector.onFrameRendered(20) }

    assertThat(capturedTriggers)
      .containsExactly(
        AnomalyTrigger.JankSpike(5),
        AnomalyTrigger.JankSpike(20),
        AnomalyTrigger.JankSpike(100),
      )
      .inOrder()
  }

  @Test
  fun `99th consecutive janky frame does NOT trigger third capture`() {
    repeat(99) { detector.onFrameRendered(20) }

    assertThat(capturedTriggers)
      .containsExactly(AnomalyTrigger.JankSpike(5), AnomalyTrigger.JankSpike(20))
      .inOrder()
  }

  @Test
  fun `19th consecutive janky frame does NOT trigger second capture`() {
    repeat(19) { detector.onFrameRendered(20) }

    assertThat(capturedTriggers).containsExactly(AnomalyTrigger.JankSpike(5))
  }
}
