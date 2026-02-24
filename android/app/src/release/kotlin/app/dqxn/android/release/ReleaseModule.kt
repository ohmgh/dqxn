package app.dqxn.android.release

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Release-only Hilt module. Currently empty -- reserves the release source set namespace
 * and ensures at least one Kotlin file exists in `src/release/`.
 *
 * Future phases may add release-only bindings here (e.g., production analytics, crash
 * reporting configuration distinct from debug).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ReleaseModule
