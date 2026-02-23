import java.io.File
import org.gradle.testkit.runner.GradleRunner

/**
 * Shared test infrastructure for Gradle TestKit convention plugin tests. Creates temporary Gradle
 * projects with proper Android SDK, version catalog, and plugin resolution.
 */
object TestProjectSetup {

  val androidHome: String by lazy {
    System.getenv("ANDROID_HOME")
      ?: File("/Users/ohm/Library/Android/sdk").takeIf { it.exists() }?.absolutePath
      ?: error("ANDROID_HOME not set and default SDK path not found")
  }

  /** Root of the android/ project directory (parent of build-logic). */
  val projectRoot: File by lazy {
    File(System.getProperty("user.dir")).resolve("../..").canonicalFile
  }

  /** Parsed version entries from libs.versions.toml. */
  val catalogVersions: Map<String, String> by lazy { parseCatalogVersions() }

  /**
   * Sets up a single-module test project in [dir] that can apply convention plugins.
   *
   * Uses a root + submodule pattern: root declares external plugins with `apply false` to put their
   * classes on the buildscript classpath, then the `:lib` submodule applies convention plugins.
   *
   * Returns the path to the `:lib` submodule directory for writing its build.gradle.kts.
   */
  fun setupSingleModule(dir: File, includeComposeConfig: Boolean = false): File {
    File(dir, "local.properties").writeText("sdk.dir=$androidHome\n")

    File(dir, "gradle").mkdirs()
    File(projectRoot, "gradle/libs.versions.toml").copyTo(File(dir, "gradle/libs.versions.toml"))

    File(dir, "gradle.properties")
      .writeText(
        """
      android.useAndroidX=true
      android.nonTransitiveRClass=true
      """
          .trimIndent()
      )

    val v = catalogVersions
    File(dir, "settings.gradle.kts").writeText(settingsContent() + "\ninclude(\":lib\")\n")

    // Root build.gradle.kts: apply all external plugins with `apply false` to put their classes
    // on the buildscript classpath. Convention plugins reference AGP/KGP/KSP/Hilt types directly,
    // so those must be classloaded before the convention plugin class can be instantiated.
    File(dir, "build.gradle.kts")
      .writeText(
        """
      plugins {
        id("com.android.library") version "${v["agp"]}" apply false
        id("com.google.devtools.ksp") version "${v["ksp"]}" apply false
        id("com.google.dagger.hilt.android") version "${v["hilt"]}" apply false
        id("org.jetbrains.kotlin.plugin.compose") version "${v["kotlin"]}" apply false
        id("org.jetbrains.kotlin.plugin.serialization") version "${v["kotlin"]}" apply false
        id("de.mannodermaus.android-junit5") version "${v["mannodermaus-junit"]}" apply false
      }
      """
          .trimIndent()
      )

    val libDir = File(dir, "lib")
    libDir.mkdirs()
    File(libDir, "src/main").mkdirs()
    File(libDir, "src/main/AndroidManifest.xml").writeText("<manifest />\n")

    if (includeComposeConfig) {
      File(dir, "sdk/common").mkdirs()
      File(dir, "sdk/common/compose_compiler_config.txt").writeText("")
    }

    return libDir
  }

