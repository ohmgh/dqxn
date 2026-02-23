package app.dqxn.android.sdk.observability.metrics

import app.dqxn.android.sdk.observability.diagnostic.AnomalyTrigger
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticFileWriter
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshot
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.log.RingBufferSink
import app.dqxn.android.sdk.observability.trace.DqxnTracer
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JankDetectorTest {

  @TempDir lateinit var tempDir: File

  private val capturedTriggers = mutableListOf<AnomalyTrigger>()

  private val detector: JankDetector by lazy {
    val fileWriter =
      object : DiagnosticFileWriter(tempDir, NoOpLogger) {
        override fun checkStoragePressure(): Boolean = false
      }

    val recordingCapture =
      object :
        DiagnosticSnapshotCapture(
          logger = NoOpLogger,
          metricsCollector = MetricsCollector(),
          tracer = DqxnTracer,
          logRingBuffer = RingBufferSink(10),
          fileWriter = fileWriter,
        ) {
        override fun capture(
          trigger: AnomalyTrigger,
          agenticTraceId: String?,
        ): DiagnosticSnapshot? {
          capturedTriggers.add(trigger)
          return null
        }
      }

    JankDetector(recordingCapture, NoOpLogger)
  }

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
