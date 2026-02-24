package app.dqxn.android.codegen.plugin.generation

import app.dqxn.android.codegen.plugin.model.SnapshotInfo
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies

internal class StabilityConfigGenerator(
  private val codeGenerator: CodeGenerator,
) {

  fun generate(snapshots: List<SnapshotInfo>) {
    if (snapshots.isEmpty()) return

    val content = snapshots.joinToString("\n") { it.qualifiedName } + "\n"

    // Write as a resource file (not Kotlin source) into KSP's resource output directory.
    // Path: build/generated/ksp/{variant}Kotlin/resources/compose_stability_config.txt
    // PackConventionPlugin references this path in the Compose compiler DSL.
    val outputStream =
      codeGenerator.createNewFile(
        dependencies = Dependencies.ALL_FILES,
        packageName = "",
        fileName = "compose_stability_config",
        extensionName = "txt",
      )

    outputStream.bufferedWriter().use { writer -> writer.write(content) }
  }
}