  /**
   * Sets up a multi-module test project with stub :sdk:* modules, :codegen:plugin, and a :testpack
   * module. Needed for PackPluginTest because dqxn.pack auto-wires :sdk:* dependencies.
   */
  fun setupMultiModuleForPack(dir: File) {
    File(dir, "local.properties").writeText("sdk.dir=$androidHome\n")

    File(dir, "gradle").mkdirs()
    File(projectRoot, "gradle/libs.versions.toml").copyTo(File(dir, "gradle/libs.versions.toml"))

    File(dir, "gradle.properties")
      .writeText(
        """
      android.useAndroidX=true
      android.nonTransitiveRClass=true
      """
          .trimIndent()
      )

    // Compose stability config (required by dqxn.android.compose which dqxn.pack applies)
    File(dir, "sdk/common").mkdirs()
    File(dir, "sdk/common/compose_compiler_config.txt").writeText("")

    val v = catalogVersions

    // Settings with all required module includes
    File(dir, "settings.gradle.kts")
      .writeText(
        settingsContent() +
          "\n" +
          """
      include(":sdk:contracts")
      include(":sdk:common")
      include(":sdk:ui")
      include(":sdk:observability")
      include(":sdk:analytics")
      include(":codegen:plugin")
      include(":testpack")
      """
            .trimIndent()
      )

    // Root build.gradle.kts — external plugins `apply false` for classpath
    File(dir, "build.gradle.kts")
      .writeText(
        """
      plugins {
        id("com.android.library") version "${v["agp"]}" apply false
        id("com.google.devtools.ksp") version "${v["ksp"]}" apply false
        id("com.google.dagger.hilt.android") version "${v["hilt"]}" apply false
        id("org.jetbrains.kotlin.plugin.compose") version "${v["kotlin"]}" apply false
        id("org.jetbrains.kotlin.plugin.serialization") version "${v["kotlin"]}" apply false
        id("org.jetbrains.kotlin.jvm") version "${v["kotlin"]}" apply false
        id("de.mannodermaus.android-junit5") version "${v["mannodermaus-junit"]}" apply false
      }
      """
          .trimIndent()
      )

    // Stub SDK modules
    for (mod in listOf("contracts", "common", "ui", "observability", "analytics")) {
      val modDir = File(dir, "sdk/$mod")
      modDir.mkdirs()
      File(modDir, "src/main").mkdirs()
      File(modDir, "src/main/AndroidManifest.xml").writeText("<manifest />\n")
      File(modDir, "build.gradle.kts")
        .writeText(
          """
        plugins { id("dqxn.android.library") }
        android { namespace = "com.test.sdk.${mod.replace("-", "")}" }
        """
            .trimIndent()
        )
    }

    // Stub codegen:plugin (JVM module)
    val codegenDir = File(dir, "codegen/plugin")
    codegenDir.mkdirs()
    File(codegenDir, "build.gradle.kts")
      .writeText(
        """
      plugins { id("dqxn.kotlin.jvm") }
      """
          .trimIndent()
      )

    // Test pack module
    val packDir = File(dir, "testpack")
    packDir.mkdirs()
    File(packDir, "src/main").mkdirs()
    File(packDir, "src/main/AndroidManifest.xml").writeText("<manifest />\n")
    File(packDir, "build.gradle.kts")
      .writeText(
        """
      plugins { id("dqxn.pack") }
      android { namespace = "com.test.pack" }
      """
          .trimIndent()
      )
  }

  /** Creates a GradleRunner for the given project directory. */
  fun runner(dir: File, vararg args: String): GradleRunner =
    GradleRunner.create()
      .withProjectDir(dir)
      .withArguments(*args, "--console=plain", "--stacktrace", "--no-configuration-cache")
      .forwardOutput()

  private fun settingsContent(): String {
    return """
      pluginManagement {
        includeBuild("${File(projectRoot, "build-logic").absolutePath}")
        repositories {
          google()
          mavenCentral()
          gradlePluginPortal()
        }
      }
      dependencyResolutionManagement {
        repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
        repositories {
          google()
          mavenCentral()
        }
      }
      rootProject.name = "test-project"
    """
      .trimIndent()
  }

  private fun parseCatalogVersions(): Map<String, String> {
    val catalog = File(projectRoot, "gradle/libs.versions.toml").readText()
    val versionRegex = Regex("""^\s*(\S+)\s*=\s*"(.+?)"\s*$""", RegexOption.MULTILINE)
    val versionsSection = catalog.substringAfter("[versions]").substringBefore("\n[")
    return versionRegex.findAll(versionsSection).associate {
      it.groupValues[1] to it.groupValues[2]
    }
  }
}
