package app.dqxn.android.app.performance

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.jupiter.api.Test

/**
 * Source-code analysis test verifying correct sensor lifecycle management in callbackFlow providers.
 *
 * For each provider source file that uses `callbackFlow`:
 * 1. Every `callbackFlow` block must have a matching `awaitClose`
 * 2. Every `awaitClose` body must contain an unregister call (unregisterListener, removeUpdates,
 *    removeCallbacks, unregisterReceiver, removeLocationUpdates)
 * 3. No bare `launch {}` inside `callbackFlow` that could escape cancellation
 *
 * This ensures no sensor/receiver leaks on coroutine cancellation (NF37 background drain).
 */
class SensorUnregistrationTest {

  /**
   * Provider source files that use callbackFlow for sensor/callback registration.
   * Paths relative to the android/ project root.
   */
  private val providerFiles: List<String> = listOf(
    "pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/providers/GpsSpeedProvider.kt",
    "pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/providers/AccelerometerProvider.kt",
    "pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/providers/AmbientLightDataProvider.kt",
    "pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/providers/OrientationDataProvider.kt",
    "pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/providers/BatteryProvider.kt",
    "pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/providers/SolarLocationDataProvider.kt",
    "pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/providers/SolarTimezoneDataProvider.kt",
    "pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/providers/TimeDataProvider.kt",
  )

  /**
   * Valid unregistration patterns inside awaitClose blocks.
   * Each callbackFlow that registers a listener/receiver/callback must unregister in awaitClose.
   */
  private val unregistrationPatterns: List<Regex> = listOf(
    Regex("""unregisterListener"""),
    Regex("""removeUpdates"""),
    Regex("""removeCallbacks"""),
    Regex("""unregisterReceiver"""),
    Regex("""removeLocationUpdates"""),
  )

  @Test
  fun `every callbackFlow has matching awaitClose with unregistration`() {
    for (relativePath in providerFiles) {
      val file = resolveProviderFile(relativePath)
      assertWithMessage("Provider file must exist: $relativePath")
        .that(file.exists())
        .isTrue()

      val source = file.readText()
      val fileName = file.name

      // Strip import lines to avoid false positives
      val sourceWithoutImports = source.lines()
        .filter { !it.trimStart().startsWith("import ") }
        .joinToString("\n")

      val callbackFlowCount = Regex("""callbackFlow\s*\{""").findAll(sourceWithoutImports).count()
      val awaitCloseCount = Regex("""awaitClose\s*\{""").findAll(sourceWithoutImports).count()

      assertWithMessage("$fileName: callbackFlow count ($callbackFlowCount) must equal awaitClose count ($awaitCloseCount)")
        .that(awaitCloseCount)
        .isEqualTo(callbackFlowCount)

      // Verify each awaitClose body contains an unregister call
      val awaitCloseBlocks = extractAwaitCloseBlocks(sourceWithoutImports)
      for ((index, block) in awaitCloseBlocks.withIndex()) {
        val hasUnregistration = unregistrationPatterns.any { it.containsMatchIn(block) }
        assertWithMessage(
          "$fileName: awaitClose block #${index + 1} must contain an unregistration call. " +
            "Found: '$block'"
        )
          .that(hasUnregistration)
          .isTrue()
      }
    }
  }

  @Test
  fun `no bare launch inside callbackFlow`() {
    for (relativePath in providerFiles) {
      val file = resolveProviderFile(relativePath)
      if (!file.exists()) continue

      val source = file.readText()
      val fileName = file.name

      // Extract callbackFlow block bodies (approximate: from callbackFlow { to matching })
      val callbackFlowBodies = extractCallbackFlowBodies(source)

      for ((index, body) in callbackFlowBodies.withIndex()) {
        // Look for bare `launch {` or `launch(` that could escape cancellation scope.
        // Allowed: `trySend`, `awaitClose`, `close()`
        // Disallowed: `launch {`, `launch(` (coroutine that escapes callbackFlow scope)
        val hasBareLaunch = Regex("""\blaunch\s*[({]""").containsMatchIn(body)
        assertWithMessage(
          "$fileName: callbackFlow block #${index + 1} must not contain bare launch {} " +
            "that could escape cancellation. Use trySend/awaitClose only."
        )
          .that(hasBareLaunch)
          .isFalse()
      }
    }
  }

