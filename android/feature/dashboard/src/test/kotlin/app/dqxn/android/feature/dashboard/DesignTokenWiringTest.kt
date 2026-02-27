package app.dqxn.android.feature.dashboard

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.jupiter.api.Test

/**
 * Source-scanning tests verifying :feature:dashboard uses dashboard design tokens instead of
 * MaterialTheme.
 *
 * Allowlisted files are owned by other Phase 14 plans and will be migrated separately. Remove
 * allowlist entries as their owning plans execute.
 */
class DesignTokenWiringTest {

  // Gradle sets user.dir to the module root (android/feature/dashboard)
  private val sourceDir = File(System.getProperty("user.dir"), "src/main/kotlin")

  /**
   * Files with legitimate MaterialTheme usage pending migration by other Phase 14 plans.
   * - ConfirmationDialog.kt -- used by widget delete/reset flows, migrated by plan 14-07
   * - WidgetErrorFallback.kt -- widget crash fallback UI, migrated by plan 14-04
   * - DashboardButtonBar.kt -- bottom bar styling, migrated by plan 14-02
   * - CriticalBannerHost.kt -- thermal/critical banner UI, migrated by plan 14-05
   * - NotificationBannerHost.kt -- notification banner UI, migrated by plan 14-05
   * - WidgetStatusOverlay.kt -- widget status overlays, migrated by plan 14-04
   */
  private val allowlist =
    setOf(
      "ConfirmationDialog.kt",
      "WidgetErrorFallback.kt",
      "DashboardButtonBar.kt",
      "CriticalBannerHost.kt",
      "NotificationBannerHost.kt",
      "WidgetStatusOverlay.kt",
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
  fun `no MaterialTheme typography in dashboard source`() {
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
  fun `no MaterialTheme colorScheme in dashboard source`() {
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
  fun `no MaterialTheme import in dashboard source`() {
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
