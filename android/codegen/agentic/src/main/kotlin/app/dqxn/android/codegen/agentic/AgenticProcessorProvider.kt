package app.dqxn.android.codegen.agentic

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class AgenticProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        AgenticProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
}
