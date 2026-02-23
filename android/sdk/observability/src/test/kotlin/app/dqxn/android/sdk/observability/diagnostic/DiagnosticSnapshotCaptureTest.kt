package app.dqxn.android.sdk.observability.diagnostic

import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.log.RingBufferSink
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import app.dqxn.android.sdk.observability.trace.DqxnTracer
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DiagnosticSnapshotCaptureTest {

  @TempDir lateinit var tempDir: File

  /** Test file writer that never reports storage pressure. */
  private fun createTestFileWriter(dir: File = tempDir): DiagnosticFileWriter =
    object : DiagnosticFileWriter(dir, NoOpLogger) {
      override fun checkStoragePressure(): Boolean = false
    }

  /** Test file writer that always reports storage pressure. */
  private fun createPressuredFileWriter(): DiagnosticFileWriter =
    object : DiagnosticFileWriter(tempDir, NoOpLogger) {
      override fun checkStoragePressure(): Boolean = true
    }

  /** Test file writer that blocks during write (for concurrency testing). */
  private fun createBlockingFileWriter(
    startedLatch: CountDownLatch,
    blockingLatch: CountDownLatch,
  ): DiagnosticFileWriter =
    object : DiagnosticFileWriter(tempDir, NoOpLogger) {
      override fun checkStoragePressure(): Boolean = false

      override fun write(snapshot: DiagnosticSnapshot, pool: String) {
        startedLatch.countDown()
        blockingLatch.await()
        super.write(snapshot, pool)
      }
    }

  private fun createCapture(
    fileWriter: DiagnosticFileWriter = createTestFileWriter(),
    metricsCollector: MetricsCollector = MetricsCollector(),
  ): DiagnosticSnapshotCapture {
    return DiagnosticSnapshotCapture(
      logger = NoOpLogger,
      metricsCollector = metricsCollector,
      tracer = DqxnTracer,
      logRingBuffer = RingBufferSink(100),
      fileWriter = fileWriter,
    )
  }

  @Test
  fun `capture assembles snapshot from metrics and log tail`() {
    val metricsCollector = MetricsCollector()
    metricsCollector.recordFrame(10)
    metricsCollector.recordFrame(20)

    val capture = createCapture(metricsCollector = metricsCollector)

    val result = capture.capture(AnomalyTrigger.JankSpike(5))

    assertThat(result).isNotNull()
    assertThat(result!!.metricsSnapshot).isNotNull()
    assertThat(result.metricsSnapshot!!.totalFrameCount).isEqualTo(2)
    assertThat(result.trigger).isEqualTo(AnomalyTrigger.JankSpike(5))
    assertThat(result.id).isNotEmpty()
  }

  @Test
  fun `concurrent capture dropped`() {
    val blockingLatch = CountDownLatch(1)
    val startedLatch = CountDownLatch(1)
    val blockingWriter = createBlockingFileWriter(startedLatch, blockingLatch)

    val capture = createCapture(fileWriter = blockingWriter)
    val executor = Executors.newFixedThreadPool(2)

    // First capture blocks on write
    val future1 =
      executor.submit<DiagnosticSnapshot?> { capture.capture(AnomalyTrigger.JankSpike(5)) }

    // Wait for first capture to enter write
    startedLatch.await()

    // Second capture should be dropped (AtomicBoolean guard)
    val result2 = capture.capture(AnomalyTrigger.JankSpike(10))
    assertThat(result2).isNull()

    // Unblock first capture
    blockingLatch.countDown()
    val result1 = future1.get()
    assertThat(result1).isNotNull()

    executor.shutdown()
  }

  @Test
  fun `rotation pool assignment - crash trigger writes to crash pool`() {
    val capture = createCapture()

    capture.capture(AnomalyTrigger.WidgetCrash("t", "w", "err"))

    val crashDir = File(tempDir, "crash")
    assertThat(crashDir.listFiles()?.isNotEmpty()).isTrue()
  }

  @Test
  fun `rotation pool assignment - thermal trigger writes to thermal pool`() {
    val capture = createCapture()

    capture.capture(AnomalyTrigger.ThermalEscalation("NOMINAL", "DEGRADED"))

    val thermalDir = File(tempDir, "thermal")
    assertThat(thermalDir.listFiles()?.isNotEmpty()).isTrue()
  }

  @Test
  fun `rotation pool assignment - perf trigger writes to perf pool`() {
    val capture = createCapture()

    capture.capture(AnomalyTrigger.JankSpike(5))

    val perfDir = File(tempDir, "perf")
    assertThat(perfDir.listFiles()?.isNotEmpty()).isTrue()
  }

  @Test
  fun `rotation eviction respects pool limits`() {
    val capture = createCapture()

    // Write 22 crash snapshots (limit is 20)
    repeat(22) { capture.capture(AnomalyTrigger.WidgetCrash("t", "w$it", "err$it")) }

    val crashDir = File(tempDir, "crash")
    assertThat(crashDir.listFiles()?.size).isEqualTo(20)
  }

  @Test
  fun `storage pressure skips capture`() {
    val capture = createCapture(fileWriter = createPressuredFileWriter())

    val result = capture.capture(AnomalyTrigger.JankSpike(5))

    assertThat(result).isNull()
  }
}
