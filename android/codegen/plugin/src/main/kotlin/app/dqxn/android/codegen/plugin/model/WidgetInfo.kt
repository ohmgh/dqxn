package app.dqxn.android.codegen.plugin.model

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName

internal data class WidgetInfo(
  val className: String,
  val packageName: String,
  val typeId: String,
  val displayName: String,
  val icon: String,
  val typeName: ClassName,
  val originatingFile: KSFile,
)
