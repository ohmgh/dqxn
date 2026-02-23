package app.dqxn.android.sdk.observability.health

import android.os.Handler
import app.dqxn.android.sdk.observability.diagnostic.AnomalyTrigger
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticFileWriter
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshot
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.log.RingBufferSink
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import app.dqxn.android.sdk.observability.trace.DqxnTracer
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * AnrWatchdog tests use a mock Handler to control main-thread scheduling and a test double for
 * DiagnosticSnapshotCapture to record triggers without file I/O.
 */
class AnrWatchdogTest {

  @TempDir lateinit var tempDir: File

  private val capturedTriggers = CopyOnWriteArrayList<AnomalyTrigger>()

  /** Test double that records triggers thread-safely. */
  private val recordingCapture: DiagnosticSnapshotCapture by lazy {
    val fileWriter =
      object : DiagnosticFileWriter(tempDir, NoOpLogger) {
        override fun checkStoragePressure(): Boolean = false
      }

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
  }

  private var anrWatchdog: AnrWatchdog? = null

  @AfterEach
  fun tearDown() {
    anrWatchdog?.stop()
  }

  private fun createMockHandler(respondToPost: () -> Boolean): Handler {
    val handler = mockk<Handler>(relaxed = true)
    every { handler.post(any()) } answers
      {
        val runnable = firstArg<Runnable>()
        if (respondToPost()) {
          runnable.run()
        }
        true
      }
    return handler
  }

  @Test
  fun `single miss does not trigger capture`() {
    var missCount = 0
    val handler = createMockHandler {
      missCount++
      missCount > 1
    }

    anrWatchdog =
      AnrWatchdog(
        diagnosticCapture = recordingCapture,
        logger = NoOpLogger,
        timeoutMs = 50,
        debuggerCheck = { false },
        mainHandler = handler,
      )
    anrWatchdog!!.start()

    Thread.sleep(300)
    anrWatchdog!!.stop()

    val anrTriggers = capturedTriggers.filterIsInstance<AnomalyTrigger.AnrDetected>()
    assertThat(anrTriggers).isEmpty()
  }

  @Test
  fun `two consecutive misses trigger capture`() {
    var postCount = 0
    val handler = createMockHandler {
      postCount++
      postCount > 2
    }

    anrWatchdog =
      AnrWatchdog(
        diagnosticCapture = recordingCapture,
        logger = NoOpLogger,
        timeoutMs = 50,
        debuggerCheck = { false },
        mainHandler = handler,
      )
    anrWatchdog!!.start()

    Thread.sleep(300)
    anrWatchdog!!.stop()

    val anrTriggers = capturedTriggers.filterIsInstance<AnomalyTrigger.AnrDetected>()
    assertThat(anrTriggers).isNotEmpty()
  }

  @Test
  fun `debugger attached suppresses detection`() {
    val handler = createMockHandler { false }

    anrWatchdog =
      AnrWatchdog(
        diagnosticCapture = recordingCapture,
        logger = NoOpLogger,
        timeoutMs = 50,
        debuggerCheck = { true },
        mainHandler = handler,
      )
    anrWatchdog!!.start()

    Thread.sleep(300)
    anrWatchdog!!.stop()

    val anrTriggers = capturedTriggers.filterIsInstance<AnomalyTrigger.AnrDetected>()
    assertThat(anrTriggers).isEmpty()
  }

  @Test
  fun `query returns last ANR info`() {
    var postCount = 0
    val handler = createMockHandler {
      postCount++
      postCount > 2
    }

    anrWatchdog =
      AnrWatchdog(
        diagnosticCapture = recordingCapture,
        logger = NoOpLogger,
        timeoutMs = 50,
        debuggerCheck = { false },
        mainHandler = handler,
      )

    assertThat(anrWatchdog!!.query()).isNull()

    anrWatchdog!!.start()
    Thread.sleep(300)
    anrWatchdog!!.stop()

    val anrInfo = anrWatchdog!!.query()
    assertThat(anrInfo).isNotNull()
    assertThat(anrInfo!!.timestamp).isGreaterThan(0)
  }
}
