package app.dqxn.android.agentic.handlers

import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.core.agentic.chaos.ChaosProviderInterceptor
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticFileWriter
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.log.RingBufferSink
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import app.dqxn.android.sdk.observability.trace.DqxnTracer
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SC3 gap closure test: proves the agentic debug pipeline
 * inject-fault -> capture-snapshot -> list-diagnostics since= produces correlated output.
 *
 * Uses real collaborators (ChaosProviderInterceptor, DiagnosticSnapshotCapture) with only
 * DiagnosticFileWriter MockK-wrapped to avoid Android StatFs in checkStoragePressure().
 */
@Tag("fast")
class ChaosCorrelationTest {

  @TempDir lateinit var tempDir: File

  private fun createMockFileWriter(dir: File): DiagnosticFileWriter {
    val realWriter = DiagnosticFileWriter(dir, NoOpLogger)
    val mock = mockk<DiagnosticFileWriter>()
    every { mock.checkStoragePressure() } returns false
    every { mock.write(any(), any()) } answers { realWriter.write(firstArg(), secondArg()) }
    every { mock.read(any()) } answers { realWriter.read(firstArg()) }
    return mock
  }

  private fun createSharedInstances(): Triple<ChaosProviderInterceptor, DiagnosticSnapshotCapture, DiagnosticFileWriter> {
    val interceptor = ChaosProviderInterceptor()
    val fileWriter = createMockFileWriter(tempDir)
    val capture = DiagnosticSnapshotCapture(
      logger = NoOpLogger,
      metricsCollector = MetricsCollector(),
      tracer = DqxnTracer,
      logRingBuffer = RingBufferSink(100),
      fileWriter = fileWriter,
    )
    return Triple(interceptor, capture, fileWriter)
  }

  @Test
  fun `inject fault then capture snapshot then list diagnostics since before injection returns snapshot`() =
    runTest {
      val (interceptor, capture, _) = createSharedInstances()
      val chaosInjectHandler = ChaosInjectHandler(interceptor)
      val captureSnapshotHandler = CaptureSnapshotHandler(capture)
      val listDiagnosticsHandler = ListDiagnosticsHandler(capture)

      val beforeMs = System.currentTimeMillis()

      // Step 1: Inject fault
      val injectResult = chaosInjectHandler.execute(
        CommandParams(
          raw = mapOf("providerId" to "test:speed", "fault" to "kill"),
          traceId = "correlation-test",
        ),
        "cmd-inject-1",
      )
      assertThat(injectResult).isInstanceOf(CommandResult.Success::class.java)

      // Step 2: Capture snapshot
      val captureResult = captureSnapshotHandler.execute(
        CommandParams(
          raw = mapOf("reason" to "chaos-correlation"),
          traceId = "correlation-test",
        ),
        "cmd-capture-1",
      )
      assertThat(captureResult).isInstanceOf(CommandResult.Success::class.java)
      val captureJson = Json.parseToJsonElement((captureResult as CommandResult.Success).data).jsonObject
      assertThat(captureJson["captured"]?.jsonPrimitive?.content).isEqualTo("true")

      // Step 3: List diagnostics with since= filter
      val listResult = listDiagnosticsHandler.execute(
        CommandParams(
          raw = mapOf("since" to beforeMs.toString()),
          traceId = "correlation-test",
        ),
        "cmd-list-1",
      )
      assertThat(listResult).isInstanceOf(CommandResult.Success::class.java)
      val listJson = Json.parseToJsonElement((listResult as CommandResult.Success).data).jsonObject

      // Verify count >= 1
      val count = listJson["count"]!!.jsonPrimitive.int
      assertThat(count).isAtLeast(1)

      // Verify at least one snapshot has timestamp >= beforeMs
      val snapshots = listJson["snapshots"]!!.jsonArray
      val hasRecentSnapshot = snapshots.any { element ->
        element.jsonObject["timestamp"]!!.jsonPrimitive.long >= beforeMs
      }
      assertThat(hasRecentSnapshot).isTrue()

      // Verify at least one snapshot has agenticTraceId containing "chaos-correlation"
      val hasCorrelatedTrace = snapshots.any { element ->
        val traceId = element.jsonObject["agenticTraceId"]?.jsonPrimitive?.content
        traceId != null && traceId.contains("chaos-correlation")
      }
      assertThat(hasCorrelatedTrace).isTrue()
    }

