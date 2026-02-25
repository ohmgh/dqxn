package app.dqxn.android.app.localization

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Guard test ensuring the HardcodedText lint check stays configured as error severity in convention
 * plugins, and that production Compose source files do not introduce NEW hardcoded user-facing text.
 *
 * The primary enforcement mechanism is Android lint's built-in HardcodedText check elevated to
 * error severity. This test is a secondary guard that:
 * 1. Verifies the convention plugin config persists
 * 2. Heuristically scans production Compose source for Text("literal") patterns
 *
 * The grep-based scan is heuristic, not exhaustive -- the lint gate is the primary enforcement.
 * Pre-existing violations are tracked in a known baseline. The test fails only if NEW violations
 * appear beyond the baseline count, preventing regression.
 */
@Tag("fast")
class HardcodedTextLintTest {

  /**
   * Finds the project root by walking up from the working directory until we find
   * `build-logic/convention`.
   */
  private fun findProjectRoot(): File {
    var dir = File(System.getProperty("user.dir"))
    while (dir.parentFile != null) {
      if (File(dir, "build-logic/convention").isDirectory) return dir
      dir = dir.parentFile
    }
    error("Could not find project root with build-logic/convention")
  }

  @Test
  fun `application convention plugin has HardcodedText error severity`() {
    val root = findProjectRoot()
    val pluginFile =
      File(
        root,
        "build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt",
      )
    assertWithMessage("AndroidApplicationConventionPlugin.kt must exist")
      .that(pluginFile.exists())
      .isTrue()

    val content = pluginFile.readText()
    assertWithMessage(
        "AndroidApplicationConventionPlugin must configure HardcodedText as error severity"
      )
      .that(content)
      .contains("error.add(\"HardcodedText\")")
  }

  @Test
  fun `library convention plugin has HardcodedText error severity`() {
    val root = findProjectRoot()
    val pluginFile =
      File(
        root,
        "build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt",
      )
    assertWithMessage("AndroidLibraryConventionPlugin.kt must exist")
      .that(pluginFile.exists())
      .isTrue()

    val content = pluginFile.readText()
    assertWithMessage(
        "AndroidLibraryConventionPlugin must configure HardcodedText as error severity"
      )
      .that(content)
      .contains("error.add(\"HardcodedText\")")
  }

  @Test
  fun `production compose source has no new hardcoded Text calls beyond baseline`() {
    val root = findProjectRoot()

    // Directories to scan -- excludes diagnostics (debug overlay UI) and test sources
    val scanDirs =
      listOf(
        "feature/dashboard/src/main",
        "feature/settings/src/main",
        "feature/onboarding/src/main",
        "app/src/main",
        "pack/*/src/main",
        "core/*/src/main",
      )

    val violations = mutableListOf<String>()

    for (pattern in scanDirs) {
      val parts = pattern.split("*")
      val baseDir = File(root, parts.first())
      if (!baseDir.exists()) continue

      val dirs =
        if (parts.size > 1) {
          baseDir
            .listFiles { f -> f.isDirectory }
            ?.flatMap { child ->
              val suffix = parts.drop(1).joinToString("*")
              val target = File(child, suffix)
              if (target.isDirectory) listOf(target) else emptyList()
            }
            ?: emptyList()
        } else {
          listOf(baseDir)
        }

      for (dir in dirs) {
        dir
          .walkTopDown()
          .filter { it.extension == "kt" }
          .forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
              val trimmed = line.trim()
              if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed

              // Only match Compose Text() composable calls, not Canvas drawText
              if (COMPOSE_TEXT_PATTERN.containsMatchIn(line) &&
                !CANVAS_DRAW_TEXT_PATTERN.containsMatchIn(line)
              ) {
                val matchValue =
                  COMPOSE_TEXT_PATTERN.find(line)?.groupValues?.getOrNull(1) ?: ""
                if (!isSafeHardcodedText(matchValue)) {
                  val relativePath = file.relativeTo(root).path
                  violations.add("$relativePath:${index + 1}: $trimmed")
                }
              }
            }
          }
      }
    }

    // Baseline: known pre-existing hardcoded text violations tracked for future cleanup.
    // The lint gate (error.add("HardcodedText")) is the primary enforcement for XML layouts.
    // These Compose Text() calls don't trigger HardcodedText lint (it only checks XML).
    // Tracked here as a regression gate -- new violations beyond this count will fail.
    assertWithMessage(
        "Hardcoded Text() violations exceed known baseline ($KNOWN_BASELINE_COUNT). " +
          "New violations found -- use stringResource() for user-facing text.\n" +
          "Current violations (${violations.size}):\n${violations.joinToString("\n")}"
      )
      .that(violations.size)
      .isAtMost(KNOWN_BASELINE_COUNT)
  }

  companion object {
    /**
     * Known pre-existing hardcoded text count across feature/settings, feature/dashboard, and
     * pack modules. This count should only decrease as violations are fixed.
     */
    private const val KNOWN_BASELINE_COUNT = 14

    /** Matches Compose Text() composable calls with hardcoded string literals. */
    private val COMPOSE_TEXT_PATTERN =
      Regex("""Text\(\s*(?:text\s*=\s*)?"([^"]*)"[^)]*\)""")

    /** Matches Canvas drawText calls (not Compose Text composable). */
    private val CANVAS_DRAW_TEXT_PATTERN = Regex("""\.drawText\(""")

    /**
     * Returns true if the matched text value is a safe/expected hardcoded string that doesn't
     * need localization.
     */
    private fun isSafeHardcodedText(value: String): Boolean {
      if (value.isBlank()) return true
      // Placeholder text for no-data states (e.g., "--", "--:--")
      if (value.matches(Regex("^[-:]+$"))) return true
      // Single punctuation or separator characters
      if (value.length <= 2 && !value.any { it.isLetter() }) return true
      return false
    }
  }
}
