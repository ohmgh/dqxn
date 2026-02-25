package app.dqxn.android.debug

import android.content.Context
import app.dqxn.android.sdk.observability.crash.CrashEvidenceWriter
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticFileWriter
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import app.dqxn.android.sdk.observability.health.WidgetHealthMonitor
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.DqxnLoggerImpl
import app.dqxn.android.sdk.observability.log.LogLevel
import app.dqxn.android.sdk.observability.log.RingBufferSink
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import app.dqxn.android.sdk.observability.trace.DqxnTracer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Debug-only Hilt module providing observability type bindings with debug-appropriate
 * configuration: verbose logging, 512-entry ring buffer, CrashEvidenceWriter.
 *
 * Agentic bindings (@Multibinds CommandHandler, ChaosProviderInterceptor) moved to
 * AgenticModule in `src/agentic/` (shared by debug + benchmark).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DebugModule {

    @Provides
    @Singleton
    fun provideRingBufferSink(): RingBufferSink = RingBufferSink(capacity = 512)

    @Provides
    @Singleton
    fun provideDqxnLogger(ringBufferSink: RingBufferSink): DqxnLogger =
      DqxnLoggerImpl(
        sinks = listOf(ringBufferSink),
        minimumLevel = LogLevel.DEBUG,
        sessionId = UUID.randomUUID().toString().take(8),
      )

    @Provides
    @Singleton
    fun provideMetricsCollector(): MetricsCollector = MetricsCollector()

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
