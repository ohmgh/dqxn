package app.dqxn.android.codegen.agentic

import app.dqxn.android.codegen.agentic.generation.CommandRouterGenerator
import app.dqxn.android.codegen.agentic.generation.SchemaGenerator
import app.dqxn.android.codegen.agentic.model.CommandInfo
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ksp.toClassName

class AgenticProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
) : SymbolProcessor {

  private var invoked = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (invoked) return emptyList()
    invoked = true

    val annotatedSymbols =
      resolver
        .getSymbolsWithAnnotation(AGENTIC_COMMAND_FQN)
        .filterIsInstance<KSClassDeclaration>()
        .toList()

    if (annotatedSymbols.isEmpty()) return emptyList()

    val commandInfos = mutableListOf<CommandInfo>()
    var hasErrors = false

    for (classDecl in annotatedSymbols) {
      if (!implementsCommandHandler(classDecl)) {
        logger.error(
          "@AgenticCommand class ${classDecl.simpleName.asString()} " +
            "must implement CommandHandler",
          classDecl,
        )
        hasErrors = true
        continue
      }

      val annotation =
        classDecl.annotations.first { annotation ->
          annotation.annotationType.resolve().declaration.qualifiedName?.asString() ==
            AGENTIC_COMMAND_FQN
        }

      val name = annotation.arguments.first { it.name?.asString() == "name" }.value as String

      val description =
        annotation.arguments.first { it.name?.asString() == "description" }.value as String

      val category =
        annotation.arguments.firstOrNull { it.name?.asString() == "category" }?.value as? String
          ?: ""

      // Check for duplicate command names
      val existing = commandInfos.firstOrNull { it.name == name }
      if (existing != null) {
        logger.error(
          "Duplicate @AgenticCommand name: \"$name\" " +
            "(already declared by ${existing.className})",
          classDecl,
        )
        hasErrors = true
        continue
      }

      commandInfos +=
        CommandInfo(
          name = name,
          description = description,
          category = category,
          className = classDecl.simpleName.asString(),
          packageName = classDecl.packageName.asString(),
          typeName = classDecl.toClassName(),
          originatingFile = classDecl.containingFile!!,
        )
    }

    if (hasErrors) return emptyList()

    CommandRouterGenerator(codeGenerator).generate(commandInfos)
    SchemaGenerator(codeGenerator).generate(commandInfos)

    return emptyList()
  }

  private fun implementsCommandHandler(classDecl: KSClassDeclaration): Boolean =
    classDecl.superTypes.any { superTypeRef ->
      superTypeRef.resolve().declaration.qualifiedName?.asString() == COMMAND_HANDLER_FQN
    }

  private companion object {
    const val AGENTIC_COMMAND_FQN = "app.dqxn.android.core.agentic.AgenticCommand"
    const val COMMAND_HANDLER_FQN = "app.dqxn.android.core.agentic.CommandHandler"
  }
}
