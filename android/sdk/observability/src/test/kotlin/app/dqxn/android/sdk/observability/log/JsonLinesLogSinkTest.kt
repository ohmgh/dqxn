package app.dqxn.android.sdk.observability.log

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JsonLinesLogSinkTest {

  @TempDir lateinit var tempDir: File

  private val json = Json { ignoreUnknownKeys = true }

  private fun makeEntry(
    message: String = "test",
    throwable: Throwable? = null,
  ): LogEntry =
    LogEntry(
      timestamp = 1_000_000L,
      level = LogLevel.INFO,
      tag = LogTag("test"),
      message = message,
      throwable = throwable,
      sessionId = "session-1",
    )

  @Test
  fun `writes valid JSON lines`() {
    val logDir = File(tempDir, "logs")
    val sink = JsonLinesLogSink(logDir = logDir, isDebugBuild = true)

    sink.write(makeEntry("first"))
    sink.write(makeEntry("second"))
    sink.write(makeEntry("third"))

    val lines = File(logDir, "dqxn.jsonl").readLines()
    assertThat(lines).hasSize(3)

    for (line in lines) {
      val obj = json.parseToJsonElement(line).jsonObject
      assertThat(obj.containsKey("timestamp")).isTrue()
      assertThat(obj.containsKey("level")).isTrue()
      assertThat(obj.containsKey("tag")).isTrue()
      assertThat(obj.containsKey("message")).isTrue()
      assertThat(obj.containsKey("sessionId")).isTrue()
    }

    assertThat(lines[0]).contains("\"first\"")
    assertThat(lines[1]).contains("\"second\"")
    assertThat(lines[2]).contains("\"third\"")
  }

  @Test
  fun `rotates at size limit`() {
    val logDir = File(tempDir, "logs")
    // Use a tiny max size to trigger rotation
    val sink =
      JsonLinesLogSink(
        logDir = logDir,
        isDebugBuild = true,
        maxFileSizeBytes = 100L,
      )

    // Write enough entries to exceed 100 bytes
    repeat(20) { i -> sink.write(makeEntry("message-$i-padding-to-make-it-longer")) }

    val backupFile = File(logDir, "dqxn.jsonl.1")
    assertThat(backupFile.exists()).isTrue()
  }

  @Test
  fun `max 3 backup files`() {
    val logDir = File(tempDir, "logs")
    // Use a tiny max size to force many rotations
    val sink =
      JsonLinesLogSink(
        logDir = logDir,
        isDebugBuild = true,
        maxFileSizeBytes = 50L,
        maxFiles = 3,
      )

    // Write many entries to force multiple rotations
    repeat(100) { i -> sink.write(makeEntry("message-$i-padding-to-make-it-longer-still")) }

    // Should have at most: dqxn.jsonl, dqxn.jsonl.1, dqxn.jsonl.2, dqxn.jsonl.3
    val file4 = File(logDir, "dqxn.jsonl.4")
    assertThat(file4.exists()).isFalse()

    // .3 should be the max backup
    val filesInDir = logDir.listFiles()?.map { it.name } ?: emptyList()
    val jsonlFiles = filesInDir.filter { it.startsWith("dqxn.jsonl") }
    assertThat(jsonlFiles.size).isAtMost(4) // main + 3 backups
  }

  @Test
  fun `disabled in non-debug builds`() {
    val logDir = File(tempDir, "logs")
    val sink = JsonLinesLogSink(logDir = logDir, isDebugBuild = false)

    sink.write(makeEntry("should not write"))

    val logFile = File(logDir, "dqxn.jsonl")
    assertThat(logFile.exists()).isFalse()
  }

  @Test
  fun `throwable serialized as stackTrace string`() {
    val logDir = File(tempDir, "logs")
    val sink = JsonLinesLogSink(logDir = logDir, isDebugBuild = true)
    val exception = RuntimeException("boom")

    sink.write(makeEntry("error", throwable = exception))

    val lines = File(logDir, "dqxn.jsonl").readLines()
    assertThat(lines).hasSize(1)

    val obj = json.parseToJsonElement(lines[0]).jsonObject
    assertThat(obj.containsKey("stackTrace")).isTrue()
    val stackTrace = obj["stackTrace"]!!.jsonPrimitive.content
    assertThat(stackTrace).contains("RuntimeException")
    assertThat(stackTrace).contains("boom")

    // Should NOT have a "throwable" key -- we serialize as stackTrace string
    assertThat(obj.containsKey("throwable")).isFalse()
  }
}
