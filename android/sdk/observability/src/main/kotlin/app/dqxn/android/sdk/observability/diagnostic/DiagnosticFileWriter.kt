package app.dqxn.android.sdk.observability.diagnostic

import android.os.StatFs
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.warn
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages diagnostic snapshot file persistence with pool-based rotation. Three pools: crash (max
 * 20), thermal (max 10), perf (max 10). Oldest files evicted when pool capacity reached.
 */
public class DiagnosticFileWriter(
  private val baseDirectory: File,
  private val logger: DqxnLogger,
) {

  private val json = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
  }

  /** Writes a serializable representation of [snapshot] to the appropriate pool directory. */
  public fun write(snapshot: DiagnosticSnapshot, pool: String) {
    val poolDir = poolDirectory(pool)
    poolDir.mkdirs()

    val maxFiles = poolCapacity(pool)
    val existingFiles = poolDir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()

    // Evict oldest files if at capacity
    val toEvict = existingFiles.size - maxFiles + 1
    if (toEvict > 0) {
      existingFiles.take(toEvict).forEach { it.delete() }
    }

    val fileName = "${pool}_${snapshot.timestamp}_${snapshot.id}.json"
    val file = File(poolDir, fileName)

    val content =
      json.encodeToString(
        DiagnosticSnapshotDto(
          id = snapshot.id,
          timestamp = snapshot.timestamp,
          triggerType = snapshot.trigger::class.simpleName ?: "Unknown",
          triggerDescription = snapshot.trigger.toString(),
          agenticTraceId = snapshot.agenticTraceId,
          activeSpans = snapshot.activeSpans.toList(),
          logTail = snapshot.logTail.toList(),
        )
      )

    file.writeText(content)
  }

  /** Reads all snapshots from the given pool (or all pools if null). */
  public fun read(pool: String? = null): List<DiagnosticSnapshotDto> {
    val pools = if (pool != null) listOf(pool) else POOLS
    return pools.flatMap { poolName ->
      val poolDir = poolDirectory(poolName)
      if (!poolDir.exists()) return@flatMap emptyList()
      poolDir
        .listFiles { file -> file.extension == "json" }
        ?.mapNotNull { file ->
          try {
            json.decodeFromString<DiagnosticSnapshotDto>(file.readText())
          } catch (_: Exception) {
            null
          }
        } ?: emptyList()
    }
  }

  /**
   * Returns true if storage is under pressure (<10MB free), suggesting capture should be skipped.
   */
  public fun checkStoragePressure(): Boolean {
    return try {
      val stat = StatFs(baseDirectory.absolutePath)
      val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
      availableBytes < MIN_FREE_BYTES
    } catch (_: Exception) {
      logger.warn(TAG) { "Failed to check storage pressure, assuming adequate space" }
      false
    }
  }

  /** Returns the number of files in the given pool directory. */
  public fun fileCount(pool: String): Int {
    val poolDir = poolDirectory(pool)
    return poolDir.listFiles()?.size ?: 0
  }

  private fun poolDirectory(pool: String): File = File(baseDirectory, pool)

  private fun poolCapacity(pool: String): Int =
    when (pool) {
      POOL_CRASH -> MAX_CRASH_FILES
      POOL_THERMAL -> MAX_THERMAL_FILES
      POOL_PERF -> MAX_PERF_FILES
      else -> MAX_PERF_FILES
    }

  public companion object {
    public const val POOL_CRASH: String = "crash"
    public const val POOL_THERMAL: String = "thermal"
    public const val POOL_PERF: String = "perf"

    internal val POOLS: List<String> = listOf(POOL_CRASH, POOL_THERMAL, POOL_PERF)
    internal const val MAX_CRASH_FILES: Int = 20
    internal const val MAX_THERMAL_FILES: Int = 10
    internal const val MAX_PERF_FILES: Int = 10
    internal const val MIN_FREE_BYTES: Long = 10L * 1024 * 1024 // 10MB

    private val TAG = LogTag("diagnostic-file-writer")
  }
}

/** Serializable DTO for diagnostic snapshot file persistence. */
@kotlinx.serialization.Serializable
public data class DiagnosticSnapshotDto(
  val id: String,
  val timestamp: Long,
  val triggerType: String,
  val triggerDescription: String,
  val agenticTraceId: String? = null,
  val activeSpans: List<String> = emptyList(),
  val logTail: List<String> = emptyList(),
)
