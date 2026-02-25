package app.dqxn.android.agentic

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.os.bundleOf
import app.dqxn.android.core.agentic.AgenticCommandRouter
import app.dqxn.android.core.agentic.CommandParams
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ContentProvider transport for the agentic diagnostic framework. Dispatches ADB
 * `content call` commands to [AgenticCommandRouter] via Hilt [EntryPoint] access.
 *
 * Uses response-file protocol: all responses are written to a temp file in cacheDir, and the
 * returned [Bundle] contains only the file path. This eliminates Binder transaction size limits.
 *
 * Available in debug and benchmark builds via `src/agentic/` shared source set.
 * Not included in release APK (NF21).
 */
internal class AgenticContentProvider : ContentProvider() {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface AgenticEntryPoint {
    fun commandRouter(): AgenticCommandRouter
  }

  override fun onCreate(): Boolean {
    // Clean up previous session response files
    context?.cacheDir?.listFiles { f -> f.name.startsWith("agentic_") }?.forEach { it.delete() }
    return true
  }

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
    val appContext = context?.applicationContext
      ?: return writeErrorResponse("No application context", "NO_CONTEXT")

    val router = try {
      EntryPointAccessors.fromApplication(appContext, AgenticEntryPoint::class.java)
        .commandRouter()
    } catch (_: IllegalStateException) {
      // Cold-start race: Hilt not yet initialized
      return writeErrorResponse("App initializing, retry after ping", "COLD_START")
    }

    return handleCall(method, arg, router, appContext.cacheDir)
  }

  /**
   * Core dispatch logic extracted for testability. Takes explicit dependencies rather than reading
   * from ContentProvider state.
   *
   * @param timeoutMs Command execution timeout in milliseconds. Defaults to [TIMEOUT_MS] (8s).
   *   Tests pass a shorter value to avoid long waits.
   */
  internal fun handleCall(
    method: String,
    arg: String?,
    router: AgenticCommandRouter,
    cacheDir: File,
    timeoutMs: Long = TIMEOUT_MS,
  ): Bundle {
    val params = parseParams(arg)
    val traceId = "agentic-${SystemClock.elapsedRealtimeNanos()}"
    val commandParams = CommandParams(raw = params, traceId = traceId)

    val responseJson = try {
      // runBlocking is allowed in debug agentic code per CLAUDE.md
      @Suppress("BlockingMethodInNonBlockingContext")
      runBlocking(Dispatchers.Default) {
        withTimeout(timeoutMs) {
          router.route(method, commandParams)
        }
      }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
      """{"status":"error","message":"Command timed out after ${timeoutMs / 1000}s","code":"TIMEOUT"}"""
    } catch (e: Exception) {
      """{"status":"error","message":"${e.message?.replace("\"", "\\\"")}","code":"HANDLER_ERROR"}"""
    }

    return writeResponseFile(responseJson, cacheDir)
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? {
    // Lock-free direct read paths for deadlock-safe escape hatches
    val pathSegment = uri.pathSegments?.firstOrNull() ?: return null
    return when (pathSegment) {
      "health" -> null // Placeholder: populated when WidgetHealthMonitor wired
      "anr" -> null // Placeholder: populated when AnrWatchdog wired
      else -> null
    }
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun getType(uri: Uri): String? = null

  private fun parseParams(arg: String?): Map<String, String> {
    if (arg.isNullOrBlank()) return emptyMap()
    return try {
      val jsonElement = Json.parseToJsonElement(arg)
      if (jsonElement is JsonObject) {
        jsonElement.mapValues { (_, value) -> value.jsonPrimitive.content }
      } else {
        emptyMap()
      }
    } catch (_: Exception) {
      emptyMap()
    }
  }

  private fun writeResponseFile(json: String, cacheDir: File): Bundle {
    val file = File.createTempFile("agentic_", ".json", cacheDir)
    file.writeText(json)
    return bundleOf("filePath" to file.absolutePath)
  }

  private fun writeErrorResponse(message: String, code: String): Bundle {
    val errorJson = """{"status":"error","message":"$message","code":"$code"}"""
    val cacheDir = context?.cacheDir ?: return bundleOf(
      "filePath" to "",
      "error" to errorJson,
    )
    return writeResponseFile(errorJson, cacheDir)
  }

  private companion object {
    const val TIMEOUT_MS = 8_000L
  }
}
