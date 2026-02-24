package app.dqxn.android.release

import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release-only Hilt module. Provides NoOp/default implementations for observability
 * bindings that DebugModule overrides in debug builds.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ReleaseModule {

  @Provides
  @Singleton
  fun provideDqxnLogger(): DqxnLogger = NoOpLogger

  @Provides
  @Singleton
  fun provideMetricsCollector(): MetricsCollector = MetricsCollector()
}
