@file:OptIn(ExperimentalCompilerApi::class)

package app.dqxn.android.codegen.plugin

import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Synthetic stubs matching the annotation and interface shapes from `:sdk:contracts`, plus minimal
 * Dagger/Hilt stubs so the generated Hilt module compiles within the test compilation.
 *
 * These stubs let compile-testing validate the KSP processor without depending on the real modules.
 * FQNs must match exactly what the processor uses for `resolver.getSymbolsWithAnnotation()` and
 * supertype checks.
 */
internal fun contractStubs(): Array<SourceFile> =
  arrayOf(
    SourceFile.kotlin(
      "DashboardWidget.kt",
      """
        package app.dqxn.android.sdk.contracts.annotation

        @Retention(AnnotationRetention.SOURCE)
        @Target(AnnotationTarget.CLASS)
        annotation class DashboardWidget(
            val typeId: String,
            val displayName: String,
            val icon: String = "",
        )
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "DashboardDataProvider.kt",
      """
        package app.dqxn.android.sdk.contracts.annotation

        @Retention(AnnotationRetention.SOURCE)
        @Target(AnnotationTarget.CLASS)
        annotation class DashboardDataProvider(
            val localId: String,
            val displayName: String,
            val description: String = "",
        )
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "DashboardSnapshot.kt",
      """
        package app.dqxn.android.sdk.contracts.annotation

        @Retention(AnnotationRetention.SOURCE)
        @Target(AnnotationTarget.CLASS)
        annotation class DashboardSnapshot(
            val dataType: String,
        )
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "Immutable.kt",
      """
        package androidx.compose.runtime

        @Retention(AnnotationRetention.BINARY)
        @Target(AnnotationTarget.CLASS)
        annotation class Immutable
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "DataSnapshot.kt",
      """
        package app.dqxn.android.sdk.contracts.provider

        import androidx.compose.runtime.Immutable

        @Immutable
        interface DataSnapshot {
            val timestamp: Long
        }
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "WidgetRenderer.kt",
      """
        package app.dqxn.android.sdk.contracts.widget

        interface WidgetRenderer
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "DataProvider.kt",
      """
        package app.dqxn.android.sdk.contracts.provider

        interface DataProvider<T : DataSnapshot>
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "DataProviderSpec.kt",
      """
        package app.dqxn.android.sdk.contracts.provider

        interface DataProviderSpec {
            val sourceId: String
            val displayName: String
            val description: String
            val dataType: String
        }
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "Gated.kt",
      """
        package app.dqxn.android.sdk.contracts.entitlement

        interface Gated {
            val requiredAnyEntitlement: Set<String>?
        }
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "WidgetSpec.kt",
      """
        package app.dqxn.android.sdk.contracts.widget

        interface WidgetSpec {
            val typeId: String
            val displayName: String
        }
        """
        .trimIndent(),
    ),
  )

/**
 * Minimal Dagger/Hilt annotation stubs so the generated Hilt module compiles within
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

/**
 * Stubs for kotlinx-collections-immutable and DashboardPackManifest types used by the
 * ManifestGenerator. The generated manifest code references these types.
 */
internal fun manifestStubs(): Array<SourceFile> =
  arrayOf(
    SourceFile.kotlin(
      "PersistentList.kt",
      """
        package kotlinx.collections.immutable

        fun <T> persistentListOf(vararg elements: T): List<T> = elements.toList()
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "DashboardPackManifest.kt",
      """
        package app.dqxn.android.sdk.contracts.pack

        data class DashboardPackManifest(
            val packId: String,
            val displayName: String,
            val description: String,
            val version: Int,
            val widgets: List<PackWidgetRef>,
            val themes: List<Any>,
            val dataProviders: List<PackDataProviderRef>,
            val category: PackCategory,
            val entitlementId: String?,
        )
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "PackWidgetRef.kt",
      """
        package app.dqxn.android.sdk.contracts.pack

        data class PackWidgetRef(
            val typeId: String,
            val displayName: String,
        )
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "PackDataProviderRef.kt",
      """
        package app.dqxn.android.sdk.contracts.pack

        data class PackDataProviderRef(
            val sourceId: String,
            val displayName: String,
            val dataType: String,
        )
        """
        .trimIndent(),
    ),
    SourceFile.kotlin(
      "PackCategory.kt",
      """
        package app.dqxn.android.sdk.contracts.pack

        enum class PackCategory {
            ESSENTIALS,
            PLUS,
            THEMES,
            DEMO,
        }
        """
        .trimIndent(),
    ),
  )