  @Test
  fun `list diagnostics since future timestamp returns zero snapshots`() = runTest {
    val (interceptor, capture, _) = createSharedInstances()
    val chaosInjectHandler = ChaosInjectHandler(interceptor)
    val captureSnapshotHandler = CaptureSnapshotHandler(capture)
    val listDiagnosticsHandler = ListDiagnosticsHandler(capture)

    // Inject a fault and capture a snapshot
    chaosInjectHandler.execute(
      CommandParams(
        raw = mapOf("providerId" to "test:speed", "fault" to "kill"),
        traceId = "future-filter-test",
      ),
      "cmd-inject-2",
    )
    captureSnapshotHandler.execute(
      CommandParams(
        raw = mapOf("reason" to "future-filter"),
        traceId = "future-filter-test",
      ),
      "cmd-capture-2",
    )

    // List with a future timestamp
    val futureMs = System.currentTimeMillis() + 100_000L
    val listResult = listDiagnosticsHandler.execute(
      CommandParams(
        raw = mapOf("since" to futureMs.toString()),
        traceId = "future-filter-test",
      ),
      "cmd-list-2",
    )
    assertThat(listResult).isInstanceOf(CommandResult.Success::class.java)
    val listJson = Json.parseToJsonElement((listResult as CommandResult.Success).data).jsonObject

    assertThat(listJson["count"]!!.jsonPrimitive.int).isEqualTo(0)
    assertThat(listJson["snapshots"]!!.jsonArray).isEmpty()
  }

  @Test
  fun `multiple fault injections followed by single capture produces one snapshot`() = runTest {
    val (interceptor, capture, _) = createSharedInstances()
    val chaosInjectHandler = ChaosInjectHandler(interceptor)
    val captureSnapshotHandler = CaptureSnapshotHandler(capture)
    val listDiagnosticsHandler = ListDiagnosticsHandler(capture)

    val beforeMs = System.currentTimeMillis()

    // Inject faults into 3 different providers
    chaosInjectHandler.execute(
      CommandParams(
        raw = mapOf("providerId" to "test:speed", "fault" to "kill"),
        traceId = "multi-fault-test",
      ),
      "cmd-inject-3a",
    )
    chaosInjectHandler.execute(
      CommandParams(
        raw = mapOf("providerId" to "test:accel", "fault" to "stall"),
        traceId = "multi-fault-test",
      ),
      "cmd-inject-3b",
    )
    chaosInjectHandler.execute(
      CommandParams(
        raw = mapOf("providerId" to "test:battery", "fault" to "delay", "delayMs" to "500"),
        traceId = "multi-fault-test",
      ),
      "cmd-inject-3c",
    )

    // Capture one snapshot
    captureSnapshotHandler.execute(
      CommandParams(
        raw = mapOf("reason" to "multi-fault"),
        traceId = "multi-fault-test",
      ),
      "cmd-capture-3",
    )

    // List diagnostics since before injections
    val listResult = listDiagnosticsHandler.execute(
      CommandParams(
        raw = mapOf("since" to beforeMs.toString()),
        traceId = "multi-fault-test",
      ),
      "cmd-list-3",
    )
    assertThat(listResult).isInstanceOf(CommandResult.Success::class.java)
    val listJson = Json.parseToJsonElement((listResult as CommandResult.Success).data).jsonObject

    // Multiple faults don't cause multiple snapshots -- only explicit capture-snapshot does
    assertThat(listJson["count"]!!.jsonPrimitive.int).isEqualTo(1)
  }
}
