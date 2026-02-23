package app.dqxn.android.sdk.observability.log

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Machine-readable JSON-lines file log sink (F13.7).
 *
 * Writes one JSON object per line to [logDir]/dqxn.jsonl. Rotation: when file exceeds
 * [maxFileSizeBytes] (default 10MB), rotates current -> .1, .1 -> .2, deletes .3+ -- max [maxFiles]
 * backup files.
 *
 * Active in debug builds only -- returns early if [isDebugBuild] is false.
 */
public class JsonLinesLogSink(
  private val logDir: File,
  private val isDebugBuild: Boolean,
  private val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE,
  private val maxFiles: Int = DEFAULT_MAX_FILES,
) : LogSink {

  private val json: Json = Json { encodeDefaults = false }
  private val logFile: File by lazy {
    logDir.mkdirs()
    File(logDir, LOG_FILE_NAME)
  }

  override fun write(entry: LogEntry) {
    if (!isDebugBuild) return

    val jsonLine = serializeEntry(entry)

    synchronized(this) {
      rotateIfNeeded()
      logFile.appendText(jsonLine + "\n")
    }
  }

  private fun serializeEntry(entry: LogEntry): String {
    val obj = buildJsonObject {
      put("timestamp", entry.timestamp)
      put("level", entry.level.name)
      put("tag", entry.tag.value)
      put("message", entry.message)
      put("sessionId", entry.sessionId)
      if (entry.traceId != null) put("traceId", entry.traceId)
      if (entry.spanId != null) put("spanId", entry.spanId)
      if (entry.throwable != null) {
        put("stackTrace", entry.throwable.stackTraceToString())
      }
      if (entry.fields.isNotEmpty()) {
        val fieldsObj = JsonObject(entry.fields.mapValues { (_, v) -> JsonPrimitive(v.toString()) })
        put("fields", fieldsObj)
      }
    }
    return json.encodeToString(obj)
  }

  private fun rotateIfNeeded() {
    if (!logFile.exists()) return
    if (logFile.length() < maxFileSizeBytes) return

    // Rotate: .2 -> .3 (delete), .1 -> .2, current -> .1
    for (i in maxFiles downTo 1) {
      val source = backupFile(i - 1)
      val target = backupFile(i)
      if (i == maxFiles) {
        target.delete()
      }
      if (source.exists()) {
        source.renameTo(target)
      }
    }
    // Rename current to .1
    val firstBackup = backupFile(1)
    logFile.renameTo(firstBackup)
  }

  private fun backupFile(index: Int): File {
    return if (index == 0) logFile else File(logDir, "$LOG_FILE_NAME.$index")
  }

  private companion object {
    const val LOG_FILE_NAME = "dqxn.jsonl"
    const val DEFAULT_MAX_FILE_SIZE = 10L * 1024 * 1024 // 10 MB
    const val DEFAULT_MAX_FILES = 3
  }
}
