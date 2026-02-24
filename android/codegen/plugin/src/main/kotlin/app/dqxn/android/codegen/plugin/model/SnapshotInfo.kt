package app.dqxn.android.codegen.plugin.model

import com.google.devtools.ksp.symbol.KSFile

internal data class SnapshotInfo(
  val className: String,
  val packageName: String,
  val qualifiedName: String,
  val dataType: String,
  val originatingFile: KSFile,
)
