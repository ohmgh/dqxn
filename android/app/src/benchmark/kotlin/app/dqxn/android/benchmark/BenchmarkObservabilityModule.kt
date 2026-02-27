package app.dqxn.android.benchmark

import android.content.Context
import app.dqxn.android.sdk.observability.crash.CrashEvidenceWriter
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
 * Benchmark-only Hilt module providing observability bindings with release-appropriate
 * configuration for production-representative performance measurement.
 *
 * Mirrors [app.dqxn.android.release.ReleaseModule] (NoOpLogger, 128-entry ring buffer) plus
 * [CrashEvidenceWriter] needed by agentic diagnostic handlers.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object BenchmarkObservabilityModule {

  @Provides @Singleton fun provideDqxnLogger(): DqxnLogger = NoOpLogger

  @Provides @Singleton fun provideMetricsCollector(): MetricsCollector = MetricsCollector()

  @Provides @Singleton fun provideRingBufferSink(): RingBufferSink = RingBufferSink(capacity = 128)

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
  fun provideCrashEvidenceWriter(
    @ApplicationContext context: Context,
  ): CrashEvidenceWriter =
    CrashEvidenceWriter(
      context.getSharedPreferences("crash_evidence", Context.MODE_PRIVATE),
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
