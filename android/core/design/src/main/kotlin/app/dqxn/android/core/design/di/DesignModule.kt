package app.dqxn.android.core.design.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Hilt module for `:core:design` dependencies.
 *
 * [ThemeAutoSwitchEngine] and [BuiltInThemes] use `@Inject constructor` so no explicit provides are
 * needed for them. This module provides shared infrastructure like the [Json] instance used by
 * [ThemeJsonParser].
 */
@Module
@InstallIn(SingletonComponent::class)
public object DesignModule {

  /** Provides a [Json] instance configured for lenient theme parsing. */
  @Provides @Singleton public fun provideJson(): Json = Json { ignoreUnknownKeys = true }
}
