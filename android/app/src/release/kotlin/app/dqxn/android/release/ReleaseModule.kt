package app.dqxn.android.release

import android.content.Context
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticFileWriter
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import app.dqxn.android.sdk.observability.health.WidgetHealthMonitor
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.observability.log.RingBufferSink
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import app.dqxn.android.sdk.observability.trace.DqxnTracer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Release-only Hilt module. Provides NoOp/default implementations for observability bindings that
 * DebugModule overrides in debug builds.
 *
 * All 6 bindings mirror DebugModule's structure but with release-appropriate configuration:
 * - NoOpLogger (zero-alloc logging disabled in release)
 * - Smaller RingBufferSink capacity (128 vs debug's 512)
 * - Same construction for WidgetHealthMonitor, DiagnosticFileWriter, DiagnosticSnapshotCapture
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReleaseModule {

  companion object {

    @Provides @Singleton fun provideDqxnLogger(): DqxnLogger = NoOpLogger

    @Provides @Singleton fun provideMetricsCollector(): MetricsCollector = MetricsCollector()

    @Provides
    @Singleton
    fun provideRingBufferSink(): RingBufferSink = RingBufferSink(capacity = 128)

    @Provides
    @Singleton
    fun provideWidgetHealthMonitor(
      logger: DqxnLogger,
    ): WidgetHealthMonitor =
      WidgetHealthMonitor(
        logger = logger,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
      )

    @Provides
    @Singleton
    fun provideDiagnosticFileWriter(
      @ApplicationContext context: Context,
      logger: DqxnLogger,
    ): DiagnosticFileWriter =
      DiagnosticFileWriter(
        baseDirectory = File(context.filesDir, "diagnostics"),
        logger = logger,
      )

    @Provides
    @Singleton
    fun provideDiagnosticSnapshotCapture(
      logger: DqxnLogger,
      metricsCollector: MetricsCollector,
      ringBufferSink: RingBufferSink,
      fileWriter: DiagnosticFileWriter,
    ): DiagnosticSnapshotCapture =
      DiagnosticSnapshotCapture(
        logger = logger,
        metricsCollector = metricsCollector,
        tracer = DqxnTracer,
        logRingBuffer = ringBufferSink,
        fileWriter = fileWriter,
      )
  }
}
