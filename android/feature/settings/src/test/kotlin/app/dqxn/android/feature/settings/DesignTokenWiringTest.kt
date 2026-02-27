package app.dqxn.android.feature.settings

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.jupiter.api.Test

/**
 * Source-scanning tests verifying :feature:settings uses dashboard design tokens
 * (DashboardTypography, DashboardSpacing, LocalDashboardTheme) instead of MaterialTheme.
 *
 * Allowlisted files are owned by other plans and will be migrated separately.
 * Remove allowlist entries as their owning plans execute.
 */
class DesignTokenWiringTest {

    // Gradle sets user.dir to the module root (android/feature/settings)
    private val sourceDir = File(
        System.getProperty("user.dir"),
        "src/main/kotlin"
    )

    /**
     * Files with legitimate MaterialTheme usage pending migration by other plans.
     * Each entry documents the owning plan. Remove after plan execution.
     */
    private val allowlist = setOf(
        "NfD1Disclaimer.kt",  // Disclaimer dialog -- not in Phase 14 scope
    )

    private fun scanSourceFiles(): List<File> {
        check(sourceDir.isDirectory) { "Source dir not found: ${sourceDir.absolutePath}" }
        return sourceDir.walk()
            .filter { it.extension == "kt" }
            .filter { it.name !in allowlist }
            .toList()
    }

    @Test
    fun `no MaterialTheme typography in settings source`() {
        scanSourceFiles().forEach { file ->
            val content = file.readText()
            assertWithMessage("${file.name} should not use MaterialTheme.typography -- use DashboardTypography instead")
                .that(content)
                .doesNotContain("MaterialTheme.typography")
        }
    }

    @Test
    fun `no MaterialTheme colorScheme in settings source`() {
        scanSourceFiles().forEach { file ->
            val content = file.readText()
            assertWithMessage("${file.name} should not use MaterialTheme.colorScheme -- use LocalDashboardTheme instead")
                .that(content)
                .doesNotContain("MaterialTheme.colorScheme")
        }
    }

    @Test
    fun `no MaterialTheme import in settings source`() {
        scanSourceFiles().forEach { file ->
            val content = file.readText()
            assertWithMessage("${file.name} should not import MaterialTheme")
                .that(content)
                .doesNotContain("import androidx.compose.material3.MaterialTheme")
        }
    }

    @Test
    fun `allowlist files actually exist`() {
        // Verify allowlisted files exist, preventing stale entries
        allowlist.forEach { fileName ->
            val found = sourceDir.walk().any { it.name == fileName }
            assertWithMessage("Allowlisted file $fileName should exist in source tree")
                .that(found)
                .isTrue()
        }
    }

    @Test
    fun `allowlist is minimal -- verify files still need MaterialTheme`() {
        // If an allowlisted file no longer uses MaterialTheme, the entry is stale -- remove it
        allowlist.forEach { fileName ->
            val file = sourceDir.walk().first { it.name == fileName }
            val content = file.readText()
            val usesMaterialTheme = content.contains("MaterialTheme.typography") ||
                content.contains("MaterialTheme.colorScheme") ||
                content.contains("import androidx.compose.material3.MaterialTheme")
            assertWithMessage("Allowlisted $fileName should still use MaterialTheme -- remove from allowlist if migrated")
                .that(usesMaterialTheme)
                .isTrue()
        }
    }
}
