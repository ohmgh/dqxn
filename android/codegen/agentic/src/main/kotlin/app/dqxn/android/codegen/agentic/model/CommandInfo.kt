package app.dqxn.android.codegen.agentic.model

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName

internal data class CommandInfo(
    val name: String,
    val description: String,
    val category: String,
    val className: String,
    val packageName: String,
    val typeName: ClassName,
    val originatingFile: KSFile,
)
