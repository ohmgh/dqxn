package app.dqxn.android.codegen.plugin.model

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName

internal data class ThemeProviderInfo(
  val className: String,
  val packageName: String,
  val typeName: ClassName,
  val originatingFile: KSFile,
)
