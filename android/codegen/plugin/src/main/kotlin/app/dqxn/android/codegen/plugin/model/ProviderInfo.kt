package app.dqxn.android.codegen.plugin.model

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName

internal data class ProviderInfo(
  val className: String,
  val packageName: String,
  val localId: String,
  val displayName: String,
  val description: String,
  val dataType: String,
  val typeName: ClassName,
  val originatingFile: KSFile,
)
