import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Validates that the version catalog (libs.versions.toml) contains all required entries for the
 * build infrastructure. This is a file-parsing test -- no Gradle runner needed.
 */
class VersionCatalogCompletenessTest {

  private lateinit var catalogContent: String

  @BeforeEach
  fun setup() {
    // Navigate from build-logic/convention working dir to the version catalog
    val catalogFile =
      File(System.getProperty("user.dir")).resolve("../../gradle/libs.versions.toml").canonicalFile
    assertThat(catalogFile.exists()).isTrue()
    catalogContent = catalogFile.readText()
  }

  @Test
  fun `catalog contains all required version entries`() {
    val requiredVersions =
      listOf(
        "agp",
        "kotlin",
        "ksp",
        "hilt",
        "compose-bom",
        "coroutines",
        "kotlinx-serialization",
        "kotlinx-collections-immutable",
        "spotless",
        "mannodermaus-junit",
        "protobuf-plugin",
        "protobuf",
        "mockk",
        "truth",
        "turbine",
        "jqwik",
        "robolectric",
        "lint",
        "kotlinpoet",
        "firebase-bom",
        "leakcanary",
        "datastore",
        "lifecycle",
      )

    val versionsSection = extractSection(catalogContent, "[versions]")

    for (version in requiredVersions) {
      assertWithMessage("version entry '$version' missing from [versions]")
        .that(versionsSection)
        .containsMatch("(?m)^\\s*${Regex.escape(version)}\\s*=")
    }
  }

  @Test
  fun `catalog contains critical library entries`() {
    val requiredLibraries =
      listOf(
        "hilt-android",
        "hilt-compiler",
        "compose-runtime",
        "compose-bom",
        "compose-ui",
        "compose-material3",
        "junit-jupiter-api",
        "junit-vintage-engine",
        "kotlinx-collections-immutable",
        "kotlinx-coroutines-core",
        "kotlinx-coroutines-test",
        "kotlinx-serialization-json",
        "datastore-proto",
        "datastore-preferences",
        "mockk",
        "truth",
        "turbine",
        "robolectric",
        "jqwik",
        "ksp-api",
        "kotlinpoet",
        "lint-api",
        "lint-tests",
        "firebase-bom",
        "leakcanary",
      )

    val librariesSection = extractSection(catalogContent, "[libraries]")

    for (lib in requiredLibraries) {
      assertWithMessage("library entry '$lib' missing from [libraries]")
        .that(librariesSection)
        .containsMatch("(?m)^\\s*${Regex.escape(lib)}\\s*=")
    }
  }

  @Test
  fun `catalog contains required plugin entries`() {
    val requiredPlugins =
      listOf(
        "android-application",
        "android-library",
        "ksp",
        "hilt",
        "kotlin-compose",
        "kotlin-serialization",
        "protobuf",
        "spotless",
        "android-junit",
      )

    val pluginsSection = extractSection(catalogContent, "[plugins]")

    for (plugin in requiredPlugins) {
      assertWithMessage("plugin entry '$plugin' missing from [plugins]")
        .that(pluginsSection)
        .containsMatch("(?m)^\\s*${Regex.escape(plugin)}\\s*=")
    }
  }

  @Test
  fun `catalog contains gradle plugin libraries for build-logic`() {
    val requiredGradlePlugins =
      listOf(
        "android-gradlePlugin",
        "kotlin-gradlePlugin",
        "compose-compiler-gradlePlugin",
        "ksp-gradlePlugin",
        "hilt-gradlePlugin",
        "android-junit-gradlePlugin",
        "spotless-gradlePlugin",
        "kotlin-serialization-gradlePlugin",
      )

    val librariesSection = extractSection(catalogContent, "[libraries]")

    for (lib in requiredGradlePlugins) {
      assertWithMessage("gradle plugin library '$lib' missing from [libraries]")
        .that(librariesSection)
        .containsMatch("(?m)^\\s*${Regex.escape(lib)}\\s*=")
    }
  }

  /**
   * Extracts a TOML section from the catalog content. Returns everything from the section header
   * until the next section header or end of file.
   */
  private fun extractSection(content: String, sectionHeader: String): String {
    val headerIndex = content.indexOf(sectionHeader)
    if (headerIndex == -1) return ""

    val startIndex = headerIndex + sectionHeader.length
    // Find the next [section] header
    val nextSectionRegex = Regex("""\n\[""")
    val nextSectionIndex =
      nextSectionRegex.find(content, startIndex)?.range?.first ?: content.length

    return content.substring(startIndex, nextSectionIndex)
  }
}
