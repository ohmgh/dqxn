package app.dqxn.android.feature.onboarding

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.jupiter.api.Test

/**
 * Source-scanning tests verifying :feature:onboarding uses dashboard design tokens instead of
 * MaterialTheme.
 *
 * Onboarding files (FirstRunFlow, AnalyticsConsentStep, FirstLaunchDisclaimer) deferred to Phase 15
 * (setup schema framework integration). All 4 onboarding files allowlisted.
 */
class DesignTokenWiringTest {

  // Gradle sets user.dir to the module root (android/feature/onboarding)
  private val sourceDir = File(System.getProperty("user.dir"), "src/main/kotlin")

  /**
   * Files with legitimate MaterialTheme usage pending migration.
   * - FirstRunFlow.kt -- deferred to Phase 15 (setup schema framework integration)
   * - AnalyticsConsentStep.kt -- deferred to Phase 15
   * - FirstLaunchDisclaimer.kt -- deferred to Phase 15
   * - ProgressiveTip.kt -- tooltip composable, not in Phase 14 scope
   */
  private val allowlist =
    setOf(
      "FirstRunFlow.kt",
      "AnalyticsConsentStep.kt",
      "FirstLaunchDisclaimer.kt",
      "ProgressiveTip.kt",
    )

  private fun scanSourceFiles(): List<File> {
    check(sourceDir.isDirectory) { "Source dir not found: ${sourceDir.absolutePath}" }
    return sourceDir
      .walk()
      .filter { it.extension == "kt" }
      .filter { it.name !in allowlist }
      .toList()
  }

  @Test
  fun `no MaterialTheme typography in onboarding source`() {
    scanSourceFiles().forEach { file ->
      val content = file.readText()
      assertWithMessage(
          "${file.name} should not use MaterialTheme.typography -- use DashboardTypography instead"
        )
        .that(content)
        .doesNotContain("MaterialTheme.typography")
    }
  }

  @Test
  fun `no MaterialTheme colorScheme in onboarding source`() {
    scanSourceFiles().forEach { file ->
      val content = file.readText()
      assertWithMessage(
          "${file.name} should not use MaterialTheme.colorScheme -- use LocalDashboardTheme instead"
        )
        .that(content)
        .doesNotContain("MaterialTheme.colorScheme")
    }
  }

  @Test
  fun `no MaterialTheme import in onboarding source`() {
    scanSourceFiles().forEach { file ->
      val content = file.readText()
      assertWithMessage("${file.name} should not import MaterialTheme")
        .that(content)
        .doesNotContain("import androidx.compose.material3.MaterialTheme")
    }
  }

  @Test
  fun `allowlist files actually exist`() {
    allowlist.forEach { fileName ->
      val found = sourceDir.walk().any { it.name == fileName }
      assertWithMessage("Allowlisted file $fileName should exist in source tree")
        .that(found)
        .isTrue()
    }
  }

  @Test
  fun `allowlist is minimal -- verify files still need MaterialTheme`() {
    allowlist.forEach { fileName ->
      val file = sourceDir.walk().first { it.name == fileName }
      val content = file.readText()
      val usesMaterialTheme =
        content.contains("MaterialTheme.typography") ||
          content.contains("MaterialTheme.colorScheme") ||
          content.contains("import androidx.compose.material3.MaterialTheme")
      assertWithMessage(
          "Allowlisted $fileName should still use MaterialTheme -- remove from allowlist if migrated"
        )
        .that(usesMaterialTheme)
        .isTrue()
    }
  }
}
