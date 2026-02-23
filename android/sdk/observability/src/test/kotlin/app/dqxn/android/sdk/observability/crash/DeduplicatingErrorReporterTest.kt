package app.dqxn.android.sdk.observability.crash

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Test

class DeduplicatingErrorReporterTest {

  private class CountingErrorReporter : ErrorReporter {
    val nonFatalCount = AtomicInteger(0)
    val widgetCrashCount = AtomicInteger(0)

    override fun reportNonFatal(e: Throwable, context: ErrorContext) {
      nonFatalCount.incrementAndGet()
    }

    override fun reportWidgetCrash(
      typeId: String,
      widgetId: String,
      context: WidgetErrorContext,
    ) {
      widgetCrashCount.incrementAndGet()
    }
  }

  @Test
  fun `first report passes through`() {
    val delegate = CountingErrorReporter()
    val reporter = DeduplicatingErrorReporter(delegate, cooldownMillis = 60_000L)

    reporter.reportNonFatal(
      RuntimeException("boom"),
      ErrorContext.System("test"),
    )

    assertThat(delegate.nonFatalCount.get()).isEqualTo(1)
  }

  @Test
  fun `duplicate within cooldown dropped`() {
    val delegate = CountingErrorReporter()
    val currentTime = AtomicInteger(1000)
    val reporter =
      DeduplicatingErrorReporter(
        delegate = delegate,
        cooldownMillis = 60_000L,
        clock = { currentTime.get().toLong() },
      )
    val error = RuntimeException("boom")
    val context = ErrorContext.System("test")

    reporter.reportNonFatal(error, context)
    reporter.reportNonFatal(error, context)

    assertThat(delegate.nonFatalCount.get()).isEqualTo(1)
  }

  @Test
  fun `duplicate after cooldown passes`() {
    val delegate = CountingErrorReporter()
    var currentTime = 1000L
    val reporter =
      DeduplicatingErrorReporter(
        delegate = delegate,
        cooldownMillis = 60_000L,
        clock = { currentTime },
      )
    val error = RuntimeException("boom")
    val context = ErrorContext.System("test")

    reporter.reportNonFatal(error, context)
    currentTime += 61_000L // past cooldown
    reporter.reportNonFatal(error, context)

    assertThat(delegate.nonFatalCount.get()).isEqualTo(2)
  }

  @Test
  fun `different errors not deduplicated`() {
    val delegate = CountingErrorReporter()
    val reporter = DeduplicatingErrorReporter(delegate, cooldownMillis = 60_000L)

    reporter.reportNonFatal(
      RuntimeException("boom1"),
      ErrorContext.System("comp1"),
    )
    reporter.reportNonFatal(
      IllegalStateException("boom2"),
      ErrorContext.System("comp2"),
    )

    assertThat(delegate.nonFatalCount.get()).isEqualTo(2)
  }
}