  @Test
  fun `all declared provider files exist and use callbackFlow`() {
    for (relativePath in providerFiles) {
      val file = resolveProviderFile(relativePath)
      assertWithMessage("Provider file must exist: $relativePath")
        .that(file.exists())
        .isTrue()

      val source = file.readText()
      val hasCallbackFlow = source.contains("callbackFlow")
      assertWithMessage("${file.name} must use callbackFlow")
        .that(hasCallbackFlow)
        .isTrue()
    }
  }

  /**
   * Resolves a provider file path relative to the android/ directory.
   * Handles both Gradle test execution (working dir = android/) and IDE execution.
   */
  private fun resolveProviderFile(relativePath: String): File {
    // Try relative to current working dir first (Gradle runs from android/)
    val fromCwd = File(relativePath)
    if (fromCwd.exists()) return fromCwd

    // Try relative to parent (IDE might run from android/app/)
    val fromParent = File("../$relativePath")
    if (fromParent.exists()) return fromParent

    // Try absolute path from project root marker
    val projectRoot = findProjectRoot()
    if (projectRoot != null) {
      val fromRoot = File(projectRoot, relativePath)
      if (fromRoot.exists()) return fromRoot
    }

    return fromCwd // Return CWD-relative for error message
  }

  private fun findProjectRoot(): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: return null)
    while (dir?.parentFile != null) {
      if (File(dir, "settings.gradle.kts").exists()) return dir
      dir = dir.parentFile
    }
    return null
  }

  /**
   * Extracts the body text of each `awaitClose { ... }` block.
   * Handles single-line `awaitClose { foo() }` and multi-line blocks.
   */
  private fun extractAwaitCloseBlocks(source: String): List<String> {
    val blocks = mutableListOf<String>()
    val pattern = Regex("""awaitClose\s*\{""")
    var searchFrom = 0

    while (true) {
      val match = pattern.find(source, searchFrom) ?: break
      val braceStart = match.range.last
      val blockEnd = findMatchingBrace(source, braceStart)
      if (blockEnd > braceStart) {
        blocks.add(source.substring(braceStart + 1, blockEnd))
      }
      searchFrom = if (blockEnd > braceStart) blockEnd + 1 else match.range.last + 1
    }

    return blocks
  }

  /**
   * Extracts the body text of each `callbackFlow { ... }` block.
   */
  private fun extractCallbackFlowBodies(source: String): List<String> {
    val bodies = mutableListOf<String>()
    val pattern = Regex("""callbackFlow\s*\{""")
    var searchFrom = 0

    while (true) {
      val match = pattern.find(source, searchFrom) ?: break
      val braceStart = match.range.last
      val blockEnd = findMatchingBrace(source, braceStart)
      if (blockEnd > braceStart) {
        bodies.add(source.substring(braceStart + 1, blockEnd))
      }
      searchFrom = if (blockEnd > braceStart) blockEnd + 1 else match.range.last + 1
    }

    return bodies
  }

  /**
   * Finds the index of the closing brace matching the opening brace at [openIndex].
   * Handles nested braces, string literals, and single-line comments.
   */
  private fun findMatchingBrace(source: String, openIndex: Int): Int {
    var depth = 1
    var i = openIndex + 1
    while (i < source.length && depth > 0) {
      when (source[i]) {
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) return i
        }
        '"' -> {
          // Skip string literal
          i++
          while (i < source.length && source[i] != '"') {
            if (source[i] == '\\') i++ // Skip escaped char
            i++
          }
        }
        '/' -> {
          if (i + 1 < source.length && source[i + 1] == '/') {
            // Skip single-line comment
            while (i < source.length && source[i] != '\n') i++
          }
        }
      }
      i++
    }
    return i
  }
}
