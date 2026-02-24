@file:OptIn(ExperimentalCompilerApi::class)

package app.dqxn.android.codegen.agentic

import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Synthetic annotation and interface stubs matching the expected `@AgenticCommand` and
 * `CommandHandler` shapes from Phase 6 (`:core:agentic`), plus minimal Dagger/Hilt stubs so the
 * generated Hilt module compiles within the test compilation.
 *
 * These stubs let compile-testing validate the KSP processor without depending on the real modules,
 * which don't exist yet.
 */
internal fun agenticStubs(): Array<SourceFile> =
  arrayOf(
    SourceFile.kotlin(
      "AgenticCommand.kt",
      """
        package app.dqxn.android.core.agentic

        @Retention(AnnotationRetention.SOURCE)
        @Target(AnnotationTarget.CLASS)
        annotation class AgenticCommand(
            val name: String,
            val description: String,
            val category: String = "",
        )
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "CommandHandler.kt",
      """
        package app.dqxn.android.core.agentic

        interface CommandHandler {
            val name: String
            val description: String
            val category: String
            val aliases: List<String>
            suspend fun execute(params: Any, commandId: String): Any
            fun paramsSchema(): Any
        }
        """
        .trimIndent(),
    ),
  )

/**
 * Minimal Dagger/Hilt annotation stubs so the generated `AgenticHiltModule` compiles within
 * compile-testing. Only the annotation shapes need to exist -- no real DI wiring.
 */
internal fun daggerStubs(): Array<SourceFile> =
  arrayOf(
    SourceFile.kotlin(
      "DaggerModule.kt",
      """
        package dagger

        @Retention(AnnotationRetention.RUNTIME)
        @Target(AnnotationTarget.CLASS)
        annotation class Module
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "DaggerBinds.kt",
      """
        package dagger

        @Retention(AnnotationRetention.RUNTIME)
        @Target(AnnotationTarget.FUNCTION)
        annotation class Binds
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "DaggerIntoSet.kt",
      """
        package dagger.multibindings

        @Retention(AnnotationRetention.RUNTIME)
        @Target(AnnotationTarget.FUNCTION)
        annotation class IntoSet
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "HiltInstallIn.kt",
      """
        package dagger.hilt

        @Retention(AnnotationRetention.RUNTIME)
        @Target(AnnotationTarget.CLASS)
        annotation class InstallIn(vararg val value: kotlin.reflect.KClass<*>)
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "SingletonComponent.kt",
      """
        package dagger.hilt.components

        interface SingletonComponent
        """
        .trimIndent(),
    ),
  )
