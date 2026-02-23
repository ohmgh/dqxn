package app.dqxn.android.sdk.observability.health

import android.os.Debug
import android.os.Handler
import android.os.Looper
import app.dqxn.android.sdk.observability.diagnostic.AnomalyTrigger
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.error
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Daemon thread that detects ANR (Application Not Responding) conditions by posting a
 * [CountDownLatch] countdown to the main thread handler and awaiting completion within [timeoutMs].
 *
 * Detection requires 2 consecutive misses. Single miss is tolerated (GC pause, brief contention).
 * Debugger attachment suppresses detection (breakpoints cause expected delays).
 *
 * Uses direct [FileOutputStream] for ANR file writes -- no coroutine dispatching. Process may die
 * before `Dispatchers.IO` schedules.
 */
public class AnrWatchdog(
  private val diagnosticCapture: DiagnosticSnapshotCapture,
  private val logger: DqxnLogger,
  private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
  private val anrDirectory: File? = null,
  private val debuggerCheck: () -> Boolean = { Debug.isDebuggerConnected() },
  private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {

  private var watchdogThread: Thread? = null
  private val lastAnrInfo: AtomicReference<AnrInfo?> = AtomicReference(null)

  @Volatile private var running: Boolean = false

  /** Starts the ANR watchdog daemon thread. */
  public fun start() {
    if (running) return
    running = true

    watchdogThread =
      Thread(
          {
            var consecutiveMisses = 0

            while (running) {
              // Check debugger suppression
              if (debuggerCheck()) {
                consecutiveMisses = 0
                try {
                  Thread.sleep(timeoutMs)
                } catch (_: InterruptedException) {
                  break
                }
                continue
              }

              val latch = CountDownLatch(1)
              mainHandler.post { latch.countDown() }

              val responded =
                try {
                  latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                  break
                }

              if (responded) {
                consecutiveMisses = 0
              } else {
                consecutiveMisses++
                if (consecutiveMisses >= REQUIRED_CONSECUTIVE_MISSES) {
                  onAnrDetected()
                  consecutiveMisses = 0
                } else {
                  logger.warn(TAG) {
                    "Main thread miss $consecutiveMisses (need $REQUIRED_CONSECUTIVE_MISSES to trigger)"
                  }
                }
              }
            }
          },
          "dqxn-anr-watchdog"
        )
        .apply {
          isDaemon = true
          start()
        }

    logger.info(TAG) { "ANR watchdog started with ${timeoutMs}ms timeout" }
  }

  /** Stops the ANR watchdog daemon thread. */
  public fun stop() {
    running = false
    watchdogThread?.interrupt()
    watchdogThread = null
    logger.info(TAG) { "ANR watchdog stopped" }
  }

  /** Returns the last ANR info for agentic `/anr` path, or null if no ANR detected. */
  public fun query(): AnrInfo? = lastAnrInfo.get()

  private fun onAnrDetected() {
    val stackTraces = Thread.getAllStackTraces()
    val mainTrace =
      stackTraces.entries
        .firstOrNull { it.key.name == "main" }
        ?.value
        ?.joinToString("\n") { "  at $it" } ?: "Main thread stack unavailable"

    val fdCount = countFileDescriptors()

    val anrInfo =
      AnrInfo(
        timestamp = System.currentTimeMillis(),
        mainThreadStackTrace = mainTrace,
        fdCount = fdCount,
        threadCount = stackTraces.size,
      )
    lastAnrInfo.set(anrInfo)

    logger.error(TAG) { "ANR detected! fd=$fdCount threads=${stackTraces.size}" }

    // Write ANR file directly (no coroutine dispatch)
    writeAnrFile(anrInfo, stackTraces)

    // Trigger diagnostic capture
    diagnosticCapture.capture(
      AnomalyTrigger.AnrDetected(
        mainThreadStackTrace = mainTrace,
        fdCount = fdCount,
      )
    )
  }

  private fun writeAnrFile(anrInfo: AnrInfo, stackTraces: Map<Thread, Array<StackTraceElement>>) {
    val dir = anrDirectory ?: return
    try {
      dir.mkdirs()
      val file = File(dir, "anr_${anrInfo.timestamp}.txt")
      FileOutputStream(file).use { fos ->
        fos.write("ANR detected at ${anrInfo.timestamp}\n".toByteArray())
        fos.write("FD count: ${anrInfo.fdCount}\n".toByteArray())
        fos.write("Thread count: ${anrInfo.threadCount}\n\n".toByteArray())
        for ((thread, trace) in stackTraces) {
          fos.write("\"${thread.name}\" (${thread.state})\n".toByteArray())
          for (element in trace) {
            fos.write("  at $element\n".toByteArray())
          }
          fos.write("\n".toByteArray())
        }
      }
    } catch (e: Exception) {
      logger.error(TAG, e) { "Failed to write ANR file" }
    }
  }

  private fun countFileDescriptors(): Int {
    return try {
      val fdDir = File("/proc/self/fd")
      fdDir.listFiles()?.size ?: -1
    } catch (_: Exception) {
      -1
    }
  }

  /** Information about a detected ANR event. */
  public data class AnrInfo(
    val timestamp: Long,
    val mainThreadStackTrace: String,
    val fdCount: Int,
    val threadCount: Int,
  )

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 2500L
    const val REQUIRED_CONSECUTIVE_MISSES = 2
    val TAG = LogTag("anr-watchdog")
  }
}
